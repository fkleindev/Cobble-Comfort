package dev.cobblecomfort.features.invtools;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.cobblecomfort.mixin.SlotAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The survival inventory with the main inventory split into sections by kind of item (blocks, tools, food, ...),
 * each a block of columns three slots high with its name above. Only where the slots are drawn changes, on this
 * client; the items stay in their slots. When an item of another kind lands in a slot, the slot slides over to
 * its new section. The hotbar stays as it is; the GUI grows wider when the sections need more room.
 */
public final class SectionView {
	/** Room for the section names between the upper part (armour, player, crafting) and the slots. */
	static final int HEADER = 12;
	private static final int CELL = 18;
	private static final int ROWS = 3;
	private static final int GAP = 6;
	private static final int SLOT_X = 8;
	private static final int SLOT_Y = 84 + HEADER;
	private static final int HOTBAR_Y = 142 + HEADER;
	private static final int VANILLA_WIDTH = 176;
	private static final int VANILLA_HEIGHT = 166;
	/** Rows of the vanilla texture kept on top, down to just above the first slot row. */
	private static final int UPPER = 83;
	private static final int BORDER = 4;
	/** Where a plain slot sits in the inventory texture. */
	private static final int SLOT_U = 7;
	private static final int SLOT_V = 83;
	private static final int TEXTURE_SIZE = 256;
	private static final int HOTBAR_SIZE = 9;
	private static final int MAIN_END = 36;
	/** A change waits this long before slots move, so quick clicks in a row do not shuffle the slots each time. */
	private static final long SETTLE_MS = 150;
	private static final float SLIDE_SPEED = 16f;

	private record Section(ItemCategory category, List<Slot> slots, int columns) {
	}

	private InventoryScreen screen;
	/** Vanilla positions of the slots moved, to put them back. */
	private final Map<Slot, int[]> home = new HashMap<>();
	private final Map<Slot, int[]> target = new HashMap<>();
	/** Positions on the way to the target, while sliding. */
	private final Map<Slot, float[]> shown = new HashMap<>();
	private List<Section> sections = List.of();
	private List<ItemCategory> laidOut = List.of();
	private int targetWidth = VANILLA_WIDTH;
	private long changedSince;
	private long lastFrame;

	/** Called before the inventory screen lays itself out: on opening, resizing and switching the view. */
	public void beforeInit(InventoryScreen screen, boolean on) {
		Minecraft mc = Minecraft.getInstance();
		// In creative mode the screen hands over to the creative inventory right away.
		if (on && mc.player != null && !mc.player.hasInfiniteMaterials()) {
			if (this.screen != screen) {
				restore();
			}
			this.screen = screen;
			layout(true);
		} else {
			if (this.screen == screen) {
				restore();
			}
			setSize(screen, VANILLA_WIDTH, VANILLA_HEIGHT);
		}
	}

	public boolean active(Object screen) {
		return screen != null && screen == this.screen;
	}

	/** Puts the slots back when the screen closes (the inventory menu lives on). */
	public void close(Object screen) {
		if (active(screen)) {
			restore();
		}
	}

	private void restore() {
		for (Map.Entry<Slot, int[]> entry : home.entrySet()) {
			move(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
		}
		home.clear();
		target.clear();
		shown.clear();
		sections = List.of();
		laidOut = List.of();
		screen = null;
	}

	// ---------------------------------------------------------------- layout

	private void layout(boolean snap) {
		AbstractContainerMenu menu = screen.getMenu();
		Map<ItemCategory, List<Slot>> groups = new EnumMap<>(ItemCategory.class);
		for (Slot slot : mainSlots(menu)) {
			groups.computeIfAbsent(ItemCategory.of(slot.getItem()), k -> new ArrayList<>()).add(slot);
		}
		List<Section> list = new ArrayList<>();
		int x = SLOT_X;
		for (Map.Entry<ItemCategory, List<Slot>> entry : groups.entrySet()) {
			List<Slot> slots = entry.getValue();
			int columns = (slots.size() + ROWS - 1) / ROWS;
			// Column by column, top to bottom, in inventory order.
			for (int i = 0; i < slots.size(); i++) {
				place(slots.get(i), x + i / ROWS * CELL, SLOT_Y + i % ROWS * CELL, snap);
			}
			list.add(new Section(entry.getKey(), slots, columns));
			x += columns * CELL + GAP;
		}
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory && slot.getContainerSlot() < HOTBAR_SIZE) {
				place(slot, SLOT_X + slot.getContainerSlot() * CELL, HOTBAR_Y, snap);
			}
		}
		sections = list;
		laidOut = kinds(menu);
		if (snap) {
			lastFrame = 0;
		}
		targetWidth = Math.max(VANILLA_WIDTH, x - GAP + SLOT_X - 2);
		fitWidth();
	}

	/** Wide enough for the sections, and for slots still sliding out of a section that got narrower. */
	private void fitWidth() {
		int right = 0;
		for (Slot slot : target.keySet()) {
			right = Math.max(right, slot.x);
		}
		// The slot's right edge plus the same margin vanilla has (176 for nine columns).
		setSize(screen, Math.max(targetWidth, right + CELL + SLOT_X - 2), VANILLA_HEIGHT + HEADER);
	}

	private void place(Slot slot, int x, int y, boolean snap) {
		home.putIfAbsent(slot, new int[]{slot.x, slot.y});
		target.put(slot, new int[]{x, y});
		if (snap || !shown.containsKey(slot)) {
			shown.put(slot, new float[]{x, y});
			move(slot, x, y);
		}
	}

	private static List<Slot> mainSlots(AbstractContainerMenu menu) {
		List<Slot> slots = new ArrayList<>();
		for (Slot slot : menu.slots) {
			int index = slot.getContainerSlot();
			if (slot.container instanceof Inventory && index >= HOTBAR_SIZE && index < MAIN_END) {
				slots.add(slot);
			}
		}
		return slots;
	}

	private static List<ItemCategory> kinds(AbstractContainerMenu menu) {
		List<ItemCategory> kinds = new ArrayList<>();
		for (Slot slot : mainSlots(menu)) {
			kinds.add(ItemCategory.of(slot.getItem()));
		}
		return kinds;
	}

	private static void move(Slot slot, int x, int y) {
		((SlotAccessor) slot).cobblecomfort$setX(x);
		((SlotAccessor) slot).cobblecomfort$setY(y);
	}

	private static void setSize(InventoryScreen screen, int width, int height) {
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		gui.cobblecomfort$setImageWidth(width);
		gui.cobblecomfort$setImageHeight(height);
	}

	// ---------------------------------------------------------------- every frame

	/**
	 * Lays the sections out anew once the kinds of items changed and nothing is held or being dragged (so slots never
	 * move away under a click), then lets the slots slide towards their places.
	 */
	public void frame(Object screen, GuiGraphicsExtractor g, int mouseX, int mouseY, boolean busy) {
		if (!active(screen)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!kinds(this.screen.getMenu()).equals(laidOut)) {
			if (changedSince == 0) {
				changedSince = now;
			}
			if (now - changedSince >= SETTLE_MS && !busy && idle()) {
				layout(false);
				changedSince = 0;
			}
		} else {
			changedSince = 0;
		}
		slide(now);
		tooltip(g, mouseX, mouseY);
	}

	private boolean idle() {
		Minecraft mc = Minecraft.getInstance();
		return screen.getMenu().getCarried().isEmpty() && !mc.mouseHandler.isLeftPressed() && !mc.mouseHandler.isRightPressed();
	}

	private void slide(long now) {
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float t = 1f - (float) Math.exp(-SLIDE_SPEED * dt);
		for (Map.Entry<Slot, int[]> entry : target.entrySet()) {
			int[] to = entry.getValue();
			float[] at = shown.get(entry.getKey());
			for (int i = 0; i < 2; i++) {
				at[i] += (to[i] - at[i]) * t;
				if (Math.abs(to[i] - at[i]) < 0.5f) {
					at[i] = to[i];
				}
			}
			move(entry.getKey(), Math.round(at[0]), Math.round(at[1]));
		}
		fitWidth();
	}

	/** The section name and how many slots it has, over its label. */
	private void tooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		int x = mouseX - gui.cobblecomfort$leftPos();
		int y = mouseY - gui.cobblecomfort$topPos();
		if (y < SLOT_Y - HEADER || y >= SLOT_Y - 1) {
			return;
		}
		for (Section section : sections) {
			int x0 = section.slots().getFirst().x;
			if (x >= x0 - 1 && x < x0 + section.columns() * CELL - 1) {
				g.setTooltipForNextFrame(Minecraft.getInstance().font, List.of(section.category().label(),
						Component.translatable("cobblecomfort.invtools.section_slots", section.slots().size())
								.withColor(0xFFA0A0A0)), Optional.empty(), mouseX, mouseY);
				return;
			}
		}
	}

	// ---------------------------------------------------------------- drawing

	/** Draws the GUI in place of vanilla's background texture; false when the view is off for this screen. */
	public boolean drawBackground(Object screen, GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y) {
		if (!active(screen)) {
			return false;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		int width = gui.cobblecomfort$imageWidth();
		int height = gui.cobblecomfort$imageHeight();
		panel(g, pipeline, texture, x, y, width, height);
		// The upper part as it is, without the frame.
		g.blit(pipeline, texture, x + BORDER, y + BORDER, BORDER, BORDER, VANILLA_WIDTH - 2 * BORDER, UPPER - BORDER,
				TEXTURE_SIZE, TEXTURE_SIZE);
		for (Slot slot : target.keySet()) {
			g.blit(pipeline, texture, x + slot.x - 1, y + slot.y - 1, SLOT_U, SLOT_V, CELL, CELL, TEXTURE_SIZE, TEXTURE_SIZE);
		}
		labels(g, x, y);
		return true;
	}

	/** The vanilla frame at any size: corners as they are, the edges stretched from a plain stretch of the texture. */
	private static void panel(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height) {
		int right = x + width - BORDER;
		int bottom = y + height - BORDER;
		int u = VANILLA_WIDTH - BORDER;
		int v = VANILLA_HEIGHT - BORDER;
		int plain = 100;
		g.blit(pipeline, texture, x, y, 0, 0, BORDER, BORDER, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, right, y, u, 0, BORDER, BORDER, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, x, bottom, 0, v, BORDER, BORDER, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, right, bottom, u, v, BORDER, BORDER, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, x + BORDER, y, plain, 0, width - 2 * BORDER, BORDER, 1, BORDER, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, x + BORDER, bottom, plain, v, width - 2 * BORDER, BORDER, 1, BORDER, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, x, y + BORDER, 0, plain, BORDER, height - 2 * BORDER, BORDER, 1, TEXTURE_SIZE, TEXTURE_SIZE);
		g.blit(pipeline, texture, right, y + BORDER, u, plain, BORDER, height - 2 * BORDER, BORDER, 1, TEXTURE_SIZE, TEXTURE_SIZE);
		g.fill(x + BORDER, y + BORDER, right, bottom, GuiStyle.FACE);
	}

	/** Each section's name above it, or a small icon of its kind when the name is too long for it. */
	private void labels(GuiGraphicsExtractor g, int x, int y) {
		Font font = Minecraft.getInstance().font;
		int labelY = y + SLOT_Y - HEADER + 1;
		for (Section section : sections) {
			// Follows the section's first slot while it slides.
			int labelX = x + section.slots().getFirst().x;
			Component name = section.category().label();
			if (font.width(name) <= section.columns() * CELL - 2) {
				g.text(font, name, labelX, labelY, GuiStyle.LABEL, false);
			} else if (section.category() != ItemCategory.EMPTY) {
				g.pose().pushMatrix();
				g.pose().translate(labelX, labelY);
				g.pose().scale(0.5f);
				g.item(section.category().icon(), 0, 0);
				g.pose().popMatrix();
			}
		}
	}

	/** The recipe book button keeps its place next to the crafting grid; vanilla puts it at half the screen height. */
	public int recipeButtonY(Object screen, int vanillaY) {
		return active(screen) ? ((ContainerScreenAccessor) screen).cobblecomfort$topPos() + VANILLA_HEIGHT / 2 - 22 : vanillaY;
	}
}
