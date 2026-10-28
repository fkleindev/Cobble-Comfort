package dev.cobblecomfort.features.map;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.data.MapLayer;
import dev.cobblecomfort.features.map.data.BlockColorCache;
import dev.cobblecomfort.features.map.data.MapRegion;
import dev.cobblecomfort.features.map.render.SeedTextures;
import dev.cobblecomfort.features.map.seed.SeedGenerator;
import dev.cobblecomfort.features.map.seed.SeedTerrain;
import dev.cobblecomfort.features.map.structure.DetectedStructures;
import dev.cobblecomfort.features.map.structure.StructureFinder;
import dev.cobblecomfort.features.map.sync.PositionSync;
import dev.cobblecomfort.features.map.structure.StructureMarker;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.contents.TranslatableContents;
import dev.cobblecomfort.features.map.data.MapWorld;
import dev.cobblecomfort.features.map.data.MovementTrail;
import dev.cobblecomfort.features.map.render.EntityHeads;
import dev.cobblecomfort.features.map.render.MapMarkers;
import dev.cobblecomfort.features.map.render.MapUi;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import dev.cobblecomfort.features.map.render.MinimapRenderer;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.map.screen.WaypointEditScreen;
import dev.cobblecomfort.features.map.screen.WaypointListScreen;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import dev.cobblecomfort.features.map.waypoint.WaypointStore;
import dev.cobblecomfort.features.map.waypoint.RespawnTracker;
import dev.cobblecomfort.features.map.waypoint.XaeroImport;
import dev.cobblecomfort.features.map.waypoint.XaeroChat;
import dev.cobblecomfort.features.furnace.FurnaceEntry;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.cobblecomfort.features.storage.StoredContainer;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** World map (M), minimap, waypoints with beams, death points and Xaero chat/import compatibility. */
public final class MapFeature extends Feature<MapConfig> {
	public static final MapFeature INSTANCE = new MapFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("minimap");

	private static final int MARGIN = 4;
	private static final int TEXT_LINE = 10;
	/** Width of the minimap frame in GUI pixels (as drawn by MinimapRenderer). */
	private static final int FRAME = 3;
	private static final int MINIMAP_HEAD = 6;
	/** Height difference in blocks at which mobs start to fade (until the height range), and how faint they get. */
	private static final double FADE_START = 3;
	private static final float FADE_MIN = 0.3f;
	private static final float LABEL_TEXT = 0.7f;
	/** Waypoints outside the minimap, clamped to its edge, are drawn this much smaller and fainter. */
	private static final float FAR_WAYPOINT_SCALE = 0.75f;
	private static final float FAR_WAYPOINT_ALPHA = 0.75f;
	private static final float BEAM_HALF_WIDTH = 0.15f;
	private static final float BEAM_HEIGHT = 256f;
	/** Labels further than this are drawn at this distance in the waypoint's direction (gizmos fade far away). */
	private static final double LABEL_MAX_DRAW = 48.0;
	private static final float LABEL_SCALE = 0.6f;

	private final MapWorld world = new MapWorld();
	private final WaypointStore waypoints = new WaypointStore();
	private final RespawnTracker respawn = new RespawnTracker(waypoints);
	private final MinimapRenderer minimap = new MinimapRenderer();
	private final MovementTrail trail = new MovementTrail();
	private final PositionSync sync = new PositionSync();
	private KeyMapping mapKey;
	private KeyMapping waypointKey;
	private KeyMapping listKey;
	private boolean deathRecorded;
	private final SeedTextures seedTextures = new SeedTextures();
	private final java.util.Map<ResourceKey<Level>, SeedTerrain> terrains = new java.util.HashMap<>();
	private final java.util.Map<ResourceKey<Level>, Long> terrainSeeds = new java.util.HashMap<>();
	private final DetectedStructures detected = new DetectedStructures();
	private final StructureFinder finder = new StructureFinder();
	private SeedGenerator structureGenerator;

	private MapFeature() {
		super("map", MapConfig.class, MapConfig::new);
	}

	@Override
	public void init() {
		mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cobblecomfort.map", InputConstants.KEY_M, CobbleComfortClient.keyCategory()));
		waypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cobblecomfort.new_waypoint", InputConstants.KEY_B, CobbleComfortClient.keyCategory()));
		listKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cobblecomfort.waypoints", InputConstants.KEY_U, CobbleComfortClient.keyCategory()));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			world.join();
			waypoints.load();
			detected.load(world.worldKey());
			finder.clear();
			if (config.importXaero && !waypoints.xaeroImported()) {
				XaeroImport.importInto(waypoints, world.worldKey());
			}
		});
		sync.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			mc.execute(sync::clear);
			world.leave();
			waypoints.unload();
			detected.unload();
			trail.clear();
			finder.clear();
			structureGenerator = null;
			// Disconnects arrive on the network thread; textures may only be released on the render thread.
			mc.execute(() -> {
				minimap.close();
				MapScreen.releaseTextures();
				seedTextures.clear();
				for (SeedTerrain terrain : terrains.values()) {
					if (terrain != null) {
						terrain.close();
					}
				}
				terrains.clear();
				terrainSeeds.clear();
				EntityHeads.clear();
			});
		});
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			if (isEnabled()) {
				world.chunkLoaded(chunk);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, LAYER_ID, (g, delta) -> drawMinimap(g));
		XaeroChat.register();
		world.setScanListener((chunk, level) -> {
			if (config.structures && config.structuresDetected) {
				detected.scan(chunk, dimensionId(level));
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			captureSeed(message);
			if (isEnabled() && config.waypoints && config.respawnWaypoint) {
				respawn.onMessage(Minecraft.getInstance(), message);
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return MapConfigUi.buildCategory(this);
	}

	public MapWorld world() {
		return world;
	}

	public PositionSync sync() {
		return sync;
	}

	public MovementTrail trail() {
		return trail;
	}

	public WaypointStore waypoints() {
		return waypoints;
	}

	/** Converts a world coordinate to a screen coordinate on the full map. */
	@FunctionalInterface
	public interface Projection {
		float apply(double world);
	}

	public boolean isMapKey(net.minecraft.client.input.KeyEvent event) {
		return mapKey.matches(event);
	}

	// ---------------------------------------------------------------- structures

	private void updateStructures(Minecraft mc, LocalPlayer player) {
		if (!config.structures || !config.structuresFromSeed || mc.level == null) {
			return;
		}
		String dim = dimensionId(mc.level);
		Long seed = knownSeed();
		if (seed == null || !finder.needsUpdate(player.getBlockX(), player.getBlockZ(), dim, seed, config.structureRadius)) {
			return;
		}
		var server = mc.getSingleplayerServer();
		if (server != null) {
			var level = server.getLevel(mc.level.dimension());
			if (level != null) {
				finder.startExact(server, level, player.getBlockX(), player.getBlockZ(), config.structureRadius, dim);
			}
			return;
		}
		if (structureGenerator == null || structureGenerator.seed != seed || structureGenerator.dimension != mc.level.dimension()) {
			structureGenerator = SeedGenerator.create(seed, mc.level.dimension());
		}
		if (structureGenerator != null) {
			finder.startSeed(structureGenerator, player.getBlockX(), player.getBlockZ(), config.structureRadius, dim);
		}
	}

	/** All structure markers for the current dimension (calculated and detected). */
	public List<StructureMarker> structures(ClientLevel level) {
		return structures(dimensionId(level), true);
	}

	/** Structure markers of a dimension; calculated ones only exist for the dimension the player is in. */
	public List<StructureMarker> structures(String dimension, boolean current) {
		if (!config.structures) {
			return List.of();
		}
		List<StructureMarker> list = current ? new ArrayList<>(finder.markers()) : new ArrayList<>();
		if (config.structuresDetected) {
			list.addAll(detected.markers(dimension));
		}
		return list;
	}

	// ---------------------------------------------------------------- seed

	/** The seed of the current world: from the integrated server, or stored for this server (/seed or entered). */
	public Long knownSeed() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSingleplayerServer() != null) {
			return mc.getSingleplayerServer().overworld().getSeed();
		}
		return world.worldKey() == null ? null : config.serverSeeds.get(world.worldKey());
	}

	public void setServerSeed(long seed) {
		if (world.worldKey() != null) {
			config.serverSeeds.put(world.worldKey(), seed);
			save();
		}
	}

	/** Seed terrain for the current dimension; null when off or unknown. */
	public SeedTerrain terrain() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? null : terrain(mc.level.dimension());
	}

	/** Seed terrain for a dimension, (re)created when the seed changes; null when off or unknown. */
	public SeedTerrain terrain(ResourceKey<Level> dimension) {
		Minecraft mc = Minecraft.getInstance();
		Long seed = knownSeed();
		if (!config.seedTerrain || seed == null || mc.level == null || world.worldKey() == null) {
			return null;
		}
		// In singleplayer we can see whether the world uses normal generation (not flat, not modded).
		if (mc.getSingleplayerServer() != null) {
			var serverLevel = mc.getSingleplayerServer().getLevel(dimension);
			if (serverLevel == null || !(serverLevel.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)) {
				return null;
			}
		}
		SeedTerrain terrain = terrains.get(dimension);
		// Remember the seed tried even if there is no terrain for it, so a dimension without one is not retried each frame.
		if (!seed.equals(terrainSeeds.get(dimension))) {
			if (terrain != null) {
				terrain.close();
			}
			SeedGenerator generator = SeedGenerator.create(seed, dimension);
			terrain = generator == null ? null : new SeedTerrain(generator, SeedTerrain.worldFolder(world.worldKey()));
			terrains.put(dimension, terrain);
			terrainSeeds.put(dimension, seed);
		}
		return terrain;
	}

	/** Draws the seed terrain of unexplored regions under the explored map (full map), faded to {@code alpha}. */
	public void drawUnexplored(GuiGraphicsExtractor g, ResourceKey<Level> dimension, int minRx, int maxRx, int minRz, int maxRz,
			Projection screenX, Projection screenY, float alpha) {
		SeedTerrain current = terrain(dimension);
		if (current == null) {
			return;
		}
		seedTextures.beginFrame();
		for (int rz = minRz; rz <= maxRz; rz++) {
			for (int rx = minRx; rx <= maxRx; rx++) {
				SeedTerrain.Region region = current.region(rx, rz);
				Identifier texture = region == null ? null : seedTextures.texture(region, config.unexploredBrightness);
				if (texture == null) {
					continue;
				}
				int x0 = Math.round(screenX.apply((double) rx * MapRegion.SIZE));
				int y0 = Math.round(screenY.apply((double) rz * MapRegion.SIZE));
				int x1 = Math.round(screenX.apply((double) (rx + 1) * MapRegion.SIZE));
				int y1 = Math.round(screenY.apply((double) (rz + 1) * MapRegion.SIZE));
				MapUi.blitFaded(g, texture, x0, y0, x1, y1, SeedTerrain.SAMPLES, alpha);
			}
		}
	}

	/** Colour of an unexplored block (darkened seed terrain), or 0. */
	public int unexploredColor(int bx, int bz) {
		SeedTerrain current = terrain();
		if (current == null) {
			return 0;
		}
		int color = current.colorAt(bx, bz);
		return color == 0 ? 0 : BlockColorCache.scale(color, config.unexploredBrightness);
	}

	private void captureSeed(Component message) {
		if (message.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals("commands.seed.success")
				&& translatable.getArgs().length > 0) {
			String text = translatable.getArgs()[0] instanceof Component c ? c.getString() : String.valueOf(translatable.getArgs()[0]);
			try {
				setServerSeed(Long.parseLong(text.replaceAll("[^0-9-]", "")));
			} catch (NumberFormatException ignored) {
				// Not a plain number; nothing to store.
			}
		}
	}

	public static String dimensionId(ClientLevel level) {
		return level.dimension().identifier().toString();
	}

	// ---------------------------------------------------------------- tick

	private void tick(Minecraft mc) {
		sync.tick(isEnabled() && config.sharePosition);
		if (!isEnabled()) {
			return;
		}
		world.tick(mc, config);
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		while (mapKey.consumeClick()) {
			mc.gui.setScreen(new MapScreen(null));
		}
		while (waypointKey.consumeClick()) {
			if (config.waypoints) {
				Waypoint waypoint = new Waypoint("", player.getBlockX(), player.getBlockY(), player.getBlockZ(), dimensionId(mc.level));
				mc.gui.setScreen(new WaypointEditScreen(null, waypoint, true));
			}
		}
		while (listKey.consumeClick()) {
			if (config.waypoints) {
				mc.gui.setScreen(new WaypointListScreen(null));
			}
		}
		trail.tick(dimensionId(mc.level), player.getX(), player.getZ());
		recordDeath(mc, player);
		if (config.waypoints && config.respawnWaypoint) {
			respawn.tick(mc, player, dimensionId(mc.level), mc.gui.screen() instanceof DeathScreen || player.isDeadOrDying());
		}
		updateStructures(mc, player);
		drawWaypointsInWorld(mc, player);
	}

	private void recordDeath(Minecraft mc, LocalPlayer player) {
		boolean dead = mc.gui.screen() instanceof DeathScreen || player.isDeadOrDying();
		if (dead && !deathRecorded && config.deathWaypoints && waypoints.loaded()) {
			deathRecorded = true;
			Waypoint death = new Waypoint(Component.translatable("cobblecomfort.map.death_name").getString(),
					player.getBlockX(), player.getBlockY(), player.getBlockZ(), dimensionId(mc.level));
			death.color = 12;
			death.initials = "✝";
			waypoints.addDeath(death, config.maxDeathWaypoints);
		} else if (!dead) {
			deathRecorded = false;
		}
	}

	/** Beams and name/distance labels at waypoints, drawn as per-tick gizmos. */
	private void drawWaypointsInWorld(Minecraft mc, LocalPlayer player) {
		if (!config.waypoints || (!config.waypointBeams && !config.waypointLabels) || !waypoints.loaded()) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
			for (Waypoint waypoint : waypoints.visible(dimensionId(mc.level))) {
				double wx = waypoint.x + 0.5;
				double wz = waypoint.z + 0.5;
				double wy = waypoint.yOr(player.getY());
				double distance = Math.sqrt(player.distanceToSqr(wx, wy, wz));
				if (config.waypointBeams && distance > 2) {
					int color = waypoint.rgb();
					int fill = 0x50000000 | (color & 0xFFFFFF);
					Gizmos.cuboid(new AABB(wx - BEAM_HALF_WIDTH, wy, wz - BEAM_HALF_WIDTH, wx + BEAM_HALF_WIDTH, wy + BEAM_HEIGHT, wz + BEAM_HALF_WIDTH),
							GizmoStyle.fill(fill));
				}
				if (config.waypointLabels && distance <= config.waypointLabelRange) {
					Vec3 target = new Vec3(wx, wy + 1.5, wz);
					Vec3 offset = target.subtract(eye);
					double length = offset.length();
					Vec3 at = length > LABEL_MAX_DRAW ? eye.add(offset.scale(LABEL_MAX_DRAW / length)) : target;
					String text = waypoint.name + " (" + formatDistance(distance) + ")";
					Gizmos.billboardText(text, at, TextGizmo.Style.forColorAndCentered(waypoint.rgb()).withScale(LABEL_SCALE));
				}
			}
		}
	}

	public static String formatDistance(double blocks) {
		return blocks >= 1000 ? String.format(Locale.ROOT, "%.1fk", blocks / 1000) : Math.round(blocks) + "m";
	}

	// ---------------------------------------------------------------- minimap

	public Rect defaultBounds(float width, float height) {
		int size = config.minimapSize;
		int text = (config.minimapCoords ? TEXT_LINE : 0) + (config.minimapBiome ? TEXT_LINE : 0);
		return new Rect(width - size - MARGIN, MARGIN, size, size + text + (text > 0 ? 2 : 0));
	}

	/** Width the minimap takes at the top right, so other top-right elements can step aside. */
	public int topRightReserved() {
		return isEnabled() && config.minimap ? config.minimapSize + MARGIN * 2 : 0;
	}

	private void drawMinimap(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!isEnabled() || !config.minimap || player == null || level == null || !world.ready()
				|| mc.debugEntries.isOverlayVisible()) {
			return;
		}
		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		int size = config.minimapSize;
		int x0 = Math.round(bounds.x());
		int y0 = Math.round(bounds.y());
		MapLayer layer = world.active(level);
		float yaw = player.getYRot();
		SeedTerrain current = terrain();
		int version = world.scanCount() * 31 + (current == null ? 0 : current.version());
		minimap.update(config, layer, this::unexploredColor, player.getX(), player.getZ(), yaw, size, version);
		g.blit(minimap.texture(), x0, y0, x0 + size, y0 + size, 0f, 1f, 0f, 1f);
		float cx = x0 + size / 2f;
		float cy = y0 + size / 2f;
		float zoom = config.minimapZoom;
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad);
		double rx = -Math.cos(rad), rz = -Math.sin(rad);
		float radius = size / 2f - FRAME;
		Font font = mc.font;
		if (config.movementTrail) {
			MapMarkers.trail(g, trail.points(dimensionId(level)), player.getX(), player.getZ(), (wx, wz) -> {
				float[] s = toScreen(wx - player.getX(), wz - player.getZ(), fx, fz, rx, rz);
				return inside(s[0] * zoom, s[1] * zoom, radius - 1) ? new float[]{cx + s[0] * zoom, cy + s[1] * zoom} : null;
			}, 1.5f);
		}
		// Entities: animals first, then monsters, then players on top.
		if (config.showEntities) {
			double range = radius / zoom;
			List<Entity> shown = new ArrayList<>();
			for (Entity entity : level.entitiesForRendering()) {
				double ox = entity.getX() - player.getX();
				double oz = entity.getZ() - player.getZ();
				if (entity != player && ox * ox + oz * oz <= range * range * 2 && entityAlpha(entity, player) > 0
						&& MapMarkers.entityColor(entity, config.showPlayers, config.showHostiles, config.showPassives) != 0) {
					shown.add(entity);
				}
			}
			shown.sort(java.util.Comparator.comparingInt(MapFeature::entityLayer));
			for (Entity entity : shown) {
				float[] s = toScreen(entity.getX() - player.getX(), entity.getZ() - player.getZ(), fx, fz, rx, rz);
				float sx = s[0] * zoom;
				float sy = s[1] * zoom;
				if (inside(sx, sy, radius - 3)) {
					int color = MapMarkers.entityColor(entity, config.showPlayers, config.showHostiles, config.showPassives);
					MapMarkers.entity(g, entity, cx + sx, cy + sy, color, config.mobHeads, MINIMAP_HEAD, entityAlpha(entity, player));
				}
			}
			// Players shared by the server plugin that are too far away to be loaded: outside the map at its edge.
			if (config.showPlayers) {
				for (PositionSync.Shown remote : sync.players(dimensionId(level))) {
					if (level.getPlayerByUUID(remote.data().uuid()) != null) {
						continue;
					}
					float[] offset = toScreen(remote.x() - player.getX(), remote.z() - player.getZ(), fx, fz, rx, rz);
					boolean far = !inside(offset[0] * zoom, offset[1] * zoom, radius - 4);
					float[] s = clampToEdge(offset, zoom, radius - 4);
					MapMarkers.remotePlayer(g, remote.data().uuid(), cx + s[0], cy + s[1], config.mobHeads, far ? MINIMAP_HEAD - 1 : MINIMAP_HEAD,
							far ? FAR_WAYPOINT_ALPHA : 1f);
				}
			}
		}
		// Remembered furnaces within the minimap, ringed in their status colour.
		for (FurnaceEntry entry : FurnaceFeature.INSTANCE.mapEntries(dimensionId(level), true)) {
			float[] offset = toScreen(entry.x + 0.5 - player.getX(), entry.z + 0.5 - player.getZ(), fx, fz, rx, rz);
			if (inside(offset[0] * zoom, offset[1] * zoom, radius - 6)) {
				MapMarkers.badge(g, FurnaceFeature.blockIcon(entry), cx + offset[0] * zoom, cy + offset[1] * zoom, true, FurnaceFeature.color(entry));
			}
		}
		// Remembered containers that are being searched for: at their place, outside clamped to the edge.
		int highlight = StorageFeature.INSTANCE.config().outlineColor;
		for (StoredContainer container : StorageFeature.INSTANCE.mapContainers(dimensionId(level))) {
			if (!StorageFeature.INSTANCE.isHighlighted(container)) {
				continue;
			}
			Vec3 center = container.center();
			float[] s = clampToEdge(toScreen(center.x - player.getX(), center.z - player.getZ(), fx, fz, rx, rz), zoom, radius - 7);
			MapMarkers.badge(g, container.icon(), cx + s[0], cy + s[1], true, highlight);
		}
		// Waypoints: markers at their place; outside clamped to the edge (the tip points at the edge), smaller, fainter
		// and with their distance, so they stand apart from the ones that are really there.
		if (config.waypoints && waypoints.loaded()) {
			for (Waypoint waypoint : waypoints.visible(dimensionId(level))) {
				double ox = waypoint.x + 0.5 - player.getX();
				double oz = waypoint.z + 0.5 - player.getZ();
				float[] offset = toScreen(ox, oz, fx, fz, rx, rz);
				boolean far = !inside(offset[0] * zoom, offset[1] * zoom, radius - 4);
				float[] s = clampToEdge(offset, zoom, radius - 4);
				float x = cx + s[0];
				float y = cy + s[1];
				MapMarkers.waypoint(g, font, waypoint, x, y, far ? FAR_WAYPOINT_ALPHA : 1f, true, far ? FAR_WAYPOINT_SCALE : 1f);
				if (far) {
					// Below the tip in the upper half, above the marker in the lower half, so it stays on the map.
					int markerHeight = Math.round(MapMarkers.waypointHeight(true) * FAR_WAYPOINT_SCALE);
					int ty = s[1] <= 0 ? Math.round(y) + 1 : Math.round(y) - markerHeight - 10;
					MapUi.label(g, font, Component.literal(formatDistance(Math.sqrt(ox * ox + oz * oz))), Math.round(x), ty,
							RenderUtil.withAlpha(MapUi.TEXT, FAR_WAYPOINT_ALPHA), 0.5f, 0x90101318);
				}
			}
		}
		// Player arrow with its view cone
		float arrowAngle = config.rotateMinimap ? 0f : (float) (rad + Math.PI);
		MapMarkers.arrow(g, cx, cy, arrowAngle, 0xFFFFFFFF, config.viewCone, true);
		drawCardinals(g, font, cx, cy, size, fx, fz, rx, rz);
		// Text below, each line in a small pill
		int ty = y0 + size + 2;
		if (config.minimapCoords) {
			String coords = player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
			label(g, font, Component.literal(coords), Math.round(cx), ty, MapUi.TEXT);
			ty += TEXT_LINE;
		}
		if (config.minimapBiome) {
			int biomeY = ty;
			level.getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> label(g, font,
					Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()),
					Math.round(cx), biomeY, MapUi.TEXT_DIM));
		}
	}

	/**
	 * How visible a marker is by height: mobs further above or below the player than the height range are hidden
	 * (0), closer ones fade towards the edge of the range if enabled. Players are never hidden, only faded.
	 */
	public float entityAlpha(Entity entity, LocalPlayer player) {
		double difference = Math.abs(entity.getY() - player.getY());
		boolean isPlayer = entity instanceof net.minecraft.world.entity.player.Player;
		if (difference > config.entityHeightRange && !isPlayer) {
			return 0f;
		}
		if (!config.heightFade) {
			return 1f;
		}
		double range = Math.max(1, config.entityHeightRange - FADE_START);
		float t = (float) Math.clamp((difference - FADE_START) / range, 0.0, 1.0);
		return 1f - t * (1f - FADE_MIN);
	}

	/** Drawing order of entities on the maps: animals below monsters below players. */
	public static int entityLayer(Entity entity) {
		return entity instanceof net.minecraft.world.entity.player.Player ? 2 : entity instanceof net.minecraft.world.entity.monster.Enemy ? 1 : 0;
	}

	/** N (and E, S, W if enabled) as small badges sitting on the frame. */
	private void drawCardinals(GuiGraphicsExtractor g, Font font, float cx, float cy, int size, double fx, double fz, double rx, double rz) {
		String[] letters = {"N", "E", "S", "W"};
		int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		float distance = size / 2f - FRAME / 2f;
		int count = config.minimapCardinals ? 4 : 1;
		for (int i = 0; i < count; i++) {
			float[] s = toScreen(directions[i][0], directions[i][1], fx, fz, rx, rz);
			float length = (float) Math.hypot(s[0], s[1]);
			float dx = s[0] / length;
			float dy = s[1] / length;
			if (!config.roundMinimap) {
				// On a square map the letters sit on the square's edge.
				float stretch = 1f / Math.max(Math.abs(dx), Math.abs(dy));
				dx *= stretch;
				dy *= stretch;
			}
			int x = Math.round(cx + dx * distance);
			int y = Math.round(cy + dy * distance);
			boolean north = i == 0;
			RenderUtil.roundedFill(g, x - 4, y - 4, x + 4, y + 4, 3, north ? 0xF0B03030 : 0xE0181B22);
			MapUi.centeredText(g, font, Component.literal(letters[i]), x, y, north ? 0xFFFFFFFF : 0xFFC8CCD6, 0.6f);
		}
	}

	/** A line of small text in a dark pill, centred on cx. */
	private static void label(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color) {
		MapUi.label(g, font, text, cx, y, color, LABEL_TEXT, 0xC0101318);
	}

	/** Scales a minimap offset by the zoom and pulls it to the edge if it is outside. */
	private float[] clampToEdge(float[] offset, float zoom, float edge) {
		float sx = offset[0] * zoom;
		float sy = offset[1] * zoom;
		if (!inside(sx, sy, edge)) {
			float scale = edgeScale(sx, sy, edge);
			sx *= scale;
			sy *= scale;
		}
		return new float[]{sx, sy};
	}

	/** World offset to minimap offset in blocks (screen axes), honouring rotation. */
	private float[] toScreen(double ox, double oz, double fx, double fz, double rx, double rz) {
		if (!config.rotateMinimap) {
			return new float[]{(float) ox, (float) oz};
		}
		return new float[]{(float) (ox * rx + oz * rz), (float) -(ox * fx + oz * fz)};
	}

	private boolean inside(float sx, float sy, float radius) {
		return config.roundMinimap ? sx * sx + sy * sy <= radius * radius : Math.abs(sx) <= radius && Math.abs(sy) <= radius;
	}

	private float edgeScale(float sx, float sy, float radius) {
		if (config.roundMinimap) {
			return radius / (float) Math.hypot(sx, sy);
		}
		return radius / Math.max(Math.abs(sx), Math.abs(sy));
	}
}
