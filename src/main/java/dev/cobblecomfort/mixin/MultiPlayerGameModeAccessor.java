package dev.cobblecomfort.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Accessor("destroyProgress")
	float cobblecomfort$destroyProgress();

	@Accessor("destroyBlockPos")
	BlockPos cobblecomfort$destroyBlockPos();
}
