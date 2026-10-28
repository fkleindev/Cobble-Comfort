package dev.cobblecomfort.features.map.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.render.MapMarkers;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.map.render.MapUi.Dim;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import dev.cobblecomfort.features.map.waypoint.WaypointStore;
import dev.cobblecomfort.features.map.waypoint.XaeroChat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** All waypoints of this world, grouped by set: show/hide sets, toggle, edit, share and delete waypoints. */
public class WaypointListScreen extends Screen {
	private static final int PANEL_WIDTH = 330;
	private static final int ROW = 22;
	private static final int HEADER_ROW = 16;
	private static final int ACTION = 14;
	private static final int ACTIONS = 4;
	private static final String[] ICONS = {"◉", "✎", "➤", "✕"};
	private static final String[] TIPS = {"cobblecomfort.waypoint.toggle", "cobblecomfort.waypoint.edit",
			"cobblecomfort.waypoint.share", "cobblecomfort.waypoint.delete"};

	private final Screen parent;
	private int left, top, panelHeight, listTop, listHeight;
	private final SmoothScroll scroll = new SmoothScroll(SmoothScroll.Kind.MOD_SCREENS);

	/** One line of the list: a set header or a waypoint. */
	private record Line(String set, Waypoint waypoint, int count) {
		int height() {
			return waypoint == null ? HEADER_ROW : ROW;
		}
	}

	public WaypointListScreen(Screen parent) {
		super(Component.translatable("cobblecomfort.waypoint.list"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelHeight = Math.min(300, height - 16);
		left = (width - PANEL_WIDTH) / 2;
		top = (height - panelHeight) / 2;
		listTop = top + 26;
		listHeight = panelHeight - 26 - 32;
		int by = top + panelHeight - 26;
		addRenderableWidget(new FlatButton(left + 10, by, 130, 18, Component.translatable("cobblecomfort.waypoint.new_here"), MapUi.ACCENT, () -> {
			LocalPlayer player = minecraft.player;
			if (player != null && minecraft.level != null) {
				Waypoint waypoint = new Waypoint("", player.getBlockX(), player.getBlockY(), player.getBlockZ(),
						MapFeature.dimensionId(minecraft.level));
				minecraft.gui.setScreen(new WaypointEditScreen(this, waypoint, true));
			}
		}));
		addRenderableWidget(new FlatButton(left + PANEL_WIDTH - 100, by, 90, 18, Component.translatable("gui.done"), 0xFF8A90A0, this::onClose));
	}

	private List<Line> lines() {
		WaypointStore store = MapFeature.INSTANCE.waypoints();
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<String, Boolean> set : store.sets().entrySet()) {
			List<Waypoint> members = new ArrayList<>(store.all().stream().filter(w -> w.set.equals(set.getKey())).toList());
			if (members.isEmpty()) {
				continue;
			}
			members.sort(Comparator.comparing(w -> w.name.toLowerCase()));
			lines.add(new Line(set.getKey(), null, members.size()));
			for (Waypoint waypoint : members) {
				lines.add(new Line(set.getKey(), waypoint, 0));
			}
		}
		return lines;
	}

	private int contentHeight(List<Line> lines) {
		int h = 0;
		for (Line line : lines) {
			h += line.height();
		}
		return h;
	}

	private int actionsLeft() {
		return left + PANEL_WIDTH - 12 - ACTIONS * (ACTION + 3);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		MapUi.panel(g, left, top, left + PANEL_WIDTH, top + panelHeight, MapUi.PANEL_SOLID);
		g.text(font, title, left + 10, top + 9, MapUi.TEXT, true);
		List<Line> lines = lines();
		int total = (int) lines.stream().filter(l -> l.waypoint() != null).count();
		String count = String.valueOf(total);
		int cw = font.width(count) + 8;
		int cx = left + 14 + font.width(title);
		MapUi.pill(g, cx, top + 7, cx + cw, top + 18, 0x30FFFFFF);
		g.text(font, count, cx + 4, top + 9, MapUi.TEXT_DIM, false);
		g.fill(left + 8, listTop - 4, left + PANEL_WIDTH - 8, listTop - 3, 0x18FFFFFF);
		int content = contentHeight(lines);
		scroll.update(Math.max(0, content - listHeight));
		if (lines.isEmpty()) {
			g.centeredText(font, Component.translatable("cobblecomfort.waypoint.none"), left + PANEL_WIDTH / 2, listTop + 20, MapUi.TEXT_FAINT);
		}
		LocalPlayer player = minecraft.player;
		WaypointStore store = MapFeature.INSTANCE.waypoints();
		g.enableScissor(left + 4, listTop, left + PANEL_WIDTH - 4, listTop + listHeight);
		int y = listTop - (int) scroll.value();
		String tooltip = null;
		for (Line line : lines) {
			int h = line.height();
			if (y + h < listTop || y > listTop + listHeight) {
				y += h;
				continue;
			}
			boolean inList = mouseY >= listTop && mouseY < listTop + listHeight;
			boolean hover = inList && mouseX >= left + 6 && mouseX < left + PANEL_WIDTH - 10 && mouseY >= y && mouseY < y + h;
			if (line.waypoint() == null) {
				boolean shown = store.sets().getOrDefault(line.set(), true);
				int color = hover ? MapUi.TEXT : shown ? 0xFFE8C860 : MapUi.TEXT_FAINT;
				g.text(font, (shown ? "▾ " : "▸ ") + WaypointStore.displayName(line.set()), left + 10, y + 5, color, false);
				MapUi.smallText(g, font, Component.literal(String.valueOf(line.count())), left + 14 + font.width((shown ? "▾ " : "▸ ") + WaypointStore.displayName(line.set())),
						y + 6, MapUi.TEXT_FAINT, 0.75f);
				y += h;
				continue;
			}
			Waypoint waypoint = line.waypoint();
			if (hover) {
				RenderUtil.roundedFill(g, left + 6, y + 1, left + PANEL_WIDTH - 10, y + h - 1, 3, MapUi.HOVER);
			}
			float alpha = waypoint.enabled ? 1f : 0.4f;
			MapMarkers.waypoint(g, font, waypoint, left + 22, y + 19, alpha, false);
			g.text(font, waypoint.name, left + 34, y + 3, waypoint.enabled ? MapUi.TEXT : MapUi.TEXT_FAINT, false);
			Dim dim = Dim.of(waypoint.dimension);
			Component info = Component.literal(waypoint.x + ", " + (waypoint.y == null ? "~" : waypoint.y) + ", " + waypoint.z);
			int ix = left + 34 + MapUi.smallText(g, font, info, left + 34, y + 13, MapUi.TEXT_DIM, 0.75f) + 5;
			Component dimLabel = dim != null ? dim.label() : Component.literal(waypoint.dimension);
			int dw = Math.round(font.width(dimLabel) * 0.75f) + 6;
			int accent = Dim.accentOf(waypoint.dimension);
			MapUi.pill(g, ix, y + 12, ix + dw, y + 20, RenderUtil.withAlpha(accent, 0.3f));
			MapUi.smallText(g, font, dimLabel, ix + 3, y + 13, RenderUtil.lerpColor(accent, 0xFFFFFFFF, 0.5f), 0.75f);
			if (player != null && minecraft.level != null && waypoint.dimension.equals(MapFeature.dimensionId(minecraft.level))) {
				Component distance = Component.literal(MapFeature.formatDistance(Math.sqrt(player.distanceToSqr(waypoint.x, waypoint.yOr(player.getY()), waypoint.z))));
				MapUi.smallText(g, font, distance, ix + dw + 5, y + 13, MapUi.TEXT_FAINT, 0.75f);
			}
			if (hover || !waypoint.enabled) {
				int ax = actionsLeft();
				for (int i = 0; i < ACTIONS; i++) {
					int bx = ax + i * (ACTION + 3);
					int by = y + (h - ACTION) / 2;
					boolean over = mouseX >= bx && mouseX < bx + ACTION && mouseY >= by && mouseY < by + ACTION;
					if (!hover && i != 0) {
						continue;
					}
					int face = over ? (i == 3 ? RenderUtil.withAlpha(MapUi.DANGER, 0.6f) : 0x40FFFFFF) : 0x18FFFFFF;
					RenderUtil.roundedFill(g, bx, by, bx + ACTION, by + ACTION, 3, face);
					String icon = i == 0 && !waypoint.enabled ? "○" : ICONS[i];
					g.centeredText(font, icon, bx + ACTION / 2, by + 3, over ? MapUi.TEXT : MapUi.TEXT_DIM);
					if (over) {
						tooltip = TIPS[i];
					}
				}
			}
			y += h;
		}
		g.disableScissor();
		if (content > listHeight) {
			int barH = Math.max(16, listHeight * listHeight / content);
			int barY = listTop + (int) ((listHeight - barH) * (scroll.value() / (content - listHeight)));
			int barX = left + PANEL_WIDTH - 7;
			RenderUtil.roundedFill(g, barX, listTop, barX + 3, listTop + listHeight, 1, 0x14FFFFFF);
			RenderUtil.roundedFill(g, barX, barY, barX + 3, barY + barH, 1, 0x60FFFFFF);
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (tooltip != null) {
			g.setTooltipForNextFrame(font, Component.translatable(tooltip), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || event.y() < listTop || event.y() >= listTop + listHeight) {
			return false;
		}
		int y = listTop - (int) scroll.value();
		WaypointStore store = MapFeature.INSTANCE.waypoints();
		for (Line line : lines()) {
			int h = line.height();
			if (event.y() >= y && event.y() < y + h) {
				if (line.waypoint() == null) {
					store.sets().put(line.set(), !store.sets().getOrDefault(line.set(), true));
					store.save();
					return true;
				}
				return clickWaypoint(line.waypoint(), y, h, event, doubleClick);
			}
			y += h;
		}
		return false;
	}

	private boolean clickWaypoint(Waypoint waypoint, int y, int h, MouseButtonEvent event, boolean doubleClick) {
		WaypointStore store = MapFeature.INSTANCE.waypoints();
		int ax = actionsLeft();
		int by = y + (h - ACTION) / 2;
		for (int i = 0; i < ACTIONS; i++) {
			int bx = ax + i * (ACTION + 3);
			if (event.x() >= bx && event.x() < bx + ACTION && event.y() >= by && event.y() < by + ACTION) {
				switch (i) {
					case 0 -> {
						waypoint.enabled = !waypoint.enabled;
						store.save();
					}
					case 1 -> minecraft.gui.setScreen(new WaypointEditScreen(this, waypoint, false));
					case 2 -> XaeroChat.share(waypoint);
					default -> store.remove(waypoint);
				}
				return true;
			}
		}
		if (doubleClick) {
			minecraft.gui.setScreen(new WaypointEditScreen(this, waypoint, false));
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll.scroll(-(scrollY * ROW));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
