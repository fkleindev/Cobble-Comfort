package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.toasts.ToastSlot;
import dev.cobblecomfort.features.toasts.ToastsFeature;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Toasts slide eased and glide to their slot when the toasts above them have gone. */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class ToastInstanceMixin implements ToastSlot {
	@Shadow
	@Final
	private int occupiedSlotCount;

	@Unique
	private float cobblecomfort$y = Float.NaN;

	@Override
	public int cobblecomfort$slots() {
		return occupiedSlotCount;
	}

	@Override
	public float cobblecomfort$y() {
		return cobblecomfort$y;
	}

	@Override
	public void cobblecomfort$setY(float y) {
		cobblecomfort$y = y;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/toasts/Toast;xPos(IF)F"))
	private float cobblecomfort$easeSlide(Toast toast, int screenWidth, float portion, Operation<Float> original) {
		return original.call(toast, screenWidth, ToastsFeature.INSTANCE.slide(portion));
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/toasts/Toast;yPos(I)F"))
	private float cobblecomfort$glideSlot(Toast toast, int slot, Operation<Float> original) {
		return ToastsFeature.INSTANCE.toastY(this, slot, s -> original.call(toast, s));
	}
}
