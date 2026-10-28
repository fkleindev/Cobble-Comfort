package dev.cobblecomfort.features.map.screen;

import dev.cobblecomfort.features.map.render.MapUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A button in the map screens' style: a rounded face tinted in a colour (accent, or red for deleting). */
public class FlatButton extends AbstractWidget {
	private final Runnable action;
	private final int color;

	public FlatButton(int x, int y, int width, int height, Component label, int color, Runnable action) {
		super(x, y, width, height, label);
		this.action = action;
		this.color = color;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		MapUi.textButton(g, Minecraft.getInstance().font, getMessage(), getX(), getY(), getX() + width, getY() + height,
				isHoveredOrFocused(), active, color);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
