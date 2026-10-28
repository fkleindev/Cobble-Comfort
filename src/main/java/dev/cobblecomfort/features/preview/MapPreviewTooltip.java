package dev.cobblecomfort.features.preview;

import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.mixin.MapRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A filled map's picture in its tooltip, with its markers, the player's position and its scale and location. */
final class MapPreviewTooltip implements ClientTooltipComponent {
	private static final int SMALL = 64;
	private static final int LARGE = 128;
	private static final int BORDER = 2;
	private static final int GAP = 2;
	private static final int FRAME_COLOR = 0xFF5A4630;
	private static final int PAPER_COLOR = 0xFFD8C9A0;
	private static final int INFO_COLOR = 0xFFAAAAAA;
	private static final String[] DIRECTIONS = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

	/** @param center where the map is centred, or null when not known yet */
	record Data(MapId id, MapItemSavedData map, MapCenters.Center center) implements TooltipComponent {
	}

	private final ItemPreviewFeature feature;
	private final Data data;
	private final int size;
	private final List<Component> lines = new ArrayList<>();
	private final MapRenderState state = new MapRenderState();

	MapPreviewTooltip(ItemPreviewFeature feature, Data data) {
		this.feature = feature;
		this.data = data;
		size = feature.large() ? LARGE : SMALL;
		if (feature.config().mapInfo) {
			addInfo();
		}
		Component hint = feature.keyHint();
		if (hint != null) {
			lines.add(hint);
		}
	}

	private void addInfo() {
		MapItemSavedData map = data.map();
		Component scale = Component.translatable("cobblecomfort.preview.map_scale", 1 << map.scale);
		lines.add(map.locked ? scale.copy().append(" · ").append(Component.translatable("cobblecomfort.preview.map_locked")) : scale);
		MapCenters.Center center = data.center();
		if (center == null) {
			return;
		}
		lines.add(Component.translatable("cobblecomfort.preview.map_center", center.x(), center.z()));
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !center.dimension().equals(MapFeature.dimensionId(mc.level))) {
			return;
		}
		double dx = center.x() - player.getX();
		double dz = center.z() - player.getZ();
		int half = 64 << map.scale;
		if (Math.abs(dx) <= half && Math.abs(dz) <= half) {
			lines.add(Component.translatable("cobblecomfort.preview.map_here"));
		} else {
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			int index = Math.floorMod(Math.round(yaw / 45f), DIRECTIONS.length);
			lines.add(Component.translatable("cobblecomfort.preview.map_distance", (int) Math.sqrt(dx * dx + dz * dz),
					Component.translatable("cobblecomfort.info.dir." + DIRECTIONS[index])));
		}
	}

	@Override
	public int getWidth(Font font) {
		int width = size + BORDER * 2;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return size + BORDER * 2 + GAP + lines.size() * (font.lineHeight + 1);
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		g.fill(x, y, x + size + BORDER * 2, y + size + BORDER * 2, FRAME_COLOR);
		g.fill(x + 1, y + 1, x + size + BORDER * 2 - 1, y + size + BORDER * 2 - 1, PAPER_COLOR);
		mc.getMapRenderer().extractRenderState(data.id(), data.map(), state);
		if (!feature.config().mapMarkers) {
			state.decorations.clear();
		} else {
			addOwnMarker(mc);
			// The GUI map draws only markers shown in item frames; the preview shows all, like a map in hand.
			state.decorations.forEach(decoration -> decoration.renderOnFrame = true);
		}
		g.pose().pushMatrix();
		g.pose().translate(x + BORDER, y + BORDER);
		g.pose().scale(size / 128f, size / 128f);
		g.map(state);
		g.pose().popMatrix();
		int lineY = y + size + BORDER * 2 + GAP;
		for (Component line : lines) {
			g.text(font, line, x, lineY, INFO_COLOR, true);
			lineY += font.lineHeight + 1;
		}
	}

	/** The player's own marker on a map that is not carried (vanilla only tracks carried maps and ones in frames). */
	private void addOwnMarker(Minecraft mc) {
		MapCenters.Center center = data.center();
		LocalPlayer player = mc.player;
		if (center == null || player == null || mc.level == null || !center.dimension().equals(MapFeature.dimensionId(mc.level))) {
			return;
		}
		for (MapDecoration decoration : data.map().getDecorations()) {
			if (decoration.type().is(MapDecorationTypes.PLAYER)) {
				return;
			}
		}
		int scale = 1 << data.map().scale;
		float mx = (float) (player.getX() - center.x()) / scale;
		float mz = (float) (player.getZ() - center.z()) / scale;
		if (Math.abs(mx) >= 63 || Math.abs(mz) >= 63) {
			return;
		}
		float yaw = Mth.wrapDegrees(player.getYRot());
		byte rot = (byte) ((yaw < 0 ? yaw - 8 : yaw + 8) * 16 / 360);
		MapDecoration own = new MapDecoration(MapDecorationTypes.PLAYER, (byte) (mx * 2 + 0.5f), (byte) (mz * 2 + 0.5f), rot, Optional.empty());
		state.decorations.add(((MapRendererInvoker) mc.getMapRenderer()).cobblecomfort$extractDecoration(own));
	}
}
