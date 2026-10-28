package dev.cobblecomfort.features.tracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** "Goal reached" notification in the style of an advancement toast. */
public final class TrackerToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
	private static final long DISPLAY_MS = 4000;

	private final ItemStack icon;
	private final Component title;
	private final Component text;
	private Visibility visibility = Visibility.SHOW;

	public TrackerToast(ItemStack icon, Component title, Component text) {
		this.icon = icon;
		this.title = title;
		this.text = text;
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long visibleTime) {
		visibility = visibleTime >= DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier()
				? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleTime) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		g.item(icon, 8, 8);
		g.text(font, title, 30, 7, 0xFFFFFF00, false);
		g.text(font, text, 30, 18, 0xFFFFFFFF, false);
	}
}
