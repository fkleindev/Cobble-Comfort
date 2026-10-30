package dev.cobblecomfort.features.storage;

import dev.cobblecomfort.features.furnace.FurnaceEntry;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.invtools.ItemSearch;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.map.screen.FlatButton;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.map.screen.TabButton;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.pets.PetEntry;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.pets.PetTab;
import dev.cobblecomfort.features.trade.TradeFeature;
import dev.cobblecomfort.features.trade.TradeMerchant;
import dev.cobblecomfort.features.trade.TradeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Searches the remembered containers. "Items" sums up everything stored; "Containers" lists where matching items are;
 * "Traders" lists remembered villagers and wandering traders with matching offers (see {@link TradeTab}); "Pets" lists
 * remembered pets and mounts (see {@link PetTab}).
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
	private static final int LINE = 0x18FFFFFF;

	private enum Mode {
		ITEMS, CONTAINERS, TRADERS, PETS
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
	private FlatButton markAllButton;
	private int tabsLeft, tabsRight, fieldRight;
	private final TradeTab tradeTab = new TradeTab();
	private final PetTab petTab = new PetTab();
	private Mode mode;
	private ItemStack selected;
	private int left, top, panelWidth, panelHeight;
	private final SmoothScroll scroll = new SmoothScroll(SmoothScroll.Kind.MOD_SCREENS);

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

	/** Opens on the traders list instead of the item search (falls back to items when trade memory is off). */
	public StorageScreen showTraders() {
		mode = Mode.TRADERS;
		return this;
	}

	/** Opens on the pets list instead of the item search (falls back to items when pet memory is off). */
	public StorageScreen showPets() {
		mode = Mode.PETS;
		return this;
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
		boolean traders = TradeFeature.INSTANCE.active();
		boolean pets = PetFeature.INSTANCE.active();
		if (!traders && mode == Mode.TRADERS || !pets && mode == Mode.PETS) {
			mode = Mode.ITEMS;
		}
		// Tabs sized to their labels on the right, the search field takes the rest.
		List<TabButton> tabs = new ArrayList<>();
		tabs.add(tab(Items.DIAMOND, "cobblecomfort.storage.mode.items", Mode.ITEMS));
		tabs.add(tab(Items.CHEST, "cobblecomfort.storage.mode.containers", Mode.CONTAINERS));
		if (traders) {
			tabs.add(tab(Items.EMERALD, "cobblecomfort.storage.mode.traders", Mode.TRADERS));
		}
		if (pets) {
			tabs.add(tab(Items.BONE, "cobblecomfort.storage.mode.pets", Mode.PETS));
		}
		int tabsWidth = tabs.stream().mapToInt(TabButton::getWidth).sum() + 4;
		tabsRight = left + panelWidth - 8;
		tabsLeft = tabsRight - tabsWidth;
		int tabX = tabsLeft + 2;
		for (TabButton tab : tabs) {
			tab.setX(tabX);
			tab.setY(top + 8);
			tabX += tab.getWidth();
			addRenderableWidget(tab);
		}
		fieldRight = tabsLeft - 6;
		box = new EditBox(font, left + 22, top + 13, fieldRight - left - 30, 10, Component.translatable("cobblecomfort.storage.search"));
		box.setBordered(false);
		box.setTextColor(MapUi.TEXT);
		box.setHint(Component.translatable("cobblecomfort.invtools.search_hint").withColor(MapUi.TEXT_FAINT));
		box.setMaxLength(50);
		box.setValue(value);
		box.setResponder(v -> {
			selected = null;
			scroll.set(0);
			updateTabs();
		});
		addRenderableWidget(box);
		markAllButton = addRenderableWidget(new FlatButton(left + panelWidth - 8 - 110, top + panelHeight - FOOTER + 4, 110, 18,
				Component.translatable("cobblecomfort.storage.mark_all"), MapUi.ACCENT, this::markAll));
		setInitialFocus(box);
		updateTabs();
	}

	private TabButton tab(Item icon, String key, Mode tabMode) {
		Component label = Component.translatable(key);
		return new TabButton(0, 0, TabButton.widthFor(font, label), 18, new ItemStack(icon), label, () -> mode == tabMode,
				() -> setMode(tabMode));
	}

	private void setMode(Mode value) {
		mode = value;
		if (value == Mode.ITEMS) {
			selected = null;
		}
		scroll.set(0);
		updateTabs();
	}

	private void updateTabs() {
		if (markAllButton != null) {
			markAllButton.visible = mode == Mode.CONTAINERS;
		}
	}

	// ---------------------------------------------------------------- data

	private boolean countShulkers() {
		return feature().config().countShulkerContents;
	}

	private void rebuildIfNeeded() {
		if (mode == Mode.TRADERS) {
			tradeTab.rebuild(box.getValue());
			return;
		}
		if (mode == Mode.PETS) {
			petTab.rebuild(box.getValue());
			return;
		}
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

	private void showOnMap(TradeMerchant merchant) {
		minecraft.gui.setScreen(new MapScreen(null, merchant.x, merchant.z, merchant.dimension));
	}

	private void showOnMap(PetEntry pet) {
		minecraft.gui.setScreen(new MapScreen(null, pet.x, pet.z, pet.dimension));
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
		return (mode == Mode.TRADERS ? tradeTab.results().size() : mode == Mode.PETS ? petTab.results().size() : results.size()) * ROW;
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight() - (bodyBottom() - bodyTop()));
		scroll.update(max);
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		rebuildIfNeeded();
		clampScroll();
		MapUi.panel(g, left, top, left + panelWidth, top + panelHeight, MapUi.PANEL_SOLID);
		MapUi.field(g, left + 8, top + 6, fieldRight, top + 28, box.isFocused());
		g.text(font, "⌕", left + 12, top + 12, MapUi.TEXT_FAINT, false);
		MapUi.panel(g, tabsLeft, top + 6, tabsRight, top + 28);
		int y0 = bodyTop();
		int y1 = bodyBottom();
		g.fill(left + 8, y0 - 1, left + panelWidth - 8, y0, LINE);
		g.fill(left + 8, y1, left + panelWidth - 8, y1 + 1, LINE);
		g.enableScissor(left + 4, y0, left + panelWidth - 4, y1);
		Object hovered;
		try {
			hovered = switch (mode) {
				case ITEMS -> drawTotals(g, mouseX, mouseY);
				case CONTAINERS -> drawResults(g, mouseX, mouseY);
				case TRADERS -> drawTraders(g, mouseX, mouseY);
				case PETS -> drawPets(g, mouseX, mouseY);
			};
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
		int y0 = bodyTop() + 2 - (int) scroll.value();
		Total hovered = null;
		for (int i = 0; i < totals.size(); i++) {
			int x = x0 + (i % columns) * CELL;
			int y = y0 + (i / columns) * CELL;
			if (y + CELL < bodyTop() || y > bodyBottom()) {
				continue;
			}
			Total total = totals.get(i);
			boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
			RenderUtil.roundedFill(g, x, y, x + CELL - 2, y + CELL - 2, 2, hover ? 0x38FFFFFF : 0x14FFFFFF);
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
		int y = bodyTop() + 2 - (int) scroll.value();
		Result hovered = null;
		for (Result result : results) {
			if (y + ROW >= bodyTop() && y <= bodyBottom()) {
				StoredContainer container = result.container();
				boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW - 2;
				if (hover) {
					RenderUtil.roundedFill(g, x0 - 2, y - 1, x1 + 2, y + ROW - 1, 3, MapUi.HOVER);
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
				g.text(font, location(container, eye, dim), textX, y + 12, MapUi.TEXT_DIM, false);
				drawRowItems(g, result, x1, y + 3);
			}
			y += ROW;
		}
		return hovered;
	}

	private TradeTab.Hovered drawTraders(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (tradeTab.results().isEmpty()) {
			drawEmpty(g);
			return null;
		}
		return tradeTab.draw(g, font, left + 8, left + panelWidth - 8, bodyTop() + 2 - (int) scroll.value(), bodyTop(), bodyBottom(), ROW,
				mouseX, mouseY);
	}

	private PetTab.Hovered drawPets(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (petTab.results().isEmpty()) {
			drawEmpty(g);
			return null;
		}
		return petTab.draw(g, font, left + 8, left + panelWidth - 8, bodyTop() + 2 - (int) scroll.value(), bodyTop(), bodyBottom(), ROW,
				mouseX, mouseY);
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
			g.text(font, "+" + (result.shown().size() - ROW_ITEMS), x - font.width("+99") - 2, y + 5, MapUi.TEXT_DIM, false);
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
		Component text = mode == Mode.TRADERS ? tradeTab.emptyText() : mode == Mode.PETS ? petTab.emptyText() : feature().store().all().isEmpty()
				? Component.translatable("cobblecomfort.storage.nothing_yet")
				: Component.translatable("cobblecomfort.storage.no_results");
		g.centeredText(font, text, left + panelWidth / 2, bodyTop() + 20, MapUi.TEXT_DIM);
	}

	private void drawFooter(GuiGraphicsExtractor g) {
		int y = bodyBottom() + 5;
		Component info;
		if (mode == Mode.ITEMS) {
			int sum = totals.stream().mapToInt(t -> t.count).sum();
			info = Component.translatable("cobblecomfort.storage.footer.items", totals.size(), sum, feature().store().all().size());
		} else if (mode == Mode.TRADERS) {
			info = Component.translatable("cobblecomfort.trade.footer", tradeTab.results().size());
		} else if (mode == Mode.PETS) {
			info = Component.translatable("cobblecomfort.pets.footer", petTab.results().size());
		} else {
			info = Component.translatable("cobblecomfort.storage.footer.containers", results.size());
		}
		g.text(font, info, left + 8, y, MapUi.TEXT_DIM, false);
		Component hint = Component.translatable(switch (mode) {
			case ITEMS -> "cobblecomfort.storage.hint.items";
			case CONTAINERS -> "cobblecomfort.storage.hint.containers";
			case TRADERS -> "cobblecomfort.trade.hint";
			case PETS -> "cobblecomfort.pets.hint";
		}).withStyle(ChatFormatting.DARK_GRAY);
		g.text(font, hint, left + 8, y + 10, 0xFFFFFFFF, false);
		if (selected != null) {
			g.text(font, Component.translatable("cobblecomfort.storage.filter", selected.getHoverName()),
					left + 8 + font.width(info) + 8, y, 0xFFFFE066, false);
		}
	}

	private void drawTooltip(GuiGraphicsExtractor g, Object hovered, int mouseX, int mouseY) {
		if (hovered instanceof PetTab.Hovered pet) {
			g.setComponentTooltipForNextFrame(font, PetTab.tooltip(pet), mouseX, mouseY);
		} else if (hovered instanceof TradeTab.Hovered trader) {
			if (trader.offer() != null) {
				g.setComponentTooltipForNextFrame(font, TradeTab.offerTooltip(trader.result().merchant(), trader.offer()), mouseX, mouseY);
			}
		} else if (hovered instanceof Total total) {
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
			int row = (int) Math.floor((mouseY - bodyTop() - 2 + scroll.value()) / CELL);
			int index = row * columns + col;
			if (col >= 0 && col < columns && row >= 0 && index < totals.size()) {
				selected = totals.get(index).stack;
				mode = Mode.CONTAINERS;
				scroll.set(0);
				updateTabs();
				return true;
			}
			return false;
		}
		int index = (int) Math.floor((mouseY - bodyTop() - 2 + scroll.value()) / ROW);
		if (mode == Mode.TRADERS) {
			if (index >= 0 && index < tradeTab.results().size() && mouseX >= left + 6 && mouseX < left + panelWidth - 6) {
				showOnMap(tradeTab.results().get(index).merchant());
				return true;
			}
			return false;
		}
		if (mode == Mode.PETS) {
			if (index >= 0 && index < petTab.results().size() && mouseX >= left + 6 && mouseX < left + panelWidth - 6) {
				PetEntry pet = petTab.results().get(index);
				if (PetTab.overForget(left + panelWidth - 8, mouseX)) {
					PetFeature.INSTANCE.forget(pet);
				} else {
					showOnMap(pet);
				}
				return true;
			}
			return false;
		}
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
		scroll.scroll(-(scrollY * (mode == Mode.ITEMS ? CELL : ROW)));
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
