package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.belt.BeltFeature;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.features.petplush.PetPlushFeature;
import dev.cobblecomfort.features.petplush.PlushLook;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.pets.PetEntry;
import dev.cobblecomfort.features.tracker.TrackerToast;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The wardrobe: one screen to dress your character, with badges pinned to the chest (free ones, and ones earned with
 * advancements), a hat in place of the helmet, the plush pets, the belt and the name charm, and outfits to switch
 * between. Opened with its key, a button on the player in the inventory, or from the map. Badges and hats are shared
 * with other players like the plushes.
 */
public final class WardrobeFeature extends Feature<WardrobeConfig> {
	public static final WardrobeFeature INSTANCE = new WardrobeFeature();

	private static final int EARN_CHECK_TICKS = 40;
	/** The inventory's player preview box, relative to the GUI's corner; the button sits in its bottom right. */
	private static final int PREVIEW_RIGHT = 75;
	private static final int PREVIEW_BOTTOM = 78;

	private KeyMapping key;
	private int ticks;

	private WardrobeFeature() {
		super("wardrobe", WardrobeConfig.class, WardrobeConfig::new);
	}

	@Override
	public void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cobblecomfort.wardrobe", InputConstants.KEY_J,
				CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory && isEnabled() && config.inventoryButton) {
				addInventoryButton(inventory);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			HatLayer.forget();
			NeckLayer.forget();
			BackLayer.forget();
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new BadgeLayer(parent));
				helper.register(new HatLayer(parent));
				helper.register(new NeckLayer(parent));
				helper.register(new BackLayer(parent));
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return WardrobeConfigUi.buildCategory(this);
	}

	private void tick(Minecraft mc) {
		if (!isEnabled()) {
			while (key.consumeClick()) {
				// Swallowed while off.
			}
			return;
		}
		while (key.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null) {
				open(null);
			}
		}
		if (mc.player != null && ++ticks % EARN_CHECK_TICKS == 0) {
			checkEarned(mc);
		}
		if (mc.player != null && mc.level != null && (config.petals || config.bellSound)) {
			HatPetals.tick(mc, config.petals, config.bellSound);
		}
	}

	public void open(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new WardrobeScreen(parent));
	}

	private void addInventoryButton(InventoryScreen screen) {
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		HangerButton button = new HangerButton(Component.translatable("cobblecomfort.wardrobe.open"), () -> open(screen));
		Screens.getWidgets(screen).add(button);
		// Follows the GUI when the recipe book moves it.
		ScreenEvents.beforeExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
			button.setX(gui.cobblecomfort$leftPos() + PREVIEW_RIGHT - HangerButton.SIZE - 1);
			button.setY(gui.cobblecomfort$topPos() + PREVIEW_BOTTOM - HangerButton.SIZE - 1);
		});
	}

	// ---------------------------------------------------------------- badges

	/** Earns the badges whose advancement is done in this world; they stay earned everywhere. */
	private void checkEarned(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return;
		}
		Map<AdvancementHolder, AdvancementProgress> progress = connection.getAdvancements().progress();
		checkHats(mc, progress);
		checkNecks(mc, progress);
		checkBack(mc, progress);
		List<Badge> fresh = new ArrayList<>();
		for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
			if (!entry.getValue().isDone()) {
				continue;
			}
			Identifier id = entry.getKey().id();
			for (Badge badge : Badge.values()) {
				if (id.equals(badge.advancement()) && !earned(badge)) {
					fresh.add(badge);
				}
			}
		}
		if (fresh.isEmpty()) {
			return;
		}
		for (Badge badge : fresh) {
			config.earned.add(badge.key());
		}
		save();
		if (config.announce) {
			Component names = Component.empty();
			for (int i = 0; i < fresh.size(); i++) {
				if (i > 0) {
					names = names.copy().append(", ");
				}
				names = names.copy().append(fresh.get(i).title());
			}
			mc.gui.toastManager().addToast(new TrackerToast(new ItemStack(Items.GOLD_NUGGET),
					Component.translatable("cobblecomfort.wardrobe.earned", names), earnedHint("earned")));
		}
	}

	/** Where to put it on: {@code what} is "earned" (badges) or "earned_hat". */
	private Component earnedHint(String what) {
		return key.isUnbound() ? Component.translatable("cobblecomfort.wardrobe." + what + "_hint_nokey")
				: Component.translatable("cobblecomfort.wardrobe." + what + "_hint", key.getTranslatedKeyMessage());
	}

	public boolean earned(Badge badge) {
		return badge.free() || config.earned.contains(badge.key());
	}

	/** The badges you wear, left to right. */
	public List<Badge> worn() {
		List<Badge> badges = new ArrayList<>();
		for (String key : config.worn) {
			Badge badge = Badge.byKey(key);
			if (badge != null && earned(badge) && !badges.contains(badge) && badges.size() < Badge.MAX_WORN) {
				badges.add(badge);
			}
		}
		return badges;
	}

	public boolean isWorn(Badge badge) {
		return worn().contains(badge);
	}

	/** Pins a badge on (if there is room) or takes it off. */
	public void toggle(Badge badge) {
		List<Badge> badges = worn();
		if (badges.contains(badge)) {
			badges.remove(badge);
		} else if (earned(badge) && badges.size() < Badge.MAX_WORN) {
			badges.add(badge);
		} else {
			return;
		}
		config.worn = new ArrayList<>(badges.stream().map(Badge::key).toList());
		save();
	}

	/** What you tell other players you wear. */
	public List<Badge> shared() {
		return isEnabled() && config.share ? worn() : List.of();
	}

	/** The badges this player wears, or an empty list. */
	List<Badge> badgesFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return List.of();
		}
		Minecraft mc = Minecraft.getInstance();
		if (player == mc.player) {
			return config.showOwn ? worn() : List.of();
		}
		if (!config.showOthers) {
			return List.of();
		}
		PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
		return look == null ? List.of() : look.badges();
	}

	// ---------------------------------------------------------------- hats

	/**
	 * Earns the hats whose condition is met: an advancement done in this world, an item in the inventory, or the biome
	 * stood in. They stay earned everywhere. Seasonal hats can only be earned in their months.
	 */
	private void checkHats(Minecraft mc, Map<AdvancementHolder, AdvancementProgress> progress) {
		List<Hat> fresh = new ArrayList<>();
		for (Hat hat : Hat.values()) {
			if (earned(hat) || !hat.inSeason()) {
				continue;
			}
			boolean met = met(mc, progress, hat.unlock);
			// The colours of a kind come together: the first stands for them all.
			if (met && !(hat.style.together && fresh.stream().anyMatch(other -> other.style == hat.style))) {
				fresh.add(hat);
			}
		}
		if (fresh.isEmpty()) {
			return;
		}
		for (Hat hat : fresh) {
			config.earnedHats.add(hat.key());
		}
		save();
		if (config.announce) {
			Component names = Component.empty();
			for (int i = 0; i < fresh.size(); i++) {
				if (i > 0) {
					names = names.copy().append(", ");
				}
				names = names.copy().append(fresh.get(i).title());
			}
			mc.gui.toastManager().addToast(new TrackerToast(fresh.getFirst().icon(),
					Component.translatable("cobblecomfort.wardrobe.earned_hat", names), earnedHint("earned_hat")));
		}
	}

	/** Whether an unlock's condition is met now: an advancement done in this world, an item carried, the biome stood in. */
	private static boolean met(Minecraft mc, Map<AdvancementHolder, AdvancementProgress> progress, Hat.Unlock unlock) {
		Identifier id = unlock.id();
		return switch (unlock.kind()) {
			case FREE -> true;
			case ADVANCEMENT -> progress.entrySet().stream().anyMatch(e -> e.getKey().id().equals(id) && e.getValue().isDone());
			case ITEM -> carries(mc.player.getInventory(), BuiltInRegistries.ITEM.getValue(id));
			case BIOME -> mc.level != null && mc.level.getBiome(mc.player.blockPosition()).is(ResourceKey.create(Registries.BIOME, id));
			case MUSIC_DISC -> carriesMusicDisc(mc.player.getInventory());
			case TAMED -> tamed(mc, id);
		};
	}

	/** Whether you have tamed an animal of this type: one remembered by the pet tracker, or one of yours nearby. */
	private static boolean tamed(Minecraft mc, Identifier type) {
		String id = type.toString();
		for (PetEntry pet : PetFeature.INSTANCE.store().all()) {
			if (pet.type.equals(id)) {
				return true;
			}
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof TamableAnimal animal && animal.isOwnedBy(mc.player)
					&& BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(type)) {
				return true;
			}
		}
		return false;
	}

	private static boolean carriesMusicDisc(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).has(DataComponents.JUKEBOX_PLAYABLE)) {
				return true;
			}
		}
		return false;
	}

	private static boolean carries(Inventory inventory, Item item) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a hat is yours: free, earned, or another colour of its kind earned (but crowns and wreaths each on their own). */
	public boolean earned(Hat hat) {
		if (hat.free() || config.earnedHats.contains(hat.key())) {
			return true;
		}
		if (hat.style.together) {
			for (Hat other : Hat.variants(hat.style)) {
				if (config.earnedHats.contains(other.key())) {
					return true;
				}
			}
		}
		return false;
	}

	/** The hat you wear, or null. */
	public Hat hat() {
		Hat hat = Hat.byKey(config.hat);
		return hat != null && earned(hat) ? hat : null;
	}

	/** Puts a hat on (null: none). */
	public void wearHat(Hat hat) {
		if (hat != null && !earned(hat)) {
			return;
		}
		config.hat = hat == null ? "" : hat.key();
		save();
	}

	/** What you tell other players you wear on your head. */
	public Hat sharedHat() {
		return isEnabled() && config.share ? hat() : null;
	}

	/** The hat this player wears, or null. */
	Hat hatFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return null;
		}
		if (player == Minecraft.getInstance().player) {
			return config.showOwn ? hat() : null;
		}
		if (!config.showOthers) {
			return null;
		}
		PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
		return look == null ? null : look.hat();
	}

	/** Whether the helmet of the player drawn here is hidden under their hat. */
	public boolean hidesHelmet(AvatarRenderState state) {
		if (state.getData(HatLayer.PREVIEW) != null) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.getEntity(state.id) instanceof AbstractClientPlayer player && hatFor(player) != null;
	}

	// ---------------------------------------------------------------- neck

	/** Earns the neck pieces whose condition is met; all colours of a kind at once, seasonal ones only in season. */
	private void checkNecks(Minecraft mc, Map<AdvancementHolder, AdvancementProgress> progress) {
		List<Neck> fresh = new ArrayList<>();
		for (Neck neck : Neck.values()) {
			if (!earned(neck) && neck.inSeason() && met(mc, progress, neck.unlock)
					&& fresh.stream().noneMatch(other -> other.style == neck.style)) {
				fresh.add(neck);
			}
		}
		if (fresh.isEmpty()) {
			return;
		}
		for (Neck neck : fresh) {
			config.earnedNecks.add(neck.key());
		}
		save();
		if (config.announce) {
			Component names = Component.empty();
			for (int i = 0; i < fresh.size(); i++) {
				if (i > 0) {
					names = names.copy().append(", ");
				}
				names = names.copy().append(fresh.get(i).style.title());
			}
			mc.gui.toastManager().addToast(new TrackerToast(fresh.getFirst().icon(),
					Component.translatable("cobblecomfort.wardrobe.earned_neck", names), earnedHint("earned_neck")));
		}
	}

	/** Whether a neck piece is yours: free, or a colour of its kind earned. */
	public boolean earned(Neck neck) {
		if (neck.free()) {
			return true;
		}
		for (Neck other : Neck.variants(neck.style)) {
			if (config.earnedNecks.contains(other.key())) {
				return true;
			}
		}
		return false;
	}

	/** What you wear round your neck, or null. */
	public Neck neck() {
		Neck neck = Neck.byKey(config.neck);
		return neck != null && earned(neck) ? neck : null;
	}

	/** Puts a neck piece on (null: none). */
	public void wearNeck(Neck neck) {
		if (neck != null && !earned(neck)) {
			return;
		}
		config.neck = neck == null ? "" : neck.key();
		save();
	}

	/** What you tell other players you wear round your neck. */
	public Neck sharedNeck() {
		return isEnabled() && config.share ? neck() : null;
	}

	/** What this player wears round the neck, or null. */
	Neck neckFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return null;
		}
		if (player == Minecraft.getInstance().player) {
			return config.showOwn ? neck() : null;
		}
		if (!config.showOthers) {
			return null;
		}
		PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
		return look == null ? null : look.neck();
	}

	// ---------------------------------------------------------------- back

	/** Earns the back pieces and charms whose condition is met; all colours of a kind at once. They stay earned. */
	private void checkBack(Minecraft mc, Map<AdvancementHolder, AdvancementProgress> progress) {
		List<BackPiece> pieces = new ArrayList<>();
		for (BackPiece piece : BackPiece.values()) {
			if (!earned(piece) && piece.inSeason() && met(mc, progress, piece.unlock)
					&& pieces.stream().noneMatch(other -> other.style == piece.style)) {
				pieces.add(piece);
			}
		}
		List<Trinket> trinkets = new ArrayList<>();
		for (Trinket trinket : Trinket.values()) {
			if (!earned(trinket) && trinket.inSeason() && met(mc, progress, trinket.unlock)
					&& trinkets.stream().noneMatch(other -> other.style == trinket.style)) {
				trinkets.add(trinket);
			}
		}
		if (pieces.isEmpty() && trinkets.isEmpty()) {
			return;
		}
		pieces.forEach(piece -> config.earnedBacks.add(piece.key()));
		trinkets.forEach(trinket -> config.earnedTrinkets.add(trinket.key()));
		save();
		if (config.announce) {
			Component names = Component.empty();
			List<Component> titles = new ArrayList<>();
			pieces.forEach(piece -> titles.add(piece.style.title()));
			trinkets.forEach(trinket -> titles.add(trinket.style.title()));
			for (int i = 0; i < titles.size(); i++) {
				names = names.copy().append(i > 0 ? Component.literal(", ") : Component.empty()).append(titles.get(i));
			}
			ItemStack icon = pieces.isEmpty() ? trinkets.getFirst().icon() : pieces.getFirst().icon();
			mc.gui.toastManager().addToast(new TrackerToast(icon, Component.translatable("cobblecomfort.wardrobe.earned_back", names),
					earnedHint("earned_back")));
		}
	}

	/** Whether a back piece is yours: free, or a colour of its kind earned. */
	public boolean earned(BackPiece piece) {
		return piece.free() || BackPiece.variants(piece.style).stream().anyMatch(other -> config.earnedBacks.contains(other.key()));
	}

	/** Whether a back charm is yours: free, or a variant of its kind earned. */
	public boolean earned(Trinket trinket) {
		return trinket.free() || Trinket.variants(trinket.style).stream().anyMatch(other -> config.earnedTrinkets.contains(other.key()));
	}

	/** What you carry on your back, or null. */
	public BackPiece back() {
		BackPiece piece = BackPiece.byKey(config.back);
		return piece != null && earned(piece) ? piece : null;
	}

	/** The charm hanging on your back, or null. */
	public Trinket trinket() {
		Trinket trinket = Trinket.byKey(config.trinket);
		return trinket != null && earned(trinket) ? trinket : null;
	}

	/** Puts a piece on your back (null: none). */
	public void wearBack(BackPiece piece) {
		if (piece != null && !earned(piece)) {
			return;
		}
		config.back = piece == null ? "" : piece.key();
		save();
	}

	/** Hangs a charm on your back (null: none). */
	public void wearTrinket(Trinket trinket) {
		if (trinket != null && !earned(trinket)) {
			return;
		}
		config.trinket = trinket == null ? "" : trinket.key();
		save();
	}

	/** What you tell other players you carry on your back. */
	public BackPiece sharedBack() {
		return isEnabled() && config.share ? back() : null;
	}

	/** The charm you tell other players you wear on your back. */
	public Trinket sharedTrinket() {
		return isEnabled() && config.share ? trinket() : null;
	}

	/** What this player carries on the back from the wardrobe, or null. */
	public BackPiece backFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return null;
		}
		if (player == Minecraft.getInstance().player) {
			return config.showOwn ? back() : null;
		}
		PlushLook look = config.showOthers ? PetPlushFeature.INSTANCE.shared(player.getUUID()) : null;
		return look == null ? null : look.back();
	}

	/** The wardrobe charm this player wears on the back, or null. */
	public Trinket trinketFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return null;
		}
		if (player == Minecraft.getInstance().player) {
			return config.showOwn ? trinket() : null;
		}
		PlushLook look = config.showOthers ? PetPlushFeature.INSTANCE.shared(player.getUUID()) : null;
		return look == null ? null : look.trinket();
	}

	/** How quickly the swinging parts of hats and neck pieces stop. */
	double damping() {
		return Mth.lerp(Mth.clamp(config.hatSwing, 0f, 1f), 7.0, 0.6);
	}

	// ---------------------------------------------------------------- outfits

	/** What is worn now, under a name. */
	public WardrobeConfig.Outfit capture(String name) {
		WardrobeConfig.Outfit outfit = new WardrobeConfig.Outfit();
		outfit.name = name;
		outfit.badges = new ArrayList<>(worn().stream().map(Badge::key).toList());
		Hat hat = hat();
		outfit.hat = hat == null ? "" : hat.key();
		Neck neck = neck();
		outfit.neck = neck == null ? "" : neck.key();
		outfit.back = config.back;
		outfit.trinket = config.trinket;
		outfit.bundles = BeltFeature.INSTANCE.config().bundles;
		outfit.melee = BeltFeature.INSTANCE.config().melee;
		outfit.ranged = BeltFeature.INSTANCE.config().ranged;
		outfit.nameCharm = NameCharmFeature.INSTANCE.config().enabled;
		return outfit;
	}

	public void saveOutfit(String name) {
		config.outfits.add(capture(name));
		save();
	}

	public void deleteOutfit(WardrobeConfig.Outfit outfit) {
		config.outfits.remove(outfit);
		save();
	}

	/** Puts an outfit on. */
	public void wear(WardrobeConfig.Outfit outfit) {
		config.worn = new ArrayList<>(outfit.badges);
		config.hat = outfit.hat;
		config.neck = outfit.neck;
		config.back = outfit.back;
		config.trinket = outfit.trinket;
		save();
		BeltFeature.INSTANCE.config().bundles = outfit.bundles;
		BeltFeature.INSTANCE.config().melee = outfit.melee;
		BeltFeature.INSTANCE.config().ranged = outfit.ranged;
		BeltFeature.INSTANCE.save();
		NameCharmFeature.INSTANCE.config().enabled = outfit.nameCharm;
		NameCharmFeature.INSTANCE.save();
	}

	/** Whether everything an outfit holds is worn right now. */
	public boolean wearing(WardrobeConfig.Outfit outfit) {
		WardrobeConfig.Outfit now = capture(outfit.name);
		return now.badges.equals(outfit.badges) && now.hat.equals(outfit.hat) && now.neck.equals(outfit.neck)
				&& now.back.equals(outfit.back) && now.trinket.equals(outfit.trinket)
				&& now.bundles == outfit.bundles && now.melee == outfit.melee
				&& now.ranged == outfit.ranged && now.nameCharm == outfit.nameCharm;
	}

	public KeyMapping key() {
		return key;
	}
}
