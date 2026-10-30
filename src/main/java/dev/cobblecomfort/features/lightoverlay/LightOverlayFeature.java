package dev.cobblecomfort.features.lightoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Light overlay: red crosses on blocks where monsters can spawn at any time, yellow ones where they only spawn at
 * night, and optionally the block light as a number. Blocks are checked a slice at a time and drawn as per-tick gizmos.
 */
public final class LightOverlayFeature extends Feature<LightOverlayConfig> {
	public static final LightOverlayFeature INSTANCE = new LightOverlayFeature();

	private static final int CHECKS_PER_TICK = 20000;
	private static final float FADE_STEP = 0.25f;
	private static final float COLOR_STEP = 0.4f;
	private static final int ALWAYS_COLOR = 0xFFFF3B30;
	private static final int NIGHT_COLOR = 0xFFFFD22E;
	private static final int SAFE_COLOR = 0xFF7EE07E;
	private static final double INSET = 0.28;
	/** How far a cross starts inside its final size while it fades in. */
	private static final double GROW = 0.18;
	private static final double LIFT = 0.02;
	private static final float LINE_WIDTH = 2f;
	private static final float NUMBER_SCALE = 0.4f;

	/** Monsters checked against, standing for the common two-block-tall ones. */
	private static final EntityType<?> REFERENCE = EntityTypes.ZOMBIE;

	/** One block a monster could stand in, keyed by its packed position. */
	private static final class Mark {
		SpawnLight light;
		int blockLight;
		/** Height of the surface the monster stands on. */
		double top;
		boolean standable;
		float cross;
		float number;
		int color;
	}

	private final Long2ObjectOpenHashMap<Mark> marks = new Long2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
	private KeyMapping key;
	private float progress;
	private ClientLevel scannedLevel;
	private BlockPos scanCenter;
	private int scanRange;
	private int scanCursor;

	private LightOverlayFeature() {
		super("light_overlay", LightOverlayConfig.class, LightOverlayConfig::new);
	}

	@Override
	public void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.light_overlay", InputConstants.KEY_F7, CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	@Override
	protected void onConfigLoaded() {
		if (!config.rememberState) {
			config.active = false;
		}
		progress = config.active ? 1f : 0f;
	}

	@Override
	public ConfigCategory buildCategory() {
		return LightOverlayConfigUi.buildCategory(this);
	}

	private void toggle(Minecraft mc) {
		if (!isEnabled()) {
			return;
		}
		config.active = !config.active;
		if (config.rememberState) {
			save();
		}
		if (config.toggleMessage) {
			mc.gui.hud.setOverlayMessage(Component.translatable(config.active
					? "cobblecomfort.light_overlay.on" : "cobblecomfort.light_overlay.off")
					.withStyle(config.active ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
		}
	}

	private void tick(Minecraft mc) {
		while (key.consumeClick()) {
			toggle(mc);
		}
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level != scannedLevel) {
			marks.clear();
			scanCenter = null;
			scannedLevel = level;
		}
		boolean on = isEnabled() && config.active && level != null && player != null;
		progress = approach(progress, on ? 1f : 0f, config.fade ? FADE_STEP : 1f);
		if (progress <= 0f || level == null || player == null) {
			marks.clear();
			scanCenter = null;
			return;
		}
		if (on) {
			scan(level, player);
		}
		draw(mc, player);
	}

	// ---------------------------------------------------------------- scanning

	/** Checks the next columns of the cube around the player; a full pass restarts around the player's new position. */
	private void scan(ClientLevel level, LocalPlayer player) {
		if (scanCenter == null || scanCursor >= side(scanRange) * side(scanRange)) {
			scanCenter = player.blockPosition();
			scanRange = config.range;
			scanCursor = 0;
		}
		int side = side(scanRange);
		int minY = Math.max(level.getMinY() + 1, scanCenter.getY() - scanRange);
		int maxY = Math.min(level.getMaxY(), scanCenter.getY() + scanRange);
		int checks = 0;
		while (scanCursor < side * side && checks < CHECKS_PER_TICK) {
			int x = scanCenter.getX() - scanRange + scanCursor % side;
			int z = scanCenter.getZ() - scanRange + scanCursor / side;
			scanCursor++;
			if (!level.hasChunkAt(x, z)) {
				continue;
			}
			for (int y = minY; y <= maxY; y++) {
				check(level, scanPos.set(x, y, z));
			}
			checks += maxY - minY + 1;
		}
	}

	private static int side(int range) {
		return 2 * range + 1;
	}

	private void check(ClientLevel level, BlockPos pos) {
		long packed = pos.asLong();
		Mark mark = marks.get(packed);
		double top = surface(level, pos);
		if (Double.isNaN(top)) {
			if (mark != null) {
				mark.standable = false;
			}
			return;
		}
		SpawnLight light = SpawnLight.at(level, pos);
		if (mark == null) {
			if (light == SpawnLight.SAFE && !config.numbers) {
				return;
			}
			mark = new Mark();
			mark.color = color(light);
			marks.put(packed, mark);
		}
		mark.standable = true;
		mark.light = light;
		mark.blockLight = level.getBrightness(LightLayer.BLOCK, pos);
		mark.top = top;
	}

	/**
	 * The height a monster would stand at with its feet in this block, or NaN when none could spawn here: the block
	 * below must allow spawning on it, there must be room for a zombie, and the biome must have monsters at all.
	 */
	private static double surface(ClientLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState ground = level.getBlockState(below);
		if (ground.isAir() || !SpawnPlacements.isSpawnPositionOk(REFERENCE, level, pos)) {
			return Double.NaN;
		}
		if (!level.noBlockCollision(null, REFERENCE.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
			return Double.NaN;
		}
		Holder<Biome> biome = level.getBiome(pos);
		if (biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(Biomes.DEEP_DARK)) {
			return Double.NaN;
		}
		VoxelShape shape = ground.getCollisionShape(level, below);
		return shape.isEmpty() ? pos.getY() : below.getY() + shape.max(Direction.Axis.Y);
	}

	// ---------------------------------------------------------------- drawing

	private void draw(Minecraft mc, LocalPlayer player) {
		BlockPos at = player.blockPosition();
		float step = config.fade ? FADE_STEP : 1f;
		try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
			ObjectIterator<Long2ObjectMap.Entry<Mark>> it = marks.long2ObjectEntrySet().fastIterator();
			while (it.hasNext()) {
				Long2ObjectMap.Entry<Mark> entry = it.next();
				long packed = entry.getLongKey();
				Mark mark = entry.getValue();
				int x = BlockPos.getX(packed);
				int y = BlockPos.getY(packed);
				int z = BlockPos.getZ(packed);
				int distance = Math.max(Math.abs(x - at.getX()), Math.max(Math.abs(y - at.getY()), Math.abs(z - at.getZ())));
				boolean crossShown = mark.standable && distance <= config.range
						&& (mark.light == SpawnLight.ALWAYS || (mark.light == SpawnLight.NIGHT && config.showNight));
				boolean numberShown = mark.standable && config.numbers && distance <= config.numberRange;
				mark.cross = approach(mark.cross, crossShown ? 1f : 0f, step);
				mark.number = approach(mark.number, numberShown ? 1f : 0f, step);
				if (mark.cross <= 0f && mark.number <= 0f && !crossShown && !numberShown) {
					it.remove();
					continue;
				}
				// A cross fading out keeps its colour; it only turns green where a number stays.
				if (crossShown || numberShown) {
					mark.color = RenderUtil.lerpColor(mark.color, color(mark.light), config.fade ? COLOR_STEP : 1f);
				}
				if (mark.cross > 0f) {
					drawCross(x, z, mark, mark.cross * progress);
				}
				if (mark.number > 0f) {
					int color = RenderUtil.withAlpha(mark.color, mark.number * progress);
					Gizmos.billboardText(Integer.toString(mark.blockLight), new Vec3(x + 0.5, mark.top + 0.15, z + 0.5),
							TextGizmo.Style.forColorAndCentered(color).withScale(NUMBER_SCALE));
				}
			}
		}
	}

	/** A cross lying flat on the surface; it grows out of the middle while fading in and shrinks while fading out. */
	private void drawCross(int x, int z, Mark mark, float alpha) {
		double inset = INSET + GROW * (1 - easeOut(alpha));
		double y = mark.top + LIFT;
		int color = RenderUtil.withAlpha(mark.color, alpha);
		Gizmos.line(new Vec3(x + inset, y, z + inset), new Vec3(x + 1 - inset, y, z + 1 - inset), color, LINE_WIDTH);
		Gizmos.line(new Vec3(x + 1 - inset, y, z + inset), new Vec3(x + inset, y, z + 1 - inset), color, LINE_WIDTH);
	}

	private static int color(SpawnLight light) {
		return switch (light) {
			case ALWAYS -> ALWAYS_COLOR;
			case NIGHT -> NIGHT_COLOR;
			case SAFE -> SAFE_COLOR;
		};
	}

	private static float easeOut(float t) {
		float inv = 1 - t;
		return 1 - inv * inv * inv;
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}
}
