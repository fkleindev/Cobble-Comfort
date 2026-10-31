package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class PetPlushConfigUi {
	private PetPlushConfigUi() {
	}

	public static ConfigCategory buildCategory(PetPlushFeature feature) {
		PetPlushConfig d = new PetPlushConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("pet_plush.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.pet_plush.who"))
						.option(Opts.bool("pet_plush.show_own", d.showOwn, () -> c(feature).showOwn, v -> c(feature).showOwn = v))
						.option(Opts.bool("pet_plush.show_others", d.showOthers, () -> c(feature).showOthers, v -> c(feature).showOthers = v))
						.option(Opts.bool("pet_plush.share", d.share, () -> c(feature).share, v -> c(feature).share = v))
						.option(Opts.bool("pet_plush.nearby_pets", d.nearbyPets, () -> c(feature).nearbyPets, v -> c(feature).nearbyPets = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.pet_plush.physics"))
						.option(Opts.percent("pet_plush.swing", d.swing, () -> c(feature).swing, v -> c(feature).swing = v))
						.option(Opts.bool("pet_plush.hurt_squish", d.hurtSquish, () -> c(feature).hurtSquish, v -> c(feature).hurtSquish = v))
						.build())
				.build();
	}

	private static PetPlushConfig c(PetPlushFeature feature) {
		return feature.config();
	}
}
