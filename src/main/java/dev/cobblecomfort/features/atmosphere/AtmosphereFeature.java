package dev.cobblecomfort.features.atmosphere;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.Movers;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Small touches of atmosphere: breath in the cold, rings where someone wades through water, fireflies on warm nights,
 * butterflies and dragonflies by day,
 * snow settling on players in snowfall and birds flying over. All of it only on this client.
 */
public final class AtmosphereFeature extends Feature<AtmosphereConfig> {
	public static final AtmosphereFeature INSTANCE = new AtmosphereFeature();

	private static final double RANGE = 32;

	private final Motes motes = new Motes();
	private final Breath breath = new Breath();
	private final Splashes splashes = new Splashes();
	private final Fireflies fireflies = new Fireflies();
	private final SnowCover snow = new SnowCover();
	private final Birds birds = new Birds();
	private final Insects insects = new Insects();
	private final Drips drips = new Drips();
	private final LandingDust landingDust = new LandingDust();
	private ClientLevel lastLevel;
	private int ticks;

	private AtmosphereFeature() {
		super("atmosphere", AtmosphereConfig.class, AtmosphereConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> clear());
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new SnowLayer(parent));
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return AtmosphereConfigUi.buildCategory(this);
	}

	private void clear() {
		motes.clear();
		snow.clear();
		birds.clear();
		insects.clear();
		drips.clear();
		landingDust.clear();
	}

	private void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (level == null || !isEnabled()) {
			clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		ticks++;
		motes.tick();
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		if (config.breath || config.splashes || config.landingDust) {
			List<Movers.Mover> movers = Movers.around(level, camera, RANGE, true, true, false);
			if (config.landingDust) {
				landingDust.tick(level, movers, motes);
			}
			if (config.breath) {
				breath.tick(mc, level, movers, motes, config, ticks);
			}
			if (config.splashes) {
				splashes.tick(level, movers, motes);
			}
		}
		if (config.drips) {
			drips.tick(level, camera, motes);
		} else {
			drips.clear();
		}
		if (config.fireflies) {
			fireflies.tick(level, camera, motes, config);
		}
		if (config.butterflies || config.dragonflies || insects.count(Insects.Kind.BUTTERFLY) + insects.count(Insects.Kind.DRAGONFLY) > 0) {
			insects.tick(level, camera, config, dev.cobblecomfort.features.wardrobe.WardrobeFeature.INSTANCE.companionPositions());
		}
		if (config.birds) {
			birds.tick(level, camera, config);
		} else {
			birds.clear();
		}
		if (config.snowCover) {
			snow.tick(level, camera);
		} else {
			snow.clear();
		}
	}

	private void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !isEnabled()) {
			return;
		}
		motes.render(context, mc.level, mc.gameRenderer.mainCamera(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		insects.render(context, mc.level, mc.gameRenderer.mainCamera().position(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (config.birds) {
			birds.render(context, mc.level, mc.gameRenderer.mainCamera().position(),
					mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
	}

	/** Whether water drips off what is wet (companions drip too). */
	public boolean drips() {
		return isEnabled() && config.drips;
	}

	/** How wet a player is, 0 to 1 (tests). */
	public float wetness(int id) {
		return drips() ? drips.wetness(id) : 0;
	}

	/** Where the butterflies around are (companions watch them and chase them). */
	public List<Vec3> butterflies() {
		return isEnabled() ? insects.butterflies() : List.of();
	}

	/** How many butterflies ({@code dragonflies} false) or dragonflies are about (tests). */
	public int insects(boolean dragonflies) {
		return insects.count(dragonflies ? Insects.Kind.DRAGONFLY : Insects.Kind.BUTTERFLY);
	}

	/** How many kinds of birds there are. */
	public int birdKinds() {
		return BirdSpecies.values().length;
	}

	/** The name of one kind of bird (screenshots). */
	public String birdKind(int kind) {
		return BirdSpecies.values()[kind].name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Hangs birds still in the air in front of the camera (screenshots): every kind in a row for {@code kind} -1, else
	 * that kind alone and close. Replaces the birds in the air.
	 */
	public void showcaseBirds(int kind) {
		Minecraft mc = Minecraft.getInstance();
		birds.clear();
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 side = new Vec3(-look.z, 0, look.x);
		BirdSpecies[] all = BirdSpecies.values();
		for (int i = 0; i < all.length; i++) {
			if (kind >= 0 && i != kind) {
				continue;
			}
			int row = i / 6, column = i % 6;
			Vec3 at = kind >= 0 ? eye.add(look.scale(1.3 + all[i].shape.scale * 12)).add(0, 0.25, 0)
					: eye.add(look.scale(3.2 + row * 0.4)).add(side.scale((column - 2.5) * 1.05)).add(0, 0.55 - row * 0.95, 0);
			Birds.Flock flock = birds.spawn(mc.level, at, all[i], true);
			flock.still = true;
			Birds.Bird bird = flock.birds.getFirst();
			flock.birds.clear();
			flock.birds.add(bird);
			bird.x = bird.xo = at.x;
			bird.y = bird.yo = at.y;
			bird.z = bird.zo = at.z;
			// Turned side-on, a little towards the camera.
			bird.yaw = bird.yawO = (float) Math.atan2(side.x, side.z) + 0.5f;
			bird.pitch = bird.pitchO = 0;
			bird.roll = bird.rollO = 0;
		}
	}

	/** How many flocks are in the air. */
	public int birdFlocks() {
		return birds.count();
	}

	/** How much snow lies on a player, 0 to 1. */
	float snowOn(int id) {
		return isEnabled() && config.snowCover ? snow.amount(id) : 0f;
	}
}
