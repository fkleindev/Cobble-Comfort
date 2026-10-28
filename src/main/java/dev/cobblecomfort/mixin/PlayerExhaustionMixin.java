package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.food.FoodHudFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Food HUD: exhaustion the client causes itself (e.g. attacks) feeds the exhaustion estimate. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"))
	private void cobblecomfort$trackExhaustion(float amount, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self instanceof LocalPlayer && !self.getAbilities().invulnerable) {
			FoodHudFeature.INSTANCE.addExhaustion(amount);
		}
	}
}
