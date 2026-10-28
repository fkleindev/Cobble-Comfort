package dev.cobblecomfort.features.invtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws in the style of vanilla container GUIs: grey face, white top-left highlight, dark bottom-right shadow. */
public final class GuiStyle {
	public static final int OUTLINE = 0xFF000000;
	public static final int FACE = 0xFFC6C6C6;
	public static final int HIGHLIGHT = 0xFFFFFFFF;
	public static final int SHADOW = 0xFF555555;
	public static final int SLOT = 0xFF8B8B8B;
	public static final int SLOT_SHADOW = 0xFF373737;
	public static final int LABEL = 0xFF404040;

	private GuiStyle() {
	}

	/**
	 * A tab sticking out of the top edge of a GUI: outline and bevel on top, left and right, open at the bottom so
	 * it merges with the GUI below. {@code join} is the GUI's top edge; the tab covers its border between the sides.
	 */
	public static void tab(GuiGraphicsExtractor g, int x0, int y0, int x1, int join) {
		int bottom = join + 3;
		g.fill(x0 + 1, y0, x1 - 1, y0 + 1, OUTLINE);
		g.fill(x0, y0 + 1, x0 + 1, bottom, OUTLINE);
		g.fill(x1 - 1, y0 + 1, x1, bottom, OUTLINE);
		g.fill(x0 + 1, y0 + 1, x1 - 1, bottom, FACE);
		g.fill(x0 + 1, y0 + 1, x1 - 3, y0 + 3, HIGHLIGHT);
		g.fill(x0 + 1, y0 + 3, x0 + 3, bottom, HIGHLIGHT);
		g.fill(x1 - 3, y0 + 3, x1 - 1, bottom, SHADOW);
	}

	/** A sunken field like an inventory slot. */
	public static void inset(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, HIGHLIGHT);
		g.fill(x0, y0, x1 - 1, y1 - 1, SLOT_SHADOW);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SLOT);
	}

	/** A small raised button with a black outline and rounded corners. */
	public static void button(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean hovered) {
		g.fill(x0 + 1, y0, x1 - 1, y1, OUTLINE);
		g.fill(x0, y0 + 1, x1, y1 - 1, OUTLINE);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, hovered ? HIGHLIGHT : SHADOW);
		g.fill(x0 + 1, y0 + 1, x1 - 2, y1 - 2, HIGHLIGHT);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, hovered ? 0xFFDADADA : FACE);
	}
}
