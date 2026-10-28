package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.hudanim.HudAnimFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD animations: the experience bar fills smoothly and flashes on a level up. */
@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {
	@WrapOperation(method = "extractBackground", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/player/LocalPlayer;experienceProgress:F", opcode = Opcodes.GETFIELD))
	private float cobblecomfort$smoothProgress(LocalPlayer player, Operation<Float> original) {
		return HudAnimFeature.INSTANCE.xpProgress(player, original.call(player));
	}

	@Inject(method = "extractBackground", at = @At("RETURN"))
	private void cobblecomfort$levelFlash(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.afterXpBar(g);
	}
}
