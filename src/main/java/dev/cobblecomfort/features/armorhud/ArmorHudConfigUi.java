package dev.cobblecomfort.features.armorhud;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class ArmorHudConfigUi {
	private ArmorHudConfigUi() {
	}

	public static ConfigCategory buildCategory(ArmorHudFeature feature) {
		ArmorHudConfig d = new ArmorHudConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("armor.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.armor.content"))
						.option(Opts.bool("armor.show_arrows", d.showArrows, () -> c(feature).showArrows, v -> c(feature).showArrows = v))
						.option(Opts.bool("armor.show_empty_slots", d.showEmptySlots,
								() -> c(feature).showEmptySlots, v -> c(feature).showEmptySlots = v))
						.option(Opts.enumOpt("armor.layout", ArmorLayout.class, d.layout, () -> c(feature).layout, v -> c(feature).layout = v))
						.option(Opts.intSlider("armor.spacing", d.spacing, 16, 40, 1, () -> c(feature).spacing, v -> c(feature).spacing = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.armor.durability"))
						.option(Opts.enumOpt("armor.durability_display", DurabilityDisplay.class, d.durabilityDisplay,
								() -> c(feature).durabilityDisplay, v -> c(feature).durabilityDisplay = v))
						.option(Opts.slider("armor.text_scale", d.textScale, 0.5f, 1.5f, 0.05f,
								() -> c(feature).textScale, v -> c(feature).textScale = v))
						.option(Opts.bool("armor.color_by_durability", d.colorByDurability,
								() -> c(feature).colorByDurability, v -> c(feature).colorByDurability = v))
						.option(Opts.color("armor.text_color", d.textColor, false,
								() -> c(feature).textColor, v -> c(feature).textColor = v | 0xFF000000))
						.option(Opts.bool("armor.text_shadow", d.textShadow, () -> c(feature).textShadow, v -> c(feature).textShadow = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.armor.warning"))
						.option(Opts.percent("armor.warn_below", d.warnBelow, () -> c(feature).warnBelow, v -> c(feature).warnBelow = v))
						.option(Opts.bool("armor.warn_blink", d.warnBlink, () -> c(feature).warnBlink, v -> c(feature).warnBlink = v))
						.option(Opts.color("armor.warn_color", d.warnColor, false,
								() -> c(feature).warnColor, v -> c(feature).warnColor = v | 0xFF000000))
						.option(Opts.bool("armor.warn_sound", d.warnSound, () -> c(feature).warnSound, v -> c(feature).warnSound = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.armor.animation"))
						.option(Opts.bool("armor.animate_equip", d.animateEquip,
								() -> c(feature).animateEquip, v -> c(feature).animateEquip = v))
						.option(Opts.slider("armor.equip_duration", d.equipDuration, 0.05f, 1f, 0.05f,
								() -> c(feature).equipDuration, v -> c(feature).equipDuration = v))
						.option(Opts.bool("armor.shake_on_damage", d.shakeOnDamage,
								() -> c(feature).shakeOnDamage, v -> c(feature).shakeOnDamage = v))
						.option(Opts.bool("armor.smooth_movement", d.smoothMovement,
								() -> c(feature).smoothMovement, v -> c(feature).smoothMovement = v))
						.build())
				.build();
	}

	private static ArmorHudConfig c(ArmorHudFeature feature) {
		return feature.config();
	}
}
