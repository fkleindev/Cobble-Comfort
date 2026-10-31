package dev.cobblecomfort.features.namecharm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Draws the name charm: a short string from the item's handle and a little paper tag with the name on both sides. It
 * is an extra layer of the item, drawn in the item's own space (the unit cube), so it follows every transform the
 * item gets, from vanilla, from the resource pack or from mods like Punchy.
 */
public final class NameCharmRenderer implements SpecialModelRenderer<NameCharmRenderer.Args> {
	/** Can be replaced by resource packs: paper (top left), paper edge (top right), string (bottom left), eyelet. */
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/name_charm.png");
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);

	private static final float PX = 1f / 16f;
	private static final float STRING = 2.5f * PX;
	private static final float STRING_WIDTH = 0.3f * PX;
	private static final float TAG_HEIGHT = 3.6f * PX;
	private static final float TAG_PAD = 0.7f * PX;
	private static final float TAG_MIN_WIDTH = 4f * PX;
	private static final float THICKNESS = 0.3f * PX;
	private static final float EYELET = 0.7f * PX;
	/** Font pixels to item units: the text is about two item pixels tall. */
	static final float TEXT_SCALE = 2.1f * PX / 8f;

	/**
	 * What to draw. {@code material} is the texture the tag is cut from (a block texture of the item's material, so
	 * it follows the resource pack), or null for plain paper; {@code ink} is the text colour that reads well on it.
	 * {@code pendulum} is null where the charm just hangs down (item frames, the ground).
	 */
	public record Args(FormattedCharSequence text, int textWidth, Identifier material, int ink, Vector3fc anchor,
			CharmPendulum pendulum) {
	}

	@Override
	public void submit(Args args, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
			boolean foil, int outlineColor) {
		if (args == null) {
			return;
		}
		Matrix4f model = new Matrix4f(pose.last().pose());
		Vector3f down = hangDirection(args, model);
		if (down == null) {
			return;
		}
		// The tag's frame in item space: hanging along `down`, facing the item's front (+z) as far as it can.
		Vector3f normal = new Vector3f(0, 0, 1).fma(-down.z, down);
		if (normal.lengthSquared() < 1e-4f) {
			normal.set(1, 0, 0).fma(-down.x, down);
		}
		normal.normalize();
		Vector3f right = new Vector3f(normal).cross(down).normalize();
		Vector3f up = new Vector3f(down).negate();

		float width = Math.max(TAG_MIN_WIDTH, args.textWidth() * TEXT_SCALE + 2 * TAG_PAD);
		Vector3f anchor = new Vector3f(args.anchor());
		Vector3f stringCenter = new Vector3f(anchor).fma(STRING / 2f, down);
		Vector3f tagCenter = new Vector3f(anchor).fma(STRING + TAG_HEIGHT / 2f, down);
		Vector3f eyelet = new Vector3f(anchor).fma(STRING + EYELET, down);

		collector.submitCustomGeometry(pose, TYPE, (p, vc) -> {
			float[] string = cell(0f, 0.5f);
			box(vc, p, stringCenter, new Vector3f(right).mul(STRING_WIDTH / 2f), new Vector3f(up).mul(STRING / 2f),
					new Vector3f(normal).mul(STRING_WIDTH / 2f), light, string, string, string);
			if (args.material() == null) {
				float[] edge = cell(0.5f, 0f);
				box(vc, p, tagCenter, new Vector3f(right).mul(width / 2f), new Vector3f(up).mul(TAG_HEIGHT / 2f),
						new Vector3f(normal).mul(THICKNESS / 2f), light, cell(0f, 0f), edge, edge);
			}
			// The eyelet the string goes through, on both sides.
			for (int side = -1; side <= 1; side += 2) {
				Vector3f c = new Vector3f(eyelet).fma(side * (THICKNESS / 2f + 0.01f * PX), normal);
				Vector3f r = new Vector3f(right).mul(EYELET / 2f * side);
				Vector3f u = new Vector3f(up).mul(EYELET / 2f);
				Vector3f n = new Vector3f(normal).mul(side);
				quad(vc, p, c, r, u, n, light, cell(0.5f, 0.5f));
			}
		});
		if (args.material() != null) {
			// Cut from the material at its real scale (one texel per item pixel), from the middle of the texture.
			float u0 = 0.5f - width / 2f;
			float v0 = 0.5f - TAG_HEIGHT / 2f;
			float[] face = {u0, v0, u0 + width, v0 + TAG_HEIGHT};
			float[] side = {u0, v0, u0 + THICKNESS, v0 + TAG_HEIGHT};
			float[] rim = {u0, v0, u0 + width, v0 + THICKNESS};
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(args.material()), (p, vc) ->
					box(vc, p, tagCenter, new Vector3f(right).mul(width / 2f), new Vector3f(up).mul(TAG_HEIGHT / 2f),
							new Vector3f(normal).mul(THICKNESS / 2f), light, face, side, rim));
		}

		// The name on the front and, mirrored, on the back.
		float textTop = STRING + EYELET * 2f;
		float textCenter = (textTop + STRING + TAG_HEIGHT) / 2f;
		for (int side = -1; side <= 1; side += 2) {
			Vector3f r = new Vector3f(right).mul(side);
			Vector3f n = new Vector3f(normal).mul(side);
			Vector3f origin = new Vector3f(anchor).fma(textCenter, down).fma(THICKNESS / 2f + 0.02f * PX, n);
			Matrix4f frame = new Matrix4f(
					r.x, r.y, r.z, 0f,
					down.x, down.y, down.z, 0f,
					n.x, n.y, n.z, 0f,
					origin.x, origin.y, origin.z, 1f);
			pose.pushPose();
			pose.mulPose(frame);
			pose.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
			collector.submitText(pose, -args.textWidth() / 2f, -3.5f, args.text(), false, Font.DisplayMode.POLYGON_OFFSET,
					light, args.ink(), 0, 0);
			pose.popPose();
		}
	}

	/**
	 * Where the charm hangs, as a unit vector in item space: straight down in the world, or wherever the pendulum
	 * swings. Null if the item is squashed flat (e.g. while being equipped).
	 */
	private static Vector3f hangDirection(Args args, Matrix4f model) {
		Matrix3f linear = model.get3x3(new Matrix3f());
		if (Math.abs(linear.determinant()) < 1e-12f) {
			return null;
		}
		// Items in the first-person hand are drawn relative to the camera; the world turns with it.
		boolean inHand = NameCharmFeature.INSTANCE.inFirstPersonPass();
		Minecraft mc = Minecraft.getInstance();
		Quaternionf view = inHand ? new Quaternionf(mc.gameRenderer.mainCamera().rotation()) : new Quaternionf();
		Vector3d world;
		CharmPendulum pendulum = args.pendulum();
		if (pendulum == null) {
			world = new Vector3d(0, -1, 0);
		} else {
			Vec3 camera = mc.gameRenderer.mainCamera().position();
			Vector3f a = model.transformPosition(new Vector3f(args.anchor()));
			Vector3f anchorWorld = view.transform(a);
			// The string's length as it is drawn here (items in hand, frames and on the ground differ in size).
			float length = linear.transform(new Vector3f(0, STRING + TAG_HEIGHT / 2f, 0)).length();
			double damping = NameCharmFeature.INSTANCE.damping();
			Vector3dc dir = pendulum.update(new Vector3d(anchorWorld).add(camera.x, camera.y, camera.z), length, damping,
					System.nanoTime());
			world = new Vector3d(dir);
		}
		Vector3f local = new Vector3f((float) world.x, (float) world.y, (float) world.z);
		view.conjugate().transform(local);
		linear.invert().transform(local);
		if (local.lengthSquared() < 1e-12f) {
			return null;
		}
		return local.normalize();
	}

	/** A string of the charm texture from {@code from} along the unit vector {@code dir}, also used by other charms. */
	public static void submitString(PoseStack pose, SubmitNodeCollector collector, Vector3fc from, Vector3fc dir, float length,
			float width, int light) {
		Vector3f down = new Vector3f(dir).normalize();
		Vector3f side = Math.abs(down.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1);
		Vector3f right = side.fma(-side.dot(down), down).normalize();
		Vector3f normal = new Vector3f(right).cross(down).normalize();
		Vector3f center = new Vector3f(from).fma(length / 2f, down);
		float[] string = cell(0f, 0.5f);
		collector.submitCustomGeometry(pose, TYPE, (p, vc) -> box(vc, p, center, right.mul(width / 2f),
				new Vector3f(down).mul(-length / 2f), normal.mul(width / 2f), light, string, string, string));
	}

	/** A box of the string texture around {@code center} with half extents {@code half} (e.g. a backpack strap). */
	public static void submitStrap(PoseStack pose, SubmitNodeCollector collector, Vector3fc center, Vector3fc half, int light) {
		float[] string = cell(0f, 0.5f);
		Vector3f c = new Vector3f(center);
		collector.submitCustomGeometry(pose, TYPE, (p, vc) -> box(vc, p, c, new Vector3f(half.x(), 0, 0),
				new Vector3f(0, -half.y(), 0), new Vector3f(0, 0, half.z()), light, string, string, string));
	}

	/** One quarter of the charm texture, {u0, v0, u1, v1}, kept off its edges. */
	private static float[] cell(float u, float v) {
		return new float[]{u + 0.03f, v + 0.03f, u + 0.47f, v + 0.47f};
	}

	/**
	 * A box around {@code c} with half extents {@code r}, {@code u}, {@code n} (right-handed). Front and back use
	 * {@code face}, the left and right sides {@code side}, top and bottom {@code rim} (texture rects {u0, v0, u1, v1}).
	 */
	private static void box(VertexConsumer vc, PoseStack.Pose p, Vector3f c, Vector3f r, Vector3f u, Vector3f n,
			int light, float[] face, float[] side, float[] rim) {
		Vector3f nr = new Vector3f(r).negate();
		Vector3f nu = new Vector3f(u).negate();
		Vector3f nn = new Vector3f(n).negate();
		quad(vc, p, new Vector3f(c).add(n), r, u, n, light, face);
		quad(vc, p, new Vector3f(c).add(nn), nr, u, nn, light, face);
		quad(vc, p, new Vector3f(c).add(r), nn, u, r, light, side);
		quad(vc, p, new Vector3f(c).add(nr), n, u, nr, light, side);
		quad(vc, p, new Vector3f(c).add(u), r, nn, u, light, rim);
		quad(vc, p, new Vector3f(c).add(nu), r, n, nu, light, rim);
	}

	/** A quad centred on {@code c} spanning {@code ±r} and {@code ±u}, facing {@code n}, textured with {@code uv}. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, Vector3f c, Vector3f r, Vector3f u, Vector3f n,
			int light, float[] uv) {
		Vector3f normal = new Vector3f(n).normalize();
		vertex(vc, p, c.x - r.x - u.x, c.y - r.y - u.y, c.z - r.z - u.z, uv[0], uv[3], light, normal);
		vertex(vc, p, c.x + r.x - u.x, c.y + r.y - u.y, c.z + r.z - u.z, uv[2], uv[3], light, normal);
		vertex(vc, p, c.x + r.x + u.x, c.y + r.y + u.y, c.z + r.z + u.z, uv[2], uv[1], light, normal);
		vertex(vc, p, c.x - r.x + u.x, c.y - r.y + u.y, c.z - r.z + u.z, uv[0], uv[1], light, normal);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light,
			Vector3f normal) {
		vc.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, normal.x, normal.y, normal.z);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
	}

	@Override
	public Args extractArgument(ItemStack stack) {
		return null;
	}
}
