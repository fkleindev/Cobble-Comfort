package dev.cobblecomfort.features.storage;

import dev.cobblecomfort.features.furnace.FurnaceEntry;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.invtools.ItemSearch;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.screen.MapScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Searches the remembered containers. "Items" sums up everything stored; "Containers" lists where matching items are.
 * Clicking a container marks it in the world, right-clicking shows it on the map.
 */
public class StorageScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 380;
	private static final int HEADER = 30;
	private static final int FOOTER = 26;
	private static final int CELL = 20;
	private static final int ROW = 24;
	private static final int ROW_ITEMS = 8;
	private static final int MAX_HIGHLIGHT = 50;
	private static final int PANEL = 0xE0101018;
	private static final int PANEL_OUTLINE = 0xFF3A3A50;
	private static final int ROW_HOVER = 0x30FFFFFF;

	private enum Mode {
		ITEMS, CONTAINERS
	}

	/** One summed-up item for the overview. */
	private static final class Total {
		final ItemStack stack;
		int count;
		int containers;

		Total(ItemStack stack) {
			this.stack = stack;
		}
	}

	/** One container in the list, with the items that made it match. */
	private record Result(StoredContainer container, List<StorageItems.Entry> shown, int matchCount) {
	}

	/** Map key that compares item stacks by item and components. */
	private record Key(ItemStack stack) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Key other && ItemStack.isSameItemSameComponents(stack, other.stack);
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(stack);
		}
	}

	private final Screen parent;
	private final ItemSearch search = new ItemSearch();
	private final String initialQuery;
	private EditBox box;
	private Button itemsTab;
	private Button containersTab;
	private Button markAllButton;
	private Mode mode;
	private ItemStack selected;
	private int left, top, panelWidth, panelHeight;
	private double scroll;

	private int builtVersion = -1;
	private String builtQuery;
	private List<Total> totals = List.of();
	private List<Result> results = List.of();

	public StorageScreen(Screen parent, String query) {
		super(Component.translatable("cobblecomfort.storage.title"));
		this.parent = parent;
		this.initialQuery = query == null ? "" : query;
		this.mode = this.initialQuery.isEmpty() ? Mode.ITEMS : Mode.CONTAINERS;
	}

	private StorageFeature feature() {
		return StorageFeature.INSTANCE;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(MAX_PANEL_WIDTH, width - 20);
		panelHeight = height - 20;
		left = (width - panelWidth) / 2;
		top = 10;
		String value = box == null ? initialQuery : box.getValue();
		box = new EditBox(font, left + 8, top + 8, panelWidth - 16 - 2 * 74 - 8, 16, Component.translatable("cobblecomfort.storage.search"));
		box.setHint(Component.translatable("cobblecomfort.invtools.search_hint").withColor(0xFF808080));
		box.setMaxLength(50);
		box.setValue(value);
		box.setResponder(v -> {
			selected = null;
			scroll = 0;
			updateTabs();
		});
		addRenderableWidget(box);
		itemsTab = addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.storage.mode.items"), b -> setMode(Mode.ITEMS))
				.bounds(left + panelWidth - 8 - 2 * 74 + 4, top + 6, 70, 20).build());
		containersTab = addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.storage.mode.containers"),
				b -> setMode(Mode.CONTAINERS)).bounds(left + panelWidth - 8 - 74 + 4, top + 6, 70, 20).build());
		markAllButton = addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.storage.mark_all"), b -> markAll())
				.bounds(left + panelWidth - 8 - 110, top + panelHeight - FOOTER + 4, 110, 18).build());
		setInitialFocus(box);
		updateTabs();
	}

	private void setMode(Mode value) {
		mode = value;
		if (value == Mode.ITEMS) {
			selected = null;
		}
		scroll = 0;
		updateTabs();
	}

	private void updateTabs() {
		if (itemsTab != null) {
			itemsTab.active = mode != Mode.ITEMS;
			containersTab.active = mode != Mode.CONTAINERS || selected != null;
			markAllButton.visible = mode == Mode.CONTAINERS;
		}
	}

	// ---------------------------------------------------------------- data

	private boolean countShulkers() {
		return feature().config().countShulkerContents;
	}

	private void rebuildIfNeeded() {
		StorageStore store = feature().store();
		String query = box.getValue() + "|" + (selected == null ? "" : selected.getItem() + "" + selected.getComponents());
		if (store.version() == builtVersion && query.equals(builtQuery)) {
			return;
		}
		builtVersion = store.version();
		builtQuery = query;
		search.setQuery(box.getValue());
		buildTotals(store);
		buildResults(store);
	}

	private void buildTotals(StorageStore store) {
		Map<Key, Total> map = new LinkedHashMap<>();
		for (StoredContainer container : store.all()) {
			Map<Key, Boolean> seenHere = new LinkedHashMap<>();
			for (ItemStack stack : StorageItems.flatten(container.items, countShulkers())) {
				Key key = new Key(stack);
				Total total = map.computeIfAbsent(key, k -> new Total(stack.copyWithCount(1)));
				total.count += stack.getCount();
				if (seenHere.putIfAbsent(key, true) == null) {
					total.containers++;
				}
			}
		}
		List<Total> list = new ArrayList<>();
		for (Total total : map.values()) {
			if (!search.active() || search.matches(total.stack, InventoryToolsFeature.INSTANCE.config())) {
				list.add(total);
			}
		}
		list.sort(Comparator.comparingInt((Total t) -> -t.count));
		totals = list;
	}

	private void buildResults(StorageStore store) {
		List<Result> list = new ArrayList<>();
		String query = search.query();
		for (StoredContainer container : store.all()) {
			List<ItemStack> items = StorageItems.flatten(container.items, countShulkers());
			if (selected != null) {
				int count = 0;
				for (ItemStack stack : items) {
					if (ItemStack.isSameItemSameComponents(stack, selected)) {
						count += stack.getCount();
					}
				}
				if (count > 0) {
					list.add(new Result(container, List.of(new StorageItems.Entry(selected.copyWithCount(1), count)), count));
				}
				continue;
			}
			if (!search.active()) {
				list.add(new Result(container, StorageItems.summary(container.items), 0));
				continue;
			}
			List<ItemStack> matching = new ArrayList<>();
			for (ItemStack stack : container.items) {
				if (search.matches(stack, InventoryToolsFeature.INSTANCE.config())) {
					matching.add(stack);
				}
			}
			boolean nameMatches = !container.name.isEmpty() && container.name.toLowerCase(Locale.ROOT).contains(query);
			if (!matching.isEmpty() || nameMatches) {
				int count = matching.stream().mapToInt(ItemStack::getCount).sum();
				list.add(new Result(container, StorageItems.summary(matching.isEmpty() ? container.items : matching), count));
			}
		}
		Vec3 eye = minecraft.player == null ? Vec3.ZERO : minecraft.player.getEyePosition();
		String dim = minecraft.level == null ? "" : MapFeature.dimensionId(minecraft.level);
		list.sort(Comparator.comparingInt((Result r) -> group(r.container(), dim))
				.thenComparingDouble(r -> r.container().located() ? r.container().distanceTo(eye) : 0));
		results = list;
	}

	/** Sort order: this dimension, ender chest, other dimensions. */
	private static int group(StoredContainer container, String dim) {
		if (!container.located()) {
			return 1;
		}
		return container.dimension.equals(dim) ? 0 : 2;
	}

	// ---------------------------------------------------------------- actions

	private void markAll() {
		String dim = minecraft.level == null ? "" : MapFeature.dimensionId(minecraft.level);
		List<StoredContainer> list = new ArrayList<>();
		for (Result result : results) {
			if (result.container().located() && result.container().dimension.equals(dim) && list.size() < MAX_HIGHLIGHT) {
				list.add(result.container());
			}
		}
		if (!list.isEmpty()) {
			feature().highlight(list);
			onClose();
		}
	}

	private void mark(StoredContainer container) {
		if (container.located()) {
			feature().highlight(List.of(container));
			onClose();
		}
	}

	private void showOnMap(StoredContainer container) {
		if (container.located()) {
			feature().highlight(List.of(container));
			Vec3 center = container.center();
			minecraft.gui.setScreen(new MapScreen(null, center.x, center.z, container.dimension));
		}
	}

	// ---------------------------------------------------------------- layout

	private int bodyTop() {
		return top + HEADER;
	}

	private int bodyBottom() {
		return top + panelHeight - FOOTER;
	}

	private int columns() {
		return Math.max(1, (panelWidth - 16) / CELL);
	}

	private int contentHeight() {
		if (mode == Mode.ITEMS) {
			return (totals.size() + columns() - 1) / columns() * CELL;
		}
		return results.size() * ROW;
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight() - (bodyBottom() - bodyTop()));
		scroll = Math.clamp(scroll, 0, max);
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		rebuildIfNeeded();
		clampScroll();
		g.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
		g.outline(left, top, panelWidth, panelHeight, PANEL_OUTLINE);
		int y0 = bodyTop();
		int y1 = bodyBottom();
		g.fill(left + 4, y0 - 1, left + panelWidth - 4, y0, PANEL_OUTLINE);
		g.fill(left + 4, y1, left + panelWidth - 4, y1 + 1, PANEL_OUTLINE);
		g.enableScissor(left + 4, y0, left + panelWidth - 4, y1);
		Object hovered;
		try {
			hovered = mode == Mode.ITEMS ? drawTotals(g, mouseX, mouseY) : drawResults(g, mouseX, mouseY);
		} finally {
			g.disableScissor();
		}
		drawFooter(g);
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (mouseY >= y0 && mouseY < y1) {
			drawTooltip(g, hovered, mouseX, mouseY);
		}
	}

	private Total drawTotals(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (totals.isEmpty()) {
			drawEmpty(g);
			return null;
		}
		int columns = columns();
		int x0 = left + 8 + (panelWidth - 16 - columns * CELL) / 2;
		int y0 = bodyTop() + 2 - (int) scroll;
		Total hovered = null;
		for (int i = 0; i < totals.size(); i++) {
			int x = x0 + (i % columns) * CELL;
			int y = y0 + (i / columns) * CELL;
			if (y + CELL < bodyTop() || y > bodyBottom()) {
				continue;
			}
			Total total = totals.get(i);
			boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
			g.fill(x, y, x + CELL - 2, y + CELL - 2, hover ? 0x50FFFFFF : 0x20FFFFFF);
			g.item(total.stack, x + 1, y + 1);
			g.itemDecorations(font, total.stack, x + 1, y + 1, StorageItems.shortCount(total.count));
			if (hover) {
				hovered = total;
			}
		}
		return hovered;
	}

	private Result drawResults(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (results.isEmpty()) {
			drawEmpty(g);
			return null;
		}
		StorageConfig cfg = feature().config();
		Vec3 eye = minecraft.player == null ? Vec3.ZERO : minecraft.player.getEyePosition();
		String dim = minecraft.level == null ? "" : MapFeature.dimensionId(minecraft.level);
		int x0 = left + 8;
		int x1 = left + panelWidth - 8;
		int y = bodyTop() + 2 - (int) scroll;
		Result hovered = null;
		for (Result result : results) {
			if (y + ROW >= bodyTop() && y <= bodyBottom()) {
				StoredContainer container = result.container();
				boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW - 2;
				if (hover) {
					g.fill(x0 - 2, y - 1, x1 + 2, y + ROW - 1, ROW_HOVER);
					hovered = result;
				}
				if (feature().isHighlighted(container)) {
					g.fill(x0 - 2, y - 1, x0 - 1, y + ROW - 1, cfg.outlineColor);
				}
				g.item(container.icon(), x0, y + 2);
				int textX = x0 + 20;
				g.text(font, container.displayName(), textX, y + 1, container.name.isEmpty() ? 0xFFFFFFFF : 0xFFFFD27A, true);
				FurnaceEntry furnace = container.kind == StoredContainer.Kind.BLOCK ? FurnaceFeature.INSTANCE.at(container.dimension, container.pos()) : null;
				if (furnace != null) {
					g.text(font, FurnaceFeature.INSTANCE.status(furnace), textX + font.width(container.displayName()) + 6, y + 1, 0xFFFFFFFF, true);
				}
				g.text(font, location(container, eye, dim), textX, y + 12, 0xFF9A9AA8, false);
				drawRowItems(g, result, x1, y + 3);
			}
			y += ROW;
		}
		return hovered;
	}

	private void drawRowItems(GuiGraphicsExtractor g, Result result, int right, int y) {
		int count = Math.min(ROW_ITEMS, result.shown().size());
		int x = right - count * 18;
		for (int i = 0; i < count; i++) {
			StorageItems.Entry entry = result.shown().get(i);
			g.item(entry.stack(), x + i * 18, y);
			g.itemDecorations(font, entry.stack(), x + i * 18, y, StorageItems.shortCount(entry.total()));
		}
		if (result.shown().size() > ROW_ITEMS) {
			g.text(font, "+" + (result.shown().size() - ROW_ITEMS), x - font.width("+99") - 2, y + 5, 0xFF9A9AA8, false);
		}
	}

	/** "12, 64, -30 · 34m · 2 h ago", with the dimension when it is not the current one. */
	private Component location(StoredContainer container, Vec3 eye, String dim) {
		StorageConfig cfg = feature().config();
		MutableComponent text;
		if (!container.located()) {
			text = Component.translatable("cobblecomfort.storage.everywhere");
		} else {
			text = Component.literal(container.x + ", " + container.y + ", " + container.z);
			if (container.dimension.equals(dim)) {
				text.append(" · " + MapFeature.formatDistance(container.distanceTo(eye)));
			} else {
				text.append(" · ").append(dimensionName(container.dimension));
			}
		}
		if (cfg.showAge) {
			boolean stale = StorageItems.stale(container.seen, cfg.staleDays);
			text.append(Component.literal(" · ").append(StorageItems.age(container.seen))
					.withStyle(stale ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}
		return text;
	}

	private static Component dimensionName(String dimension) {
		String path = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
		String key = "cobblecomfort.storage.dim." + path;
		Component translated = Component.translatable(key);
		return translated.getString().equals(key) ? Component.literal(path) : translated;
	}

	private void drawEmpty(GuiGraphicsExtractor g) {
		Component text = feature().store().all().isEmpty()
				? Component.translatable("cobblecomfort.storage.nothing_yet")
				: Component.translatable("cobblecomfort.storage.no_results");
		g.centeredText(font, text, left + panelWidth / 2, bodyTop() + 20, 0xFF9A9AA8);
	}

	private void drawFooter(GuiGraphicsExtractor g) {
		int y = bodyBottom() + 5;
		Component info;
		if (mode == Mode.ITEMS) {
			int sum = totals.stream().mapToInt(t -> t.count).sum();
			info = Component.translatable("cobblecomfort.storage.footer.items", totals.size(), sum, feature().store().all().size());
		} else {
			info = Component.translatable("cobblecomfort.storage.footer.containers", results.size());
		}
		g.text(font, info, left + 8, y, 0xFFC0C0C0, false);
		Component hint = Component.translatable(mode == Mode.ITEMS ? "cobblecomfort.storage.hint.items" : "cobblecomfort.storage.hint.containers")
				.withStyle(ChatFormatting.DARK_GRAY);
		g.text(font, hint, left + 8, y + 10, 0xFFFFFFFF, false);
		if (selected != null) {
			g.text(font, Component.translatable("cobblecomfort.storage.filter", selected.getHoverName()),
					left + 8 + font.width(info) + 8, y, 0xFFFFE066, false);
		}
	}

	private void drawTooltip(GuiGraphicsExtractor g, Object hovered, int mouseX, int mouseY) {
		if (hovered instanceof Total total) {
			List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, total.stack));
			lines.add(Component.translatable("cobblecomfort.storage.total", total.count, total.containers).withStyle(ChatFormatting.YELLOW));
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		} else if (hovered instanceof Result result) {
			int itemsX = left + panelWidth - 8 - Math.min(ROW_ITEMS, result.shown().size()) * 18;
			if (mouseX >= itemsX) {
				int index = (mouseX - itemsX) / 18;
				if (index >= 0 && index < Math.min(ROW_ITEMS, result.shown().size())) {
					StorageItems.Entry entry = result.shown().get(index);
					List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, entry.stack()));
					lines.add(Component.literal("× " + entry.total()).withStyle(ChatFormatting.YELLOW));
					g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
				}
			}
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mouseX = event.x();
		double mouseY = event.y();
		if (mouseY < bodyTop() || mouseY >= bodyBottom()) {
			return false;
		}
		if (mode == Mode.ITEMS) {
			int columns = columns();
			int x0 = left + 8 + (panelWidth - 16 - columns * CELL) / 2;
			int col = (int) Math.floor((mouseX - x0) / CELL);
			int row = (int) Math.floor((mouseY - bodyTop() - 2 + scroll) / CELL);
			int index = row * columns + col;
			if (col >= 0 && col < columns && row >= 0 && index < totals.size()) {
				selected = totals.get(index).stack;
				mode = Mode.CONTAINERS;
				scroll = 0;
				updateTabs();
				return true;
			}
			return false;
		}
		int index = (int) Math.floor((mouseY - bodyTop() - 2 + scroll) / ROW);
		if (index >= 0 && index < results.size() && mouseX >= left + 6 && mouseX < left + panelWidth - 6) {
			StoredContainer container = results.get(index).container();
			if (event.button() == 1) {
				showOnMap(container);
			} else {
				mark(container);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * (mode == Mode.ITEMS ? CELL : ROW);
		clampScroll();
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
