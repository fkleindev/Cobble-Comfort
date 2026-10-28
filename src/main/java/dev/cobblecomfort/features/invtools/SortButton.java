package dev.cobblecomfort.features.invtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A tiny vanilla-style button with a "sorted lines" icon. */
final class SortButton extends AbstractButton {
	static final int SIZE = 11;
	private static final int ICON_HOVER = 0xFF202020;

	private final Runnable action;

	SortButton(Component tooltip, Runnable action) {
		super(0, 0, SIZE, SIZE, tooltip);
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
		int icon = hover ? ICON_HOVER : GuiStyle.LABEL;
		// Three lines getting shorter: a "sort" symbol.
		g.fill(x + 3, y + 3, x + 8, y + 4, icon);
		g.fill(x + 3, y + 5, x + 7, y + 6, icon);
		g.fill(x + 3, y + 7, x + 6, y + 8, icon);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
