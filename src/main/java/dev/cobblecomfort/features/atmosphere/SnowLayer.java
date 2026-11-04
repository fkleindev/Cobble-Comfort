package dev.cobblecomfort.features.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Snow lying on a player's head and shoulders: a layer of the snow texture on top of the head and of each arm, thicker
 * the more has settled, on top of helmet and chestplate. A wardrobe hat keeps the head free.
 */
final class SnowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final RenderType TYPE = RenderTypes.entityTranslucent(Identifier.withDefaultNamespace("textures/block/snow.png"));
	private static final float PX = 1f / 16f;
	/** Thickness in pixels with a dusting and when fully covered. */
	private static final float MIN_THICKNESS = 0.4f;
	private static final float MAX_THICKNESS = 1.8f;
	/** How far the skin's outer layer and armour stand out, in pixels. */
	private static final float SKIN_LAYER = 0.5f;
	private static final float ARMOR = 1f;

	SnowLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		float amount = AtmosphereFeature.INSTANCE.snowOn(state.id);
		if (amount < 0.02f || state.isInvisible || state.isSpectator || mc.level == null) {
			return;
		}
		Entity entity = mc.level.getEntity(state.id);
		boolean hat = entity instanceof AbstractClientPlayer player && WardrobeFeature.INSTANCE.wearsHat(player);
		float thickness = (MIN_THICKNESS + (MAX_THICKNESS - MIN_THICKNESS) * amount) * PX;
		int color = RenderUtil.withAlpha(0xFFFFFFFF, Mth.clamp(amount * 4f, 0f, 1f));
		boolean slim = state.skin.model() == PlayerModelType.SLIM;
		PlayerModel model = getParentModel();
		pose.pushPose();
		model.root().translateAndRotate(pose);
		if (!hat) {
			float grow = (state.headEquipment.isEmpty() ? SKIN_LAYER : ARMOR) * PX;
			cap(pose, collector, model.head, -4 * PX - grow, 4 * PX + grow, -8 * PX - grow, -4 * PX - grow, 4 * PX + grow,
					thickness, color, light);
		}
		float grow = (state.chestEquipment.isEmpty() ? SKIN_LAYER / 2 : ARMOR) * PX;
		float armWidth = slim ? 3 : 4;
		cap(pose, collector, model.rightArm, (1 - armWidth) * PX - grow, PX + grow, -2 * PX - grow, -2 * PX - grow, 2 * PX + grow,
				thickness, color, light);
		cap(pose, collector, model.leftArm, -PX - grow, (armWidth - 1) * PX + grow, -2 * PX - grow, -2 * PX - grow, 2 * PX + grow,
				thickness, color, light);
		pose.popPose();
	}

	/** A slab of snow lying on top ({@code top}, y points down) of a model part, from x0 to x1 and z0 to z1. */
	private static void cap(PoseStack pose, SubmitNodeCollector collector, ModelPart part, float x0, float x1, float top,
			float z0, float z1, float thickness, int color, int light) {
		pose.pushPose();
		part.translateAndRotate(pose);
		float y0 = top - thickness;
		collector.submitCustomGeometry(pose, TYPE, (p, vc) -> {
			// Texture coordinates follow the block's pixels, so the snow looks the same at any size.
			float u0 = x0 + 0.5f, u1 = x1 + 0.5f, v0 = z0 + 0.5f, v1 = z1 + 0.5f;
			float side = thickness;
			face(vc, p, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, u0, v0, u1, v1, 0f, -1f, 0f, color, light);
			face(vc, p, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, top, z0}, {x0, top, z0}}, u0, 0f, u1, side, 0f, 0f, -1f, color, light);
			face(vc, p, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, top, z1}, {x0, top, z1}}, u0, 0f, u1, side, 0f, 0f, 1f, color, light);
			face(vc, p, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, top, z1}, {x0, top, z0}}, v0, 0f, v1, side, -1f, 0f, 0f, color, light);
			face(vc, p, new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, top, z1}, {x1, top, z0}}, v0, 0f, v1, side, 1f, 0f, 0f, color, light);
		});
		pose.popPose();
	}

	/** A quad facing ({@code nx}, {@code ny}, {@code nz}), its corners turned the way that is seen from there. */
	private static void face(VertexConsumer vc, PoseStack.Pose p, float[][] c, float u0, float v0, float u1, float v1,
			float nx, float ny, float nz, int color, int light) {
		float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
		float ax = c[1][0] - c[0][0], ay = c[1][1] - c[0][1], az = c[1][2] - c[0][2];
		float bx = c[2][0] - c[1][0], by = c[2][1] - c[1][1], bz = c[2][2] - c[1][2];
		float facing = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz;
		for (int i = 0; i < 4; i++) {
			int k = facing >= 0 ? i : 3 - i;
			vc.addVertex(p, c[k][0], c[k][1], c[k][2]).setColor(color).setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(p, nx, ny, nz);
		}
	}
}
