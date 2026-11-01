package dev.cobblecomfort.sheets.mixin;

import dev.cobblecomfort.sheets.SheetSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dresses the figure on the cosmetic sheets in the chosen skin. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void cobblecomfortSheets$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin skin = SheetSkin.skin;
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
