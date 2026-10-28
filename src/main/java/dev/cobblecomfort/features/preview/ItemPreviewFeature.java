package dev.cobblecomfort.features.preview;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.enchantdesc.EnchantDescFeature;
import dev.cobblecomfort.features.invtools.ItemSearch;
import dev.cobblecomfort.features.map.MapFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Map, book and enchanted book previews in item tooltips. */
public final class ItemPreviewFeature extends Feature<ItemPreviewConfig> {
	public static final ItemPreviewFeature INSTANCE = new ItemPreviewFeature();

	/** A book preview drawn this recently is the one the mouse wheel turns the pages of. */
	private static final long SCROLL_WINDOW_MS = 150;
	private static final int LEARN_TICKS = 20;
	/** Representative items for an enchantment's icon, in order of preference. */
	private static final List<Item> ICON_ORDER = List.of(Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
			Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.FISHING_ROD,
			Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS, Items.ELYTRA,
			Items.SHEARS, Items.SHIELD, Items.FLINT_AND_STEEL, Items.BRUSH);

	private final MapCenters centers = new MapCenters();
	/** The page last shown per book (by content). */
	private final Map<Integer, Integer> pages = new HashMap<>();
	private int shownBook;
	private int shownBookPages;
	private long shownBookMs;
	private int ticks;

	private ItemPreviewFeature() {
		super("item_preview", ItemPreviewConfig.class, ItemPreviewConfig::new);
	}

	@Override
	public void init() {
		ClientTooltipComponentCallback.EVENT.register(data -> switch (data) {
			case MapPreviewTooltip.Data map -> new MapPreviewTooltip(this, map);
			case BookPreviewTooltip.Data book -> new BookPreviewTooltip(this, book);
			case EnchantPreviewTooltip.Data enchants -> new EnchantPreviewTooltip(this, enchants);
			default -> null;
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> centers.load());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			centers.unload();
			pages.clear();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) ->
				ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> !scrollBook(vertical)));
	}

	@Override
	public ConfigCategory buildCategory() {
		return ItemPreviewConfigUi.buildCategory(this);
	}

	/** Whether previews are shown large right now. */
	boolean large() {
		return !config.compactByDefault || config.largeKey.isDown();
	}

	/** The "hold Shift for a larger view" hint, or null. */
	Component keyHint() {
		return config.compactByDefault && config.keyHint && !config.largeKey.isDown()
				? Component.translatable("cobblecomfort.preview.hint", config.largeKey.getDisplayName()).withColor(0xFF808080)
				: null;
	}

	// ---------------------------------------------------------------- tooltip

	/** Tooltip image for a map, book or enchanted book, or empty to keep vanilla's. */
	public Optional<TooltipComponent> tooltipImage(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null) {
			return Optional.empty();
		}
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if (mapId != null) {
			MapItemSavedData data = config.maps ? mc.level.getMapData(mapId) : null;
			return data == null ? Optional.empty() : Optional.of(new MapPreviewTooltip.Data(mapId, data, centers.get(mapId.id())));
		}
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null && config.writtenBooks) {
			return bookData(written.getPages(mc.isTextFilteringEnabled()));
		}
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (writable != null && config.writableBooks) {
			return bookData(writable.getPages(mc.isTextFilteringEnabled()).<Component>map(Component::literal).toList());
		}
		if (replacesEnchantLines(stack)) {
			return Optional.of(enchantData(stack.get(DataComponents.STORED_ENCHANTMENTS)));
		}
		return Optional.empty();
	}

	/**
	 * Whether the enchanted book preview replaces vanilla's enchantment lines for this stack. Holding the key (or
	 * showing enchantment descriptions) brings the vanilla lines back, and searches always see them.
	 */
	public boolean replacesEnchantLines(Object stack) {
		if (!isEnabled() || !config.enchantedBooks || !(stack instanceof ItemStack item) || config.largeKey.isDown() || ItemSearch.searching()) {
			return false;
		}
		ItemEnchantments stored = item.get(DataComponents.STORED_ENCHANTMENTS);
		return stored != null && !stored.isEmpty()
				&& !(EnchantDescFeature.INSTANCE.isEnabled() && EnchantDescFeature.INSTANCE.detailsShown());
	}

	private static Optional<TooltipComponent> bookData(List<Component> content) {
		if (content.stream().allMatch(page -> page.getString().isBlank())) {
			return Optional.empty();
		}
		return Optional.of(new BookPreviewTooltip.Data(content, content.hashCode()));
	}

	private static EnchantPreviewTooltip.Data enchantData(ItemEnchantments enchantments) {
		Minecraft mc = Minecraft.getInstance();
		// Vanilla's tooltip order first, then the rest.
		Set<Holder<Enchantment>> order = new LinkedHashSet<>();
		mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TOOLTIP_ORDER)
				.ifPresent(tag -> tag.forEach(holder -> {
					if (enchantments.keySet().contains(holder)) {
						order.add(holder);
					}
				}));
		order.addAll(enchantments.keySet());
		List<EnchantPreviewTooltip.Row> rows = new ArrayList<>();
		for (Holder<Enchantment> holder : order) {
			int level = enchantments.getLevel(holder);
			rows.add(new EnchantPreviewTooltip.Row(icon(holder.value().definition().supportedItems()),
					Enchantment.getFullname(holder, level), level, holder.value().getMaxLevel()));
		}
		return new EnchantPreviewTooltip.Data(rows);
	}

	/** An item the enchantment goes on; an anvil for ones that go on nearly everything (Mending, Unbreaking). */
	private static ItemStack icon(HolderSet<Item> items) {
		if (contains(items, Items.DIAMOND_SWORD) && contains(items, Items.DIAMOND_CHESTPLATE)) {
			return new ItemStack(Items.ANVIL);
		}
		for (Item item : ICON_ORDER) {
			if (contains(items, item)) {
				return new ItemStack(item);
			}
		}
		return items.stream().findFirst().map(holder -> new ItemStack(holder.value())).orElseGet(() -> new ItemStack(Items.ENCHANTED_BOOK));
	}

	private static boolean contains(HolderSet<Item> items, Item item) {
		return items.contains(BuiltInRegistries.ITEM.wrapAsHolder(item));
	}

	// ---------------------------------------------------------------- book pages

	/** The page to show of a book with {@code count} pages, and notes it as the book the mouse wheel turns. */
	int bookPage(int book, int count) {
		shownBook = book;
		shownBookPages = count;
		shownBookMs = System.currentTimeMillis();
		return Mth.clamp(pages.getOrDefault(book, 0), 0, count - 1);
	}

	boolean scrollPages() {
		return config.scrollPages;
	}

	/** Turns the page of the book whose tooltip is open; true when the scroll was used. */
	private boolean scrollBook(double vertical) {
		if (!isEnabled() || !config.scrollPages || vertical == 0 || shownBookPages <= 1
				|| System.currentTimeMillis() - shownBookMs > SCROLL_WINDOW_MS) {
			return false;
		}
		int page = Mth.clamp(pages.getOrDefault(shownBook, 0), 0, shownBookPages - 1);
		pages.put(shownBook, Mth.clamp(page + (vertical < 0 ? 1 : -1), 0, shownBookPages - 1));
		return true;
	}

	// ---------------------------------------------------------------- map centres

	/**
	 * Learns the centre of carried maps from the player's own marker on them. Vanilla maps are aligned to a grid of
	 * 128 × scale blocks, so the marker's rough position snaps to the exact centre.
	 */
	private void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!isEnabled() || !config.maps || player == null || mc.level == null || ++ticks % LEARN_TICKS != 0) {
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			MapId id = player.getInventory().getItem(i).get(DataComponents.MAP_ID);
			MapItemSavedData data = id == null ? null : mc.level.getMapData(id);
			MapDecoration own = data == null ? null : ownMarker(data, player);
			if (own == null) {
				continue;
			}
			int scale = 1 << data.scale;
			int size = 128 * scale;
			centers.put(id.id(), new MapCenters.Center(snap(player.getX() - own.x() * scale / 2.0, size),
					snap(player.getZ() - own.y() * scale / 2.0, size), MapFeature.dimensionId(mc.level)));
		}
	}

	/** The player's marker on the map: the only player marker, or the one facing the player's way. */
	private static MapDecoration ownMarker(MapItemSavedData data, LocalPlayer player) {
		List<MapDecoration> markers = new ArrayList<>();
		for (MapDecoration decoration : data.getDecorations()) {
			if (decoration.type().is(MapDecorationTypes.PLAYER)) {
				markers.add(decoration);
			}
		}
		if (markers.size() > 1) {
			float yaw = player.getYRot();
			int rot = (int) ((yaw < 0 ? yaw - 8 : yaw + 8) * 16 / 360) & 15;
			markers.removeIf(marker -> {
				int diff = (marker.rot() - rot) & 15;
				return diff > 1 && diff < 15;
			});
		}
		return markers.size() == 1 ? markers.getFirst() : null;
	}

	/** The centre of the map-grid cell containing {@code pos}, as vanilla places new maps. */
	private static int snap(double pos, int size) {
		return Mth.floor((pos + 64) / size) * size + size / 2 - 64;
	}
}
