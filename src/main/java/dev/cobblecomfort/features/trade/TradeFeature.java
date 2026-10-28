package dev.cobblecomfort.features.trade;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.invtools.ItemSearch;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.storage.NameButton;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.cobblecomfort.features.storage.StorageItems;
import dev.cobblecomfort.features.tracker.TrackerStorage;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the offers of every villager and wandering trader the player trades with, follows where they walk while
 * nearby, and makes the offers searchable (a tab in the storage memory), visible on the map and hinted in tooltips.
 */
public final class TradeFeature extends Feature<TradeConfig> {
	public static final TradeFeature INSTANCE = new TradeFeature();

	private static final int SNAPSHOT_TICKS = 10;
	private static final int MAINTENANCE_TICKS = 20;
	private static final int SAVE_TICKS = 600;
	private static final double MOVE = 2.0;
	/** Within this distance of where a trader was, it would be visible if it were still there. */
	private static final double VISIBLE_RANGE = 48.0;
	/** Checks in a row (one per second) without seeing a trader where it should be before it is forgotten. */
	private static final int MISSING_CHECKS = 10;
	/** Wandering traders despawn after at most 40 minutes. */
	private static final long WANDERING_LIFETIME_MS = 45 * 60 * 1000L;
	/** How far from the player a trader can be to belong to a just-opened trading screen without a crosshair hit. */
	private static final double OPEN_RANGE = 8.0;
	private static final int NAME_COLOR = 0xFFFFD27A;
	private static final Map<String, Item> PROFESSION_ICONS = Map.ofEntries(
			Map.entry("armorer", Items.BLAST_FURNACE), Map.entry("butcher", Items.SMOKER),
			Map.entry("cartographer", Items.CARTOGRAPHY_TABLE), Map.entry("cleric", Items.BREWING_STAND),
			Map.entry("farmer", Items.COMPOSTER), Map.entry("fisherman", Items.BARREL), Map.entry("fletcher", Items.FLETCHING_TABLE),
			Map.entry("leatherworker", Items.CAULDRON), Map.entry("librarian", Items.LECTERN), Map.entry("mason", Items.STONECUTTER),
			Map.entry("shepherd", Items.LOOM), Map.entry("toolsmith", Items.SMITHING_TABLE), Map.entry("weaponsmith", Items.GRINDSTONE));

	private final TradeStore store = new TradeStore();
	private int ticks;
	private MerchantScreen sessionScreen;
	private TradeMerchant session;

	private TradeFeature() {
		super("trade", TradeConfig.class, TradeConfig::new);
	}

	@Override
	public void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
				store.load(TrackerStorage.currentWorldKey(), handler.registryAccess()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			store.unload();
			session = null;
			sessionScreen = null;
		}));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof MerchantScreen merchant) {
				onOpen(mc, merchant);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addTooltipHints(stack, lines));
	}

	@Override
	public ConfigCategory buildCategory() {
		return TradeConfigUi.buildCategory(this);
	}

	public TradeStore store() {
		return store;
	}

	public boolean active() {
		return isEnabled() && store.loaded();
	}

	// ---------------------------------------------------------------- trading screen

	private void onOpen(Minecraft mc, MerchantScreen screen) {
		if (!active() || mc.player == null || mc.level == null || screen == sessionScreen) {
			return;
		}
		AbstractVillager trader = openedTrader(mc);
		if (trader == null || trader instanceof WanderingTrader && !config.wanderingTraders) {
			return;
		}
		TradeMerchant merchant = store.byUuid(trader.getStringUUID());
		if (merchant == null) {
			merchant = new TradeMerchant();
			merchant.uuid = trader.getStringUUID();
		}
		update(merchant, trader, MapFeature.dimensionId(mc.level));
		store.put(merchant);
		session = merchant;
		sessionScreen = screen;
		TradeMerchant opened = merchant;
		ScreenEvents.remove(screen).register(s -> {
			if (session == opened) {
				snapshot(screen.getMenu(), opened);
				store.save();
				session = null;
				sessionScreen = null;
			}
		});
		if (config.naming) {
			addNameEditor(mc, screen, merchant);
		}
	}

	/** The trader whose screen just opened: the one under the crosshair, else the nearest one close by. */
	private static AbstractVillager openedTrader(Minecraft mc) {
		if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof AbstractVillager villager) {
			return villager;
		}
		AbstractVillager nearest = null;
		double best = OPEN_RANGE * OPEN_RANGE;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof AbstractVillager villager && villager.distanceToSqr(mc.player) < best) {
				best = villager.distanceToSqr(mc.player);
				nearest = villager;
			}
		}
		return nearest;
	}

	private void update(TradeMerchant merchant, AbstractVillager trader, String dimension) {
		merchant.wandering = trader instanceof WanderingTrader;
		if (trader instanceof Villager villager) {
			merchant.profession = professionId(villager);
			merchant.level = villager.getVillagerData().level();
		}
		merchant.dimension = dimension;
		merchant.x = trader.getX();
		merchant.y = trader.getY();
		merchant.z = trader.getZ();
		merchant.alive = System.currentTimeMillis();
	}

	private static String professionId(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return profession.unwrapKey().map(key -> key.identifier().toString()).orElse("");
	}

	/** Copies the offers from the open trading screen, if they arrived and changed. */
	private void snapshot(MerchantMenu menu, TradeMerchant merchant) {
		if (menu.getOffers().isEmpty()) {
			return;
		}
		List<TradeOffer> offers = new ArrayList<>();
		for (MerchantOffer offer : menu.getOffers()) {
			offers.add(store.offer(offer.getCostA(), offer.getCostB(), offer.getResult(), offer.getUses(), offer.getMaxUses()));
		}
		if (menu.getTraderLevel() > 0 && !merchant.wandering) {
			merchant.level = menu.getTraderLevel();
		}
		merchant.seen = System.currentTimeMillis();
		if (!sameOffers(offers, merchant.offers)) {
			merchant.offers = offers;
		}
		store.changed();
	}

	private static boolean sameOffers(List<TradeOffer> a, List<TradeOffer> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			TradeOffer x = a.get(i);
			TradeOffer y = b.get(i);
			if (x.uses != y.uses || x.maxUses != y.maxUses || !ItemStack.matches(x.costA(), y.costA())
					|| !ItemStack.matches(x.costB(), y.costB()) || !ItemStack.matches(x.result(), y.result())) {
				return false;
			}
		}
		return true;
	}

	/** A pencil next to the title; the name is shown (and edited) above the offer list, left of the inventory search. */
	private void addNameEditor(Minecraft mc, MerchantScreen screen, TradeMerchant merchant) {
		Font font = mc.font;
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		EditBox box = new EditBox(font, 100, 10, Component.translatable("cobblecomfort.storage.name"));
		box.setBordered(false);
		box.setMaxLength(32);
		box.setTextColor(0xFFFFFFFF);
		box.setHint(Component.translatable("cobblecomfort.storage.name_hint").withColor(0xFFC0C0C0));
		box.setVisible(false);
		NameButton button = new NameButton(Component.translatable("cobblecomfort.trade.rename"), () -> {
			box.setValue(merchant.name);
			box.moveCursorToEnd(false);
			box.setVisible(true);
			screen.setFocused(box);
		});
		Screens.getWidgets(screen).add(box);
		Screens.getWidgets(screen).add(button);
		ScreenEvents.beforeExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			// Vanilla centres the title over the right part of the screen, 49 px right of the middle.
			int center = gui.cobblecomfort$leftPos() + 49 + gui.cobblecomfort$imageWidth() / 2;
			button.setX(center + font.width(screenTitle(screen)) / 2 + 3);
			button.setY(gui.cobblecomfort$topPos() + 4);
			button.visible = !box.isVisible();
			// The screen focuses the clicked pencil after it opened the field; hand the focus on so typing reaches it.
			if (box.isVisible() && s.getFocused() == button) {
				s.setFocused(box);
			}
			box.setX(gui.cobblecomfort$leftPos() + 6);
			box.setY(gui.cobblecomfort$topPos() - 11);
		});
		ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			int x = gui.cobblecomfort$leftPos() + 6;
			int y = gui.cobblecomfort$topPos() - 11;
			if (box.isVisible()) {
				g.fill(box.getX() - 2, y - 2, box.getX() + box.getWidth() + 2, y + 10, 0xC0000000);
				box.extractRenderState(g, mouseX, mouseY, delta);
			} else if (!merchant.name.isEmpty()) {
				Component label = Component.literal("„" + merchant.name + "“");
				g.text(font, label, x, y, NAME_COLOR, true);
			}
		});
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
			if (!box.isVisible()) {
				return true;
			}
			if (key.key() == InputConstants.KEY_RETURN || key.key() == InputConstants.KEY_NUMPADENTER) {
				merchant.name = box.getValue().trim();
				store.changed();
				box.setVisible(false);
				screen.setFocused(null);
			} else if (key.key() == InputConstants.KEY_ESCAPE) {
				box.setVisible(false);
				screen.setFocused(null);
			} else {
				box.keyPressed(key);
			}
			return false;
		});
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
			if (box.isVisible() && !box.isMouseOver(event.x(), event.y())) {
				merchant.name = box.getValue().trim();
				store.changed();
				box.setVisible(false);
				screen.setFocused(null);
			}
			return true;
		});
	}

	/** The title as vanilla draws it: with the level ("Librarian - Apprentice") once the trader can level up. */
	private static Component screenTitle(MerchantScreen screen) {
		MerchantMenu menu = screen.getMenu();
		int level = menu.getTraderLevel();
		if (level > 0 && level <= 5 && menu.showProgressBar()) {
			return Component.translatable("merchant.title", screen.getTitle(), Component.translatable("merchant.level." + level));
		}
		return screen.getTitle();
	}

	// ---------------------------------------------------------------- tick

	private void tick(Minecraft mc) {
		if (!active() || mc.player == null || mc.level == null) {
			return;
		}
		ticks++;
		if (session != null && mc.gui.screen() == sessionScreen && ticks % SNAPSHOT_TICKS == 0) {
			snapshot(sessionScreen.getMenu(), session);
		}
		if (ticks % MAINTENANCE_TICKS == 0) {
			maintain(mc.level, mc.player);
		}
		if (ticks % SAVE_TICKS == 0) {
			store.save();
		}
	}

	/** Follows traders that walk around, and forgets ones that died, changed their job or (wandering) left. */
	private void maintain(ClientLevel level, LocalPlayer player) {
		String dim = MapFeature.dimensionId(level);
		Map<String, AbstractVillager> nearby = new HashMap<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof AbstractVillager villager) {
				nearby.put(villager.getStringUUID(), villager);
			}
		}
		long now = System.currentTimeMillis();
		for (TradeMerchant merchant : new ArrayList<>(store.all())) {
			if (merchant.wandering && now - merchant.alive > WANDERING_LIFETIME_MS) {
				store.remove(merchant);
				continue;
			}
			AbstractVillager trader = nearby.get(merchant.uuid);
			if (trader != null) {
				merchant.missing = 0;
				if (trader.isDeadOrDying()
						|| trader instanceof Villager villager && !professionId(villager).equals(merchant.profession)) {
					// Dead, or a new job (or none) means different offers.
					if (config.forgetGone) {
						store.remove(merchant);
					}
					continue;
				}
				boolean moved = trader.distanceToSqr(merchant.x, merchant.y, merchant.z) > MOVE * MOVE || !merchant.dimension.equals(dim);
				int levelBefore = merchant.level;
				update(merchant, trader, dim);
				if (moved || levelBefore != merchant.level) {
					store.changed();
				}
				continue;
			}
			if (config.forgetGone && merchant.dimension.equals(dim)
					&& player.distanceToSqr(merchant.x, player.getY(), merchant.z) < VISIBLE_RANGE * VISIBLE_RANGE
					&& level.isLoaded(BlockPos.containing(merchant.position()))
					&& ++merchant.missing >= MISSING_CHECKS) {
				store.remove(merchant);
			}
		}
	}

	// ---------------------------------------------------------------- texts and icons

	/** "Librarian · Master" or "Wandering Trader". */
	public Component title(TradeMerchant merchant) {
		if (merchant.wandering) {
			return Component.translatable("entity.minecraft.wandering_trader");
		}
		MutableComponent text = professionName(merchant.profession).copy();
		if (merchant.level >= 1 && merchant.level <= 5) {
			text.append(" · ").append(Component.translatable("merchant.level." + merchant.level));
		}
		return text;
	}

	/** The given name in quotes, or the title. */
	public Component displayName(TradeMerchant merchant) {
		return merchant.name.isEmpty() ? title(merchant) : Component.literal("„" + merchant.name + "“").withColor(NAME_COLOR);
	}

	private static Component professionName(String profession) {
		Identifier id = Identifier.tryParse(profession);
		Minecraft mc = Minecraft.getInstance();
		if (id != null && mc.level != null) {
			var found = mc.level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getValue(id);
			if (found != null) {
				return found.name();
			}
		}
		return Component.translatable("entity.minecraft.villager");
	}

	/** The profession's job block, or a spawn egg for wandering traders and unknown jobs. */
	public static ItemStack icon(TradeMerchant merchant) {
		if (merchant.wandering) {
			return new ItemStack(Items.WANDERING_TRADER_SPAWN_EGG);
		}
		Identifier id = Identifier.tryParse(merchant.profession);
		Item item = id == null ? null : PROFESSION_ICONS.get(id.getPath());
		return new ItemStack(item == null ? Items.VILLAGER_SPAWN_EGG : item);
	}

	/** Marker ring colour: green for villagers, blue for wandering traders. */
	public static int color(TradeMerchant merchant) {
		return merchant.wandering ? 0xFF4A90E0 : 0xFF3FB950;
	}

	/** "12 Emerald + 1 Book". */
	public static Component priceText(TradeOffer offer) {
		MutableComponent text = stackText(offer.costA());
		if (!offer.costB().isEmpty()) {
			text.append(" + ").append(stackText(offer.costB()));
		}
		return text;
	}

	private static MutableComponent stackText(ItemStack stack) {
		return Component.literal(stack.getCount() + " ").append(stack.getHoverName());
	}

	/** "12 Emerald + 1 Book → Enchanted Book (Mending)", grey and struck through when sold out. */
	public static Component offerText(TradeOffer offer) {
		MutableComponent text = priceText(offer).copy().append(" → ").append(resultName(offer.result()));
		return offer.outOfStock() ? text.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH) : text.withStyle(ChatFormatting.GRAY);
	}

	/** The result's name, with the enchantments of an enchanted book ("Enchanted Book (Mending)"). */
	public static Component resultName(ItemStack stack) {
		MutableComponent name = (stack.getCount() > 1 ? Component.literal(stack.getCount() + " ") : Component.empty()).append(stack.getHoverName());
		var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored != null && !stored.isEmpty()) {
			MutableComponent list = Component.empty();
			boolean first = true;
			for (var entry : stored.entrySet()) {
				if (!first) {
					list.append(", ");
				}
				list.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).copy()
						.withStyle(ChatFormatting.WHITE));
				first = false;
			}
			name.append(" (").append(list).append(")");
		}
		return name;
	}

	/** "34m" in this dimension, else the dimension name. */
	public static Component where(TradeMerchant merchant, Vec3 eye, String dim) {
		if (merchant.dimension.equals(dim)) {
			return Component.literal(MapFeature.formatDistance(merchant.distanceTo(eye)));
		}
		String path = merchant.dimension.contains(":") ? merchant.dimension.substring(merchant.dimension.indexOf(':') + 1) : merchant.dimension;
		String key = "cobblecomfort.storage.dim." + path;
		Component translated = Component.translatable(key);
		return translated.getString().equals(key) ? Component.literal(path) : translated;
	}

	/** How long ago the offers were seen, gold when outdated (the storage memory's setting). */
	public static Component age(TradeMerchant merchant) {
		boolean stale = StorageItems.stale(merchant.seen, StorageFeature.INSTANCE.config().staleDays);
		return StorageItems.age(merchant.seen).copy().withStyle(stale ? ChatFormatting.GOLD : ChatFormatting.GRAY);
	}

	// ---------------------------------------------------------------- map

	/** Remembered traders in a dimension, for the map. */
	public List<TradeMerchant> mapEntries(String dimension) {
		List<TradeMerchant> list = new ArrayList<>();
		if (!active() || !config.mapMarkers) {
			return list;
		}
		for (TradeMerchant merchant : store.all()) {
			if (merchant.dimension.equals(dimension)) {
				list.add(merchant);
			}
		}
		return list;
	}

	// ---------------------------------------------------------------- tooltip hints

	/** One offer of a trader, for hints and search results. */
	public record Match(TradeMerchant merchant, TradeOffer offer) {
	}

	/** "Sold by Librarian for 12 Emerald (34m)" and "Bought by Farmer: 20 Wheat → 1 Emerald" lines under items. */
	private void addTooltipHints(ItemStack stack, List<Component> lines) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || !config.tooltipHint || stack.isEmpty() || stack.is(Items.EMERALD) || mc.player == null || mc.level == null
				|| mc.gui.screen() instanceof MerchantScreen || ItemSearch.searching()) {
			return;
		}
		List<Match> matches = new ArrayList<>();
		for (TradeMerchant merchant : store.all()) {
			for (TradeOffer offer : merchant.offers) {
				boolean sells = ItemStack.isSameItemSameComponents(offer.result(), stack);
				boolean buys = offer.buying() && (offer.costA().is(stack.getItem()) || offer.costB().is(stack.getItem()));
				if (sells || buys) {
					matches.add(new Match(merchant, offer));
				}
			}
		}
		if (matches.isEmpty()) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition();
		String dim = MapFeature.dimensionId(mc.level);
		matches.sort(Comparator.comparingInt((Match m) -> m.merchant().dimension.equals(dim) ? 0 : 1)
				.thenComparingInt(m -> m.offer().outOfStock() ? 1 : 0)
				.thenComparingInt(m -> m.offer().price())
				.thenComparingDouble(m -> m.merchant().distanceTo(eye)));
		int shown = Math.min(config.tooltipLines, matches.size());
		for (int i = 0; i < shown; i++) {
			Match match = matches.get(i);
			String key = match.offer().buying() ? "cobblecomfort.trade.tooltip.buys" : "cobblecomfort.trade.tooltip.sells";
			MutableComponent line = Component.translatable(key, displayName(match.merchant()), match.offer().buying()
					? priceText(match.offer()).copy().append(" → ").append(resultName(match.offer().result()))
					: priceText(match.offer()), where(match.merchant(), eye, dim));
			lines.add(match.offer().outOfStock() ? line.withStyle(ChatFormatting.DARK_GRAY) : line.withStyle(ChatFormatting.DARK_GREEN));
		}
		if (matches.size() > shown) {
			lines.add(Component.translatable("cobblecomfort.trade.tooltip.more", matches.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
