package dev.cobblecomfort.features.hud;

import dev.cobblecomfort.core.FeatureManager;
import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.core.config.SettingsScreen;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Builds the YACL options for the HUD customizer. */
public final class HudConfigUi {
	/** Name typed into the "save preset" field; not persisted. */
	private static String presetName = "";

	private HudConfigUi() {
	}

	public static ConfigCategory buildCategory(HudCustomizerFeature feature) {
		ConfigCategory.Builder category = ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("hud.enabled", true, () -> feature.config().enabled, v -> feature.config().enabled = v))
				.option(ButtonOption.createBuilder()
						.name(Opts.name("hud.open_editor"))
						.description(Opts.desc("hud.open_editor"))
						.action(screen -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(Minecraft.getInstance().gui.screen())))
						.build());

		HudConfig defaults = new HudConfig();
		category.group(OptionGroup.createBuilder()
				.name(Component.translatable("cobblecomfort.group.hud.editor"))
				.option(Opts.bool("hud.snapping", defaults.snapping,
						() -> feature.config().snapping, v -> feature.config().snapping = v))
				.option(Opts.intSlider("hud.snap_threshold", defaults.snapThreshold, 1, 16, 1,
						() -> feature.config().snapThreshold, v -> feature.config().snapThreshold = v))
				.option(Opts.intSlider("hud.grid_size", defaults.gridSize, 0, 32, 1,
						() -> feature.config().gridSize, v -> feature.config().gridSize = v))
				.build());

		category.group(presetGroup(feature));

		for (HudElementDef def : HudElements.all()) {
			category.group(OptionGroup.createBuilder()
					.name(def.displayName())
					.collapsed(true)
					.options(elementOptions(feature, def))
					.build());
		}
		return category.build();
	}

	/** A config screen for a single element, opened from the HUD editor. */
	public static Screen elementScreen(HudCustomizerFeature feature, HudElementDef def, Screen parent) {
		ConfigCategory category = ConfigCategory.createBuilder()
				.name(def.displayName())
				.options(elementOptions(feature, def))
				.build();
		return new SettingsScreen(parent, def.displayName(),
				List.of(new SettingsScreen.Section(def.displayName(), null, category, null)), FeatureManager::saveAll);
	}

	private static OptionGroup presetGroup(HudCustomizerFeature feature) {
		OptionGroup.Builder group = OptionGroup.createBuilder()
				.name(Component.translatable("cobblecomfort.group.hud.presets"))
				.collapsed(true);
		for (HudPresets.BuiltIn preset : HudPresets.BuiltIn.values()) {
			String key = preset.name().toLowerCase(Locale.ROOT);
			group.option(ButtonOption.createBuilder()
					.name(Component.translatable("cobblecomfort.preset." + key))
					.description(Opts.desc("hud.preset." + key))
					.text(Component.translatable("cobblecomfort.option.hud.preset.apply"))
					.action(screen -> applyAndShow(feature, preset.create(), Minecraft.getInstance().gui.screen()))
					.build());
		}
		Option<String> nameOption = Option.<String>createBuilder()
				.name(Opts.name("hud.preset.name"))
				.description(Opts.desc("hud.preset.name"))
				.binding("", () -> presetName, v -> presetName = v)
				.controller(StringControllerBuilder::create)
				.build();
		group.option(nameOption);
		group.option(ButtonOption.createBuilder()
				.name(Opts.name("hud.preset.save"))
				.description(Opts.desc("hud.preset.save"))
				.action((screen, button) -> {
					// The text field is only committed on save, so read its pending value directly.
					if (HudPresets.save(nameOption.pendingValue(), HudPresets.copy(feature.config().elements))) {
						button.setAvailable(false);
					}
				})
				.build());
		for (String name : HudPresets.userPresets()) {
			group.option(ButtonOption.createBuilder()
					.name(Component.literal(name))
					.description(Opts.desc("hud.preset.user"))
					.text(Component.translatable("cobblecomfort.option.hud.preset.apply"))
					.action(screen -> {
						Map<String, HudElementConfig> layout = HudPresets.load(name);
						if (layout != null) {
							applyAndShow(feature, layout, Minecraft.getInstance().gui.screen());
						}
					})
					.build());
		}
		return group.build();
	}

	private static void applyAndShow(HudCustomizerFeature feature, Map<String, HudElementConfig> layout, Screen parent) {
		feature.applyLayout(layout);
		Minecraft.getInstance().gui.setScreen(new HudEditorScreen(parent));
	}

	private static List<Option<?>> elementOptions(HudCustomizerFeature feature, HudElementDef def) {
		Supplier<HudElementConfig> c = () -> feature.config().element(def);
		HudElementConfig d = HudConfig.defaultsFor(def);
		List<Option<?>> options = new ArrayList<>();

		options.add(Opts.bool("hud.element.visible", d.visible, () -> c.get().visible, v -> c.get().visible = v));
		options.add(Opts.percent("hud.element.opacity", d.opacity, () -> c.get().opacity, v -> c.get().opacity = v));
		options.add(Opts.slider("hud.element.scale", d.scale, 0.25f, 3f, 0.05f, () -> c.get().scale, v -> c.get().scale = v));

		options.add(Opts.bool("hud.element.custom_position", d.customPosition,
				() -> c.get().customPosition, v -> c.get().customPosition = v));
		options.add(Opts.enumOpt("hud.element.anchor", Anchor.class, d.anchor, () -> c.get().anchor, v -> c.get().anchor = v));
		options.add(Opts.slider("hud.element.offset_x", d.offsetX, -2000f, 2000f, 1f,
				() -> c.get().offsetX, v -> c.get().offsetX = v));
		options.add(Opts.slider("hud.element.offset_y", d.offsetY, -2000f, 2000f, 1f,
				() -> c.get().offsetY, v -> c.get().offsetY = v));

		options.add(Opts.color("hud.element.tint", d.tint, false, () -> c.get().tint, v -> c.get().tint = v | 0xFF000000));
		options.add(Opts.enumOpt("hud.element.text_shadow", TextShadow.class, d.textShadow,
				() -> c.get().textShadow, v -> c.get().textShadow = v));
		options.add(Opts.bool("hud.element.background", d.background, () -> c.get().background, v -> c.get().background = v));
		options.add(Opts.color("hud.element.background_color", d.backgroundColor, true,
				() -> c.get().backgroundColor, v -> c.get().backgroundColor = v));
		options.add(Opts.intSlider("hud.element.background_padding", d.backgroundPadding, 0, 12, 1,
				() -> c.get().backgroundPadding, v -> c.get().backgroundPadding = v));
		options.add(Opts.intSlider("hud.element.background_radius", d.backgroundRadius, 0, 10, 1,
				() -> c.get().backgroundRadius, v -> c.get().backgroundRadius = v));

		options.add(Opts.bool("hud.element.auto_hide", d.autoHide, () -> c.get().autoHide, v -> c.get().autoHide = v));
		options.add(Opts.slider("hud.element.auto_hide_delay", d.autoHideDelay, 0.5f, 30f, 0.5f,
				() -> c.get().autoHideDelay, v -> c.get().autoHideDelay = v));
		options.add(Opts.enumOpt("hud.element.animation", ShowAnimation.class, d.animation,
				() -> c.get().animation, v -> c.get().animation = v));
		options.add(Opts.enumOpt("hud.element.easing", Easing.class, d.easing, () -> c.get().easing, v -> c.get().easing = v));
		options.add(Opts.slider("hud.element.animation_duration", d.animationDuration, 0.05f, 2f, 0.05f,
				() -> c.get().animationDuration, v -> c.get().animationDuration = v));
		if (def.supportsPulse()) {
			options.add(Opts.bool("hud.element.pulse_when_low", d.pulseWhenLow,
					() -> c.get().pulseWhenLow, v -> c.get().pulseWhenLow = v));
		}
		return options;
	}
}
