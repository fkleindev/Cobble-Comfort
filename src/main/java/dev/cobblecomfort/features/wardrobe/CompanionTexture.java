package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The surfaces of the companions: a small atlas of grey pixel patterns (fur, wool, stone, scales, feathers...), made
 * here at first use, tinted by each box's colour. Every surface has the same pixel size (two pixels per voxel, like a
 * mob a little finer than the game's own): a face larger than one pattern is split where the pattern repeats.
 * Rounded boxes are single boxes whose faces darken softly towards their edges, so they read round without the steps
 * of stacked boxes.
 */
final class CompanionTexture {
	static final Identifier ID = Identifier.fromNamespaceAndPath("cobblecomfort", "companion_materials");
	private static final RenderType TYPE = RenderTypes.entityCutout(ID);
	/** What glows on top (lava cracks, light beams): see-through by its alpha, at full light. */
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(ID);
	private static final int TILE = 16, ATLAS = 64, ATLAS_H = 128;
	/** Pixels per voxel, and how many voxels until a pattern repeats. */
	private static final float DENSITY = 2f, PERIOD = TILE / DENSITY;
	/** Faces narrower than this (voxels) stay plain: eyes, dots, claws and other small details. */
	private static final float SMALL = 0.7f;
	/** How much darker the very edge of a rounded box is. */
	private static final float EDGE = 0.2f;
	private static DynamicTexture texture;

	/** What a surface is made of. */
	enum Material {
		PLAIN, SOFT, SKIN, FUR, WOOL, STONE, SCALES, SHELL, FEATHER, CARDBOARD, PUMPKIN, SNOW, SMOKE, CAP, SPINES, DOWN,
		METAL, COPPER, PATINA, BASALT, CRACKS, STRIPES
	}

	private CompanionTexture() {
	}

	/** The render type, with the atlas made and registered if it is not yet (render thread). */
	static RenderType type() {
		if (texture == null) {
			NativeImage image = new NativeImage(ATLAS, ATLAS_H, false);
			for (Material material : Material.values()) {
				int ox = material.ordinal() % 4 * TILE, oy = material.ordinal() / 4 * TILE;
				for (int y = 0; y < TILE; y++) {
					for (int x = 0; x < TILE; x++) {
						int v = Mth.clamp(Math.round(value(material, x, y)), 0, 255);
						// The glowing cracks are white, see-through where they do not glow.
						image.setPixel(ox + x, oy + y, material == Material.CRACKS ? ARGB.color(v, 255, 255, 255) : ARGB.color(255, v, v, v));
					}
				}
			}
			texture = new DynamicTexture(() -> "cobblecomfort companion materials", image);
			Minecraft.getInstance().getTextureManager().register(ID, texture);
		}
		return TYPE;
	}

	// ---------------------------------------------------------------- the patterns (seamless, 16 × 16)

	private static float value(Material material, int x, int y) {
		return switch (material) {
			case PLAIN -> 255;
			case SOFT -> 244 + (noise(x, y, 4, 1) - 0.5f) * 14 + (hash(x, y, 2) - 0.5f) * 5;
			case CAP -> 242 + (noise(x, y, 4, 3) - 0.5f) * 16 + (hash(x, y, 4) < 0.05f ? -10 : 0);
			case SKIN -> 241 + (noise(x, y, 4, 5) - 0.5f) * 12 + (hash(x, y, 6) < 0.07f ? -16 : 0);
			case FUR -> {
				// Strands one pixel wide and a few long, each its own shade, a few dark ones between.
				int offset = (int) (hash(x, 0, 7) * 16);
				int strand = Math.floorMod(y + offset, TILE) / 4;
				float shade = hash(x, strand, 8);
				yield 222 + shade * 33 - (shade < 0.12f ? 16 : 0) + (noise(x, y, 4, 9) - 0.5f) * 8;
			}
			case WOOL -> {
				// Little curls: bright in the middle, darker round the rim.
				float d = nearest(x, y, 4, 10);
				yield 226 + 29 * Math.max(0, 1 - d / 2.3f) + (hash(x, y, 11) - 0.5f) * 6;
			}
			case STONE -> {
				float[] cell = cells(x, y, 6, 12);
				float tone = (hash((int) cell[2], 0, 13) - 0.5f) * 26;
				yield cell[1] - cell[0] < 0.75f ? 214 : 246 + tone + (hash(x, y, 14) < 0.08f ? -14 : 0);
			}
			case SCALES -> {
				// Rows of round scales, every other row shifted half a scale; the lower rim of each darker.
				int row = y / 4;
				int lx = Math.floorMod(x + (row % 2) * 2, 4), ly = y % 4;
				boolean rim = ly == 3 || ly == 2 && (lx == 0 || lx == 3);
				yield rim ? 206 : 238 + (lx == 1 && ly == 0 ? 14 : 0) + (hash(x, y, 15) - 0.5f) * 6;
			}
			case SHELL -> {
				// Growth rings round each plate.
				float ring = Math.max(Math.abs(x % 8 - 3.5f), Math.abs(y % 8 - 3.5f));
				yield (ring > 1.2f && ring < 1.8f || ring > 3.2f ? 214 : 240) + (noise(x, y, 4, 16) - 0.5f) * 10;
			}
			case FEATHER -> {
				// Overlapping feathers in rows: a pale shaft, a dark V at the tip.
				int row = y / 4;
				int lx = Math.floorMod(x + (row % 2) * 2, 4), ly = y % 4;
				boolean tip = ly == 3 && (lx == 1 || lx == 2) || ly == 2 && (lx == 0 || lx == 3);
				yield tip ? 204 : lx == 1 && ly < 2 ? 250 : 234 + (hash(x, y, 17) - 0.5f) * 8;
			}
			case CARDBOARD -> (y % 3 == 0 ? 234 : 245) + (hash(x, y, 18) < 0.05f ? -18 : 0) + (noise(x, y, 4, 19) - 0.5f) * 8;
			case PUMPKIN -> {
				// Ribs: lighter down the middle, darker at the grooves.
				float r = Math.abs(x % 8 - 3.5f) / 3.5f;
				yield 252 - 38 * r * r + (noise(x, y, 4, 20) - 0.5f) * 8;
			}
			case SNOW -> 238 + (noise(x, y, 4, 21) - 0.5f) * 12 + (hash(x, y, 22) < 0.04f ? 17 : 0);
			case SMOKE -> 222 + (noise(x, y, 4, 23) - 0.5f) * 40 + (noise(x, y, 2, 24) - 0.5f) * 20;
			case SPINES -> {
				// Quills lying back in staggered rows: a bright shaft, a dark gap between, a dark tip at the end of each.
				int row = y / 4;
				int lx = Math.floorMod(x + (row % 2) * 1, 2), ly = y % 4;
				yield lx == 1 ? 190 + (hash(x, row, 25) - 0.5f) * 14 : ly == 3 ? 176 : 236 + (ly == 0 ? 16 : 0) + (hash(x, y, 26) - 0.5f) * 10;
			}
			case DOWN -> {
				// Fluffy down: soft blotches, bright wisps, a few darker flecks.
				float wisp = hash(x, y, 27);
				yield 230 + (noise(x, y, 8, 28) - 0.5f) * 22 + (wisp > 0.9f ? 18 : wisp < 0.06f ? -18 : 0);
			}
			// Brushed metal: fine streaks along each row, a bright scratch now and then.
			case METAL -> 234 + (hash(0, y, 29) - 0.5f) * 18 + (hash(x, y, 30) - 0.5f) * 6 + (hash(x / 4, y, 31) < 0.04f ? 16 : 0);
			// Copper: brushed too, with soft hammered dents.
			case COPPER -> 236 + (noise(x, y, 4, 32) - 0.5f) * 22 + (hash(0, y, 33) - 0.5f) * 8;
			// Verdigris: blotchy, chalky, with darker pits.
			case PATINA -> 232 + (noise(x, y, 4, 34) - 0.5f) * 34 + (noise(x, y, 8, 35) - 0.5f) * 14 + (hash(x, y, 36) < 0.06f ? -26 : 0);
			case BASALT -> {
				// Basalt scales: dark seams between, each scale its own shade, streaked up and down.
				float[] cell = cells(x, y, 3, 40);
				float tone = (hash((int) cell[2], 1, 41) - 0.5f) * 30;
				yield cell[1] - cell[0] < 0.6f ? 120 : 228 + tone + (hash(x, 0, 42) - 0.5f) * 10;
			}
			case CRACKS -> {
				// What glows in the seams of the basalt (drawn on top): bright in the seam, a soft rim beside it.
				float[] cell = cells(x, y, 3, 40);
				float gap = cell[1] - cell[0];
				yield gap < 0.45f ? 255 : gap < 0.9f ? 90 : 0;
			}
			// Warning stripes, diagonal.
			case STRIPES -> Math.floorMod(x + y, 8) < 4 ? 250 : 46;
		};
	}

	/** The render type for what glows on top (render thread; makes the atlas first if needed). */
	static RenderType glowType() {
		type();
		return GLOW;
	}

	private static float hash(int x, int y, int seed) {
		int h = Math.floorMod(x, TILE) * 374761393 + Math.floorMod(y, TILE) * 668265263 + seed * 2147483647;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	/** Smooth noise repeating every 16 pixels, with {@code cells} lattice cells across. */
	private static float noise(int x, int y, int cells, int seed) {
		float size = TILE / (float) cells;
		float fx = x / size, fy = y / size;
		int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
		float tx = smooth(fx - ix), ty = smooth(fy - iy);
		float a = lattice(ix, iy, cells, seed), b = lattice(ix + 1, iy, cells, seed);
		float c = lattice(ix, iy + 1, cells, seed), d = lattice(ix + 1, iy + 1, cells, seed);
		return Mth.lerp(ty, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
	}

	private static float lattice(int x, int y, int cells, int seed) {
		return hash(Math.floorMod(x, cells) * 5, Math.floorMod(y, cells) * 7, seed);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Distance to the nearest of a jittered grid of points ({@code cells} across), repeating. */
	private static float nearest(int x, int y, int cells, int seed) {
		return cells(x, y, cells, seed)[0];
	}

	/** The nearest and second nearest distance to a jittered grid of points, and which point is nearest. */
	private static float[] cells(int x, int y, int cells, int seed) {
		float size = TILE / (float) cells;
		float best = Float.MAX_VALUE, second = Float.MAX_VALUE;
		int which = 0;
		int cx = (int) (x / size), cy = (int) (y / size);
		for (int j = -1; j <= 1; j++) {
			for (int i = -1; i <= 1; i++) {
				int gx = cx + i, gy = cy + j;
				int wx = Math.floorMod(gx, cells), wy = Math.floorMod(gy, cells);
				float px = (gx + 0.2f + 0.6f * hash(wx, wy, seed)) * size;
				float py = (gy + 0.2f + 0.6f * hash(wy, wx, seed + 1)) * size;
				float d = (float) Math.hypot(px - (x + 0.5f), py - (y + 0.5f));
				if (d < best) {
					second = best;
					best = d;
					which = wx + wy * cells;
				} else if (d < second) {
					second = d;
				}
			}
		}
		return new float[]{best, second, which};
	}

	// ---------------------------------------------------------------- boxes

	/**
	 * A box in the pose's units, y up, in {@code material}; {@code bevel} (voxels) above 0 darkens its faces softly
	 * towards the edges, so it reads rounded. Sides and underside a little darker, as baked shading for packs without
	 * lighting of their own.
	 */
	static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
			int color, int light, Material material, float bevel) {
		int side = ARGB.scaleRGB(color, 0.86f);
		int under = ARGB.scaleRGB(color, 0.72f);
		float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		face(vc, p, side, light, material, bevel, 0, 0, -1, x1, y0, z0, -dx, 0, 0, 0, dy, 0);
		face(vc, p, side, light, material, bevel, 0, 0, 1, x0, y0, z1, dx, 0, 0, 0, dy, 0);
		face(vc, p, side, light, material, bevel, -1, 0, 0, x0, y0, z0, 0, 0, dz, 0, dy, 0);
		face(vc, p, side, light, material, bevel, 1, 0, 0, x1, y0, z1, 0, 0, -dz, 0, dy, 0);
		face(vc, p, under, light, material, bevel, 0, -1, 0, x0, y0, z0, dx, 0, 0, 0, 0, dz);
		face(vc, p, color, light, material, bevel, 0, 1, 0, x0, y1, z1, dx, 0, 0, 0, 0, -dz);
	}

	/**
	 * One face: from corner P along U and then V (counter-clockwise seen from outside), cut into pieces where the
	 * pattern repeats and where the edge shading begins.
	 */
	private static void face(VertexConsumer vc, PoseStack.Pose p, int color, int light, Material material, float bevel,
			float nx, float ny, float nz, float px, float py, float pz, float ux, float uy, float uz, float vx, float vy, float vz) {
		float lu = Math.abs(ux + uy + uz), lv = Math.abs(vx + vy + vz);
		if (lu < 1e-4f || lv < 1e-4f) {
			return;
		}
		if (material == Material.CRACKS && Math.min(lu, lv) < SMALL) {
			// Too small to show the pattern: no glow (it would light up whole).
			return;
		}
		if (Math.min(lu, lv) < SMALL) {
			material = Material.PLAIN;
			bevel = 0;
		}
		// Where along each axis it is in the world of the pattern: the coordinate that changes along it.
		float pu = ux != 0 ? px : uy != 0 ? py : pz, du = ux + uy + uz;
		float pv = vx != 0 ? px : vy != 0 ? py : pz, dv = vx + vy + vz;
		float bu = Math.min(bevel, lu / 2), bv = Math.min(bevel, lv / 2);
		float[] su = cuts(pu, du, lu, bu, material != Material.PLAIN);
		float[] sv = cuts(pv, dv, lv, bv, material != Material.PLAIN);
		int tile = material.ordinal();
		float tileU = tile % 4 * TILE, tileV = tile / 4 * TILE;
		for (int i = 0; i + 1 < su.length; i++) {
			float baseU = (float) Math.floor((pu + du * (su[i] + su[i + 1]) / 2) / PERIOD) * PERIOD;
			for (int j = 0; j + 1 < sv.length; j++) {
				float baseV = (float) Math.floor((pv + dv * (sv[j] + sv[j + 1]) / 2) / PERIOD) * PERIOD;
				float[][] corners = {{su[i], sv[j]}, {su[i + 1], sv[j]}, {su[i + 1], sv[j + 1]}, {su[i], sv[j + 1]}};
				for (float[] c : corners) {
					float s = c[0], t = c[1];
					float u, v;
					if (material == Material.PLAIN) {
						u = tileU + 8;
						v = tileV + 8;
					} else {
						u = tileU + Mth.clamp((pu + du * s - baseU) * DENSITY, 0.02f, TILE - 0.02f);
						v = tileV + Mth.clamp(TILE - (pv + dv * t - baseV) * DENSITY, 0.02f, TILE - 0.02f);
					}
					float edge = Math.max(edge(s * lu, lu, bu), edge(t * lv, lv, bv));
					int shaded = edge > 0 ? ARGB.scaleRGB(color, 1 - EDGE * edge) : color;
					vc.addVertex(p, px + ux * s + vx * t, py + uy * s + vy * t, pz + uz * s + vz * t).setColor(shaded)
							.setUv(u / ATLAS, v / ATLAS_H).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
				}
			}
		}
	}

	/** How far into the edge shading a point {@code d} along a side of length {@code length} is: 1 at the edge. */
	private static float edge(float d, float length, float bevel) {
		if (bevel <= 0) {
			return 0;
		}
		return Mth.clamp(1 - Math.min(d, length - d) / bevel, 0, 1);
	}

	/**
	 * Where to cut a side (0 to 1): its ends, where the edge shading begins, and where the pattern repeats (the
	 * coordinate {@code start + delta * s} crossing a multiple of the period).
	 */
	private static float[] cuts(float start, float delta, float length, float bevel, boolean textured) {
		float[] out = new float[16];
		int n = 0;
		out[n++] = 0;
		if (bevel > 0) {
			out[n++] = bevel / length;
			out[n++] = 1 - bevel / length;
		}
		if (textured) {
			float a = Math.min(start, start + delta), b = Math.max(start, start + delta);
			for (float k = (float) Math.ceil(a / PERIOD + 1e-4f) * PERIOD; k < b - 1e-4f && n < out.length - 1; k += PERIOD) {
				out[n++] = (k - start) / delta;
			}
		}
		out[n++] = 1;
		java.util.Arrays.sort(out, 0, n);
		// Without repeats.
		int m = 0;
		for (int i = 0; i < n; i++) {
			if (m == 0 || out[i] - out[m - 1] > 1e-4f) {
				out[m++] = out[i];
			}
		}
		return java.util.Arrays.copyOf(out, m);
	}
}
