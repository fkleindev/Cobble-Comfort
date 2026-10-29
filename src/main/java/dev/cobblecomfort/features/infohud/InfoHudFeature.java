package dev.cobblecomfort.features.infohud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.phantom.PhantomFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix3x2fStack;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A configurable panel with coordinates, direction, biome, light, time, weather, FPS, ping and more. */
public final class InfoHudFeature extends Feature<InfoHudConfig> {
	public static final InfoHudFeature INSTANCE = new InfoHudFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("info_hud");

	private static final int PAD = 4;
	private static final int ROW_HEIGHT = 11;
	private static final int ICON_SIZE = 10;
	private static final float ICON_SCALE = ICON_SIZE / 16f;
	private static final int ICON_GAP = 3;
	private static final int DEFAULT_X = 2;
	private static final int DEFAULT_Y = 16;
	private static final float ROW_FADE_PER_SECOND = 6f;
	private static final float SMOOTHING = 8f;
	private static final String SEPARATOR = "  ·  ";
	private static final String[] DIRECTIONS = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

	/** A line ready to draw: label and value (null value = not available right now). */
	private record Row(InfoEntry entry, Component label, Component value) {
	}

	private final Map<InfoEntry, Float> appear = new EnumMap<>(InfoEntry.class);
	private final Map<InfoEntry, Row> lastRows = new EnumMap<>(InfoEntry.class);
	private final Map<InfoEntry, Float> smoothed = new EnumMap<>(InfoEntry.class);
	private KeyMapping copyKey;
	private double lastX, lastZ;
	private float speed;
	private long lastFrameNanos;
	private float width = 100;
	private float height = ROW_HEIGHT;

	private InfoHudFeature() {
		super("info_hud", InfoHudConfig.class, InfoHudConfig::new);
	}

	@Override
	public void init() {
		copyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.copy_coords", InputConstants.KEY_F6, CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, LAYER_ID, (g, delta) -> draw(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return InfoHudConfigUi.buildCategory(this);
	}

	public Rect defaultBounds(float screenWidth, float screenHeight) {
		return new Rect(DEFAULT_X, DEFAULT_Y, Math.max(20, width), Math.max(ROW_HEIGHT, height));
	}

	/** Lowest point of the panel at its default place with all configured lines (0 when off), so others can avoid it. */
	public float defaultBottom() {
		if (!isEnabled()) {
			return 0f;
		}
		int lines = config.style == InfoStyle.COMPACT ? 1 : config.entries.size();
		return DEFAULT_Y + lines * ROW_HEIGHT + 2 * PAD;
	}

	private void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player != null) {
			double dx = player.getX() - lastX;
			double dz = player.getZ() - lastZ;
			speed = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
			lastX = player.getX();
			lastZ = player.getZ();
		}
		while (copyKey.consumeClick()) {
			if (isEnabled() && player != null) {
				BlockPos pos = player.blockPosition();
				String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
				mc.keyboardHandler.setClipboard(coords);
				mc.gui.hud.setOverlayMessage(Component.translatable("cobblecomfort.info.copied", coords)
						.withStyle(ChatFormatting.GREEN), false);
			}
		}
	}

	// ---------------------------------------------------------------- rows

	private List<Row> rows(Minecraft mc, float dt) {
		List<Row> rows = new ArrayList<>();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return rows;
		}
		BlockPos pos = player.blockPosition();
		for (InfoEntry entry : config.entries) {
			Component value = switch (entry) {
				case COORDS -> coords(level, pos);
				case DIRECTION -> direction(player);
				case BIOME -> level.getBiome(pos).unwrapKey()
						.<Component>map(key -> Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()))
						.orElse(Component.literal("?"));
				case LIGHT -> light(level, pos);
				case TIME -> time(level);
				case WEATHER -> Component.translatable(level.isThundering() ? "cobblecomfort.info.weather.thunder"
						: level.isRaining() ? "cobblecomfort.info.weather.rain" : "cobblecomfort.info.weather.clear");
				case FPS -> fps(mc, dt);
				case PING -> ping(mc, player, dt);
				case CHUNK -> Component.literal(SectionPos.blockToSectionCoord(pos.getX()) + ", " + SectionPos.blockToSectionCoord(pos.getZ())
						+ "  (" + (pos.getX() & 15) + ", " + (pos.getZ() & 15) + ")");
				case SPEED -> Component.literal(String.format(Locale.ROOT, "%.1f", smooth(entry, speed, dt)))
						.append(Component.translatable("cobblecomfort.info.speed_unit"));
				case REAL_TIME -> Component.literal(String.format("%tH:%<tM", LocalTime.now()));
				case TARGET -> target(mc);
				case REST -> PhantomFeature.INSTANCE.infoValue(mc, config.colorValues);
			};
			rows.add(new Row(entry, Component.translatable("cobblecomfort.info.label." + entry.name().toLowerCase(Locale.ROOT)), value));
		}
		return rows;
	}

	private Component coords(ClientLevel level, BlockPos pos) {
		MutableComponent text = Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
		if (config.otherDimensionCoords) {
			if (level.dimension() == Level.OVERWORLD) {
				text.append(Component.literal("  " + Math.floorDiv(pos.getX(), 8) + " " + Math.floorDiv(pos.getZ(), 8))
						.withStyle(ChatFormatting.RED));
			} else if (level.dimension() == Level.NETHER) {
				text.append(Component.literal("  " + pos.getX() * 8 + " " + pos.getZ() * 8).withStyle(ChatFormatting.GREEN));
			}
		}
		return text;
	}

	private Component direction(LocalPlayer player) {
		float yaw = ((player.getYRot() % 360f) + 360f) % 360f;
		int index = Math.round(yaw / 45f) % DIRECTIONS.length;
		MutableComponent text = Component.translatable("cobblecomfort.info.dir." + DIRECTIONS[index]);
		String axis = switch (player.getDirection()) {
			case SOUTH -> "+Z";
			case NORTH -> "-Z";
			case EAST -> "+X";
			default -> "-X";
		};
		text.append(Component.literal("  " + axis).withStyle(ChatFormatting.GRAY));
		if (config.directionDegrees) {
			text.append(Component.literal(String.format(Locale.ROOT, "  %.0f°", yaw)).withStyle(ChatFormatting.GRAY));
		}
		return text;
	}

	private Component light(ClientLevel level, BlockPos pos) {
		int block = level.getBrightness(LightLayer.BLOCK, pos);
		int sky = level.getBrightness(LightLayer.SKY, pos);
		MutableComponent text = Component.translatable("cobblecomfort.info.light_value", block, sky);
		// Monsters need block light 0, and darkness from the sky too (night or no sky light).
		if (config.colorValues && block == 0 && (sky == 0 || level.isDarkOutside())) {
			text.withStyle(ChatFormatting.RED);
		}
		return text;
	}

	private Component time(ClientLevel level) {
		long time = level.getDefaultClockTime();
		long dayTicks = Math.floorMod(time, 24000L);
		int hours = (int) ((dayTicks / 1000 + 6) % 24);
		int minutes = (int) (dayTicks % 1000 * 60 / 1000);
		MutableComponent text = Component.literal(String.format("%02d:%02d", hours, minutes))
				.append(Component.translatable("cobblecomfort.info.day", time / 24000L + 1).withStyle(ChatFormatting.GRAY));
		if (config.sleepHint && (level.isDarkOutside() || level.isThundering()) && level.dimension() == Level.OVERWORLD) {
			text.append(Component.translatable("cobblecomfort.info.sleep").withStyle(ChatFormatting.AQUA));
		}
		return text;
	}

	private Component fps(Minecraft mc, float dt) {
		int fps = Math.round(smooth(InfoEntry.FPS, mc.getFps(), dt));
		ChatFormatting color = fps >= 60 ? ChatFormatting.GREEN : fps >= 30 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return colored(Component.literal(String.valueOf(fps)), color);
	}

	/** Ping, or null in singleplayer where it means nothing. */
	private Component ping(Minecraft mc, LocalPlayer player, float dt) {
		if (mc.getSingleplayerServer() != null || mc.getConnection() == null) {
			return null;
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
		if (info == null) {
			return null;
		}
		int ping = Math.round(smooth(InfoEntry.PING, info.getLatency(), dt));
		ChatFormatting color = ping < 80 ? ChatFormatting.GREEN : ping < 200 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return colored(Component.literal(ping + " ms"), color);
	}

	private static Component target(Minecraft mc) {
		if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
		return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	private Component colored(MutableComponent text, ChatFormatting color) {
		return config.colorValues ? text.withStyle(color) : text;
	}

	/** Eases a changing number (FPS, ping, speed) towards its new value when animations are on. */
	private float smooth(InfoEntry entry, float target, float dt) {
		if (!config.animate) {
			return target;
		}
		float current = smoothed.getOrDefault(entry, target);
		current += (target - current) * (1f - (float) Math.exp(-SMOOTHING * dt));
		smoothed.put(entry, current);
		return current;
	}

	// ---------------------------------------------------------------- drawing

	private void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		long nanos = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((nanos - lastFrameNanos) / 1e9f, 0.1f);
		lastFrameNanos = nanos;
		if (!isEnabled() || mc.player == null || (config.hideWithDebug && mc.debugEntries.isOverlayVisible())) {
			return;
		}
		List<Row> rows = new ArrayList<>();
		for (Row row : rows(mc, dt)) {
			boolean available = row.value() != null;
			if (available) {
				lastRows.put(row.entry(), row);
			}
			float a = appear.getOrDefault(row.entry(), available ? (config.animate ? 0f : 1f) : 0f);
			float step = config.animate ? ROW_FADE_PER_SECOND * dt : 1f;
			a = Math.clamp(a + (available ? step : -step), 0f, 1f);
			appear.put(row.entry(), a);
			Row shown = available ? row : lastRows.get(row.entry());
			if (a > 0f && shown != null) {
				rows.add(shown);
			}
		}
		if (rows.isEmpty()) {
			return;
		}
		Font font = mc.font;
		if (config.style == InfoStyle.COMPACT) {
			drawCompact(g, font, rows);
		} else {
			drawRows(g, font, rows, dt);
		}
	}

	private void drawRows(GuiGraphicsExtractor g, Font font, List<Row> rows, float dt) {
		boolean panel = config.style == InfoStyle.PANEL;
		int textX = config.icons ? ICON_SIZE + ICON_GAP : 0;
		float targetWidth = 0;
		float targetHeight = 0;
		for (Row row : rows) {
			targetWidth = Math.max(targetWidth, textX + font.width(row.label()) + font.width(row.value()));
			targetHeight += ROW_HEIGHT * appear.get(row.entry());
		}
		int pad = panel ? PAD : 0;
		targetWidth += 2 * pad;
		targetHeight += 2 * pad - 2;
		float follow = config.animate ? 1f - (float) Math.exp(-14f * dt) : 1f;
		width += (targetWidth - width) * follow;
		height += (targetHeight - height) * follow;
		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		int x0 = Math.round(bounds.x());
		int y0 = Math.round(bounds.y());
		if (panel) {
			RenderUtil.roundedFill(g, x0, y0, x0 + Math.round(width), y0 + Math.round(height), 3, config.backgroundColor);
		}
		float y = y0 + pad;
		for (Row row : rows) {
			float a = appear.get(row.entry());
			int ry = Math.round(y);
			if (config.icons && a > 0.5f) {
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(x0 + pad, ry - 1);
				pose.scale(ICON_SCALE);
				g.item(new ItemStack(row.entry().icon()), 0, 0);
				pose.popMatrix();
			}
			int tx = x0 + pad + textX;
			g.text(font, row.label(), tx, ry, RenderUtil.withAlpha(config.labelColor, a), true);
			g.text(font, row.value(), tx + font.width(row.label()), ry, RenderUtil.withAlpha(config.valueColor, a), true);
			y += ROW_HEIGHT * a;
		}
	}

	private void drawCompact(GuiGraphicsExtractor g, Font font, List<Row> rows) {
		MutableComponent line = Component.empty();
		for (int i = 0; i < rows.size(); i++) {
			if (i > 0) {
				line.append(Component.literal(SEPARATOR).withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(rows.get(i).value());
		}
		width = font.width(line) + 2 * PAD;
		height = font.lineHeight + 2 * PAD - 2;
		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		int x0 = Math.round(bounds.x());
		int y0 = Math.round(bounds.y());
		RenderUtil.roundedFill(g, x0, y0, x0 + Math.round(width), y0 + Math.round(height), 3, config.backgroundColor);
		g.text(font, line, x0 + PAD, y0 + PAD, config.valueColor, true);
	}
}
