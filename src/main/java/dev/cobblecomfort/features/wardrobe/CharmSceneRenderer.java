package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Draws a {@link CharmScene}: the item as it is without any display transform (the space charms are placed in), its
 * charm hanging straight down the screen, turned and zoomed as the editor shows it.
 */
public final class CharmSceneRenderer extends PictureInPictureRenderer<CharmScene> {
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Down the screen, in the space the picture is drawn in (y grows downwards there). */
	private static final Vector3fc DOWN = new Vector3f(0f, 1f, 0f);

	private final ItemStackRenderState item = new ItemStackRenderState();

	@Override
	public Class<CharmScene> getRenderStateClass() {
		return CharmScene.class;
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected void renderToTexture(CharmScene scene, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
		item.clear();
		mc.getItemModelResolver().updateForTopItem(item, scene.stack(), ItemDisplayContext.NONE, mc.level, null, 0);
		NameCharmFeature.INSTANCE.appendEditorLayer(item, scene.stack(), scene.charm(), scene.spot(), DOWN);
		float size = scene.zoom();
		pose.pushPose();
		pose.scale(size, -size, size);
		pose.rotate(scene.rotation());
		pose.translate(-scene.centre().x(), -scene.centre().y(), -scene.centre().z());
		item.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	@Override
	protected String getTextureLabel() {
		return "Cobble Comfort charm editor";
	}
}
