package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.fullbright.FullbrightFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shader-compatible fullbright: the local player reports a hidden night vision effect, which shader
 * packs read like the real one. The effect is never added to the player, so nothing is sent to or
 * overwritten by the server and no effect icon appears.
 */
@Mixin(LivingEntity.class)
public abstract class NightVisionMixin {
	@Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$hasFakeNightVision(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect == MobEffects.NIGHT_VISION && cobblecomfort$fake() != null) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$getFakeNightVision(Holder<MobEffect> effect, CallbackInfoReturnable<MobEffectInstance> cir) {
		if (effect == MobEffects.NIGHT_VISION) {
			MobEffectInstance fake = cobblecomfort$fake();
			// A real effect (e.g. from a potion) takes precedence over the fake one.
			if (fake != null && !((LivingEntity) (Object) this).getActiveEffectsMap().containsKey(effect)) {
				cir.setReturnValue(fake);
			}
		}
	}

	private MobEffectInstance cobblecomfort$fake() {
		return (Object) this == Minecraft.getInstance().player ? FullbrightFeature.INSTANCE.fakeNightVision() : null;
	}
}
