package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.transitions.TransitionsFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Soft transitions in the HUD: sleeping, titles and the action bar. */
@Mixin(Hud.class)
public abstract class TransitionsMixin {
	@Shadow
	private Component overlayMessageString;
	@Shadow
	private int overlayMessageTime;

	@Inject(method = "extractSleepOverlay", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$sleep(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && TransitionsFeature.INSTANCE.drawSleep(g, mc.player.getSleepTimer())) {
			ci.cancel();
		}
	}

	@Inject(method = "setTitle", at = @At("HEAD"))
	private void cobblecomfort$title(Component title, CallbackInfo ci) {
		TransitionsFeature.INSTANCE.onTitle();
	}

	@Inject(method = "extractTitle", at = @At("HEAD"))
	private void cobblecomfort$beginTitle(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		TransitionsFeature.INSTANCE.beginTitle(g);
	}

	@Inject(method = "extractTitle", at = @At("RETURN"))
	private void cobblecomfort$endTitle(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		TransitionsFeature.INSTANCE.endTitle(g);
	}

	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void cobblecomfort$message(Component message, boolean animateColor, CallbackInfo ci) {
		TransitionsFeature.INSTANCE.onMessage(overlayMessageString, overlayMessageTime, message);
	}

	@WrapOperation(method = "extractOverlayMessage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
	private void cobblecomfort$slideMessage(GuiGraphicsExtractor g, Font font, Component message, int x, int y, int width, int color,
			Operation<Void> original) {
		TransitionsFeature.INSTANCE.drawMessage(g, message, color, (text, textColor) -> {
			int w = font.width(text);
			original.call(g, font, text, -w / 2, y, w, textColor);
		});
	}
}
