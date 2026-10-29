package dev.cobblecomfort.features.map;

import dev.cobblecomfort.core.FeatureConfig;

public class MapConfig extends FeatureConfig {
	// Minimap
	public boolean minimap = true;
	public boolean roundMinimap = true;
	public boolean rotateMinimap = true;
	public int minimapSize = 100;
	public float minimapZoom = 1f;
	public boolean minimapCoords = true;
	public boolean minimapBiome = false;
	public boolean showEntities = true;
	public boolean showHostiles = true;
	public boolean showPassives = true;
	public boolean showPlayers = true;
	public boolean mobHeads = true;
	public boolean heightFade = true;
	/** Mobs more than this many blocks above or below the player are hidden on the minimap and map. */
	public int entityHeightRange = 16;
	public boolean movementTrail = true;
	/** Share the own position with other mod users through the server plugin (opt-in); only then are theirs shown. */
	public boolean sharePosition = false;
	public boolean viewCone = true;
	public boolean minimapCardinals = true;
	/** Waypoints beyond the minimap's edge as small arrows on the edge; off hides them. */
	public boolean minimapEdgeWaypoints = true;
	public int borderColor = 0xFF1A1A1A;

	// Map
	public boolean caveMode = true;
	public boolean netherUnderRoof = true;
	public boolean heightShading = true;
	public boolean showGrid = false;
	/** The map opens out of the minimap, new waypoints drop in, edge arrows pulse when a waypoint leaves the minimap. */
	public boolean mapAnimations = true;
	/** Whether the layer sidebar on the full map is open. */
	public boolean mapLayers = true;
	/** Map as a movable, see-through window instead of full screen; its place and size (-1 / 0 = default). */
	public boolean mapWindowed = false;
	public int mapWindowX = -1;
	public int mapWindowY = -1;
	public int mapWindowW = 0;
	public int mapWindowH = 0;
	public float mapWindowOpacity = 0.8f;

	// Waypoints
	public boolean waypoints = true;
	public boolean waypointBeams = true;
	public boolean waypointLabels = true;
	public int waypointLabelRange = 2000;
	public boolean deathWaypoints = true;
	public int maxDeathWaypoints = 3;
	/** Remember what was dropped at a death, with a despawn timer, shown in the death waypoint's tooltip. */
	public boolean deathInventory = true;
	/** Toasts when the dropped items are about to despawn, have despawned or are all back. */
	public boolean deathWarning = true;
	public int deathWarnSeconds = 60;
	public boolean respawnWaypoint = true;
	public boolean chatWaypoints = true;
	public boolean importXaero = true;

	// Locator bar
	public boolean locatorWaypoints = true;
	public boolean locatorHeads = true;
	public boolean locatorNames = true;

	// Seed
	public boolean seedTerrain = true;
	public float unexploredBrightness = 0.45f;
	/** Seed for servers (entered by hand or picked up from /seed), keyed by world key. */
	public java.util.Map<String, Long> serverSeeds = new java.util.HashMap<>();

	// Structures
	public boolean structures = true;
	public boolean structuresFromSeed = true;
	public boolean structuresDetected = true;
	public int structureRadius = 4000;
}
