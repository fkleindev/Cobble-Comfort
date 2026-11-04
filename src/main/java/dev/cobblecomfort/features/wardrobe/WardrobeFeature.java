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
import dev.cobblecomfort.features.tracker.TrackerStorage;
import dev.cobblecomfort.features.tracker.TrackerToast;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The wardrobe: one screen to dress your character, with badges pinned to the chest (free ones, and ones earned with
 * advancements), a hat in place of the helmet, the plush pets, the belt and the name charm, and outfits to switch
 * between. Opened with its key, a button on the player in the inventory, or from the map. Badges and hats are shared
 * with other players like the plushes. What is earned counts in the world or server it was earned in.
 */
public final class WardrobeFeature extends Feature<WardrobeConfig> {
	public static final WardrobeFeature INSTANCE = new WardrobeFeature();

	private static final int EARN_CHECK_TICKS = 40;
	/** How far in front of a weapon or tool or behind it a charm may be tied, in item pixels. */
	public static final float MAX_SPOT_DEPTH = 4f;
	/** The inventory's player preview box, relative to the GUI's corner; the button sits in its bottom right. */
	private static final int PREVIEW_RIGHT = 75;
	private static final int PREVIEW_BOTTOM = 78;

	private KeyMapping key;
	private int ticks;
	/** The world or server played in, whose unlocks count; null outside one. */
	private String worldKey;

	private WardrobeFeature() {
		super("wardrobe", WardrobeConfig.class, WardrobeConfig::new);
	}

	@Override
	public void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.cobblecomfort.wardrobe", InputConstants.KEY_J,
				CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		PictureInPictureRendererRegistry.register(context -> new CharmSceneRenderer());
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory && isEnabled() && config.inventoryButton) {
				addInventoryButton(inventory);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> worldKey = TrackerStorage.currentWorldKey());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			worldKey = null;
			HatLayer.forget();
			FaceLayer.forget();
			NeckLayer.forget();
			BackLayer.forget();
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new BadgeLayer(parent));
				helper.register(new HatLayer(parent));
				helper.register(new FaceLayer(parent));
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
		migrateNameCharm();
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

	/** What has been earned in this world or server; outside one, nothing (and not kept). */
	private WardrobeConfig.Unlocks unlocks() {
		return worldKey == null ? new WardrobeConfig.Unlocks()
				: config.unlocks.computeIfAbsent(worldKey, k -> new WardrobeConfig.Unlocks());
	}

	/** Earns the badges whose advancement is done in this world; they stay earned in it. */
	private void checkEarned(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || worldKey == null) {
			return;
		}
		Map<AdvancementHolder, AdvancementProgress> progress = connection.getAdvancements().progress();
		checkHats(mc, progress);
		checkNecks(mc, progress);
		checkFaces(mc, progress);
		checkBack(mc, progress);
		checkTotems(mc, progress);
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
			unlocks().badges.add(badge.key());
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
		return badge.free() || unlocks().badges.contains(badge.key());
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
	 * stood in. They stay earned in this world. Seasonal hats can only be earned in their months.
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
			unlocks().hats.add(hat.key());
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
		List<String> hats = unlocks().hats;
		if (hat.free() || hats.contains(hat.key())) {
			return true;
		}
		if (hat.style.together) {
			for (Hat other : Hat.variants(hat.style)) {
				if (hats.contains(other.key())) {
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

	/** Whether this player is seen wearing a wardrobe hat. */
	public boolean wearsHat(AbstractClientPlayer player) {
		return hatFor(player) != null;
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
			unlocks().necks.add(neck.key());
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
		List<String> necks = unlocks().necks;
		for (Neck other : Neck.variants(neck.style)) {
			if (necks.contains(other.key())) {
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

	// ---------------------------------------------------------------- face

	/** Earns the face pieces whose condition is met; all colours of a kind at once, seasonal ones only in season. */
	private void checkFaces(Minecraft mc, Map<AdvancementHolder, AdvancementProgress> progress) {
		List<Face> fresh = new ArrayList<>();
		for (Face face : Face.values()) {
			if (!earned(face) && face.inSeason() && met(mc, progress, face.unlock)
					&& fresh.stream().noneMatch(other -> other.style == face.style)) {
				fresh.add(face);
			}
		}
		if (fresh.isEmpty()) {
			return;
		}
		for (Face face : fresh) {
			unlocks().faces.add(face.key());
		}
		save();
		if (config.announce) {
			Component names = Component.empty();
			for (int i = 0; i < fresh.size(); i++) {
				names = names.copy().append(i > 0 ? Component.literal(", ") : Component.empty()).append(fresh.get(i).style.title());
			}
			mc.gui.toastManager().addToast(new TrackerToast(fresh.getFirst().icon(),
					Component.translatable("cobblecomfort.wardrobe.earned_face", names), earnedHint("earned_face")));
		}
	}

	/** Whether a face piece is yours: free, or a colour of its kind earned. */
	public boolean earned(Face face) {
		List<String> faces = unlocks().faces;
		return face.free() || Face.variants(face.style).stream().anyMatch(other -> faces.contains(other.key()));
	}

	/** What you wear at this place on the face, or null. */
	public Face face(Face.Place place) {
		Face face = Face.byKey(switch (place) {
			case EYES -> config.eyes;
			case MOUTH -> config.mouth;
			case SKIN -> config.skin;
		});
		return face != null && face.style.place == place && earned(face) ? face : null;
	}

	/** Puts a piece on the face, in its place (null: takes off what is worn at {@code place}). */
	public void wearFace(Face.Place place, Face face) {
		if (face != null && (!earned(face) || face.style.place != place)) {
			return;
		}
		String key = face == null ? "" : face.key();
		switch (place) {
			case EYES -> config.eyes = key;
			case MOUTH -> config.mouth = key;
			case SKIN -> config.skin = key;
		}
		save();
	}

	/** What you tell other players you wear at this place on the face. */
	public Face sharedFace(Face.Place place) {
		return isEnabled() && config.share ? face(place) : null;
	}

	/** What this player wears on the face: the paint on the skin first, then eyes and mouth; empty for none. */
	List<Face> facesFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return List.of();
		}
		Face eyes;
		Face mouth;
		Face skin;
		if (player == Minecraft.getInstance().player) {
			if (!config.showOwn) {
				return List.of();
			}
			eyes = face(Face.Place.EYES);
			mouth = face(Face.Place.MOUTH);
			skin = face(Face.Place.SKIN);
		} else {
			PlushLook look = config.showOthers ? PetPlushFeature.INSTANCE.shared(player.getUUID()) : null;
			if (look == null) {
				return List.of();
			}
			eyes = look.eyes();
			mouth = look.mouth();
			skin = look.skin();
		}
		List<Face> faces = new ArrayList<>(3);
		// Paint first: it lies on the skin, under the rest.
		if (skin != null) {
			faces.add(skin);
		}
		if (eyes != null) {
			faces.add(eyes);
		}
		if (mouth != null) {
			faces.add(mouth);
		}
		return faces;
	}

	// ---------------------------------------------------------------- back

	/** Earns the back pieces and charms whose condition is met; all colours of a kind at once. They stay earned in this world. */
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
		List<ToolCharm> tools = new ArrayList<>();
		for (ToolCharm charm : ToolCharm.values()) {
			if (!earned(charm) && charm.inSeason() && met(mc, progress, charm.unlock)
					&& tools.stream().noneMatch(other -> other.style == charm.style)) {
				tools.add(charm);
			}
		}
		if (pieces.isEmpty() && trinkets.isEmpty() && tools.isEmpty()) {
			return;
		}
		WardrobeConfig.Unlocks unlocks = unlocks();
		pieces.forEach(piece -> unlocks.backs.add(piece.key()));
		trinkets.forEach(trinket -> unlocks.trinkets.add(trinket.key()));
		tools.forEach(charm -> unlocks.toolCharms.add(charm.key()));
		save();
		if (config.announce) {
			Component names = Component.empty();
			List<Component> titles = new ArrayList<>();
			pieces.forEach(piece -> titles.add(piece.style.title()));
			trinkets.forEach(trinket -> titles.add(trinket.style.title()));
			tools.forEach(charm -> titles.add(charm.style.title()));
			for (int i = 0; i < titles.size(); i++) {
				names = names.copy().append(i > 0 ? Component.literal(", ") : Component.empty()).append(titles.get(i));
			}
			ItemStack icon = !pieces.isEmpty() ? pieces.getFirst().icon() : !trinkets.isEmpty() ? trinkets.getFirst().icon() : tools.getFirst().icon();
			mc.gui.toastManager().addToast(new TrackerToast(icon, Component.translatable("cobblecomfort.wardrobe.earned_back", names),
					earnedHint("earned_back")));
		}
	}

	/** Whether a back piece is yours: free, or a colour of its kind earned. */
	public boolean earned(BackPiece piece) {
		List<String> backs = unlocks().backs;
		return piece.free() || BackPiece.variants(piece.style).stream().anyMatch(other -> backs.contains(other.key()));
	}

	/** Whether a back charm is yours: free, or a variant of its kind earned. */
	public boolean earned(Trinket trinket) {
		List<String> trinkets = unlocks().trinkets;
		return trinket.free() || Trinket.variants(trinket.style).stream().anyMatch(other -> trinkets.contains(other.key()));
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

	// ---------------------------------------------------------------- totem

	/** Earns the totem skins whose condition is met; all colours of a kind at once, seasonal ones only in season. */
	private void checkTotems(Minecraft mc, Map<AdvancementHolder, AdvancementProgress> progress) {
		List<TotemSkin> fresh = new ArrayList<>();
		for (TotemSkin skin : TotemSkin.values()) {
			if (!earned(skin) && skin.inSeason() && met(mc, progress, skin.unlock)
					&& fresh.stream().noneMatch(other -> other.style == skin.style)) {
				fresh.add(skin);
			}
		}
		if (fresh.isEmpty()) {
			return;
		}
		for (TotemSkin skin : fresh) {
			unlocks().totems.add(skin.key());
		}
		save();
		if (config.announce) {
			Component names = Component.empty();
			for (int i = 0; i < fresh.size(); i++) {
				names = names.copy().append(i > 0 ? Component.literal(", ") : Component.empty()).append(fresh.get(i).style.title());
			}
			mc.gui.toastManager().addToast(new TrackerToast(fresh.getFirst().icon(),
					Component.translatable("cobblecomfort.wardrobe.earned_totem", names), earnedHint("earned_totem")));
		}
	}

	/** Whether a totem skin is yours: free, or a colour of its kind earned. */
	public boolean earned(TotemSkin skin) {
		List<String> totems = unlocks().totems;
		return skin.free() || TotemSkin.variants(skin.style).stream().anyMatch(other -> totems.contains(other.key()));
	}

	/** How your totems look: the plush of you unless another skin is chosen. */
	public TotemSkin totem() {
		TotemSkin skin = TotemSkin.byKey(config.totem);
		return skin != null && earned(skin) ? skin : TotemSkin.PLAYER;
	}

	/** Chooses how totems look (null: the plush of you). */
	public void wearTotem(TotemSkin skin) {
		if (skin != null && !earned(skin)) {
			return;
		}
		config.totem = skin == null || skin == TotemSkin.PLAYER ? "" : skin.key();
		save();
	}

	/** What you tell other players your totems look like (null: the plush of you). */
	public TotemSkin sharedTotem() {
		TotemSkin skin = isEnabled() && config.share ? totem() : null;
		return skin == TotemSkin.PLAYER ? null : skin;
	}

	/**
	 * How a totem held by this player looks: your choice for yours (and for one lying about, with no holder), theirs
	 * as they share it for another player's, else the plush of them.
	 */
	public TotemSkin totemFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return TotemSkin.PLAYER;
		}
		if (player == null || player == Minecraft.getInstance().player) {
			return totem();
		}
		PlushLook look = config.showOthers ? PetPlushFeature.INSTANCE.shared(player.getUUID()) : null;
		return look == null || look.totem() == null ? TotemSkin.PLAYER : look.totem();
	}

	/** The hat a Mini-Me totem of this player wears: the one they wear (yours for one with no holder), or null. */
	public Hat miniMeHat(AbstractClientPlayer player) {
		if (player == null || player == Minecraft.getInstance().player) {
			return hat();
		}
		PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
		return look == null ? null : look.hat();
	}

	/** The neck piece a Mini-Me totem of this player wears, like {@link #miniMeHat}. */
	public Neck miniMeNeck(AbstractClientPlayer player) {
		if (player == null || player == Minecraft.getInstance().player) {
			return neck();
		}
		PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
		return look == null ? null : look.neck();
	}

	// ---------------------------------------------------------------- weapon and tool charms

	/** Whether a tool charm is yours: free, or a variant of its kind earned. */
	public boolean earned(ToolCharm charm) {
		List<String> earned = unlocks().toolCharms;
		return charm.free() || ToolCharm.variants(charm.style).stream().anyMatch(other -> earned.contains(other.key()));
	}

	/** The charm on your weapons or tools of this kind: the name tag unless another (or none) is chosen. */
	public ToolCharm toolCharm(ToolKind kind) {
		String key = config.toolCharms.get(kind.key());
		if (key == null) {
			return ToolCharm.NAME_TAG;
		}
		ToolCharm charm = ToolCharm.byKey(key);
		return charm != null && earned(charm) ? charm : null;
	}

	/** Ties a charm to every weapon or tool of this kind (null: none). */
	public void wearToolCharm(ToolKind kind, ToolCharm charm) {
		if (charm != null && !earned(charm)) {
			return;
		}
		config.toolCharms.put(kind.key(), charm == null ? "" : charm.key());
		save();
	}

	/** The charm on each kind as you tell other players, or null when not shared (they see the name tag then). */
	public Map<ToolKind, ToolCharm> sharedToolCharms() {
		if (!isEnabled() || !config.share) {
			return null;
		}
		Map<ToolKind, ToolCharm> charms = new EnumMap<>(ToolKind.class);
		for (ToolKind kind : ToolKind.values()) {
			charms.put(kind, toolCharm(kind));
		}
		return charms;
	}

	/**
	 * The charm on a weapon or tool of this kind held by {@code holder}: your choice for yours (and for one with no
	 * holder), a player's as they share it, else the name tag (mobs, players without the mod or who don't share).
	 */
	public ToolCharm toolCharmFor(LivingEntity holder, ToolKind kind) {
		Minecraft mc = Minecraft.getInstance();
		if (holder == null || holder == mc.player) {
			return isEnabled() ? toolCharm(kind) : ToolCharm.NAME_TAG;
		}
		if (!isEnabled() || !config.showOthers || !(holder instanceof AbstractClientPlayer player)) {
			return ToolCharm.NAME_TAG;
		}
		PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
		Map<ToolKind, ToolCharm> charms = look == null ? null : look.toolCharms();
		return charms == null || !charms.containsKey(kind) ? ToolCharm.NAME_TAG : charms.get(kind);
	}

	/**
	 * Where your charms are tied on weapons and tools of this kind, in item pixels ({@link ToolKind#defaultSpot}):
	 * where you moved them in the charm editor, else the default.
	 */
	public Vector3f charmSpot(ToolKind kind, boolean firstPerson) {
		WardrobeConfig.CharmSpot spot = config.charmSpots.get(kind.key());
		if (spot == null) {
			return kind.defaultSpot(firstPerson);
		}
		return firstPerson ? new Vector3f(spot.nearX, spot.nearY, spot.nearZ) : new Vector3f(spot.x, spot.y, spot.z);
	}

	/** Whether charms on this kind are tied somewhere else than by default. */
	public boolean charmSpotMoved(ToolKind kind) {
		return config.charmSpots.containsKey(kind.key());
	}

	/** Moves where charms are tied on this kind (not saved: {@link #save()} once done). */
	public void moveCharmSpot(ToolKind kind, boolean firstPerson, Vector3fc to) {
		WardrobeConfig.CharmSpot spot = config.charmSpots.get(kind.key());
		if (spot == null) {
			spot = new WardrobeConfig.CharmSpot();
			Vector3f far = kind.defaultSpot(false);
			Vector3f near = kind.defaultSpot(true);
			spot.x = far.x;
			spot.y = far.y;
			spot.z = far.z;
			spot.nearX = near.x;
			spot.nearY = near.y;
			spot.nearZ = near.z;
			config.charmSpots.put(kind.key(), spot);
		}
		Vector3f clamped = clampSpot(to);
		if (firstPerson) {
			spot.nearX = clamped.x;
			spot.nearY = clamped.y;
			spot.nearZ = clamped.z;
		} else {
			spot.x = clamped.x;
			spot.y = clamped.y;
			spot.z = clamped.z;
		}
	}

	/** Ties charms on this kind where they are by default again. */
	public void resetCharmSpot(ToolKind kind) {
		config.charmSpots.remove(kind.key());
		save();
	}

	/** Within the item, and no further than a few pixels in front of it or behind. */
	public static Vector3f clampSpot(Vector3fc spot) {
		return new Vector3f(Mth.clamp(spot.x(), 0f, 16f), Mth.clamp(spot.y(), 0f, 16f), Mth.clamp(spot.z(), -MAX_SPOT_DEPTH, MAX_SPOT_DEPTH));
	}

	/** Where charms are tied on each kind as you tell other players (third person, only the moved ones), or null. */
	public Map<ToolKind, Vector3fc> sharedCharmSpots() {
		if (!isEnabled() || !config.share) {
			return null;
		}
		Map<ToolKind, Vector3fc> spots = new EnumMap<>(ToolKind.class);
		for (ToolKind kind : ToolKind.values()) {
			if (charmSpotMoved(kind)) {
				spots.put(kind, charmSpot(kind, false));
			}
		}
		return spots;
	}

	/**
	 * Where a charm is tied on a weapon or tool of this kind held by {@code holder}, in item pixels: yours for yours (and
	 * for one with no holder), a player's as they share it, else the default.
	 */
	public Vector3f charmSpotFor(LivingEntity holder, ToolKind kind, boolean firstPerson) {
		Minecraft mc = Minecraft.getInstance();
		if (holder == null || holder == mc.player) {
			return charmSpot(kind, firstPerson);
		}
		if (isEnabled() && config.showOthers && holder instanceof AbstractClientPlayer player) {
			PlushLook look = PetPlushFeature.INSTANCE.shared(player.getUUID());
			Map<ToolKind, Vector3fc> spots = look == null ? null : look.charmSpots();
			Vector3fc spot = spots == null ? null : spots.get(kind);
			if (spot != null) {
				return clampSpot(spot);
			}
		}
		return kind.defaultSpot(firstPerson);
	}

	/** The name charm used to be switched on and off as a whole: switched off, it becomes no charm on any kind. */
	private void migrateNameCharm() {
		if (config.toolCharmsMigrated) {
			return;
		}
		config.toolCharmsMigrated = true;
		NameCharmFeature names = NameCharmFeature.INSTANCE;
		if (!names.config().enabled) {
			for (ToolKind kind : ToolKind.values()) {
				config.toolCharms.putIfAbsent(kind.key(), "");
			}
			names.config().enabled = true;
			names.save();
		}
		save();
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
		Face eyes = face(Face.Place.EYES);
		outfit.eyes = eyes == null ? "" : eyes.key();
		Face mouth = face(Face.Place.MOUTH);
		outfit.mouth = mouth == null ? "" : mouth.key();
		Face skin = face(Face.Place.SKIN);
		outfit.skin = skin == null ? "" : skin.key();
		outfit.back = config.back;
		outfit.trinket = config.trinket;
		outfit.totem = config.totem;
		outfit.bundles = BeltFeature.INSTANCE.config().bundles;
		outfit.melee = BeltFeature.INSTANCE.config().melee;
		outfit.ranged = BeltFeature.INSTANCE.config().ranged;
		for (ToolKind kind : ToolKind.values()) {
			ToolCharm charm = toolCharm(kind);
			outfit.toolCharms.put(kind.key(), charm == null ? "" : charm.key());
		}
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
		config.eyes = outfit.eyes;
		config.mouth = outfit.mouth;
		config.skin = outfit.skin;
		config.back = outfit.back;
		config.trinket = outfit.trinket;
		config.totem = outfit.totem;
		save();
		BeltFeature.INSTANCE.config().bundles = outfit.bundles;
		BeltFeature.INSTANCE.config().melee = outfit.melee;
		BeltFeature.INSTANCE.config().ranged = outfit.ranged;
		BeltFeature.INSTANCE.save();
		// Outfits from before tool charms only knew whether the name charm was on.
		config.toolCharms = new HashMap<>(outfit.toolCharms);
		if (outfit.toolCharms.isEmpty() && !outfit.nameCharm) {
			for (ToolKind kind : ToolKind.values()) {
				config.toolCharms.put(kind.key(), "");
			}
		}
		save();
	}

	/** Whether everything an outfit holds is worn right now. */
	public boolean wearing(WardrobeConfig.Outfit outfit) {
		WardrobeConfig.Outfit now = capture(outfit.name);
		return now.badges.equals(outfit.badges) && now.hat.equals(outfit.hat) && now.neck.equals(outfit.neck)
				&& now.eyes.equals(outfit.eyes) && now.mouth.equals(outfit.mouth) && now.skin.equals(outfit.skin)
				&& now.back.equals(outfit.back) && now.trinket.equals(outfit.trinket) && now.totem.equals(outfit.totem)
				&& now.bundles == outfit.bundles && now.melee == outfit.melee
				&& now.ranged == outfit.ranged && sameToolCharms(now, outfit);
	}

	/** Whether two outfits put the same charms on weapons and tools (one from before tool charms by its name charm switch). */
	private static boolean sameToolCharms(WardrobeConfig.Outfit now, WardrobeConfig.Outfit outfit) {
		for (ToolKind kind : ToolKind.values()) {
			String theirs = outfit.toolCharms.getOrDefault(kind.key(), outfit.nameCharm || !outfit.toolCharms.isEmpty() ? ToolCharm.NAME_TAG.key() : "");
			if (!now.toolCharms.get(kind.key()).equals(theirs)) {
				return false;
			}
		}
		return true;
	}

	public KeyMapping key() {
		return key;
	}
}
