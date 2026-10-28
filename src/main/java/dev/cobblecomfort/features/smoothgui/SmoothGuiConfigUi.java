package dev.cobblecomfort.features.smoothgui;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class SmoothGuiConfigUi {
	private SmoothGuiConfigUi() {
	}

	public static ConfigCategory buildCategory(SmoothGuiFeature feature) {
		SmoothGuiConfig d = new SmoothGuiConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("smooth_gui.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.smooth_gui.where"))
						.option(Opts.bool("smooth_gui.containers", d.containers, () -> c(feature).containers, v -> c(feature).containers = v))
						.option(Opts.bool("smooth_gui.menus", d.menus, () -> c(feature).menus, v -> c(feature).menus = v))
						.option(Opts.bool("smooth_gui.chat", d.chat, () -> c(feature).chat, v -> c(feature).chat = v))
						.option(Opts.bool("smooth_gui.only_from_game", d.onlyFromGame,
								() -> c(feature).onlyFromGame, v -> c(feature).onlyFromGame = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.smooth_gui.animation"))
						.option(Opts.slider("smooth_gui.duration", d.duration, 0.05f, 0.4f, 0.01f,
								() -> c(feature).duration, v -> c(feature).duration = v))
						.option(Opts.slider("smooth_gui.start_scale", d.startScale, 0.7f, 1f, 0.01f,
								() -> c(feature).startScale, v -> c(feature).startScale = v))
						.option(Opts.bool("smooth_gui.bounce", d.bounce, () -> c(feature).bounce, v -> c(feature).bounce = v))
						.option(Opts.bool("smooth_gui.fade", d.fade, () -> c(feature).fade, v -> c(feature).fade = v))
						.option(Opts.intSlider("smooth_gui.chat_slide", d.chatSlide, 0, 30, 1, () -> c(feature).chatSlide, v -> c(feature).chatSlide = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.smooth_gui.extras"))
						.option(Opts.bool("smooth_gui.stagger_slots", d.staggerSlots,
								() -> c(feature).staggerSlots, v -> c(feature).staggerSlots = v))
						.option(Opts.bool("smooth_gui.background_fade", d.backgroundFade,
								() -> c(feature).backgroundFade, v -> c(feature).backgroundFade = v))
						.option(Opts.slider("smooth_gui.background_duration", d.backgroundDuration, 0.05f, 0.5f, 0.01f,
								() -> c(feature).backgroundDuration, v -> c(feature).backgroundDuration = v))
						.build())
				.build();
	}

	private static SmoothGuiConfig c(SmoothGuiFeature feature) {
		return feature.config();
	}
}
