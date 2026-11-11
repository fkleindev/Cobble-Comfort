package dev.cobblecomfort.features.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.Voxels;
import net.minecraft.util.Mth;

/**
 * A bird of voxel boxes, nose along +z, up +y, in voxels of {@link BirdSpecies.Shape#scale} blocks. The wings hinge at
 * the shoulder (beat up and down, fold back along the body) and bend once more at the wrist.
 */
final class BirdModel {

	private BirdModel() {
	}

	/** The wings' pose: beat angle at the shoulder and at the wrist (up positive), and how far they are folded back. */
	record Wings(float shoulder, float wrist, float fold) {
	}

	/** Draws the bird in {@code pose}, which already sits at the bird and faces its way. */
	static void draw(VertexConsumer vc, PoseStack.Pose pose, BirdSpecies species, BirdSpecies.Look look, Wings wings,
			int light) {
		BirdSpecies.Shape s = species.shape;
		PoseStack.Pose p = pose.copy();
		p.scale(s.scale, s.scale, s.scale);
		float hl = s.bodyL / 2, hw = s.bodyW / 2, hh = s.bodyH / 2;

		// Body: back and belly.
		Voxels.box(vc, p, -hw, 0, -hl, hw, hh, hl, look.body(), light);
		Voxels.box(vc, p, -hw, -hh, -hl, hw, 0, hl, look.belly(), light);

		// Neck and head, a little above the body's middle.
		float headY = hh - s.head / 2 + 0.5f;
		float headZ = hl + s.neck + s.head / 2 - 0.5f;
		if (s.neck > 0) {
			float n = s.head * 0.3f;
			Voxels.box(vc, p, -n, headY - n, hl - 0.5f, n, headY + n, headZ, look.head(), light);
		}
		float r = s.head / 2;
		Voxels.box(vc, p, -r, headY - r, headZ - r, r, headY + r, headZ + r, look.head(), light);
		if (s.beak > 0) {
			float b = Math.min(0.6f, r * 0.45f);
			Voxels.box(vc, p, -b, headY - r * 0.4f - b, headZ + r, b, headY - r * 0.4f + b, headZ + r + s.beak, look.beak(), light);
		}
		if (s.eyes) {
			for (int side = -1; side <= 1; side += 2) {
				float x = side * r * 0.45f;
				Voxels.box(vc, p, x - 0.5f, headY - 0.2f, headZ + r, x + 0.5f, headY + 0.8f, headZ + r + 0.2f, 0xFFF0C030, Voxels.FULL_BRIGHT);
			}
		}
		if (s.ears) {
			for (int side = -1; side <= 1; side += 2) {
				float x = side * r * 0.6f;
				Voxels.box(vc, p, x - 0.4f, headY + r, headZ - 0.4f, x + 0.4f, headY + r + 1f, headZ + 0.4f, look.head(), light);
			}
		}

		// Tail, flat behind; legs trailing (herons).
		if (s.tail > 0) {
			float t = s.tailW / 2;
			Voxels.box(vc, p, -t, -0.4f, -hl - s.tail, t, 0.6f, -hl + 0.5f, look.tail(), light);
		}
		if (s.legs) {
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 0.6f;
				Voxels.box(vc, p, x - 0.3f, -hh + 0.2f, -hl - 6f, x + 0.3f, -hh + 0.8f, -hl + 1f, 0xFF3A3A30, light);
			}
		}

		// Wings from the shoulders, a little in front of the middle.
		float c = s.chord / 2;
		for (int side = -1; side <= 1; side += 2) {
			PoseStack.Pose w = p.copy();
			w.translate(side * hw, hh * 0.5f, hl * 0.2f);
			w.rotate(Axis.YP, side * wings.fold());
			w.rotate(Axis.ZP, side * wings.shoulder());
			float in0 = side < 0 ? -s.wingIn : 0, in1 = side < 0 ? 0 : s.wingIn;
			Voxels.box(vc, w, in0, -0.4f, -c, in1, 0.4f, c, look.wing(), light);
			w.translate(side * s.wingIn, 0, 0);
			w.rotate(Axis.ZP, side * wings.wrist());
			// The hand: wing colour first, then the darker tip, narrowing.
			float mid = s.wingOut * 0.55f;
			float a0 = side < 0 ? -mid : 0, a1 = side < 0 ? 0 : mid;
			Voxels.box(vc, w, a0, -0.35f, -c, a1, 0.35f, c * 0.8f, look.wing(), light);
			float b0 = side < 0 ? -s.wingOut : mid, b1 = side < 0 ? -mid : s.wingOut;
			Voxels.box(vc, w, b0, -0.3f, -c, b1, 0.3f, c * 0.4f, look.tip(), light);
		}
	}

	/** The wing pose at {@code time} (ticks, with this bird's own offset), for the way the species flies. */
	static Wings wings(BirdSpecies.Flight flight, float time, float climb) {
		switch (flight) {
			case FLAP -> {
				float beat = Mth.sin(time * 0.75f);
				return new Wings(0.15f + beat * 0.75f, beat * 0.35f + 0.1f, 0.05f);
			}
			case SLOW_FLAP -> {
				float beat = Mth.sin(time * 0.32f);
				return new Wings(0.05f + beat * 0.6f, -0.15f + Mth.sin(time * 0.32f - 0.7f) * 0.35f, 0.15f);
			}
			case BOUND -> {
				float cycle = time % 22f;
				if (cycle < 9f) {
					float beat = Mth.sin(cycle * 1.6f);
					return new Wings(0.2f + beat * 0.8f, beat * 0.3f, 0.05f);
				}
				// Folded: a short smooth tuck in and out.
				float tuck = Mth.sin((cycle - 9f) / 13f * Mth.PI);
				return new Wings(0.1f, 0f, 0.05f + tuck * 1.25f);
			}
			case SOAR -> {
				// A few beats every so often, more when climbing; otherwise held wings with a gentle wobble.
				float cycle = time % 140f;
				float beats = climb > 0.04f ? 1f : cycle < 18f ? Mth.sin(cycle / 18f * Mth.PI) : 0f;
				float beat = Mth.sin(time * 0.55f) * 0.55f * beats;
				float hold = 0.14f + Mth.sin(time * 0.07f) * 0.03f;
				return new Wings(hold + beat, -0.08f + beat * 0.4f, 0.08f);
			}
			case GLIDE -> {
				float cycle = time % 80f;
				float beats = cycle < 24f ? Mth.sin(cycle / 24f * Mth.PI) : 0f;
				float beat = Mth.sin(time * 0.4f) * 0.6f * beats;
				return new Wings(0.05f + beat, beat * 0.3f, 0.05f);
			}
			case FLUTTER -> {
				float beat = Mth.sin(time * 1.9f);
				return new Wings(beat * 0.9f, beat * 0.6f, 0.1f + Mth.sin(time * 0.37f) * 0.1f);
			}
		}
		return new Wings(0f, 0f, 0f);
	}
}
