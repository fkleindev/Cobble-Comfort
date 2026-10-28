package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.ScrollTicker;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth scrolling for vanilla lists (servers, worlds, options, packs, controls, ...): the wheel sets a target. */
@Mixin(AbstractScrollArea.class)
public abstract class AbstractScrollAreaMixin implements ScrollTicker {
	@Unique
	private double cobblecomfort$target = Double.NaN;
	@Unique
	private long cobblecomfort$nanos;
	@Unique
	private boolean cobblecomfort$applying;

	@Shadow
	public abstract double scrollAmount();

	@Shadow
	public abstract void setScrollAmount(double amount);

	@Shadow
	public abstract int maxScrollAmount();

	@WrapOperation(method = "mouseScrolled", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V"))
	private void cobblecomfort$scrollToTarget(AbstractScrollArea area, double amount, Operation<Void> original) {
		if (!SmoothScroll.enabled(SmoothScroll.Kind.LISTS)) {
			original.call(area, amount);
			return;
		}
		double current = scrollAmount();
		double base = Double.isNaN(cobblecomfort$target) ? current : cobblecomfort$target;
		if (Double.isNaN(cobblecomfort$target)) {
			cobblecomfort$nanos = System.nanoTime();
		}
		cobblecomfort$target = Math.clamp(base + amount - current, 0, Math.max(0, maxScrollAmount()));
	}

	/** Anything else that scrolls (scrollbar, keyboard, jumping to an entry) ends the glide. */
	@Inject(method = "setScrollAmount", at = @At("HEAD"))
	private void cobblecomfort$stopGlide(double amount, CallbackInfo ci) {
		if (!cobblecomfort$applying) {
			cobblecomfort$target = Double.NaN;
		}
	}

	@Override
	public void cobblecomfort$tickScroll() {
		if (Double.isNaN(cobblecomfort$target)) {
			return;
		}
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(cobblecomfort$nanos, now);
		cobblecomfort$nanos = now;
		double current = scrollAmount();
		double next = current + (cobblecomfort$target - current) * SmoothScroll.approach(seconds);
		boolean done = Math.abs(cobblecomfort$target - next) < 0.5;
		cobblecomfort$applying = true;
		setScrollAmount(done ? cobblecomfort$target : next);
		cobblecomfort$applying = false;
		if (done) {
			cobblecomfort$target = Double.NaN;
		}
	}
}
