package dev.cobblecomfort.features.crosshair;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class CrosshairConfigUi {
	private CrosshairConfigUi() {
	}

	public static ConfigCategory buildCategory(CrosshairFeature feature) {
		CrosshairConfig d = new CrosshairConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("crosshair.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.crosshair.hits"))
						.option(Opts.bool("crosshair.hit_marker", d.hitMarker, () -> c(feature).hitMarker, v -> c(feature).hitMarker = v))
						.option(Opts.bool("crosshair.crit_color", d.critColor, () -> c(feature).critColor, v -> c(feature).critColor = v))
						.option(Opts.bool("crosshair.kill_marker", d.killMarker, () -> c(feature).killMarker, v -> c(feature).killMarker = v))
						.option(Opts.color("crosshair.hit_color", d.hitColor, false, () -> c(feature).hitColor, v -> c(feature).hitColor = v | 0xFF000000))
						.option(Opts.color("crosshair.crit_marker_color", d.critMarkerColor, false,
								() -> c(feature).critMarkerColor, v -> c(feature).critMarkerColor = v | 0xFF000000))
						.option(Opts.color("crosshair.kill_color", d.killColor, false, () -> c(feature).killColor, v -> c(feature).killColor = v | 0xFF000000))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.crosshair.cooldown"))
						.option(Opts.bool("crosshair.attack_ring", d.attackRing, () -> c(feature).attackRing, v -> c(feature).attackRing = v))
						.option(Opts.color("crosshair.ring_color", d.ringColor, false, () -> c(feature).ringColor, v -> c(feature).ringColor = v | 0xFF000000))
						.build())
				.build();
	}

	private static CrosshairConfig c(CrosshairFeature feature) {
		return feature.config();
	}
}
