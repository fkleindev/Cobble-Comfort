package dev.cobblecomfort.features.invtools;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;

/**
 * A panel right of the GUI for every bundle in the open menu, showing what it holds. Clicking an item takes it out
 * (shift-click straight into the inventory), clicking with an item on the cursor puts it in. Everything goes through
 * the clicks and packets vanilla uses for bundles, so it works on any server.
 */
final class BundlePanels {
	private static final int GAP = 4;
	private static final int PAD = 5;
	private static final int CELL = 18;
	/** Bundle icon with the fill level beside it. */
	private static final int HEADER = 18;
	private static final int FEW_COLUMNS = 4;
	private static final int MANY_COLUMNS = 8;
	private static final int BAR_HEIGHT = 3;
	private static final int MAX_FILL = 64;
	private static final int MAIN_END = 36;
	private static final int HOVER = 0x80FFFFFF;
	private static final int BAR_BACK = 0xFF373737;
	private static final int OUTLINE = 0xFF1E1E1E;
	private static final int RADIUS = 3;
	/** How strongly face and slots take on the bundle's colour (as in the shulker box tooltips). */
	private static final float TINT = 0.45f;
	private static final int LEATHER = 0xFF9C6A43;

	private record Panel(Slot slot, ItemStack bundle, List<ItemStack> items, int x, int y, int columns, int rows) {
		int width() {
			return 2 * PAD + columns * CELL;
		}

		int height() {
			return 2 * PAD + HEADER + rows * CELL;
		}

		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + height();
		}

		int cellX(int i) {
			return x + PAD + 1 + i % columns * CELL;
		}

		int cellY(int i) {
			return y + PAD + HEADER + 1 + i / columns * CELL;
		}

		/** The index of the item under the mouse, -1 for none. */
		int itemAt(double mouseX, double mouseY) {
			for (int i = 0; i < items.size(); i++) {
				if (mouseX >= cellX(i) - 1 && mouseX < cellX(i) + 17 && mouseY >= cellY(i) - 1 && mouseY < cellY(i) + 17) {
					return i;
				}
			}
			return -1;
		}
	}

	private final InventoryToolsFeature feature;
	/** Whether the mouse press went to a panel, so its release is kept from the screen as well. */
	private boolean pressed;

	BundlePanels(InventoryToolsFeature feature) {
		this.feature = feature;
	}

	private static boolean isBundle(ItemStack stack) {
		return stack.getItem() instanceof BundleItem && stack.has(DataComponents.BUNDLE_CONTENTS);
	}

	static boolean hasBundles(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot.isActive() && isBundle(slot.getItem())) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- layout

	/** One panel per bundle, stacked down the right side of the GUI; a new column starts when the screen ends. */
	private List<Panel> layout(AbstractContainerScreen<?> screen) {
		List<Panel> panels = new ArrayList<>();
		if (!feature.bundlePanelsShown()) {
			return panels;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		int top = gui.cobblecomfort$topPos();
		int x = gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() + GAP;
		int y = top;
		int columnWidth = 0;
		for (Slot slot : screen.getMenu().slots) {
			ItemStack bundle = slot.getItem();
			if (!slot.isActive() || !isBundle(bundle)) {
				continue;
			}
			List<ItemStack> items = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopies().toList();
			Panel panel = panel(screen, slot, bundle, items, x, y);
			if (y > top && y + panel.height() > screen.height - 2) {
				x += columnWidth + GAP;
				y = top;
				columnWidth = 0;
				panel = panel(screen, slot, bundle, items, x, y);
			}
			panels.add(panel);
			columnWidth = Math.max(columnWidth, panel.width());
			y += panel.height() + GAP;
		}
		return panels;
	}

	/** Four columns, eight for a bundle with many kinds of items while the screen has room for them. */
	private static Panel panel(AbstractContainerScreen<?> screen, Slot slot, ItemStack bundle, List<ItemStack> items, int x, int y) {
		int columns = FEW_COLUMNS;
		if (items.size() > FEW_COLUMNS * FEW_COLUMNS) {
			int room = (screen.width - 2 - x - 2 * PAD) / CELL;
			columns = Math.clamp(room, FEW_COLUMNS, MANY_COLUMNS);
		}
		int rows = Math.max(1, (items.size() + columns - 1) / columns);
		return new Panel(slot, bundle, items, x, y, columns, rows);
	}

	/** How far the panels reach past the GUI's right edge (0 without any), so other things there can make room. */
	int width(AbstractContainerScreen<?> screen) {
		int right = 0;
		for (Panel panel : layout(screen)) {
			right = Math.max(right, panel.x() + panel.width());
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		return right == 0 ? 0 : right - gui.cobblecomfort$leftPos() - gui.cobblecomfort$imageWidth();
	}

	private Panel panelAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
		for (Panel panel : layout(screen)) {
			if (panel.contains(mouseX, mouseY)) {
				return panel;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- drawing

	void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		Slot hoveredSlot = gui.cobblecomfort$hoveredSlot();
		Font font = Minecraft.getInstance().font;
		int highlight = feature.config().highlightColor;
		boolean carrying = !screen.getMenu().getCarried().isEmpty();
		for (Panel panel : layout(screen)) {
			boolean hovered = panel.contains(mouseX, mouseY);
			// Panel and bundle slot light up together, so it is clear which panel belongs to which bundle.
			if (hovered) {
				Slot slot = panel.slot();
				frame(g, gui.cobblecomfort$leftPos() + slot.x - 1, gui.cobblecomfort$topPos() + slot.y - 1, CELL, CELL, highlight);
			} else if (panel.slot() == hoveredSlot) {
				RenderUtil.roundedFill(g, panel.x() - 1, panel.y() - 1, panel.x() + panel.width() + 1,
						panel.y() + panel.height() + 1, RADIUS + 1, highlight);
			}
			drawPanel(g, font, panel, mouseX, mouseY, hovered, carrying);
		}
	}

	/** The bundle's dye colour; the plain bundle is leather brown. */
	private static int color(ItemStack bundle) {
		String id = BuiltInRegistries.ITEM.getKey(bundle.getItem()).getPath();
		DyeColor dye = id.endsWith("_bundle") ? DyeColor.byName(id.substring(0, id.length() - "_bundle".length()), null) : null;
		return dye == null ? LEATHER : 0xFF000000 | dye.getTextureDiffuseColor();
	}

	private static void drawPanel(GuiGraphicsExtractor g, Font font, Panel panel, int mouseX, int mouseY, boolean hovered,
			boolean carrying) {
		int x0 = panel.x();
		int y0 = panel.y();
		int x1 = x0 + panel.width();
		int y1 = y0 + panel.height();
		// Rounded and tinted in the bundle's colour, like the shulker box tooltips.
		int tint = color(panel.bundle());
		RenderUtil.roundedFill(g, x0, y0, x1, y1, RADIUS, OUTLINE);
		RenderUtil.roundedFill(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, RADIUS - 1, RenderUtil.lerpColor(GuiStyle.FACE, tint, TINT));
		int slotColor = RenderUtil.lerpColor(GuiStyle.SLOT, tint, TINT);
		int shadow = RenderUtil.lerpColor(GuiStyle.SLOT_SHADOW, tint, TINT / 2);
		int light = RenderUtil.lerpColor(GuiStyle.HIGHLIGHT, tint, TINT / 2);
		g.item(panel.bundle(), x0 + PAD, y0 + PAD - 1);
		float fullness = BundleItem.getFullnessDisplay(panel.bundle());
		int textX = x0 + PAD + 19;
		g.text(font, Component.literal(Math.round(fullness * MAX_FILL) + "/" + MAX_FILL), textX, y0 + PAD, GuiStyle.LABEL, false);
		int barY = y0 + PAD + 10;
		int barRight = x1 - PAD - 1;
		g.fill(textX, barY, barRight, barY + BAR_HEIGHT, BAR_BACK);
		int fill = Math.round((barRight - textX) * Math.min(1f, fullness));
		if (fill > 0) {
			g.fill(textX, barY, textX + fill, barY + BAR_HEIGHT, 0xFF000000 | panel.bundle().getItem().getBarColor(panel.bundle()));
		}
		int cells = panel.columns() * panel.rows();
		for (int i = 0; i < cells; i++) {
			int cx = panel.cellX(i) - 1;
			int cy = panel.cellY(i) - 1;
			g.fill(cx, cy, cx + CELL, cy + CELL, light);
			g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, shadow);
			g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, slotColor);
		}
		int hoveredItem = hovered ? panel.itemAt(mouseX, mouseY) : -1;
		for (int i = 0; i < panel.items().size(); i++) {
			ItemStack stack = panel.items().get(i);
			g.item(stack, panel.cellX(i), panel.cellY(i));
			g.itemDecorations(font, stack, panel.cellX(i), panel.cellY(i));
			if (i == hoveredItem) {
				g.fill(panel.cellX(i), panel.cellY(i), panel.cellX(i) + 16, panel.cellY(i) + 16, HOVER);
			}
		}
		if (!hovered) {
			return;
		}
		if (hoveredItem >= 0) {
			// Like slots: no item tooltip while something is on the cursor.
			if (carrying) {
				return;
			}
			g.setTooltipForNextFrame(font, panel.items().get(hoveredItem), mouseX, mouseY);
		} else {
			g.setComponentTooltipForNextFrame(font, List.of(panel.bundle().getHoverName(),
					Component.translatable("cobblecomfort.invtools.bundle_take").withStyle(ChatFormatting.GRAY),
					Component.translatable("cobblecomfort.invtools.bundle_shift").withStyle(ChatFormatting.GRAY),
					Component.translatable("cobblecomfort.invtools.bundle_put").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
		}
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
		g.fill(x, y, x + width, y + 1, color);
		g.fill(x, y + height - 1, x + width, y + height, color);
		g.fill(x, y + 1, x + 1, y + height - 1, color);
		g.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}

	// ---------------------------------------------------------------- mouse

	/** Handles a click on a panel; false when the click is not on one and goes to the screen as usual. */
	boolean click(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
		Panel panel = panelAt(screen, event.x(), event.y());
		pressed = panel != null;
		Minecraft mc = Minecraft.getInstance();
		if (panel == null || mc.player == null || mc.gameMode == null || mc.getConnection() == null
				|| event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
			return pressed;
		}
		AbstractContainerMenu menu = screen.getMenu();
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			// A bundle on the cursor would take the clicked bundle into itself instead.
			if (!isBundle(carried)) {
				put(mc, menu, panel.slot());
			}
			return true;
		}
		int index = panel.itemAt(event.x(), event.y());
		if (index >= 0) {
			take(mc, menu, panel.slot(), index);
			if (event.hasShiftDown()) {
				toInventory(mc, menu, panel.slot());
			}
		}
		return true;
	}

	/** Keeps the release of a press that went to a panel from the screen. */
	boolean release() {
		boolean was = pressed;
		pressed = false;
		return was;
	}

	/** Selects the item in the bundle and right-clicks the bundle: vanilla hands the selected item to the cursor. */
	private static void take(Minecraft mc, AbstractContainerMenu menu, Slot slot, int index) {
		// Selecting toggles: a reset to "nothing selected" first makes sure the item ends up selected, not unselected.
		select(mc, slot, -1);
		select(mc, slot, index);
		mc.gameMode.handleContainerInput(menu.containerId, slot.index, 1, ContainerInput.PICKUP, mc.player);
	}

	private static void select(Minecraft mc, Slot slot, int index) {
		BundleItem.toggleSelectedItem(slot.getItem(), index);
		mc.getConnection().send(new ServerboundSelectBundleItemPacket(slot.index, index));
	}

	/** A left click on the bundle with an item on the cursor puts as much of it in as fits. */
	private static void put(Minecraft mc, AbstractContainerMenu menu, Slot slot) {
		mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
	}

	/** Puts the item on the cursor onto matching stacks in the inventory, then into free slots; the rest goes back. */
	private void toInventory(Minecraft mc, AbstractContainerMenu menu, Slot bundle) {
		for (boolean merge : new boolean[]{true, false}) {
			for (Slot slot : menu.slots) {
				ItemStack carried = menu.getCarried();
				if (carried.isEmpty()) {
					return;
				}
				if (slot == bundle || !(slot.container instanceof Inventory) || slot.getContainerSlot() >= MAIN_END
						|| !slot.isActive() || feature.isLocked(slot)) {
					continue;
				}
				ItemStack have = slot.getItem();
				boolean fits = merge
						? ItemStack.isSameItemSameComponents(have, carried) && have.getCount() < slot.getMaxStackSize(have)
						: have.isEmpty() && slot.mayPlace(carried);
				if (fits) {
					mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
				}
			}
		}
		if (!menu.getCarried().isEmpty()) {
			put(mc, menu, bundle);
		}
	}
}
