package dev.cobblecomfort.features.inventoryfx;

import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.features.rarity.RarityLevel;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class InventoryFxConfigUi {
	private InventoryFxConfigUi() {
	}

	public static ConfigCategory buildCategory(InventoryFxFeature feature) {
		InventoryFxConfig d = new InventoryFxConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("invfx.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invfx.carried"))
						.option(Opts.percent("invfx.follow_lag", d.followLag, () -> c(feature).followLag, v -> c(feature).followLag = v))
						.option(Opts.bool("invfx.tilt", d.tilt, () -> c(feature).tilt, v -> c(feature).tilt = v))
						.option(Opts.slider("invfx.tilt_strength", d.tiltStrength, 0.1f, 3f, 0.1f,
								() -> c(feature).tiltStrength, v -> c(feature).tiltStrength = v))
						.option(Opts.bool("invfx.pickup_pop", d.pickupPop, () -> c(feature).pickupPop, v -> c(feature).pickupPop = v))
						.option(Opts.percent("invfx.pop_strength", d.popStrength, () -> c(feature).popStrength, v -> c(feature).popStrength = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invfx.slots"))
						.option(Opts.bool("invfx.drop_squash", d.dropSquash, () -> c(feature).dropSquash, v -> c(feature).dropSquash = v))
						.option(Opts.percent("invfx.squash_strength", d.squashStrength,
								() -> c(feature).squashStrength, v -> c(feature).squashStrength = v))
						.option(Opts.bool("invfx.slot_flash", d.slotFlash, () -> c(feature).slotFlash, v -> c(feature).slotFlash = v))
						.option(Opts.bool("invfx.spread_pop", d.spreadPop, () -> c(feature).spreadPop, v -> c(feature).spreadPop = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invfx.shortcuts"))
						.option(Opts.bool("invfx.shortcut_fx", d.shortcutFx, () -> c(feature).shortcutFx, v -> c(feature).shortcutFx = v))
						.option(Opts.bool("invfx.flying_items", d.flyingItems, () -> c(feature).flyingItems, v -> c(feature).flyingItems = v))
						.option(Opts.slider("invfx.fly_duration", d.flyDuration, 0.1f, 0.8f, 0.05f,
								() -> c(feature).flyDuration, v -> c(feature).flyDuration = v))
						.option(Opts.intSlider("invfx.fly_arc", d.flyArc, 0, 40, 2, () -> c(feature).flyArc, v -> c(feature).flyArc = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invfx.stations"))
						.option(Opts.bool("invfx.stations", d.stations, () -> c(feature).stations, v -> c(feature).stations = v))
						.option(Opts.bool("invfx.trading", d.trading, () -> c(feature).trading, v -> c(feature).trading = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invfx.particles"))
						.option(Opts.bool("invfx.particles", d.particles, () -> c(feature).particles, v -> c(feature).particles = v))
						.option(Opts.enumOpt("invfx.particle_style", ParticleStyle.class, d.particleStyle,
								() -> c(feature).particleStyle, v -> c(feature).particleStyle = v))
						.option(Opts.enumOpt("invfx.sparks_from", RarityLevel.class, d.sparksFrom,
								() -> c(feature).sparksFrom, v -> c(feature).sparksFrom = v))
						.option(Opts.bool("invfx.move_particles", d.moveParticles,
								() -> c(feature).moveParticles, v -> c(feature).moveParticles = v))
						.option(Opts.slider("invfx.density", d.density, 0.1f, 4f, 0.1f, () -> c(feature).density, v -> c(feature).density = v))
						.option(Opts.bool("invfx.pickup_burst", d.pickupBurst, () -> c(feature).pickupBurst, v -> c(feature).pickupBurst = v))
						.option(Opts.bool("invfx.drop_burst", d.dropBurst, () -> c(feature).dropBurst, v -> c(feature).dropBurst = v))
						.option(Opts.intSlider("invfx.burst_size", d.burstSize, 2, 40, 1, () -> c(feature).burstSize, v -> c(feature).burstSize = v))
						.option(Opts.slider("invfx.gravity", d.gravity, 0f, 3f, 0.1f, () -> c(feature).gravity, v -> c(feature).gravity = v))
						.option(Opts.slider("invfx.particle_lifetime", d.particleLifetime, 0.2f, 3f, 0.1f,
								() -> c(feature).particleLifetime, v -> c(feature).particleLifetime = v))
						.option(Opts.intSlider("invfx.max_particles", d.maxParticles, 50, 1000, 50,
								() -> c(feature).maxParticles, v -> c(feature).maxParticles = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invfx.sound"))
						.option(Opts.bool("invfx.sound", d.sound, () -> c(feature).sound, v -> c(feature).sound = v))
						.option(Opts.percent("invfx.sound_volume", d.soundVolume, () -> c(feature).soundVolume, v -> c(feature).soundVolume = v))
						.build())
				.build();
	}

	private static InventoryFxConfig c(InventoryFxFeature feature) {
		return feature.config();
	}
}
