package dev.cobblecomfort.core;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The game's own textures, straight from its built-in assets, whatever resource packs are active: so the cosmetics
 * that borrow vanilla textures (plush pets, name charm wood and metal, the items on pins, belts and backs) always look
 * the same. Items are drawn from their vanilla icon as a sixteenth-deep slab of pixels, the way dropped items look.
 */
public final class VanillaAssets {
	/** A plain white texture: the item pixels carry their own colours. */
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/hat.png");
	private static final RenderType PIXELS = RenderTypes.entityCutout(WHITE);
	private static final RenderType GLINT = RenderTypes.itemCutoutGlint(WHITE);
	private static final Map<Identifier, Identifier> TEXTURES = new HashMap<>();
	private static final Map<Identifier, Optional<int[][]>> ICONS = new HashMap<>();

	private VanillaAssets() {
	}

	/**
	 * A texture of the game ({@code minecraft:textures/...png}) as the game ships it, under an id of our own; the id as
	 * given when it isn't one of the game's or can't be read.
	 */
	public static Identifier texture(Identifier vanilla) {
		if (!Identifier.DEFAULT_NAMESPACE.equals(vanilla.getNamespace())) {
			return vanilla;
		}
		return TEXTURES.computeIfAbsent(vanilla, id -> {
			NativeImage image = read(id);
			if (image == null) {
				return id;
			}
			Identifier own = Identifier.fromNamespaceAndPath("cobblecomfort", "vanilla/" + id.getPath());
			Minecraft.getInstance().getTextureManager().register(own, new DynamicTexture(own::toString, image));
			return own;
		});
	}

	/**
	 * Draws an item from its vanilla icon, a block wide and a sixteenth deep, centred on the origin (y up), like an item
	 * model in no particular display context. False if the game has no plain icon for it (then nothing is drawn).
	 */
	public static boolean submitItem(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, int light) {
		int[][] pixels = icon(stack);
		if (pixels == null) {
			return false;
		}
		collector.submitCustomGeometry(pose, PIXELS, (p, vc) -> emit(vc, p, pixels, light));
		if (stack.hasFoil()) {
			collector.order(1).submitCustomGeometry(pose, GLINT, (p, vc) -> emit(vc, p, pixels, light));
		}
		return true;
	}

	/** The item's vanilla icon as ARGB pixels, [y][x], or null without one. */
	private static int[][] icon(ItemStack stack) {
		Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return ICONS.computeIfAbsent(item, id -> {
			// Most icons are named after the item; crossbows have a texture for each state.
			for (String suffix : new String[]{"", "_standby"}) {
				NativeImage image = read(id.withPath("textures/item/" + id.getPath() + suffix + ".png"));
				if (image != null) {
					try (image) {
						int[][] pixels = new int[image.getHeight()][image.getWidth()];
						for (int y = 0; y < pixels.length; y++) {
							for (int x = 0; x < pixels[y].length; x++) {
								pixels[y][x] = image.getPixel(x, y);
							}
						}
						return Optional.of(pixels);
					}
				}
			}
			return Optional.empty();
		}).orElse(null);
	}

	private static NativeImage read(Identifier id) {
		Optional<Resource> resource = Minecraft.getInstance().getVanillaPackResources().asResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		} catch (IOException e) {
			return null;
		}
	}

	/** The solid pixels as little boxes; only the faces next to an empty pixel are drawn. */
	private static void emit(VertexConsumer vc, PoseStack.Pose p, int[][] pixels, int light) {
		int h = pixels.length;
		int w = pixels[0].length;
		float px = 1f / w;
		float py = 1f / h;
		float z0 = -0.5f / 16f;
		float z1 = 0.5f / 16f;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = pixels[y][x];
				if (alpha(c) < 128) {
					continue;
				}
				int color = c | 0xFF000000;
				float x0 = x * px - 0.5f;
				float x1 = x0 + px;
				float y1 = 0.5f - y * py;
				float y0 = y1 - py;
				quad(vc, p, color, light, 0f, 0f, 1f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
				quad(vc, p, color, light, 0f, 0f, -1f, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
				if (x == 0 || alpha(pixels[y][x - 1]) < 128) {
					quad(vc, p, color, light, -1f, 0f, 0f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
				}
				if (x == w - 1 || alpha(pixels[y][x + 1]) < 128) {
					quad(vc, p, color, light, 1f, 0f, 0f, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
				}
				if (y == 0 || alpha(pixels[y - 1][x]) < 128) {
					quad(vc, p, color, light, 0f, 1f, 0f, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
				}
				if (y == h - 1 || alpha(pixels[y + 1][x]) < 128) {
					quad(vc, p, color, light, 0f, -1f, 0f, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, y0, z0);
				}
			}
		}
	}

	private static int alpha(int argb) {
		return argb >>> 24;
	}

	/** A quad facing ({@code nx}, {@code ny}, {@code nz}), its corners turned the way that is seen from there. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, int color, int light, float nx, float ny, float nz, float... c) {
		float ax = c[3] - c[0], ay = c[4] - c[1], az = c[5] - c[2];
		float bx = c[6] - c[3], by = c[7] - c[4], bz = c[8] - c[5];
		float facing = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz;
		for (int i = 0; i < 4; i++) {
			int k = (facing >= 0 ? i : 3 - i) * 3;
			// The glint follows the position, so it moves across the item.
			float u = Math.clamp(0.5f + (c[k] + c[k + 2]) * 0.8f, 0.01f, 0.99f);
			float v = Math.clamp(0.5f - c[k + 1] * 0.8f, 0.01f, 0.99f);
			vc.addVertex(p, c[k], c[k + 1], c[k + 2]).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(p, nx, ny, nz);
		}
	}
}
