package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.toasts.StackedToast;
import dev.cobblecomfort.features.toasts.ToastsFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An advancement toast standing in for more: a "+2" badge, and it stays a little longer for each. */
@Mixin(AdvancementToast.class)
public abstract class AdvancementToastMixin implements StackedToast {
	/** Each stacked advancement adds half the display time. */
	@Unique
	private static final double EXTRA_TIME = 0.5;

	@Unique
	private int cobblecomfort$extra;

	@Override
	public int cobblecomfort$extra() {
		return cobblecomfort$extra;
	}

	@Override
	public void cobblecomfort$addExtra() {
		cobblecomfort$extra++;
	}

	@ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true)
	private long cobblecomfort$stayLonger(long visibleTime) {
		return cobblecomfort$extra == 0 ? visibleTime : (long) (visibleTime / (1 + EXTRA_TIME * cobblecomfort$extra));
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void cobblecomfort$badge(GuiGraphicsExtractor g, Font font, long visibleTime, CallbackInfo ci) {
		if (cobblecomfort$extra > 0) {
			ToastsFeature.INSTANCE.drawBadge(g, "+" + cobblecomfort$extra);
		}
	}
}
