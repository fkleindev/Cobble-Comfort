package dev.cobblecomfort.features.inventoryfx;

import dev.cobblecomfort.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Colour palettes of item textures, sampled from the same particle sprite vanilla uses for item break
 * particles. Cached per item and kept across resource reloads, since the palettes are purely cosmetic.
 */
public final class ItemColors {
	private static final int MAX_COLORS = 48;
	private static final int[] FALLBACK = {0xFF8B8B8B, 0xFFC6C6C6, 0xFF555555};
	private static final Map<Item, int[]> CACHE = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	private ItemColors() {
	}

	public static int[] palette(ItemStack stack) {
		return CACHE.computeIfAbsent(stack.getItem(), item -> sample(stack));
	}

	private static int[] sample(ItemStack stack) {
		try {
			Minecraft mc = Minecraft.getInstance();
			ItemStackRenderState state = new ItemStackRenderState();
			mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
			var material = state.pickParticleMaterial(RANDOM);
			if (material == null) {
				return FALLBACK;
			}
			TextureAtlasSprite sprite = material.sprite();
			SpriteContents contents = sprite.contents();
			var image = ((SpriteContentsAccessor) contents).cobblecomfort$originalImage();
			int w = contents.width();
			int h = contents.height();
			int[] colors = new int[MAX_COLORS];
			int count = 0;
			// Walk the texture in a coarse grid so large textures stay cheap.
			int step = Math.max(1, (int) Math.sqrt(w * h / (double) (MAX_COLORS * 2)));
			for (int y = 0; y < h && count < MAX_COLORS; y += step) {
				for (int x = 0; x < w && count < MAX_COLORS; x += step) {
					int argb = image.getPixel(x, y);
					if ((argb >>> 24) > 128) {
						colors[count++] = argb | 0xFF000000;
					}
				}
			}
			if (count == 0) {
				return FALLBACK;
			}
			int[] result = new int[count];
			System.arraycopy(colors, 0, result, 0, count);
			return result;
		} catch (RuntimeException e) {
			return FALLBACK;
		}
	}
}
