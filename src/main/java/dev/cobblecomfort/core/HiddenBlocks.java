package dev.cobblecomfort.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks left out of the chunk meshes while a feature draws them on its own (swaying plants, swinging doors). The
 * chunk builder threads ask {@link #hidden} for every block (see {@code SectionCompilerMixin} and
 * {@code SodiumBlockRendererMixin}).
 */
public final class HiddenBlocks {
	private static final Set<Long> HIDDEN = ConcurrentHashMap.newKeySet();

	private HiddenBlocks() {
	}

	public static boolean hidden(BlockPos pos) {
		return !HIDDEN.isEmpty() && HIDDEN.contains(pos.asLong());
	}

	public static void hide(BlockPos pos) {
		HIDDEN.add(pos.asLong());
		rebuild(pos);
	}

	public static void show(BlockPos pos) {
		HIDDEN.remove(pos.asLong());
		rebuild(pos);
	}

	public static void clear() {
		HIDDEN.clear();
	}

	/**
	 * The quads of a block drawn on its own, with its tint, at the origin, with flat shading: ambient occlusion would
	 * come out wrong once the block bends or turns.
	 */
	public static void tesselate(ClientLevel level, BlockPos pos, BlockState state, BlockQuadOutput output) {
		Minecraft mc = Minecraft.getInstance();
		MovingBlockRenderState block = new MovingBlockRenderState();
		block.blockPos = pos;
		block.randomSeedPos = pos;
		block.blockState = state;
		block.biome = level.getBiome(pos);
		block.cardinalLighting = level.cardinalLighting();
		block.lightEngine = level.getLightEngine();
		ModelBlockRenderer renderer = new ModelBlockRenderer(false, mc.options.cutoutLeaves().get(), mc.getBlockColors());
		renderer.tesselateBlock(output, 0f, 0f, 0f, block, pos, state,
				mc.getModelManager().getBlockStateModelSet().get(state), state.getSeed(pos));
	}

	/** Rebuilds the section at once (flag 8: "important", also with Sodium), so the block doesn't flicker. */
	private static void rebuild(BlockPos pos) {
		Minecraft.getInstance().levelExtractor.blockChanged(pos, 8);
	}
}
