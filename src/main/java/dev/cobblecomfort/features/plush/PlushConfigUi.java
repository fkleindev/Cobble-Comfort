package dev.cobblecomfort.features.plush;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class PlushConfigUi {
	private PlushConfigUi() {
	}

	public static ConfigCategory buildCategory(PlushFeature feature) {
		PlushConfig d = new PlushConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("plush.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.plush.where"))
						.option(Opts.bool("plush.in_hand", d.inHand, () -> c(feature).inHand, v -> c(feature).inHand = v))
						.option(Opts.bool("plush.in_gui", d.inGui, () -> c(feature).inGui, v -> c(feature).inGui = v))
						.option(Opts.bool("plush.activation", d.activation, () -> c(feature).activation, v -> c(feature).activation = v))
						.option(Opts.bool("plush.in_world", d.inWorld, () -> c(feature).inWorld, v -> c(feature).inWorld = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.plush.animation"))
						.option(Opts.bool("plush.wobble", d.wobble, () -> c(feature).wobble, v -> c(feature).wobble = v))
						.option(Opts.bool("plush.equip_squish", d.equipSquish, () -> c(feature).equipSquish, v -> c(feature).equipSquish = v))
						.option(Opts.bool("plush.activation_squish", d.activationSquish,
								() -> c(feature).activationSquish, v -> c(feature).activationSquish = v))
						.option(Opts.bool("plush.hurt_squish", d.hurtSquish, () -> c(feature).hurtSquish, v -> c(feature).hurtSquish = v))
						.build())
				.build();
	}

	private static PlushConfig c(PlushFeature feature) {
		return feature.config();
	}
}
