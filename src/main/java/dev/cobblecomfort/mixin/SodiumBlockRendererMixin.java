package dev.cobblecomfort.mixin;

import dev.cobblecomfort.core.HiddenBlocks;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves {@link HiddenBlocks} (swaying plants, swinging doors) out of Sodium's chunk meshes. Skipped when Sodium is
 * missing or changes this method.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer")
public abstract class SodiumBlockRendererMixin {
	@Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, require = 0)
	private void cobblecomfort$skipHiddenBlock(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
		if (HiddenBlocks.hidden(pos)) {
			ci.cancel();
		}
	}
}
