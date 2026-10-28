package dev.cobblecomfort.features.map.render;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.data.MovementTrail;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.Collection;
import java.util.UUID;

/**
 * Markers shared by the minimap and the full map: waypoint boxes, round icon badges, the player arrow with its view
 * cone and mob faces or dots. The sprites are drawn at twice the GUI resolution, tinted where they have a colour.
 */
public final class MapMarkers {
	/** Colour of the player's trail. */
	public static final int TRAIL = 0xFF7FD4FF;
	public static final int HOSTILE_DOT = 0xFFFF5050;
	public static final int PASSIVE_DOT = 0xFF6CE06C;
	/** Waypoint marker box height, tip height (GUI pixels) and text scale; normal and small (minimap). */
	private static final int WAYPOINT_H = 11;
	private static final int WAYPOINT_TIP = 3;
	private static final float WAYPOINT_TEXT = 0.75f;
	private static final int WAYPOINT_SMALL_H = 8;
	private static final int WAYPOINT_SMALL_TIP = 2;
	private static final float WAYPOINT_SMALL_TEXT = 0.55f;

	private static final Identifier BADGE = sprite("badge");
	private static final Identifier BADGE_SMALL = sprite("badge_small");
	private static final Identifier RING = sprite("ring");
	private static final Identifier RING_SMALL = sprite("ring_small");
	private static final Identifier ARROW = sprite("arrow");
	private static final Identifier ARROW_SMALL = sprite("arrow_small");
	private static final Identifier CONE = sprite("cone");
	private static final Identifier DOT = sprite("dot");

	private MapMarkers() {
	}

	private static Identifier sprite(String name) {
		return CobbleComfortClient.id("map/" + name);
	}

	private static void blit(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, int color) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
	}

	/** A waypoint marker in the waypoint's colour whose tip points at (x, y), with its initials in the box. */
	public static void waypoint(GuiGraphicsExtractor g, Font font, Waypoint waypoint, float x, float y, float alpha, boolean small) {
		waypoint(g, font, waypoint, x, y, alpha, small, 1f);
	}

	/**
	 * A waypoint marker in the style of Xaero's: a box in the waypoint's colour, as wide as its initials, with a
	 * small tip below pointing at (x, y). Drawn at {@code scale} around the tip (smaller for far waypoints).
	 */
	public static void waypoint(GuiGraphicsExtractor g, Font font, Waypoint waypoint, float x, float y, float alpha, boolean small, float scale) {
		Component text = Component.literal(waypoint.initials.isEmpty() ? "?" : waypoint.initials);
		int rgb = waypoint.rgb();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x), Math.round(y));
		pose.scale(scale);
		ItemStack icon = waypoint.iconStack();
		int height = small ? WAYPOINT_SMALL_H : WAYPOINT_H;
		int width = icon.isEmpty() ? waypointWidth(font, text, small) : height;
		int tip = small ? WAYPOINT_SMALL_TIP : WAYPOINT_TIP;
		waypointBox(g, width, height, tip, rgb, alpha, small);
		if (!icon.isEmpty()) {
			// Items cannot be faded; the icon stays opaque on faint (far) markers.
			MapUi.icon(g, icon, 0, -tip - height / 2f, height - 2);
			pose.popMatrix();
			return;
		}
		int brightness = (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
		int textColor = RenderUtil.withAlpha(brightness > 450 ? 0xFF101010 : 0xFFFFFFFF, alpha);
		MapUi.centeredText(g, font, text, 0, -tip - height / 2f, textColor, small ? WAYPOINT_SMALL_TEXT : WAYPOINT_TEXT);
		pose.popMatrix();
	}

	/** The empty waypoint marker centred on (cx, cy), as the waypoint icon in buttons and menus. */
	public static void waypointIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy + (WAYPOINT_SMALL_H + WAYPOINT_SMALL_TIP) / 2);
		waypointBox(g, WAYPOINT_SMALL_H, WAYPOINT_SMALL_H, WAYPOINT_SMALL_TIP, color, (color >>> 24) / 255f, true);
		pose.popMatrix();
	}

	/** Whether (mx, my) is on the marker of a waypoint whose tip is at (x, y). */
	public static boolean waypointHit(Font font, Waypoint waypoint, float x, float y, double mx, double my, boolean small) {
		Component text = Component.literal(waypoint.initials.isEmpty() ? "?" : waypoint.initials);
		int width = waypoint.iconStack().isEmpty() ? waypointWidth(font, text, small) : small ? WAYPOINT_SMALL_H : WAYPOINT_H;
		int half = width / 2 + 1;
		int height = small ? WAYPOINT_SMALL_H + WAYPOINT_SMALL_TIP : WAYPOINT_H + WAYPOINT_TIP;
		return Math.abs(mx - x) <= half && my >= y - height - 1 && my <= y + 1;
	}

	/** Height of a waypoint marker above its tip, for placing text above it. */
	public static int waypointHeight(boolean small) {
		return small ? WAYPOINT_SMALL_H + WAYPOINT_SMALL_TIP : WAYPOINT_H + WAYPOINT_TIP;
	}

	private static int waypointWidth(Font font, Component text, boolean small) {
		float scale = small ? WAYPOINT_SMALL_TEXT : WAYPOINT_TEXT;
		int padding = small ? 4 : 6;
		return Math.max(small ? WAYPOINT_SMALL_H : WAYPOINT_H, (int) Math.ceil((font.width(text) - 1) * scale) + padding);
	}

	/**
	 * The marker shape with the tip at (0, 0), drawn in half GUI pixels: a soft shadow, a dark outline with cut
	 * corners, the colour with a light gloss along the top and a slightly darker bottom edge, and the tip below.
	 */
	private static void waypointBox(GuiGraphicsExtractor g, int width, int height, int tip, int rgb, float alpha, boolean small) {
		int base = RenderUtil.withAlpha(rgb | 0xFF000000, alpha);
		int outline = RenderUtil.withAlpha(RenderUtil.lerpColor(rgb | 0xFF000000, 0xFF000000, 0.55f), alpha);
		int gloss = RenderUtil.withAlpha(RenderUtil.lerpColor(rgb | 0xFF000000, 0xFFFFFFFF, 0.35f), alpha);
		int shade = RenderUtil.withAlpha(RenderUtil.lerpColor(rgb | 0xFF000000, 0xFF000000, 0.18f), alpha);
		int shadow = RenderUtil.withAlpha(0x60000000, alpha);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(0.5f);
		int t = tip * 2;
		int left = -width;
		int right = width;
		int bottom = -t;
		int top = bottom - height * 2;
		// Shadow, one half pixel down and right
		g.fill(left + 1, top + 1, right + 1, bottom + 1, shadow);
		for (int r = 0; r < t; r++) {
			g.fill(-(t - r) + 1, bottom + r + 1, t - r + 1, bottom + r + 2, shadow);
		}
		// Outline with cut corners, then the colour inside
		g.fill(left + 1, top, right - 1, bottom, outline);
		g.fill(left, top + 1, right, bottom - 1, outline);
		for (int r = 0; r < t; r++) {
			g.fill(-(t - r), bottom + r, t - r, bottom + r + 1, outline);
		}
		g.fill(left + 1, top + 1, right - 1, bottom - 1, base);
		g.fill(left + 1, top + 1, right - 1, top + 1 + (small ? 1 : 2), gloss);
		g.fill(left + 1, bottom - 2, right - 1, bottom - 1, shade);
		for (int r = -1; r < t - 1; r++) {
			g.fill(-(t - r - 1), bottom + r, t - r - 1, bottom + r + 1, shade);
		}
		pose.popMatrix();
	}

	/** A round dark badge with an item icon, optionally ringed in {@code ring} (0 = no ring). */
	public static void badge(GuiGraphicsExtractor g, ItemStack icon, float x, float y, boolean small, int ring) {
		int ix = Math.round(x);
		int iy = Math.round(y);
		int size = small ? 11 : 16;
		if (ring != 0) {
			int rs = small ? 14 : 20;
			blit(g, small ? RING_SMALL : RING, ix - rs / 2, iy - rs / 2, rs, rs, ring);
		}
		blit(g, small ? BADGE_SMALL : BADGE, ix - size / 2, iy - size / 2, size, size, 0xFFFFFFFF);
		MapUi.icon(g, icon, ix, iy, small ? 8 : 11);
	}

	/** The player arrow; {@code angle} in radians, 0 = pointing up. The view cone is drawn first if wanted. */
	public static void arrow(GuiGraphicsExtractor g, float x, float y, float angle, int color, boolean cone, boolean small) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate(angle);
		if (cone) {
			int cs = small ? 36 : 48;
			blit(g, CONE, -cs / 2, -cs / 2, cs, cs, RenderUtil.withAlpha(color, 0.7f * ((color >>> 24) / 255f)));
		}
		int size = small ? 10 : 12;
		blit(g, small ? ARROW_SMALL : ARROW, -size / 2, -size / 2, size, size, color);
		pose.popMatrix();
	}

	/** A small round dot with a dark outline. */
	public static void dot(GuiGraphicsExtractor g, float x, float y, int color) {
		blit(g, DOT, Math.round(x) - 3, Math.round(y) - 3, 6, 6, color);
	}

	/** A mob: its face if enabled and known, otherwise a dot in its category colour. Players are framed in their colour. */
	public static void entity(GuiGraphicsExtractor g, Entity entity, float x, float y, int color, boolean heads, int size, float alpha) {
		int frame = entity instanceof Player ? color : darken(color);
		if (heads && EntityHeads.draw(g, entity, Math.round(x), Math.round(y), size, frame, alpha)) {
			return;
		}
		dot(g, x, y, RenderUtil.withAlpha(color, alpha));
	}

	/**
	 * A player known only from position sharing (not loaded nearby): its face from the tab list skin, framed in its
	 * colour, or a dot in its colour.
	 */
	public static void remotePlayer(GuiGraphicsExtractor g, UUID uuid, float x, float y, boolean heads, int size, float alpha) {
		int color = playerColor(uuid);
		if (heads) {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
			PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid);
			EntityHeads.drawFace(g, skin, Math.round(x), Math.round(y), size, color, alpha);
			return;
		}
		dot(g, x, y, RenderUtil.withAlpha(color, alpha));
	}

	private static int darken(int color) {
		return RenderUtil.lerpColor(color, 0xFF000000, 0.35f);
	}

	public static int playerColor(Player player) {
		return playerColor(player.getUUID());
	}

	/** A player's colour in the locator bar: the colour the server gave its waypoint, otherwise one from its UUID. */
	public static int playerColor(UUID uuid) {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection != null && mc.player != null) {
			int[] found = {0};
			connection.getWaypointManager().forEachWaypoint(mc.player, waypoint -> {
				if (found[0] == 0 && waypoint.id().left().filter(uuid::equals).isPresent()) {
					found[0] = waypoint.icon().color.map(c -> c | 0xFF000000).orElse(0);
				}
			});
			if (found[0] != 0) {
				return found[0];
			}
		}
		return ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f);
	}

	/** Gives the screen position of a world position, or null if it is not on the map. */
	@FunctionalInterface
	public interface Projector {
		float[] project(double x, double z);
	}

	/**
	 * The player's trail as a line, older parts fainter, ending at the player's current position (unless playerX is
	 * NaN). Points closer than a few pixels on screen are merged so a long trail stays cheap when zoomed out.
	 */
	public static void trail(GuiGraphicsExtractor g, Collection<MovementTrail.Point> points, double playerX, double playerZ,
			Projector projector, float width) {
		int count = points.size();
		if (count == 0) {
			return;
		}
		float[] previous = null;
		int index = 0;
		for (MovementTrail.Point point : points) {
			index++;
			float[] s = projector.project(point.x(), point.z());
			if (point.start() || s == null || previous == null) {
				previous = s;
				continue;
			}
			float dx = s[0] - previous[0];
			float dy = s[1] - previous[1];
			if (dx * dx + dy * dy < 4f && index < count) {
				continue;
			}
			segment(g, previous, s, RenderUtil.withAlpha(TRAIL, 0.2f + 0.6f * index / count), width);
			previous = s;
		}
		float[] here = Double.isNaN(playerX) ? null : projector.project(playerX, playerZ);
		if (previous != null && here != null) {
			segment(g, previous, here, RenderUtil.withAlpha(TRAIL, 0.8f), width);
		}
	}

	/** A straight line of the given width from a to b. */
	private static void segment(GuiGraphicsExtractor g, float[] a, float[] b, int color, float width) {
		float dx = b[0] - a[0];
		float dy = b[1] - a[1];
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.01f) {
			return;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(a[0], a[1]);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.scale(length, width / 2f);
		g.fill(0, -1, 1, 1, color);
		pose.popMatrix();
	}

	/** Dot colour of an entity, or 0 if it should not be shown with these settings. */
	public static int entityColor(Entity entity, boolean players, boolean hostiles, boolean passives) {
		if (entity instanceof Player player) {
			return players ? playerColor(player) : 0;
		}
		if (entity instanceof Enemy) {
			return hostiles ? HOSTILE_DOT : 0;
		}
		if (entity instanceof Mob) {
			return passives ? PASSIVE_DOT : 0;
		}
		return 0;
	}
}
