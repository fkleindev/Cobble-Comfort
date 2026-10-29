package dev.cobblecomfort.features.invtools;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Inventory search, sorting (button and middle click) and lockable player inventory slots. */
public final class InventoryToolsFeature extends Feature<InventoryToolsConfig> {
	public static final InventoryToolsFeature INSTANCE = new InventoryToolsFeature();

	private static final int TAB_WIDTH = 120;
	/** How far the search tab sticks out above the GUI. */
	private static final int TAB_HEIGHT = 17;
	private static final int TAB_INSET = 4;
	private static final int TAB_RIGHT_MARGIN = 4;
	private static final int FIELD_HEIGHT = 12;
	private static final int HINT_COLOR = 0xFFC0C0C0;
	private static final int OFFHAND_INDEX = 40;
	private static final int HOTBAR_SIZE = 9;
	private static final int MAIN_END = 36;
	private static final int POP_STAGGER_MS = 12;
	private static final int FLY_STAGGER_MS = 35;

	private final ItemSearch search = new ItemSearch();
	private final SlotLocks locks = new SlotLocks();
	private String rememberedQuery = "";
	private Sorter sorter;
	/** Slots already shift-clicked during the current shift-drag, null while none is going on. */
	private Set<Slot> dragged;

	private InventoryToolsFeature() {
		super("inventory_tools", InventoryToolsConfig.class, InventoryToolsConfig::new);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen)) {
				onScreenInit(container);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> locks.load());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> locks.unload());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tickSorter());
	}

	@Override
	public ConfigCategory buildCategory() {
		return InventoryToolsConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- screen setup

	private void onScreenInit(AbstractContainerScreen<?> screen) {
		if (!isEnabled()) {
			return;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		Minecraft mc = Minecraft.getInstance();
		EditBox box;
		if (config.search) {
			box = new EditBox(mc.font, TAB_WIDTH - 2 * TAB_INSET - 6, FIELD_HEIGHT - 3, Component.translatable("cobblecomfort.invtools.search"));
			box.setHint(Component.translatable("cobblecomfort.invtools.search_hint").withColor(HINT_COLOR));
			box.setBordered(false);
			box.setTextColor(0xFFFFFFFF);
			box.setMaxLength(50);
			box.setValue(config.rememberQuery ? rememberedQuery : "");
			box.setResponder(value -> {
				search.setQuery(value);
				rememberedQuery = value;
			});
			search.setQuery(box.getValue());
			Screens.getWidgets(screen).add(box);
		} else {
			box = null;
			search.setQuery("");
		}
		List<ToolButton> buttons = new ArrayList<>();
		List<List<Slot>> regions = new ArrayList<>();
		if (config.sortButtons) {
			for (List<Slot> region : regions(screen.getMenu())) {
				ToolButton button = new ToolButton(ToolButton.Icon.SORT, Component.translatable("cobblecomfort.invtools.sort"), () -> sort(screen.getMenu(), region));
				buttons.add(button);
				regions.add(region);
				Screens.getWidgets(screen).add(button);
			}
		}
		// Deposit and restock sit left of the inventory's sort button, in storage containers only.
		List<ToolButton> transfer = new ArrayList<>();
		List<Slot> mainInventory = playerSlots(screen.getMenu(), false);
		if (config.transferButtons && !containerSlots(screen.getMenu()).isEmpty() && !mainInventory.isEmpty()) {
			transfer.add(new ToolButton(ToolButton.Icon.DEPOSIT, Component.translatable("cobblecomfort.invtools.deposit"),
					() -> deposit(screen.getMenu())));
			transfer.add(new ToolButton(ToolButton.Icon.RESTOCK, Component.translatable("cobblecomfort.invtools.restock"),
					() -> restock(screen.getMenu())));
			transfer.forEach(Screens.getWidgets(screen)::add);
		}
		int transferOffset = config.sortButtons ? 1 : 0;
		// Keep the widgets next to the GUI even when it moves (e.g. when the recipe book opens).
		ScreenEvents.beforeExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			if (box != null) {
				box.setX(tabX0(gui) + TAB_INSET + 3);
				box.setY(gui.cobblecomfort$topPos() - TAB_HEIGHT + TAB_INSET + 2);
			}
			for (int i = 0; i < buttons.size(); i++) {
				placeButton(buttons.get(i), regions.get(i), gui);
			}
			for (int i = 0; i < transfer.size(); i++) {
				ToolButton button = transfer.get(i);
				placeButton(button, mainInventory, gui);
				button.setX(button.getX() - (i + transferOffset) * (ToolButton.SIZE + 1));
			}
		});
		if (box != null) {
			ScreenEvents.afterBackground(screen).register((s, g, mouseX, mouseY, delta) -> drawSearchTab(g, gui));
		}
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
			if (box == null) {
				return true;
			}
			if (key.key() == InputConstants.KEY_F && key.hasControlDown()) {
				screen.setFocused(box);
				return false;
			}
			if (box.isFocused() && key.key() != InputConstants.KEY_ESCAPE) {
				// While typing, keys must not close the screen, swap hotbar slots or drop items.
				box.keyPressed(key);
				return false;
			}
			return true;
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> onClick(screen, event.button(), event.hasAltDown()));
		ScreenMouseEvents.afterMouseClick(screen).register((s, event, consumed) -> {
			startShiftDrag(screen, event.button(), event.hasShiftDown());
			return consumed;
		});
		ScreenMouseEvents.afterMouseDrag(screen).register((s, event, dx, dy, consumed) -> {
			shiftDrag(screen, event);
			return consumed;
		});
		ScreenMouseEvents.afterMouseRelease(screen).register((s, event, consumed) -> {
			dragged = null;
			return consumed;
		});
		ScreenEvents.remove(screen).register(s -> {
			dragged = null;
			if (!config.rememberQuery) {
				rememberedQuery = "";
			}
			search.setQuery("");
		});
	}

	private static int tabX0(ContainerScreenAccessor gui) {
		return gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() - TAB_RIGHT_MARGIN - TAB_WIDTH;
	}

	/** The search field sits in a tab attached to the top edge of the GUI, styled like the GUI itself. */
	private static void drawSearchTab(GuiGraphicsExtractor g, ContainerScreenAccessor gui) {
		int x0 = tabX0(gui);
		int top = gui.cobblecomfort$topPos();
		int y0 = top - TAB_HEIGHT;
		GuiStyle.tab(g, x0, y0, x0 + TAB_WIDTH, top);
		GuiStyle.inset(g, x0 + TAB_INSET, y0 + TAB_INSET, x0 + TAB_WIDTH - TAB_INSET, y0 + TAB_INSET + FIELD_HEIGHT);
	}

	/** The current inventory search text (lower case), empty when not searching. */
	public String activeQuery() {
		return isEnabled() ? search.query() : "";
	}

	/** Height the search tab adds above container GUIs (0 without it), so other things above the GUI can make room. */
	public int searchTabHeight() {
		return isEnabled() && config.search ? TAB_HEIGHT : 0;
	}

	private static void placeButton(ToolButton button, List<Slot> region, ContainerScreenAccessor gui) {
		int maxX = 0;
		int minY = Integer.MAX_VALUE;
		for (Slot slot : region) {
			maxX = Math.max(maxX, slot.x + 16);
			minY = Math.min(minY, slot.y);
		}
		button.setX(gui.cobblecomfort$leftPos() + maxX - ToolButton.SIZE);
		button.setY(gui.cobblecomfort$topPos() + minY - ToolButton.SIZE - 2);
	}

	/** Alt+click locks a slot, middle click sorts the area under the cursor. Returns false to swallow the click. */
	private boolean onClick(AbstractContainerScreen<?> screen, int button, boolean alt) {
		Slot hovered = ((ContainerScreenAccessor) screen).cobblecomfort$hoveredSlot();
		if (!isEnabled() || hovered == null) {
			return true;
		}
		if (config.locks && alt && button == InputConstants.MOUSE_BUTTON_LEFT && hovered.container instanceof Inventory) {
			toggleLock(hovered.getContainerSlot());
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (config.middleClickSort && button == InputConstants.MOUSE_BUTTON_MIDDLE && mc.player != null
				&& !mc.player.hasInfiniteMaterials()) {
			for (List<Slot> region : regions(screen.getMenu())) {
				if (region.contains(hovered)) {
					sort(screen.getMenu(), region);
					return false;
				}
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- shift-drag

	/** A shift-click on a slot starts a drag: every further slot the mouse passes over is shift-clicked too. */
	private void startShiftDrag(AbstractContainerScreen<?> screen, int button, boolean shift) {
		Slot hovered = ((ContainerScreenAccessor) screen).cobblecomfort$hoveredSlot();
		dragged = null;
		if (isEnabled() && config.shiftDrag && shift && button == InputConstants.MOUSE_BUTTON_LEFT && hovered != null
				&& screen.getMenu().getCarried().isEmpty()) {
			// Vanilla has just moved the clicked slot itself.
			dragged = new HashSet<>();
			dragged.add(hovered);
		}
	}

	private void shiftDrag(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
		Minecraft mc = Minecraft.getInstance();
		if (dragged == null || mc.player == null || mc.gameMode == null) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !event.hasShiftDown() || !menu.getCarried().isEmpty()) {
			dragged = null;
			return;
		}
		Slot slot = slotAt((ContainerScreenAccessor) screen, menu, event.x(), event.y());
		// Crafting results are left out: shift-clicking them crafts as much as possible.
		if (slot == null || dragged.contains(slot) || !slot.hasItem() || slot instanceof ResultSlot || !slot.mayPickup(mc.player)) {
			return;
		}
		dragged.add(slot);
		mc.gameMode.handleContainerInput(menu.containerId, slot.index, InputConstants.MOUSE_BUTTON_LEFT, ContainerInput.QUICK_MOVE, mc.player);
	}

	private static Slot slotAt(ContainerScreenAccessor gui, AbstractContainerMenu menu, double mouseX, double mouseY) {
		double x = mouseX - gui.cobblecomfort$leftPos();
		double y = mouseY - gui.cobblecomfort$topPos();
		for (Slot slot : menu.slots) {
			if (slot.isActive() && x >= slot.x - 1 && x < slot.x + 17 && y >= slot.y - 1 && y < slot.y + 17) {
				return slot;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- deposit and restock

	/** The storage slots of a chest, barrel, shulker box, hopper, dispenser or an animal's chest; empty elsewhere. */
	private static List<Slot> containerSlots(AbstractContainerMenu menu) {
		boolean storage = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu
				|| menu instanceof DispenserMenu;
		boolean mount = menu instanceof AbstractMountInventoryMenu;
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			// On mounts only the chest slots count, not saddle and armour (special slot classes).
			if (!(slot.container instanceof Inventory) && (storage || (mount && slot.getClass() == Slot.class))) {
				slots.add(slot);
			}
		}
		return slots;
	}

	/** The player's main inventory slots in a menu, with the hotbar if asked for. */
	private static List<Slot> playerSlots(AbstractContainerMenu menu, boolean hotbar) {
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) {
				int index = slot.getContainerSlot();
				if ((index >= HOTBAR_SIZE && index < MAIN_END) || (hotbar && index < HOTBAR_SIZE)) {
					slots.add(slot);
				}
			}
		}
		return slots;
	}

	/** Shift-clicks every unlocked inventory stack of an item the container already holds. */
	private void deposit(AbstractContainerMenu menu) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || !menu.getCarried().isEmpty() || sorter != null) {
			return;
		}
		List<Slot> container = containerSlots(menu);
		Set<Item> present = new HashSet<>();
		for (Slot slot : container) {
			if (slot.hasItem()) {
				present.add(slot.getItem().getItem());
			}
		}
		List<ItemStack> before = snapshot(container);
		boolean flying = InventoryFxFeature.INSTANCE.beginBatch(menu);
		for (Slot slot : playerSlots(menu, config.depositHotbar)) {
			if (slot.hasItem() && !isLocked(slot) && present.contains(slot.getItem().getItem())) {
				mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
			}
		}
		finishTransfer(menu, flying, container, before);
	}

	/** Tops up the inventory's stacks that are not full from matching stacks in the container. */
	private void restock(AbstractContainerMenu menu) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || !menu.getCarried().isEmpty() || sorter != null) {
			return;
		}
		List<Slot> container = containerSlots(menu);
		List<Slot> inventory = playerSlots(menu, true);
		List<ItemStack> before = snapshot(inventory);
		boolean flying = InventoryFxFeature.INSTANCE.beginBatch(menu);
		for (Slot target : inventory) {
			for (Slot source : container) {
				ItemStack have = target.getItem();
				if (have.isEmpty() || have.getCount() >= target.getMaxStackSize(have)) {
					break;
				}
				if (!ItemStack.isSameItemSameComponents(have, source.getItem()) || !source.mayPickup(mc.player)) {
					continue;
				}
				click(mc, menu, source);
				click(mc, menu, target);
				if (!menu.getCarried().isEmpty()) {
					click(mc, menu, source);
				}
			}
		}
		finishTransfer(menu, flying, inventory, before);
	}

	private static void click(Minecraft mc, AbstractContainerMenu menu, Slot slot) {
		mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
	}

	private static List<ItemStack> snapshot(List<Slot> slots) {
		List<ItemStack> stacks = new ArrayList<>();
		for (Slot slot : slots) {
			stacks.add(slot.getItem().copy());
		}
		return stacks;
	}

	/** The items fly from slot to slot one after another; without flying items the slots that got something pop. */
	private void finishTransfer(AbstractContainerMenu menu, boolean flying, List<Slot> receivers, List<ItemStack> before) {
		if (flying) {
			InventoryFxFeature.INSTANCE.endBatch(menu, FLY_STAGGER_MS);
		} else {
			popChanged(receivers, before);
		}
	}

	/** Lets the slots that got something pop, like after sorting. */
	private void popChanged(List<Slot> slots, List<ItemStack> before) {
		if (!config.sortAnimation) {
			return;
		}
		List<Slot> changed = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack now = slots.get(i).getItem();
			if (now.getCount() > before.get(i).getCount() || (!now.isEmpty() && !ItemStack.isSameItemSameComponents(now, before.get(i)))) {
				changed.add(slots.get(i));
			}
		}
		InventoryFxFeature.INSTANCE.popSlots(changed, POP_STAGGER_MS);
	}

	// ---------------------------------------------------------------- sorting

	/** The sortable areas of a menu: the container (chests, barrels, shulker boxes) and the player's main inventory. */
	private List<List<Slot>> regions(AbstractContainerMenu menu) {
		List<Slot> container = new ArrayList<>();
		List<Slot> player = new ArrayList<>();
		boolean storage = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) {
				int index = slot.getContainerSlot();
				if ((index >= HOTBAR_SIZE && index < MAIN_END) || (config.sortHotbar && index < HOTBAR_SIZE)) {
					player.add(slot);
				}
			} else if (storage) {
				container.add(slot);
			}
		}
		List<List<Slot>> regions = new ArrayList<>();
		if (!container.isEmpty()) {
			regions.add(container);
		}
		if (!player.isEmpty()) {
			regions.add(player);
		}
		return regions;
	}

	private void sort(AbstractContainerMenu menu, List<Slot> region) {
		if (!menu.getCarried().isEmpty()) {
			return;
		}
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : region) {
			if (!isLocked(slot)) {
				slots.add(slot);
			}
		}
		sorter = new Sorter(menu, slots, config.sortOrder);
		if (config.actionsPerTick <= 0) {
			while (sorter != null && sorter.step()) {
				// Instant: run the whole job now.
			}
			finishSort(slots);
		}
	}

	private void tickSorter() {
		if (sorter == null) {
			return;
		}
		for (int i = 0; i < config.actionsPerTick; i++) {
			if (!sorter.step()) {
				finishSort(sorter.slots());
				return;
			}
		}
	}

	private void finishSort(List<Slot> slots) {
		sorter = null;
		if (config.sortAnimation) {
			InventoryFxFeature.INSTANCE.popSlots(slots, POP_STAGGER_MS);
		}
	}

	// ---------------------------------------------------------------- locks

	private boolean isLocked(Slot slot) {
		return config.locks && slot.container instanceof Inventory && locks.isLocked(slot.getContainerSlot());
	}

	/** Locks or unlocks an inventory index. */
	public void toggleLock(int index) {
		locks.toggle(index);
	}

	/** Whether an inventory index (0-8 hotbar, 9-35 main, 40 offhand) is locked. */
	public boolean isLockedIndex(int index) {
		return isEnabled() && config.locks && locks.isLocked(index);
	}

	/** Whether a container input would move or drop an item out of a locked slot. */
	public boolean blocksInput(AbstractContainerMenu menu, int slotIndex, int button, ContainerInput input) {
		if (!isEnabled() || !config.locks || !config.lockProtection || sorter != null) {
			return false;
		}
		Slot slot = slotIndex >= 0 && slotIndex < menu.slots.size() ? menu.slots.get(slotIndex) : null;
		return switch (input) {
			case QUICK_MOVE, THROW -> slot != null && isLocked(slot);
			case SWAP -> (slot != null && isLocked(slot)) || locks.isLocked(button == OFFHAND_INDEX ? OFFHAND_INDEX : button);
			case PICKUP_ALL -> collectsFromLocked(menu);
			default -> false;
		};
	}

	/** Double-click collecting would pull matching items out of locked slots. */
	private boolean collectsFromLocked(AbstractContainerMenu menu) {
		ItemStack carried = menu.getCarried();
		for (Slot slot : menu.slots) {
			if (isLocked(slot) && ItemStack.isSameItemSameComponents(slot.getItem(), carried)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the drop key must not drop the selected hotbar item. */
	public boolean blocksWorldDrop(int selectedSlot) {
		return isLockedIndex(selectedSlot) && config.lockProtection && config.lockWorldDrop;
	}

	// ---------------------------------------------------------------- drawing

	/** Search highlight and lock marker of a container slot (slot-local coordinates). */
	public void decorateSlot(GuiGraphicsExtractor g, Object screen, Slot slot) {
		if (!isEnabled() || screen instanceof CreativeModeInventoryScreen) {
			return;
		}
		if (search.active()) {
			if (search.matches(slot.getItem(), config)) {
				int color = config.highlightColor;
				g.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, color);
				g.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, color);
				g.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, color);
				g.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, color);
				g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, RenderUtil.withAlpha(color, 0.18f));
			} else {
				g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, config.dimColor);
			}
		}
		if (isLocked(slot)) {
			drawLock(g, slot.x, slot.y);
		}
	}

	/** Lock marker on a hotbar slot. */
	public void decorateHotbarSlot(GuiGraphicsExtractor g, int index, int x, int y) {
		if (isLockedIndex(index) && config.lockInHotbar) {
			drawLock(g, x, y);
		}
	}

	/** A small padlock in the top-left corner and a thin frame in the lock colour. */
	private void drawLock(GuiGraphicsExtractor g, int x, int y) {
		int color = config.lockColor;
		int frame = RenderUtil.withAlpha(color, 0.45f);
		g.fill(x, y, x + 16, y + 1, frame);
		g.fill(x, y + 15, x + 16, y + 16, frame);
		g.fill(x, y + 1, x + 1, y + 15, frame);
		g.fill(x + 15, y + 1, x + 16, y + 15, frame);
		// Shackle and body of the padlock, with a dark outline.
		g.fill(x, y, x + 7, y + 8, 0xC0000000);
		g.fill(x + 2, y + 1, x + 5, y + 2, color);
		g.fill(x + 1, y + 2, x + 2, y + 4, color);
		g.fill(x + 5, y + 2, x + 6, y + 4, color);
		g.fill(x + 1, y + 4, x + 6, y + 7, color);
		g.fill(x + 3, y + 5, x + 4, y + 6, 0xFF000000);
	}
}
