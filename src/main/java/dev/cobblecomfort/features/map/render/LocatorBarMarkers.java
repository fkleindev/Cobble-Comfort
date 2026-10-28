package dev.cobblecomfort.features.map.render;

import dev.cobblecomfort.features.map.MapConfig;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint.Icon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The map's waypoints in the vanilla locator bar, players drawn as their heads (framed in their dot colour), and the
 * name and distance of whatever points almost straight ahead.
 */
public final class LocatorBarMarkers {
	/** Vanilla shows waypoints within 60° to either side across 173 px. */
	private static final double VISIBLE_ANGLE = 60.0;
	private static final double BAR_PIXELS = 173.0;
	private static final int DOT = 9;
	/** Within this angle a marker counts as straight ahead and gets its name shown. */
	private static final double NAME_ANGLE = 3.0;
	/** Head sizes (without the frame) by distance, like the four sizes of the vanilla dot. */
	private static final int[] HEAD_SIZES = {7, 6, 5, 4};
	private static final float NEAR = 128f;
	private static final float FAR = 332f;
	/** The label sits above the hearts and armour. */
	private static final int LABEL_OFFSET = 31;
	private static final float LABEL_SCALE = 0.75f;
	private static final Identifier ARROW_UP = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
	private static final Identifier ARROW_DOWN = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");
	private static final UUID WAYPOINT_ID = new UUID(0, 0);

	private static Component label;
	private static double labelAngle;
	private static int labelX;

	private LocatorBarMarkers() {
	}

	private static MapConfig config() {
		return MapFeature.INSTANCE.config();
	}

	/** Whether the map has waypoints for the locator bar, so it is shown even without other players. */
	public static boolean hasWaypoints() {
		Minecraft mc = Minecraft.getInstance();
		return !waypoints(mc).isEmpty();
	}

	private static List<Waypoint> waypoints(Minecraft mc) {
		MapConfig c = config();
		if (!MapFeature.INSTANCE.isEnabled() || !c.waypoints || !c.locatorWaypoints || mc.level == null
				|| !MapFeature.INSTANCE.waypoints().loaded()) {
			return List.of();
		}
		return MapFeature.INSTANCE.waypoints().visible(MapFeature.dimensionId(mc.level)).stream().filter(w -> w.locatorBar).toList();
	}

	/** Start of a frame of the locator bar. */
	public static void begin() {
		label = null;
		labelAngle = NAME_ANGLE;
	}

	private static int center(GuiGraphicsExtractor g) {
		return Mth.ceil((g.guiWidth() - DOT) / 2f);
	}

	private static int offset(double angle) {
		return Mth.floor(angle * BAR_PIXELS / 2.0 / VISIBLE_ANGLE);
	}

	private static void candidate(double angle, Component name, double distance, int x) {
		if (config().locatorNames && name != null && Math.abs(angle) < labelAngle) {
			labelAngle = Math.abs(angle);
			label = name.copy().append(" · " + MapFeature.formatDistance(distance));
			labelX = x;
		}
	}

	/**
	 * Draws a player of the vanilla bar as its head instead of the dot. Returns false to leave the waypoint to
	 * vanilla (heads off, or not a player).
	 */
	public static boolean drawTracked(GuiGraphicsExtractor g, int top, Entity camera, Level level, PartialTickSupplier ticks,
			TrackedWaypoint waypoint) {
		Optional<UUID> uuid = waypoint.id().left();
		if (!MapFeature.INSTANCE.isEnabled() || uuid.isEmpty() || uuid.get().equals(camera.getUUID())) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		double angle = waypoint.yawAngleToCamera(level, mc.gameRenderer.mainCamera(), ticks);
		if (angle <= -VISIBLE_ANGLE || angle > VISIBLE_ANGLE) {
			return false;
		}
		ClientPacketListener connection = mc.getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid.get());
		float distance = Mth.sqrt((float) waypoint.distanceSquared(camera));
		int x = center(g) + offset(angle);
		candidate(angle, info == null ? null : Component.literal(info.getProfile().name()), distance, x + DOT / 2);
		if (!config().locatorHeads) {
			return false;
		}
		int color = waypoint.icon().color.map(c -> c | 0xFF000000).orElseGet(() -> ARGB.setBrightness(ARGB.color(255, uuid.get().hashCode()), 0.9f));
		PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid.get());
		EntityHeads.drawFace(g, skin, x + DOT / 2, top + 2, headSize(distance), color, 1f);
		drawPitchArrow(g, waypoint.pitchDirectionToCamera(level, mc.gameRenderer, ticks), x, top);
		return true;
	}

	private static int headSize(float distance) {
		if (distance < NEAR) {
			return HEAD_SIZES[0];
		}
		if (distance >= FAR) {
			return HEAD_SIZES[HEAD_SIZES.length - 1];
		}
		int middle = HEAD_SIZES.length - 2;
		return HEAD_SIZES[1 + Math.min(middle - 1, (int) ((distance - NEAR) / (FAR - NEAR) * middle))];
	}

	/** Vanilla's arrow above or below a marker that is above or below the view. */
	private static void drawPitchArrow(GuiGraphicsExtractor g, TrackedWaypoint.PitchDirection pitch, int x, int top) {
		if (pitch == TrackedWaypoint.PitchDirection.NONE) {
			return;
		}
		boolean down = pitch == TrackedWaypoint.PitchDirection.DOWN;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, down ? ARROW_DOWN : ARROW_UP, x + 1, top + (down ? 6 : -6), 7, 5);
	}

	/** The map's waypoints as small pins (skull for deaths, bed for the respawn point), after vanilla's markers. */
	public static void drawWaypoints(GuiGraphicsExtractor g, int top, Entity camera, Level level, PartialTickSupplier ticks) {
		Minecraft mc = Minecraft.getInstance();
		for (Waypoint waypoint : waypoints(mc)) {
			double y = waypoint.y == null ? camera.getEyeY() : waypoint.y;
			// A vanilla waypoint at the same block gives the same angle, distance and arrows as the players.
			TrackedWaypoint tracked = TrackedWaypoint.setPosition(WAYPOINT_ID, new Icon(), BlockPos.containing(waypoint.x, y, waypoint.z));
			double angle = tracked.yawAngleToCamera(level, mc.gameRenderer.mainCamera(), ticks);
			if (angle <= -VISIBLE_ANGLE || angle > VISIBLE_ANGLE) {
				continue;
			}
			int x = center(g) + offset(angle) + DOT / 2;
			ItemStack icon = waypoint.iconStack();
			if (icon.isEmpty() && waypoint.death) {
				icon = new ItemStack(Items.SKELETON_SKULL);
			}
			MapMarkers.waypoint(g, mc.font, waypoint, icon, x + 0.5f, top + 6, 1f, true, 1f);
			if (waypoint.y != null) {
				drawPitchArrow(g, tracked.pitchDirectionToCamera(level, mc.gameRenderer, ticks), x - DOT / 2, top);
			}
			candidate(angle, Component.literal(waypoint.name).withColor(waypoint.rgb()),
					Math.sqrt(tracked.distanceSquared(camera)), x);
		}
	}

	/** The name and distance of the marker straight ahead, above the hearts. */
	public static void drawLabel(GuiGraphicsExtractor g, int top) {
		if (label != null) {
			MapUi.label(g, Minecraft.getInstance().font, label, labelX, top - LABEL_OFFSET, 0xFFFFFFFF, LABEL_SCALE, MapUi.PANEL);
		}
	}
}
