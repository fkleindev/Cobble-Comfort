package dev.cobblecomfort.features.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small particle system of its own: soft puffs, rings lying on water and glowing dots, drawn straight from the
 * texture. Vanilla particle types would have to be registered, which a client-only mod should not do.
 */
final class Motes {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/atmosphere.png");
	private static final int MAX = 600;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** How dark an unlit mote gets in total darkness. */
	private static final float MIN_BRIGHTNESS = 0.12f;

	enum Shape {
		/** Faces the camera, soft and round. */
		PUFF,
		/** Lies flat, a thin circle. */
		RING,
		/** Faces the camera and keeps its brightness in the dark. */
		GLOW
	}

	static final class Mote {
		final Shape shape;
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		/** Velocity kept each tick. */
		float drag = 1f;
		float size0;
		float size1;
		float alpha;
		int color;
		int life;
		int age;
		/** Ticks it takes to fade in. */
		int fadeIn;
		/** Ticks it fades out over at the end of its life; 0 fades over the whole life. */
		int fadeOut;
		/** Brightness for glowing motes, set by whoever moves them. */
		float glow = 1f;
		/** Moves a mote each tick on top of its velocity (fireflies wander), may be null. */
		Consumer<Mote> mover;

		Mote(Shape shape, double x, double y, double z, int life) {
			this.shape = shape;
			this.x = x;
			this.y = y;
			this.z = z;
			this.life = life;
		}

		float progress(float partial) {
			return Mth.clamp((age + partial) / life, 0f, 1f);
		}
	}

	private final List<Mote> motes = new ArrayList<>();

	Mote add(Mote mote) {
		if (motes.size() < MAX) {
			motes.add(mote);
		}
		return mote;
	}

	int count(Shape shape) {
		int n = 0;
		for (Mote mote : motes) {
			if (mote.shape == shape) {
				n++;
			}
		}
		return n;
	}

	void clear() {
		motes.clear();
	}

	void tick() {
		Iterator<Mote> it = motes.iterator();
		while (it.hasNext()) {
			Mote mote = it.next();
			if (++mote.age >= mote.life) {
				it.remove();
				continue;
			}
			if (mote.mover != null) {
				mote.mover.accept(mote);
			}
			mote.x += mote.vx;
			mote.y += mote.vy;
			mote.z += mote.vz;
			mote.vx *= mote.drag;
			mote.vy *= mote.drag;
			mote.vz *= mote.drag;
		}
	}

	void render(LevelRenderContext context, ClientLevel level, Camera camera, float partial) {
		if (motes.isEmpty()) {
			return;
		}
		Vec3 eye = camera.position();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		List<Mote> snapshot = List.copyOf(motes);
		int darken = level.getSkyDarken();
		// The shadow type writes no depth, so water and glass drawn later still show behind the motes. It has no lighting
		// of its own: the colour is darkened by the light where the mote is, except for glowing ones.
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityShadow(TEXTURE), (base, consumer) -> {
			for (Mote mote : snapshot) {
				float brightness = 1f;
				if (mote.shape != Shape.GLOW) {
					int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(mote.x, mote.y, mote.z));
					int level15 = Math.max(LightCoordsUtil.block(light), LightCoordsUtil.sky(light) - darken);
					brightness = Mth.lerp(Mth.clamp(level15 / 15f, 0f, 1f), MIN_BRIGHTNESS, 1f);
				}
				draw(base, consumer, mote, eye, left, up, partial, brightness);
			}
		});
	}

	private static void draw(PoseStack.Pose base, VertexConsumer consumer, Mote mote, Vec3 eye, Vector3fc left, Vector3fc up,
			float partial, float brightness) {
		float t = mote.progress(partial);
		float size = Mth.lerp(1f - (1f - t) * (1f - t), mote.size0, mote.size1) / 2f;
		float fadeIn = mote.fadeIn <= 0 ? 1f : Mth.clamp((mote.age + partial) / mote.fadeIn, 0f, 1f);
		float fadeOut = mote.fadeOut <= 0 ? 1f - t : Mth.clamp((mote.life - mote.age - partial) / mote.fadeOut, 0f, 1f);
		float alpha = mote.alpha * fadeIn * fadeOut * (mote.shape == Shape.GLOW ? mote.glow : 1f);
		if (alpha < 0.01f) {
			return;
		}
		float cx = (float) (mote.x + mote.vx * partial - eye.x);
		float cy = (float) (mote.y + mote.vy * partial - eye.y);
		float cz = (float) (mote.z + mote.vz * partial - eye.z);
		int color = RenderUtil.withAlpha(RenderUtil.lerpColor(0xFF000000, mote.color, brightness), alpha);
		float u0 = mote.shape.ordinal() / 3f;
		float u1 = (mote.shape.ordinal() + 1) / 3f;
		if (mote.shape == Shape.RING) {
			vertex(base, consumer, cx - size, cy, cz - size, u0, 0f, color);
			vertex(base, consumer, cx - size, cy, cz + size, u0, 1f, color);
			vertex(base, consumer, cx + size, cy, cz + size, u1, 1f, color);
			vertex(base, consumer, cx + size, cy, cz - size, u1, 0f, color);
			return;
		}
		float lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
		float ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
		vertex(base, consumer, cx + lx + ux, cy + ly + uy, cz + lz + uz, u0, 0f, color);
		vertex(base, consumer, cx + lx - ux, cy + ly - uy, cz + lz - uz, u0, 1f, color);
		vertex(base, consumer, cx - lx - ux, cy - ly - uy, cz - lz - uz, u1, 1f, color);
		vertex(base, consumer, cx - lx + ux, cy - ly + uy, cz - lz + uz, u1, 0f, color);
	}

	private static void vertex(PoseStack.Pose base, VertexConsumer consumer, float x, float y, float z, float u, float v,
			int color) {
		consumer.addVertex(base, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT).setNormal(base, 0f, 1f, 0f);
	}
}
