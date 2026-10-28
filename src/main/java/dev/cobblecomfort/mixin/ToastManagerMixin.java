package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cobblecomfort.features.toasts.StackedToast;
import dev.cobblecomfort.features.toasts.ToastsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;
import java.util.List;

/** Toasts: layout from the top, shorter display time, and advancements made in a row stacked onto one toast. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Shadow
	@Final
	private List<?> visibleToasts;
	@Shadow
	@Final
	private Deque<Toast> queued;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cobblecomfort$beginToasts(GuiGraphicsExtractor g, CallbackInfo ci) {
		ToastsFeature.INSTANCE.beginToasts();
	}

	@ModifyReturnValue(method = "getNotificationDisplayTimeMultiplier", at = @At("RETURN"))
	private double cobblecomfort$displayTime(double multiplier) {
		return ToastsFeature.INSTANCE.displayTime(multiplier);
	}

	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$stackAdvancement(Toast toast, CallbackInfo ci) {
		if (!ToastsFeature.INSTANCE.stack() || !(toast instanceof AdvancementToast added)
				|| ((AdvancementToastInvoker) added).cobblecomfort$isChallenge()) {
			return;
		}
		for (Object instance : visibleToasts) {
			Toast shown = ((ToastInstanceAccessor) instance).cobblecomfort$toast();
			if (cobblecomfort$stacksWith(shown) && ((ToastInstanceAccessor) instance).cobblecomfort$visibility() == Toast.Visibility.SHOW) {
				((StackedToast) shown).cobblecomfort$addExtra();
				ci.cancel();
				return;
			}
		}
		for (Toast waiting : queued) {
			if (cobblecomfort$stacksWith(waiting)) {
				((StackedToast) waiting).cobblecomfort$addExtra();
				ci.cancel();
				return;
			}
		}
	}

	@Unique
	private static boolean cobblecomfort$stacksWith(Toast toast) {
		return toast instanceof AdvancementToast advancement && !((AdvancementToastInvoker) advancement).cobblecomfort$isChallenge();
	}
}
