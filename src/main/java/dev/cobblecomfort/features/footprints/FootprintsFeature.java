package dev.cobblecomfort.features.footprints;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.Movers;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

/**
 * Footprints: players and mobs walking over snow, sand or mud leave prints that fade away again, and wet feet print
 * on any ground for a few steps out of the water. Shoes for players
 * and the like, hooves, paws and bird claws for animals, flat on the ground in a darker shade of it.
 */
public final class FootprintsFeature extends Feature<FootprintsConfig> {
	public static final FootprintsFeature INSTANCE = new FootprintsFeature();

	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/footprints.png");
	private static final double RANGE = 48;
	private static final int MAX_PRINTS = 800;
	/** Distance walked between two prints, for a player-sized walker. */
	private static final double STRIDE = 0.55;
	private static final double SPRINT_STRIDE = 0.75;
	/** Further in one tick is a teleport, not a step. */
	private static final double MAX_STEP = 1.2;
	private static final double LIFT = 0.012;
	private static final double PLAYER_WIDTH = 0.6;
	/** The part of its life over which a print fades out. */
	private static final float FADE_PART = 0.4f;
	/** Rain and snowfall wear prints away this much faster. */
	private static final float WEATHER_SPEED = 3f;
	private static final float SNEAK_ALPHA = 0.6f;
	private static final int SNOW_SHADE = 0xFF5D7896;
	private static final float GROUND_DARKEN = 0.5f;
	/** Out of the water, feet stay wet this long (ticks) and leave wet prints on any ground, which dry this fast. */
	private static final int WET_TICKS = 100;
	private static final int WET_LIFE = 160;
	private static final int WET_SHADE = 0xFF1C232B;

	/** The shapes in the texture, side by side, toes at the top. */
	private enum Foot {
		SHOE, PAW, HOOF, CLAW
	}

	private static final class Print {
		final BlockPos ground;
		final BlockState state;
		final BlockPos lightPos;
		final double x;
		final double y;
		final double z;
		/** Direction of travel. */
		final float dirX;
		final float dirZ;
		final float size;
		final Foot foot;
		final boolean mirrored;
		final int tint;
		final float alpha;
		final int light;
		/** Ticks until it is gone. */
		final float life;
		float age;

		Print(BlockPos ground, BlockState state, BlockPos lightPos, double x, double y, double z, float dirX, float dirZ,
				float size, Foot foot, boolean mirrored, int tint, float alpha, int light, float life) {
			this.ground = ground;
			this.state = state;
			this.lightPos = lightPos;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dirX = dirX;
			this.dirZ = dirZ;
			this.size = size;
			this.foot = foot;
			this.mirrored = mirrored;
			this.tint = tint;
			this.alpha = alpha;
			this.light = light;
			this.life = life;
		}
	}

	/** How far an entity has walked since its last print, which foot is next, and how long its feet stay wet. */
	private static final class Walker {
		double walked;
		boolean left;
		int wet;
	}

	private final ArrayDeque<Print> prints = new ArrayDeque<>();
	private final Int2ObjectOpenHashMap<Walker> walkers = new Int2ObjectOpenHashMap<>();
	private final IntSet seen = new IntOpenHashSet();
	private ClientLevel lastLevel;

	private FootprintsFeature() {
		super("footprints", FootprintsConfig.class, FootprintsConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> clear());
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	public ConfigCategory buildCategory() {
		return FootprintsConfigUi.buildCategory(this);
	}

	private void clear() {
		prints.clear();
		walkers.clear();
	}

	// ---------------------------------------------------------------- walking

	private void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (level == null || !isEnabled()) {
			clear();
			return;
		}
		if (!mc.isPaused()) {
			age(level);
		}
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		seen.clear();
		for (Movers.Mover mover : Movers.around(level, camera, RANGE, config.players, config.mobs, false)) {
			Foot foot = foot(mover.entity());
			if (foot != null) {
				seen.add(mover.entity().getId());
				walk(level, mover, foot);
			}
		}
		walkers.keySet().retainAll(seen);
	}

	/** Which prints an entity leaves; null for those that fly, hover, hop or have no feet. */
	private static Foot foot(Entity entity) {
		if (entity instanceof Player || entity instanceof Zombie || entity instanceof AbstractSkeleton
				|| entity instanceof AbstractIllager || entity instanceof AbstractVillager || entity instanceof AbstractPiglin
				|| entity instanceof Witch || entity instanceof Enderman || entity instanceof IronGolem
				|| entity instanceof Warden || entity instanceof Creaking) {
			return Foot.SHOE;
		}
		if (!(entity instanceof Mob) || entity instanceof Slime || entity instanceof SnowGolem || entity instanceof Breeze) {
			return null;
		}
		if (entity instanceof AbstractHorse || entity instanceof AbstractCow || entity instanceof Pig || entity instanceof Sheep
				|| entity instanceof Goat || entity instanceof Hoglin || entity instanceof Camel || entity instanceof Sniffer) {
			return Foot.HOOF;
		}
		if (entity instanceof Chicken || entity instanceof Parrot) {
			return Foot.CLAW;
		}
		return Foot.PAW;
	}

	private void walk(ClientLevel level, Movers.Mover mover, Foot foot) {
		Entity entity = mover.entity();
		Walker walker = walkers.computeIfAbsent(entity.getId(), id -> new Walker());
		Vec3 motion = mover.motion();
		double moved = motion.horizontalDistance();
		if (entity.isInWater()) {
			walker.wet = config.wet ? WET_TICKS : 0;
		} else if (walker.wet > 0) {
			walker.wet--;
		}
		if (!entity.onGround() || entity.isPassenger() || entity.isInWater() || moved > MAX_STEP) {
			walker.walked = 0;
			return;
		}
		walker.walked += moved;
		// Big animals have bigger feet, but not in proportion to their width.
		float width = entity.getBbWidth() / (float) PLAYER_WIDTH;
		float scale = Mth.clamp((float) Math.sqrt(width), 0.6f, 1.3f);
		double stride = (entity.isSprinting() ? SPRINT_STRIDE : STRIDE) * Math.max(0.7f, width);
		if (walker.walked < stride || moved < 1e-4) {
			return;
		}
		walker.walked %= stride;
		walker.left = !walker.left;
		float dirX = (float) (motion.x / moved);
		float dirZ = (float) (motion.z / moved);
		// Left and right feet sit a little apart, across the direction of travel.
		double side = (walker.left ? -1 : 1) * entity.getBbWidth() * 0.3;
		Vec3 at = entity.position();
		double x = at.x - dirZ * side;
		double z = at.z + dirX * side;
		float alpha = entity.isCrouching() ? SNEAK_ALPHA : 1f;
		place(level, x, at.y, z, dirX, dirZ, scale, foot, walker.left, alpha, walker.wet / (float) WET_TICKS);
	}

	/**
	 * A print where a foot comes down, if the ground there takes prints; with wet feet ({@code wetness} above 0) on any
	 * solid ground, as a dark wet print that dries quickly.
	 */
	private void place(ClientLevel level, double x, double y, double z, float dirX, float dirZ, float scale, Foot foot,
			boolean left, float alpha, float wetness) {
		BlockPos feet = BlockPos.containing(x, y + 0.01, z);
		BlockState state = level.getBlockState(feet);
		BlockPos ground;
		double top;
		int tint;
		float life = Math.max(1, config.duration) * 20f;
		if (state.is(Blocks.SNOW)) {
			if (!config.snow) {
				return;
			}
			ground = feet;
			top = feet.getY() + state.getValue(SnowLayerBlock.LAYERS) / 8.0;
			tint = SNOW_SHADE;
		} else {
			// Low enough to find mud and soul sand, which feet sink into a little.
			ground = BlockPos.containing(x, y - 0.2, z);
			state = level.getBlockState(ground);
			boolean open = level.getBlockState(ground.above()).getCollisionShape(level, ground.above()).isEmpty();
			if (takesPrints(state) && open) {
				top = ground.getY() + 1;
				tint = state.is(Blocks.SNOW_BLOCK) ? SNOW_SHADE
						: RenderUtil.lerpColor(0xFF000000 | state.getMapColor(level, ground).col, 0xFF000000, GROUND_DARKEN);
			} else {
				// Wet feet print on whatever they stand on: the top of its shape, where the feet are.
				VoxelShape shape = state.getCollisionShape(level, ground);
				top = shape.isEmpty() ? Double.NaN : ground.getY() + shape.max(Direction.Axis.Y);
				if (wetness <= 0f || Double.isNaN(top) || Math.abs(top - y) > 0.1) {
					return;
				}
				tint = WET_SHADE;
				alpha *= wetness;
				life = WET_LIFE;
			}
		}
		BlockPos lightPos = BlockPos.containing(x, Math.floor(top - 1e-4) + 1, z);
		prints.addLast(new Print(ground, state, lightPos, x, top, z, dirX, dirZ, scale, foot, left, tint, alpha,
				LightCoordsUtil.getLightCoords(level, lightPos), life));
		while (prints.size() > MAX_PRINTS) {
			prints.removeFirst();
		}
	}

	private boolean takesPrints(BlockState state) {
		if (state.is(Blocks.SNOW_BLOCK)) {
			return config.snow;
		}
		if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SUSPICIOUS_SAND)) {
			return config.sand;
		}
		if (state.is(Blocks.MUD) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) {
			return config.mud;
		}
		return false;
	}

	/** Prints age, faster in rain or snowfall; they go at once when their ground is dug up or changes. */
	private void age(ClientLevel level) {
		boolean precipitation = config.weather && level.isRaining();
		Iterator<Print> it = prints.iterator();
		while (it.hasNext()) {
			Print print = it.next();
			boolean wet = precipitation && level.canSeeSky(print.lightPos)
					&& level.getBiome(print.lightPos).value().hasPrecipitation();
			print.age += wet ? WEATHER_SPEED : 1f;
			if (print.age >= print.life || level.getBlockState(print.ground) != print.state) {
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- drawing

	private void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (prints.isEmpty() || mc.level == null || !isEnabled()) {
			return;
		}
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		float opacity = config.opacity;
		List<Print> snapshot = List.copyOf(prints);
		PoseStack pose = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (base, consumer) -> {
			for (Print print : snapshot) {
				float left = 1f - print.age / print.life;
				float fade = Mth.clamp(left / FADE_PART, 0f, 1f);
				float alpha = opacity * print.alpha * fade * fade;
				if (alpha > 0.01f) {
					quad(base, consumer, print, camera, alpha);
				}
			}
		});
	}

	/** One flat quad, a block wide at full size, turned to face where the walker went. */
	private static void quad(PoseStack.Pose base, VertexConsumer consumer, Print print, Vec3 camera, float alpha) {
		float half = 0.5f * print.size;
		float fx = print.dirX * half;
		float fz = print.dirZ * half;
		// Right of the direction of travel, mirrored for the left foot.
		float rx = -print.dirZ * half * (print.mirrored ? -1 : 1);
		float rz = print.dirX * half * (print.mirrored ? -1 : 1);
		float cx = (float) (print.x - camera.x);
		float cy = (float) (print.y + LIFT - camera.y);
		float cz = (float) (print.z - camera.z);
		float u0 = print.foot.ordinal() / (float) Foot.values().length;
		float u1 = (print.foot.ordinal() + 1) / (float) Foot.values().length;
		int color = RenderUtil.withAlpha(print.tint, alpha);
		vertex(base, consumer, cx + fx - rx, cy, cz + fz - rz, u0, 0f, color, print.light);
		vertex(base, consumer, cx - fx - rx, cy, cz - fz - rz, u0, 1f, color, print.light);
		vertex(base, consumer, cx - fx + rx, cy, cz - fz + rz, u1, 1f, color, print.light);
		vertex(base, consumer, cx + fx + rx, cy, cz + fz + rz, u1, 0f, color, print.light);
	}

	private static void vertex(PoseStack.Pose base, VertexConsumer consumer, float x, float y, float z, float u, float v,
			int color, int light) {
		consumer.addVertex(base, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(base, 0f, 1f, 0f);
	}
}
