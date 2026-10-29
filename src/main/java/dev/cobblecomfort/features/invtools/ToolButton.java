package dev.cobblecomfort.features.invtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A tiny vanilla-style button with a pixel icon: sort, deposit (arrow up) or restock (arrow down). */
final class ToolButton extends AbstractButton {
	static final int SIZE = 11;
	private static final int ICON_HOVER = 0xFF202020;

	enum Icon {
		SORT, DEPOSIT, RESTOCK
	}

	private final Icon icon;
	private final Runnable action;

	ToolButton(Icon icon, Component tooltip, Runnable action) {
		super(0, 0, SIZE, SIZE, tooltip);
		this.icon = icon;
		this.action = action;
		setTooltip(Tooltip.create(tooltip));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = getX();
		int y = getY();
		boolean hover = isHoveredOrFocused();
		GuiStyle.button(g, x, y, x + SIZE, y + SIZE, hover);
		int color = hover ? ICON_HOVER : GuiStyle.LABEL;
		switch (icon) {
			case SORT -> {
				// Three lines getting shorter: a "sort" symbol.
				g.fill(x + 3, y + 3, x + 8, y + 4, color);
				g.fill(x + 3, y + 5, x + 7, y + 6, color);
				g.fill(x + 3, y + 7, x + 6, y + 8, color);
			}
			case DEPOSIT -> arrow(g, x, y, color, true);
			case RESTOCK -> arrow(g, x, y, color, false);
		}
	}

	/** A small arrow, up (into the container above) or down (into the inventory below). */
	private static void arrow(GuiGraphicsExtractor g, int x, int y, int color, boolean up) {
		for (int row = 0; row < 3; row++) {
			int ry = up ? y + 3 + row : y + 7 - row;
			g.fill(x + 5 - row, ry, x + 6 + row, ry + 1, color);
		}
		g.fill(x + 5, up ? y + 6 : y + 3, x + 6, up ? y + 9 : y + 6, color);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
