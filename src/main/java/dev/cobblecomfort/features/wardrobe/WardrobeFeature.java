package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.belt.BeltFeature;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.features.petplush.PetPlushFeature;
import dev.cobblecomfort.features.petplush.PlushLook;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.pets.PetStore;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> HatLayer.forget());
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new BadgeLayer(parent));
				helper.register(new HatLayer(parent));
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
		if (mc.player != null && mc.level != null && config.petals) {
			HatPetals.tick(mc);
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
			Identifier id = hat.unlock.id();
			boolean met = switch (hat.unlock.kind()) {
				case FREE -> true;
				case ADVANCEMENT -> progress.entrySet().stream().anyMatch(e -> e.getKey().id().equals(id) && e.getValue().isDone());
				case ITEM -> carries(mc.player.getInventory(), BuiltInRegistries.ITEM.getValue(id));
				case BIOME -> mc.level != null && mc.level.getBiome(mc.player.blockPosition()).is(ResourceKey.create(Registries.BIOME, id));
			};
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

	/** The hat you wear, or null (a seasonal one is put away out of its season). */
	public Hat hat() {
		Hat hat = Hat.byKey(config.hat);
		return hat != null && earned(hat) && hat.inSeason() ? hat : null;
	}

	/** Puts a hat on (null: none). */
	public void wearHat(Hat hat) {
		if (hat != null && (!earned(hat) || !hat.inSeason())) {
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

	/** How quickly the wreath's strands stop swinging. */
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
		PetStore store = PetFeature.INSTANCE.store();
		if (store.loaded()) {
			outfit.charm = store.charm();
			outfit.pack = store.pack();
		}
		outfit.plush = PetPlushFeature.INSTANCE.config().enabled;
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

	/** Puts an outfit on. Pets it names that this world doesn't know are left as they are. */
	public void wear(WardrobeConfig.Outfit outfit) {
		config.worn = new ArrayList<>(outfit.badges);
		config.hat = outfit.hat;
		save();
		PetStore store = PetFeature.INSTANCE.store();
		if (store.loaded()) {
			if (outfit.charm.isEmpty() || PetPlushFeature.NONE.equals(outfit.charm) || store.byUuid(outfit.charm) != null) {
				store.setCharm(outfit.charm);
			}
			if (outfit.pack.isEmpty() || store.byUuid(outfit.pack) != null) {
				store.setPack(outfit.pack);
			}
		}
		PetPlushFeature.INSTANCE.config().enabled = outfit.plush;
		PetPlushFeature.INSTANCE.save();
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
		return now.badges.equals(outfit.badges) && now.hat.equals(outfit.hat) && now.charm.equals(outfit.charm) && now.pack.equals(outfit.pack)
				&& now.plush == outfit.plush && now.bundles == outfit.bundles && now.melee == outfit.melee
				&& now.ranged == outfit.ranged && now.nameCharm == outfit.nameCharm;
	}

	public KeyMapping key() {
		return key;
	}
}
