package dev.cobblecomfort.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Plain coloured boxes for voxel models drawn on their own (birds, companions): an entity render type over a white
 * texture, the colour in the vertices, with normals, so shader packs light and shade them like mobs.
 */
public final class Voxels {
	/** Plain white. */
	public static final RenderType TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/hat.png"));
	public static final int FULL_BRIGHT = 0xF000F0;

	private Voxels() {
	}

	/**
	 * A box from ({@code x0}, {@code y0}, {@code z0}) to ({@code x1}, {@code y1}, {@code z1}) in the pose's units, y up.
	 * Sides and underside a little darker, as baked shading for packs without lighting of their own.
	 */
	public static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
			int color, int light) {
		int side = ARGB.scaleRGB(color, 0.86f);
		int under = ARGB.scaleRGB(color, 0.72f);
		quad(vc, p, side, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		quad(vc, p, side, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(vc, p, side, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(vc, p, side, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		quad(vc, p, under, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, p, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
	}

	/** A box centred on x (symmetric across), from y0 to y1 and z0 to z1. */
	public static void centred(VertexConsumer vc, PoseStack.Pose p, float halfWidth, float y0, float y1, float z0, float z1,
			int color, int light) {
		box(vc, p, -halfWidth, y0, z0, halfWidth, y1, z1, color, light);
	}

	/** A quad with its corners counter-clockwise as seen from the side its normal points to. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, int color, int light, float nx, float ny, float nz,
			float... c) {
		for (int i = 0; i < 4; i++) {
			vc.addVertex(p, c[i * 3], c[i * 3 + 1], c[i * 3 + 2]).setColor(color).setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		}
	}
}
