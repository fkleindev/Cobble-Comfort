package dev.cobblecomfort.features.map.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.MapConfig;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.data.MapLayer;
import dev.cobblecomfort.features.map.data.MapRegion;
import dev.cobblecomfort.features.map.data.MapWorld;
import dev.cobblecomfort.features.map.render.MapMarkers;
import dev.cobblecomfort.features.map.render.WaypointAnim;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.map.render.MapUi.Dim;
import dev.cobblecomfort.features.map.render.RegionTextures;
import dev.cobblecomfort.features.map.structure.StructureIcons;
import dev.cobblecomfort.features.map.structure.StructureMarker;
import dev.cobblecomfort.features.map.sync.PositionSync;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import dev.cobblecomfort.features.map.waypoint.WaypointStore;
import dev.cobblecomfort.features.map.death.DeathRecord;
import dev.cobblecomfort.features.map.death.DeathTooltip;
import dev.cobblecomfort.features.furnace.FurnaceEntry;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.pets.PetEntry;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.trade.TradeFeature;
import dev.cobblecomfort.features.trade.TradeMerchant;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.cobblecomfort.features.storage.StorageScreen;
import dev.cobblecomfort.features.tracker.TrackerFeature;
import dev.cobblecomfort.features.tracker.TrackerScreen;
import dev.cobblecomfort.features.hud.HudCustomizerFeature;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.core.config.CobbleConfigScreen;
import dev.cobblecomfort.features.storage.StorageItems;
import dev.cobblecomfort.features.storage.StoredContainer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The full world map, full screen or as a movable, resizable, see-through window. Drag or the arrow keys to pan,
 * scroll to zoom towards the cursor, right click for a menu, double click for a waypoint, C to follow the player
 * again, Tab to switch dimension. WASD, jump, sneak and sprint still move the player. Tabs at the top show the same
 * place in the other dimensions (nether coordinates are an eighth of the overworld's).
 */
public class MapScreen extends Screen {
	private static final float MIN_ZOOM = 0.03125f;
	private static final float MAX_ZOOM = 16f;
	private static final float ZOOM_STEP = 1.25f;
	private static final float PAN_SPEED = 400f;
	private static final int BACKGROUND = 0xFF0B0B0F;
	private static final int VIGNETTE = 0x70000000;
	private static final int MENU_WIDTH = 140;
	private static final int MENU_ROW = 16;
	private static final int TAB_HEIGHT = 20;
	private static final int TOOL = 20;
	private static final int TOOLBAR_HEIGHT = 26;
	private static final int SCALE_BAR_TARGET = 60;
	private static final int MAP_HEAD = 8;
	/** Window mode: title bar height, resize grip size, smallest size and default share of the screen. */
	private static final int TITLE_BAR = 14;
	private static final int GRIP = 8;
	private static final int MIN_WINDOW_WIDTH = 300;
	private static final int MIN_WINDOW_HEIGHT = 180;
	private static final float DEFAULT_WINDOW = 0.65f;
	/** Layer sidebar: width, row height and header height. */
	private static final int LAYERS_WIDTH = 124;
	private static final int LAYER_ROW = 13;
	private static final int LAYERS_HEADER = 16;
	private static final int TRADER_TOOLTIP_OFFERS = 6;
	private static final RegionTextures TEXTURES = new RegionTextures("full");
	private static float lastZoom = 2f;
	private static final float OPEN_SECONDS = 0.4f;
	private static final float CLOSE_SECONDS = 0.35f;
	/** Pets: how much and how slowly living ones breathe; graves younger than this rise from dust when first seen. */
	private static final float PET_BREATH = 0.06f;
	private static final float PET_BREATH_SECONDS = 2.6f;
	private static final long FRESH_GRAVE_MS = 10 * 60 * 1000L;
	private static final long DUST_MS = 900;
	private static final int DUST_PUFFS = 10;
	/** When each fresh grave was first drawn this session. */
	private static final Map<String, Long> GRAVES_SHOWN = new HashMap<>();

	private final Screen parent;
	private double centerX, centerZ;
	private boolean centerGiven;
	private float zoom = lastZoom;
	private boolean dragging;
	/** Whether the view stays centred on the player; panning stops it, C or the compass button resume it. */
	private boolean following;
	private long lastFrameNanos;
	private ContextMenu menu;
	/** The dimension shown, and the one the player is in. */
	private String viewDim;
	private String playerDim;
	private final List<String> tabs = new ArrayList<>();
	private final List<Tool> tools = new ArrayList<>();
	private int tabsLeft, tabsWidth, tabsTop;
	private int toolbarLeft, toolbarRight, toolbarTop;
	/** The window (window mode; the whole screen otherwise) and the map area inside it. */
	private int windowX, windowY, windowW, windowH;
	private int mapX, mapY, mapW, mapH;
	/** Moving or resizing the window: what is being done and where the mouse and window were when it started. */
	private WindowDrag windowDrag = WindowDrag.NONE;
	private double dragMouseX, dragMouseY;
	private int dragX, dragY, dragW, dragH;
	/** Movement keys passed on to the player while the map is open, and whether each is held. */
	private final Map<KeyMapping, Boolean> passedKeys = new HashMap<>();
	/** The navigation bar to the mod's other screens, top left, and its top edge. */
	private final List<Nav> nav = new ArrayList<>();
	private int navTop;
	/** The layer sidebar's place (set while drawing) and how far its list is scrolled. */
	private int layersX0, layersX1, layersTop, layersBottom;
	private final SmoothScroll layersScroll = new SmoothScroll(SmoothScroll.Kind.MOD_SCREENS);
	/** Depth slider: the layer chosen by hand (null = follow the player), its place, whether it is dragged and its cached list. */
	private Integer manualLayer;
	private int sliderX0, sliderX1, sliderTop, sliderBottom;
	private boolean sliderDragging;
	private List<Integer> layerIds = List.of(MapWorld.SURFACE);
	private String layerIdsDim;
	private long layerIdsMs;
	/** Opening out of the minimap: its place on screen (null when not opening that way), its turn, and the start. */
	private Rect openFrom;
	private boolean openChecked;
	private float openRotation;
	private long openStartNanos;
	/** Closing back into the minimap: when it started (0 = not closing) and where the view was then. */
	private long closeStartNanos;
	private double closeFromX, closeFromZ;

	private enum WindowDrag {
		NONE, MOVE, RESIZE
	}

	/** The right-click menu: its place on screen, the world position it was opened at and its entries. */
	private record ContextMenu(int x, int y, int worldX, int worldZ, List<Entry> entries) {
	}

	private record Entry(ItemStack icon, Component label, Runnable action) {
	}

	/** A button of the navigation bar on the left: another screen of the mod, opened in place of the map. */
	private record Nav(ItemStack icon, Component tooltip, String key, Runnable action) {
	}

	/** A round icon button in the toolbar. */
	private record Tool(int x, ItemStack icon, Component tooltip, Runnable action, BooleanSupplier selected) {
	}

	/**
	 * A row of the layer sidebar: indented by depth, with a count (-1 = none); not active when its parent layer is
	 * off, so its switch has no effect.
	 */
	private record Layer(Component label, int depth, LayerIcon icon, int count, boolean active, BooleanSupplier shown, Runnable toggle) {
	}

	/** A player under the cursor: loaded nearby or only shared by the server. Health is -1 when unknown. */
	private record HoveredPlayer(UUID uuid, Component name, float health, float maxHealth, int armor, double x, double y, double z) {
	}

	@FunctionalInterface
	private interface LayerIcon {
		void draw(GuiGraphicsExtractor g, int cx, int cy);
	}

	/** Frees the region textures (on leaving a world). */
	public static void releaseTextures() {
		TEXTURES.clear();
	}

	public MapScreen(Screen parent) {
		super(Component.translatable("cobblecomfort.map.title"));
		this.parent = parent;
	}

	/** Opens the map centred on a world position. */
	public MapScreen(Screen parent, double x, double z) {
		this(parent);
		centerX = x;
		centerZ = z;
		centerGiven = true;
	}

	/** Opens the map centred on a world position in a dimension (null = the player's). */
	public MapScreen(Screen parent, double x, double z, String dimension) {
		this(parent, x, z);
		viewDim = dimension == null || dimension.isEmpty() ? null : dimension;
	}

	@Override
	protected void init() {
		opensFromMinimap();
		if (minecraft.level != null) {
			playerDim = MapFeature.dimensionId(minecraft.level);
			if (viewDim == null) {
				viewDim = playerDim;
			}
		}
		if (!centerGiven) {
			following = true;
		}
		if (minecraft.player != null && !centerGiven) {
			centerX = minecraft.player.getX();
			centerZ = minecraft.player.getZ();
			centerGiven = true;
		}
		tabs.clear();
		for (Dim dim : Dim.values()) {
			tabs.add(dim.id);
		}
		if (playerDim != null && Dim.of(playerDim) == null) {
			tabs.add(playerDim);
		}
		tabsWidth = 0;
		for (String tab : tabs) {
			tabsWidth += tabWidth(tab);
		}
		tabsWidth += 4;
		layout();
	}

	/** Places the window (or the full screen), the map area inside it, the tabs and the toolbar. */
	private void layout() {
		MapConfig config = config();
		if (config.mapWindowed) {
			windowW = Math.min(width, Math.max(MIN_WINDOW_WIDTH, config.mapWindowW > 0 ? config.mapWindowW : Math.round(width * DEFAULT_WINDOW)));
			windowH = Math.min(height, Math.max(MIN_WINDOW_HEIGHT, config.mapWindowH > 0 ? config.mapWindowH : Math.round(height * DEFAULT_WINDOW)));
			windowX = Math.clamp(config.mapWindowX >= 0 ? config.mapWindowX : (width - windowW) / 2, 0, width - windowW);
			windowY = Math.clamp(config.mapWindowY >= 0 ? config.mapWindowY : (height - windowH) / 2, 0, height - windowH);
			mapX = windowX + 1;
			mapY = windowY + TITLE_BAR;
			mapW = windowW - 2;
			mapH = windowH - TITLE_BAR - 1;
		} else {
			windowX = mapX = 0;
			windowY = mapY = 0;
			windowW = mapW = width;
			windowH = mapH = height;
		}
		tabsLeft = mapX + (mapW - tabsWidth) / 2;
		tabsTop = mapY + 6;
		buildToolbar();
		buildNav();
	}

	private void toggleWindowed() {
		config().mapWindowed = !config().mapWindowed;
		MapFeature.INSTANCE.save();
		menu = null;
		layout();
	}

	/** How opaque the map itself is: see-through in window mode. */
	private float mapAlpha() {
		return config().mapWindowed ? Math.max(0.2f, config().mapWindowOpacity) : 1f;
	}

	private boolean inMap(double x, double y) {
		return x >= mapX && x < mapX + mapW && y >= mapY && y < mapY + mapH;
	}

	/**
	 * The map is the mod's main screen, so a column of buttons top left leads to its other screens. Screens of
	 * features that are switched off are left out; closing any of them goes back to the game.
	 */
	private void buildNav() {
		nav.clear();
		if (StorageFeature.INSTANCE.isEnabled()) {
			nav.add(new Nav(new ItemStack(Items.CHEST), Component.translatable("cobblecomfort.map.nav.storage"), "key.cobblecomfort.storage",
					() -> minecraft.gui.setScreen(new StorageScreen(null, ""))));
		}
		if (TradeFeature.INSTANCE.active()) {
			nav.add(new Nav(new ItemStack(Items.EMERALD), Component.translatable("cobblecomfort.map.nav.traders"), null,
					() -> minecraft.gui.setScreen(new StorageScreen(null, "").showTraders())));
		}
		if (PetFeature.INSTANCE.active()) {
			nav.add(new Nav(new ItemStack(Items.BONE), Component.translatable("cobblecomfort.map.nav.pets"), null,
					() -> minecraft.gui.setScreen(new StorageScreen(null, "").showPets())));
		}
		if (config().waypoints) {
			nav.add(new Nav(ItemStack.EMPTY, Component.translatable("cobblecomfort.map.button.waypoints"), "key.cobblecomfort.waypoints",
					() -> minecraft.gui.setScreen(new WaypointListScreen(null))));
		}
		TrackerFeature tracker = TrackerFeature.INSTANCE;
		if (tracker.isEnabled() && tracker.hasWorld()) {
			nav.add(new Nav(new ItemStack(Items.LODESTONE), Component.translatable("cobblecomfort.map.nav.tracker"), "key.cobblecomfort.tracker",
					() -> minecraft.gui.setScreen(new TrackerScreen(tracker, null))));
		}
		if (HudCustomizerFeature.INSTANCE.isEnabled()) {
			nav.add(new Nav(new ItemStack(Items.PAINTING), Component.translatable("cobblecomfort.map.nav.hud_editor"),
					"key.cobblecomfort.open_hud_editor", () -> minecraft.gui.setScreen(new HudEditorScreen(null))));
		}
		nav.add(new Nav(new ItemStack(Items.COMPARATOR), Component.translatable("cobblecomfort.map.nav.settings"), "key.cobblecomfort.open_config",
				() -> minecraft.gui.setScreen(CobbleConfigScreen.create(null))));
		navTop = tabsTop;
	}

	private int navRight() {
		return mapX + 6 + TOOL + 4;
	}

	private int navBottom() {
		return navTop + nav.size() * (TOOL + 2) + 2;
	}

	/** The navigation button under the cursor, or -1. */
	private int navAt(double x, double y) {
		int x0 = mapX + 8;
		if (x < x0 || x >= x0 + TOOL || y < navTop + 2 || y >= navBottom()) {
			return -1;
		}
		int index = (int) ((y - navTop - 2) / (TOOL + 2));
		return index < nav.size() && y < navTop + 2 + index * (TOOL + 2) + TOOL ? index : -1;
	}

	private void drawNav(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (nav.isEmpty()) {
			return;
		}
		MapUi.panel(g, mapX + 6, navTop, navRight(), navBottom());
		int accent = Dim.accentOf(viewDim);
		int hovered = menu == null ? navAt(mouseX, mouseY) : -1;
		for (int i = 0; i < nav.size(); i++) {
			int x = mapX + 8;
			int y = navTop + 2 + i * (TOOL + 2);
			MapUi.buttonFace(g, x, y, x + TOOL, y + TOOL, i == hovered, false, accent);
			drawIcon(g, nav.get(i).icon(), x + TOOL / 2, y + TOOL / 2, 14, accent);
		}
		if (hovered >= 0) {
			Nav button = nav.get(hovered);
			List<Component> lines = new ArrayList<>();
			lines.add(button.tooltip());
			KeyMapping key = keyMapping(button.key());
			if (key != null && !key.isUnbound()) {
				lines.add(key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY));
			}
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}

	private KeyMapping keyMapping(String name) {
		if (name == null) {
			return null;
		}
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping.getName().equals(name)) {
				return mapping;
			}
		}
		return null;
	}

	private void buildToolbar() {
		tools.clear();
		List<Tool> list = new ArrayList<>();
		list.add(new Tool(0, new ItemStack(Items.COMPASS), Component.translatable("cobblecomfort.map.button.center"), this::centerOnPlayer,
				() -> following));
		list.add(new Tool(0, new ItemStack(Items.GLASS_PANE), Component.translatable("cobblecomfort.map.button.window"), this::toggleWindowed,
				() -> config().mapWindowed));
		if (minecraft.getSingleplayerServer() == null) {
			list.add(new Tool(0, new ItemStack(Items.WHEAT_SEEDS), Component.translatable("cobblecomfort.map.button.seed"),
					() -> minecraft.gui.setScreen(new SeedScreen(this)), () -> false));
		}
		int scaleWidth = SCALE_BAR_TARGET * 2 + 40;
		int total = list.size() * (TOOL + 2) + 6 + scaleWidth + TOOL + 8;
		toolbarLeft = mapX + (mapW - total) / 2;
		toolbarRight = toolbarLeft + total;
		toolbarTop = mapY + mapH - TOOLBAR_HEIGHT - 6;
		int x = toolbarLeft + 4;
		for (Tool tool : list) {
			tools.add(new Tool(x, tool.icon(), tool.tooltip(), tool.action(), tool.selected()));
			x += TOOL + 2;
		}
	}

	private MapConfig config() {
		return MapFeature.INSTANCE.config();
	}

	private boolean viewingPlayerDim() {
		return viewDim != null && viewDim.equals(playerDim);
	}

	private static ResourceKey<Level> key(String dimension) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
	}

	/** Blocks in the viewed dimension per block where the player is. */
	private double playerToView() {
		return Dim.scaleOf(playerDim) / Dim.scaleOf(viewDim);
	}

	private void centerOnPlayer() {
		following = true;
		if (minecraft.player != null) {
			centerX = minecraft.player.getX() * playerToView();
			centerZ = minecraft.player.getZ() * playerToView();
		}
	}

	/** Shows another dimension at the same place (converted coordinates), keeping the zoom. */
	private void switchTo(String dimension) {
		if (dimension.equals(viewDim)) {
			return;
		}
		double factor = Dim.scaleOf(viewDim) / Dim.scaleOf(dimension);
		centerX *= factor;
		centerZ *= factor;
		viewDim = dimension;
		menu = null;
		manualLayer = null;
	}

	private double worldX(double screenX) {
		return centerX + (screenX - (mapX + mapW / 2.0)) / zoom;
	}

	private double worldZ(double screenY) {
		return centerZ + (screenY - (mapY + mapH / 2.0)) / zoom;
	}

	private float screenX(double worldX) {
		return (float) ((worldX - centerX) * zoom + mapX + mapW / 2.0);
	}

	private float screenY(double worldZ) {
		return (float) ((worldZ - centerZ) * zoom + mapY + mapH / 2.0);
	}

	/** Whether a screen point is near enough to the map area to be worth drawing something there. */
	private boolean onMap(float x, float y, float margin) {
		return x >= mapX - margin && y >= mapY - margin && x <= mapX + mapW + margin && y <= mapY + mapH + margin;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// In window mode the world stays visible around (and through) the window.
		if (!config().mapWindowed) {
			g.fill(0, 0, width, height, RenderUtil.withAlpha(BACKGROUND, openProgress()));
		}
	}

	/** The window around the map: a shadow, a title bar with the title and a close button. */
	private void drawWindow(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x1 = windowX + windowW;
		int y1 = windowY + windowH;
		RenderUtil.roundedFill(g, windowX - 1, windowY + 1, x1 + 1, y1 + 3, MapUi.RADIUS + 1, MapUi.SHADOW);
		RenderUtil.roundedFill(g, windowX, windowY, x1, y1, MapUi.RADIUS, MapUi.PANEL_SOLID);
		g.fill(windowX + MapUi.RADIUS, windowY, x1 - MapUi.RADIUS, windowY + 1, MapUi.PANEL_EDGE_TOP);
		g.fill(mapX, mapY, mapX + mapW, mapY + mapH, RenderUtil.withAlpha(BACKGROUND, mapAlpha()));
		boolean moving = windowDrag == WindowDrag.MOVE;
		g.text(font, title, windowX + 7, windowY + 3, moving ? MapUi.TEXT : MapUi.TEXT_DIM, false);
		boolean closeHovered = overClose(mouseX, mouseY);
		g.centeredText(font, "×", x1 - 8, windowY + 3, closeHovered ? MapUi.DANGER : MapUi.TEXT_DIM);
	}

	/** Three short diagonal lines in the bottom right corner of the window for resizing it. */
	private void drawGrip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int color = overGrip(mouseX, mouseY) || windowDrag == WindowDrag.RESIZE ? MapUi.TEXT : MapUi.TEXT_FAINT;
		int x1 = mapX + mapW - 2;
		int y1 = mapY + mapH - 2;
		for (int line = 0; line < 3; line++) {
			int length = 2 + line * 2;
			for (int i = 0; i < length; i++) {
				g.fill(x1 - i - 1, y1 - length + i, x1 - i, y1 - length + i + 1, color);
			}
		}
	}

	private boolean overTitleBar(double x, double y) {
		return config().mapWindowed && x >= windowX && x < windowX + windowW && y >= windowY && y < mapY;
	}

	private boolean overClose(double x, double y) {
		return overTitleBar(x, y) && x >= windowX + windowW - 16;
	}

	private boolean overGrip(double x, double y) {
		return config().mapWindowed && x >= mapX + mapW - GRIP && x < mapX + mapW + 1 && y >= mapY + mapH - GRIP && y < mapY + mapH + 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long nanos = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((nanos - lastFrameNanos) / 1e9f, 0.1f);
		lastFrameNanos = nanos;
		panWithKeys(dt);
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || viewDim == null) {
			return;
		}
		Vec3 position = player.getPosition(delta);
		if (following) {
			centerX = position.x * playerToView();
			centerZ = position.z * playerToView();
		}
		if (closeStartNanos != 0) {
			if (closeProgress() >= 1f) {
				minecraft.gui.setScreen(parent);
				return;
			}
			// Shrinking into the minimap: glide back to the player and turn the way the minimap faces right now.
			float back = 1f - openProgress();
			centerX = closeFromX + (position.x * playerToView() - closeFromX) * back;
			centerZ = closeFromZ + (position.z * playerToView() - closeFromZ) * back;
			openRotation = (float) Math.IEEEremainder(MapFeature.INSTANCE.minimapRotation(player.getViewYRot(delta)), Math.PI * 2);
		}
		MapFeature feature = MapFeature.INSTANCE;
		boolean here = viewingPlayerDim();
		MapLayer layer = feature.world().layer(key(viewDim), shownLayer());
		// Opening out of the minimap: the map (and its window) grows from there, turning north-up and zooming.
		float open = openProgress();
		float targetZoom = zoom;
		if (open < 1f) {
			beginOpening(g, open);
		}
		if (config().mapWindowed) {
			drawWindow(g, mouseX, mouseY);
		}
		int[] area = {mapX, mapY, mapW, mapH};
		if (open < 1f) {
			// Turned, the map alone would not fill the growing frame: draw more of it around the same centre.
			int grow = Math.max(mapW, mapH) / 2;
			mapX -= grow;
			mapY -= grow;
			mapW += 2 * grow;
			mapH += 2 * grow;
		}
		g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
		drawRegions(g, layer);
		if (config().showGrid && zoom >= 2f) {
			drawGrid(g);
		}
		if (config().movementTrail) {
			MapMarkers.trail(g, feature.trail().points(viewDim), here ? position.x : Double.NaN, position.z, (wx, wz) -> {
				float x = screenX(wx);
				float y = screenY(wz);
				return onMap(x, y, 50) ? new float[]{x, y} : null;
			}, 2f);
		}
		boolean overUi = overUi(mouseX, mouseY) || menu != null || !inMap(mouseX, mouseY) || windowDrag != WindowDrag.NONE
				|| open < 1f;
		StructureMarker structure = drawStructures(g, here, mouseX, mouseY);
		StoredContainer storage = drawStorage(g, mouseX, mouseY);
		FurnaceEntry furnace = drawFurnaces(g, mouseX, mouseY);
		TradeMerchant trader = drawTraders(g, mouseX, mouseY);
		PetEntry pet = drawPets(g, mouseX, mouseY);
		HoveredPlayer hoveredPlayer = drawEntities(g, level, player, here, delta, mouseX, mouseY);
		Waypoint hovered = drawWaypoints(g, player, mouseX, mouseY);
		drawPlayer(g, player, position, here);
		if (open < 1f) {
			g.disableScissor();
			g.pose().popMatrix();
			g.disableScissor();
			zoom = targetZoom;
			mapX = area[0];
			mapY = area[1];
			mapW = area[2];
			mapH = area[3];
			g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
		}
		g.fillGradient(mapX, mapY, mapX + mapW, mapY + 36, RenderUtil.withAlpha(VIGNETTE, open), 0);
		g.fillGradient(mapX, mapY + mapH - 40, mapX + mapW, mapY + mapH, 0, RenderUtil.withAlpha(VIGNETTE, open));
		if (open < 1f) {
			// The panels are not part of the growing map: they slide in from the edges as it opens.
			float hidden = 1f - open;
			slid(g, 0, -hidden * 40, () -> drawTabs(g, mouseX, mouseY));
			slid(g, -hidden * (TOOL + 16), 0, () -> drawNav(g, mouseX, mouseY));
			slid(g, 0, -hidden * 40, () -> drawInfo(g, layer, level, mouseX, mouseY, here));
			slid(g, 0, hidden * 40, () -> drawToolbar(g, mouseX, mouseY));
			slid(g, hidden * (LAYERS_WIDTH + 12), 0, () -> drawLayers(g, mouseX, mouseY));
			slid(g, hidden * (LAYERS_WIDTH + 12), 0, () -> drawSlider(g, mouseX, mouseY));
		} else {
			drawTabs(g, mouseX, mouseY);
			drawNav(g, mouseX, mouseY);
			drawInfo(g, layer, level, mouseX, mouseY, here);
			drawToolbar(g, mouseX, mouseY);
			drawLayers(g, mouseX, mouseY);
			drawSlider(g, mouseX, mouseY);
		}
		if (config().mapWindowed) {
			drawGrip(g, mouseX, mouseY);
		}
		g.disableScissor();
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (menu != null) {
			drawMenu(g, mouseX, mouseY);
		} else if (overUi) {
			return;
		} else if (hovered != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(hovered.name).withColor(hovered.rgb()));
			lines.add(Component.literal(hovered.x + ", " + (hovered.y == null ? "~" : hovered.y) + ", " + hovered.z).withStyle(ChatFormatting.GRAY));
			if (here) {
				lines.add(Component.literal(MapFeature.formatDistance(Math.sqrt(player.distanceToSqr(hovered.x, hovered.yOr(player.getY()), hovered.z))))
						.withStyle(ChatFormatting.GRAY));
			}
			lines.add(Component.translatable("cobblecomfort.map.hint_edit").withStyle(ChatFormatting.DARK_GRAY));
			DeathRecord death = config().deathInventory ? MapFeature.INSTANCE.deaths().of(hovered) : null;
			// The death inventory goes right below the name.
			g.setTooltipForNextFrame(font, lines, Optional.ofNullable(death).map(DeathTooltip.Data::new), mouseX, mouseY);
		} else if (hoveredPlayer != null) {
			g.setComponentTooltipForNextFrame(font, playerTooltip(hoveredPlayer, player, here), mouseX, mouseY);
		} else if (trader != null) {
			g.setComponentTooltipForNextFrame(font, traderTooltip(trader, player, here), mouseX, mouseY);
		} else if (pet != null) {
			g.setComponentTooltipForNextFrame(font, PetFeature.INSTANCE.tooltip(pet, player.getEyePosition(), here ? viewDim : ""), mouseX, mouseY);
		} else if (furnace != null) {
			g.setComponentTooltipForNextFrame(font, furnaceTooltip(furnace), mouseX, mouseY);
		} else if (storage != null) {
			g.setComponentTooltipForNextFrame(font, storageTooltip(storage), mouseX, mouseY);
		} else if (structure != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(StructureIcons.name(structure.type()));
			lines.add(Component.literal(structure.x() + ", " + structure.z()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("cobblecomfort.structure.source." + structure.source().name().toLowerCase(Locale.ROOT))
					.withStyle(ChatFormatting.DARK_GRAY));
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}

	// ---------------------------------------------------------------- opening out of the minimap

	/**
	 * Whether this map will open out of the minimap: opened at the player's position with animations on and the
	 * minimap on screen. Decided once, before the first frame.
	 */
	public boolean opensFromMinimap() {
		if (openFrom == null && !openChecked) {
			openChecked = true;
			Minecraft mc = Minecraft.getInstance();
			if (!centerGiven && config().mapAnimations && mc.player != null) {
				openFrom = MapFeature.INSTANCE.minimapRect(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
				float rotation = MapFeature.INSTANCE.minimapRotation(mc.player.getYRot());
				// Turn the short way round to north-up.
				openRotation = (float) Math.IEEEremainder(rotation, Math.PI * 2);
			}
		}
		return openFrom != null;
	}

	/** Eased progress of the opening (running backwards while closing), 1 once done or when not opening out of the minimap. */
	private float openProgress() {
		if (closeStartNanos != 0) {
			return 1f - closeEase(closeProgress());
		}
		if (!opensFromMinimap()) {
			return 1f;
		}
		long now = System.nanoTime();
		if (openStartNanos == 0) {
			openStartNanos = now;
		}
		float t = Math.min(1f, (now - openStartNanos) / 1e9f / OPEN_SECONDS);
		if (t >= 1f) {
			openFrom = null;
			return 1f;
		}
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private float closeProgress() {
		return Math.min(1f, (System.nanoTime() - closeStartNanos) / 1e9f / CLOSE_SECONDS);
	}

	/** Closing eases in and out, so the map starts shrinking at once and settles into the minimap. */
	private static float closeEase(float t) {
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	/**
	 * Starts shrinking back into the minimap, when the map goes back to the game, shows the player's dimension and the
	 * minimap is on screen. Closing during the opening turns it around from where it is.
	 */
	private boolean startClosing() {
		if (parent != null || !config().mapAnimations || !viewingPlayerDim() || minecraft.player == null) {
			return false;
		}
		Rect to = MapFeature.INSTANCE.minimapRect(width, height);
		if (to == null) {
			return false;
		}
		float open = openProgress();
		openFrom = to;
		openChecked = true;
		// Start the closing curve at the current point, so an unfinished opening does not jump.
		float t = 0f;
		for (float step = 0.5f; step > 0.001f; step /= 2) {
			if (closeEase(t + step) <= 1f - open) {
				t += step;
			}
		}
		closeStartNanos = System.nanoTime() - (long) (t * CLOSE_SECONDS * 1e9f);
		closeFromX = centerX;
		closeFromZ = centerZ;
		following = false;
		menu = null;
		windowDrag = WindowDrag.NONE;
		return true;
	}

	/**
	 * Pushes the transform that puts the map where the minimap is at the start and in its place at the end, and sets
	 * the zoom in between, so the map shows what the minimap showed and then widens. The map is cut to a frame that
	 * grows from the minimap's square to the window. Undone after the map is drawn.
	 */
	private void beginOpening(GuiGraphicsExtractor g, float open) {
		Rect from = openFrom;
		g.enableScissor(Math.round(from.x() + (windowX - from.x()) * open), Math.round(from.y() + (windowY - from.y()) * open),
				Math.round(from.x() + from.w() + (windowX + windowW - from.x() - from.w()) * open),
				Math.round(from.y() + from.h() + (windowY + windowH - from.y() - from.h()) * open));
		float mapCenterX = mapX + mapW / 2f;
		float mapCenterY = mapY + mapH / 2f;
		float startScale = from.w() / Math.min(mapW, mapH);
		float scale = startScale + (1f - startScale) * open;
		// Pixels per block on screen: the minimap's at the start (its zoom, scaled like it is on the HUD), the map's at the end.
		double startPixels = config().minimapZoom * from.w() / config().minimapSize;
		double pixels = Math.exp(Math.log(startPixels) + (Math.log(zoom) - Math.log(startPixels)) * open);
		zoom = (float) (pixels / scale);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(from.x() + from.w() / 2f + (mapCenterX - from.x() - from.w() / 2f) * open,
				from.y() + from.h() / 2f + (mapCenterY - from.y() - from.h() / 2f) * open);
		pose.rotate(openRotation * (1f - open));
		pose.scale(scale);
		pose.translate(-mapCenterX, -mapCenterY);
	}

	/** Draws a panel moved by (dx, dy). */
	private static void slid(GuiGraphicsExtractor g, float dx, float dy, Runnable draw) {
		g.pose().pushMatrix();
		g.pose().translate(dx, dy);
		draw.run();
		g.pose().popMatrix();
	}

	private void drawRegions(GuiGraphicsExtractor g, MapLayer layer) {
		TEXTURES.beginFrame();
		int minRx = (int) Math.floor(worldX(mapX) / MapRegion.SIZE);
		int maxRx = (int) Math.floor(worldX(mapX + mapW) / MapRegion.SIZE);
		int minRz = (int) Math.floor(worldZ(mapY) / MapRegion.SIZE);
		int maxRz = (int) Math.floor(worldZ(mapY + mapH) / MapRegion.SIZE);
		// Seed terrain is the surface: not behind a cave layer or the roof.
		if (shownLayer() == MapWorld.SURFACE) {
			MapFeature.INSTANCE.drawUnexplored(g, key(viewDim), minRx, maxRx, minRz, maxRz, this::screenX, this::screenY, mapAlpha());
		}
		for (int rz = minRz; rz <= maxRz; rz++) {
			for (int rx = minRx; rx <= maxRx; rx++) {
				MapRegion region = layer.region(rx, rz, false);
				if (region == null) {
					continue;
				}
				Identifier texture = TEXTURES.texture(region, config().heightShading);
				if (texture == null) {
					continue;
				}
				int x0 = Math.round(screenX((double) rx * MapRegion.SIZE));
				int y0 = Math.round(screenY((double) rz * MapRegion.SIZE));
				int x1 = Math.round(screenX((double) (rx + 1) * MapRegion.SIZE));
				int y1 = Math.round(screenY((double) (rz + 1) * MapRegion.SIZE));
				MapUi.blitFaded(g, texture, x0, y0, x1, y1, MapRegion.SIZE, mapAlpha());
			}
		}
	}

	private void drawGrid(GuiGraphicsExtractor g) {
		int minCx = (int) Math.floor(worldX(mapX) / 16);
		int maxCx = (int) Math.floor(worldX(mapX + mapW) / 16) + 1;
		int minCz = (int) Math.floor(worldZ(mapY) / 16);
		int maxCz = (int) Math.floor(worldZ(mapY + mapH) / 16) + 1;
		for (int cx = minCx; cx <= maxCx; cx++) {
			int x = Math.round(screenX(cx * 16.0));
			g.fill(x, mapY, x + 1, mapY + mapH, 0x30FFFFFF);
		}
		for (int cz = minCz; cz <= maxCz; cz++) {
			int y = Math.round(screenY(cz * 16.0));
			g.fill(mapX, y, mapX + mapW, y + 1, 0x30FFFFFF);
		}
	}

	/**
	 * Mobs and other players with the same markers and filters as on the minimap (animals below monsters below
	 * players), plus players shared by the server plugin; players get their name below. Returns the hovered player.
	 */
	private HoveredPlayer drawEntities(GuiGraphicsExtractor g, ClientLevel level, LocalPlayer player, boolean here, float delta,
			int mouseX, int mouseY) {
		MapConfig config = config();
		if (!config.showEntities) {
			return null;
		}
		HoveredPlayer hovered = here ? drawLoadedEntities(g, level, player, delta, mouseX, mouseY) : null;
		if (!config.showPlayers) {
			return hovered;
		}
		// Players shared by the server plugin: those too far away to be loaded, and everyone in other dimensions.
		PositionSync sync = MapFeature.INSTANCE.sync();
		for (PositionSync.Shown shown : sync.players(viewDim)) {
			PositionSync.Remote data = shown.data();
			if (here && level.getPlayerByUUID(data.uuid()) != null) {
				continue;
			}
			float x = screenX(shown.x());
			float y = screenY(shown.z());
			if (!onMap(x, y, 10)) {
				continue;
			}
			MapMarkers.remotePlayer(g, data.uuid(), x, y, config.mobHeads, MAP_HEAD, 1f);
			nameTag(g, Component.literal(data.name()), Math.round(x), Math.round(y) + 7, MapUi.TEXT);
			if (overMarker(x, y, mouseX, mouseY)) {
				hovered = new HoveredPlayer(data.uuid(), Component.literal(data.name()), data.health(), data.maxHealth(), data.armor(),
						shown.x(), shown.y(), shown.z());
			}
		}
		return hovered;
	}

	private static boolean overMarker(float x, float y, int mouseX, int mouseY) {
		return Math.abs(mouseX - x) <= MAP_HEAD / 2f + 1 && Math.abs(mouseY - y) <= MAP_HEAD / 2f + 1;
	}

	/** Name, health and armour (if known), position and distance of a player on the map. */
	private List<Component> playerTooltip(HoveredPlayer hovered, LocalPlayer player, boolean here) {
		List<Component> lines = new ArrayList<>();
		lines.add(hovered.name().copy().withColor(MapMarkers.playerColor(hovered.uuid())));
		if (hovered.health() >= 0) {
			lines.add(Component.translatable("cobblecomfort.map.player_stats", Math.round(hovered.health()), Math.round(hovered.maxHealth()),
					hovered.armor()).withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.literal((int) Math.floor(hovered.x()) + ", " + (int) Math.floor(hovered.y()) + ", " + (int) Math.floor(hovered.z()))
				.withStyle(ChatFormatting.GRAY));
		if (here) {
			lines.add(Component.literal(MapFeature.formatDistance(Math.sqrt(player.distanceToSqr(hovered.x(), hovered.y(), hovered.z()))))
					.withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	/** Mobs and players around the player (in the loaded world), with the same filters as the minimap. */
	private HoveredPlayer drawLoadedEntities(GuiGraphicsExtractor g, ClientLevel level, LocalPlayer player, float delta, int mouseX, int mouseY) {
		MapConfig config = config();
		HoveredPlayer hovered = null;
		List<Entity> shown = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == player || MapFeature.INSTANCE.entityAlpha(entity, player) <= 0
					|| MapMarkers.entityColor(entity, config.showPlayers, config.showHostiles, config.showPassives) == 0) {
				continue;
			}
			float x = screenX(entity.getX());
			float y = screenY(entity.getZ());
			if (onMap(x, y, 10)) {
				shown.add(entity);
			}
		}
		shown.sort(Comparator.comparingInt(MapFeature::entityLayer));
		for (Entity entity : shown) {
			Vec3 position = entity.getPosition(delta);
			float x = screenX(position.x);
			float y = screenY(position.z);
			int color = MapMarkers.entityColor(entity, config.showPlayers, config.showHostiles, config.showPassives);
			float alpha = MapFeature.INSTANCE.entityAlpha(entity, player);
			MapMarkers.entity(g, entity, x, y, color, config.mobHeads, MAP_HEAD, alpha);
			if (entity instanceof AbstractClientPlayer other) {
				nameTag(g, entity.getName(), Math.round(x), Math.round(y) + 7, RenderUtil.withAlpha(MapUi.TEXT, alpha));
				if (overMarker(x, y, mouseX, mouseY)) {
					hovered = new HoveredPlayer(other.getUUID(), other.getName(), other.getHealth(), other.getMaxHealth(), other.getArmorValue(),
							position.x, position.y, position.z);
				}
			}
		}
		return hovered;
	}

	/** The own arrow; in another dimension a faint arrow where you would be. */
	private void drawPlayer(GuiGraphicsExtractor g, LocalPlayer player, Vec3 position, boolean here) {
		double factor = playerToView();
		float px = screenX(position.x * factor);
		float py = screenY(position.z * factor);
		float angle = (float) (Math.toRadians(player.getYRot()) + Math.PI);
		if (here) {
			MapMarkers.arrow(g, px, py, angle, 0xFFFFFFFF, config().viewCone, false);
		} else {
			MapMarkers.arrow(g, px, py, angle, 0x90FFFFFF, false, false);
			nameTag(g, Component.translatable("cobblecomfort.map.you_here"), Math.round(px), Math.round(py) + 9, MapUi.TEXT_DIM);
		}
	}

	/** A small dark label centred below a marker. */
	private void nameTag(GuiGraphicsExtractor g, Component text, int cx, int y, int color) {
		MapUi.label(g, font, text, cx, y, color, 0.75f, 0xB0101318);
	}

	/** An item icon for a button or menu entry; no item means the waypoint marker in the accent colour. */
	private void drawIcon(GuiGraphicsExtractor g, ItemStack icon, int cx, int cy, int size, int accent) {
		if (icon.isEmpty()) {
			MapMarkers.waypointIcon(g, cx, cy, accent);
		} else {
			MapUi.icon(g, icon, cx, cy, size);
		}
	}

	private StructureMarker drawStructures(GuiGraphicsExtractor g, boolean here, int mouseX, int mouseY) {
		StructureMarker hovered = null;
		for (StructureMarker marker : MapFeature.INSTANCE.structures(viewDim, here)) {
			ItemStack icon = StructureIcons.icon(marker.type());
			if (icon.isEmpty()) {
				continue;
			}
			float x = screenX(marker.x() + 0.5);
			float y = screenY(marker.z() + 0.5);
			if (!onMap(x, y, 10)) {
				continue;
			}
			MapMarkers.badge(g, icon, x, y, false, 0);
			if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) {
				hovered = marker;
			}
		}
		return hovered;
	}

	/** Remembered containers (highlighted search results, or all of them if enabled). */
	private StoredContainer drawStorage(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		StoredContainer hovered = null;
		int highlight = StorageFeature.INSTANCE.config().outlineColor;
		for (StoredContainer container : StorageFeature.INSTANCE.mapContainers(viewDim)) {
			Vec3 center = container.center();
			float x = screenX(center.x);
			float y = screenY(center.z);
			if (!onMap(x, y, 10)) {
				continue;
			}
			boolean marked = StorageFeature.INSTANCE.isHighlighted(container);
			MapMarkers.badge(g, container.icon(), x, y, false, marked ? highlight : 0);
			if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) {
				hovered = container;
			}
		}
		return hovered;
	}

	/** Remembered traders, with their job block (green ring) or as wandering traders (blue ring). */
	private TradeMerchant drawTraders(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		TradeMerchant hovered = null;
		for (TradeMerchant merchant : TradeFeature.INSTANCE.mapEntries(viewDim)) {
			float x = screenX(merchant.x);
			float y = screenY(merchant.z);
			if (!onMap(x, y, 10)) {
				continue;
			}
			MapMarkers.badge(g, TradeFeature.icon(merchant), x, y, false, TradeFeature.color(merchant));
			if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) {
				hovered = merchant;
			}
		}
		return hovered;
	}

	/** Remembered pets with their spawn egg, ringed pink while alive and grey as a grave. */
	/** Living pets breathe slowly, each in its own rhythm; a fresh grave appears with a small puff of dust. */
	private PetEntry drawPets(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		PetEntry hovered = null;
		long now = System.currentTimeMillis();
		boolean animate = config().mapAnimations;
		for (PetEntry pet : PetFeature.INSTANCE.mapEntries(viewDim)) {
			float x = screenX(pet.x);
			float y = screenY(pet.z);
			if (!onMap(x, y, 10)) {
				continue;
			}
			float scale = 1f;
			if (animate && !pet.dead()) {
				double phase = (pet.uuid.hashCode() & 0xFFFF) / 1000.0;
				scale = 1f + PET_BREATH * (float) Math.sin(now / 1000.0 * Math.PI * 2 / PET_BREATH_SECONDS + phase);
			}
			float dust = 1f;
			if (animate && pet.dead() && now - pet.died < FRESH_GRAVE_MS) {
				// The first time a fresh grave is seen, it rises from a little dust.
				long shown = GRAVES_SHOWN.computeIfAbsent(pet.uuid.toString(), k -> now);
				dust = (now - shown) / (float) DUST_MS;
				if (dust < 1f) {
					scale = 0.6f + 0.4f * Math.min(1f, dust * 2.5f);
				}
			}
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y);
			pose.scale(scale);
			pose.translate(-x, -y);
			MapMarkers.badge(g, PetFeature.INSTANCE.icon(pet), x, y, false, pet.dead() ? PetFeature.DEAD_COLOR : PetFeature.ALIVE_COLOR);
			pose.popMatrix();
			if (dust < 1f) {
				drawDust(g, x, y, dust);
			}
			if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) {
				hovered = pet;
			}
		}
		return hovered;
	}

	/** Grey dust puffs spreading out and rising from under a fresh grave, {@code t} from 0 to 1. */
	private static void drawDust(GuiGraphicsExtractor g, float x, float y, float t) {
		float spread = 8f + 9f * (1f - (1f - t) * (1f - t));
		for (int i = 0; i < DUST_PUFFS; i++) {
			double angle = Math.PI * 2 * i / DUST_PUFFS + 0.4;
			int px = Math.round(x + (float) Math.cos(angle) * spread);
			int py = Math.round(y + 5 + (float) Math.sin(angle) * spread * 0.5f - 6f * t);
			int size = i % 2 == 0 ? 4 : 3;
			g.fill(px - size / 2, py - size / 2, px - size / 2 + size, py - size / 2 + size,
					RenderUtil.withAlpha(0xFFE6DCC8, 0.95f * (1f - t * t)));
		}
	}

	private List<Component> traderTooltip(TradeMerchant merchant, LocalPlayer player, boolean here) {
		List<Component> lines = new ArrayList<>();
		lines.add(TradeFeature.INSTANCE.title(merchant));
		if (!merchant.name.isEmpty()) {
			lines.add(TradeFeature.INSTANCE.displayName(merchant));
		}
		MutableComponent where = Component.literal((int) Math.floor(merchant.x) + ", " + (int) Math.floor(merchant.y) + ", " + (int) Math.floor(merchant.z));
		if (here) {
			where.append(" · " + MapFeature.formatDistance(merchant.distanceTo(player.position())));
		}
		lines.add(where.withStyle(ChatFormatting.GRAY));
		int shown = Math.min(TRADER_TOOLTIP_OFFERS, merchant.offers.size());
		for (int i = 0; i < shown; i++) {
			lines.add(TradeFeature.offerText(merchant.offers.get(i)));
		}
		if (merchant.offers.size() > shown) {
			lines.add(Component.translatable("cobblecomfort.trade.tooltip.more", merchant.offers.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
		}
		lines.add(Component.translatable("cobblecomfort.storage.seen", TradeFeature.age(merchant)).withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/** Remembered furnaces and brewing stands, ringed in their status colour. */
	private FurnaceEntry drawFurnaces(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		FurnaceEntry hovered = null;
		for (FurnaceEntry entry : FurnaceFeature.INSTANCE.mapEntries(viewDim, false)) {
			float x = screenX(entry.x + 0.5);
			float y = screenY(entry.z + 0.5);
			if (!onMap(x, y, 10)) {
				continue;
			}
			MapMarkers.badge(g, FurnaceFeature.blockIcon(entry), x, y, false, FurnaceFeature.color(entry));
			if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) {
				hovered = entry;
			}
		}
		return hovered;
	}

	private List<Component> furnaceTooltip(FurnaceEntry entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(FurnaceFeature.blockIcon(entry).getHoverName());
		lines.add(FurnaceFeature.amountText(entry).copy().append(" · ").append(FurnaceFeature.INSTANCE.status(entry)));
		lines.add(Component.literal(entry.x + ", " + entry.y + ", " + entry.z).withStyle(ChatFormatting.GRAY));
		if (!entry.done && entry.remaining > 0) {
			lines.add(Component.translatable("cobblecomfort.furnace.loaded_time").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private List<Component> storageTooltip(StoredContainer container) {
		List<Component> lines = new ArrayList<>();
		lines.add(container.displayName());
		lines.add(Component.literal(container.x + ", " + container.y + ", " + container.z).withStyle(ChatFormatting.GRAY));
		List<StorageItems.Entry> summary = StorageItems.summary(container.items());
		for (int i = 0; i < Math.min(6, summary.size()); i++) {
			StorageItems.Entry entry = summary.get(i);
			lines.add(Component.literal(entry.total() + "× ").append(entry.stack().getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (summary.size() > 6) {
			lines.add(Component.literal("…").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private Waypoint drawWaypoints(GuiGraphicsExtractor g, LocalPlayer player, int mouseX, int mouseY) {
		if (!config().waypoints || !MapFeature.INSTANCE.waypoints().loaded()) {
			return null;
		}
		Waypoint hovered = null;
		long now = System.currentTimeMillis();
		for (Waypoint waypoint : MapFeature.INSTANCE.waypoints().visible(viewDim)) {
			float x = screenX(waypoint.x + 0.5);
			float y = screenY(waypoint.z + 0.5);
			if (config().mapAnimations) {
				// New waypoints drop in; the name stays at the place.
				WaypointAnim.impact(g, waypoint, x, y, now, 1f);
				MapMarkers.waypoint(g, font, waypoint, x, y + WaypointAnim.dropOffset(waypoint, now), 1f, false);
			} else {
				MapMarkers.waypoint(g, font, waypoint, x, y, 1f, false);
			}
			boolean hit = menu == null && MapMarkers.waypointHit(font, waypoint, x, y, mouseX, mouseY, false);
			// Name and distance below the tip; when zoomed out only for the hovered waypoint.
			if (zoom >= 1f || hit) {
				MutableComponent tag = Component.literal(waypoint.name);
				if (viewingPlayerDim()) {
					double distance = Math.sqrt(player.distanceToSqr(waypoint.x + 0.5, waypoint.yOr(player.getY()), waypoint.z + 0.5));
					tag.append(Component.literal(" · " + MapFeature.formatDistance(distance)).withColor(MapUi.TEXT_DIM));
				}
				nameTag(g, tag, Math.round(x), Math.round(y) + 3, MapUi.TEXT);
			}
			if (hit) {
				hovered = waypoint;
			}
		}
		return hovered;
	}

	// ---------------------------------------------------------------- panels

	private int tabWidth(String dimension) {
		return 12 + 4 + font.width(tabLabel(dimension)) + 12;
	}

	private static Component tabLabel(String dimension) {
		Dim dim = Dim.of(dimension);
		return dim != null ? dim.label() : Component.literal(dimension.substring(dimension.indexOf(':') + 1));
	}

	/** The dimension tabs at the top centre; the player's dimension has a small dot. */
	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int top = tabsTop;
		MapUi.panel(g, tabsLeft, top, tabsLeft + tabsWidth, top + TAB_HEIGHT);
		int x = tabsLeft + 2;
		for (String tab : tabs) {
			int w = tabWidth(tab);
			boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= top + 2 && mouseY < top + TAB_HEIGHT - 2;
			boolean selected = tab.equals(viewDim);
			int accent = Dim.accentOf(tab);
			MapUi.buttonFace(g, x, top + 2, x + w, top + TAB_HEIGHT - 2, hovered, selected, accent);
			Dim dim = Dim.of(tab);
			MapUi.icon(g, dim != null ? dim.icon() : new ItemStack(Items.ENDER_EYE), x + 12, top + TAB_HEIGHT / 2f, 11);
			g.text(font, tabLabel(tab), x + 20, top + 6, selected ? MapUi.TEXT : MapUi.TEXT_DIM, false);
			if (tab.equals(playerDim)) {
				RenderUtil.roundedFill(g, x + w - 6, top + 4, x + w - 3, top + 7, 1, 0xFFFFFFFF);
			}
			x += w;
		}
	}

	/** Cursor position, biome and the matching position in the other dimension, top left. */
	private void drawInfo(GuiGraphicsExtractor g, MapLayer layer, ClientLevel level, int mouseX, int mouseY, boolean here) {
		int bx = (int) Math.floor(worldX(mouseX));
		int bz = (int) Math.floor(worldZ(mouseY));
		int y = layer.heightAt(bx, bz);
		List<Component> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		lines.add(Component.literal(bx + ", " + (y == Integer.MIN_VALUE ? "?" : String.valueOf(y)) + ", " + bz));
		colors.add(MapUi.TEXT);
		if (here && level.isLoaded(new BlockPos(bx, 64, bz))) {
			var biome = level.getBiome(new BlockPos(bx, Math.max(y, level.getMinY()), bz)).unwrapKey();
			biome.ifPresent(key -> {
				lines.add(Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()));
				colors.add(MapUi.TEXT_DIM);
			});
		}
		Dim view = Dim.of(viewDim);
		if (view == Dim.OVERWORLD || view == Dim.NETHER) {
			Dim other = view == Dim.OVERWORLD ? Dim.NETHER : Dim.OVERWORLD;
			double factor = view.scale / other.scale;
			lines.add(Component.translatable("cobblecomfort.map.counterpart", other.label(),
					(int) Math.floor(bx * factor) + ", " + (int) Math.floor(bz * factor)));
			colors.add(RenderUtil.lerpColor(other.accent, 0xFFFFFFFF, 0.35f));
		}
		int shown = shownLayer();
		if (shown != MapWorld.SURFACE) {
			lines.add(Component.translatable("cobblecomfort.map.depth.info", layerName(shown, false)));
			colors.add(0xFFE8C860);
		}
		int w = 0;
		for (Component line : lines) {
			w = Math.max(w, font.width(line));
		}
		int h = lines.size() * 11 + 7;
		// Below the tabs if it would run into them.
		// Right of the navigation bar.
		int left = nav.isEmpty() ? mapX + 6 : navRight() + 4;
		int top = left + w + 14 > tabsLeft - 4 ? tabsTop + TAB_HEIGHT + 4 : tabsTop;
		MapUi.panel(g, left, top, left + w + 14, top + h);
		RenderUtil.roundedFill(g, left + 3, top + 4, left + 5, top + h - 4, 1, Dim.accentOf(viewDim));
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), left + 9, top + 4 + i * 11, colors.get(i), i == 0);
		}
	}

	/** Icon buttons, the scale bar with zoom level and the help button, bottom centre. */
	private void drawToolbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		MapUi.panel(g, toolbarLeft, toolbarTop, toolbarRight, toolbarTop + TOOLBAR_HEIGHT);
		int ty = toolbarTop + (TOOLBAR_HEIGHT - TOOL) / 2;
		int accent = Dim.accentOf(viewDim);
		Tool hoveredTool = null;
		for (Tool tool : tools) {
			boolean hovered = menu == null && mouseX >= tool.x() && mouseX < tool.x() + TOOL && mouseY >= ty && mouseY < ty + TOOL;
			MapUi.buttonFace(g, tool.x(), ty, tool.x() + TOOL, ty + TOOL, hovered, tool.selected().getAsBoolean(), accent);
			drawIcon(g, tool.icon(), tool.x() + TOOL / 2, ty + TOOL / 2, 14, accent);
			if (hovered) {
				hoveredTool = tool;
			}
		}
		int sx = toolbarLeft + 4 + tools.size() * (TOOL + 2) + 2;
		g.fill(sx, toolbarTop + 6, sx + 1, toolbarTop + TOOLBAR_HEIGHT - 6, 0x30FFFFFF);
		// Scale bar: a round number of blocks close to the target width.
		double blocks = niceBlocks(SCALE_BAR_TARGET / zoom);
		int bar = (int) Math.round(blocks * zoom);
		int bx = sx + 8;
		int by = toolbarTop + 16;
		g.fill(bx, by, bx + bar, by + 2, 0xFFE8E8EE);
		g.fill(bx, by - 3, bx + 1, by + 2, 0xFFE8E8EE);
		g.fill(bx + bar - 1, by - 3, bx + bar, by + 2, 0xFFE8E8EE);
		MapUi.smallText(g, font, Component.literal(MapFeature.formatDistance(blocks)), bx, toolbarTop + 6, MapUi.TEXT_DIM, 0.75f);
		String zoomText = zoom >= 1 ? Math.round(zoom) + ":1" : "1:" + Math.round(1 / zoom);
		int help = toolbarRight - 4 - TOOL;
		g.text(font, zoomText, help - 6 - font.width(zoomText), toolbarTop + 9, MapUi.TEXT, false);
		boolean helpHovered = menu == null && mouseX >= help && mouseX < help + TOOL && mouseY >= ty && mouseY < ty + TOOL;
		MapUi.buttonFace(g, help, ty, help + TOOL, ty + TOOL, helpHovered, false, accent);
		g.centeredText(font, "?", help + TOOL / 2, ty + 6, helpHovered ? MapUi.TEXT : MapUi.TEXT_DIM);
		if (hoveredTool != null) {
			g.setTooltipForNextFrame(font, hoveredTool.tooltip(), mouseX, mouseY);
		} else if (helpHovered) {
			List<Component> lines = new ArrayList<>();
			for (String part : Component.translatable("cobblecomfort.map.hint").getString().split(" · ")) {
				lines.add(Component.literal(part).withStyle(ChatFormatting.GRAY));
			}
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}

	/** 1, 2 or 5 times a power of ten, at most {@code blocks}. */
	private static double niceBlocks(double blocks) {
		double power = Math.pow(10, Math.floor(Math.log10(Math.max(1, blocks))));
		for (int step : new int[]{5, 2, 1}) {
			if (step * power <= blocks) {
				return step * power;
			}
		}
		return power;
	}

	private boolean overUi(double x, double y) {
		boolean tabsHit = x >= tabsLeft && x < tabsLeft + tabsWidth && y >= tabsTop && y < tabsTop + TAB_HEIGHT;
		boolean toolbarHit = x >= toolbarLeft && x < toolbarRight && y >= toolbarTop && y < toolbarTop + TOOLBAR_HEIGHT;
		boolean navHit = !nav.isEmpty() && x >= mapX + 6 && x < navRight() && y >= navTop && y < navBottom();
		return tabsHit || toolbarHit || navHit || overLayers(x, y) || overSlider(x, y);
	}

	private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int h = menu.entries().size() * MENU_ROW + 6;
		g.nextStratum();
		MapUi.panel(g, menu.x(), menu.y(), menu.x() + MENU_WIDTH, menu.y() + h, MapUi.PANEL_SOLID);
		int accent = Dim.accentOf(viewDim);
		for (int i = 0; i < menu.entries().size(); i++) {
			int ry = menu.y() + 3 + i * MENU_ROW;
			boolean hover = mouseX >= menu.x() && mouseX < menu.x() + MENU_WIDTH && mouseY >= ry && mouseY < ry + MENU_ROW;
			if (hover) {
				RenderUtil.roundedFill(g, menu.x() + 3, ry, menu.x() + MENU_WIDTH - 3, ry + MENU_ROW, 3, RenderUtil.withAlpha(accent, 0.3f));
			}
			Entry entry = menu.entries().get(i);
			drawIcon(g, entry.icon(), menu.x() + 12, ry + MENU_ROW / 2, 11, accent);
			g.text(font, entry.label(), menu.x() + 22, ry + 4, hover ? MapUi.TEXT : 0xFFD8DBE2, false);
		}
	}

	// ---------------------------------------------------------------- depth slider

	/** The layer on screen: the one chosen on the slider, else the player's (their dimension) or the surface. */
	private int shownLayer() {
		if (manualLayer != null) {
			return manualLayer;
		}
		return viewingPlayerDim() ? MapFeature.INSTANCE.world().autoLayer() : MapWorld.SURFACE;
	}

	/** The layers of the shown dimension, top to bottom; refreshed now and then (other dimensions list their folders). */
	private List<Integer> layerIds() {
		long now = System.currentTimeMillis();
		if (!viewDim.equals(layerIdsDim) || now - layerIdsMs > 1000) {
			layerIds = MapFeature.INSTANCE.world().layerIds(key(viewDim), minecraft.level);
			layerIdsDim = viewDim;
			layerIdsMs = now;
		}
		return layerIds;
	}

	/** A layer's name: the roof, the surface or the cave layer's heights (short: just its lowest Y). */
	private static Component layerName(int id, boolean shortName) {
		if (id == MapWorld.SURFACE) {
			return Component.translatable("cobblecomfort.map.depth.surface");
		}
		if (id == MapWorld.ROOF) {
			return Component.translatable("cobblecomfort.map.depth.roof");
		}
		return shortName ? Component.literal("Y " + id * 16)
				: Component.translatable("cobblecomfort.map.depth.cave", id * 16, id * 16 + 15);
	}

	/**
	 * Places the slider at the right edge below the layer sidebar, or left of the sidebar when there is no room below.
	 * The top row is the "follow the player" button, the track below has a step per layer.
	 */
	private void placeSlider(int steps) {
		int width = 22;
		int track = Math.clamp(steps * 7, 60, 180);
		int height = 18 + track + 8;
		if (layersBottom + 6 + height <= maxSliderBottom(layersX1 - width)) {
			sliderX1 = layersX1;
			sliderTop = layersBottom + 6;
		} else {
			sliderX1 = layersX0 - 4;
			sliderTop = layersTop;
			height = Math.min(height, maxSliderBottom(sliderX1 - width) - sliderTop);
		}
		sliderX0 = sliderX1 - width;
		sliderBottom = sliderTop + height;
	}

	/** How far down the slider may reach at this left edge: above the toolbar if it is below, else above the grip. */
	private int maxSliderBottom(int x0) {
		return (toolbarRight > x0 ? toolbarTop : mapY + mapH - GRIP) - 6;
	}

	private int trackTop() {
		return sliderTop + 20;
	}

	private int trackBottom() {
		return sliderBottom - 6;
	}

	private float stepY(int index, int steps) {
		float stepH = (trackBottom() - trackTop()) / (float) Math.max(1, steps);
		return trackTop() + stepH * (index + 0.5f);
	}

	private boolean overSlider(double x, double y) {
		return x >= sliderX0 && x < sliderX1 && y >= sliderTop && y < sliderBottom;
	}

	private boolean overAutoButton(double x, double y) {
		return x >= sliderX0 + 2 && x < sliderX1 - 2 && y >= sliderTop + 2 && y < sliderTop + 18;
	}

	/** Picks the layer at a height on the track (dragging may go past its ends). */
	private void pickLayer(double y) {
		List<Integer> ids = layerIds();
		float stepH = (trackBottom() - trackTop()) / (float) ids.size();
		int index = Math.clamp((int) Math.floor((y - trackTop()) / stepH), 0, ids.size() - 1);
		manualLayer = ids.get(index);
	}

	/** Moves the chosen layer up (negative) or down by steps, starting from the one on screen. */
	private void stepLayer(int steps) {
		List<Integer> ids = layerIds();
		int index = Math.max(0, ids.indexOf(shownLayer()));
		manualLayer = ids.get(Math.clamp(index + steps, 0, ids.size() - 1));
	}

	/**
	 * The depth slider: an "A" button that follows the player again, a track with a notch per layer (the surface in
	 * the accent colour) and a knob on the layer shown, with its name in a tag to the left.
	 */
	private void drawSlider(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Integer> ids = layerIds();
		placeSlider(ids.size());
		int accent = Dim.accentOf(viewDim);
		boolean interactive = menu == null && windowDrag == WindowDrag.NONE;
		MapUi.panel(g, sliderX0, sliderTop, sliderX1, sliderBottom);
		boolean auto = manualLayer == null;
		MapUi.buttonFace(g, sliderX0 + 2, sliderTop + 2, sliderX1 - 2, sliderTop + 18,
				interactive && overAutoButton(mouseX, mouseY), auto, accent);
		MapUi.centeredText(g, font, Component.literal("A"), (sliderX0 + sliderX1) / 2f, sliderTop + 10.5f, auto ? MapUi.TEXT : MapUi.TEXT_DIM, 1f);
		int cx = (sliderX0 + sliderX1) / 2;
		RenderUtil.roundedFill(g, cx - 1, trackTop(), cx + 1, trackBottom(), 1, MapUi.FIELD);
		int shown = shownLayer();
		int shownIndex = Math.max(0, ids.indexOf(shown));
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.get(i);
			int y = Math.round(stepY(i, ids.size()));
			boolean major = id == MapWorld.SURFACE || id == MapWorld.ROOF || id % 4 == 0;
			int w = major ? 4 : 2;
			g.fill(cx - w, y, cx + w + 1, y + 1, id == MapWorld.SURFACE ? accent : major ? 0x60FFFFFF : 0x30FFFFFF);
		}
		float ky = stepY(shownIndex, ids.size());
		boolean knobHover = interactive && overSlider(mouseX, mouseY) && mouseY >= trackTop() - 4;
		RenderUtil.roundedFill(g, cx - 7, Math.round(ky) - 3, cx + 8, Math.round(ky) + 3, 3,
				knobHover || sliderDragging ? 0xFFFFFFFF : auto ? 0xFFDDE0E8 : accent);
		// The name of the layer next to the knob.
		Component name = layerName(shown, true);
		int tw = font.width(name);
		int tx1 = sliderX0 - 4;
		int ty = Math.round(ky) - 6;
		MapUi.panel(g, tx1 - tw - 8, ty, tx1, ty + 12);
		g.text(font, name, tx1 - tw - 4, ty + 2, auto ? MapUi.TEXT_DIM : MapUi.TEXT, false);
		if (interactive && overAutoButton(mouseX, mouseY)) {
			g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("cobblecomfort.map.depth.auto")), mouseX, mouseY);
		} else if (interactive && overSlider(mouseX, mouseY) && !sliderDragging) {
			g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("cobblecomfort.map.depth.title"),
					Component.translatable("cobblecomfort.map.depth.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
		}
	}

	/** A click on the slider: the "A" button follows the player again, the track picks a layer and starts dragging. */
	private void clickSlider(double mx, double my) {
		if (overAutoButton(mx, my)) {
			manualLayer = null;
			return;
		}
		pickLayer(my);
		sliderDragging = true;
	}

	// ---------------------------------------------------------------- layers

	/**
	 * What the layer sidebar lists: the map elements and, below the waypoints, their sets. The switches are the
	 * config options (and the sets' visibility), so they apply to the minimap too.
	 */
	private List<Layer> layers() {
		MapConfig c = config();
		List<Layer> list = new ArrayList<>();
		list.add(new Layer(layerLabel("waypoints"), 0, (g, x, y) -> MapMarkers.waypointIcon(g, x, y, 0xFF55FFFF), -1, true,
				() -> c.waypoints, () -> c.waypoints = !c.waypoints));
		WaypointStore store = MapFeature.INSTANCE.waypoints();
		if (store.loaded()) {
			for (String set : store.usedSets()) {
				int color = store.all().stream().filter(w -> w.set.equals(set)).findFirst().map(Waypoint::rgb).orElse(MapUi.ACCENT);
				LayerIcon icon = set.equals(WaypointStore.DEATH_SET) ? itemIcon(Items.SKELETON_SKULL)
						: set.equals(WaypointStore.RESPAWN_SET) ? itemIcon(Items.BED.red())
						: (g, x, y) -> MapMarkers.waypointIcon(g, x, y, color);
				list.add(new Layer(Component.literal(WaypointStore.displayName(set)), 1, icon, store.count(set), c.waypoints,
						() -> store.sets().getOrDefault(set, true), () -> {
							store.sets().put(set, !store.sets().getOrDefault(set, true));
							store.save();
						}));
			}
		}
		list.add(new Layer(layerLabel("structures"), 0, itemIcon(Items.CHISELED_STONE_BRICKS), -1, true,
				() -> c.structures, () -> c.structures = !c.structures));
		StorageFeature storage = StorageFeature.INSTANCE;
		if (storage.isEnabled()) {
			list.add(new Layer(layerLabel("storage"), 0, itemIcon(Items.CHEST), -1, true, () -> storage.config().mapMarkers, () -> {
				storage.config().mapMarkers = !storage.config().mapMarkers;
				storage.save();
			}));
		}
		FurnaceFeature furnaces = FurnaceFeature.INSTANCE;
		if (furnaces.isEnabled()) {
			list.add(new Layer(layerLabel("furnaces"), 0, itemIcon(Items.FURNACE), furnaces.mapEntries(viewDim, false).size(), true,
					() -> furnaces.config().mapMarkers, () -> {
						furnaces.config().mapMarkers = !furnaces.config().mapMarkers;
						furnaces.save();
					}));
		}
		TradeFeature trades = TradeFeature.INSTANCE;
		if (trades.isEnabled()) {
			list.add(new Layer(layerLabel("traders"), 0, itemIcon(Items.EMERALD), trades.mapEntries(viewDim).size(), true,
					() -> trades.config().mapMarkers, () -> {
						trades.config().mapMarkers = !trades.config().mapMarkers;
						trades.save();
					}));
		}
		PetFeature pets = PetFeature.INSTANCE;
		if (pets.isEnabled()) {
			list.add(new Layer(layerLabel("pets"), 0, itemIcon(Items.LEAD), pets.mapEntries(viewDim).size(), true,
					() -> pets.config().mapMarkers, () -> {
						pets.config().mapMarkers = !pets.config().mapMarkers;
						pets.save();
					}));
		}
		list.add(new Layer(layerLabel("entities"), 0, itemIcon(Items.ZOMBIE_HEAD), -1, true,
				() -> c.showEntities, () -> c.showEntities = !c.showEntities));
		list.add(new Layer(layerLabel("players"), 1, itemIcon(Items.PLAYER_HEAD), -1, c.showEntities,
				() -> c.showPlayers, () -> c.showPlayers = !c.showPlayers));
		list.add(new Layer(layerLabel("hostiles"), 1, dotIcon(MapMarkers.HOSTILE_DOT), -1, c.showEntities,
				() -> c.showHostiles, () -> c.showHostiles = !c.showHostiles));
		list.add(new Layer(layerLabel("passives"), 1, dotIcon(MapMarkers.PASSIVE_DOT), -1, c.showEntities,
				() -> c.showPassives, () -> c.showPassives = !c.showPassives));
		list.add(new Layer(layerLabel("trail"), 0, dotIcon(MapMarkers.TRAIL), -1, true,
				() -> c.movementTrail, () -> c.movementTrail = !c.movementTrail));
		list.add(new Layer(Component.translatable("cobblecomfort.map.button.grid"), 0, itemIcon(Items.ITEM_FRAME), -1, true,
				() -> c.showGrid, () -> c.showGrid = !c.showGrid));
		// Only on servers with the sync plugin; the count is how many others share with you.
		PositionSync sync = MapFeature.INSTANCE.sync();
		if (sync.supported()) {
			list.add(new Layer(layerLabel("share"), 0, itemIcon(Items.RECOVERY_COMPASS), c.sharePosition ? sync.count() : -1, true,
					() -> c.sharePosition, () -> c.sharePosition = !c.sharePosition));
		}
		return list;
	}

	private static Component layerLabel(String key) {
		return Component.translatable("cobblecomfort.map.layer." + key);
	}

	private LayerIcon itemIcon(Item item) {
		ItemStack stack = new ItemStack(item);
		return (g, x, y) -> MapUi.icon(g, stack, x, y, 9);
	}

	private static LayerIcon dotIcon(int color) {
		return (g, x, y) -> {
			RenderUtil.roundedFill(g, x - 3, y - 3, x + 3, y + 3, 2, 0xFF000000);
			RenderUtil.roundedFill(g, x - 2, y - 2, x + 2, y + 2, 1, color);
		};
	}

	/**
	 * Places the sidebar at the right edge of the map: below the tabs if it would run into them, and above the
	 * toolbar (or the resize grip). Collapsed it is a single button.
	 */
	private void placeLayers(int rows) {
		boolean open = config().mapLayers;
		layersX1 = mapX + mapW - 6;
		layersX0 = layersX1 - (open ? LAYERS_WIDTH : TOOL + 4);
		layersTop = tabsLeft + tabsWidth + 4 > layersX0 ? tabsTop + TAB_HEIGHT + 4 : tabsTop;
		int maxBottom = (toolbarRight > layersX0 ? toolbarTop : mapY + mapH - GRIP) - 6;
		layersBottom = open ? Math.min(layersTop + LAYERS_HEADER + rows * LAYER_ROW + 4, maxBottom) : layersTop + TOOL + 4;
		int visible = layersBottom - layersTop - LAYERS_HEADER - 4;
		layersScroll.update(Math.max(0, rows * LAYER_ROW - visible));
	}

	private boolean overLayers(double x, double y) {
		return x >= layersX0 && x < layersX1 && y >= layersTop && y < layersBottom;
	}

	private boolean overLayersToggle(double x, double y) {
		return config().mapLayers ? x >= layersX1 - 16 && x < layersX1 - 2 && y >= layersTop + 2 && y < layersTop + LAYERS_HEADER - 1
				: overLayers(x, y);
	}

	/** The layer sidebar: a header with a collapse button, then a row with a switch per layer (scrolls if long). */
	private void drawLayers(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Layer> layers = layers();
		placeLayers(layers.size());
		int accent = Dim.accentOf(viewDim);
		boolean interactive = menu == null && windowDrag == WindowDrag.NONE;
		MapUi.panel(g, layersX0, layersTop, layersX1, layersBottom);
		if (!config().mapLayers) {
			boolean hovered = interactive && overLayers(mouseX, mouseY);
			MapUi.buttonFace(g, layersX0 + 2, layersTop + 2, layersX1 - 2, layersBottom - 2, hovered, false, accent);
			g.centeredText(font, "☰", (layersX0 + layersX1) / 2, layersTop + 8, hovered ? MapUi.TEXT : MapUi.TEXT_DIM);
			if (hovered) {
				g.setTooltipForNextFrame(font, Component.translatable("cobblecomfort.map.layers"), mouseX, mouseY);
			}
			return;
		}
		g.text(font, Component.translatable("cobblecomfort.map.layers"), layersX0 + 8, layersTop + 5, MapUi.TEXT, false);
		boolean toggleHovered = interactive && overLayersToggle(mouseX, mouseY);
		MapUi.buttonFace(g, layersX1 - 16, layersTop + 2, layersX1 - 2, layersTop + LAYERS_HEADER - 1, toggleHovered, false, accent);
		g.centeredText(font, "»", layersX1 - 9, layersTop + 5, toggleHovered ? MapUi.TEXT : MapUi.TEXT_DIM);
		g.fill(layersX0 + 4, layersTop + LAYERS_HEADER, layersX1 - 4, layersTop + LAYERS_HEADER + 1, 0x18FFFFFF);
		int listTop = layersTop + LAYERS_HEADER + 2;
		g.enableScissor(layersX0, listTop, layersX1, layersBottom - 2);
		for (int i = 0; i < layers.size(); i++) {
			Layer layer = layers.get(i);
			int y = listTop + i * LAYER_ROW - (int) layersScroll.value();
			if (y + LAYER_ROW < listTop || y > layersBottom) {
				continue;
			}
			boolean shown = layer.shown().getAsBoolean();
			boolean hovered = interactive && mouseX >= layersX0 + 3 && mouseX < layersX1 - 3 && mouseY >= Math.max(y, listTop)
					&& mouseY < y + LAYER_ROW && mouseY < layersBottom - 2;
			if (hovered) {
				RenderUtil.roundedFill(g, layersX0 + 3, y, layersX1 - 3, y + LAYER_ROW, 3, MapUi.HOVER);
			}
			int cy = y + LAYER_ROW / 2;
			int indent = layer.depth() * 8;
			layer.icon().draw(g, layersX0 + 11 + indent, cy);
			int textColor = !layer.active() ? MapUi.TEXT_FAINT : shown ? MapUi.TEXT : MapUi.TEXT_DIM;
			int labelX = layersX0 + 19 + indent;
			Component count = layer.count() >= 0 ? Component.literal(String.valueOf(layer.count())) : null;
			int countWidth = count == null ? 0 : Math.round(font.width(count) * 0.75f) + 3;
			int room = layersX1 - 24 - labelX - countWidth;
			String label = layer.label().getString();
			if (font.width(label) > room) {
				label = font.plainSubstrByWidth(label, room - font.width("…")) + "…";
			}
			g.text(font, label, labelX, cy - 4, textColor, false);
			if (count != null) {
				MapUi.smallText(g, font, count, labelX + font.width(label) + 3, cy - 2, MapUi.TEXT_FAINT, 0.75f);
			}
			MapUi.toggle(g, layersX1 - 20, cy, shown, layer.active(), accent);
		}
		g.disableScissor();
		int visible = layersBottom - 2 - listTop;
		int content = layers.size() * LAYER_ROW;
		if (content > visible) {
			int barH = Math.max(10, visible * visible / content);
			int barY = listTop + (int) ((visible - barH) * (layersScroll.value() / (content - visible)));
			RenderUtil.roundedFill(g, layersX1 - 4, barY, layersX1 - 2, barY + barH, 1, 0x60FFFFFF);
		}
	}

	private void clickLayers(double mx, double my) {
		if (overLayersToggle(mx, my)) {
			config().mapLayers = !config().mapLayers;
			MapFeature.INSTANCE.save();
			return;
		}
		int listTop = layersTop + LAYERS_HEADER + 2;
		if (my < listTop || my >= layersBottom - 2) {
			return;
		}
		List<Layer> layers = layers();
		int index = (int) Math.floor((my - listTop + layersScroll.value()) / LAYER_ROW);
		if (index >= 0 && index < layers.size()) {
			layers.get(index).toggle().run();
			MapFeature.INSTANCE.save();
		}
	}

	// ---------------------------------------------------------------- input

	/** The arrow keys pan the map; WASD stays with the player (see {@link #passMovementKeys()}). */
	private void panWithKeys(float dt) {
		double step = PAN_SPEED * dt / zoom;
		double startX = centerX, startZ = centerZ;
		if (InputConstants.isKeyDown(InputConstants.KEY_UP)) {
			centerZ -= step;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_DOWN)) {
			centerZ += step;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_LEFT)) {
			centerX -= step;
		}
		if (InputConstants.isKeyDown(InputConstants.KEY_RIGHT)) {
			centerX += step;
		}
		if (centerX != startX || centerZ != startZ) {
			following = false;
		}
	}

	@Override
	public void tick() {
		super.tick();
		passMovementKeys();
	}

	/**
	 * Screens keep key presses from reaching the game, so walking, jumping, sneaking and sprinting are passed on to
	 * the player by hand while the map is open. Only changes are passed, so toggle sneak/sprint still work.
	 */
	private void passMovementKeys() {
		Options options = minecraft.options;
		for (KeyMapping mapping : new KeyMapping[]{options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
				options.keyJump, options.keyShift, options.keySprint}) {
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
			boolean down = key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
			if (down != passedKeys.getOrDefault(mapping, false)) {
				mapping.setDown(down);
				passedKeys.put(mapping, down);
			}
		}
	}

	@Override
	public void removed() {
		passedKeys.forEach((mapping, down) -> {
			if (down) {
				mapping.setDown(false);
			}
		});
		passedKeys.clear();
		super.removed();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closeStartNanos != 0) {
			return true;
		}
		if (menu == null && event.button() == InputConstants.MOUSE_BUTTON_LEFT && config().mapWindowed) {
			if (overClose(event.x(), event.y())) {
				onClose();
				return true;
			}
			if (overTitleBar(event.x(), event.y()) || overGrip(event.x(), event.y())) {
				windowDrag = overGrip(event.x(), event.y()) ? WindowDrag.RESIZE : WindowDrag.MOVE;
				dragMouseX = event.x();
				dragMouseY = event.y();
				dragX = windowX;
				dragY = windowY;
				dragW = windowW;
				dragH = windowH;
				return true;
			}
		}
		if (menu == null && !inMap(event.x(), event.y())) {
			return true;
		}
		if (menu != null) {
			int h = menu.entries().size() * MENU_ROW + 6;
			if (event.x() >= menu.x() && event.x() < menu.x() + MENU_WIDTH && event.y() >= menu.y() && event.y() < menu.y() + h) {
				int index = (int) ((event.y() - menu.y() - 3) / MENU_ROW);
				if (index >= 0 && index < menu.entries().size()) {
					Runnable action = menu.entries().get(index).action();
					menu = null;
					action.run();
				}
				return true;
			}
			menu = null;
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (overUi(event.x(), event.y())) {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				clickUi(event.x(), event.y());
			}
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			openMenu((int) event.x(), (int) event.y());
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			Waypoint clicked = waypointAt(event.x(), event.y());
			if (clicked != null) {
				minecraft.gui.setScreen(new WaypointEditScreen(this, clicked, false));
				return true;
			}
			if (doubleClick) {
				createWaypointAt((int) Math.floor(worldX(event.x())), (int) Math.floor(worldZ(event.y())));
				return true;
			}
			dragging = true;
			return true;
		}
		return false;
	}

	private void clickUi(double mx, double my) {
		int navIndex = navAt(mx, my);
		if (navIndex >= 0) {
			nav.get(navIndex).action().run();
			return;
		}
		if (overLayers(mx, my)) {
			clickLayers(mx, my);
			return;
		}
		if (overSlider(mx, my)) {
			clickSlider(mx, my);
			return;
		}
		int x = tabsLeft + 2;
		for (String tab : tabs) {
			int w = tabWidth(tab);
			if (mx >= x && mx < x + w && my >= tabsTop && my < tabsTop + TAB_HEIGHT) {
				switchTo(tab);
				return;
			}
			x += w;
		}
		int ty = toolbarTop + (TOOLBAR_HEIGHT - TOOL) / 2;
		for (Tool tool : tools) {
			if (mx >= tool.x() && mx < tool.x() + TOOL && my >= ty && my < ty + TOOL) {
				tool.action().run();
				return;
			}
		}
	}

	private Waypoint waypointAt(double mx, double my) {
		if (!MapFeature.INSTANCE.waypoints().loaded()) {
			return null;
		}
		for (Waypoint waypoint : MapFeature.INSTANCE.waypoints().visible(viewDim)) {
			double x = screenX(waypoint.x + 0.5);
			double y = screenY(waypoint.z + 0.5);
			if (MapMarkers.waypointHit(font, waypoint, (float) x, (float) y, mx, my, false)) {
				return waypoint;
			}
		}
		return null;
	}

	private void openMenu(int x, int y) {
		int wx = (int) Math.floor(worldX(x));
		int wz = (int) Math.floor(worldZ(y));
		List<Entry> entries = new ArrayList<>();
		entries.add(new Entry(ItemStack.EMPTY, Component.translatable("cobblecomfort.map.menu.waypoint"), () -> createWaypointAt(wx, wz)));
		entries.add(new Entry(new ItemStack(Items.PAPER), Component.translatable("cobblecomfort.map.menu.copy"), () -> {
			minecraft.keyboardHandler.setClipboard(wx + " " + heightOr(wx, wz, "~") + " " + wz);
		}));
		if (minecraft.player != null && minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			entries.add(new Entry(new ItemStack(Items.ENDER_PEARL), Component.translatable("cobblecomfort.map.menu.teleport"), () -> {
				String height = heightOr(wx, wz, "~");
				String target = wx + " " + (height.equals("~") ? "~" : String.valueOf(Integer.parseInt(height) + 1)) + " " + wz;
				String command = viewingPlayerDim() ? "tp @s " + target : "execute in " + viewDim + " run tp @s " + target;
				minecraft.player.connection.sendCommand(command);
				onClose();
			}));
		}
		entries.add(new Entry(new ItemStack(Items.COMPASS), Component.translatable("cobblecomfort.map.menu.center"), () -> {
			following = false;
			centerX = wx + 0.5;
			centerZ = wz + 0.5;
		}));
		int h = entries.size() * MENU_ROW + 6;
		menu = new ContextMenu(Math.min(x, width - MENU_WIDTH - 2), Math.min(y, height - h - 2), wx, wz, entries);
	}

	private String heightOr(int wx, int wz, String fallback) {
		int h = MapFeature.INSTANCE.world().layer(key(viewDim), shownLayer()).heightAt(wx, wz);
		return h == Integer.MIN_VALUE ? fallback : String.valueOf(h);
	}

	private void createWaypointAt(int wx, int wz) {
		String h = heightOr(wx, wz, "~");
		Integer y = h.equals("~") ? null : Integer.parseInt(h) + 1;
		Waypoint waypoint = new Waypoint("", wx, y, wz, viewDim);
		minecraft.gui.setScreen(new WaypointEditScreen(this, waypoint, true));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		sliderDragging = false;
		if (windowDrag != WindowDrag.NONE) {
			windowDrag = WindowDrag.NONE;
			MapFeature.INSTANCE.save();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (closeStartNanos != 0) {
			return true;
		}
		if (windowDrag != WindowDrag.NONE) {
			// The new place or size is remembered in the config (saved on release); layout() keeps it on screen.
			int offsetX = (int) Math.round(event.x() - dragMouseX);
			int offsetY = (int) Math.round(event.y() - dragMouseY);
			MapConfig config = config();
			config.mapWindowX = windowDrag == WindowDrag.MOVE ? Math.clamp(dragX + offsetX, 0, width - windowW) : dragX;
			config.mapWindowY = windowDrag == WindowDrag.MOVE ? Math.clamp(dragY + offsetY, 0, height - windowH) : dragY;
			config.mapWindowW = windowDrag == WindowDrag.RESIZE ? Math.clamp(dragW + offsetX, MIN_WINDOW_WIDTH, width - dragX) : dragW;
			config.mapWindowH = windowDrag == WindowDrag.RESIZE ? Math.clamp(dragH + offsetY, MIN_WINDOW_HEIGHT, height - dragY) : dragH;
			layout();
			return true;
		}
		if (sliderDragging) {
			pickLayer(event.y());
			return true;
		}
		if (dragging) {
			following = false;
			centerX -= dx / zoom;
			centerZ -= dy / zoom;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (closeStartNanos != 0) {
			return true;
		}
		if (!inMap(mouseX, mouseY)) {
			return false;
		}
		if (overLayers(mouseX, mouseY)) {
			layersScroll.scroll(-(scrollY * LAYER_ROW * 2));
			return true;
		}
		if (overSlider(mouseX, mouseY)) {
			stepLayer(scrollY > 0 ? -1 : 1);
			return true;
		}
		double beforeX = worldX(mouseX);
		double beforeZ = worldZ(mouseY);
		zoom = Math.clamp(zoom * (float) Math.pow(ZOOM_STEP, scrollY), MIN_ZOOM, MAX_ZOOM);
		// Keep the point under the cursor in place; while following, zoom around the player instead.
		if (!following) {
			centerX += beforeX - worldX(mouseX);
			centerZ += beforeZ - worldZ(mouseY);
		}
		lastZoom = zoom;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// While shrinking away, Escape or the map key close at once.
		if (closeStartNanos != 0) {
			if (event.key() == InputConstants.KEY_ESCAPE || MapFeature.INSTANCE.isMapKey(event)) {
				minecraft.gui.setScreen(parent);
			}
			return true;
		}
		// Space jumps (passed on to the player), so C brings the view back to the player.
		if (event.key() == InputConstants.KEY_C) {
			if (!viewingPlayerDim() && playerDim != null) {
				switchTo(playerDim);
			}
			centerOnPlayer();
			return true;
		}
		// Page up and down step through the layers, Home follows the player again.
		if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
			stepLayer(event.key() == InputConstants.KEY_PAGEUP ? -1 : 1);
			return true;
		}
		if (event.key() == InputConstants.KEY_HOME) {
			manualLayer = null;
			return true;
		}
		if (event.key() == InputConstants.KEY_TAB && !tabs.isEmpty()) {
			int index = tabs.indexOf(viewDim);
			int step = event.hasShiftDown() ? tabs.size() - 1 : 1;
			switchTo(tabs.get((index + step) % tabs.size()));
			return true;
		}
		if (MapFeature.INSTANCE.isMapKey(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (closeStartNanos == 0 && startClosing()) {
			return;
		}
		minecraft.gui.setScreen(parent);
	}
}
