package dev.cobblecomfort.features.storage;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.invtools.GuiStyle;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.invtools.ItemSearch;
import dev.cobblecomfort.features.lookinfo.InfoLine;
import dev.cobblecomfort.features.lookinfo.LookTarget;
import dev.cobblecomfort.features.lookinfo.LookInfoFeature;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.render.MapMarkers;
import dev.cobblecomfort.features.shulker.ShulkerContents;
import dev.cobblecomfort.features.tracker.TrackerStorage;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers what is in every container the player opens, so items can be searched for later and the container found
 * again (outline in the world, compass arrow, map markers). Containers can be named.
 */
public final class StorageFeature extends Feature<StorageConfig> {
	public static final StorageFeature INSTANCE = new StorageFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("storage_compass");

	/** How long after a right click an opening screen still belongs to the clicked block or entity. */
	private static final long PENDING_MS = 3000;
	private static final int SNAPSHOT_TICKS = 10;
	private static final int MAINTENANCE_TICKS = 20;
	private static final int SAVE_TICKS = 600;
	private static final double ENTITY_MOVE = 2.0;
	private static final int HINT_COLOR = 0xFFFFE066;
	private static final int LOOK_COLUMNS = 9;
	private static final int ARROW_RADIUS = 16;
	private static final float LABEL_SCALE = 0.6f;
	private static final double LABEL_MAX_DRAW = 48.0;
	/** Dark teal of the ender chest, for the preview tint. */
	private static final int ENDER_COLOR = 0xFF1E4F4A;

	private final StorageStore store = new StorageStore();
	private final ItemSearch hintSearch = new ItemSearch();
	private KeyMapping searchKey;
	private int ticks;

	// The block or entity the player last used, to know which container a screen belongs to.
	private BlockPos pendingBlock;
	private Entity pendingEntity;
	private long pendingTime;

	// The container whose screen is open.
	private AbstractContainerScreen<?> sessionScreen;
	private StoredContainer session;

	private final List<StoredContainer> highlighted = new ArrayList<>();
	private long highlightUntil;

	private String hintQuery = "";
	private int hintVersion = -1;
	private int hintCount;

	private StorageFeature() {
		super("storage", StorageConfig.class, StorageConfig::new);
	}

	@Override
	public void init() {
		searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cobblecomfort.storage", InputConstants.KEY_N,
				CobbleComfortClient.keyCategory()));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
				store.load(TrackerStorage.currentWorldKey(), handler.registryAccess()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			store.unload();
			highlighted.clear();
			session = null;
			sessionScreen = null;
			pendingEntity = null;
		}));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen)) {
				onScreenInit(mc, container);
			}
		});
		// Above the look info, which sits near the crosshair when moved there.
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addEnderLines(stack, lines));
		HudElementRegistry.attachElementAfter(LookInfoFeature.LAYER_ID, LAYER_ID, (g, delta) -> drawCompass(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return StorageConfigUi.buildCategory(this);
	}

	public StorageStore store() {
		return store;
	}

	// ---------------------------------------------------------------- tick

	private void tick(Minecraft mc) {
		if (!isEnabled() || mc.player == null || mc.level == null || !store.loaded()) {
			return;
		}
		ticks++;
		LocalPlayer player = mc.player;
		if (mc.options.keyUse.isDown() && mc.gui.screen() == null) {
			HitResult hit = mc.hitResult;
			if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
				pendingBlock = blockHit.getBlockPos().immutable();
				pendingEntity = null;
				pendingTime = System.currentTimeMillis();
			} else if (hit instanceof EntityHitResult entityHit) {
				pendingEntity = entityHit.getEntity();
				pendingBlock = null;
				pendingTime = System.currentTimeMillis();
			}
		}
		while (searchKey.consumeClick()) {
			mc.gui.setScreen(new StorageScreen(null, ""));
		}
		if (session != null && mc.gui.screen() == sessionScreen && ticks % SNAPSHOT_TICKS == 0) {
			snapshot(sessionScreen.getMenu(), session);
		}
		if (ticks % MAINTENANCE_TICKS == 0) {
			maintain(mc.level, player);
		}
		if (ticks % SAVE_TICKS == 0) {
			store.save();
		}
		drawHighlights(mc, player);
	}

	/** Forgets broken containers and follows minecarts, boats and animals with chests. */
	private void maintain(ClientLevel level, LocalPlayer player) {
		String dim = MapFeature.dimensionId(level);
		for (StoredContainer container : new ArrayList<>(store.all())) {
			if (container.kind != StoredContainer.Kind.BLOCK || !container.dimension.equals(dim) || !level.isLoaded(container.pos())) {
				continue;
			}
			BlockState state = level.getBlockState(container.pos());
			if (category(state.getBlock()) == null) {
				if (config.forgetBroken) {
					store.remove(container);
					highlighted.remove(container);
				}
				continue;
			}
			String type = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			if (!type.equals(container.type)) {
				// E.g. a copper chest that oxidised.
				container.type = type;
				store.changed();
			}
			BlockPos other = container.otherPos();
			if (other != null && level.isLoaded(other) && !(level.getBlockState(other).getBlock() instanceof ChestBlock)) {
				container.other = null;
				store.changed();
			}
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ContainerEntity) && !(entity instanceof AbstractChestedHorse)) {
				continue;
			}
			StoredContainer container = store.byEntity(entity.getStringUUID());
			if (container == null) {
				continue;
			}
			if (entity instanceof AbstractChestedHorse horse && !horse.hasChest() && config.forgetBroken) {
				store.remove(container);
				highlighted.remove(container);
				continue;
			}
			if (entity.position().distanceToSqr(container.x + 0.5, container.y, container.z + 0.5) > ENTITY_MOVE * ENTITY_MOVE
					|| !container.dimension.equals(dim)) {
				container.x = entity.getBlockX();
				container.y = entity.getBlockY();
				container.z = entity.getBlockZ();
				container.dimension = dim;
				store.changed();
			}
		}
	}

	// ---------------------------------------------------------------- recognising containers

	/** Which option a block belongs to: "chests", "utility" or null when it is not remembered at all. */
	private static String category(Block block) {
		if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
			return "chests";
		}
		if (block instanceof AbstractFurnaceBlock || block instanceof HopperBlock || block instanceof DispenserBlock
				|| block instanceof BrewingStandBlock) {
			return "utility";
		}
		if (block instanceof EnderChestBlock) {
			return "ender";
		}
		return null;
	}

	private boolean allowed(String category) {
		return switch (category) {
			case "chests" -> config.chests;
			case "utility" -> config.utilityBlocks;
			case "ender" -> config.enderChest;
			default -> false;
		};
	}

	private static boolean menuFits(Block block, AbstractContainerMenu menu) {
		if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock) {
			return menu instanceof ChestMenu;
		}
		if (block instanceof ShulkerBoxBlock) {
			return menu instanceof ShulkerBoxMenu;
		}
		if (block instanceof AbstractFurnaceBlock) {
			return menu instanceof AbstractFurnaceMenu;
		}
		if (block instanceof HopperBlock) {
			return menu instanceof HopperMenu;
		}
		if (block instanceof DispenserBlock) {
			return menu instanceof DispenserMenu;
		}
		return block instanceof BrewingStandBlock && menu instanceof BrewingStandMenu;
	}

	/** The remembered container an opening menu belongs to (created if new), or null if it is not remembered. */
	private StoredContainer identify(Minecraft mc, AbstractContainerMenu menu) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		boolean recent = System.currentTimeMillis() - pendingTime < PENDING_MS;
		if (!recent) {
			// A click shorter than a tick is not seen by the tick; the crosshair target is the next best guess.
			HitResult hit = mc.hitResult;
			if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
				pendingBlock = blockHit.getBlockPos().immutable();
				pendingEntity = null;
				recent = true;
			} else if (hit instanceof EntityHitResult entityHit) {
				pendingEntity = entityHit.getEntity();
				pendingBlock = null;
				recent = true;
			}
		}
		String dim = MapFeature.dimensionId(level);
		if (menu instanceof AbstractMountInventoryMenu) {
			Entity mount = recent && pendingEntity != null ? pendingEntity : player.getVehicle();
			return mount instanceof AbstractChestedHorse horse && horse.hasChest() && config.entities ? forEntity(mount, dim) : null;
		}
		if (recent && pendingEntity != null) {
			if (pendingEntity instanceof ContainerEntity && (menu instanceof ChestMenu || menu instanceof HopperMenu) && config.entities) {
				return forEntity(pendingEntity, dim);
			}
			return null;
		}
		if (!recent || pendingBlock == null) {
			return null;
		}
		BlockState state = level.getBlockState(pendingBlock);
		Block block = state.getBlock();
		String category = category(block);
		if (category == null || !allowed(category) || !menuFits(block, menu)) {
			return null;
		}
		if (block instanceof EnderChestBlock) {
			StoredContainer ender = store.ender();
			if (ender == null) {
				ender = new StoredContainer();
				ender.kind = StoredContainer.Kind.ENDER;
				ender.type = BuiltInRegistries.BLOCK.getKey(block).toString();
				store.put(ender);
			}
			return ender;
		}
		BlockPos other = null;
		if (block instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			other = pendingBlock.relative(ChestBlock.getConnectedDirection(state));
		}
		StoredContainer container = store.atBlock(dim, pendingBlock);
		if (container == null && other != null) {
			container = store.atBlock(dim, other);
		}
		if (container == null) {
			container = new StoredContainer();
		}
		container.kind = StoredContainer.Kind.BLOCK;
		container.dimension = dim;
		container.x = pendingBlock.getX();
		container.y = pendingBlock.getY();
		container.z = pendingBlock.getZ();
		container.other = other == null ? null : new int[]{other.getX(), other.getY(), other.getZ()};
		container.type = BuiltInRegistries.BLOCK.getKey(block).toString();
		store.put(container);
		return container;
	}

	private StoredContainer forEntity(Entity entity, String dim) {
		StoredContainer container = store.byEntity(entity.getStringUUID());
		if (container == null) {
			container = new StoredContainer();
			container.kind = StoredContainer.Kind.ENTITY;
			container.uuid = entity.getStringUUID();
		}
		container.dimension = dim;
		container.x = entity.getBlockX();
		container.y = entity.getBlockY();
		container.z = entity.getBlockZ();
		container.type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		store.put(container);
		return container;
	}

	/** Copies the container's slots (not the player's) into the memory, if they changed. */
	private void snapshot(AbstractContainerMenu menu, StoredContainer container) {
		List<ItemStack> items = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory) && slot.hasItem()) {
				items.add(slot.getItem().copy());
			}
		}
		if (!sameItems(items, container.items)) {
			store.setItems(container, items);
		} else {
			container.seen = System.currentTimeMillis();
		}
	}

	private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- container screens

	private void onScreenInit(Minecraft mc, AbstractContainerScreen<?> screen) {
		if (!isEnabled() || !store.loaded() || mc.player == null || mc.level == null) {
			return;
		}
		if (screen != sessionScreen) {
			session = identify(mc, screen.getMenu());
			sessionScreen = session == null ? null : screen;
			pendingBlock = null;
			pendingEntity = null;
			if (session != null) {
				// Found what we were looking for.
				highlighted.remove(session);
			}
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		StoredContainer container = session != null && sessionScreen == screen ? session : null;
		if (container != null) {
			ScreenEvents.remove(screen).register(s -> {
				if (session == container) {
					snapshot(screen.getMenu(), container);
					store.save();
					session = null;
					sessionScreen = null;
				}
			});
			if (config.naming) {
				addNameEditor(mc, screen, gui, container);
			}
		}
		if (config.inventoryHint) {
			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> drawHint(mc, g, gui, container, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hintHovered(mc, gui, container, event.x(), event.y())) {
					String query = InventoryToolsFeature.INSTANCE.activeQuery();
					mc.player.closeContainer();
					mc.gui.setScreen(new StorageScreen(null, query));
					return false;
				}
				return true;
			});
		}
	}

	/** A pencil next to the title; clicking it turns the title row into a text field for the container's name. */
	private void addNameEditor(Minecraft mc, AbstractContainerScreen<?> screen, ContainerScreenAccessor gui, StoredContainer container) {
		Font font = mc.font;
		EditBox box = new EditBox(font, 100, 10, Component.translatable("cobblecomfort.storage.name"));
		box.setBordered(false);
		box.setMaxLength(32);
		box.setTextColor(0xFFFFFFFF);
		box.setHint(Component.translatable("cobblecomfort.storage.name_hint").withColor(0xFFC0C0C0));
		box.setVisible(false);
		NameButton button = new NameButton(Component.translatable("cobblecomfort.storage.rename"), () -> {
			box.setValue(container.name);
			box.moveCursorToEnd(false);
			box.setVisible(true);
			screen.setFocused(box);
		});
		Screens.getWidgets(screen).add(box);
		Screens.getWidgets(screen).add(button);
		ScreenEvents.beforeExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			int titleX = gui.cobblecomfort$leftPos() + gui.cobblecomfort$titleLabelX();
			int titleY = gui.cobblecomfort$topPos() + gui.cobblecomfort$titleLabelY();
			int labelEnd = titleX + font.width(screen.getTitle()) + (container.name.isEmpty() ? 0 : 4 + font.width(nameLabel(container)));
			button.setX(labelEnd + 3);
			button.setY(titleY - 2);
			button.visible = !box.isVisible();
			box.setX(titleX + 2);
			box.setY(titleY);
			box.setWidth(Math.max(60, gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() - 22 - titleX));
		});
		ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			int titleX = gui.cobblecomfort$leftPos() + gui.cobblecomfort$titleLabelX();
			int titleY = gui.cobblecomfort$topPos() + gui.cobblecomfort$titleLabelY();
			if (box.isVisible()) {
				// Drawn again on top so the vanilla title does not show through.
				GuiStyle.inset(g, titleX - 1, titleY - 2, box.getX() + box.getWidth() + 1, titleY + 9);
				box.extractRenderState(g, mouseX, mouseY, delta);
			} else if (!container.name.isEmpty()) {
				g.text(font, nameLabel(container), titleX + font.width(screen.getTitle()) + 4, titleY, 0xFF8A5A00, false);
			}
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
			if (!box.isVisible()) {
				return true;
			}
			if (key.key() == InputConstants.KEY_RETURN || key.key() == InputConstants.KEY_NUMPADENTER) {
				container.name = box.getValue().trim();
				store.changed();
				box.setVisible(false);
				screen.setFocused(null);
			} else if (key.key() == InputConstants.KEY_ESCAPE) {
				box.setVisible(false);
				screen.setFocused(null);
			} else {
				// While typing, keys must not close the screen, swap hotbar slots or drop items.
				box.keyPressed(key);
			}
			return false;
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
			if (box.isVisible() && !box.isMouseOver(event.x(), event.y())) {
				container.name = box.getValue().trim();
				store.changed();
				box.setVisible(false);
				screen.setFocused(null);
			}
			return true;
		});
	}

	private static Component nameLabel(StoredContainer container) {
		return Component.literal("„" + container.name + "“");
	}

	// ---------------------------------------------------------------- inventory search hint

	/** How many remembered containers (other than the open one) hold items matching the inventory search. */
	private int hintCount(StoredContainer open) {
		String query = InventoryToolsFeature.INSTANCE.activeQuery();
		if (query.isEmpty()) {
			return 0;
		}
		if (!query.equals(hintQuery) || store.version() != hintVersion) {
			hintQuery = query;
			hintVersion = store.version();
			hintSearch.setQuery(query);
			hintCount = 0;
			for (StoredContainer container : store.all()) {
				if (container != open && matches(hintSearch, container)) {
					hintCount++;
				}
			}
		}
		return hintCount;
	}

	static boolean matches(ItemSearch search, StoredContainer container) {
		for (ItemStack stack : container.items) {
			if (search.matches(stack, InventoryToolsFeature.INSTANCE.config())) {
				return true;
			}
		}
		return false;
	}

	private Component hintText(int count) {
		return Component.translatable(count == 1 ? "cobblecomfort.storage.hint.one" : "cobblecomfort.storage.hint", count);
	}

	private int[] hintBox(Minecraft mc, ContainerScreenAccessor gui, int count) {
		int x = gui.cobblecomfort$leftPos();
		int y = gui.cobblecomfort$topPos() + gui.cobblecomfort$imageHeight() + 3;
		return new int[]{x, y, x + mc.font.width(hintText(count)) + 6, y + 12};
	}

	private boolean hintHovered(Minecraft mc, ContainerScreenAccessor gui, StoredContainer open, double mouseX, double mouseY) {
		int count = hintCount(open);
		if (count == 0) {
			return false;
		}
		int[] box = hintBox(mc, gui, count);
		return mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3];
	}

	private void drawHint(Minecraft mc, GuiGraphicsExtractor g, ContainerScreenAccessor gui, StoredContainer open, int mouseX, int mouseY) {
		int count = hintCount(open);
		if (count == 0) {
			return;
		}
		int[] box = hintBox(mc, gui, count);
		boolean hover = mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3];
		g.fill(box[0], box[1], box[2], box[3], hover ? 0xE0303030 : 0xC0000000);
		g.text(mc.font, hintText(count), box[0] + 3, box[1] + 2, HINT_COLOR, true);
	}

	// ---------------------------------------------------------------- finding

	/** Marks containers in the world, on the compass and on the map for the configured time. */
	public void highlight(List<StoredContainer> containers) {
		highlighted.clear();
		for (StoredContainer container : containers) {
			if (container.located()) {
				highlighted.add(container);
			}
		}
		highlightUntil = System.currentTimeMillis() + config.highlightSeconds * 1000L;
	}

	public boolean isHighlighted(StoredContainer container) {
		return highlightActive() && highlighted.contains(container);
	}

	private boolean highlightActive() {
		if (!highlighted.isEmpty() && System.currentTimeMillis() > highlightUntil) {
			highlighted.clear();
		}
		return !highlighted.isEmpty();
	}

	/** Containers to show on the map in a dimension: the highlighted ones and, if enabled, all others. */
	public List<StoredContainer> mapContainers(String dimension) {
		List<StoredContainer> list = new ArrayList<>();
		if (!isEnabled() || !config.mapMarkers || !store.loaded()) {
			return list;
		}
		for (StoredContainer container : store.all()) {
			if (container.located() && container.dimension.equals(dimension) && (config.mapAllContainers || isHighlighted(container))) {
				list.add(container);
			}
		}
		return list;
	}

	private void drawHighlights(Minecraft mc, LocalPlayer player) {
		if (!highlightActive() || !config.outline) {
			return;
		}
		String dim = MapFeature.dimensionId(mc.level);
		Vec3 eye = player.getEyePosition();
		int color = config.outlineColor;
		try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
			for (StoredContainer container : highlighted) {
				if (!container.dimension.equals(dim)) {
					continue;
				}
				AABB box = bounds(mc.level, container);
				Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 2f, 0x30000000 | (color & 0xFFFFFF))).setAlwaysOnTop();
				Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.5, (box.minZ + box.maxZ) / 2);
				Vec3 offset = top.subtract(eye);
				double length = offset.length();
				Vec3 at = length > LABEL_MAX_DRAW ? eye.add(offset.scale(LABEL_MAX_DRAW / length)) : top;
				String text = container.displayName().getString() + " (" + MapFeature.formatDistance(length) + ")";
				Gizmos.billboardText(text, at, TextGizmo.Style.forColorAndCentered(color).withScale(LABEL_SCALE)).setAlwaysOnTop();
			}
		}
	}

	private static AABB bounds(ClientLevel level, StoredContainer container) {
		if (container.kind == StoredContainer.Kind.ENTITY) {
			for (Entity entity : level.entitiesForRendering()) {
				if (entity.getStringUUID().equals(container.uuid)) {
					return entity.getBoundingBox();
				}
			}
			return new AABB(container.pos());
		}
		AABB box = new AABB(container.pos());
		BlockPos other = container.otherPos();
		return other == null ? box : box.minmax(new AABB(other));
	}

	/** An arrow around the crosshair pointing at the nearest highlighted container, with its distance. */
	private void drawCompass(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!isEnabled() || !config.compassArrow || player == null || mc.level == null || !highlightActive()) {
			return;
		}
		String dim = MapFeature.dimensionId(mc.level);
		StoredContainer nearest = null;
		double best = Double.MAX_VALUE;
		for (StoredContainer container : highlighted) {
			if (container.dimension.equals(dim)) {
				double distance = container.distanceTo(player.getEyePosition());
				if (distance < best) {
					best = distance;
					nearest = container;
				}
			}
		}
		if (nearest == null) {
			return;
		}
		Vec3 target = nearest.center();
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		double bearing = Math.atan2(-dx, dz);
		float angle = (float) (bearing - Math.toRadians(player.getYRot()));
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		float ax = cx + (float) Math.sin(angle) * ARROW_RADIUS;
		float ay = cy - (float) Math.cos(angle) * ARROW_RADIUS;
		MapMarkers.arrow(g, ax, ay, angle, config.outlineColor, false, true);
		double dy = target.y - player.getEyeY();
		String text = MapFeature.formatDistance(best) + (dy > 3 ? " ↑" : dy < -3 ? " ↓" : "");
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy + ARROW_RADIUS + 6);
		pose.scale(0.75f);
		g.centeredText(mc.font, text, 0, 0, config.outlineColor);
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- ender chest tooltip

	/** The remembered ender chest contents as a 27-slot box for the shulker preview, or null when unknown or empty. */
	public ShulkerContents enderContents() {
		StoredContainer ender = enderForTooltip();
		if (ender == null || ender.items.isEmpty()) {
			return null;
		}
		List<ItemStack> slots = new ArrayList<>(ender.items);
		while (slots.size() < ShulkerContents.SLOTS) {
			slots.add(ItemStack.EMPTY);
		}
		return new ShulkerContents(slots, ENDER_COLOR);
	}

	/** "Seen 2 h ago" below the ender chest preview. */
	public Component enderNote() {
		StoredContainer ender = enderForTooltip();
		if (ender == null) {
			return null;
		}
		boolean stale = StorageItems.stale(ender.seen, config.staleDays);
		return Component.translatable("cobblecomfort.storage.seen", StorageItems.age(ender.seen))
				.withStyle(stale ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
	}

	private StoredContainer enderForTooltip() {
		return isEnabled() && config.enderTooltip && store.loaded() ? store.ender() : null;
	}

	/** Text instead of a preview when the ender chest is empty or was never opened in this world. */
	private void addEnderLines(ItemStack stack, List<Component> lines) {
		if (!stack.is(Items.ENDER_CHEST) || !isEnabled() || !config.enderTooltip || !store.loaded()) {
			return;
		}
		StoredContainer ender = store.ender();
		if (ender == null) {
			lines.add(Component.translatable("cobblecomfort.storage.ender_unknown").withStyle(ChatFormatting.DARK_GRAY));
		} else if (ender.items.isEmpty()) {
			lines.add(Component.translatable("cobblecomfort.storage.ender_empty").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---------------------------------------------------------------- look info

	/** Adds the remembered contents (and name, age) of a looked-at container to the look info. */
	public LookTarget decorate(LookTarget target, HitResult hit) {
		if (target == null || !isEnabled() || !config.lookInfo || !store.loaded()) {
			return target;
		}
		Minecraft mc = Minecraft.getInstance();
		StoredContainer container = null;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			if (mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof EnderChestBlock) {
				container = store.ender();
			} else {
				container = store.atBlock(MapFeature.dimensionId(mc.level), blockHit.getBlockPos());
			}
		} else if (hit instanceof EntityHitResult entityHit) {
			container = store.byEntity(entityHit.getEntity().getStringUUID());
		}
		if (container == null) {
			return target;
		}
		List<InfoLine> lines = new ArrayList<>(target.lines());
		List<StorageItems.Entry> summary = StorageItems.summary(container.items);
		if (summary.isEmpty()) {
			lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.storage.empty").withStyle(ChatFormatting.GRAY)));
		} else {
			List<ItemStack> stacks = new ArrayList<>();
			for (StorageItems.Entry entry : summary) {
				stacks.add(entry.stack().copyWithCount(entry.total()));
			}
			lines.add(new InfoLine.Items(stacks, LOOK_COLUMNS, config.lookInfoRows));
		}
		if (config.showAge) {
			boolean stale = StorageItems.stale(container.seen, config.staleDays);
			lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.storage.seen", StorageItems.age(container.seen))
					.withStyle(stale ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
		}
		Component name = container.name.isEmpty() ? target.name() : Component.literal(container.name);
		return new LookTarget(target.identity(), target.icon(), name, target.modName(), lines);
	}
}
