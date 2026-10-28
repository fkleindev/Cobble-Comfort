package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import dev.cobblecomfort.features.smoothgui.WidgetAnim;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Slider handles glide to a clicked or keyed value; while dragged they follow the mouse directly. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderGlideMixin {
	@Shadow
	private boolean dragging;

	@Unique
	private double cobblecomfort$shown = Double.NaN;
	@Unique
	private long cobblecomfort$nanos;

	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/gui/components/AbstractSliderButton;value:D", opcode = Opcodes.GETFIELD))
	private double cobblecomfort$glideHandle(AbstractSliderButton slider, Operation<Double> original) {
		double value = original.call(slider);
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(cobblecomfort$nanos, now);
		cobblecomfort$nanos = now;
		cobblecomfort$shown = dragging || !WidgetAnim.enabled(WidgetAnim.Kind.SLIDERS) ? value
				: WidgetAnim.step(cobblecomfort$shown, value, seconds);
		return cobblecomfort$shown;
	}
}
