package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Voxels;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Companions in the wardrobe (in the world they are drawn on their own, see {@link Companions}): alone on a tile, or
 * sitting next to you in the big preview.
 */
final class CompanionLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** A companion drawn alone, the figure hidden. */
	static final RenderStateDataKey<Companion.Look> ALONE = RenderStateDataKey.create(() -> "cobblecomfort:companion_alone");
	/** A companion sitting at the figure's feet. */
	static final RenderStateDataKey<Companion.Look> BESIDE = RenderStateDataKey.create(() -> "cobblecomfort:companion_beside");
	/** How a companion drawn alone stands (happy, asleep), instead of idling. */
	static final RenderStateDataKey<CompanionModel.Pose> STANCE = RenderStateDataKey.create(() -> "cobblecomfort:companion_stance");

	CompanionLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Companion.Look alone = state.getData(ALONE);
		Companion.Look beside = state.getData(BESIDE);
		Companion.Look look = alone != null ? alone : beside;
		if (look == null) {
			return;
		}
		float time = (System.nanoTime() / 50_000_000L) % 100_000;
		pose.pushPose();
		// Back from the model's frame (y down, from the neck) to the feet, y up, facing forward.
		pose.translate(0, 1.501f, 0);
		pose.scale(-1, -1, 1);
		pose.scale(1 / 0.9375f, 1 / 0.9375f, 1 / 0.9375f);
		pose.mulPose(new Matrix4f().rotationY(Mth.PI));
		CompanionModel.Pose stance = state.getData(STANCE) != null ? state.getData(STANCE) : CompanionModel.Pose.idle(time);
		if (beside != null && alone == null) {
			// At your right foot, a little in front, turned towards the viewer.
			pose.translate(-0.62f, 0, 0.25f);
			pose.mulPose(new Matrix4f().rotationY(-0.35f));
			stance = new CompanionModel.Pose(0, 0, 1, 0, 0, 0, 0.3f, 0, 0, 0, time, CompanionModel.NONE, 0, 0, 0, 0);
		}
		CompanionModel.Pose pose1 = stance;
		collector.submitCustomGeometry(pose, CompanionTexture.type(), (p, vc) -> CompanionModel.draw(vc, p, look, pose1, light));
		if (CompanionModel.glows(look.companion().style)) {
			collector.submitCustomGeometry(pose, CompanionTexture.glowType(), (p, vc) -> CompanionModel.draw(vc, p, look, pose1, light, true));
		}
		pose.popPose();
	}
}
