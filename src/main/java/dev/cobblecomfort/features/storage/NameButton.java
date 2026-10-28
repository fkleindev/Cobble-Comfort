package dev.cobblecomfort.features.storage;

import dev.cobblecomfort.features.invtools.GuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A tiny vanilla-style button with a pencil, for naming the open container (or trader). */
public final class NameButton extends AbstractButton {
	public static final int SIZE = 11;
	private static final int ICON_HOVER = 0xFF202020;

	private final Runnable action;

	public NameButton(Component tooltip, Runnable action) {
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
		// A diagonal pencil from bottom left to top right.
		for (int i = 0; i < 5; i++) {
			g.fill(x + 3 + i, y + 7 - i, x + 5 + i, y + 8 - i, icon);
		}
		g.fill(x + 2, y + 8, x + 3, y + 9, icon);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
