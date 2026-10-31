package dev.cobblecomfort.features.softdoors;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class SoftDoorsConfigUi {
	private SoftDoorsConfigUi() {
	}

	public static ConfigCategory buildCategory(SoftDoorsFeature feature) {
		SoftDoorsConfig d = new SoftDoorsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("soft_doors.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.soft_doors.blocks"))
						.option(Opts.bool("soft_doors.doors", d.doors, () -> c(feature).doors, v -> c(feature).doors = v))
						.option(Opts.bool("soft_doors.trapdoors", d.trapdoors, () -> c(feature).trapdoors, v -> c(feature).trapdoors = v))
						.option(Opts.bool("soft_doors.gates", d.gates, () -> c(feature).gates, v -> c(feature).gates = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.soft_doors.feel"))
						.option(Opts.percent("soft_doors.speed", d.speed, () -> c(feature).speed, v -> c(feature).speed = v))
						.option(Opts.percent("soft_doors.bounce", d.bounce, () -> c(feature).bounce, v -> c(feature).bounce = v))
						.build())
				.build();
	}

	private static SoftDoorsConfig c(SoftDoorsFeature feature) {
		return feature.config();
	}
}
