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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates or edits one waypoint: name, initials, colour, position and set, with a live preview of the pin. Existing
 * sets are offered as chips below the set field, and typing completes a set name (Tab takes it).
 */
public class WaypointEditScreen extends Screen {
	private static final int PANEL_WIDTH = 250;
	private static final int PANEL_HEIGHT = 214;
	private static final int CHIP_HEIGHT = 11;
	private static final int CHIP_GAP = 3;
	private static final int CHIP_ROWS = 2;
	private static final float CHIP_TEXT = 0.75f;
	private static final int FIELD_HEIGHT = 16;
	private static final int ROW = 28;
	private static final int LABEL_WIDTH = 64;
	private static final int COLORS = 16;
	private static final int SWATCH = 12;

	private final Screen parent;
	private final Waypoint waypoint;
	private final boolean isNew;
	private final List<EditBox> fields = new ArrayList<>();
	private EditBox name;
	private EditBox initials;
	private EditBox x, y, z;
	private EditBox set;
	private int color;
	private int left, top, panelHeight;
	private final List<Chip> chips = new ArrayList<>();

	/** A clickable existing set below the set field. */
	private record Chip(String set, String label, int x0, int y0, int x1, int y1) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public WaypointEditScreen(Screen parent, Waypoint waypoint, boolean isNew) {
		super(Component.translatable(isNew ? "cobblecomfort.waypoint.new" : "cobblecomfort.waypoint.edit"));
		this.parent = parent;
		this.waypoint = waypoint;
		this.isNew = isNew;
		this.color = waypoint.color;
	}

	private int fieldX() {
		return left + 10 + LABEL_WIDTH;
	}

	private int fieldWidth() {
		return PANEL_WIDTH - 20 - LABEL_WIDTH;
	}

	private int rowY(int row) {
		return top + 30 + row * ROW;
	}

	@Override
	protected void init() {
		int chipRows = layoutChips(0, 0);
		panelHeight = PANEL_HEIGHT + chipRows * (CHIP_HEIGHT + CHIP_GAP);
		left = (width - PANEL_WIDTH) / 2;
		top = (height - panelHeight) / 2;
		layoutChips(fieldX(), rowY(4) + FIELD_HEIGHT + CHIP_GAP);
		fields.clear();
		int fx = fieldX();
		int fw = fieldWidth();
		name = field(fx, rowY(0), fw, waypoint.name, 32);
		name.setResponder(value -> initials.setValue(Waypoint.initialsOf(value)));
		initials = field(fx, rowY(1), 30, waypoint.initials, 2);
		int cw = (fw - 8) / 3;
		x = field(fx, rowY(3), cw, String.valueOf(waypoint.x), 9);
		y = field(fx + cw + 4, rowY(3), cw, waypoint.y == null ? "~" : String.valueOf(waypoint.y), 9);
		z = field(fx + 2 * (cw + 4), rowY(3), cw, String.valueOf(waypoint.z), 9);
		set = field(fx, rowY(4), fw, WaypointStore.displayName(waypoint.set), 24);
		set.setHint(Component.literal(WaypointStore.displayName(WaypointStore.DEFAULT_SET)).withColor(MapUi.TEXT_FAINT));
		int by = top + panelHeight - 28;
		int bw = (PANEL_WIDTH - 20 - 12) / 4;
		addRenderableWidget(new FlatButton(left + 10, by, bw, 18, Component.translatable("gui.done"), MapUi.ACCENT, this::save));
		addRenderableWidget(new FlatButton(left + 10 + (bw + 4), by, bw, 18, Component.translatable("cobblecomfort.waypoint.share"),
				0xFF8A90A0, this::share));
		FlatButton delete = new FlatButton(left + 10 + 2 * (bw + 4), by, bw, 18, Component.translatable("cobblecomfort.waypoint.delete"),
				MapUi.DANGER, this::delete);
		delete.active = !isNew;
		addRenderableWidget(delete);
		addRenderableWidget(new FlatButton(left + 10 + 3 * (bw + 4), by, bw, 18, Component.translatable("gui.cancel"), 0xFF8A90A0, this::onClose));
		setInitialFocus(name);
		if (isNew && waypoint.name.isEmpty()) {
			name.setValue(Component.translatable("cobblecomfort.waypoint.default_name").getString());
		}
	}

	/** Places the set chips in rows from (x, y), as many as fit in {@link #CHIP_ROWS}; returns the rows used. */
	private int layoutChips(int x, int y) {
		chips.clear();
		int maxX = x + fieldWidth();
		int cx = x;
		int row = 0;
		for (String name : MapFeature.INSTANCE.waypoints().choosableSets()) {
			String label = WaypointStore.displayName(name);
			int w = Math.round((font.width(label) - 1) * CHIP_TEXT) + 10;
			if (cx + w > maxX && cx > x) {
				if (++row >= CHIP_ROWS) {
					break;
				}
				cx = x;
			}
			int cy = y + row * (CHIP_HEIGHT + CHIP_GAP);
			chips.add(new Chip(name, label, cx, cy, Math.min(cx + w, maxX), cy + CHIP_HEIGHT));
			cx += w + CHIP_GAP;
		}
		return chips.isEmpty() ? 0 : Math.min(row + 1, CHIP_ROWS);
	}

	/** Shows the rest of a matching existing set name in grey after the typed text, while the field is focused. */
	private void suggestSet(String value) {
		String completion = MapFeature.INSTANCE.waypoints().completion(value);
		set.setSuggestion(completion == null || !set.isFocused() ? null : completion.substring(value.length()));
	}

	/** A borderless text field; its rounded background is drawn by this screen. */
	private EditBox field(int fx, int fy, int fw, String value, int max) {
		EditBox box = new EditBox(font, fx + 5, fy + 4, fw - 10, FIELD_HEIGHT - 6, Component.empty());
		box.setBordered(false);
		box.setMaxLength(max);
		box.setValue(value);
		box.setTextColor(MapUi.TEXT);
		addRenderableWidget(box);
		fields.add(box);
		return box;
	}

	private void apply() {
		waypoint.name = name.getValue().isBlank() ? "?" : name.getValue().trim();
		waypoint.initials = initials.getValue().isBlank() ? Waypoint.initialsOf(waypoint.name) : initials.getValue().trim();
		waypoint.x = parse(x.getValue(), waypoint.x);
		waypoint.z = parse(z.getValue(), waypoint.z);
		String yText = y.getValue().trim();
		waypoint.y = yText.isEmpty() || yText.equals("~") ? null : parse(yText, waypoint.y == null ? 64 : waypoint.y);
		waypoint.color = color;
		waypoint.set = MapFeature.INSTANCE.waypoints().setFor(set.getValue());
	}

	private static int parse(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void save() {
		apply();
		if (isNew) {
			MapFeature.INSTANCE.waypoints().add(waypoint);
		} else {
			MapFeature.INSTANCE.waypoints().save();
		}
		onClose();
	}

	private void share() {
		apply();
		XaeroChat.share(waypoint);
		onClose();
	}

	private void delete() {
		MapFeature.INSTANCE.waypoints().remove(waypoint);
		onClose();
	}

	private static int rgbOf(int index) {
		Waypoint sample = new Waypoint();
		sample.color = index;
		return sample.rgb();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		MapUi.panel(g, left, top, left + PANEL_WIDTH, top + panelHeight, MapUi.PANEL_SOLID);
		g.text(font, title, left + 10, top + 10, MapUi.TEXT, true);
		// Live preview of the pin, and the dimension it belongs to.
		Waypoint preview = new Waypoint();
		preview.color = color;
		preview.initials = initials.getValue().isBlank() ? Waypoint.initialsOf(name.getValue()) : initials.getValue();
		MapMarkers.waypoint(g, font, preview, left + PANEL_WIDTH - 18, top + 22, 1f, false);
		Dim dim = Dim.of(waypoint.dimension);
		Component dimLabel = dim != null ? dim.label() : Component.literal(waypoint.dimension);
		int accent = Dim.accentOf(waypoint.dimension);
		int dw = Math.round(font.width(dimLabel) * 0.75f) + 8;
		int dx = left + PANEL_WIDTH - 32 - dw;
		MapUi.pill(g, dx, top + 9, dx + dw, top + 19, RenderUtil.withAlpha(accent, 0.3f));
		MapUi.smallText(g, font, dimLabel, dx + 4, top + 11, RenderUtil.lerpColor(accent, 0xFFFFFFFF, 0.5f), 0.75f);
		g.fill(left + 8, top + 25, left + PANEL_WIDTH - 8, top + 26, 0x18FFFFFF);
		label(g, "cobblecomfort.waypoint.name", 0);
		label(g, "cobblecomfort.waypoint.initials", 1);
		label(g, "cobblecomfort.waypoint.color", 2);
		label(g, "cobblecomfort.waypoint.position", 3);
		label(g, "cobblecomfort.waypoint.set", 4);
		for (EditBox box : fields) {
			int x0 = box.getX() - 5;
			int y0 = box.getY() - 4;
			RenderUtil.roundedFill(g, x0, y0, x0 + box.getWidth() + 10, y0 + FIELD_HEIGHT, 3, MapUi.FIELD);
			int edge = box.isFocused() ? MapUi.ACCENT : 0x30FFFFFF;
			RenderUtil.roundedOutline(g, x0, y0, x0 + box.getWidth() + 10, y0 + FIELD_HEIGHT, 3, edge, edge);
		}
		drawPalette(g, mouseX, mouseY);
		drawChips(g, mouseX, mouseY);
		suggestSet(set.getValue());
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	/** Two rows of colour swatches; the chosen one is ringed. */
	private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int px = fieldX();
		int py = rowY(2) - 4;
		for (int i = 0; i < COLORS; i++) {
			int sx = px + (i % 8) * (SWATCH + 3);
			int sy = py + (i / 8) * (SWATCH + 2);
			boolean hovered = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
			if (i == color) {
				RenderUtil.roundedFill(g, sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, 4, 0xFFFFFFFF);
			} else if (hovered) {
				RenderUtil.roundedFill(g, sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 3, 0x80FFFFFF);
			}
			RenderUtil.roundedFill(g, sx, sy, sx + SWATCH, sy + SWATCH, 3, rgbOf(i));
		}
	}

	/** The existing sets as pills; the one the typed name belongs to is tinted in the accent colour. */
	private void drawChips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		String current = MapFeature.INSTANCE.waypoints().setFor(set.getValue());
		for (Chip chip : chips) {
			boolean selected = chip.set().equals(current);
			boolean hovered = chip.contains(mouseX, mouseY);
			int face = selected ? RenderUtil.withAlpha(MapUi.ACCENT, hovered ? 0.6f : 0.4f) : hovered ? 0x40FFFFFF : 0x1CFFFFFF;
			MapUi.pill(g, chip.x0(), chip.y0(), chip.x1(), chip.y1(), face);
			if (selected) {
				RenderUtil.roundedOutline(g, chip.x0(), chip.y0(), chip.x1(), chip.y1(), CHIP_HEIGHT / 2, MapUi.ACCENT, MapUi.ACCENT);
			}
			MapUi.centeredText(g, font, Component.literal(chip.label()), (chip.x0() + chip.x1()) / 2f, (chip.y0() + chip.y1()) / 2f,
					selected || hovered ? MapUi.TEXT : MapUi.TEXT_DIM, CHIP_TEXT);
		}
	}

	private void label(GuiGraphicsExtractor g, String key, int row) {
		g.text(font, Component.translatable(key), left + 10, rowY(row) + 4, MapUi.TEXT_DIM, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int px = fieldX();
			int py = rowY(2) - 4;
			for (int i = 0; i < COLORS; i++) {
				int sx = px + (i % 8) * (SWATCH + 3);
				int sy = py + (i / 8) * (SWATCH + 2);
				if (event.x() >= sx - 1 && event.x() < sx + SWATCH + 1 && event.y() >= sy - 1 && event.y() < sy + SWATCH + 1) {
					color = i;
					return true;
				}
			}
			for (Chip chip : chips) {
				if (chip.contains(event.x(), event.y())) {
					set.setValue(chip.label());
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_TAB && set.isFocused()) {
			String completion = MapFeature.INSTANCE.waypoints().completion(set.getValue());
			if (completion != null) {
				set.setValue(completion);
				set.moveCursorToEnd(false);
				return true;
			}
		}
		if (event.isConfirmation()) {
			save();
			return true;
		}
		return super.keyPressed(event);
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
