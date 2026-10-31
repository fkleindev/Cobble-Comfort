package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.IrisCompat;
import dev.cobblecomfort.features.belt.BeltFeature;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import dev.cobblecomfort.features.pets.PetEntry;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.pets.PetStore;
import dev.cobblecomfort.features.plush.PlushJiggle;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A little plush of your wolf or cat dangles from your back, swinging with pendulum physics. You pick the pet in the
 * pets tab of the storage screen (or the one seen last is used). Other players with the mod see it through the Cobble
 * Comfort Sync plugin; without it, through their own pets that are nearby.
 */
public final class PetPlushFeature extends Feature<PetPlushConfig> {
	public static final PetPlushFeature INSTANCE = new PetPlushFeature();
	public static final String NONE = "none";

	private static final int NEARBY_SCAN_TICKS = 20;
	private static final long FORGET_NANOS = 2_000_000_000L;

	private final PetPlushSync sync = new PetPlushSync();
	/** Owner UUID -> plush of their tamed wolf or cat nearest to us, refreshed every second. */
	private Map<UUID, PetPlush> nearby = Map.of();
	private final Int2ObjectOpenHashMap<Motion> motions = new Int2ObjectOpenHashMap<>();
	private int ticks;

	private PetPlushFeature() {
		super("pet_plush", PetPlushConfig.class, PetPlushConfig::new);
	}

	@Override
	public void init() {
		sync.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			sync.clear();
			nearby = Map.of();
			motions.clear();
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new PetPlushLayer(parent));
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return PetPlushConfigUi.buildCategory(this);
	}

	private void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		// One look carries everything worn: pet plushes, belt and badges, each shared if its feature allows.
		PlushLook pets = isEnabled() && config.share ? ownLook() : PlushLook.NONE;
		sync.tick(new PlushLook(pets.charm(), pets.pack(), BeltFeature.INSTANCE.shared(), BeltFeature.INSTANCE.sharedGear(),
				WardrobeFeature.INSTANCE.shared(), WardrobeFeature.INSTANCE.sharedHat()));
		if (++ticks % NEARBY_SCAN_TICKS == 0) {
			nearby = isEnabled() && config.nearbyPets ? scanNearby(mc) : Map.of();
		}
	}

	/** Speed lost per second: little with a long swing, a lot with a short one. */
	double damping() {
		return Mth.lerp(Mth.clamp(config.swing, 0f, 1f), 7.0, 0.6);
	}

	/** How one player's plush moves: the string swinging, and head, legs and tail jiggling. */
	static final class Motion {
		final CharmPendulum pendulum = new CharmPendulum();
		final PlushJiggle jiggle = new PlushJiggle();
		private long lastJiggle;

		/** Advances the jiggle once per frame (the layer may draw a player more than once). */
		void updateJiggle(LivingEntity holder, long now) {
			if (IrisCompat.inShadowPass()) {
				return;
			}
			float dt = (now - lastJiggle) / 1e9f;
			if (dt < 0.001f) {
				return;
			}
			lastJiggle = now;
			jiggle.update(holder, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false), dt);
		}
	}

	Motion motion(int entityId) {
		long now = System.nanoTime();
		Motion motion = motions.get(entityId);
		if (motion == null) {
			motion = new Motion();
			motion.pendulum.lastSeen = now;
			motions.put(entityId, motion);
			if (motions.size() > 64) {
				motions.int2ObjectEntrySet().removeIf(e -> now - e.getValue().pendulum.lastSeen > FORGET_NANOS);
			}
		}
		return motion;
	}

	/** What another player shared through the server plugin (plushes and belt bundles), or null. */
	public PlushLook shared(UUID player) {
		return sync.get(player);
	}

	/** What this player wears, or null. */
	PlushLook lookFor(AbstractClientPlayer player) {
		if (!isEnabled()) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player == mc.player) {
			return config.showOwn ? ownLook() : null;
		}
		if (!config.showOthers) {
			return null;
		}
		PlushLook shared = sync.get(player.getUUID());
		if (shared != null) {
			return shared;
		}
		PetPlush nearbyPet = nearby.get(player.getUUID());
		return nearbyPet == null ? null : new PlushLook(nearbyPet, null);
	}

	// ---------------------------------------------------------------- choosing

	/**
	 * What this player wears: the pet chosen as backpack, and the pet chosen as charm (by default the wolf or cat seen
	 * last that isn't the backpack). Without remembered pets, the nearest one of ours as charm.
	 */
	public PlushLook ownLook() {
		PetStore store = PetFeature.INSTANCE.store();
		if (PetFeature.INSTANCE.active()) {
			PetEntry pack = packPet(store);
			PetEntry charm = charmPet(store);
			return new PlushLook(charm == null ? null : PetPlush.of(charm), pack == null ? null : PetPlush.of(pack));
		}
		Minecraft mc = Minecraft.getInstance();
		PetPlush nearbyPet = mc.player == null ? null : nearby.get(mc.player.getUUID());
		return new PlushLook(nearbyPet, null);
	}

	private static PetEntry packPet(PetStore store) {
		PetEntry pet = store.pack().isEmpty() ? null : store.byUuid(store.pack());
		return wearable(pet) ? pet : null;
	}

	private static PetEntry charmPet(PetStore store) {
		String choice = store.charm();
		if (NONE.equals(choice)) {
			return null;
		}
		PetEntry pack = packPet(store);
		PetEntry chosen = choice.isEmpty() ? null : store.byUuid(choice);
		if (wearable(chosen) && chosen != pack) {
			return chosen;
		}
		// Automatic: the wolf or cat seen last, other than the backpack.
		PetEntry latest = null;
		for (PetEntry pet : store.all()) {
			if (wearable(pet) && pet != pack && (latest == null || pet.seen > latest.seen)) {
				latest = pet;
			}
		}
		return latest;
	}

	private static boolean wearable(PetEntry pet) {
		return pet != null && !pet.dead() && PetPlush.of(pet) != null;
	}

	/** Whether this remembered pet hangs as the charm right now (chosen, or picked automatically). */
	public boolean isCharm(PetEntry pet) {
		return charmPet(PetFeature.INSTANCE.store()) == pet;
	}

	/** Whether this remembered pet is worn as the backpack. */
	public boolean isPack(PetEntry pet) {
		return packPet(PetFeature.INSTANCE.store()) == pet;
	}

	/** Hangs a pet on as the charm, or takes it off again (then no charm hangs there). */
	public void toggleCharm(PetEntry pet) {
		PetStore store = PetFeature.INSTANCE.store();
		if (isCharm(pet)) {
			store.setCharm(NONE);
			return;
		}
		if (isPack(pet)) {
			store.setPack("");
		}
		store.setCharm(pet.uuid);
	}

	/** Puts a pet on as the backpack, or takes it off again. A pet can't be the charm at the same time. */
	public void togglePack(PetEntry pet) {
		PetStore store = PetFeature.INSTANCE.store();
		if (isPack(pet)) {
			store.setPack("");
			return;
		}
		if (store.charm().equals(pet.uuid)) {
			store.setCharm("");
		}
		store.setPack(pet.uuid);
	}

	/** Whether a remembered pet can be worn as a plush (a living wolf or cat). */
	public static boolean canWear(PetEntry pet) {
		return wearable(pet);
	}

	// ---------------------------------------------------------------- nearby pets

	/** For every owner, the plush of their tamed wolf or cat nearest to them, among the entities loaded here. */
	private static Map<UUID, PetPlush> scanNearby(Minecraft mc) {
		Map<UUID, PetPlush> found = new HashMap<>();
		Map<UUID, Double> distance = new HashMap<>();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof TamableAnimal animal) || !animal.isTame() || ((LivingEntity) animal).isDeadOrDying()) {
				continue;
			}
			PetPlush plush = PetPlush.of(entity);
			EntityReference<LivingEntity> owner = animal.getOwnerReference();
			if (plush == null || owner == null) {
				continue;
			}
			UUID uuid = owner.getUUID();
			Entity ownerEntity = mc.level.getPlayerByUUID(uuid);
			double d = ownerEntity == null ? Double.MAX_VALUE : ownerEntity.distanceToSqr(entity);
			if (d < distance.getOrDefault(uuid, Double.POSITIVE_INFINITY)) {
				distance.put(uuid, d);
				found.put(uuid, plush);
			}
		}
		return found;
	}
}
