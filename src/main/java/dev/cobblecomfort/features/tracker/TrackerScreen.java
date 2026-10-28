package dev.cobblecomfort.features.tracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Goal list on the left, searchable item grid on the right. */
public class TrackerScreen extends Screen {
	private static final int PANEL_WIDTH = 372;
	private static final int COLUMN_GAP = 12;
	private static final int LIST_WIDTH = 180;
	private static final int ROW_HEIGHT = 20;
	private static final int CELL = 18;
	private static final int GRID_COLUMNS = 10;
	private static final int CONTROL = 10;
	private static final int TARGET_WIDTH = 34;
	private static final long STATUS_MS = 3000;

	private final TrackerFeature feature;
	private final Screen parent;
	private final List<Item> allItems = new ArrayList<>();
	private final Map<Item, Boolean> recipeCache = new HashMap<>();
	private List<Item> filtered = new ArrayList<>();

	private EditBox search;
	private EditBox quantity;
	private EditBox targetEdit;
	private TrackerData.Goal editingGoal;

	private int left, top, panelHeight;
	private int listX, listY, listHeight;
	private int gridX, gridY, gridRows;
	private int listScroll;
	private int gridScroll;
	private Component status;
	private long statusMs;

	public TrackerScreen(TrackerFeature feature, Screen parent) {
		super(Component.translatable("cobblecomfort.tracker.screen.title"));
		this.feature = feature;
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (allItems.isEmpty()) {
			for (Item item : BuiltInRegistries.ITEM) {
				if (item != Items.AIR && (minecraft.level == null || item.isEnabled(minecraft.level.enabledFeatures()))) {
					allItems.add(item);
				}
			}
		}
		panelHeight = Math.min(260, height - 16);
		left = (width - PANEL_WIDTH) / 2;
		top = (height - panelHeight) / 2;

		listX = left + 8;
		listY = top + 22;
		listHeight = panelHeight - 22 - 30;

		int rightX = left + 8 + LIST_WIDTH + COLUMN_GAP;
		int rightWidth = PANEL_WIDTH - 16 - LIST_WIDTH - COLUMN_GAP;
		search = new EditBox(font, rightX, top + 20, rightWidth - 50, 16, Component.translatable("cobblecomfort.tracker.screen.search"));
		search.setHint(Component.translatable("cobblecomfort.tracker.screen.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setResponder(text -> refilter());
		addRenderableWidget(search);

		quantity = new EditBox(font, rightX + rightWidth - 44, top + 20, 44, 16, Component.translatable("cobblecomfort.tracker.screen.quantity"));
		quantity.setValue(Integer.toString(feature.config().defaultQuantity));
		quantity.setMaxLength(6);
		addRenderableWidget(quantity);

		gridX = rightX + (rightWidth - GRID_COLUMNS * CELL) / 2;
		gridY = top + 42;
		gridRows = Math.max(1, (panelHeight - 42 - 30) / CELL);

		targetEdit = new EditBox(font, 0, 0, TARGET_WIDTH, 12, Component.empty());
		targetEdit.setMaxLength(7);
		targetEdit.visible = false;
		addRenderableWidget(targetEdit);

		int buttonY = top + panelHeight - 24;
		addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.tracker.screen.remove_done"),
				b -> feature.removeCompleted()).bounds(left + 8, buttonY, 120, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + PANEL_WIDTH - 8 - 90, buttonY, 90, 20).build());

		refilter();
		setInitialFocus(search);
	}

	private void refilter() {
		String query = search.getValue().trim().toLowerCase(Locale.ROOT);
		filtered = new ArrayList<>();
		for (Item item : allItems) {
			if (query.isEmpty()
					|| new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
					|| BuiltInRegistries.ITEM.getKey(item).toString().contains(query)) {
				filtered.add(item);
			}
		}
		gridScroll = 0;
	}

	private int quantity() {
		try {
			return Math.max(1, Integer.parseInt(quantity.getValue()));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private boolean hasRecipe(Item item) {
		return recipeCache.computeIfAbsent(item, RecipeLookup::hasRecipe);
	}

	private void showStatus(Component message) {
		status = message;
		statusMs = System.currentTimeMillis();
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(left, top, left + PANEL_WIDTH, top + panelHeight, 0xE0101018);
		g.outline(left, top, PANEL_WIDTH, panelHeight, 0xFF3A3A50);
		g.text(font, title, left + 8, top + 7, 0xFFFFFFFF, true);
		g.text(font, Component.translatable("cobblecomfort.tracker.screen.add_title"),
				left + 8 + LIST_WIDTH + COLUMN_GAP, top + 7, 0xFFAAAAAA, true);

		drawGoals(g, mouseX, mouseY);
		Item hovered = drawGrid(g, mouseX, mouseY);

		if (status != null && System.currentTimeMillis() - statusMs < STATUS_MS) {
			g.centeredText(font, status, left + PANEL_WIDTH / 2, top + panelHeight - 18, 0xFFFFFF55);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(new ItemStack(hovered).getHoverName());
			lines.add(Component.translatable("cobblecomfort.tracker.screen.hint_add", quantity()).withStyle(ChatFormatting.GRAY));
			lines.add(hasRecipe(hovered)
					? Component.translatable("cobblecomfort.tracker.screen.hint_recipe").withStyle(ChatFormatting.GRAY)
					: Component.translatable("cobblecomfort.tracker.screen.hint_no_recipe").withStyle(ChatFormatting.DARK_GRAY));
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}

	private void drawGoals(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<TrackerData.Goal> goals = feature.goals();
		g.fill(listX - 2, listY - 2, listX + LIST_WIDTH + 2, listY + listHeight + 2, 0x60000000);
		if (goals.isEmpty()) {
			g.text(font, Component.translatable("cobblecomfort.tracker.screen.empty"), listX + 4, listY + 4, 0xFF888888, false);
			return;
		}
		int visibleRows = listHeight / ROW_HEIGHT;
		listScroll = Math.clamp(listScroll, 0, Math.max(0, goals.size() - visibleRows));
		g.enableScissor(listX - 2, listY - 2, listX + LIST_WIDTH + 2, listY + listHeight + 2);
		for (int i = 0; i < visibleRows && i + listScroll < goals.size(); i++) {
			TrackerData.Goal goal = goals.get(i + listScroll);
			int y = listY + i * ROW_HEIGHT;
			boolean rowHovered = mouseX >= listX && mouseX < listX + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
			if (rowHovered) {
				g.fill(listX, y, listX + LIST_WIDTH, y + ROW_HEIGHT, 0x20FFFFFF);
			}
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(goal.item)));
			g.item(stack, listX + 2, y + 2);
			int have = feature.progress(goal);
			boolean done = have >= goal.target;
			int controlsX = listX + LIST_WIDTH - (CONTROL * 3 + TARGET_WIDTH + 6);
			String name = font.plainSubstrByWidth(stack.getHoverName().getString(), controlsX - (listX + 22) - 2);
			g.text(font, name, listX + 22, y + 2, done ? 0xFF55FF55 : 0xFFFFFFFF, false);
			int nearby = feature.nearbyCount(goal.item);
			Component haveText = Component.translatable("cobblecomfort.tracker.screen.have", have)
					.append(nearby > 0 ? Component.literal("  +" + nearby).withColor(feature.config().nearbyColor & 0xFFFFFF) : Component.empty());
			g.text(font, haveText, listX + 22, y + 11, 0xFF999999, false);

			int cy = y + 5;
			drawControl(g, "-", controlsX, cy, mouseX, mouseY);
			if (goal != editingGoal) {
				String target = Integer.toString(goal.target);
				int tx = controlsX + CONTROL + 2;
				boolean hoverTarget = hit(mouseX, mouseY, tx, cy - 1, TARGET_WIDTH, 11);
				g.fill(tx, cy - 1, tx + TARGET_WIDTH, cy + 10, hoverTarget ? 0x60FFFFFF : 0x30FFFFFF);
				g.centeredText(font, target, tx + TARGET_WIDTH / 2, cy + 1, 0xFFFFFFFF);
			}
			drawControl(g, "+", controlsX + CONTROL + TARGET_WIDTH + 4, cy, mouseX, mouseY);
			drawControl(g, "✕", controlsX + CONTROL * 2 + TARGET_WIDTH + 6, cy, mouseX, mouseY);
		}
		g.disableScissor();
	}

	private void drawControl(GuiGraphicsExtractor g, String label, int x, int y, int mouseX, int mouseY) {
		boolean hover = hit(mouseX, mouseY, x, y - 1, CONTROL, 11);
		g.fill(x, y - 1, x + CONTROL, y + 10, hover ? 0x80FFFFFF : 0x40FFFFFF);
		g.centeredText(font, label, x + CONTROL / 2 + 1, y + 1, 0xFFFFFFFF);
	}

	private Item drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int maxScroll = Math.max(0, (filtered.size() + GRID_COLUMNS - 1) / GRID_COLUMNS - gridRows);
		gridScroll = Math.clamp(gridScroll, 0, maxScroll);
		g.fill(gridX - 2, gridY - 2, gridX + GRID_COLUMNS * CELL + 2, gridY + gridRows * CELL + 2, 0x60000000);
		Item hovered = null;
		for (int row = 0; row < gridRows; row++) {
			for (int col = 0; col < GRID_COLUMNS; col++) {
				int index = (row + gridScroll) * GRID_COLUMNS + col;
				if (index >= filtered.size()) {
					break;
				}
				int x = gridX + col * CELL;
				int y = gridY + row * CELL;
				Item item = filtered.get(index);
				if (hit(mouseX, mouseY, x, y, CELL, CELL)) {
					g.fill(x, y, x + CELL, y + CELL, 0x50FFFFFF);
					hovered = item;
				}
				g.item(new ItemStack(item), x + 1, y + 1);
			}
		}
		if (maxScroll > 0) {
			int trackHeight = gridRows * CELL;
			int thumb = Math.max(8, trackHeight * gridRows / (gridRows + maxScroll));
			int thumbY = gridY + (trackHeight - thumb) * gridScroll / maxScroll;
			int sx = gridX + GRID_COLUMNS * CELL + 3;
			g.fill(sx, gridY, sx + 2, gridY + trackHeight, 0x40FFFFFF);
			g.fill(sx, thumbY, sx + 2, thumbY + thumb, 0xC0FFFFFF);
		}
		return hovered;
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (editingGoal != null && !targetEdit.isMouseOver(event.x(), event.y())) {
			commitTarget();
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		int step = event.hasShiftDown() ? 10 : 1;

		// Item grid
		if (hit(mx, my, gridX, gridY, GRID_COLUMNS * CELL, gridRows * CELL)) {
			int index = ((int) (my - gridY) / CELL + gridScroll) * GRID_COLUMNS + (int) (mx - gridX) / CELL;
			if (index >= 0 && index < filtered.size()) {
				Item item = filtered.get(index);
				if (right) {
					addIngredients(item);
				} else {
					feature.addGoal(item, quantity());
					showStatus(Component.translatable("cobblecomfort.tracker.screen.added", quantity(), new ItemStack(item).getHoverName()));
				}
				return true;
			}
		}

		// Goal list controls
		List<TrackerData.Goal> goals = feature.goals();
		int visibleRows = listHeight / ROW_HEIGHT;
		for (int i = 0; i < visibleRows && i + listScroll < goals.size(); i++) {
			TrackerData.Goal goal = goals.get(i + listScroll);
			int cy = listY + i * ROW_HEIGHT + 5;
			int controlsX = listX + LIST_WIDTH - (CONTROL * 3 + TARGET_WIDTH + 6);
			if (hit(mx, my, controlsX, cy - 1, CONTROL, 11)) {
				feature.setTarget(goal, goal.target - step);
				return true;
			}
			if (hit(mx, my, controlsX + CONTROL + 2, cy - 1, TARGET_WIDTH, 11)) {
				startEditing(goal, controlsX + CONTROL + 2, cy - 1);
				return true;
			}
			if (hit(mx, my, controlsX + CONTROL + TARGET_WIDTH + 4, cy - 1, CONTROL, 11)) {
				feature.setTarget(goal, goal.target + step);
				return true;
			}
			if (hit(mx, my, controlsX + CONTROL * 2 + TARGET_WIDTH + 6, cy - 1, CONTROL, 11)) {
				feature.removeGoal(goal);
				return true;
			}
		}
		return false;
	}

	private void addIngredients(Item item) {
		Optional<RecipeLookup.Found> recipe = RecipeLookup.find(item);
		if (recipe.isEmpty()) {
			showStatus(Component.translatable("cobblecomfort.tracker.screen.no_recipe", new ItemStack(item).getHoverName()));
			return;
		}
		int amount = quantity();
		recipe.get().scaledFor(amount).forEach(feature::addGoal);
		showStatus(Component.translatable("cobblecomfort.tracker.screen.added_recipe", amount, new ItemStack(item).getHoverName()));
	}

	private void startEditing(TrackerData.Goal goal, int x, int y) {
		editingGoal = goal;
		targetEdit.setX(x);
		targetEdit.setY(y);
		targetEdit.setValue(Integer.toString(goal.target));
		targetEdit.visible = true;
		setFocused(targetEdit);
		targetEdit.setFocused(true);
	}

	private void commitTarget() {
		if (editingGoal != null) {
			try {
				feature.setTarget(editingGoal, Integer.parseInt(targetEdit.getValue()));
			} catch (NumberFormatException ignored) {
				// Empty field: keep the old target.
			}
		}
		editingGoal = null;
		targetEdit.visible = false;
		targetEdit.setFocused(false);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (hit(mx, my, gridX, gridY, GRID_COLUMNS * CELL, gridRows * CELL)) {
			gridScroll -= (int) Math.signum(scrollY);
			return true;
		}
		if (hit(mx, my, listX, listY, LIST_WIDTH, listHeight)) {
			// Scrolling over a target changes it, elsewhere it scrolls the list.
			List<TrackerData.Goal> goals = feature.goals();
			int controlsX = listX + LIST_WIDTH - (CONTROL * 3 + TARGET_WIDTH + 6);
			int row = (int) (my - listY) / ROW_HEIGHT + listScroll;
			if (row < goals.size() && hit(mx, my, controlsX + CONTROL + 2, listY, TARGET_WIDTH, listHeight)) {
				TrackerData.Goal goal = goals.get(row);
				feature.setTarget(goal, goal.target + (int) Math.signum(scrollY) * (minecraft.hasShiftDown() ? 10 : 1));
			} else {
				listScroll -= (int) Math.signum(scrollY);
			}
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editingGoal != null && (event.isConfirmation() || event.isEscape())) {
			if (event.isConfirmation()) {
				commitTarget();
			} else {
				editingGoal = null;
				targetEdit.visible = false;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		commitTarget();
		feature.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
