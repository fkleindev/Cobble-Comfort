package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.hudanim.HudAnimFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD animations: boss bars glide, and lost health runs down behind the fill. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	private static final String BAR = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V";
	private static final String GET_PROGRESS = "Lnet/minecraft/world/BossEvent;getProgress()F";

	@Shadow
	@Final
	private static Identifier[] BAR_PROGRESS_SPRITES;

	@Inject(method = BAR, at = @At(value = "INVOKE", target = GET_PROGRESS))
	private void cobblecomfort$trail(GuiGraphicsExtractor g, int x, int y, BossEvent event, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.beforeBossFill(g, x, y, event, BAR_PROGRESS_SPRITES[event.getColor().ordinal()]);
	}

	@WrapOperation(method = BAR, at = @At(value = "INVOKE", target = GET_PROGRESS))
	private float cobblecomfort$smooth(BossEvent event, Operation<Float> original) {
		return HudAnimFeature.INSTANCE.bossProgress(event, original.call(event));
	}
}
