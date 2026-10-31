package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.features.invtools.GuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A tiny vanilla-style button with a clothes hanger on it, on the player in the inventory: opens the wardrobe. */
final class HangerButton extends AbstractButton {
	static final int SIZE = 11;
	private static final int ICON_HOVER = 0xFF202020;

	private final Runnable action;

	HangerButton(Component tooltip, Runnable action) {
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
		int c = hover ? ICON_HOVER : GuiStyle.LABEL;
		// The hook, then the shoulders sloping down to the bar.
		g.fill(x + 5, y + 2, x + 7, y + 3, c);
		g.fill(x + 6, y + 3, x + 7, y + 4, c);
		g.fill(x + 5, y + 4, x + 6, y + 5, c);
		g.fill(x + 4, y + 5, x + 7, y + 6, c);
		g.fill(x + 3, y + 6, x + 4, y + 7, c);
		g.fill(x + 7, y + 6, x + 8, y + 7, c);
		g.fill(x + 2, y + 7, x + 3, y + 8, c);
		g.fill(x + 8, y + 7, x + 9, y + 8, c);
		g.fill(x + 2, y + 8, x + 9, y + 9, c);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
