package dev.cobblecomfort.gametest.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Punchy (run along with {@code -Pextra_runtime_mod=<jar>}) leaves a key binding without a name, which breaks the
 * options reset before the tests. Such a key is read as unbound.
 */
@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {
	@Inject(method = "getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;", at = @At("HEAD"), cancellable = true)
	private static void cobblecomfort$unnamed(String name, CallbackInfoReturnable<InputConstants.Key> cir) {
		if (name == null) {
			cir.setReturnValue(InputConstants.UNKNOWN);
		}
	}
}
