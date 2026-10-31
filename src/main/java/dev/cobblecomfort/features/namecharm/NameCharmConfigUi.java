package dev.cobblecomfort.features.namecharm;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class NameCharmConfigUi {
	private NameCharmConfigUi() {
	}

	public static ConfigCategory buildCategory(NameCharmFeature feature) {
		NameCharmConfig d = new NameCharmConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("name_charm.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.name_charm.where"))
						.option(Opts.bool("name_charm.first_person", d.firstPerson, () -> c(feature).firstPerson, v -> c(feature).firstPerson = v))
						.option(Opts.bool("name_charm.third_person", d.thirdPerson, () -> c(feature).thirdPerson, v -> c(feature).thirdPerson = v))
						.option(Opts.bool("name_charm.in_world", d.inWorld, () -> c(feature).inWorld, v -> c(feature).inWorld = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.name_charm.physics"))
						.option(Opts.percent("name_charm.swing", d.swing, () -> c(feature).swing, v -> c(feature).swing = v))
						.build())
				.build();
	}

	private static NameCharmConfig c(NameCharmFeature feature) {
		return feature.config();
	}
}
