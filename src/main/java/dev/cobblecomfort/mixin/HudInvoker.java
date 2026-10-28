package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Hud.class)
public interface HudInvoker {
	/** Vanilla spyglass mask; scale 1 is the fully opened scope. */
	@Invoker("extractSpyglassOverlay")
	void cobblecomfort$extractSpyglassOverlay(GuiGraphicsExtractor g, float scale);
}
