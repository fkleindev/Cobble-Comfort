package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.features.petplush.Torso;
import dev.cobblecomfort.features.plush.PlushFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Pins a player's badges to the left of their chest, on top of armour and backpack straps. Each is a voxel model of two
 * layers, one pixel deep each: the disc, and in front of it the symbol, a vanilla item where the badge has one.
 */
final class BadgeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final RenderType TYPE = RenderTypes.entityCutout(Badge.TEXTURE);
	/** Pin size (blocks) and how far in front of the chest it sits (over the backpack straps). */
	private static final float SIZE = 0.12f;
	private static final float LIFT = 0.02f;
	/** One texture pixel of the pin, which is also how deep each layer is. */
	private static final float PIXEL = SIZE / Badge.CELL;
	/** A vanilla item symbol spans the whole face inside the rim: its sprites have a transparent margin. */
	private static final float ITEM_SIZE = Badge.ITEM_PIXELS * PIXEL;
	private static final int SYMBOL_SIZE = 8;
	private static final int BACK = 0xFF707070;
	/**
	 * Where each pin sits: across from the middle (+x is the wearer's left) and down from the top of the torso. Two
	 * side by side, the third below between them.
	 */
	private static final float[][] SLOTS = {{0.165f, 0.2f}, {0.06f, 0.19f}, {0.115f, 0.31f}};

	/** Which atlas pixels are solid, read once from the texture. */
	private static boolean[] solid;

	BadgeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (state.isInvisible || state.isSpectator || mc.level == null) {
			return;
		}
		Entity entity = mc.level.getEntity(state.id);
		if (!(entity instanceof AbstractClientPlayer player)) {
			return;
		}
		List<Badge> badges = WardrobeFeature.INSTANCE.badgesFor(player);
		if (badges.isEmpty()) {
			return;
		}
		boolean[] mask = solid(mc);
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		ModelPart body = getParentModel().body;
		Torso bare = Torso.of(body);
		// In front of armour, but placed by the chest itself: a chestplate makes it taller, not the pins higher.
		Torso torso = bare.inflated(Torso.worn(state, false));
		body.translateAndRotate(pose);
		float front = torso.front() - LIFT;
		float[][] pins = new float[badges.size()][];
		for (int i = 0; i < pins.length; i++) {
			pins[i] = new float[]{bare.centerX() + SLOTS[i][0], bare.top() + SLOTS[i][1]};
		}
		collector.submitCustomGeometry(pose, TYPE, (p, vc) -> {
			for (int i = 0; i < pins.length; i++) {
				Badge badge = badges.get(i);
				float left = pins[i][0] - SIZE / 2f;
				float top = pins[i][1] - SIZE / 2f;
				layer(vc, p, mask, badge.u(), badge.v(), Badge.CELL, left, top, front, front + PIXEL, true, light);
				if (badge.item().isEmpty()) {
					int inset = (Badge.CELL - SYMBOL_SIZE) / 2;
					layer(vc, p, mask, badge.u() + inset, badge.symbolV() + inset, SYMBOL_SIZE, left + inset * PIXEL, top + inset * PIXEL,
							front - PIXEL, front, false, light);
				}
			}
		});
		for (int i = 0; i < pins.length; i++) {
			ItemStack item = badges.get(i).item();
			if (!item.isEmpty()) {
				pose.pushPose();
				pose.translate(pins[i][0], pins[i][1], front - PIXEL / 2f);
				// Body space has y down and the chest facing -z: turn the item's front (+z) outwards, upright.
				pose.mulPose(new Matrix4f().rotationX((float) Math.PI));
				// Its model is a sixteenth deep: as deep as the pin's own layers.
				pose.scale(ITEM_SIZE, ITEM_SIZE, PIXEL * 16f);
				submitItem(pose, collector, item, player, light);
				pose.popPose();
			}
		}
		pose.popPose();
	}

	private static void submitItem(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, AbstractClientPlayer player, int light) {
		ItemStackRenderState state = new ItemStackRenderState();
		PlushFeature plush = PlushFeature.INSTANCE;
		plush.plain = true;
		try {
			Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack, ItemDisplayContext.NONE, player);
		} finally {
			plush.plain = false;
		}
		state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
	}

	/**
	 * Extrudes the solid pixels of an atlas square ({@code u}, {@code v}, {@code size} wide) into voxels from depth
	 * {@code z0} (the front) to {@code z1}, its top left corner at ({@code left}, {@code top}). Faces between two solid
	 * pixels are left out; the back only where it can be seen.
	 */
	private static void layer(VertexConsumer vc, PoseStack.Pose p, boolean[] mask, int u, int v, int size, float left, float top,
			float z0, float z1, boolean back, int light) {
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (!solid(mask, u + x, v + y, u, v, size)) {
					continue;
				}
				// Seen from the front, the wearer's right (-x) is on the left: the texture's left edge goes there.
				float x0 = left + x * PIXEL;
				float x1 = x0 + PIXEL;
				float y0 = top + y * PIXEL;
				float y1 = y0 + PIXEL;
				float tu0 = (u + x) / (float) Badge.ATLAS_WIDTH;
				float tv0 = (v + y) / (float) Badge.ATLAS_HEIGHT;
				float tu1 = (u + x + 1) / (float) Badge.ATLAS_WIDTH;
				float tv1 = (v + y + 1) / (float) Badge.ATLAS_HEIGHT;
				face(vc, p, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
						new float[][]{{tu0, tv0}, {tu1, tv0}, {tu1, tv1}, {tu0, tv1}}, 0f, 0f, -1f, -1, light);
				if (back) {
					face(vc, p, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
							new float[][]{{tu0, tv0}, {tu1, tv0}, {tu1, tv1}, {tu0, tv1}}, 0f, 0f, 1f, BACK, light);
				}
				// The sides take the pixel's own colour.
				float cu = (tu0 + tu1) / 2f;
				float cv = (tv0 + tv1) / 2f;
				float[][] flat = {{cu, cv}, {cu, cv}, {cu, cv}, {cu, cv}};
				if (!solid(mask, u + x - 1, v + y, u, v, size)) {
					face(vc, p, new float[][]{{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}}, flat, -1f, 0f, 0f, -1, light);
				}
				if (!solid(mask, u + x + 1, v + y, u, v, size)) {
					face(vc, p, new float[][]{{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}, flat, 1f, 0f, 0f, -1, light);
				}
				if (!solid(mask, u + x, v + y - 1, u, v, size)) {
					face(vc, p, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, flat, 0f, -1f, 0f, -1, light);
				}
				if (!solid(mask, u + x, v + y + 1, u, v, size)) {
					face(vc, p, new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, flat, 0f, 1f, 0f, -1, light);
				}
			}
		}
	}

	/** A quad facing ({@code nx}, {@code ny}, {@code nz}), its corners turned the way that is seen from there. */
	private static void face(VertexConsumer vc, PoseStack.Pose p, float[][] c, float[][] uv, float nx, float ny, float nz, int color, int light) {
		float ax = c[1][0] - c[0][0], ay = c[1][1] - c[0][1], az = c[1][2] - c[0][2];
		float bx = c[2][0] - c[1][0], by = c[2][1] - c[1][1], bz = c[2][2] - c[1][2];
		float facing = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz;
		for (int i = 0; i < 4; i++) {
			int k = facing >= 0 ? i : 3 - i;
			vc.addVertex(p, c[k][0], c[k][1], c[k][2]).setColor(color).setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(p, nx, ny, nz);
		}
	}

	private static boolean solid(boolean[] mask, int x, int y, int u, int v, int size) {
		return x >= u && y >= v && x < u + size && y < v + size && mask[y * Badge.ATLAS_WIDTH + x];
	}

	private static boolean[] solid(Minecraft mc) {
		if (solid == null) {
			solid = new boolean[Badge.ATLAS_WIDTH * Badge.ATLAS_HEIGHT];
			Optional<Resource> resource = mc.getResourceManager().getResource(Badge.TEXTURE);
			if (resource.isPresent()) {
				try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
					int width = Math.min(Badge.ATLAS_WIDTH, image.getWidth());
					int height = Math.min(Badge.ATLAS_HEIGHT, image.getHeight());
					for (int y = 0; y < height; y++) {
						for (int x = 0; x < width; x++) {
							solid[y * Badge.ATLAS_WIDTH + x] = (image.getPixel(x, y) >>> 24) >= 128;
						}
					}
				} catch (Exception e) {
					CobbleComfortClient.LOGGER.warn("Could not read the badge texture", e);
				}
			}
		}
		return solid;
	}
}
