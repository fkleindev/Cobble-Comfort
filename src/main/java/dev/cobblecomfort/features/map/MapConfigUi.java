package dev.cobblecomfort.features.map;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class MapConfigUi {
	private MapConfigUi() {
	}

	public static ConfigCategory buildCategory(MapFeature feature) {
		MapConfig d = new MapConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("map.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.map.minimap"))
						.option(Opts.bool("map.minimap", d.minimap, () -> c(feature).minimap, v -> c(feature).minimap = v))
						.option(Opts.bool("map.round", d.roundMinimap, () -> c(feature).roundMinimap, v -> c(feature).roundMinimap = v))
						.option(Opts.bool("map.rotate", d.rotateMinimap, () -> c(feature).rotateMinimap, v -> c(feature).rotateMinimap = v))
						.option(Opts.intSlider("map.size", d.minimapSize, 60, 200, 5, () -> c(feature).minimapSize, v -> c(feature).minimapSize = v))
						.option(Opts.slider("map.zoom", d.minimapZoom, 0.25f, 4f, 0.25f, () -> c(feature).minimapZoom, v -> c(feature).minimapZoom = v))
						.option(Opts.bool("map.coords", d.minimapCoords, () -> c(feature).minimapCoords, v -> c(feature).minimapCoords = v))
						.option(Opts.bool("map.biome", d.minimapBiome, () -> c(feature).minimapBiome, v -> c(feature).minimapBiome = v))
						.option(Opts.bool("map.entities", d.showEntities, () -> c(feature).showEntities, v -> c(feature).showEntities = v))
						.option(Opts.bool("map.players", d.showPlayers, () -> c(feature).showPlayers, v -> c(feature).showPlayers = v))
						.option(Opts.bool("map.hostiles", d.showHostiles, () -> c(feature).showHostiles, v -> c(feature).showHostiles = v))
						.option(Opts.bool("map.passives", d.showPassives, () -> c(feature).showPassives, v -> c(feature).showPassives = v))
						.option(Opts.bool("map.mob_heads", d.mobHeads, () -> c(feature).mobHeads, v -> c(feature).mobHeads = v))
						.option(Opts.bool("map.height_fade", d.heightFade, () -> c(feature).heightFade, v -> c(feature).heightFade = v))
						.option(Opts.intSlider("map.height_range", d.entityHeightRange, 4, 64, 2,
								() -> c(feature).entityHeightRange, v -> c(feature).entityHeightRange = v))
						.option(Opts.bool("map.trail", d.movementTrail, () -> c(feature).movementTrail, v -> c(feature).movementTrail = v))
						.option(Opts.bool("map.share_position", d.sharePosition, () -> c(feature).sharePosition, v -> c(feature).sharePosition = v))
						.option(Opts.bool("map.view_cone", d.viewCone, () -> c(feature).viewCone, v -> c(feature).viewCone = v))
						.option(Opts.bool("map.cardinals", d.minimapCardinals, () -> c(feature).minimapCardinals, v -> c(feature).minimapCardinals = v))
						.option(Opts.color("map.border_color", d.borderColor, false,
								() -> c(feature).borderColor, v -> c(feature).borderColor = v | 0xFF000000))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.map.map"))
						.option(Opts.bool("map.cave_mode", d.caveMode, () -> c(feature).caveMode, v -> c(feature).caveMode = v))
						.option(Opts.bool("map.nether_roof", d.netherUnderRoof, () -> c(feature).netherUnderRoof, v -> c(feature).netherUnderRoof = v))
						.option(Opts.bool("map.shading", d.heightShading, () -> c(feature).heightShading, v -> c(feature).heightShading = v))
						.option(Opts.bool("map.grid", d.showGrid, () -> c(feature).showGrid, v -> c(feature).showGrid = v))
						.option(Opts.percent("map.window_opacity", d.mapWindowOpacity,
								() -> c(feature).mapWindowOpacity, v -> c(feature).mapWindowOpacity = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.map.waypoints"))
						.option(Opts.bool("map.waypoints", d.waypoints, () -> c(feature).waypoints, v -> c(feature).waypoints = v))
						.option(Opts.bool("map.beams", d.waypointBeams, () -> c(feature).waypointBeams, v -> c(feature).waypointBeams = v))
						.option(Opts.bool("map.labels", d.waypointLabels, () -> c(feature).waypointLabels, v -> c(feature).waypointLabels = v))
						.option(Opts.intSlider("map.label_range", d.waypointLabelRange, 100, 10000, 100,
								() -> c(feature).waypointLabelRange, v -> c(feature).waypointLabelRange = v))
						.option(Opts.bool("map.death_waypoints", d.deathWaypoints,
								() -> c(feature).deathWaypoints, v -> c(feature).deathWaypoints = v))
						.option(Opts.intSlider("map.max_deaths", d.maxDeathWaypoints, 1, 10, 1,
								() -> c(feature).maxDeathWaypoints, v -> c(feature).maxDeathWaypoints = v))
						.option(Opts.bool("map.respawn_waypoint", d.respawnWaypoint,
								() -> c(feature).respawnWaypoint, v -> c(feature).respawnWaypoint = v))
						.option(Opts.bool("map.chat_waypoints", d.chatWaypoints, () -> c(feature).chatWaypoints, v -> c(feature).chatWaypoints = v))
						.option(Opts.bool("map.import_xaero", d.importXaero, () -> c(feature).importXaero, v -> c(feature).importXaero = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.map.locator"))
						.option(Opts.bool("map.locator_waypoints", d.locatorWaypoints,
								() -> c(feature).locatorWaypoints, v -> c(feature).locatorWaypoints = v))
						.option(Opts.bool("map.locator_heads", d.locatorHeads, () -> c(feature).locatorHeads, v -> c(feature).locatorHeads = v))
						.option(Opts.bool("map.locator_names", d.locatorNames, () -> c(feature).locatorNames, v -> c(feature).locatorNames = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.map.seed"))
						.option(Opts.bool("map.seed_terrain", d.seedTerrain, () -> c(feature).seedTerrain, v -> c(feature).seedTerrain = v))
						.option(Opts.percent("map.unexplored_brightness", d.unexploredBrightness,
								() -> c(feature).unexploredBrightness, v -> c(feature).unexploredBrightness = v))
						.option(Opts.bool("map.structures", d.structures, () -> c(feature).structures, v -> c(feature).structures = v))
						.option(Opts.bool("map.structures_seed", d.structuresFromSeed,
								() -> c(feature).structuresFromSeed, v -> c(feature).structuresFromSeed = v))
						.option(Opts.bool("map.structures_detected", d.structuresDetected,
								() -> c(feature).structuresDetected, v -> c(feature).structuresDetected = v))
						.option(Opts.intSlider("map.structure_radius", d.structureRadius, 500, 20000, 500,
								() -> c(feature).structureRadius, v -> c(feature).structureRadius = v))
						.build())
				.build();
	}

	private static MapConfig c(MapFeature feature) {
		return feature.config();
	}
}
