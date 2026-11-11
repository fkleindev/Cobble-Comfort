package dev.cobblecomfort.features.atmosphere;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The birds that fly over: their shape in voxels, colours, how they fly and sound. Sizes are a little larger than life
 * so they still read as birds at a distance.
 */
enum BirdSpecies {
	SEAGULL(new Shape(6, 3, 3, 3, 0, 1.5f, 2, 3, 5, 6, 3).scale(0.06f), Flight.SOAR, 0.16f, 6, 22, Group.LOOSE, 1, 3,
			true, 0.32f, 0.85f, look(0xFFF2F2F2, 0xFFFFFFFF, 0xFFB9C0C6, 0xFF2A2A2A, 0xFFF4F4F4, 0xFFF2C230, 0xFFEDEDED)),
	GOOSE(new Shape(7, 3, 3, 2.5f, 3, 1.5f, 2, 3, 5, 6, 3).scale(0.065f), Flight.FLAP, 0.3f, 18, 40, Group.V, 5, 11,
			false, 0.3f, 0.55f, look(0xFF8A7F72, 0xFFD9D2C5, 0xFF6E655A, 0xFF3A342E, 0xFF1E1E1E, 0xFF1E1E1E, 0xFF2A2A2A)),
	DUCK(new Shape(6, 3, 3, 2.5f, 1, 1.5f, 1.5f, 3, 4, 4, 2.5f).scale(0.05f), Flight.FLAP, 0.34f, 5, 16, Group.LOOSE, 2, 5,
			false, 0.25f, 0.7f, look(0xFF8C7B6A, 0xFFB7A994, 0xFF6B5E50, 0xFF3B4FA0, 0xFF1F6B3A, 0xFFE8C040, 0xFF4A4038),
			look(0xFF8E7457, 0xFFB09574, 0xFF7A6248, 0xFF3B4FA0, 0xFF8E7457, 0xFFC08A30, 0xFF6A5640)),
	HERON(new Shape(7, 2.5f, 3, 2.5f, 0, 3, 1.5f, 3, 6, 7, 3.5f).scale(0.065f).legs(), Flight.SLOW_FLAP, 0.2f, 6, 18,
			Group.ALONE, 1, 1, false, 0.22f, 0.45f,
			look(0xFF9AA3AC, 0xFFD6DADE, 0xFF7D8790, 0xFF2E3338, 0xFFE8E8E8, 0xFFD9A030, 0xFF8A949C)),
	SONGBIRD(new Shape(4, 2, 2, 2, 0, 1, 2, 2, 3, 3, 2).scale(0.04f), Flight.BOUND, 0.3f, 2, 7, Group.LOOSE, 3, 7, false,
			0.2f, 1.6f,
			look(0xFF8A6A4A, 0xFFD8C8A8, 0xFF6E5238, 0xFF3E2E20, 0xFF7A5A3A, 0xFF3A3A3A, 0xFF5E4630), // sparrow
			look(0xFF6F8A3A, 0xFFF0D84A, 0xFF4F6FB0, 0xFF2E3F70, 0xFF3D6FC0, 0xFF2A2A2A, 0xFF4A5F90), // blue tit
			look(0xFF7A6450, 0xFFE0662A, 0xFF6A5442, 0xFF443428, 0xFF7A6450, 0xFF2A2A2A, 0xFF5A4838), // robin
			look(0xFFE8C838, 0xFFF4E070, 0xFF2A2A2A, 0xFF1A1A1A, 0xFFE8C838, 0xFFE0A0A0, 0xFF2A2A2A)), // goldfinch
	PARROT(new Shape(5, 2.5f, 2.5f, 2.5f, 0, 1.5f, 6, 1.5f, 3, 4, 2.5f).scale(0.05f), Flight.FLAP, 0.32f, 3, 12,
			Group.LOOSE, 2, 4, false, 0.3f, 1.0f,
			look(0xFFE02A2A, 0xFFE84A3A, 0xFFE02A2A, 0xFF2F55C8, 0xFFE02A2A, 0xFF3A3A3A, 0xFF2F55C8),
			look(0xFF2F7FE0, 0xFFF2C840, 0xFF2F7FE0, 0xFF1F4FA0, 0xFF2F7FE0, 0xFF2A2A2A, 0xFF1F4FA0),
			look(0xFF4CC34C, 0xFF7ADC5A, 0xFF4CC34C, 0xFF2F55C8, 0xFFE8D040, 0xFF3A3A3A, 0xFF3A9A3A),
			look(0xFF39C4D6, 0xFF8AE0EA, 0xFF39C4D6, 0xFF2A8A9A, 0xFF39C4D6, 0xFF2A2A2A, 0xFF2A8A9A)),
	CROW(new Shape(5, 2.5f, 2.5f, 2.5f, 0, 1.5f, 3, 2.5f, 4, 5, 2.5f).scale(0.055f), Flight.FLAP, 0.26f, 6, 20,
			Group.LOOSE, 1, 4, false, 0.3f, 0.5f,
			look(0xFF1E1E24, 0xFF26262C, 0xFF26262E, 0xFF15151A, 0xFF1E1E24, 0xFF2E2E30, 0xFF1A1A20)),
	EAGLE(new Shape(7, 3, 3, 2.5f, 0, 1.5f, 3, 4, 6, 7, 4).scale(0.08f), Flight.SOAR, 0.22f, 22, 45, Group.ALONE, 1, 1,
			true, 0.28f, 1.3f, look(0xFF4A3424, 0xFF3E2C1F, 0xFF3E2C1F, 0xFF231810, 0xFFEDEDE6, 0xFFE5B530, 0xFFEDEDE6)),
	VULTURE(new Shape(7, 3.5f, 3, 2, 1, 1.5f, 2.5f, 4, 6, 8, 4.5f).scale(0.085f), Flight.SOAR, 0.18f, 25, 50, Group.LOOSE,
			1, 3, true, 0f, 1f, look(0xFF2B2420, 0xFF3A302A, 0xFF2B2420, 0xFF1A1512, 0xFFB86A5A, 0xFFD8CBB0, 0xFF231D1A)),
	OWL(new Shape(4, 3.5f, 3.5f, 3.5f, 0, 0.6f, 2, 3, 4, 5, 3.5f).scale(0.055f).eyes(), Flight.GLIDE, 0.22f, 3, 10,
			Group.ALONE, 1, 1, false, 0f, 1f,
			look(0xFF8A6A48, 0xFFD8C3A0, 0xFF7A5A3C, 0xFF5A4028, 0xFF9A7A56, 0xFF4A3A2A, 0xFF7A5A3C),
			look(0xFFEDEDE8, 0xFFFFFFFF, 0xFFE0E0D8, 0xFF8A8A80, 0xFFF2F2EE, 0xFF3A3A3A, 0xFFE0E0D8)),
	BAT(new Shape(3, 2, 2, 2, 0, 0, 0, 0, 3, 3, 3).scale(0.04f).ears(), Flight.FLUTTER, 0.24f, 3, 14, Group.LOOSE, 1, 4,
			false, 0.12f, 1.0f, look(0xFF4A3A30, 0xFF5A4A3E, 0xFF2E2420, 0xFF241C18, 0xFF4A3A30, 0xFF4A3A30, 0xFF2E2420));

	/** Body length, width, height, head size, neck, beak and tail length, tail width, inner and outer wing, wing depth. */
	static final class Shape {
		final float bodyL, bodyW, bodyH, head, neck, beak, tail, tailW, wingIn, wingOut, chord;
		float scale = 0.05f;
		boolean legs, eyes, ears;

		Shape(float bodyL, float bodyW, float bodyH, float head, float neck, float beak, float tail, float tailW,
				float wingIn, float wingOut, float chord) {
			this.bodyL = bodyL;
			this.bodyW = bodyW;
			this.bodyH = bodyH;
			this.head = head;
			this.neck = neck;
			this.beak = beak;
			this.tail = tail;
			this.tailW = tailW;
			this.wingIn = wingIn;
			this.wingOut = wingOut;
			this.chord = chord;
		}

		Shape scale(float scale) {
			this.scale = scale;
			return this;
		}

		Shape legs() {
			legs = true;
			return this;
		}

		Shape eyes() {
			eyes = true;
			return this;
		}

		Shape ears() {
			ears = true;
			return this;
		}
	}

	/** Colours of one bird: body, belly, wing, wing tip, head, beak, tail. */
	record Look(int body, int belly, int wing, int tip, int head, int beak, int tail) {
	}

	enum Flight {
		/** Steady wing beats. */
		FLAP,
		/** Slow, deep wing beats (herons). */
		SLOW_FLAP,
		/** A few quick beats, then wings folded for a moment (small birds). */
		BOUND,
		/** Gliding on held wings, now and then a few beats (gulls, birds of prey). */
		SOAR,
		/** Long silent glides with soft beats (owls). */
		GLIDE,
		/** Fast, jittery beats (bats). */
		FLUTTER
	}

	enum Group {
		ALONE, LOOSE, V
	}

	final Shape shape;
	final Flight flight;
	/** Blocks per tick. */
	final float speed;
	/** Height above the ground (or trees, or water) they fly at. */
	final float minHeight, maxHeight;
	final Group group;
	final int minCount, maxCount;
	/** Whether they like to circle over a spot instead of passing by. */
	final boolean circles;
	/** How loud and how high their call is; 0 for silent birds. */
	final float callVolume, callPitch;
	final Look[] looks;

	BirdSpecies(Shape shape, Flight flight, float speed, float minHeight, float maxHeight, Group group, int minCount,
			int maxCount, boolean circles, float callVolume, float callPitch, Look... looks) {
		this.shape = shape;
		this.flight = flight;
		this.speed = speed;
		this.minHeight = minHeight;
		this.maxHeight = maxHeight;
		this.group = group;
		this.minCount = minCount;
		this.maxCount = maxCount;
		this.circles = circles;
		this.callVolume = callVolume;
		this.callPitch = callPitch;
		this.looks = looks;
	}

	SoundEvent call() {
		return this == BAT ? SoundEvents.BAT_AMBIENT : SoundEvents.PARROT_AMBIENT;
	}

	boolean nocturnal() {
		return this == OWL || this == BAT;
	}

	private static Look look(int body, int belly, int wing, int tip, int head, int beak, int tail) {
		return new Look(body, belly, wing, tip, head, beak, tail);
	}
}
