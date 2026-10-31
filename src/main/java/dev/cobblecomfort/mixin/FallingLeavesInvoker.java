package dev.cobblecomfort.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets rustled leaves drop the same falling leaf as the block does on its own (tinted, cherry petals, pale oak). */
@Mixin(FallingParticlesLeavesBlock.class)
public interface FallingLeavesInvoker {
	@Invoker("spawnFallingLeavesParticle")
	void cobblecomfort$spawnFallingLeaf(Level level, BlockPos pos, RandomSource random);
}
