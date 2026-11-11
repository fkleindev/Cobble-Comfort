package dev.cobblecomfort.gametest.mixin;

import dev.cobblecomfort.features.petplush.PetPlushSync;
import dev.cobblecomfort.features.petplush.PlushLook;
import dev.cobblecomfort.gametest.TestLooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Lets tests make up what other players share, as the Sync plugin would tell (in singleplayer the sync forgets the
 * list every tick).
 */
@Mixin(PetPlushSync.class)
public abstract class PetPlushSyncMixin {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$testLook(UUID player, CallbackInfoReturnable<PlushLook> cir) {
		PlushLook look = TestLooks.LOOKS.get(player);
		if (look != null) {
			cir.setReturnValue(look);
		}
	}

	@Inject(method = "hasMod", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$testMod(UUID player, CallbackInfoReturnable<Boolean> cir) {
		if (TestLooks.LOOKS.containsKey(player)) {
			cir.setReturnValue(true);
		}
	}
}
