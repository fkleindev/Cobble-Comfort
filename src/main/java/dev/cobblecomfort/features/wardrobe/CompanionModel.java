package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.Voxels;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The companions, in voxels (1/16 block), origin at the feet, nose along +z, up +y. Chibi proportions: big heads, big
 * shiny eyes, rosy cheeks, rounded edges. Everything moves from a {@link Pose}: walking (a little waddle), sitting,
 * sleeping (slow breaths, eyes shut), flying, hopping (squashed on landing, stretched in the air), where it looks, and
 * being petted (happy eyes, a wiggle, a wave).
 */
final class CompanionModel {
	private static final int COAL = 0xFF1E1E22;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int STICK = 0xFF5A3A22;
	private static final int GOLD = 0xFFE8C040;
	private static final int BLUSH = 0xFFF49AB0;
	private static final int LEAF = 0xFF5EA83A;
	/**
	 * What a companion does by itself now and then: nothing, rear up, nibble a snack, groom (red panda), hide, yawn (turtle),
	 * hop out of its box (kitten), howl, bow down to play, vanish into smoke and step out elsewhere (shadow wolf).
	 */
	static final int NONE = 0, STAND = 1, SNACK = 2, GROOM = 3, HIDE = 4, YAWN = 5, OUT = 6, HOWL = 7, PLAYBOW = 8, VANISH = 9;

	private CompanionModel() {
	}

	/**
	 * How it stands this frame. {@code walk} the stride's phase (radians) and {@code walking} how much it walks; {@code sit}
	 * and {@code sleep} 0 to 1; {@code fly} how far its wings are out and {@code flap} their beat's phase; where the
	 * head looks (radians, from the body); {@code happy} 0 to 1 after being petted; {@code squash} positive when landing,
	 * negative when stretched in a jump; {@code time} in ticks; {@code action} something it does by itself now and then
	 * (see {@link #STAND}), how far along it is (0 to 1) and how much it shows (easing in and out); {@code shade} how dark
	 * it is around it (0 bright to 1 dark: night, caves).
	 */
	record Pose(float walk, float walking, float sit, float sleep, float fly, float flap, float headYaw, float headPitch,
			float happy, float squash, float time, int action, float actionTime, float actionBlend, float shade) {
		static Pose idle(float time) {
			return new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, time, NONE, 0, 0, 0);
		}

		/** The same pose doing {@code action}, {@code time} of the way through, fully shown. */
		Pose doing(int action, float time) {
			return new Pose(walk, walking, sit, sleep, fly, flap, headYaw, headPitch, happy, squash, this.time, action, time, 1, shade);
		}

		/** The same pose in the dark ({@code shade} 0 to 1). */
		Pose inShade(float shade) {
			return new Pose(walk, walking, sit, sleep, fly, flap, headYaw, headPitch, happy, squash, time, action, actionTime, actionBlend, shade);
		}
	}

	/** How tall it is (blocks), for the name above it. */
	static float height(Companion.Style style) {
		return switch (style) {
			case MUSHROOM -> 10.6f / 16f;
			case CLOUD -> 9.4f / 16f;
			case DRAGON -> 12.4f / 16f;
			case OWL -> 12.8f / 16f;
			case GOLEM -> 11.8f / 16f;
			case PUMPKIN -> 10f / 16f;
			case SNOWMAN -> 10.2f / 16f;
			case BUNNY -> 14f / 16f;
			case RED_PANDA -> 10.4f / 16f;
			case TURTLE -> 6.6f / 16f;
			case KITTEN -> 10.6f / 16f;
			case SHADOW_WOLF -> 11.6f / 16f;
		};
	}

	static void draw(VertexConsumer vc, PoseStack.Pose base, Companion.Look look, Pose pose, int light) {
		Painter p = new Painter(vc, base, light);
		p.scale(1f / 16f);
		// Breathing (slower and deeper asleep), squash and stretch, a waddle, a happy wiggle.
		float breath = Mth.sin(pose.time * (pose.sleep > 0.5f ? 0.07f : 0.12f)) * (0.018f + pose.sleep * 0.035f);
		float sq = Mth.clamp(pose.squash, -0.6f, 1f);
		p.scale(1 + 0.14f * sq, (1 - 0.18f * sq) * (1 + breath), 1 + 0.14f * sq);
		p.rotateZ(Mth.sin(pose.walk) * 0.07f * pose.walking);
		p.rotateY(Mth.sin(pose.time * 0.9f) * 0.12f * pose.happy);
		Companion c = look.companion();
		switch (c.style) {
			case MUSHROOM -> mushroom(p, c, look, pose);
			case CLOUD -> cloud(p, c, look, pose);
			case DRAGON -> dragon(p, c, look, pose);
			case OWL -> owl(p, c, look, pose);
			case GOLEM -> golem(p, c, look, pose);
			case PUMPKIN -> pumpkin(p, c, look, pose);
			case SNOWMAN -> snowman(p, c, look, pose);
			case BUNNY -> bunny(p, c, look, pose);
			case RED_PANDA -> redPanda(p, c, look, pose);
			case TURTLE -> turtle(p, c, look, pose);
			case KITTEN -> kitten(p, c, look, pose);
			case SHADOW_WOLF -> shadowWolf(p, c, look, pose);
		}
	}

	// ---------------------------------------------------------------- the companions

	/** A fly agaric with a round face on its stem, stubby feet and arms, a layered cap with dots and a sprout on top. */
	private static void mushroom(Painter p, Companion c, Companion.Look look, Pose pose) {
		int stem = c.third;
		int stemDark = ARGB.scaleRGB(stem, 0.82f);
		float bob = Math.abs(Mth.sin(pose.walk)) * 0.6f * pose.walking;
		for (int side = -1; side <= 1; side += 2) {
			float phase = pose.walk + (side > 0 ? 0 : Mth.PI);
			float lift = Math.max(0, Mth.sin(phase)) * 1.0f * pose.walking;
			float fwd = Mth.cos(phase) * 1.0f * pose.walking + pose.sit * 1.5f;
			p.round(side * 1.2f - 1f, lift, fwd - 1.3f, side * 1.2f + 1f, lift + 1.3f, fwd + 1.2f, stemDark, 0.45f);
		}
		p.push();
		p.translate(0, 1.1f + bob - pose.sit * 0.9f - pose.sleep * 0.3f, 0);
		p.rotateY(pose.headYaw * 0.5f);
		p.rotateX(pose.headPitch * 0.25f + pose.sleep * 0.22f);
		p.round(-2.6f, 0, -2.4f, 2.6f, 1.2f, 2.4f, stemDark, 0.5f);
		p.round(-2.3f, 0, -2.1f, 2.3f, 4.6f, 2.1f, stem, 0.7f);
		// Little arms; the right one waves when petted.
		for (int side = -1; side <= 1; side += 2) {
			float swing = -Mth.sin(pose.walk + (side > 0 ? 0 : Mth.PI)) * 0.6f * pose.walking;
			float wave = side > 0 ? -2.3f * pose.happy + Mth.sin(pose.time * 0.8f) * 0.4f * pose.happy : 0;
			p.push();
			p.translate(side * 2.2f, 2.7f, 0);
			p.rotateX(swing + wave);
			p.rotateZ(side * (0.35f - pose.sleep * 0.2f));
			p.round(side < 0 ? -1.1f : 0, -1.9f, -0.55f, side < 0 ? 0 : 1.1f, 0.2f, 0.55f, stem, 0.35f);
			p.pop();
		}
		face(p, 2.1f, 2.8f, 1.05f, 0.95f, 1.35f, c.accent, pose, 0);
		// The cap: brim with gills, layers rounding up, a shine, dots, a sprout; it lags behind a step.
		p.push();
		p.translate(0, 4.3f, 0);
		p.rotateZ(Mth.sin(pose.walk * 2 - 0.8f) * 0.06f * pose.walking + Mth.sin(pose.time * 0.6f) * 0.08f * pose.happy);
		p.rotateX(Mth.cos(pose.walk * 2 - 0.8f) * 0.04f * pose.walking + pose.sleep * 0.12f);
		int gill = ARGB.scaleRGB(stem, 0.7f);
		p.round(-4.2f, -0.25f, -4.2f, 4.2f, 0.3f, 4.2f, gill, 0.3f);
		for (int i = -3; i <= 3; i++) {
			p.box(i * 1.1f - 0.08f, -0.3f, -3.8f, i * 1.1f + 0.08f, -0.2f, 3.8f, ARGB.scaleRGB(gill, 0.8f));
		}
		p.round(-4.7f, 0.2f, -4.7f, 4.7f, 1.8f, 4.7f, c.main, 0.75f);
		p.round(-4.0f, 1.5f, -4.0f, 4.0f, 3.0f, 4.0f, c.main, 0.8f);
		p.round(-3.0f, 2.8f, -3.0f, 3.0f, 3.9f, 3.0f, c.main, 0.7f);
		p.round(-1.8f, 3.7f, -1.8f, 1.8f, 4.3f, 1.8f, c.main, 0.5f);
		p.box(-1.5f, 4.3f, -1.2f, -0.4f, 4.36f, -0.1f, lighter(c.main, 50));
		p.box(-3.6f, 3.0f, -2.2f, -3.0f, 3.06f, -1.2f, lighter(c.main, 40));
		// Dots: on the brim's sides, the middle layer, the top.
		dotFront(p, -2.3f, 1.0f, 4.7f, 1.2f, c.second);
		dotFront(p, 2.1f, 1.0f, 4.7f, 0.8f, c.second);
		dotFront(p, 0.4f, 2.3f, 4.0f, 0.9f, c.second);
		dotFront(p, 1.0f, 1.0f, -4.7f, 1.0f, c.second);
		dotFront(p, -2.6f, 1.0f, -4.7f, 0.8f, c.second);
		dotSide(p, 4.7f, 0.9f, 1.4f, 1.1f, c.second);
		dotSide(p, 4.7f, 1.0f, -2.2f, 0.8f, c.second);
		dotSide(p, -4.7f, 1.0f, 0.4f, 1.2f, c.second);
		dotSide(p, -4.0f, 2.3f, -1.8f, 0.9f, c.second);
		dotSide(p, 4.0f, 2.3f, 2.0f, 0.8f, c.second);
		dotTop(p, -2.3f, 3.9f, 1.1f, 0.9f, c.second);
		dotTop(p, 2.2f, 3.9f, -1.4f, 1.0f, c.second);
		dotTop(p, 0.3f, 3.9f, 2.4f, 0.8f, c.second);
		dotTop(p, 0.6f, 4.3f, -0.6f, 0.8f, c.second);
		p.push();
		p.translate(0.4f, 4.3f, 0.3f);
		p.rotateZ(Mth.sin(pose.time * 0.15f) * 0.2f + Mth.sin(pose.walk * 2) * 0.2f * pose.walking);
		p.box(-0.12f, 0, -0.12f, 0.12f, 1.2f, 0.12f, LEAF);
		p.round(0.1f, 0.9f, -0.35f, 1.5f, 1.25f, 0.45f, LEAF, 0.15f);
		p.round(-1.1f, 0.6f, -0.3f, -0.1f, 0.9f, 0.4f, ARGB.scaleRGB(LEAF, 0.85f), 0.12f);
		p.pop();
		headWear(p, look, -1.6f, 3.9f, 0.8f);
		p.pop();
		neckWear(p, look, 3.85f, 0, 2.35f, 2.15f);
		p.pop();
	}

	/**
	 * A cloud sheep: a body of cumulus puffs that drift and swell each in its own time, a dark sheep's head poking out in
	 * front with a fluffy tuft and floppy ears, four short legs dangling below. The rain cloud drips tiny drops.
	 */
	private static void cloud(Painter p, Companion c, Companion.Look look, Pose pose) {
		p.push();
		p.translate(0, 1.6f + Mth.sin(pose.time * 0.12f) * 0.5f - pose.sit * 0.6f - pose.sleep * 0.7f, 0);
		p.rotateY(pose.headYaw * 0.4f);
		p.rotateX(pose.walking * 0.18f);
		p.rotateZ(Mth.sin(pose.time * 0.05f) * 0.05f);
		if (pose.sleep > 0) {
			p.scale3(1 + pose.sleep * 0.06f, 1 - pose.sleep * 0.12f, 1 + pose.sleep * 0.06f);
		}
		// A flat, slightly darker underside; then the puffs, each swelling in its own rhythm.
		p.round(-4.6f, 0, -3.2f, 4.6f, 2.0f, 3.0f, c.second, 0.9f);
		float[][] puffs = {
				{0, 3.4f, 0.3f, 3.1f, 2.4f, 2.8f}, {-3.1f, 2.7f, 0, 2.2f, 1.9f, 2.4f}, {3.2f, 2.9f, -0.2f, 2.3f, 2.0f, 2.5f},
				{-0.9f, 5.5f, -0.4f, 2.0f, 1.6f, 2.0f}, {1.7f, 5.0f, 0.2f, 1.6f, 1.4f, 1.7f}, {0.2f, 3.0f, -2.6f, 2.8f, 1.9f, 1.5f},
				{-2.4f, 4.4f, -1.4f, 1.4f, 1.3f, 1.4f}, {2.6f, 4.3f, -1.6f, 1.3f, 1.1f, 1.3f}, {0.6f, 6.6f, -0.9f, 1.1f, 0.8f, 1.1f}};
		for (int i = 0; i < puffs.length; i++) {
			float[] f = puffs[i];
			float swell = 1 + Mth.sin(pose.time * 0.07f + i * 1.7f) * 0.05f;
			float bob = Mth.sin(pose.time * 0.09f + i * 1.3f) * 0.18f;
			float hx = f[3] * swell, hy = f[4] * swell, hz = f[5] * swell;
			int color = i == 5 ? ARGB.scaleRGB(c.main, 0.96f) : c.main;
			p.round(f[0] - hx, f[1] - hy + bob, f[2] - hz, f[0] + hx, f[1] + hy + bob, f[2] + hz, color, Math.min(hx, Math.min(hy, hz)) * 0.6f);
		}
		// A shine on the top puff.
		p.box(-1.9f, 7.1f, -0.2f, -0.9f, 7.15f, 0.6f, lighter(c.main, 30));
		// Four short dark legs dangling under the cloud, pattering when it moves.
		for (int side = -1; side <= 1; side += 2) {
			for (int end = -1; end <= 1; end += 2) {
				float swing = Mth.sin(pose.walk * 1.4f + (side * end > 0 ? 0 : Mth.PI)) * 0.55f * pose.walking + 0.1f;
				float length = 1.8f - pose.sleep * 1.0f - pose.sit * 0.6f;
				p.push();
				p.translate(side * 2.2f, 0.3f, end * 1.5f);
				p.rotateX(swing);
				p.round(-0.5f, -length, -0.5f, 0.5f, 0, 0.5f, c.accent, 0.2f);
				p.pop();
			}
		}
		// The sheep's head poking out of the front: dark, round, a fluffy tuft on top, floppy ears, big light eyes.
		p.push();
		p.translate(0, 3.2f, 2.3f);
		p.rotateY(pose.headYaw * 0.8f);
		p.rotateX(pose.headPitch * 0.5f + pose.sleep * 0.3f);
		p.rotateZ(Mth.sin(pose.time * 0.045f) * 0.08f + pose.happy * 0.15f);
		p.round(-1.8f, -1.6f, -0.4f, 1.8f, 1.6f, 2.4f, c.third, 0.9f);
		p.round(-1.1f, -1.6f, 1.6f, 1.1f, -0.5f, 2.55f, lighter(c.third, 18), 0.35f);
		p.round(-1.7f, 1.0f, -0.3f, 1.7f, 2.3f, 1.9f, c.main, 0.55f);
		p.round(-0.9f, 1.9f, 0.2f, 0.7f, 2.7f, 1.5f, c.main, 0.35f);
		for (int side = -1; side <= 1; side += 2) {
			float flop = Mth.sin(pose.time * 0.1f + side) * 0.08f + pose.sleep * 0.4f - pose.happy * 0.3f;
			p.push();
			p.translate(side * 1.7f, 0.7f, 0.9f);
			p.rotateZ(side * (-0.45f - flop));
			p.round(side < 0 ? -1.5f : 0, -0.35f, -0.55f, side < 0 ? 0 : 1.5f, 0.35f, 0.55f, c.third, 0.2f);
			p.box(side < 0 ? -1.2f : 0.3f, -0.15f, 0.55f, side < 0 ? -0.3f : 1.2f, 0.15f, 0.6f, 0xFFE8A0B0);
			p.pop();
		}
		sheepFace(p, 2.4f, pose);
		headWear(p, look, 0, 2.6f, 0.9f);
		neckWear(p, look, -1.5f, 0.9f, 1.75f, 1.4f);
		p.pop();
		// The rain cloud never quite stops raining: little drops falling and fading below it.
		if (c == Companion.CLOUD_RAIN) {
			float[] xs = {-2.4f, 0.6f, 2.8f, -0.9f};
			for (int k = 0; k < xs.length; k++) {
				float fall = (pose.time * 0.18f + k * 0.65f) % 2.6f;
				p.box(xs[k] - 0.15f, -fall - 0.5f, (k % 2) * 1.2f - 0.6f - 0.15f, xs[k] + 0.15f, -fall, (k % 2) * 1.2f - 0.6f + 0.15f,
						0xFF7AA8E8);
			}
		}
		p.pop();
	}

	/** Light eyes on the sheep's dark face: white with a dark pupil and a shine; pale lines when shut; pink cheeks, a pale smile. */
	private static void sheepFace(Painter p, float z, Pose pose) {
		int pale = 0xFFE8E2E6;
		// The mouth sits on the lighter muzzle, a little further out.
		float m = z + 0.16f;
		Eye state = eye(pose, 0.2f);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 0.8f, y = 0.35f;
			if (state == Eye.OPEN) {
				p.box(x - 0.5f, y - 0.6f, z, x + 0.5f, y + 0.6f, z + 0.1f, WHITE);
				p.box(x - 0.3f + side * 0.1f, y - 0.5f, z + 0.1f, x + 0.3f + side * 0.1f, y + 0.25f, z + 0.15f, COAL);
				p.glow(x - 0.2f + side * 0.1f, y - 0.05f, z + 0.15f, x + side * 0.1f, y + 0.15f, z + 0.2f, WHITE);
			} else {
				closedEye(p, x, y, z, 1.1f, state, pale);
			}
			blush(p, side * 1.45f, -0.3f, z - 0.05f, pose);
		}
		if (pose.happy > 0.4f) {
			p.box(-0.45f, -1.2f, m, 0.45f, -0.75f, m + 0.1f, 0xFF4A1E22);
			p.box(-0.3f, -1.2f, m + 0.1f, 0.3f, -1.0f, m + 0.13f, 0xFFE87A90);
		} else {
			p.box(-0.3f, -1.05f, m, 0.3f, -0.88f, m + 0.1f, pale);
			p.box(-0.5f, -0.9f, m, -0.3f, -0.75f, m + 0.1f, pale);
			p.box(0.3f, -0.9f, m, 0.5f, -0.75f, m + 0.1f, pale);
		}
	}

	/** A chubby little dragon: a big head with a snout, horns and fins, shiny eyes, scalloped wings, a spade-tipped tail. */
	private static void dragon(Painter p, Companion c, Companion.Look look, Pose pose) {
		float lift = pose.fly;
		int dark = ARGB.scaleRGB(c.main, 0.8f);
		int claw = lighter(c.second, 30);
		// Legs: walking, tucked in flight, the hind ones folded when sitting.
		for (int side = -1; side <= 1; side += 2) {
			for (int end = -1; end <= 1; end += 2) {
				float swing = Mth.sin(pose.walk + (side * end > 0 ? 0 : Mth.PI)) * 0.6f * pose.walking * (1 - lift);
				p.push();
				p.translate(side * 1.7f, 2.4f, end * 1.6f);
				p.rotateX(swing + lift * 0.9f + (end < 0 ? pose.sit * 1.1f : 0) + pose.sleep * 1.2f);
				p.round(-0.85f, -2.4f, -0.9f, 0.85f, 0.2f, 0.9f, c.main, 0.4f);
				for (int t = -1; t <= 1; t++) {
					p.box(t * 0.5f - 0.15f, -2.4f, 0.8f, t * 0.5f + 0.15f, -2.1f, 1.25f, claw);
				}
				p.pop();
			}
		}
		p.push();
		p.translate(0, 2.0f - pose.sleep * 1.1f - pose.sit * 0.3f, 0);
		p.rotateX(-pose.sit * 0.3f + lift * 0.12f);
		p.round(-2.5f, 0, -2.9f, 2.5f, 3.9f, 2.9f, c.main, 1.1f);
		p.round(-1.8f, -0.1f, -2.2f, 1.8f, 2.8f, 3.05f, c.second, 0.7f);
		for (int i = 0; i < 3; i++) {
			p.box(-1.6f, 0.5f + i * 0.8f, 3.0f, 1.6f, 0.62f + i * 0.8f, 3.1f, ARGB.scaleRGB(c.second, 0.85f));
		}
		// Little back spikes, from big to small.
		for (int i = 0; i < 4; i++) {
			float z = 1.6f - i * 1.3f;
			float h = 0.8f - i * 0.12f;
			p.box(-0.4f, 3.7f, z - 0.45f, 0.4f, 3.7f + h, z + 0.45f, c.third);
			p.box(-0.22f, 3.7f + h, z - 0.25f, 0.22f, 3.7f + h + 0.35f, z + 0.25f, lighter(c.third, 20));
		}
		// The tail, swaying (wagging when happy, curled round in sleep), with a spade at the end.
		p.push();
		p.translate(0, 1.4f, -2.6f);
		for (int i = 0; i < 4; i++) {
			float w = 1.3f - i * 0.25f;
			float sway = Mth.sin(pose.time * 0.12f - i * 0.6f) * 0.22f + Mth.sin(pose.time * 0.9f - i * 0.5f) * 0.35f * pose.happy
					+ pose.sleep * 0.5f;
			p.rotateY(sway);
			p.rotateX(i == 0 ? 0.25f - lift * 0.2f : -0.06f);
			p.round(-w, -w * 0.8f, -2.3f, w, w * 0.8f, 0.2f, c.main, w * 0.45f);
			p.translate(0, 0, -2.1f);
		}
		p.round(-1.1f, -0.2f, -1.0f, 1.1f, 0.2f, 0.1f, c.third, 0.2f);
		p.round(-0.6f, -0.2f, -1.7f, 0.6f, 0.2f, -0.8f, c.third, 0.2f);
		p.pop();
		// Wings: an arm with three scalloped panels, then the hand with three more and a claw.
		for (int side = -1; side <= 1; side += 2) {
			float beat = Mth.sin(pose.flap) * 0.95f * lift;
			p.push();
			p.translate(side * 2.2f, 3.3f, 0.5f);
			p.rotateY(side * (1 - lift) * 1.05f);
			p.rotateZ(side * (beat + 0.4f * (1 - lift) + 0.08f + Mth.sin(pose.time * 0.1f) * 0.05f * (1 - lift)));
			wingPart(p, side, 4f, 2.8f, c.main, c.third);
			p.translate(side * 4f, 0, 0);
			p.rotateZ(side * (Mth.sin(pose.flap - 0.7f) * 0.55f * lift - 0.25f * (1 - lift)));
			wingPart(p, side, 3.4f, 2.3f, c.main, ARGB.scaleRGB(c.third, 0.92f));
			p.box(side < 0 ? -3.9f : 3.4f, -0.2f, 0.1f, side < 0 ? -3.4f : 3.9f, 0.2f, 0.6f, claw);
			p.pop();
		}
		neckWear(p, look, 3.4f, 2.5f, 1.4f, 1.2f);
		// Neck and the big head.
		p.push();
		p.translate(0, 3.0f, 2.2f);
		p.round(-1.25f, 0, -0.8f, 1.25f, 2.3f, 1.4f, c.main, 0.55f);
		p.translate(0, 2.0f, 0.6f);
		p.rotateY(pose.headYaw);
		p.rotateX(pose.headPitch + pose.sleep * 0.6f);
		p.rotateZ(Mth.sin(pose.time * 0.04f) * 0.08f + pose.happy * 0.15f);
		p.round(-2.7f, -0.4f, -1.9f, 2.7f, 3.7f, 2.6f, c.main, 1.1f);
		p.round(-1.8f, -0.3f, 2.2f, 1.8f, 1.9f, 4.4f, c.main, 0.65f);
		p.round(-1.6f, -0.55f, 2.2f, 1.6f, 0.3f, 4.2f, c.second, 0.3f);
		p.box(-1.0f, 1.25f, 4.38f, -0.45f, 1.6f, 4.48f, dark);
		p.box(0.45f, 1.25f, 4.38f, 1.0f, 1.6f, 4.48f, dark);
		// A little smile with one fang; open and happy when petted.
		if (pose.happy > 0.3f) {
			p.box(-0.9f, 0.3f, 4.3f, 0.9f, 0.85f, 4.42f, 0xFF5A1E22);
			p.box(-0.6f, 0.3f, 4.35f, 0.6f, 0.5f, 4.45f, 0xFFE87A90);
		} else {
			p.box(-1.0f, 0.55f, 4.38f, 1.0f, 0.68f, 4.46f, dark);
		}
		p.box(0.45f, 0.25f, 4.32f, 0.75f, 0.6f, 4.42f, WHITE);
		dragonEye(p, -1.45f, 2.3f, 2.6f, c, pose);
		dragonEye(p, 1.45f, 2.3f, 2.6f, c, pose);
		blush(p, -2.35f, 1.3f, 2.6f, pose);
		blush(p, 2.35f, 1.3f, 2.6f, pose);
		for (int side = -1; side <= 1; side += 2) {
			// Curved horns.
			p.push();
			p.translate(side * 1.4f, 3.4f, -0.3f);
			p.rotateX(-0.5f);
			p.rotateZ(-side * 0.15f);
			p.round(-0.45f, 0, -0.45f, 0.45f, 1.4f, 0.45f, c.second, 0.2f);
			p.translate(0, 1.3f, 0);
			p.rotateX(-0.6f);
			p.round(-0.3f, 0, -0.3f, 0.3f, 1.0f, 0.3f, lighter(c.second, 25), 0.15f);
			p.pop();
			// Ear fins, fluttering.
			p.push();
			p.translate(side * 2.6f, 2.2f, -0.4f);
			p.rotateY(side * (0.5f + Mth.sin(pose.time * 0.2f + side) * 0.12f));
			p.box(side < 0 ? -1.6f : 0, -0.9f, -0.1f, side < 0 ? 0 : 1.6f, 0.9f, 0.1f, c.third);
			p.box(side < 0 ? -2.1f : 1.6f, -0.4f, -0.08f, side < 0 ? -1.6f : 2.1f, 0.5f, 0.08f, c.third);
			p.pop();
		}
		headWear(p, look, 0, 3.7f, 0.3f);
		p.pop();
		p.pop();
	}

	/** One part of a dragon wing: the bone along its front, three panels of skin behind, shorter towards the tip. */
	private static void wingPart(Painter p, int side, float length, float depth, int bone, int skin) {
		float a0 = side < 0 ? -length : 0, a1 = side < 0 ? 0 : length;
		p.round(a0, -0.35f, -0.35f, a1, 0.35f, 0.35f, bone, 0.15f);
		float third = length / 3f;
		for (int k = 0; k < 3; k++) {
			float x0 = k * third, x1 = (k + 1) * third;
			float d = depth * (1 - k * 0.18f);
			p.box(side < 0 ? -x1 : x0, -0.1f, -d, side < 0 ? -x0 : x1, 0.1f, 0, skin);
			// The spar between two panels.
			p.box(side < 0 ? -x1 - 0.1f : x1 - 0.1f, -0.15f, -d - 0.2f, side < 0 ? -x1 + 0.1f : x1 + 0.1f, 0.15f, 0, bone);
		}
	}

	private static void dragonEye(Painter p, float x, float y, float z, Companion c, Pose pose) {
		Eye state = eye(pose, 0);
		if (state != Eye.OPEN) {
			closedEye(p, x, y, z, 1.3f, state, ARGB.scaleRGB(c.main, 0.45f));
			return;
		}
		p.box(x - 0.7f, y - 0.8f, z, x + 0.7f, y + 0.8f, z + 0.1f, 0xFF1E1A22);
		p.glow(x - 0.55f, y - 0.65f, z + 0.1f, x + 0.55f, y + 0.6f, z + 0.15f, c.accent);
		p.box(x - 0.2f, y - 0.55f, z + 0.15f, x + 0.2f, y + 0.45f, z + 0.2f, 0xFF140E14);
		p.glow(x - 0.55f, y + 0.15f, z + 0.2f, x - 0.15f, y + 0.55f, z + 0.25f, WHITE);
		p.glow(x + 0.2f, y - 0.5f, z + 0.2f, x + 0.4f, y - 0.3f, z + 0.25f, WHITE);
	}

	/** A round owl: a big head with a heart-shaped face, huge eyes, tufts; chevrons on the belly, layered wing feathers. */
	private static void owl(Painter p, Companion c, Companion.Look look, Pose pose) {
		int talon = 0xFFE0B040;
		int beak = 0xFFE0A030;
		float tuck = Math.max(pose.fly, pose.sit);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 1.25f;
			for (int t = -1; t <= 1; t++) {
				p.box(x + t * 0.5f - 0.18f, 0, 0.5f, x + t * 0.5f + 0.18f, 0.55f - tuck * 0.2f, 1.9f + (t == 0 ? 0.25f : 0), talon);
			}
			p.round(x - 0.85f, 0.4f, -0.3f, x + 0.85f, 1.6f, 1.2f, c.second, 0.4f);
		}
		p.push();
		p.translate(0, 0.9f - pose.sleep * 0.4f, 0);
		p.round(-3.0f, 0.4f, -2.7f, 3.0f, 6.0f, 2.6f, c.main, 1.3f);
		p.round(-2.2f, 0.6f, 1.5f, 2.2f, 5.0f, 2.8f, c.second, 0.9f);
		// Chevrons down the belly.
		for (int r = 0; r < 3; r++) {
			for (int k = -1; k <= 1; k++) {
				float x = k * 1.2f + (r % 2) * 0.6f - 0.3f;
				float y = 1.3f + r * 1.1f;
				if (Math.abs(x) > 1.6f) {
					continue;
				}
				p.box(x - 0.5f, y + 0.22f, 2.78f, x - 0.12f, y + 0.47f, 2.88f, c.third);
				p.box(x - 0.15f, y, 2.78f, x + 0.15f, y + 0.27f, 2.88f, c.third);
				p.box(x + 0.12f, y + 0.22f, 2.78f, x + 0.5f, y + 0.47f, 2.88f, c.third);
			}
		}
		for (int i = -1; i <= 1; i++) {
			p.round(i * 0.8f - 0.42f, 0.7f, -3.8f, i * 0.8f + 0.42f, 1.3f, -2.2f, i == 0 ? c.third : ARGB.scaleRGB(c.main, 0.85f), 0.15f);
		}
		// Wings: down along the sides, beating out in flight; feathers layered towards dark tips.
		for (int side = -1; side <= 1; side += 2) {
			float beat = Mth.sin(pose.flap) * 0.9f;
			float rest = -1.42f + Mth.sin(pose.time * 0.6f) * 0.15f * pose.happy;
			float angle = Mth.lerp(pose.fly, rest, beat);
			p.push();
			p.translate(side * 2.9f, 5.0f, -0.1f);
			p.rotateZ(side * angle);
			p.round(side < 0 ? -3.4f : 0, -0.35f, -2.3f, side < 0 ? 0 : 3.4f, 0.35f, 2.0f, ARGB.scaleRGB(c.main, 0.86f), 0.3f);
			p.box(side < 0 ? -2.4f : 0.3f, -0.42f, -2.1f, side < 0 ? -0.3f : 2.4f, 0.42f, 1.7f, c.main);
			for (int k = 0; k < 3; k++) {
				float z0 = -2.2f + k * 1.4f;
				float len = 5.4f - k * 0.45f;
				p.box(side < 0 ? -len : 2.8f, -0.28f, z0, side < 0 ? -2.8f : len, 0.28f, z0 + 1.3f, k == 1 ? c.third : ARGB.scaleRGB(c.third, 0.85f));
			}
			p.pop();
		}
		neckWear(p, look, 5.5f, 0, 2.95f, 2.65f);
		p.push();
		p.translate(0, 5.6f, 0);
		p.rotateY(pose.headYaw * 1.6f);
		p.rotateX(pose.headPitch + pose.sleep * 0.35f);
		p.rotateZ(Mth.sin(pose.time * 0.035f) * 0.14f * (1 - pose.sleep) + pose.happy * 0.25f);
		p.round(-3.3f, 0, -2.7f, 3.3f, 4.5f, 2.8f, c.main, 1.4f);
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 1.4f - 1.5f, 0.6f, 2.45f, side * 1.4f + 1.5f, 3.7f, 3.05f, c.second, 0.65f);
		}
		// A wink now and then.
		boolean wink = pose.time % 160 >= 80 && pose.time % 160 < 87;
		owlEye(p, -1.4f, 2.2f, 3.05f, c, pose, false);
		owlEye(p, 1.4f, 2.2f, 3.05f, c, pose, wink);
		p.box(-0.5f, 1.1f, 3.05f, 0.5f, 1.95f, 3.6f, beak);
		p.box(-0.3f, 0.75f, 3.05f, 0.3f, 1.15f, 3.45f, ARGB.scaleRGB(beak, 0.8f));
		blush(p, -2.5f, 1.2f, 2.95f, pose);
		blush(p, 2.5f, 1.2f, 2.95f, pose);
		for (int i = 0; i < 4; i++) {
			p.box(-1.6f + i * 1.0f, 4.45f, -0.6f + (i % 2) * 0.8f, -1.3f + i * 1.0f, 4.55f, -0.3f + (i % 2) * 0.8f, c.third);
		}
		for (int side = -1; side <= 1; side += 2) {
			p.push();
			p.translate(side * 2.4f, 4.1f, 0.2f);
			p.rotateZ(-side * 0.35f + Mth.sin(pose.time * 0.1f + side) * 0.06f);
			p.round(-0.6f, 0, -0.5f, 0.6f, 1.3f, 0.5f, c.main, 0.3f);
			p.translate(0, 1.1f, 0);
			p.rotateZ(-side * 0.3f);
			p.round(-0.4f, 0, -0.35f, 0.4f, 1.0f, 0.35f, c.third, 0.2f);
			p.pop();
		}
		headWear(p, look, 0, 4.5f, 0);
		p.pop();
		p.pop();
	}

	private static void owlEye(Painter p, float x, float y, float z, Companion c, Pose pose, boolean wink) {
		Eye state = wink ? Eye.HAPPY : eye(pose, 17);
		if (state != Eye.OPEN) {
			closedEye(p, x, y, z, 1.6f, state, c.third);
			return;
		}
		p.box(x - 0.95f, y - 0.95f, z, x + 0.95f, y + 0.95f, z + 0.1f, c.accent);
		p.box(x - 0.55f, y - 0.6f, z + 0.1f, x + 0.55f, y + 0.55f, z + 0.15f, 0xFF16120E);
		p.glow(x - 0.5f, y + 0.1f, z + 0.15f, x - 0.1f, y + 0.5f, z + 0.2f, WHITE);
		p.glow(x + 0.2f, y - 0.45f, z + 0.15f, x + 0.4f, y - 0.25f, z + 0.2f, WHITE);
	}

	/** A small round stone golem: a big head with a long nose and glowing eyes, a glowing heart on its chest, a flower in hand. */
	private static void golem(Painter p, Companion c, Companion.Look look, Pose pose) {
		int dark = ARGB.scaleRGB(c.main, 0.82f);
		float sink = pose.sit * 2.2f + pose.sleep * 2.4f;
		for (int side = -1; side <= 1; side += 2) {
			float swing = Mth.sin(pose.walk + (side > 0 ? 0 : Mth.PI)) * 0.55f * pose.walking;
			p.push();
			p.translate(side * 1.6f, 2.6f - sink, 0);
			p.rotateX(swing - pose.sit * 1.4f - pose.sleep * 1.4f);
			p.round(-1.2f, -2.6f, -1.2f, 1.2f, 0, 1.2f, dark, 0.45f);
			p.round(-1.35f, -2.6f, -1.3f, 1.35f, -1.8f, 1.7f, ARGB.scaleRGB(dark, 0.9f), 0.3f);
			p.pop();
		}
		p.push();
		float stomp = Math.abs(Mth.sin(pose.walk)) * 0.35f * pose.walking;
		p.translate(0, 2.4f - sink + stomp, 0);
		p.rotateZ(Mth.sin(pose.walk) * 0.08f * pose.walking);
		p.round(-3.0f, 0, -2.2f, 3.0f, 5.2f, 2.2f, c.main, 0.9f);
		// Cobbles standing out (moss on the mossy one).
		float[][] stones = {{-2.0f, 4.0f, 2.2f, 1.4f}, {1.9f, 1.2f, 2.2f, 1.2f}, {-1.4f, 1.0f, 2.2f, 1.0f}, {2.3f, 4.2f, 2.2f, 0.9f},
				{-1.8f, 3.2f, -2.2f, 1.5f}, {1.2f, 1.6f, -2.2f, 1.3f}, {0.6f, 4.2f, -2.2f, 1.0f}, {3.0f, 2.2f, 0.6f, 1.4f},
				{3.0f, 4.0f, -1.2f, 1.0f}, {-3.0f, 1.8f, -0.8f, 1.3f}, {-3.0f, 3.9f, 1.0f, 1.1f}};
		for (int i = 0; i < stones.length; i++) {
			float[] s = stones[i];
			int color = i % 3 == 0 ? c.second : i % 3 == 1 ? c.third : dark;
			float h = s[3] / 2;
			if (Math.abs(s[0]) >= 3f) {
				float x = Math.signum(s[0]) * 3.0f;
				p.round(Math.min(x, x + Math.signum(x) * 0.3f), s[1] - h, s[2] - h, Math.max(x, x + Math.signum(x) * 0.3f), s[1] + h, s[2] + h, color, 0.15f);
			} else {
				float z = Math.signum(s[2]) * 2.2f;
				p.round(s[0] - h, s[1] - h, Math.min(z, z + Math.signum(z) * 0.3f), s[0] + h, s[1] + h, Math.max(z, z + Math.signum(z) * 0.3f), color, 0.15f);
			}
		}
		// A glowing heart on the chest, beating (faster when happy).
		float beat = Mth.sin(pose.time * (0.25f + pose.happy * 0.3f));
		int heart = ARGB.scaleRGB(c.accent, 0.65f + Math.max(0, beat) * 0.35f);
		float hz = 2.2f;
		p.glow(-1.0f, 3.3f, hz, -0.2f, 3.7f, hz + 0.12f, heart);
		p.glow(0.2f, 3.3f, hz, 1.0f, 3.7f, hz + 0.12f, heart);
		p.glow(-1.0f, 2.9f, hz, 1.0f, 3.3f, hz + 0.12f, heart);
		p.glow(-0.6f, 2.5f, hz, 0.6f, 2.9f, hz + 0.12f, heart);
		p.glow(-0.2f, 2.1f, hz, 0.2f, 2.5f, hz + 0.12f, heart);
		// Long arms with chunky fists; the left one holds a flower, the right one waves.
		for (int side = -1; side <= 1; side += 2) {
			float swing = -Mth.sin(pose.walk + (side > 0 ? 0 : Mth.PI)) * 0.5f * pose.walking;
			float wave = side > 0 ? Mth.sin(pose.time * 0.6f) * 0.5f * pose.happy - pose.happy * 2.3f : 0;
			p.push();
			p.translate(side * 3.4f, 4.6f, 0);
			p.rotateX(swing + wave - (side < 0 ? 0.25f : 0));
			p.rotateZ(side * 0.12f);
			p.round(-1.0f, -3.2f, -1.0f, 1.0f, 0.6f, 1.0f, c.main, 0.45f);
			p.round(-1.3f, -5.0f, -1.3f, 1.3f, -3.0f, 1.3f, dark, 0.5f);
			if (side < 0) {
				p.box(-0.12f, -4.4f, 1.3f, 0.12f, -2.0f, 1.55f, LEAF);
				p.box(-0.7f, -2.3f, 1.25f, 0.7f, -1.7f, 1.65f, 0xFFD8302A);
				p.box(-0.35f, -2.65f, 1.25f, 0.35f, -1.35f, 1.65f, 0xFFD8302A);
				p.box(-0.22f, -2.15f, 1.65f, 0.22f, -1.85f, 1.75f, 0xFFF0D040);
			}
			p.pop();
		}
		neckWear(p, look, 5.0f, 0, 2.6f, 2.4f);
		p.push();
		p.translate(0, 5.1f, 0);
		p.rotateY(pose.headYaw);
		p.rotateX(pose.headPitch + pose.sleep * 0.45f);
		p.rotateZ(Mth.sin(pose.time * 0.04f) * 0.06f + pose.happy * 0.12f);
		p.round(-2.7f, 0, -2.3f, 2.7f, 4.3f, 2.4f, c.main, 1.1f);
		p.round(-2.5f, 2.6f, 2.0f, 2.5f, 3.3f, 2.75f, c.third, 0.3f);
		p.round(-0.65f, 0.6f, 2.3f, 0.65f, 2.4f, 3.3f, dark, 0.3f);
		Eye state = eye(pose, 41);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 1.35f;
			if (state != Eye.OPEN) {
				closedEye(p, x, 1.9f, 2.4f, 1.2f, state, COAL);
			} else {
				p.glow(x - 0.65f, 1.4f, 2.4f, x + 0.65f, 2.45f, 2.5f, ARGB.scaleRGB(c.accent, 0.6f));
				p.glow(x - 0.35f, 1.6f, 2.5f, x + 0.35f, 2.25f, 2.55f, c.accent);
				p.glow(x - 0.55f, 2.05f, 2.55f, x - 0.25f, 2.35f, 2.6f, WHITE);
			}
		}
		blush(p, -1.95f, 0.9f, 2.4f, pose);
		blush(p, 1.95f, 0.9f, 2.4f, pose);
		// Something on top: moss and a sprout on the mossy one, a pebble on the others.
		if (c == Companion.GOLEM_MOSSY) {
			p.round(-2.0f, 4.1f, -1.4f, 1.4f, 4.6f, 1.6f, c.second, 0.3f);
			p.box(0.4f, 4.6f, 0.2f, 0.6f, 5.6f, 0.4f, LEAF);
			p.round(0.5f, 5.3f, 0, 1.6f, 5.6f, 0.6f, LEAF, 0.12f);
		} else {
			p.round(-1.6f, 4.1f, -0.6f, -0.2f, 4.8f, 0.8f, c.third, 0.25f);
		}
		headWear(p, look, 0.8f, 4.3f, 0);
		p.pop();
		p.pop();
	}

	/** A round, ribbed pumpkin on vine legs, with leaf feet, little vine arms, a curly stem and a cheerful glowing face. */
	private static void pumpkin(Painter p, Companion c, Companion.Look look, Pose pose) {
		int vine = c.third;
		int vineLight = lighter(vine, 30);
		float down = pose.sit * 1.6f + pose.sleep * 1.8f;
		for (int side = -1; side <= 1; side += 2) {
			float swing = Mth.sin(pose.walk + (side > 0 ? 0 : Mth.PI)) * 0.6f * pose.walking + pose.sit * 1.3f;
			p.push();
			p.translate(side * 1.6f, 2.4f - down, 0);
			p.rotateX(swing);
			p.box(-0.35f, -2.4f, -0.35f, 0.35f, 0, 0.35f, vine);
			p.round(-0.9f, -2.4f, -0.6f, 0.9f, -2.0f, 1.5f, vineLight, 0.2f);
			p.pop();
		}
		p.push();
		p.translate(0, 2.2f - down + Math.abs(Mth.sin(pose.walk)) * 0.5f * pose.walking, 0);
		p.rotateY(pose.headYaw * 0.6f);
		// Five rounded ribs, the middle ones furthest out.
		float[] xs = {-3.2f, -1.6f, 0, 1.6f, 3.2f};
		// The three in front reach equally far, so the carved face lies flat on them.
		float[] depths = {5.0f, 6.2f, 6.2f, 6.2f, 5.0f};
		float[] heights = {4.8f, 5.5f, 5.7f, 5.5f, 4.8f};
		for (int i = 0; i < 5; i++) {
			float h = heights[i], d = depths[i] / 2;
			int color = i % 2 == 1 ? c.second : c.main;
			p.round(xs[i] - 1.0f, (5.7f - h) / 2, -d, xs[i] + 1.0f, (5.7f + h) / 2, d, color, i == 0 || i == 4 ? 0.9f : 0.4f);
		}
		p.round(-2.6f, 5.1f, -2.2f, 2.6f, 5.8f, 2.2f, ARGB.scaleRGB(c.main, 0.85f), 0.4f);
		p.box(-2.8f, 4.2f, 2.6f, -1.6f, 4.3f, 2.85f, lighter(c.main, 40));
		// Curly stem, a leaf, a tendril.
		p.box(-0.5f, 5.6f, -0.5f, 0.5f, 7.0f, 0.5f, vine);
		p.box(0.3f, 6.6f, -0.4f, 1.3f, 7.2f, 0.4f, vine);
		p.push();
		p.translate(-0.5f, 5.9f, 0.4f);
		p.rotateZ(0.25f + Mth.sin(pose.time * 0.12f) * 0.12f);
		p.round(-2.4f, -0.2f, -0.9f, 0, 0.2f, 0.9f, vineLight, 0.3f);
		p.box(-2.1f, 0.18f, -0.08f, -0.2f, 0.25f, 0.08f, vine);
		p.pop();
		p.box(1.2f, 7.1f, -0.15f, 1.5f, 7.9f, 0.15f, vineLight);
		p.box(1.5f, 7.7f, -0.15f, 2.0f, 8.0f, 0.15f, vineLight);
		p.box(1.8f, 7.3f, -0.15f, 2.1f, 7.8f, 0.15f, vineLight);
		// The carved face, glowing from inside and flickering, each opening with a dark cut edge round it: triangle eyes
		// (^^ when happy, slits asleep), a little triangle nose, a wide grin with teeth (open wider when happy).
		float flicker = 0.85f + Mth.sin(pose.time * 0.9f) * 0.08f + Mth.sin(pose.time * 2.3f) * 0.07f;
		int glow = ARGB.scaleRGB(lighter(c.accent, 30), Mth.clamp(flicker * (1 - pose.sleep * 0.55f), 0.3f, 1f));
		int cut = ARGB.scaleRGB(c.main, 0.42f);
		Eye state = eye(pose, 7);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 1.5f;
			if (state == Eye.OPEN) {
				carve(p, x - 0.85f, 2.9f, x + 0.85f, 3.35f, glow, cut);
				carve(p, x - 0.58f, 3.35f, x + 0.58f, 3.8f, glow, cut);
				carve(p, x - 0.3f, 3.8f, x + 0.3f, 4.25f, glow, cut);
			} else {
				closedEye(p, x, 3.4f, 3.08f, 1.7f, state, cut);
				closedEye(p, x, 3.4f, 3.1f, 1.5f, state, glow);
			}
		}
		carve(p, -0.38f, 2.35f, 0.38f, 2.6f, glow, cut);
		carve(p, -0.16f, 2.6f, 0.16f, 2.82f, glow, cut);
		float open = pose.happy > 0.4f ? 0.35f : 0;
		carve(p, -1.9f, 1.25f - open, 1.9f, 1.85f, glow, cut);
		carve(p, -2.4f, 1.7f, -1.9f, 2.3f, glow, cut);
		carve(p, 1.9f, 1.7f, 2.4f, 2.3f, glow, cut);
		// Teeth: two hanging from above, one standing below.
		float z1 = 3.14f;
		p.box(-1.0f, 1.5f, z1 + 0.04f, -0.45f, 1.85f, z1 + 0.08f, c.main);
		p.box(0.45f, 1.5f, z1 + 0.04f, 1.0f, 1.85f, z1 + 0.08f, c.main);
		p.box(-0.25f, 1.25f - open, z1 + 0.04f, 0.25f, 1.55f - open, z1 + 0.08f, c.main);
		// Little vine arms with leaf hands.
		for (int side = -1; side <= 1; side += 2) {
			float wave = side > 0 ? Mth.sin(pose.time * 0.7f) * 0.5f * pose.happy - pose.happy * 0.9f : 0;
			p.push();
			p.translate(side * 4.1f, 2.6f, 0.3f);
			p.rotateZ(side * (0.7f + Mth.sin(pose.walk) * 0.25f * pose.walking + wave));
			p.box(side < 0 ? -2.0f : 0, -0.18f, -0.18f, side < 0 ? 0 : 2.0f, 0.18f, 0.18f, vine);
			p.round(side < 0 ? -2.8f : 1.8f, -0.45f, -0.5f, side < 0 ? -1.8f : 2.8f, 0.45f, 0.5f, vineLight, 0.2f);
			p.pop();
		}
		headWear(p, look, -1.7f, 5.8f, 0);
		p.pop();
		neckWear(p, look, 2.4f - down, 0, 3.9f, 3.2f);
	}

	/** An opening carved into a pumpkin's front: the glow set in from behind, a dark cut edge round it. */
	private static void carve(Painter p, float x0, float y0, float x1, float y1, int glow, int cut) {
		p.box(x0 - 0.14f, y0 - 0.14f, 3.1f, x1 + 0.14f, y1 + 0.14f, 3.16f, cut);
		p.glow(x0, y0, 2.75f, x1, y1, 3.18f, glow);
	}

	/** A small snowman: rounded snowballs, a striped scarf with fringe, coal eyes with a shine, a carrot nose, twig arms. */
	private static void snowman(Painter p, Companion c, Companion.Look look, Pose pose) {
		int scarf = look.accessory() == Companion.Accessory.COLLAR ? dye(look.color()) : c.third;
		int stripe = lighter(scarf, 70);
		p.round(-3.3f, 0, -3.3f, 3.3f, 5.3f, 3.3f, c.main, 1.5f);
		p.round(-3.0f, -0.01f, -3.0f, 3.0f, 0.7f, 3.0f, c.second, 0.4f);
		p.box(-1.8f, 4.2f, 2.9f, -0.8f, 4.27f, 3.2f, lighter(c.main, 20));
		for (float y : new float[]{1.6f, 3.1f}) {
			p.round(-0.4f, y, 3.2f, 0.4f, y + 0.7f, 3.45f, COAL, 0.15f);
		}
		// Twig arms with fingers; they wave when happy and flail in a hop.
		for (int side = -1; side <= 1; side += 2) {
			float wave = side > 0 ? Mth.sin(pose.time * 0.7f) * 0.45f * pose.happy - pose.happy * 0.6f : 0;
			float flail = -Math.min(0, pose.squash) * 0.8f;
			p.push();
			p.translate(side * 3.1f, 3.8f, 0);
			p.rotateZ(side * (0.45f + wave + flail + Mth.sin(pose.time * 0.15f) * 0.05f));
			p.box(side < 0 ? -3.4f : 0, -0.22f, -0.22f, side < 0 ? 0 : 3.4f, 0.22f, 0.22f, STICK);
			p.box(side < 0 ? -2.3f : 2.0f, 0.2f, -0.18f, side < 0 ? -2.0f : 2.3f, 1.1f, 0.18f, STICK);
			p.box(side < 0 ? -3.9f : 3.3f, 0.1f, -0.16f, side < 0 ? -3.3f : 3.9f, 0.4f, 0.16f, STICK);
			p.box(side < 0 ? -3.8f : 3.4f, -0.5f, -0.16f, side < 0 ? -3.4f : 3.8f, -0.2f, 0.16f, STICK);
			p.pop();
		}
		// The scarf, striped, its end hanging and swinging, with a fringe.
		ring(p, 4.7f, 5.8f, 0, 2.85f, 2.85f, 0.65f, scarf);
		ring(p, 5.05f, 5.3f, 0, 2.9f, 2.9f, 0.1f, stripe);
		p.push();
		p.translate(1.3f, 5.0f, 2.9f);
		p.rotateX(Mth.sin(pose.walk) * 0.3f * pose.walking + 0.15f + Math.max(0, -pose.squash) * 0.5f);
		p.round(-0.7f, -3.0f, 0, 0.7f, 0, 0.55f, scarf, 0.2f);
		p.box(-0.72f, -1.6f, -0.02f, 0.72f, -1.3f, 0.57f, stripe);
		for (int i = 0; i < 3; i++) {
			p.box(-0.55f + i * 0.45f, -3.6f, 0.15f, -0.35f + i * 0.45f, -3.0f, 0.4f, scarf);
		}
		p.pop();
		p.push();
		p.translate(0, 5.4f, 0);
		p.rotateY(pose.headYaw);
		p.rotateX(pose.headPitch + pose.sleep * 0.3f);
		p.rotateZ(Mth.sin(pose.time * 0.05f) * 0.07f + pose.happy * 0.15f);
		p.round(-2.5f, 0, -2.5f, 2.5f, 4.7f, 2.5f, c.main, 1.3f);
		p.box(-1.6f, 3.9f, 1.4f, -0.7f, 3.95f, 2.0f, lighter(c.main, 20));
		face(p, 2.5f, 2.6f, 1.0f, 0.8f, 0.95f, COAL, pose, 31);
		// Carrot nose, tapering, with ridges.
		int orange = c.accent;
		p.round(-0.5f, 1.55f, 2.3f, 0.5f, 2.35f, 3.3f, orange, 0.2f);
		p.box(-0.36f, 1.7f, 3.3f, 0.36f, 2.2f, 4.1f, orange);
		p.box(-0.2f, 1.8f, 4.1f, 0.2f, 2.1f, 4.8f, lighter(orange, 25));
		p.box(-0.38f, 1.65f, 3.0f, 0.38f, 2.25f, 3.08f, ARGB.scaleRGB(orange, 0.75f));
		p.box(-0.4f, 1.7f, 3.7f, 0.4f, 2.2f, 3.76f, ARGB.scaleRGB(orange, 0.75f));
		headWear(p, look, 0, 4.7f, 0);
		p.pop();
	}

	/** A chibi bunny: a big head, one floppy ear, a twitching nose, buck teeth, whiskers; it hugs a painted egg. */
	private static void bunny(Painter p, Companion c, Companion.Look look, Pose pose) {
		int light = lighter(c.main, 35);
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 1.4f - 0.95f, 0, -1.8f, side * 1.4f + 0.95f, 0.95f, 1.5f, c.main, 0.4f);
			p.box(side * 1.4f - 0.5f, 0.3f, 1.5f, side * 1.4f + 0.5f, 0.7f, 1.58f, c.second);
		}
		p.push();
		p.translate(0, 0.7f - pose.sleep * 0.4f, 0);
		p.round(-2.4f, 0, -2.5f, 2.4f, 4.3f, 2.0f, c.main, 1.1f);
		p.round(-1.6f, 0.3f, 1.2f, 1.6f, 3.4f, 2.15f, light, 0.7f);
		p.push();
		p.translate(0, 2.2f, -2.4f);
		p.rotateY(Mth.sin(pose.time * 0.8f) * 0.3f * pose.happy + Mth.sin(pose.time * 0.07f) * 0.08f);
		p.round(-1.2f, -1.1f, -1.4f, 1.2f, 1.1f, 0.2f, WHITE, 0.6f);
		p.pop();
		// The egg, hugged: zigzag band and dots.
		if (pose.sleep < 0.5f) {
			p.push();
			p.translate(0, 0.8f, 2.2f);
			p.round(-1.35f, 0, 0, 1.35f, 3.2f, 2.1f, c.third, 0.75f);
			for (int k = 0; k < 6; k++) {
				float x = -1.35f + k * 0.45f;
				float y = 1.5f + (k % 2 == 0 ? 0 : 0.3f);
				p.box(x, y, 2.08f, x + 0.45f, y + 0.3f, 2.18f, c.accent);
			}
			p.box(-1.38f, 1.5f, 0.3f, 1.38f, 1.8f, 1.8f, c.accent);
			p.box(-0.6f, 2.5f, 2.0f, -0.2f, 2.85f, 2.12f, WHITE);
			p.box(0.4f, 0.6f, 2.0f, 0.75f, 0.95f, 2.1f, WHITE);
			p.pop();
		}
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 1.35f - 0.6f, 2.4f, 1.9f, side * 1.35f + 0.6f, 3.2f, 3.4f, c.main, 0.3f);
		}
		neckWear(p, look, 4.0f, 0.2f, 2.3f, 2.1f);
		p.push();
		p.translate(0, 4.0f, 0.4f);
		p.rotateY(pose.headYaw);
		p.rotateX(pose.headPitch + pose.sleep * 0.4f);
		p.rotateZ(Mth.sin(pose.time * 0.045f) * 0.1f + pose.happy * 0.2f);
		p.round(-2.8f, 0, -2.2f, 2.8f, 4.4f, 2.6f, c.main, 1.3f);
		p.round(-1.3f, 0.5f, 2.3f, 1.3f, 1.8f, 3.0f, light, 0.4f);
		float twitch = (pose.time % 50) < 6 ? Mth.sin(pose.time * 3f) * 0.1f : 0;
		p.round(-0.45f, 1.45f + twitch, 2.85f, 0.45f, 1.9f + twitch, 3.2f, c.second, 0.1f);
		p.box(-0.5f, 1.0f, 2.98f, -0.1f, 1.15f, 3.06f, COAL);
		p.box(0.1f, 1.0f, 2.98f, 0.5f, 1.15f, 3.06f, COAL);
		p.box(-0.08f, 1.1f, 2.98f, 0.08f, 1.45f, 3.06f, COAL);
		p.box(-0.35f, 0.55f, 2.95f, -0.04f, 1.0f, 3.05f, WHITE);
		p.box(0.04f, 0.55f, 2.95f, 0.35f, 1.0f, 3.05f, WHITE);
		for (int side = -1; side <= 1; side += 2) {
			for (int w = 0; w < 2; w++) {
				float y = 1.15f + w * 0.35f;
				p.box(side < 0 ? -2.7f : 1.3f, y, 2.55f, side < 0 ? -1.3f : 2.7f, y + 0.08f, 2.62f, 0xFFE8E4DE);
			}
		}
		face(p, 2.6f, 2.7f, 1.35f, 1.0f, 1.35f, COAL, pose, 29);
		// Ears: the left one stands, the right one flops over halfway; both flap in a hop.
		float flap = -pose.squash * 0.45f;
		for (int side = -1; side <= 1; side += 2) {
			float twitchEar = (pose.time + side * 13) % 90 < 5 ? 0.3f : 0;
			p.push();
			p.translate(side * 1.15f, 4.1f, 0.2f);
			p.rotateX(-0.15f - pose.sleep * 1.0f - twitchEar + flap + Mth.sin(pose.walk) * 0.15f * pose.walking);
			p.rotateZ(side * 0.18f);
			p.round(-0.75f, 0, -0.45f, 0.75f, 3.0f, 0.45f, c.main, 0.35f);
			p.box(-0.4f, 0.5f, 0.45f, 0.4f, 2.9f, 0.55f, c.second);
			p.translate(0, 2.9f, 0);
			p.rotateX(side > 0 ? 1.0f + Mth.sin(pose.time * 0.1f) * 0.08f + flap * 0.5f : 0.05f + flap * 0.3f);
			p.round(-0.7f, 0, -0.4f, 0.7f, 2.3f, 0.4f, c.main, 0.35f);
			p.box(-0.35f, 0.1f, 0.4f, 0.35f, 1.9f, 0.5f, c.second);
			p.pop();
		}
		headWear(p, look, 0, 4.4f, 0.2f);
		p.pop();
		p.pop();
	}

	/**
	 * A red panda: a wide fluffy head with a white mask, tear marks and white-rimmed ears, dark legs and belly, a long
	 * ringed tail. It trots (with little bounds when catching up), sits on its haunches, rears up with its paws in the
	 * air, nibbles bamboo held in both paws, washes its face, and sleeps curled up under its own tail.
	 */
	private static void redPanda(Painter p, Companion c, Companion.Look look, Pose pose) {
		int fur = c.main, dark = c.second, white = c.third, ring = c.accent;
		int nose = c == Companion.RED_PANDA_SNOW ? 0xFFE89AA8 : COAL;
		float stand = pose.action == STAND ? pose.actionBlend : 0;
		float snack = pose.action == SNACK ? pose.actionBlend : 0;
		float groom = pose.action == GROOM ? pose.actionBlend : 0;
		float lie = pose.sleep;
		float sit = Math.max(pose.sit, Math.max(snack, groom)) * (1 - stand) * (1 - lie);
		float t = pose.time;
		// Where the hips are and how far the body tips up: on four legs, sitting, rearing up, curled up lying.
		float hipY = Mth.lerp(lie, Mth.lerp(stand, Mth.lerp(sit, 3.0f, 1.3f), 2.6f), 1.4f);
		float pitch = sit * 1.0f + stand * 1.45f;
		float bob = Math.abs(Mth.sin(pose.walk)) * 0.45f * pose.walking;
		float hipZ = -1.4f;

		// Hind legs: trotting, sticking out in front when sitting, straight when rearing up, tucked when lying.
		for (int side = -1; side <= 1; side += 2) {
			float phase = pose.walk + (side > 0 ? 0 : Mth.PI);
			float a = Mth.sin(phase) * 0.7f * pose.walking;
			a = Mth.lerp(sit, a, -1.35f);
			a = Mth.lerp(stand, a, -0.05f + Mth.sin(t * 0.5f + side) * 0.04f);
			a = Mth.lerp(lie, a, -1.5f);
			p.push();
			p.translate(side * 1.5f, hipY - 0.4f + bob, hipZ);
			p.rotateX(a);
			p.round(-1.05f, -1.2f, -1.15f, 1.05f, 0.5f, 1.15f, fur, 0.5f);
			p.round(-0.75f, -2.4f, -0.8f, 0.75f, -1.0f, 0.8f, dark, 0.35f);
			p.round(-0.85f, -2.6f, -0.7f, 0.85f, -2.05f, 1.2f, dark, 0.3f);
			for (int k = -1; k <= 1; k++) {
				p.box(k * 0.45f - 0.12f, -2.6f, 1.15f, k * 0.45f + 0.12f, -2.4f, 1.35f, 0xFFD8D0C4);
			}
			p.pop();
		}

		p.push();
		p.translate(0, hipY + bob, hipZ);
		p.rotateX(-pitch);
		p.rotateZ(Mth.sin(pose.walk) * 0.05f * pose.walking);
		p.round(-2.3f, -1.5f, -1.3f, 2.3f, 1.8f, 4.9f, fur, 1.1f);
		p.round(-1.7f, -1.65f, -0.6f, 1.7f, -0.9f, 4.3f, dark, 0.4f);
		p.round(-1.6f, -1.4f, 4.1f, 1.6f, 0.6f, 5.05f, dark, 0.5f);
		p.box(-1.1f, 1.8f, 0.2f, 1.1f, 1.86f, 3.4f, lighter(fur, 22));

		// The tail: six fluffy rings, held out behind and swaying, wagging when happy, resting on the ground when it
		// sits or rears up, wrapped round it like a blanket asleep.
		p.push();
		p.translate(0, 0.3f, -1.1f);
		p.rotateX(0.3f + pitch * 0.95f - stand * 0.35f - lie * 0.25f);
		// Rust and cream rings, a dark tip.
		int pale = c == Companion.RED_PANDA_SNOW ? ring : ARGB.srgbLerp(0.5f, fur, white);
		float[] widths = {1.15f, 1.35f, 1.45f, 1.4f, 1.25f, 0.95f};
		for (int i = 0; i < widths.length; i++) {
			float w = widths[i];
			float sway = Mth.sin(t * 0.1f - i * 0.5f) * 0.1f + Mth.sin(t * 1.1f - i * 0.6f) * 0.28f * pose.happy
					+ Mth.sin(pose.walk - i * 0.4f) * 0.1f * pose.walking;
			p.rotateY(sway + lie * 0.56f);
			p.rotateX(i > 2 ? 0.1f * (1 - lie) : 0.02f);
			int color = i == widths.length - 1 ? ring : i % 2 == 0 ? fur : pale;
			p.round(-w, -w * 0.95f, -1.6f, w, w * 0.95f, 0.15f, color, w * 0.55f);
			p.translate(0, 0, -1.45f);
		}
		p.pop();

		// Front legs: trotting; in front of the chest when sitting; up to the mouth with a snack; one paw rubbing the face
		// when grooming; wide in the air when rearing up; tucked under the chin asleep.
		for (int side = -1; side <= 1; side += 2) {
			boolean right = side > 0;
			float phase = pose.walk + (side > 0 ? Mth.PI : 0);
			float a = pitch + Mth.sin(phase) * 0.7f * pose.walking;
			a = Mth.lerp(sit, a, pitch - 0.75f);
			a = Mth.lerp(snack, a, pitch - 1.35f + Mth.sin(t * 0.9f) * 0.06f);
			if (right) {
				a = Mth.lerp(groom, a, pitch - 1.8f + Mth.sin(t * 0.6f) * 0.28f);
			}
			a = Mth.lerp(stand, a, -1.35f + Mth.sin(t * 0.45f + side) * 0.15f);
			a = Mth.lerp(lie, a, -1.5f);
			float z = side * 0.06f + stand * side * 0.8f - snack * side * 0.3f - (right ? groom * 0.45f : 0);
			p.push();
			p.translate(side * 1.45f, -0.6f, 3.9f);
			p.rotateX(a);
			p.rotateZ(z);
			p.round(-0.85f, -1.4f, -0.85f, 0.85f, 0.4f, 0.85f, fur, 0.4f);
			p.round(-0.7f, -2.6f, -0.7f, 0.7f, -1.2f, 0.7f, dark, 0.3f);
			p.round(-0.8f, -2.85f, -0.6f, 0.8f, -2.3f, 1.0f, dark, 0.3f);
			for (int k = -1; k <= 1; k++) {
				p.box(k * 0.42f - 0.1f, -2.85f, 0.95f, k * 0.42f + 0.1f, -2.65f, 1.15f, 0xFFD8D0C4);
			}
			p.pop();
		}
		neckWear(p, look, -0.5f, 4.55f, 1.95f, 0.95f);

		// The head, kept level.
		p.push();
		p.translate(0, 0.9f, 4.8f);
		p.rotateX(pitch);
		p.translate(0, -lie * 1.0f, lie * 0.4f);
		p.rotateY(pose.headYaw * (1 - lie) + lie * 0.4f);
		p.rotateX(pose.headPitch * (1 - lie) + lie * 0.3f + groom * 0.2f - stand * 0.15f);
		p.rotateZ(Mth.sin(t * 0.045f) * 0.08f + pose.happy * 0.15f + groom * Mth.sin(t * 0.6f) * 0.08f);
		p.round(-3.0f, -0.6f, -1.6f, 3.0f, 3.6f, 2.4f, fur, 1.3f);
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 2.2f - 0.95f, -0.6f, 0.6f, side * 2.2f + 0.95f, 1.1f, 2.35f, white, 0.45f);
		}
		p.round(-1.35f, -0.75f, 1.9f, 1.35f, 0.9f, 3.3f, white, 0.5f);
		// Chewing moves the nose and the jaw.
		float chew = snack > 0.3f ? Math.max(0, Mth.sin(t * 1.4f)) * 0.18f : 0;
		p.round(-0.55f, 0.35f + chew * 0.5f, 3.15f, 0.55f, 0.95f + chew * 0.5f, 3.62f, nose, 0.15f);
		if (pose.happy > 0.4f || chew > 0.05f) {
			p.box(-0.4f, -0.5f - chew, 3.26f, 0.4f, -0.05f, 3.36f, 0xFF4A1E22);
			p.box(-0.25f, -0.5f - chew, 3.36f, 0.25f, -0.3f - chew, 3.39f, 0xFFE87A90);
		} else {
			p.box(-0.08f, -0.1f, 3.28f, 0.08f, 0.35f, 3.36f, COAL);
			p.box(-0.55f, -0.25f, 3.28f, -0.08f, -0.1f, 3.36f, COAL);
			p.box(0.08f, -0.25f, 3.28f, 0.55f, -0.1f, 3.36f, COAL);
		}
		Eye state = groom > 0.5f ? Eye.HAPPY : eye(pose, 13);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 1.2f;
			p.round(x - 0.65f, 2.0f, 2.2f, x + 0.65f, 2.55f, 2.45f, white, 0.2f);
			p.box(x + side * 0.15f - 0.22f, -0.3f, 2.36f, x + side * 0.15f + 0.22f, 0.75f, 2.44f, ring);
			if (state == Eye.OPEN) {
				p.box(x - 0.45f, 0.75f, 2.4f, x + 0.45f, 1.85f, 2.52f, COAL);
				p.glow(x - 0.37f, 1.4f, 2.52f, x - 0.03f, 1.75f, 2.57f, WHITE);
				p.glow(x + 0.15f, 0.85f, 2.52f, x + 0.3f, 1.0f, 2.57f, WHITE);
			} else {
				closedEye(p, x, 1.3f, 2.4f, 1.1f, state, COAL);
			}
			blush(p, side * 2.3f, 0.25f, 2.36f, pose);
		}
		// Big ears with white rims and dark insides, twitching; perked when happy, flat asleep.
		for (int side = -1; side <= 1; side += 2) {
			float twitch = (t + side * 23) % 110 < 5 ? 0.25f : 0;
			p.push();
			p.translate(side * 2.0f, 3.1f, 0.2f);
			p.rotateZ(-side * (0.3f + twitch) + side * lie * 0.4f);
			p.rotateX(-0.1f - pose.happy * 0.2f + lie * 0.35f);
			p.round(-1.1f, 0, -0.5f, 1.1f, 2.0f, 0.5f, fur, 0.4f);
			p.round(-1.2f, 1.35f, -0.55f, 1.2f, 2.3f, 0.55f, white, 0.35f);
			p.box(-0.6f, 0.3f, 0.5f, 0.6f, 1.45f, 0.6f, dark);
			if (c == Companion.RED_PANDA_AUTUMN && side < 0) {
				int leaf = 0xFFD8481E;
				p.box(-0.3f, 0.6f, 0.6f, 0.7f, 1.3f, 0.72f, leaf);
				p.box(-0.55f, 0.95f, 0.6f, 0.95f, 1.15f, 0.72f, leaf);
				p.box(0.05f, 1.3f, 0.6f, 0.35f, 1.6f, 0.72f, leaf);
				p.box(0.12f, 0.35f, 0.6f, 0.28f, 0.6f, 0.72f, 0xFF7A3A1A);
			}
			p.pop();
		}
		// The snack: a bamboo stalk held up to the mouth, getting shorter.
		if (snack > 0.05f) {
			float top = 0.1f - pose.actionTime * 1.4f;
			p.push();
			p.translate(0, -1.6f, 3.5f);
			p.rotateZ(0.35f);
			p.box(-0.22f, -1.6f, -0.22f, 0.22f, top + 1.6f, 0.22f, 0xFF6AA83A);
			p.box(-0.26f, -0.4f, -0.26f, 0.26f, -0.25f, 0.26f, 0xFF4A8A2A);
			if (pose.actionTime < 0.6f) {
				p.round(0.1f, top + 1.2f, -0.15f, 1.5f, top + 1.5f, 0.25f, LEAF, 0.12f);
				p.round(-1.3f, top + 0.9f, -0.15f, -0.1f, top + 1.15f, 0.25f, ARGB.scaleRGB(LEAF, 0.85f), 0.1f);
			}
			p.pop();
		}
		headWear(p, look, 0, 3.6f, 0.2f);
		p.pop();
		p.pop();
	}

	/**
	 * A little sea turtle: a low, smooth shell of layers with raised plates and a rim of edge plates, a pale belly plate,
	 * a big round head on a neck, long front flippers and small back ones, a tiny tail. On land it rows itself forward
	 * with both front flippers at once; in the water ({@code fly} is how much it swims) it beats them like wings and
	 * steers with the back ones. It naps with its chin on the ground, pulls in and peeks out of its shell, munches a
	 * berry, yawns, and waves a flipper when petted. The mossy one has moss, grass and a mushroom growing on it, the coral
	 * one corals and a starfish, the sandy one a golden star on every plate.
	 */
	private static void turtle(Painter p, Companion c, Companion.Look look, Pose pose) {
		int skin = c.main, shell = c.second, plate = c.third, belly = c.accent;
		int skinDark = ARGB.scaleRGB(skin, 0.78f);
		int shellDark = ARGB.scaleRGB(shell, 0.78f);
		float hide = pose.action == HIDE ? pose.actionBlend : 0;
		float snack = pose.action == SNACK ? pose.actionBlend : 0;
		float yawn = pose.action == YAWN ? pose.actionBlend : 0;
		float swim = pose.fly;
		float lie = pose.sleep;
		float t = pose.time;
		// Low to the ground like a sea turtle; resting on its belly asleep or hidden; bobbing when swimming. Each stroke of
		// the front flippers heaves it forward a little.
		float tuck = Math.max(hide, lie * 0.8f);
		float stroke = Mth.sin(pose.walk * 1.4f);
		float shellY = Mth.lerp(tuck, 0.55f, 0.1f) + Math.max(0, stroke) * 0.3f * pose.walking * (1 - swim)
				+ Mth.sin(t * 0.15f) * 0.2f * swim;

		// Front flippers: rowing together on land, beating like wings in the water, one waving when happy, laid back
		// along the shell when hiding, flat and relaxed asleep.
		for (int side = -1; side <= 1; side += 2) {
			float row = stroke * 0.55f * pose.walking * (1 - swim);
			float beat = Mth.sin(t * 0.22f) * 0.75f * swim;
			float wave = side > 0 ? (Mth.sin(t * 0.7f) * 0.35f + 0.7f) * pose.happy : 0;
			p.push();
			p.translate(side * 2.7f, shellY + 1.45f, 1.7f);
			p.rotateY(side * (-0.35f + row - tuck * 0.9f));
			// On land the tips rest on the ground; in the water they are level.
			p.rotateZ(side * (Math.max(0, -stroke) * 0.25f * pose.walking + beat + wave - lie * 0.12f - 0.3f * (1 - swim)));
			p.rotateX(Mth.cos(t * 0.22f) * 0.3f * swim);
			flipper(p, side, 1f, skin, skinDark);
			p.pop();
		}
		// Back flippers: pushing by turns, steering in the water.
		for (int side = -1; side <= 1; side += 2) {
			float push = Mth.sin(pose.walk * 1.4f + (side > 0 ? 0 : Mth.PI)) * 0.3f * pose.walking * (1 - swim);
			float steer = Mth.sin(t * 0.18f + side) * 0.3f * swim;
			p.push();
			p.translate(side * 2.3f, shellY + 1.35f, -2.7f);
			// Reaching out behind, a little to the side (tucked further back when hiding).
			p.rotateY(side * (0.8f + push + steer + tuck * 0.5f));
			p.rotateZ(side * -0.2f * (1 - swim));
			flipper(p, side, 0.65f, skin, skinDark);
			p.pop();
		}

		p.push();
		p.translate(0, shellY, 0);
		p.rotateZ(Mth.sin(pose.walk * 2) * 0.06f * pose.walking + Mth.sin(t * 0.9f) * 0.06f * pose.happy);
		p.rotateX(swim * -0.08f);
		// Belly plate, with seams.
		p.round(-2.9f, 0.6f, -3.2f, 2.9f, 1.4f, 3.2f, belly, 0.6f);
		p.box(-2.6f, 0.55f, -0.05f, 2.6f, 0.6f, 0.05f, ARGB.scaleRGB(belly, 0.8f));
		p.box(-0.05f, 0.55f, -2.9f, 0.05f, 0.6f, 2.9f, ARGB.scaleRGB(belly, 0.8f));
		// Rim of edge plates, two colours by turns.
		p.round(-3.45f, 1.2f, -3.7f, 3.45f, 2.2f, 3.7f, shellDark, 0.8f);
		for (int i = -2; i <= 2; i++) {
			int color = (i & 1) == 0 ? plate : shell;
			p.box(i * 1.3f - 0.6f, 1.35f, 3.68f, i * 1.3f + 0.6f, 2.05f, 3.8f, color);
			p.box(i * 1.3f - 0.6f, 1.35f, -3.8f, i * 1.3f + 0.6f, 2.05f, -3.68f, color);
			p.box(3.38f, 1.35f, i * 1.4f - 0.65f, 3.5f, 2.05f, i * 1.4f + 0.65f, color);
			p.box(-3.5f, 1.35f, i * 1.4f - 0.65f, -3.38f, 2.05f, i * 1.4f + 0.65f, color);
		}
		// The dome, in layers, kept low and smooth like a sea turtle's (decorations below are moved down to match).
		p.push();
		p.translate(0, 1.9f, 0);
		p.scale(1, 0.78f, 1);
		p.translate(0, -1.9f, 0);
		p.round(-3.0f, 1.9f, -3.3f, 3.0f, 4.1f, 3.3f, shell, 1.3f);
		p.round(-2.25f, 3.7f, -2.6f, 2.25f, 5.0f, 2.6f, shell, 1.0f);
		p.round(-1.2f, 4.8f, -1.45f, 1.2f, 5.45f, 1.45f, shell, 0.5f);
		// Raised plates: a row along the top, two on each side.
		p.round(-0.95f, 5.4f, -1.1f, 0.95f, 5.62f, 1.1f, plate, 0.25f);
		for (int e = -1; e <= 1; e += 2) {
			p.round(-1.0f, 4.95f, e * 1.75f - 0.65f, 1.0f, 5.12f, e * 1.75f + 0.65f, plate, 0.2f);
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int e = -1; e <= 1; e += 2) {
				float x = side * 3.0f;
				p.box(Math.min(x, x + side * 0.12f), 2.5f, e * 1.4f - 0.8f, Math.max(x, x + side * 0.12f), 3.7f, e * 1.4f + 0.8f, plate);
				float x2 = side * 2.25f;
				p.box(Math.min(x2, x2 + side * 0.12f), 4.1f, e * 1.1f - 0.6f, Math.max(x2, x2 + side * 0.12f), 4.75f, e * 1.1f + 0.6f, plate);
			}
			p.box(-0.9f, 2.4f, side * 3.3f, 0.9f, 3.6f, side * 3.3f + side * 0.12f, plate);
		}
		// A shine.
		p.box(-1.6f, 5.0f, -1.9f, -0.9f, 5.06f, -1.1f, lighter(shell, 60));
		// Decorations by variant.
		if (c == Companion.TURTLE_DESERT) {
			// A golden star on every plate, like a star tortoise.
			int gold = 0xFFF0D060;
			starTop(p, 0, 5.62f, 0, 0.8f, gold);
			for (int e = -1; e <= 1; e += 2) {
				starTop(p, 0, 5.12f, e * 1.75f, 0.55f, gold);
			}
			for (int side = -1; side <= 1; side += 2) {
				for (int e = -1; e <= 1; e += 2) {
					starSide(p, side * 3.12f, 3.1f, e * 1.4f, 0.55f, gold);
					starSide(p, side * 2.37f, 4.42f, e * 1.1f, 0.3f, gold);
				}
			}
		}
		p.pop();
		// Things growing on the lowered dome.
		p.push();
		p.translate(0, -0.75f, 0);
		if (c == Companion.TURTLE_MOSSY) {
			int moss = 0xFF5E9A3A;
			p.round(-2.0f, 4.9f, -2.2f, 0.6f, 5.25f, 0.2f, moss, 0.2f);
			p.round(0.4f, 5.3f, -0.4f, 1.5f, 5.6f, 1.2f, moss, 0.15f);
			p.round(-2.9f, 3.2f, 0.6f, -2.5f, 4.1f, 2.2f, moss, 0.15f);
			for (int k = 0; k < 4; k++) {
				float sway = Mth.sin(t * 0.12f + k) * 0.08f;
				p.box(-1.6f + k * 0.45f, 5.2f, -1.4f, -1.45f + k * 0.45f + sway, 5.9f + (k % 2) * 0.35f, -1.25f, LEAF);
			}
			p.box(0.85f, 5.6f, 0.3f, 1.05f, 6.3f, 0.5f, 0xFFE8DCC4);
			p.round(0.35f, 6.2f, -0.2f, 1.55f, 6.7f, 1.0f, 0xFFD8302A, 0.2f);
			p.box(0.6f, 6.7f, 0.2f, 0.8f, 6.75f, 0.4f, WHITE);
			p.box(1.1f, 6.5f, 0.95f, 1.3f, 6.6f, 1.05f, WHITE);
		} else if (c == Companion.TURTLE_CORAL) {
			int coral = 0xFFF06A8A;
			p.push();
			p.translate(-1.0f, 5.2f, -0.8f);
			p.rotateZ(Mth.sin(t * 0.1f) * 0.06f);
			p.box(-0.18f, 0, -0.18f, 0.18f, 1.4f, 0.18f, coral);
			p.box(-0.7f, 0.6f, -0.15f, -0.15f, 0.9f, 0.15f, coral);
			p.box(-0.75f, 0.6f, -0.15f, -0.45f, 1.3f, 0.15f, coral);
			p.box(0.15f, 0.9f, -0.15f, 0.6f, 1.15f, 0.15f, coral);
			p.box(0.4f, 0.9f, -0.15f, 0.65f, 1.6f, 0.15f, coral);
			p.pop();
			int star = 0xFFF0A040;
			p.box(0.5f, 5.45f, 0.3f, 1.9f, 5.6f, 0.65f, star);
			p.box(1.05f, 5.45f, -0.3f, 1.35f, 5.6f, 1.2f, star);
			p.box(0.7f, 5.45f, 0.7f, 1.05f, 5.6f, 1.05f, star);
			p.box(1.35f, 5.45f, 0.7f, 1.7f, 5.6f, 1.05f, star);
			p.box(1.0f, 5.58f, 0.35f, 1.4f, 5.64f, 0.6f, lighter(star, 40));
		}
		p.pop();
		// The tail, wagging.
		p.push();
		p.translate(0, 1.4f, -3.6f);
		p.rotateY(Mth.sin(t * 0.2f) * 0.25f + Mth.sin(t * 1.1f) * 0.4f * pose.happy);
		p.round(-0.4f, -0.3f, -1.1f + tuck * 0.8f, 0.4f, 0.3f, 0.1f, skin, 0.2f);
		p.pop();
		// The opening the head comes out of, dark inside.
		p.box(-1.4f, 1.55f, 3.62f, 1.4f, 2.9f, 3.7f, 0xFF16140E);
		neckWear(p, look, 1.75f, 4.0f, 1.1f, 0.7f);

		// Neck and head: out, sinking down to rest on the ground asleep, pulled in when hiding.
		float out = 1 - hide;
		if (out > 0.15f) {
			p.push();
			p.translate(0, 2.2f - lie * 1.3f, 3.4f + out * 0.6f);
			float lean = pose.headPitch * (1 - lie) + lie * 0.35f - yawn * 0.6f + snack * 0.1f;
			p.rotateX(lean * 0.4f);
			p.round(-1.0f, -0.6f, -0.6f + (1 - out) * -1.2f, 1.0f, 0.75f, 1.4f * out, skin, 0.45f);
			p.box(-0.8f, -0.65f, 0.2f, 0.8f, -0.55f, 1.2f * out, lighter(skin, 25));
			p.translate(0, 0.4f + yawn * 0.4f, 1.3f * out);
			p.rotateY(pose.headYaw * (1 - lie));
			p.rotateX(lean * 0.6f);
			p.rotateZ(Mth.sin(t * 0.045f) * 0.1f + pose.happy * 0.18f + yawn * Mth.sin(t * 0.5f) * 0.06f);
			p.round(-1.85f, -1.2f, -1.2f, 1.85f, 1.7f, 2.1f, skin, 0.95f);
			p.round(-1.4f, -1.3f, 0.6f, 1.4f, -0.6f, 2.0f, lighter(skin, 25), 0.35f);
			for (float[] s : new float[][]{{-1.0f, 1.65f, -0.2f}, {0.6f, 1.65f, 0.5f}, {1.1f, 1.65f, -0.6f}, {-0.3f, 1.65f, 0.9f}}) {
				p.box(s[0] - 0.25f, s[1], s[2] - 0.25f, s[0] + 0.25f, s[1] + 0.07f, s[2] + 0.25f, skinDark);
			}
			p.box(-0.45f, -0.05f, 2.08f, -0.25f, 0.1f, 2.14f, skinDark);
			p.box(0.25f, -0.05f, 2.08f, 0.45f, 0.1f, 2.14f, skinDark);
			// Mouth: a smile; wide open in a yawn; munching a berry; open when happy.
			float gape = yawn * Mth.clamp(Mth.sin(pose.actionTime * Mth.PI) * 1.6f, 0, 1);
			float chew = snack > 0.3f ? Math.max(0, Mth.sin(t * 1.3f)) * 0.15f : 0;
			if (gape > 0.1f) {
				p.box(-0.75f, -1.0f - gape * 0.4f, 2.06f, 0.75f, -0.35f, 2.14f, 0xFF4A1E22);
				p.box(-0.5f, -1.0f - gape * 0.4f, 2.14f, 0.5f, -0.75f - gape * 0.3f, 2.17f, 0xFFE87A90);
			} else if (pose.happy > 0.4f || chew > 0.05f) {
				p.box(-0.5f, -0.75f - chew, 2.06f, 0.5f, -0.4f, 2.14f, 0xFF4A1E22);
				p.box(-0.3f, -0.75f - chew, 2.14f, 0.3f, -0.6f - chew, 2.17f, 0xFFE87A90);
			} else {
				p.box(-0.45f, -0.6f, 2.06f, 0.45f, -0.45f, 2.14f, skinDark);
				p.box(-0.75f, -0.48f, 2.06f, -0.45f, -0.33f, 2.14f, skinDark);
				p.box(0.45f, -0.48f, 2.06f, 0.75f, -0.33f, 2.14f, skinDark);
			}
			Eye state = gape > 0.4f ? Eye.SLEEP : eye(pose, 37);
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 0.95f;
				if (state == Eye.OPEN) {
					p.box(x - 0.45f, 0.0f, 2.04f, x + 0.45f, 1.1f, 2.16f, COAL);
					p.glow(x - 0.37f, 0.65f, 2.16f, x - 0.03f, 1.0f, 2.21f, WHITE);
					p.glow(x + 0.15f, 0.1f, 2.16f, x + 0.3f, 0.25f, 2.21f, WHITE);
				} else {
					closedEye(p, x, 0.55f, 2.06f, 1.0f, state, COAL);
				}
				blush(p, side * 1.45f, -0.25f, 2.0f, pose);
			}
			// A berry held in the mouth, getting smaller.
			if (snack > 0.05f && pose.actionTime < 0.9f) {
				float r = 0.6f * (1 - pose.actionTime * 0.8f);
				p.round(-r, -1.1f - r, 2.0f, r, -1.1f + r, 2.0f + r * 2, 0xFFD8304A, r * 0.5f);
				p.box(-0.12f, -1.1f + r, 2.0f + r * 0.7f, 0.12f, -1.1f + r + 0.3f, 2.0f + r * 1.2f, LEAF);
			}
			headWear(p, look, 0, 1.7f, 0.4f);
			p.pop();
		} else if (lie < 0.5f) {
			// Hidden: just the eyes, peeking out of the dark (shut now and then).
			Eye state = eye(pose, 37);
			for (int side = -1; side <= 1; side += 2) {
				float x = side * 0.6f;
				if (state == Eye.OPEN) {
					p.box(x - 0.3f, 1.9f, 3.7f, x + 0.3f, 2.6f, 3.76f, COAL);
					p.glow(x - 0.25f, 2.3f, 3.76f, x - 0.02f, 2.53f, 3.8f, WHITE);
					p.glow(x + 0.08f, 1.98f, 3.76f, x + 0.2f, 2.1f, 3.8f, WHITE);
				}
			}
		}
		p.pop();
	}

	/**
	 * A sea turtle's flipper reaching out to {@code side} (+x or -x) and sweeping back: a short arm, then a flat blade in
	 * three rounded pieces, narrowing to the tip, with a darker edge, pale scale spots and a little claw. {@code size}
	 * scales its length and breadth (the back ones are smaller). Mirrored by hand, so the faces keep facing out.
	 */
	private static void flipper(Painter p, int side, float size, int skin, int dark) {
		int pale = lighter(skin, 30);
		float s = side * size;
		p.round(-0.2f * s, -0.45f, -0.85f * size, 1.4f * s, 0.45f, 0.85f * size, skin, 0.35f);
		p.round(1.0f * s, -0.36f, -1.45f * size, 2.7f * s, 0.36f, 1.05f * size, skin, 0.3f);
		p.round(2.4f * s, -0.3f, -1.75f * size, 3.95f * s, 0.3f, 0.6f * size, skin, 0.28f);
		p.round(3.7f * s, -0.24f, -1.8f * size, 4.9f * s, 0.24f, -0.1f * size, skin, 0.22f);
		p.box(1.1f * s, -0.32f, 0.95f * size, 2.6f * s, 0.32f, 1.12f * size, dark);
		p.box(2.5f * s, -0.26f, 0.45f * size, 3.9f * s, 0.26f, 0.65f * size, dark);
		p.box(1.5f * s, 0.36f, -0.8f * size, 1.95f * s, 0.4f, -0.35f * size, pale);
		p.box(2.1f * s, 0.36f, 0.2f * size, 2.45f * s, 0.4f, 0.55f * size, pale);
		p.box(2.9f * s, 0.3f, -1.2f * size, 3.3f * s, 0.34f, -0.8f * size, pale);
		p.box(3.2f * s, 0.3f, -0.2f * size, 3.5f * s, 0.34f, 0.1f * size, pale);
		p.box(4.1f * s, 0.24f, -1.2f * size, 4.45f * s, 0.28f, -0.85f * size, pale);
		p.box(1.6f * s, -0.1f, 1.08f * size, 1.85f * s, 0.12f, 1.32f * size, 0xFFF0EADA);
	}

	/** A little star of six rays lying on a surface facing up. */
	private static void starTop(Painter p, float x, float y, float z, float r, int color) {
		p.box(x - 0.09f, y, z - r, x + 0.09f, y + 0.06f, z + r, color);
		p.box(x - r * 0.85f, y, z - 0.09f - r * 0.45f, x - r * 0.45f, y + 0.06f, z + 0.09f - r * 0.45f, color);
		p.box(x + r * 0.45f, y, z - 0.09f + r * 0.45f, x + r * 0.85f, y + 0.06f, z + 0.09f + r * 0.45f, color);
		p.box(x - r * 0.85f, y, z - 0.09f + r * 0.45f, x - r * 0.45f, y + 0.06f, z + 0.09f + r * 0.45f, color);
		p.box(x + r * 0.45f, y, z - 0.09f - r * 0.45f, x + r * 0.85f, y + 0.06f, z + 0.09f - r * 0.45f, color);
		p.box(x - 0.2f, y + 0.02f, z - 0.2f, x + 0.2f, y + 0.08f, z + 0.2f, color);
	}

	/** A little star on a surface facing sideways (x). */
	private static void starSide(Painter p, float x, float y, float z, float r, int color) {
		float x0 = Math.min(x, x + Math.signum(x) * 0.06f), x1 = Math.max(x, x + Math.signum(x) * 0.06f);
		p.box(x0, y - r, z - 0.09f, x1, y + r, z + 0.09f, color);
		p.box(x0, y - 0.09f, z - r, x1, y + 0.09f, z + r, color);
		p.box(x0, y + r * 0.35f, z + r * 0.35f, x1, y + r * 0.65f, z + r * 0.65f, color);
		p.box(x0, y - r * 0.65f, z - r * 0.65f, x1, y - r * 0.35f, z - r * 0.35f, color);
		p.box(x0, y + r * 0.35f, z - r * 0.65f, x1, y + r * 0.65f, z - r * 0.35f, color);
		p.box(x0, y - r * 0.65f, z + r * 0.35f, x1, y - r * 0.35f, z + r * 0.65f, color);
	}

	/**
	 * A kitten in a cardboard box. The box (tape, "this side up" arrows, a heart sticker, four flaps that flutter with
	 * every hop) hops along after you; the kitten peeks over the edge with its paws (pink toe beans) on the rim and its
	 * tail curling over the back. It ducks down and slowly peeks out again, hops out to stretch, sits beside the box
	 * washing a paw and hops back in, licks a paw in the box, yawns, purrs and rubs its head when petted, sleeps curled up
	 * with just its ears showing under half-closed flaps, and sits nervously ears-flat when the box floats.
	 */
	private static void kitten(Painter p, Companion c, Companion.Look look, Pose pose) {
		float t = pose.time;
		float lie = pose.sleep;
		float swim = pose.fly;
		float at = pose.actionTime;
		// The box.
		int card = 0xFFC8A06A, cardDark = 0xFFA0784A, inside = 0xFF4A3420, tape = 0xFFE8D49A;
		float bw = 3.9f, bh = 5.5f;
		p.push();
		p.rotateZ(Mth.sin(t * 0.15f) * 0.07f * swim);
		p.rotateX(Mth.cos(t * 0.12f) * 0.05f * swim);
		p.box(-bw, 0, bw - 0.5f, bw, bh, bw, card);
		p.box(-bw, 0, -bw, bw, bh, -bw + 0.5f, card);
		p.box(-bw, 0, -bw + 0.5f, -bw + 0.5f, bh, bw - 0.5f, card);
		p.box(bw - 0.5f, 0, -bw + 0.5f, bw, bh, bw - 0.5f, card);
		p.box(-bw + 0.5f, 0, -bw + 0.5f, bw - 0.5f, 0.5f, bw - 0.5f, inside);
		p.box(-bw + 0.5f, 0.5f, bw - 0.62f, bw - 0.5f, bh - 0.05f, bw - 0.5f, inside);
		p.box(-bw + 0.5f, 0.5f, -bw + 0.5f, bw - 0.5f, bh - 0.05f, -bw + 0.62f, inside);
		p.box(-bw + 0.5f, 0.5f, -bw + 0.5f, -bw + 0.62f, bh - 0.05f, bw - 0.5f, inside);
		p.box(bw - 0.62f, 0.5f, -bw + 0.5f, bw - 0.5f, bh - 0.05f, bw - 0.5f, inside);
		p.box(-bw - 0.03f, 0, -bw - 0.03f, bw + 0.03f, 0.35f, bw + 0.03f, cardDark);
		for (int k = 0; k < 4; k++) {
			float cx = k % 2 == 0 ? -bw : bw, cz = k < 2 ? -bw : bw;
			p.box(cx - 0.06f, 0, cz - 0.06f, cx + 0.06f, bh, cz + 0.06f, cardDark);
		}
		p.box(-0.6f, bh - 1.7f, bw, 0.6f, bh, bw + 0.06f, tape);
		p.box(-0.6f, bh - 1.7f, -bw - 0.06f, 0.6f, bh, -bw, tape);
		// "This side up" arrows and a heart sticker on the front.
		for (float x : new float[]{-2.4f, -1.3f}) {
			p.box(x - 0.12f, 0.55f, bw, x + 0.12f, 1.75f, bw + 0.05f, cardDark);
			p.box(x - 0.4f, 1.4f, bw, x + 0.4f, 1.65f, bw + 0.05f, cardDark);
			p.box(x - 0.25f, 1.65f, bw, x + 0.25f, 1.9f, bw + 0.05f, cardDark);
		}
		int heart = 0xFFE8506A;
		p.box(1.4f, 1.55f, bw, 1.9f, 1.9f, bw + 0.06f, heart);
		p.box(2.1f, 1.55f, bw, 2.6f, 1.9f, bw + 0.06f, heart);
		p.box(1.4f, 1.15f, bw, 2.6f, 1.55f, bw + 0.06f, heart);
		p.box(1.65f, 0.8f, bw, 2.35f, 1.15f, bw + 0.06f, heart);
		p.box(1.85f, 0.5f, bw, 2.15f, 0.8f, bw + 0.06f, heart);
		p.box(1.55f, 1.65f, bw + 0.06f, 1.75f, 1.8f, bw + 0.08f, WHITE);
		// Four flaps hinged at the top edges: open outward, fluttering with each hop, half closed over a sleeping kitten.
		for (int k = 0; k < 4; k++) {
			boolean side = k % 2 == 1;
			float len = side ? 3.0f : 3.6f;
			// The front one folded down outside, so the kitten can lean over the edge; the others up and out.
			float open = (k == 0 ? 2.85f : side ? 2.2f : 2.0f) - pose.squash * (k == 0 ? 0.15f : 0.35f) + Mth.sin(t * 0.08f + k) * 0.03f;
			float angle = Mth.lerp(lie, open, side ? 0.8f : 0.6f);
			p.push();
			p.rotateY(k * Mth.HALF_PI);
			p.translate(0, bh, bw);
			p.rotateX(angle);
			p.box(-bw + 0.15f, -0.3f, -len, bw - 0.15f, 0, 0, card);
			p.box(-bw + 0.15f, -0.32f, -len, bw - 0.15f, 0.02f, -len + 0.3f, cardDark);
			p.pop();
		}
		p.pop();

		// Where the kitten is: up in the box with its head and paws over the rim; crouched down (squashed small, its ears
		// just showing) when ducking or asleep, peeking with its eyes over the rim in between; or out beside the box.
		float crouch = 0;
		float outside = 0, jump = 0, stretch = 0, groomOut = 0;
		if (pose.action == HIDE) {
			crouch = at < 0.2f ? smooth(at / 0.2f) : at < 0.55f ? 1 : at < 0.8f ? Mth.lerp(smooth((at - 0.55f) / 0.25f), 1, 0.45f)
					: at < 0.9f ? 0.45f : Mth.lerp(smooth((at - 0.9f) / 0.1f), 0.45f, 0);
		} else if (pose.action == OUT) {
			float j1 = Mth.clamp(at / 0.12f, 0, 1), j2 = Mth.clamp((at - 0.85f) / 0.15f, 0, 1);
			outside = smooth(j1) * (1 - smooth(j2));
			jump = Mth.sin(j1 * Mth.PI) + Mth.sin(j2 * Mth.PI);
			stretch = at > 0.14f && at < 0.45f ? Mth.sin((at - 0.14f) / 0.31f * Mth.PI) : 0;
			groomOut = at > 0.5f && at < 0.82f ? Mth.sin((at - 0.5f) / 0.32f * Mth.PI) : 0;
		}
		crouch = Math.max(crouch, lie * 0.9f);
		float inBox = 1 - outside;
		float base = Mth.lerp(crouch, 2.1f, 0.5f) + pose.happy * 0.5f;
		p.push();
		p.translate(outside * 5.8f, Mth.lerp(outside, base, 0) + jump * 3.2f, Mth.lerp(outside, -0.3f, 1.4f));
		p.rotateY(outside * 0.5f);
		p.rotateX(stretch * 0.5f);
		p.scale(1, 1 - 0.45f * crouch * inBox, 1);
		kittenBody(p, c, look, pose, inBox, stretch, groomOut, lie, swim, crouch);
		p.pop();
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}

	/** The kitten itself, sitting, its feet at the origin, facing +z. */
	private static void kittenBody(Painter p, Companion c, Companion.Look look, Pose pose, float inBox, float stretch,
			float groomOut, float lie, float swim, float crouch) {
		float t = pose.time;
		boolean calico = c == Companion.KITTEN_CALICO;
		int fur = c.main;
		int light = calico ? WHITE : c.third;
		int stripe = c.second;
		int pink = 0xFFF4A0B0;
		float groom = pose.action == GROOM ? pose.actionBlend : 0;
		float yawn = pose.action == YAWN ? pose.actionBlend : 0;
		// Haunches, hind feet, chest with a pale bib.
		p.round(-1.9f, 0, -2.2f, 1.9f, 2.4f, 1.0f, fur, 0.9f);
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 1.25f - 0.6f, 0, 0.2f, side * 1.25f + 0.6f, 0.7f, 1.7f, light, 0.3f);
		}
		p.round(-1.6f, 1.4f, -0.9f, 1.6f, 4.4f, 1.3f, fur, 0.8f);
		p.round(-1.0f, 1.8f, 1.1f, 1.0f, 4.0f, 1.45f, light, 0.4f);
		if (c == Companion.KITTEN_GINGER) {
			for (int i = 0; i < 3; i++) {
				p.box(-1.65f, 1.6f + i * 0.9f, -0.5f, -1.58f, 1.9f + i * 0.9f, 0.6f, stripe);
				p.box(1.58f, 1.6f + i * 0.9f, -0.5f, 1.65f, 1.9f + i * 0.9f, 0.6f, stripe);
			}
		}
		// The tail: up over the back of the box, swishing; wrapped round the feet when sitting outside.
		p.push();
		p.translate(0, 0.9f, -2.1f);
		p.rotateX(Mth.lerp(inBox, 1.3f, -0.95f) - stretch * 1.4f);
		for (int i = 0; i < 5; i++) {
			float swish = Mth.sin(t * 0.12f - i * 0.45f) * 0.22f + Mth.sin(t * 0.9f - i * 0.5f) * 0.3f * pose.happy;
			p.rotateY(swish + (1 - inBox) * 0.35f);
			p.rotateX(Mth.lerp(inBox, 0.05f, 0.22f));
			int color = i == 4 ? (c == Companion.KITTEN_GINGER ? stripe : calico ? c.third : fur) : (c == Companion.KITTEN_GINGER && i % 2 == 1 ? stripe : fur);
			p.round(-0.45f, -0.45f, -1.1f, 0.45f, 0.45f, 0.1f, color, 0.2f);
			p.translate(0, 0, -1.0f);
		}
		p.pop();
		// Front legs: paws on the rim in the box, on the ground outside, stretched far ahead, the right one up to the mouth
		// for a lick.
		for (int side = -1; side <= 1; side += 2) {
			boolean right = side > 0;
			// Paws down inside once it ducks.
			float a = Mth.lerp(inBox, -0.12f, Mth.lerp(Math.min(1, crouch * 1.6f), -1.62f, -0.1f));
			a = Mth.lerp(stretch, a, -1.25f);
			if (right) {
				float lick = Math.max(groom, groomOut);
				a = Mth.lerp(lick, a, -2.55f + Mth.sin(t * 0.7f) * 0.2f);
			}
			p.push();
			p.translate(side * 0.95f, 3.6f, 0.9f);
			p.rotateX(a);
			p.rotateZ(right ? -Math.max(groom, groomOut) * 0.35f : 0);
			p.round(-0.55f, -3.4f, -0.55f, 0.55f, 0.2f, 0.55f, fur, 0.3f);
			p.round(-0.68f, -3.75f, -0.6f, 0.68f, -3.1f, 0.75f, light, 0.3f);
			p.box(-0.35f, -3.78f, -0.15f, 0.35f, -3.74f, 0.35f, pink);
			for (int k = -1; k <= 1; k++) {
				p.box(k * 0.3f - 0.1f, -3.78f, 0.45f, k * 0.3f + 0.1f, -3.74f, 0.65f, pink);
			}
			p.pop();
		}
		neckWear(p, look, 4.2f, 0.2f, 1.7f, 1.4f);
		// The head.
		p.push();
		p.translate(0, 4.5f, 0.4f);
		p.rotateY(pose.headYaw * (1 - lie));
		p.rotateX(pose.headPitch * (1 - lie) - yawn * 0.45f + groom * 0.2f + groomOut * 0.2f - stretch * 0.4f + lie * 0.2f);
		p.rotateZ(Mth.sin(t * 0.04f) * 0.1f + pose.happy * (0.15f + Mth.sin(t * 0.5f) * 0.12f));
		p.round(-2.6f, -0.8f, -2.0f, 2.6f, 2.9f, 2.0f, fur, 1.1f);
		if (calico) {
			p.round(-2.65f, 0.9f, -2.05f, -0.3f, 2.95f, 2.05f, c.second, 1.0f);
			p.round(0.5f, 1.4f, -2.05f, 2.65f, 2.95f, 0.6f, c.third, 0.9f);
		}
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 2.55f - 0.45f, -0.85f, -0.6f, side * 2.55f + 0.45f, 0.5f, 1.4f, fur, 0.3f);
		}
		p.round(-1.0f, -0.85f, 1.7f, 1.0f, 0.15f, 2.3f, light, 0.35f);
		if (c == Companion.KITTEN_GINGER) {
			p.box(-0.15f, 1.9f, 1.88f, 0.15f, 2.8f, 2.02f, stripe);
			for (int side = -1; side <= 1; side += 2) {
				p.box(side * 0.75f - 0.15f, 2.0f, 1.82f, side * 0.75f + 0.15f, 2.6f, 1.96f, stripe);
				p.box(side * 2.58f - 0.06f, 0.4f, -0.4f, side * 2.58f + 0.06f, 0.65f, 0.8f, stripe);
				p.box(side * 2.58f - 0.06f, 1.0f, -0.4f, side * 2.58f + 0.06f, 1.25f, 0.6f, stripe);
			}
		}
		p.box(-0.3f, 0.0f, 2.26f, 0.3f, 0.3f, 2.36f, pink);
		p.box(-0.14f, -0.18f, 2.26f, 0.14f, 0.0f, 2.34f, pink);
		float gape = yawn * Mth.clamp(Mth.sin(pose.actionTime * Mth.PI) * 1.6f, 0, 1);
		int line = c == Companion.KITTEN_BLACK ? 0xFF8A8A92 : COAL;
		if (gape > 0.1f || pose.happy > 0.4f) {
			float o = 0.3f + gape * 0.45f;
			p.box(-0.45f, -0.45f - o, 2.28f, 0.45f, -0.3f, 2.36f, 0xFF4A1E22);
			p.box(-0.3f, -0.45f - o, 2.36f, 0.3f, -0.3f - o * 0.6f, 2.39f, 0xFFE87A90);
			p.box(-0.4f, -0.5f, 2.36f, -0.25f, -0.3f, 2.4f, WHITE);
			p.box(0.25f, -0.5f, 2.36f, 0.4f, -0.3f, 2.4f, WHITE);
		} else {
			p.box(-0.06f, -0.45f, 2.28f, 0.06f, -0.18f, 2.34f, line);
			p.box(-0.45f, -0.56f, 2.28f, -0.06f, -0.43f, 2.34f, line);
			p.box(0.06f, -0.56f, 2.28f, 0.45f, -0.43f, 2.34f, line);
		}
		int whisker = c == Companion.KITTEN_BLACK || c == Companion.KITTEN_GINGER ? 0xFFF4F0E8 : 0xFF8A8A8A;
		for (int side = -1; side <= 1; side += 2) {
			for (int w = 0; w < 2; w++) {
				float y = -0.35f + w * 0.3f;
				p.box(side < 0 ? -3.3f : 1.0f, y, 2.15f, side < 0 ? -1.0f : 3.3f, y + 0.07f, 2.22f, whisker);
			}
		}
		// Cat's eyes: coloured, a slit of a pupil (rounder in the dark of the box), a shine.
		Eye state = groom > 0.5f || groomOut > 0.5f ? Eye.HAPPY : gape > 0.4f ? Eye.SLEEP : eye(pose, 23);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 1.15f, y = 0.95f;
			if (state == Eye.OPEN) {
				p.box(x - 0.6f, y - 0.7f, 2.0f, x + 0.6f, y + 0.7f, 2.1f, 0xFF1A1A1E);
				p.box(x - 0.5f, y - 0.6f, 2.1f, x + 0.5f, y + 0.6f, 2.14f, c.accent);
				float pupil = 0.18f + 0.12f * pose.happy;
				p.box(x - pupil, y - 0.5f, 2.14f, x + pupil, y + 0.5f, 2.18f, 0xFF101014);
				p.glow(x - 0.42f, y + 0.18f, 2.18f, x - 0.12f, y + 0.5f, 2.22f, WHITE);
				p.glow(x + 0.18f, y - 0.42f, 2.18f, x + 0.32f, y - 0.28f, 2.22f, WHITE);
			} else {
				closedEye(p, x, y, 2.02f, 1.2f, state, line);
			}
			blush(p, side * 1.95f, -0.05f, 2.0f, pose);
		}
		// Ears: twitching, flat when the box floats, laid back asleep.
		for (int side = -1; side <= 1; side += 2) {
			float twitch = (t + side * 17) % 120 < 5 ? 0.3f : 0;
			p.push();
			p.translate(side * 1.5f, 2.6f, -0.2f);
			p.rotateZ(-side * (0.15f + twitch) + side * swim * 0.9f + side * lie * 0.3f);
			p.rotateX(-0.1f - pose.squash * 0.25f);
			int tip = c == Companion.KITTEN_GINGER ? stripe : calico && side > 0 ? c.third : fur;
			p.round(-0.95f, 0, -0.45f, 0.95f, 1.1f, 0.45f, calico && side < 0 ? c.second : fur, 0.3f);
			p.box(-0.62f, 1.1f, -0.38f, 0.62f, 1.75f, 0.38f, calico && side < 0 ? c.second : fur);
			p.box(-0.3f, 1.75f, -0.3f, 0.3f, 2.25f, 0.3f, tip);
			p.box(-0.55f, 0.2f, 0.45f, 0.55f, 1.05f, 0.5f, pink);
			p.box(-0.32f, 1.05f, 0.38f, 0.32f, 1.65f, 0.43f, pink);
			p.pop();
		}
		headWear(p, look, 0, 2.9f, 0);
		p.pop();
	}

	/**
	 * A shadow wolf pup: sleek dark fur with glowing marks, slanted glowing eyes under stern brows, tall pointed ears, a
	 * mane and a bushy tail that flicker into smoke, smoke curling off its paws, a fang showing. It trots on four legs,
	 * sits, sleeps curled up with its tail round it, howls with its nose up, bows down to play, and now and then vanishes
	 * into a swirl of smoke to step out somewhere else. In the dark it fades into the shadows: darker fur, more smoke, a
	 * pool of shadow under it, the marks and eyes glowing brighter, the eyes leaving trails.
	 */
	private static void shadowWolf(Painter p, Companion c, Companion.Look look, Pose pose) {
		float shade = Mth.clamp(pose.shade, 0, 1);
		int fur = ARGB.srgbLerp(shade * 0.6f, c.main, c.second);
		int dark = c.second;
		int smoke = c.third;
		int chest = ARGB.srgbLerp(0.45f, fur, smoke);
		int eye = c.accent;
		// The marks: faint by day, glowing in the dark.
		int mark = ARGB.srgbLerp(0.3f + 0.7f * shade, ARGB.srgbLerp(0.5f, smoke, fur), eye);
		float t = pose.time;
		float howl = pose.action == HOWL ? pose.actionBlend : 0;
		float bow = pose.action == PLAYBOW ? pose.actionBlend : 0;
		float yawn = pose.action == YAWN ? pose.actionBlend : 0;
		// Vanishing: shrinking into a swirl of smoke up to the middle (where it moves), growing back out of it after.
		float gone = pose.action == VANISH ? Mth.clamp(1.6f - Math.abs(pose.actionTime - 0.5f) * 4.5f, 0, 1) : 0;
		float swirl = pose.action == VANISH ? Mth.clamp(1.15f - Math.abs(pose.actionTime - 0.5f) * 2.6f, 0, 1) : 0;
		float lie = pose.sleep;
		float sit = Math.max(pose.sit, Math.max(howl, yawn)) * (1 - bow) * (1 - lie);

		if (swirl > 0) {
			vortex(p, swirl, t, smoke, eye);
		}
		if (gone > 0.98f) {
			return;
		}
		// A pool of shadow under it in the dark.
		if (shade > 0.35f) {
			// A rough ellipse of three overlapping slabs.
			float r = (shade - 0.35f) * 5f * (1 - gone);
			float zc = -0.4f, l = r * 1.7f;
			p.box(-r, 0.06f, zc - l * 0.55f, r, 0.1f, zc + l * 0.55f, dark);
			p.box(-r * 0.8f, 0.06f, zc - l * 0.82f, r * 0.8f, 0.1f, zc + l * 0.82f, dark);
			p.box(-r * 0.5f, 0.06f, zc - l, r * 0.5f, 0.1f, zc + l, dark);
		}
		p.push();
		p.translate(0, 3, 0);
		p.scale(1 - gone);
		p.translate(0, -3, 0);

		// Smoke curling off its paws, more of it in the dark.
		int puffs = 2 + Math.round(shade * 4);
		for (int side = -1; side <= 1; side += 2) {
			wisps(p, side * 1.3f, 0.3f, -2.0f, 0.35f, puffs, t, side * 0.3f + 0.1f, smoke, 0.5f + 0.3f * shade);
			wisps(p, side * 1.3f, 0.3f, 1.4f, 0.35f, puffs, t, side * 0.2f + 0.6f, smoke, 0.5f + 0.3f * shade);
		}

		// Where the hips are and how the body tips: on four legs, sitting, bowing down (rump up), lying.
		float hipY = Mth.lerp(lie, Mth.lerp(sit, 4.0f, 2.4f), 1.5f) + bow * 0.2f;
		float pitch = sit * 0.55f - bow * 0.5f;
		float bob = Math.abs(Mth.sin(pose.walk)) * 0.4f * pose.walking;
		float hipZ = -2.0f;

		// Hind legs: thigh and shin; trotting (the paw lifting on the swing forward), folded when sitting, tucked lying.
		for (int side = -1; side <= 1; side += 2) {
			float phase = pose.walk + (side > 0 ? 0 : Mth.PI);
			float a = Mth.sin(phase) * 0.6f * pose.walking;
			float b = Math.max(0, Mth.cos(phase)) * 0.6f * pose.walking;
			a = Mth.lerp(sit, a, -1.45f);
			b = Mth.lerp(sit, b, 1.35f);
			a = Mth.lerp(lie, a, -1.45f);
			b = Mth.lerp(lie, b, 2.6f);
			a = Mth.lerp(bow, a, 0.15f);
			b = Mth.lerp(bow, b, 0);
			p.push();
			p.translate(side * 1.3f, hipY - 0.3f + bob, hipZ);
			p.rotateX(a);
			p.round(-0.85f, -1.9f, -1.0f, 0.85f, 0.6f, 1.0f, fur, 0.45f);
			p.glow(side * 0.86f, -1.2f, -0.5f, side * 0.9f, -0.2f, -0.2f, mark);
			p.translate(0, -1.7f, 0);
			p.rotateX(b);
			leg(p, dark, mark, side);
			p.pop();
		}

		p.push();
		p.translate(0, hipY + bob, hipZ);
		p.rotateX(-pitch);
		p.rotateZ(Mth.sin(pose.walk) * 0.04f * pose.walking);
		p.round(-2.0f, -1.4f, -1.6f, 2.0f, 1.6f, 4.4f, fur, 1.0f);
		p.round(-1.4f, -1.55f, 0.2f, 1.4f, -0.7f, 3.8f, chest, 0.4f);
		// Glowing chevrons down the back and a slash on each flank.
		for (int i = 0; i < 3; i++) {
			float z = 0.2f + i * 1.15f;
			p.glow(-1.0f, 1.6f, z, -0.25f, 1.66f, z + 0.3f, mark);
			p.glow(0.25f, 1.6f, z, 1.0f, 1.66f, z + 0.3f, mark);
			p.glow(-0.3f, 1.6f, z + 0.25f, 0.3f, 1.66f, z + 0.6f, mark);
		}
		for (int side = -1; side <= 1; side += 2) {
			float x0 = side * 2.0f, x1 = side * 2.05f;
			p.glow(x0, -0.4f, 0.6f, x1, -0.1f, 1.6f, mark);
			p.glow(x0, -0.1f, 1.3f, x1, 0.2f, 2.3f, mark);
			p.glow(x0, 0.2f, 2.0f, x1, 0.5f, 2.9f, mark);
		}

		// The tail: bushy, held up behind, swishing; wagging when happy or bowing; its end flickering into smoke; curled
		// round it asleep, resting on the ground when it sits.
		p.push();
		p.translate(0, 0.9f, -1.3f);
		float wag = Mth.sin(t * 1.3f) * 0.5f * Math.max(pose.happy, bow);
		p.rotateX(0.55f - sit * 0.5f - lie * 0.55f + bow * 0.2f);
		float[] widths = {0.75f, 1.0f, 1.15f, 1.1f, 0.85f};
		for (int i = 0; i < widths.length; i++) {
			float w = widths[i];
			float sway = Mth.sin(t * 0.08f - i * 0.6f) * 0.08f + Mth.sin(pose.walk - i * 0.5f) * 0.08f * pose.walking;
			p.rotateY(sway + wag * (i == 0 ? 1 : 0.3f) + lie * 0.5f);
			p.rotateX(i > 1 ? 0.06f * (1 - lie) : 0);
			float flick = i >= 3 ? Mth.sin(t * 0.5f + i * 1.3f) * 0.15f : 0;
			int color = i < 3 ? (i == 2 ? dark : fur) : smoke;
			p.round(-w - flick, -w * 0.9f, -1.5f, w + flick, w * 0.9f + flick, 0.15f, color, w * 0.5f);
			if (i == 2) {
				p.glow(-w - 0.05f, -w * 0.4f, -0.9f, w + 0.05f, w * 0.4f, -0.6f, mark);
				p.glow(-w * 0.4f, -w * 0.9f - 0.05f, -0.9f, w * 0.4f, w * 0.9f + 0.05f, -0.6f, mark);
			}
			p.translate(0, 0, -1.3f);
		}
		wisps(p, 0, 0.2f, -0.4f, 0.5f, 3 + Math.round(shade * 3), t, 0.45f, smoke, 0.7f);
		p.pop();

		// Front legs: trotting; straight when sitting; elbows down, paws out front when bowing; stretched out lying.
		for (int side = -1; side <= 1; side += 2) {
			float phase = pose.walk + (side > 0 ? Mth.PI : 0);
			float a = Mth.sin(phase) * 0.6f * pose.walking;
			float b = Math.max(0, -Mth.cos(phase)) * 0.7f * pose.walking;
			a = Mth.lerp(lie, a, -1.5f);
			b = Mth.lerp(lie, b, 0);
			a = Mth.lerp(bow, a, 0.05f);
			b = Mth.lerp(bow, b, -1.5f);
			p.push();
			p.translate(side * 1.3f, -0.3f - lie * 0.6f, 3.4f);
			p.rotateX(pitch + a);
			p.round(-0.75f, -1.9f, -0.8f, 0.75f, 0.5f, 0.8f, fur, 0.4f);
			p.translate(0, -1.7f, 0);
			p.rotateX(b);
			leg(p, dark, mark, side);
			p.pop();
		}
		neckWear(p, look, 0.1f, 4.2f, 1.8f, 0.95f);
		// A pale chest tuft and a mane of tufts flickering at the tips like flames.
		p.round(-1.15f, -1.2f, 4.0f, 1.15f, 0.7f, 4.85f, chest, 0.4f);
		p.box(-0.5f, -1.6f, 4.3f, 0.5f, -1.1f, 4.75f, chest);
		for (int i = 0; i < 5; i++) {
			float flick = Mth.sin(t * 0.3f + i * 1.7f) * 0.25f;
			p.push();
			p.translate((i - 2) * 0.8f, 1.2f - Math.abs(i - 2) * 0.4f, 3.3f);
			p.rotateX(-0.95f + flick * 0.3f);
			p.rotateZ((i - 2) * 0.3f);
			p.round(-0.6f, 0, -0.5f, 0.6f, 1.7f + flick, 0.5f, i % 2 == 0 ? fur : dark, 0.3f);
			p.box(-0.25f, 1.5f + flick, -0.2f, 0.25f, 2.1f + flick * 2, 0.2f, smoke);
			p.pop();
		}

		// The head, kept level: nose up to howl, lowered onto its paws asleep.
		p.push();
		p.translate(0, 1.6f, 4.7f);
		p.rotateX(pitch);
		p.translate(0, -lie * 1.9f, lie * 0.4f);
		p.rotateY(pose.headYaw * (1 - lie) * (1 - howl) + lie * 0.3f);
		p.rotateX(pose.headPitch * (1 - lie) * (1 - howl) - howl * 0.95f + bow * 0.15f + lie * 0.2f);
		p.rotateZ(Mth.sin(t * 0.03f) * 0.06f + pose.happy * 0.12f);
		p.round(-2.2f, -1.0f, -1.6f, 2.2f, 2.4f, 2.0f, fur, 1.1f);
		// Cheek ruffs ending in points.
		for (int side = -1; side <= 1; side += 2) {
			p.round(side * 2.0f - 0.6f, -1.1f, -0.9f, side * 2.0f + 0.6f, 0.9f, 1.2f, fur, 0.4f);
			p.box(side * 2.35f, -1.5f, -0.4f, side * 2.75f, -0.6f, 0.5f, dark);
			p.box(side * 2.5f, -1.9f, -0.2f, side * 2.8f, -1.5f, 0.2f, dark);
		}
		// The snout with a glowing line down the bridge and a mark on the brow.
		p.round(-1.1f, -0.8f, 1.6f, 1.1f, 0.7f, 4.0f, fur, 0.45f);
		p.box(-0.75f, 0.62f, 2.1f, 0.75f, 0.74f, 3.7f, dark);
		p.glow(-0.12f, 0.74f, 2.0f, 0.12f, 0.8f, 3.3f, mark);
		p.glow(-0.4f, 1.75f, 1.98f, 0.4f, 2.05f, 2.06f, mark);
		p.glow(-0.15f, 2.05f, 1.98f, 0.15f, 2.3f, 2.06f, mark);
		p.round(-0.5f, 0.1f, 3.85f, 0.5f, 0.75f, 4.3f, 0xFF0C0C10, 0.15f);
		// The jaw: open to howl, wide to yawn, panting with its tongue out when happy.
		float open = howl * (0.5f + Mth.sin(t * 0.25f) * 0.08f) + yawn * Mth.sin(pose.actionTime * Mth.PI) * 0.8f
				+ pose.happy * (0.22f + Mth.sin(t * 0.8f) * 0.06f) * (1 - howl);
		p.push();
		p.translate(0, -0.6f, 1.7f);
		p.rotateX(open);
		p.round(-0.95f, -0.75f, 0, 0.95f, 0.1f, 2.1f, fur, 0.3f);
		if (open > 0.05f) {
			p.box(-0.7f, 0.1f, 0.2f, 0.7f, 0.15f, 1.9f, 0xFF3A1018);
			float tongue = pose.happy > 0.3f && howl < 0.3f ? 0.9f : 0;
			p.round(-0.45f, 0.1f, 0.5f, 0.45f, 0.3f, 1.9f + tongue, 0xFFC0505E, 0.1f);
			for (int side = -1; side <= 1; side += 2) {
				p.box(side * 0.65f - 0.11f, 0.1f, 1.7f, side * 0.65f + 0.11f, 0.45f, 1.9f, 0xFFF0EEE8);
			}
		}
		p.pop();
		if (open > 0.05f) {
			p.box(-0.8f, -0.85f, 1.9f, 0.8f, -0.75f, 3.8f, 0xFF3A1018);
		}
		for (int side = -1; side <= 1; side += 2) {
			if (side > 0 || open > 0.05f) {
				// A fang peeking over the lip (both when the mouth is open).
				p.box(side * 0.5f - 0.11f, -1.05f, 3.55f, side * 0.5f + 0.11f, -0.6f, 3.8f, 0xFFF0EEE8);
			}
		}
		// Eyes: slanted and glowing, a dark slit, stern brows; trails of light behind them in the dark.
		Eye state = howl > 0.5f || yawn > 0.4f ? Eye.SLEEP : eye(pose, 29);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 1.2f;
			if (state == Eye.OPEN) {
				p.box(x - 0.8f, 0.7f, 1.98f, x + 0.8f, 1.66f, 2.03f, dark);
				p.glow(x - 0.62f, 0.86f, 2.03f, x + 0.62f, 1.3f, 2.09f, eye);
				float outer = side > 0 ? x - 0.05f : x - 0.62f;
				p.glow(outer, 1.3f, 2.03f, outer + 0.67f, 1.52f, 2.09f, eye);
				p.box(x + side * 0.05f - 0.07f, 0.95f, 2.09f, x + side * 0.05f + 0.07f, 1.4f, 2.12f, dark);
				p.glow(x - side * 0.35f - 0.07f, 1.12f, 2.09f, x - side * 0.35f + 0.07f, 1.24f, 2.12f, WHITE);
				if (shade > 0.3f) {
					float trail = (shade - 0.3f) * 3.2f;
					float flicker = Mth.sin(t * 0.4f + side) * 0.2f;
					p.glow(side * 2.0f, 1.12f, 1.8f - trail - flicker, side * 2.08f, 1.32f, 1.95f, ARGB.scaleRGB(eye, 0.8f));
				}
			} else {
				closedEye(p, x, 1.15f, 2.0f, 1.0f, state, mark);
			}
			p.box(x - side * 0.8f, 1.48f, 1.98f, x - side * 0.05f, 1.66f, 2.12f, dark);
			p.box(x + side * 0.05f, 1.62f, 1.98f, x + side * 0.8f, 1.8f, 2.12f, dark);
		}
		// Tall pointed ears, smoke inside; laid back when petted or asleep, pricked up to howl; a twitch now and then.
		for (int side = -1; side <= 1; side += 2) {
			float twitch = (t + side * 31) % 130 < 5 ? 0.2f : 0;
			float flick = Mth.sin(t * 0.35f + side) * 0.12f;
			p.push();
			p.translate(side * 1.3f, 2.1f, 0.1f);
			p.rotateZ(-side * (0.18f + twitch) + side * pose.happy * 0.3f);
			p.rotateX(-0.08f - pose.happy * 0.55f - lie * 0.7f + howl * 0.2f);
			p.round(-0.85f, 0, -0.45f, 0.85f, 1.3f, 0.45f, fur, 0.3f);
			p.box(-0.6f, 1.3f, -0.35f, 0.6f, 2.1f, 0.35f, fur);
			p.box(-0.35f, 2.1f, -0.25f, 0.35f, 2.7f, 0.25f, fur);
			p.box(-0.15f, 2.7f, -0.15f, 0.15f, 3.1f, 0.15f, dark);
			p.box(-0.1f, 3.1f, -0.1f, 0.1f, 3.35f + flick, 0.1f, smoke);
			p.box(-0.45f, 0.3f, 0.45f, 0.45f, 1.8f, 0.5f, smoke);
			p.box(-0.22f, 1.8f, 0.35f, 0.22f, 2.4f, 0.4f, smoke);
			p.pop();
		}
		headWear(p, look, 0, 2.4f, -0.2f);
		p.pop();
		p.pop();
		p.pop();
	}

	/** A wolf's lower leg (from the knee down) with its paw, claws and a glowing band. */
	private static void leg(Painter p, int dark, int mark, int side) {
		p.round(-0.55f, -1.75f, -0.55f, 0.55f, 0.2f, 0.55f, dark, 0.25f);
		p.glow(-0.58f, -0.9f, -0.58f, 0.58f, -0.7f, 0.58f, mark);
		p.round(-0.7f, -2.0f, -0.6f, 0.7f, -1.45f, 0.95f, dark, 0.25f);
		for (int k = -1; k <= 1; k++) {
			p.box(k * 0.4f - 0.09f, -2.0f, 0.9f, k * 0.4f + 0.09f, -1.85f, 1.12f, 0xFF9A96A8);
		}
	}

	/** Smoke curling up from a point: {@code n} small cubes rising, drifting apart and shrinking. */
	private static void wisps(Painter p, float x, float y, float z, float spread, int n, float t, float seed, int color, float size) {
		for (int i = 0; i < n; i++) {
			float k = t * 0.02f + seed + i / (float) n;
			k -= Mth.floor(k);
			float s = size * (1 - k);
			if (s < 0.08f) {
				continue;
			}
			float wx = x + Mth.sin(seed * 7 + i * 2.3f + t * 0.05f) * spread * (0.4f + k);
			float wz = z + Mth.cos(seed * 5 + i * 1.7f + t * 0.04f) * spread * (0.4f + k);
			float wy = y + k * 2.4f;
			p.box(wx - s / 2, wy - s / 2, wz - s / 2, wx + s / 2, wy + s / 2, wz + s / 2, color);
		}
	}

	/** A swirl of smoke and sparks spinning up round it ({@code amount} 0 to 1). */
	private static void vortex(Painter p, float amount, float t, int smoke, int glow) {
		for (int i = 0; i < 18; i++) {
			float k = i / 18f;
			float a = t * 0.4f + k * Mth.TWO_PI * 2;
			float r = (2.8f - k * 1.4f) * (0.5f + 0.5f * amount);
			float y = 0.4f + k * 8f * amount;
			float s = (1.0f - k * 0.5f) * amount;
			if (s < 0.06f) {
				continue;
			}
			float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
			if (i % 4 == 0) {
				s *= 0.45f;
				p.glow(x - s, y - s, z - s, x + s, y + s, z + s, glow);
			} else {
				p.box(x - s / 2, y - s / 2, z - s / 2, x + s / 2, y + s / 2, z + s / 2, smoke);
			}
		}
	}

	// ---------------------------------------------------------------- faces

	enum Eye {
		OPEN, BLINK, HAPPY, SLEEP
	}

	/** Asleep: shut; petted: happy ^^; now and then a blink; else open. */
	private static Eye eye(Pose pose, float offset) {
		if (pose.sleep > 0.5f) {
			return Eye.SLEEP;
		}
		if (pose.happy > 0.4f) {
			return Eye.HAPPY;
		}
		return (pose.time + offset) % 97 < 3 ? Eye.BLINK : Eye.OPEN;
	}

	/**
	 * A cute face on a surface at depth {@code z}: two big eyes ({@code w} × {@code h}) with a shine, rosy cheeks below,
	 * a small smile (open when happy).
	 */
	private static void face(Painter p, float z, float y, float apart, float w, float h, int color, Pose pose, float offset) {
		Eye state = eye(pose, offset);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * apart;
			if (state == Eye.OPEN) {
				p.box(x - w / 2, y - h / 2, z, x + w / 2, y + h / 2, z + 0.12f, color);
				float s = Math.min(0.42f, w * 0.42f);
				p.glow(x - w / 2 + 0.08f, y + h / 2 - s - 0.08f, z + 0.12f, x - w / 2 + 0.08f + s, y + h / 2 - 0.08f, z + 0.17f, WHITE);
				p.glow(x + w / 2 - 0.3f, y - h / 2 + 0.12f, z + 0.12f, x + w / 2 - 0.12f, y - h / 2 + 0.3f, z + 0.17f, WHITE);
			} else {
				closedEye(p, x, y, z, w + 0.2f, state, color);
			}
			blush(p, side * (apart + w * 0.55f), y - h / 2 - 0.35f, z, pose);
		}
		float my = y - h / 2 - 0.55f;
		if (pose.happy > 0.4f) {
			p.box(-0.55f, my - 0.45f, z, 0.55f, my + 0.1f, z + 0.1f, 0xFF4A1E22);
			p.box(-0.35f, my - 0.45f, z + 0.1f, 0.35f, my - 0.2f, z + 0.13f, 0xFFE87A90);
		} else {
			p.box(-0.35f, my - 0.2f, z, 0.35f, my, z + 0.1f, color);
			p.box(-0.6f, my - 0.02f, z, -0.35f, my + 0.18f, z + 0.1f, color);
			p.box(0.35f, my - 0.02f, z, 0.6f, my + 0.18f, z + 0.1f, color);
		}
	}

	/** A shut eye: a thin line blinking, ︶ asleep, ^ happy. */
	private static void closedEye(Painter p, float x, float y, float z, float w, Eye state, int color) {
		float third = w / 3;
		float t = 0.22f;
		switch (state) {
			case BLINK -> p.box(x - w / 2, y - t / 2, z, x + w / 2, y + t / 2, z + 0.12f, color);
			case SLEEP -> {
				p.box(x - w / 2, y, z, x - w / 2 + third, y + t, z + 0.12f, color);
				p.box(x - third / 2 - 0.05f, y - t * 0.8f, z, x + third / 2 + 0.05f, y + t * 0.2f, z + 0.12f, color);
				p.box(x + w / 2 - third, y, z, x + w / 2, y + t, z + 0.12f, color);
			}
			case HAPPY -> {
				p.box(x - w / 2, y - t, z, x - w / 2 + third, y, z + 0.12f, color);
				p.box(x - third / 2 - 0.05f, y, z, x + third / 2 + 0.05f, y + t, z + 0.12f, color);
				p.box(x + w / 2 - third, y - t, z, x + w / 2, y, z + 0.12f, color);
			}
			default -> {
			}
		}
	}

	/** A rosy cheek, rosier when happy. */
	private static void blush(Painter p, float x, float y, float z, Pose pose) {
		int color = pose.happy > 0.3f ? 0xFFF47A98 : BLUSH;
		p.box(x - 0.55f, y - 0.22f, z, x + 0.55f, y + 0.22f, z + 0.08f, color);
	}

	private static void dotFront(Painter p, float x, float y, float z, float s, int color) {
		float d = Math.signum(z) * 0.12f;
		p.box(x - s / 2, y - s / 2, Math.min(z, z + d), x + s / 2, y + s / 2, Math.max(z, z + d), color);
	}

	private static void dotSide(Painter p, float x, float y, float z, float s, int color) {
		float d = Math.signum(x) * 0.12f;
		p.box(Math.min(x, x + d), y - s / 2, z - s / 2, Math.max(x, x + d), y + s / 2, z + s / 2, color);
	}

	private static void dotTop(Painter p, float x, float y, float z, float s, int color) {
		p.box(x - s / 2, y, z - s / 2, x + s / 2, y + 0.12f, z + s / 2, color);
	}

	// ---------------------------------------------------------------- accessories

	/** What is worn on top of the head (bow, flower, crown), drawn at the top of the head in its frame. */
	private static void headWear(Painter p, Companion.Look look, float x, float y, float z) {
		switch (look.accessory()) {
			case BOW -> {
				int color = dye(look.color());
				int dark = ARGB.scaleRGB(color, 0.75f);
				p.push();
				p.translate(x + 1.2f, y - 0.1f, z);
				p.rotateZ(-0.25f);
				p.round(-0.55f, 0, -0.45f, 0.55f, 1.0f, 0.45f, dark, 0.2f);
				p.round(-2.1f, 0.05f, -0.35f, -0.5f, 1.45f, 0.35f, color, 0.3f);
				p.round(0.5f, 0.05f, -0.35f, 2.1f, 1.45f, 0.35f, color, 0.3f);
				p.box(-1.7f, 0.5f, 0.35f, -0.9f, 0.9f, 0.4f, lighter(color, 50));
				p.box(0.9f, 0.5f, 0.35f, 1.7f, 0.9f, 0.4f, lighter(color, 50));
				p.box(-0.9f, -0.6f, -0.2f, -0.5f, 0.1f, 0.2f, color);
				p.box(0.5f, -0.6f, -0.2f, 0.9f, 0.1f, 0.2f, color);
				p.pop();
			}
			case FLOWER -> {
				p.push();
				p.translate(x + 1.3f, y - 0.1f, z);
				p.rotateZ(-0.2f);
				p.box(-0.15f, 0, -0.15f, 0.15f, 1.2f, 0.15f, LEAF);
				p.round(0.1f, 0.4f, -0.2f, 1.0f, 0.65f, 0.3f, LEAF, 0.1f);
				for (int i = 0; i < 5; i++) {
					float a = i * Mth.TWO_PI / 5;
					float px = Mth.cos(a) * 0.65f, pz = Mth.sin(a) * 0.65f;
					p.round(px - 0.45f, 1.1f, pz - 0.45f, px + 0.45f, 1.45f, pz + 0.45f, 0xFFF8F4FA, 0.15f);
				}
				p.round(-0.4f, 1.15f, -0.4f, 0.4f, 1.6f, 0.4f, 0xFFF0C030, 0.15f);
				p.pop();
			}
			case CROWN -> {
				p.push();
				p.translate(x, y - 0.1f, z);
				p.rotateZ(0.15f);
				ring(p, 0, 0.8f, 0, 1.5f, 1.5f, 0.35f, GOLD);
				for (int i = 0; i < 4; i++) {
					float cx = i % 2 == 0 ? -1.25f : 1.25f;
					float cz = i < 2 ? -1.25f : 1.25f;
					p.box(cx - 0.25f, 0.8f, cz - 0.25f, cx + 0.25f, 1.5f, cz + 0.25f, GOLD);
					p.box(cx - 0.18f, 1.5f, cz - 0.18f, cx + 0.18f, 1.85f, cz + 0.18f, WHITE);
				}
				p.glow(-0.3f, 0.2f, 1.5f, 0.3f, 0.65f, 1.62f, 0xFFD82A3A);
				p.pop();
			}
			default -> {
			}
		}
	}

	/** A collar round the neck (centre {@code z}, half sizes), with a little gold bell in front. */
	private static void neckWear(Painter p, Companion.Look look, float y, float z, float hx, float hz) {
		if (look.accessory() != Companion.Accessory.COLLAR || look.companion().style == Companion.Style.SNOWMAN) {
			return;
		}
		ring(p, y, y + 0.65f, z, hx + 0.1f, hz + 0.1f, 0.35f, dye(look.color()));
		p.round(-0.45f, y - 0.75f, z + hz + 0.3f, 0.45f, y + 0.1f, z + hz + 0.95f, GOLD, 0.25f);
		p.box(-0.1f, y - 0.6f, z + hz + 0.95f, 0.1f, y - 0.4f, z + hz + 1.0f, ARGB.scaleRGB(GOLD, 0.6f));
	}

	/** A flat ring round x = 0 and {@code z}, from y0 to y1, its outer half sizes {@code hx} and {@code hz}. */
	private static void ring(Painter p, float y0, float y1, float z, float hx, float hz, float t, int color) {
		p.box(-hx, y0, z + hz - t, hx, y1, z + hz, color);
		p.box(-hx, y0, z - hz, hx, y1, z - hz + t, color);
		p.box(-hx, y0, z - hz + t, -hx + t, y1, z + hz - t, color);
		p.box(hx - t, y0, z - hz + t, hx, y1, z + hz - t, color);
	}

	static int dye(int id) {
		return 0xFF000000 | DyeColor.byId(Mth.clamp(id, 0, 15)).getTextureDiffuseColor();
	}

	private static int lighter(int color, int by) {
		return ARGB.color(255, Math.min(255, ARGB.red(color) + by), Math.min(255, ARGB.green(color) + by),
				Math.min(255, ARGB.blue(color) + by));
	}

	/** Boxes in a moving frame. */
	private static final class Painter {
		private final VertexConsumer vc;
		private final int light;
		private final Deque<PoseStack.Pose> stack = new ArrayDeque<>();
		private PoseStack.Pose pose;

		Painter(VertexConsumer vc, PoseStack.Pose base, int light) {
			this.vc = vc;
			this.light = light;
			this.pose = base.copy();
		}

		void push() {
			stack.push(pose.copy());
		}

		void pop() {
			pose = stack.pop();
		}

		void scale(float s) {
			pose.scale(s, s, s);
		}

		void scale(float x, float y, float z) {
			pose.scale(x, y, z);
		}

		void scale3(float x, float y, float z) {
			pose.scale(x, y, z);
		}

		void translate(float x, float y, float z) {
			pose.translate(x, y, z);
		}

		void rotateX(float radians) {
			pose.rotate(Axis.XP, radians);
		}

		void rotateY(float radians) {
			pose.rotate(Axis.YP, radians);
		}

		void rotateZ(float radians) {
			pose.rotate(Axis.ZP, radians);
		}

		void box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
			Voxels.box(vc, pose, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1),
					Math.max(z0, z1), color, light);
		}

		/** A box with its edges rounded off by {@code r}: three boxes, each shortened on two axes, and one a little smaller all round. */
		void round(float x0, float y0, float z0, float x1, float y1, float z1, int color, float r) {
			float ax = Math.min(x0, x1), bx = Math.max(x0, x1);
			float ay = Math.min(y0, y1), by = Math.max(y0, y1);
			float az = Math.min(z0, z1), bz = Math.max(z0, z1);
			r = Math.min(r, Math.min(bx - ax, Math.min(by - ay, bz - az)) * 0.45f);
			if (r <= 0.02f) {
				box(ax, ay, az, bx, by, bz, color);
				return;
			}
			Voxels.box(vc, pose, ax, ay + r, az + r, bx, by - r, bz - r, color, light);
			Voxels.box(vc, pose, ax + r, ay, az + r, bx - r, by, bz - r, color, light);
			Voxels.box(vc, pose, ax + r, ay + r, az, bx - r, by - r, bz, color, light);
			// A second, smaller step on every edge and corner, so it reads round rather than cut.
			float s = r * 0.32f;
			Voxels.box(vc, pose, ax + s, ay + s, az + s, bx - s, by - s, bz - s, color, light);
		}

		/** A box that glows: full light, whatever is around. */
		void glow(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
			Voxels.box(vc, pose, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1),
					Math.max(z0, z1), color, Voxels.FULL_BRIGHT);
		}
	}
}
