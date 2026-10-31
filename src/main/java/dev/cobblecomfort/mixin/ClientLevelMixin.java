package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.softdoors.SoftDoorsFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees doors, trapdoors and gates open and close (own clicks, server updates, redstone), before their section is rebuilt. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void cobblecomfort$blockUpdated(BlockPos pos, BlockState old, BlockState updated, int flags, CallbackInfo ci) {
		SoftDoorsFeature.INSTANCE.changed((ClientLevel) (Object) this, pos, old, updated);
	}
}
