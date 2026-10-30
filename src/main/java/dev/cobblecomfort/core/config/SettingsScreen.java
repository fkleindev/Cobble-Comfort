package dev.cobblecomfort.core.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.ListOptionEntry;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.gui.controllers.ColorController;
import dev.isxander.yacl3.gui.controllers.cycling.ICyclingController;
import dev.isxander.yacl3.gui.controllers.slider.ISliderController;
import dev.isxander.yacl3.gui.controllers.string.IStringController;
import dev.isxander.yacl3.impl.ListOptionEntryImpl;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The mod's settings in the look of its other screens: categories with icons on the left, their options on the right,
 * and a search over everything. It works on the YACL option definitions each feature builds, so those stay the one
 * place options are described; every change is applied at once and saved when the screen closes.
 */
public final class SettingsScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 480;
	private static final int HEADER = 28;
	private static final int SIDEBAR = 140;
	private static final int SECTION_ROW = 16;
	private static final int GROUP_ROW = 18;
	private static final int OPTION_ROW = 20;
	private static final int CONTROL = 124;
	private static final int SLIDER_LABEL = 42;
	private static final int SMALL_BUTTON = 12;
	private static final int DESCRIPTION_WIDTH = 220;
	private static final int ON = 0xFF6CCB5F;
	private static final int OFF = 0xFF5A606C;

	/** A category in the sidebar, with an icon and whether its feature is switched on (null = no indicator). */
	public record Section(Component name, ItemStack icon, ConfigCategory category, BooleanSupplier enabled) {
	}

	private enum Kind {
		HEADER, GROUP, OPTION, LIST_ADD
	}

	/** One line of the option list. {@code list} is set for the entries of a list option and its add line. */
	private record Row(Kind kind, Component label, OptionGroup group, Option<?> option, ListOption<?> list) {
		int height() {
			return switch (kind) {
				case HEADER -> GROUP_ROW + 4;
				case GROUP -> GROUP_ROW;
				case OPTION, LIST_ADD -> OPTION_ROW;
			};
		}
	}

	private final Screen parent;
	private final List<Section> sections;
	private final Runnable save;
	private final Map<OptionGroup, Boolean> open = new HashMap<>();
	private final SmoothScroll contentScroll = new SmoothScroll(SmoothScroll.Kind.MOD_SCREENS);
	private final SmoothScroll sidebarScroll = new SmoothScroll(SmoothScroll.Kind.MOD_SCREENS);
	private int selected;
	private int left, top, panelW, panelH;
	private int listX0, listX1, listTop, listBottom;
	private EditBox search;
	/** The one text field, moved onto whichever text option is being edited. */
	private EditBox editor;
	private Option<?> editing;
	private ISliderController<?> dragging;
	private int dragX0, dragX1;
	private boolean dirty;

	public SettingsScreen(Screen parent, Component title, List<Section> sections, Runnable save) {
		super(title);
		this.parent = parent;
		this.sections = sections;
		this.save = save;
	}

	@Override
	protected void init() {
		panelW = Math.min(MAX_PANEL_WIDTH, width - 16);
		panelH = height - 16;
		left = (width - panelW) / 2;
		top = 8;
		boolean sidebar = sections.size() > 1;
		listX0 = left + (sidebar ? SIDEBAR + 12 : 8);
		listX1 = left + panelW - 8;
		listTop = top + HEADER + 4;
		listBottom = top + panelH - 6;

		String query = search == null ? "" : search.getValue();
		int searchW = Math.min(150, panelW / 3);
		search = new EditBox(font, left + panelW - searchW - 26, top + 10, searchW - 10, 10, Component.translatable("cobblecomfort.settings.search"));
		search.setBordered(false);
		search.setTextColor(MapUi.TEXT);
		search.setHint(Component.translatable("cobblecomfort.settings.search").withColor(MapUi.TEXT_FAINT));
		search.setValue(query);
		search.setResponder(value -> {
			stopEditing();
			contentScroll.set(0);
		});
		search.visible = sidebar;
		addRenderableWidget(search);

		editor = new EditBox(font, 0, 0, 10, 10, Component.empty());
		editor.setBordered(false);
		editor.setTextColor(MapUi.TEXT);
		editor.setMaxLength(256);
		editor.visible = false;
		editor.setResponder(this::typed);
		addRenderableWidget(editor);
		editing = null;
	}

	// ---------------------------------------------------------------- rows

	private boolean searching() {
		return search != null && !search.getValue().isBlank();
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		if (searching()) {
			String query = search.getValue().trim().toLowerCase(Locale.ROOT);
			for (Section section : sections) {
				List<Row> found = new ArrayList<>();
				for (OptionGroup group : section.category().groups()) {
					if (group instanceof ListOption<?> list) {
						if (matches(list, query)) {
							addList(found, list, true);
						}
						continue;
					}
					for (Option<?> option : group.options()) {
						if (matches(option, query)) {
							found.add(new Row(Kind.OPTION, null, group, option, null));
						}
					}
				}
				if (!found.isEmpty()) {
					rows.add(new Row(Kind.HEADER, section.name(), null, null, null));
					rows.addAll(found);
				}
			}
			return rows;
		}
		ConfigCategory category = sections.get(selected).category();
		for (OptionGroup group : category.groups()) {
			if (group instanceof ListOption<?> list) {
				addList(rows, list, false);
				continue;
			}
			if (!group.isRoot()) {
				rows.add(new Row(Kind.GROUP, group.name(), group, null, null));
				if (!isOpen(group)) {
					continue;
				}
			}
			for (Option<?> option : group.options()) {
				rows.add(new Row(Kind.OPTION, null, group, option, null));
			}
		}
		return rows;
	}

	private void addList(List<Row> rows, ListOption<?> list, boolean forceOpen) {
		rows.add(new Row(Kind.GROUP, list.name(), list, null, list));
		if (!forceOpen && !isOpen(list)) {
			return;
		}
		for (ListOptionEntry<?> entry : list.options()) {
			rows.add(new Row(Kind.OPTION, null, list, entry, list));
		}
		if (list.numberOfEntries() < list.maximumNumberOfEntries()) {
			rows.add(new Row(Kind.LIST_ADD, null, list, null, list));
		}
	}

	private boolean isOpen(OptionGroup group) {
		return open.computeIfAbsent(group, g -> !g.collapsed());
	}

	private static boolean matches(Option<?> option, String query) {
		return option.name().getString().toLowerCase(Locale.ROOT).contains(query)
				|| option.description().text().getString().toLowerCase(Locale.ROOT).contains(query);
	}

	private static int contentHeight(List<Row> rows) {
		int h = 0;
		for (Row row : rows) {
			h += row.height();
		}
		return h;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null) {
			super.extractBackground(g, mouseX, mouseY, delta);
		}
		g.fill(0, 0, width, height, 0x90101318);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		MapUi.panel(g, left, top, left + panelW, top + panelH, MapUi.PANEL_SOLID);
		drawHeader(g, mouseX, mouseY);
		if (sections.size() > 1) {
			drawSidebar(g, mouseX, mouseY);
		}
		Component tooltip = drawOptions(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (tooltip != null) {
			g.setTooltipForNextFrame(font, font.split(tooltip, DESCRIPTION_WIDTH), mouseX, mouseY);
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		RenderUtil.roundedFill(g, left + 7, top + 9, left + 9, top + 19, 1, MapUi.ACCENT);
		g.text(font, title, left + 14, top + 10, MapUi.TEXT, true);
		if (search.visible) {
			int x0 = search.getX() - 16;
			int x1 = search.getX() + search.getWidth() + 4;
			RenderUtil.roundedFill(g, x0, top + 6, x1, top + 22, 3, MapUi.FIELD);
			int edge = search.isFocused() ? RenderUtil.withAlpha(MapUi.ACCENT, 0.8f) : 0x20FFFFFF;
			RenderUtil.roundedOutline(g, x0, top + 6, x1, top + 22, 3, edge, edge);
			g.text(font, "⌕", x0 + 5, top + 10, MapUi.TEXT_FAINT, false);
		}
		int cx = left + panelW - 14;
		boolean closeHover = mouseX >= cx - 7 && mouseX < cx + 7 && mouseY >= top + 6 && mouseY < top + 22;
		g.centeredText(font, "×", cx, top + 10, closeHover ? MapUi.DANGER : MapUi.TEXT_DIM);
		g.fill(left + 8, top + HEADER, left + panelW - 8, top + HEADER + 1, 0x18FFFFFF);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x0 = left + 6;
		int x1 = left + SIDEBAR + 6;
		int content = sections.size() * SECTION_ROW;
		int height = listBottom - listTop;
		sidebarScroll.update(Math.max(0, content - height));
		g.enableScissor(x0, listTop, x1, listBottom);
		int y = listTop - (int) sidebarScroll.value();
		boolean searching = searching();
		Component cut = null;
		for (int i = 0; i < sections.size(); i++, y += SECTION_ROW) {
			if (y + SECTION_ROW < listTop || y > listBottom) {
				continue;
			}
			Section section = sections.get(i);
			boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + SECTION_ROW && mouseY >= listTop && mouseY < listBottom;
			MapUi.buttonFace(g, x0, y, x1 - 4, y + SECTION_ROW - 1, hover, i == selected && !searching, MapUi.ACCENT);
			MapUi.icon(g, section.icon(), x0 + 9, y + SECTION_ROW / 2f, 11);
			int dotSpace = section.enabled() != null ? 10 : 0;
			String name = font.plainSubstrByWidth(section.name().getString(), x1 - x0 - 26 - dotSpace);
			if (hover && name.length() < section.name().getString().length()) {
				cut = section.name();
			}
			g.text(font, name, x0 + 18, y + 4, i == selected && !searching ? MapUi.TEXT : MapUi.TEXT_DIM, false);
			if (section.enabled() != null) {
				int dx = x1 - 12;
				RenderUtil.roundedFill(g, dx, y + 6, dx + 4, y + 10, 2, section.enabled().getAsBoolean() ? ON : OFF);
			}
		}
		g.disableScissor();
		drawScrollbar(g, x1 - 3, content, height, sidebarScroll);
		g.fill(x1 + 1, listTop, x1 + 2, listBottom, 0x14FFFFFF);
		if (cut != null) {
			g.setTooltipForNextFrame(font, cut, mouseX, mouseY);
		}
	}

	/** Draws the visible rows; returns the description to show for the hovered option, if any. */
	private Component drawOptions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Row> rows = rows();
		int content = contentHeight(rows);
		int height = listBottom - listTop;
		contentScroll.update(Math.max(0, content - height));
		boolean inList = mouseX >= listX0 && mouseX < listX1 && mouseY >= listTop && mouseY < listBottom;
		Component tooltip = null;
		boolean editorPlaced = false;
		g.enableScissor(listX0, listTop, listX1, listBottom);
		if (rows.isEmpty()) {
			g.centeredText(font, Component.translatable("cobblecomfort.settings.no_results"), (listX0 + listX1) / 2, listTop + 20, MapUi.TEXT_FAINT);
		}
		int y = listTop - (int) contentScroll.value();
		for (Row row : rows) {
			int h = row.height();
			if (y + h >= listTop && y <= listBottom) {
				boolean hover = inList && mouseY >= y && mouseY < y + h;
				switch (row.kind()) {
					case HEADER -> {
						g.text(font, row.label(), listX0 + 2, y + 9, MapUi.ACCENT, false);
						g.fill(listX0 + 4 + font.width(row.label()), y + 13, listX1 - 6, y + 14, 0x20FFFFFF);
					}
					case GROUP -> drawGroup(g, row, y, hover);
					case LIST_ADD -> {
						int x0 = listX0 + 14;
						MapUi.textButton(g, font, Component.translatable("cobblecomfort.settings.add_entry"), x0, y + 3, x0 + 110, y + OPTION_ROW - 3,
								hover && mouseX >= x0 && mouseX < x0 + 110, true, MapUi.ACCENT);
					}
					case OPTION -> {
						Component tip = drawOption(g, row, y, hover, mouseX, mouseY);
						if (tip != null) {
							tooltip = tip;
						}
						if (row.option() == editing) {
							placeEditor(row, y);
							editorPlaced = true;
						}
					}
				}
			} else if (row.option() != null && row.option() == editing) {
				placeEditor(row, y);
				editorPlaced = true;
			}
			y += h;
		}
		g.disableScissor();
		editor.visible = editorPlaced && editor.getY() >= listTop && editor.getY() + editor.getHeight() <= listBottom;
		drawScrollbar(g, listX1 - 3, content, height, contentScroll);
		return tooltip;
	}

	private void drawGroup(GuiGraphicsExtractor g, Row row, int y, boolean hover) {
		boolean expanded = row.list() != null && searching() || isOpen(row.group());
		if (hover) {
			RenderUtil.roundedFill(g, listX0, y + 1, listX1 - 6, y + GROUP_ROW - 1, 3, MapUi.HOVER);
		}
		g.text(font, expanded ? "▾" : "▸", listX0 + 3, y + 5, MapUi.TEXT_DIM, false);
		Component label = row.label();
		g.text(font, label, listX0 + 13, y + 5, MapUi.TEXT, false);
		if (row.list() != null) {
			String count = String.valueOf(row.list().numberOfEntries());
			int cx = listX0 + 17 + font.width(label);
			MapUi.pill(g, cx, y + 4, cx + font.width(count) + 8, y + 14, 0x30FFFFFF);
			g.text(font, count, cx + 4, y + 5, MapUi.TEXT_DIM, false);
		}
	}

	private Component drawOption(GuiGraphicsExtractor g, Row row, int y, boolean hover, int mouseX, int mouseY) {
		Option<?> option = row.option();
		boolean available = option.available();
		if (hover) {
			RenderUtil.roundedFill(g, listX0, y + 1, listX1 - 6, y + OPTION_ROW - 1, 3, MapUi.HOVER);
		}
		int[] control = controlArea(row, y);
		if (row.list() == null) {
			int nameX = listX0 + (row.group() != null && !row.group().isRoot() && !searching() ? 13 : 4);
			int nameRoom = control[0] - nameX - 18;
			String name = font.plainSubstrByWidth(option.name().getString(), nameRoom);
			g.text(font, name, nameX, y + 6, available ? MapUi.TEXT : MapUi.TEXT_FAINT, false);
			if (canReset(option)) {
				int rx = control[0] - 14;
				boolean over = hover && mouseX >= rx && mouseX < rx + SMALL_BUTTON;
				g.text(font, "↺", rx + 2, y + 6, over ? MapUi.ACCENT : hover ? MapUi.TEXT_DIM : 0x00000000, false);
				if (over) {
					return Component.translatable("cobblecomfort.settings.reset");
				}
			}
		} else {
			drawListButtons(g, row, y, hover, mouseX);
		}
		drawControl(g, option, control, y, available, hover && mouseX >= control[0] && mouseX < control[2]);
		boolean overName = hover && mouseX < control[0] - 16;
		Component description = option.description().text();
		return overName && !description.getString().isEmpty() ? description : null;
	}

	/** Left, top, right and bottom of an option's control. List entries use most of the row. */
	private int[] controlArea(Row row, int y) {
		int x1 = listX1 - 8;
		// A switch only needs its own width, leaving the name more room.
		int x0 = x1 - (isSwitch(row.option()) ? 20 : CONTROL);
		if (row.list() != null) {
			x1 -= 3 * (SMALL_BUTTON + 2);
			x0 = listX0 + 14;
		}
		return new int[]{x0, y + 3, x1, y + OPTION_ROW - 3};
	}

	/** The controller drawing an option; list entries wrap the controller of their kind. */
	private static Controller<?> controllerOf(Option<?> option) {
		Controller<?> controller = option.controller();
		return controller instanceof ListOptionEntryImpl.EntryController<?> entry ? entry.controller() : controller;
	}

	private static boolean isSwitch(Option<?> option) {
		return !(option instanceof ButtonOption) && option.pendingValue() instanceof Boolean;
	}

	private void drawListButtons(GuiGraphicsExtractor g, Row row, int y, boolean hover, int mouseX) {
		String[] icons = {"▲", "▼", "×"};
		int bx = listX1 - 8 - 3 * (SMALL_BUTTON + 2) + 2;
		for (int i = 0; i < 3; i++) {
			int x = bx + i * (SMALL_BUTTON + 2);
			boolean over = hover && mouseX >= x && mouseX < x + SMALL_BUTTON;
			MapUi.buttonFace(g, x, y + 4, x + SMALL_BUTTON, y + OPTION_ROW - 4, over, false, MapUi.ACCENT);
			int color = over ? (i == 2 ? MapUi.DANGER : MapUi.TEXT) : MapUi.TEXT_FAINT;
			MapUi.centeredText(g, font, Component.literal(icons[i]), x + SMALL_BUTTON / 2f, y + OPTION_ROW / 2f, color, i == 2 ? 1f : 0.6f);
		}
	}

	@SuppressWarnings("unchecked")
	private void drawControl(GuiGraphicsExtractor g, Option<?> option, int[] c, int y, boolean available, boolean hover) {
		Controller<?> controller = controllerOf(option);
		int cy = (c[1] + c[3]) / 2;
		if (option instanceof ButtonOption) {
			Component label = controller.formatValue();
			if (label.getString().isEmpty()) {
				label = Component.literal("›");
			}
			MapUi.textButton(g, font, label, c[0], c[1], c[2], c[3], hover, available, MapUi.ACCENT);
		} else if (option.pendingValue() instanceof Boolean on) {
			MapUi.toggle(g, c[2] - 15, cy, on, available, MapUi.ACCENT);
		} else if (controller instanceof ISliderController<?> slider) {
			Component value = slider.formatValue();
			int tx0 = c[0] + SLIDER_LABEL;
			g.text(font, value, tx0 - 4 - font.width(value), cy - 4, available ? MapUi.TEXT_DIM : MapUi.TEXT_FAINT, false);
			double range = slider.max() - slider.min();
			float t = range <= 0 ? 0f : (float) Mth.clamp((slider.pendingValue() - slider.min()) / range, 0, 1);
			int knob = tx0 + Math.round((c[2] - tx0 - 6) * t);
			RenderUtil.roundedFill(g, tx0, cy - 1, c[2], cy + 2, 1, 0x38FFFFFF);
			RenderUtil.roundedFill(g, tx0, cy - 1, knob + 3, cy + 2, 1, RenderUtil.withAlpha(MapUi.ACCENT, available ? 0.9f : 0.3f));
			RenderUtil.roundedFill(g, knob, cy - 5, knob + 6, cy + 5, 3, available ? (hover || slider == dragging ? 0xFFFFFFFF : 0xFFDDE0E8) : 0x80FFFFFF);
		} else if (controller instanceof ICyclingController<?>) {
			RenderUtil.roundedFill(g, c[0], c[1], c[2], c[3], 3, hover ? 0x30FFFFFF : 0x1CFFFFFF);
			g.text(font, "‹", c[0] + 4, cy - 4, MapUi.TEXT_FAINT, false);
			g.text(font, "›", c[2] - 8, cy - 4, MapUi.TEXT_FAINT, false);
			String value = font.plainSubstrByWidth(controller.formatValue().getString(), c[2] - c[0] - 22);
			g.centeredText(font, value, (c[0] + c[2]) / 2, cy - 4, available ? MapUi.TEXT : MapUi.TEXT_FAINT);
		} else if (controller instanceof IStringController<?> text) {
			boolean active = option == editing;
			RenderUtil.roundedFill(g, c[0], c[1], c[2], c[3], 3, MapUi.FIELD);
			int edge = active ? RenderUtil.withAlpha(MapUi.ACCENT, 0.8f) : hover ? 0x40FFFFFF : 0x20FFFFFF;
			RenderUtil.roundedOutline(g, c[0], c[1], c[2], c[3], 3, edge, edge);
			int textX = c[0] + 5;
			if (controller instanceof ColorController color) {
				int argb = ((Option<java.awt.Color>) color.option()).pendingValue().getRGB();
				RenderUtil.roundedFill(g, c[0] + 3, c[1] + 3, c[0] + 11, c[3] - 3, 2, argb | 0xFF000000);
				textX += 10;
			}
			if (!active) {
				String value = font.plainSubstrByWidth(text.getString(), c[2] - textX - 4);
				g.text(font, value, textX, cy - 4, available ? MapUi.TEXT : MapUi.TEXT_FAINT, false);
			}
		} else {
			g.text(font, controller.formatValue(), c[0], cy - 4, MapUi.TEXT_DIM, false);
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor g, int x, int content, int height, SmoothScroll scroll) {
		if (content <= height) {
			return;
		}
		int barH = Math.max(16, height * height / content);
		int barY = listTop + (int) ((height - barH) * (scroll.value() / (content - height)));
		RenderUtil.roundedFill(g, x, listTop, x + 3, listTop + height, 1, 0x14FFFFFF);
		RenderUtil.roundedFill(g, x, barY, x + 3, barY + barH, 1, 0x60FFFFFF);
	}

	// ---------------------------------------------------------------- editing

	private boolean canReset(Option<?> option) {
		return option.available() && option.canResetToDefault() && !option.isPendingValueDefault() && !(option instanceof ButtonOption);
	}

	/** Applies an option right away; a list entry applies its whole list. */
	private void apply(Option<?> option) {
		if (option instanceof ListOptionEntry<?> entry) {
			entry.parentGroup().applyValue();
		} else {
			option.applyValue();
		}
		dirty = true;
	}

	private void startEditing(Option<?> option) {
		if (!(controllerOf(option) instanceof IStringController<?> text)) {
			return;
		}
		editing = option;
		editor.setResponder(value -> { });
		editor.setValue(text.getString());
		editor.setResponder(this::typed);
		editor.moveCursorToEnd(false);
		setFocused(editor);
		editor.setFocused(true);
	}

	private void typed(String value) {
		if (editing != null && controllerOf(editing) instanceof IStringController<?> text) {
			try {
				text.setFromString(value);
				apply(editing);
			} catch (RuntimeException ignored) {
				// Half-typed values (like a colour) are taken once they parse.
			}
		}
	}

	private void stopEditing() {
		if (editing != null) {
			editing = null;
			editor.setFocused(false);
			editor.visible = false;
			if (getFocused() == editor) {
				setFocused(null);
			}
		}
	}

	private void placeEditor(Row row, int y) {
		int[] c = controlArea(row, y);
		int x = c[0] + 5 + (controllerOf(row.option()) instanceof ColorController ? 10 : 0);
		editor.setX(x);
		editor.setY((c[1] + c[3]) / 2 - 4);
		editor.setWidth(c[2] - x - 4);
		editor.setHeight(10);
	}

	private void setSlider(ISliderController<?> slider, double mouseX) {
		double t = Mth.clamp((mouseX - dragX0) / Math.max(1, dragX1 - dragX0 - 6), 0, 1);
		double value = slider.min() + t * (slider.max() - slider.min());
		double step = slider.interval();
		if (step > 0) {
			value = slider.min() + Math.round((value - slider.min()) / step) * step;
		}
		slider.setPendingValue(Mth.clamp(value, slider.min(), slider.max()));
		apply(slider.option());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void clickOption(Row row, int y, MouseButtonEvent event) {
		Option<?> option = row.option();
		int[] c = controlArea(row, y);
		double mx = event.x();
		if (row.list() != null) {
			int bx = listX1 - 8 - 3 * (SMALL_BUTTON + 2) + 2;
			if (mx >= bx) {
				ListOption list = row.list();
				ListOptionEntry entry = (ListOptionEntry) option;
				int index = list.indexOf(entry);
				int button = (int) ((mx - bx) / (SMALL_BUTTON + 2));
				if (button == 2) {
					if (list.numberOfEntries() > list.minimumNumberOfEntries()) {
						list.removeEntry(entry);
					}
				} else {
					int target = button == 0 ? index - 1 : index + 1;
					if (target >= 0 && target < list.numberOfEntries()) {
						list.removeEntry(entry);
						list.insertEntry(target, entry);
					}
				}
				stopEditing();
				list.applyValue();
				dirty = true;
				return;
			}
		} else if (canReset(option) && mx >= c[0] - 14 && mx < c[0] - 2) {
			option.requestSetDefault();
			apply(option);
			return;
		}
		if (!option.available()) {
			return;
		}
		Controller<?> controller = controllerOf(option);
		if (option instanceof ButtonOption button) {
			if (mx >= c[0]) {
				button.action().accept(null, button);
			}
			return;
		}
		if (option.pendingValue() instanceof Boolean on) {
			((Option<Boolean>) option).requestSet(!on);
			apply(option);
			return;
		}
		if (mx < c[0]) {
			return;
		}
		if (controller instanceof ISliderController<?> slider) {
			dragging = slider;
			dragX0 = c[0] + SLIDER_LABEL;
			dragX1 = c[2];
			setSlider(slider, mx);
		} else if (controller instanceof ICyclingController<?> cycling) {
			// Back with the left arrow or a right click, forward otherwise.
			int step = event.button() == InputConstants.MOUSE_BUTTON_RIGHT || mx < c[0] + 14 ? -1 : 1;
			int length = cycling.getCycleLength();
			cycling.setPendingValue(Math.floorMod(cycling.getPendingValue() + step, length));
			apply(option);
		} else if (controller instanceof IStringController<?>) {
			startEditing(option);
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mx >= left + panelW - 21 && mx < left + panelW - 7 && my >= top + 6 && my < top + 22) {
			onClose();
			return true;
		}
		if (editor.visible && editor.isMouseOver(mx, my)) {
			return super.mouseClicked(event, doubleClick);
		}
		if (search.visible && mx >= search.getX() - 16 && mx < search.getX() + search.getWidth() + 4 && my >= top + 6 && my < top + 22) {
			stopEditing();
			setFocused(search);
			search.setFocused(true);
			return super.mouseClicked(event, doubleClick);
		}
		stopEditing();
		search.setFocused(false);
		if (sections.size() > 1 && mx >= left + 6 && mx < left + SIDEBAR + 6 && my >= listTop && my < listBottom) {
			int index = (int) ((my - listTop + sidebarScroll.value()) / SECTION_ROW);
			if (index >= 0 && index < sections.size()) {
				selected = index;
				search.setValue("");
				contentScroll.set(0);
			}
			return true;
		}
		if (mx < listX0 || mx >= listX1 || my < listTop || my >= listBottom) {
			return false;
		}
		int y = listTop - (int) contentScroll.value();
		for (Row row : rows()) {
			int h = row.height();
			if (my >= y && my < y + h) {
				switch (row.kind()) {
					case GROUP -> {
						if (!(row.list() != null && searching())) {
							open.put(row.group(), !isOpen(row.group()));
						}
					}
					case LIST_ADD -> {
						if (mx < listX0 + 124) {
							row.list().insertNewEntry();
							row.list().applyValue();
							dirty = true;
						}
					}
					case OPTION -> clickOption(row, y, event);
					default -> {
					}
				}
				return true;
			}
			y += h;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			setSlider(dragging, event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (sections.size() > 1 && mouseX < left + SIDEBAR + 6) {
			sidebarScroll.scroll(-scrollY * SECTION_ROW * 2);
		} else {
			contentScroll.scroll(-scrollY * OPTION_ROW * 2);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE && (editing != null || search.isFocused())) {
			stopEditing();
			search.setFocused(false);
			setFocused(null);
			return true;
		}
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && editing != null) {
			stopEditing();
			return true;
		}
		if (event.key() == InputConstants.KEY_F && event.hasControlDown() && search.visible) {
			stopEditing();
			setFocused(search);
			search.setFocused(true);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (dirty) {
			save.run();
			dirty = false;
		}
		super.removed();
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
