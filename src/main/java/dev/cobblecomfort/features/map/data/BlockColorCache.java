package dev.cobblecomfort.features.map.data;

import dev.cobblecomfort.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map colour of a block: the average colour of its top face texture, tinted like in the world (grass, leaves,
 * water). Blocks that are fully transparent from above (air, glass panes, ...) have colour 0 and are looked through.
 */
public final class BlockColorCache {
	private static final int WATER_BASE = 0xFFB4B4B4;
	private static final int LAVA = 0xFFD8661C;

	private record Entry(int color, boolean tinted) {
	}

	private static final Map<BlockState, Entry> CACHE = new IdentityHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create(42);

	private BlockColorCache() {
	}

	public static void clear() {
		CACHE.clear();
	}

	/** ARGB colour of the block at pos as seen from above, or 0 if it should be looked through. */
	public static int color(ClientLevel level, BlockState state, BlockPos pos) {
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty() && (state.isAir() || state.getBlock() == fluid.createLegacyBlock().getBlock())) {
			if (fluid.is(FluidTags.WATER)) {
				return multiply(WATER_BASE, BiomeColors.getAverageWaterColor(level, pos));
			}
			if (fluid.is(FluidTags.LAVA)) {
				return LAVA;
			}
		}
		if (state.isAir()) {
			return 0;
		}
		Entry entry = CACHE.get(state);
		if (entry == null) {
			entry = compute(state);
			CACHE.put(state, entry);
		}
		if (entry.color() == 0 || !entry.tinted()) {
			return entry.color();
		}
		BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
		return tint == null ? entry.color() : multiply(entry.color(), tint.colorInWorld(state, level, pos));
	}

	public static boolean isWater(BlockState state) {
		return state.getFluidState().is(FluidTags.WATER);
	}

	private static Entry compute(BlockState state) {
		try {
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RANDOM, parts);
			for (BlockStateModelPart part : parts) {
				for (BakedQuad quad : part.getQuads(Direction.UP)) {
					return fromSprite(quad.materialInfo().sprite(), quad.materialInfo().isTinted(), state);
				}
			}
			for (BlockStateModelPart part : parts) {
				for (BakedQuad quad : part.getQuads(null)) {
					return fromSprite(quad.materialInfo().sprite(), quad.materialInfo().isTinted(), state);
				}
			}
			return fromSprite(model.particleMaterial().sprite(), false, state);
		} catch (RuntimeException e) {
			return fallback(state);
		}
	}

	private static Entry fromSprite(TextureAtlasSprite sprite, boolean tinted, BlockState state) {
		SpriteContents contents = sprite.contents();
		var image = ((SpriteContentsAccessor) contents).cobblecomfort$originalImage();
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = 0; y < contents.height(); y++) {
			for (int x = 0; x < contents.width(); x++) {
				int argb = image.getPixel(x, y);
				if ((argb >>> 24) < 128) {
					continue;
				}
				r += argb >> 16 & 0xFF;
				g += argb >> 8 & 0xFF;
				b += argb & 0xFF;
				n++;
			}
		}
		if (n == 0) {
			return new Entry(0, false);
		}
		// Mostly transparent textures (flowers, rails, glass panes) only count if the block shows on maps.
		boolean sparse = n < contents.width() * contents.height() / 4;
		if (sparse && mapColor(state) == MapColor.NONE) {
			return new Entry(0, false);
		}
		int color = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
		return new Entry(color, tinted);
	}

	private static Entry fallback(BlockState state) {
		MapColor color = mapColor(state);
		return new Entry(color == MapColor.NONE ? 0 : 0xFF000000 | color.col, false);
	}

	private static MapColor mapColor(BlockState state) {
		try {
			return state.getMapColor(null, null);
		} catch (RuntimeException e) {
			return MapColor.STONE;
		}
	}

	public static int multiply(int argb, int tint) {
		int r = (argb >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
		int g = (argb >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
		int b = (argb & 0xFF) * (tint & 0xFF) / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	public static int scale(int argb, float factor) {
		int r = Math.min(255, Math.round((argb >> 16 & 0xFF) * factor));
		int g = Math.min(255, Math.round((argb >> 8 & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
	}
}
