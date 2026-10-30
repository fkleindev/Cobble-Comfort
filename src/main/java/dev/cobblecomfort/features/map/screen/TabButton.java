package dev.cobblecomfort.features.map.screen;

import dev.cobblecomfort.features.map.render.MapUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/** A tab in a row of tabs, like the map's dimension tabs: an item icon and a label, tinted while selected. */
public class TabButton extends AbstractWidget {
	private final ItemStack icon;
	private final BooleanSupplier selected;
	private final Runnable action;

	public TabButton(int x, int y, int width, int height, ItemStack icon, Component label, BooleanSupplier selected, Runnable action) {
		super(x, y, width, height, label);
		this.icon = icon;
		this.selected = selected;
		this.action = action;
	}

	/** The width a tab needs for its icon and label. */
	public static int widthFor(Font font, Component label) {
		return font.width(label) + 24;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		boolean on = selected.getAsBoolean();
		MapUi.buttonFace(g, getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused(), on, MapUi.ACCENT);
		MapUi.icon(g, icon, getX() + 10, getY() + height / 2f, 11);
		g.text(font, getMessage(), getX() + 18, getY() + (height - 8) / 2, on ? MapUi.TEXT : MapUi.TEXT_DIM, false);
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
