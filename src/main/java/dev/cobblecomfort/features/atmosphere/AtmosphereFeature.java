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
 * Small touches of atmosphere: breath in the cold, rings where someone wades through water, fireflies on warm nights
 * and snow settling on players in snowfall. All of it only on this client.
 */
public final class AtmosphereFeature extends Feature<AtmosphereConfig> {
	public static final AtmosphereFeature INSTANCE = new AtmosphereFeature();

	private static final double RANGE = 32;

	private final Motes motes = new Motes();
	private final Breath breath = new Breath();
	private final Splashes splashes = new Splashes();
	private final Fireflies fireflies = new Fireflies();
	private final SnowCover snow = new SnowCover();
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
		if (config.breath || config.splashes) {
			List<Movers.Mover> movers = Movers.around(level, camera, RANGE, true, true, false);
			if (config.breath) {
				breath.tick(mc, level, movers, motes, config, ticks);
			}
			if (config.splashes) {
				splashes.tick(level, movers, motes);
			}
		}
		if (config.fireflies) {
			fireflies.tick(level, camera, motes, config);
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
	}

	/** How much snow lies on a player, 0 to 1. */
	float snowOn(int id) {
		return isEnabled() && config.snowCover ? snow.amount(id) : 0f;
	}
}
