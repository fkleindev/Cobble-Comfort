package dev.cobblecomfort.features.hud.anim;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Easing curves mapping linear progress 0..1 to eased progress. */
public enum Easing implements NameableEnum {
	LINEAR {
		@Override
		public float apply(float t) {
			return t;
		}
	},
	EASE_IN_OUT_SINE {
		@Override
		public float apply(float t) {
			return (float) (-(Math.cos(Math.PI * t) - 1) / 2);
		}
	},
	EASE_OUT_CUBIC {
		@Override
		public float apply(float t) {
			float inv = 1 - t;
			return 1 - inv * inv * inv;
		}
	},
	EASE_OUT_BACK {
		@Override
		public float apply(float t) {
			float c1 = 1.70158f;
			float c3 = c1 + 1;
			float u = t - 1;
			return 1 + c3 * u * u * u + c1 * u * u;
		}
	},
	EASE_OUT_ELASTIC {
		@Override
		public float apply(float t) {
			if (t <= 0 || t >= 1) {
				return t <= 0 ? 0 : 1;
			}
			return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
		}
	},
	EASE_OUT_BOUNCE {
		@Override
		public float apply(float t) {
			float n1 = 7.5625f;
			float d1 = 2.75f;
			if (t < 1 / d1) {
				return n1 * t * t;
			} else if (t < 2 / d1) {
				t -= 1.5f / d1;
				return n1 * t * t + 0.75f;
			} else if (t < 2.5 / d1) {
				t -= 2.25f / d1;
				return n1 * t * t + 0.9375f;
			}
			t -= 2.625f / d1;
			return n1 * t * t + 0.984375f;
		}
	};

	public abstract float apply(float t);

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.easing." + name().toLowerCase(Locale.ROOT));
	}
}
