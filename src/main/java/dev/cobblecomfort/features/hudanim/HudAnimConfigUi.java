package dev.cobblecomfort.features.hudanim;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class HudAnimConfigUi {
	private HudAnimConfigUi() {
	}

	public static ConfigCategory buildCategory(HudAnimFeature feature) {
		HudAnimConfig d = new HudAnimConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("hud_anim.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hud_anim.hearts"))
						.option(Opts.bool("hud_anim.health_trail", d.healthTrail, () -> c(feature).healthTrail, v -> c(feature).healthTrail = v))
						.option(Opts.bool("hud_anim.heal_pop", d.healPop, () -> c(feature).healPop, v -> c(feature).healPop = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hud_anim.xp"))
						.option(Opts.bool("hud_anim.smooth_xp", d.smoothXp, () -> c(feature).smoothXp, v -> c(feature).smoothXp = v))
						.option(Opts.bool("hud_anim.level_pulse", d.levelPulse, () -> c(feature).levelPulse, v -> c(feature).levelPulse = v))
						.option(Opts.bool("hud_anim.xp_gain", d.xpGain, () -> c(feature).xpGain, v -> c(feature).xpGain = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hud_anim.icons"))
						.option(Opts.bool("hud_anim.food", d.food, () -> c(feature).food, v -> c(feature).food = v))
						.option(Opts.bool("hud_anim.armor", d.armor, () -> c(feature).armor, v -> c(feature).armor = v))
						.option(Opts.bool("hud_anim.air", d.air, () -> c(feature).air, v -> c(feature).air = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hud_anim.boss"))
						.option(Opts.bool("hud_anim.boss_smooth", d.bossSmooth, () -> c(feature).bossSmooth, v -> c(feature).bossSmooth = v))
						.option(Opts.bool("hud_anim.boss_trail", d.bossTrail, () -> c(feature).bossTrail, v -> c(feature).bossTrail = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hud_anim.timing"))
						.option(Opts.slider("hud_anim.trail_delay", d.trailDelay, 0f, 1.5f, 0.05f, () -> c(feature).trailDelay, v -> c(feature).trailDelay = v))
						.option(Opts.slider("hud_anim.speed", d.speed, 0.25f, 3f, 0.05f, () -> c(feature).speed, v -> c(feature).speed = v))
						.option(Opts.percent("hud_anim.pop_strength", d.popStrength, () -> c(feature).popStrength, v -> c(feature).popStrength = v))
						.build())
				.build();
	}

	private static HudAnimConfig c(HudAnimFeature feature) {
		return feature.config();
	}
}
