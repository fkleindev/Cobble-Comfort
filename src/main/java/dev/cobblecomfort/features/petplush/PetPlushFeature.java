package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.IrisCompat;
import dev.cobblecomfort.features.belt.BeltFeature;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
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
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Draws the plushes worn on the back (a wolf or cat backpack or charm, picked in the wardrobe) and the charms from the
 * wardrobe, swinging with pendulum physics, and carries everything a player wears to others through the Cobble Comfort
 * Sync plugin. Players on older versions may still share plushes of their own pets; those are shown too.
 */
public final class PetPlushFeature extends Feature<PetPlushConfig> {
	public static final PetPlushFeature INSTANCE = new PetPlushFeature();

	private static final long FORGET_NANOS = 2_000_000_000L;

	private final PetPlushSync sync = new PetPlushSync();
	private final Int2ObjectOpenHashMap<Motion> motions = new Int2ObjectOpenHashMap<>();

	private PetPlushFeature() {
		super("pet_plush", PetPlushConfig.class, PetPlushConfig::new);
	}

	@Override
	public void init() {
		sync.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			sync.clear();
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
		// One look carries everything worn (belt, badges, hat, neck, back, totem), each shared if its feature allows.
		sync.tick(new PlushLook(null, null, BeltFeature.INSTANCE.shared(), BeltFeature.INSTANCE.sharedGear(),
				WardrobeFeature.INSTANCE.shared(), WardrobeFeature.INSTANCE.sharedHat(),
				WardrobeFeature.INSTANCE.sharedNeck(), WardrobeFeature.INSTANCE.sharedBack(),
				WardrobeFeature.INSTANCE.sharedTrinket(), WardrobeFeature.INSTANCE.sharedTotem()));
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

	/** The pet plushes a player on an older version shares, or null (our own come from the wardrobe). */
	PlushLook lookFor(AbstractClientPlayer player) {
		if (!isEnabled() || !config.showOthers || player == Minecraft.getInstance().player) {
			return null;
		}
		return sync.get(player.getUUID());
	}
}
