package dev.cobblecomfort.mixin;

import dev.cobblecomfort.core.ModUserIcon;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players with the mod get the Cobble Comfort icon in front of the name above their head. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarNameTagMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void cobblecomfort$modUserIcon(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (state.nameTag != null && entity instanceof Player player) {
			state.nameTag = ModUserIcon.decorate(player.getUUID(), state.nameTag);
		}
	}
}
