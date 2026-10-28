package dev.cobblecomfort.features.lookinfo;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.lookinfo.providers.BlockInfoProvider;
import dev.cobblecomfort.features.lookinfo.providers.EntityInfoProvider;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Shows information about the block or entity under the crosshair, like Jade. */
public final class LookInfoFeature extends Feature<LookInfoConfig> {
	public static final LookInfoFeature INSTANCE = new LookInfoFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("look_info");
	/** Vanilla top of the status bars (armor row) above the screen bottom, used before a player exists. */
	private static final int DEFAULT_STATUS_HEIGHT = 49;
	private static final float PLACEHOLDER_WIDTH = 110f;
	private static final float PLACEHOLDER_HEIGHT = 34f;

	private final LookInfoRenderer renderer = new LookInfoRenderer(this);
	private LookTarget current;

	private LookInfoFeature() {
		super("look_info", LookInfoConfig.class, LookInfoConfig::new);
	}

	@Override
	public void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HELD_ITEM_TOOLTIP, LAYER_ID, renderer);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	@Override
	public ConfigCategory buildCategory() {
		return LookInfoConfigUi.buildCategory(this);
	}

	public LookTarget current() {
		return current;
	}

	private void tick(Minecraft mc) {
		current = isEnabled() ? findTarget(mc) : null;
	}

	private LookTarget findTarget(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return null;
		}
		LookInfoConfig cfg = config;
		if (cfg.hideInSpectator && player.isSpectator()) {
			return null;
		}
		if (cfg.onlyWhenSneaking && !player.isShiftKeyDown()) {
			return null;
		}
		HitResult hit = mc.hitResult;

		if (cfg.showFluids) {
			LookTarget fluid = findFluid(mc, player, hit);
			if (fluid != null) {
				return fluid;
			}
		}
		if (hit instanceof EntityHitResult entityHit && cfg.showEntities) {
			return StorageFeature.INSTANCE.decorate(EntityInfoProvider.entity(cfg, entityHit.getEntity()), hit);
		}
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && cfg.showBlocks) {
			return StorageFeature.INSTANCE.decorate(BlockInfoProvider.block(mc, cfg, blockHit.getBlockPos()), hit);
		}
		return null;
	}

	/** Fluids are not hit by the vanilla crosshair ray, so cast a second ray that stops at them. */
	private LookTarget findFluid(Minecraft mc, LocalPlayer player, HitResult vanillaHit) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1f, true);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = blockHit.getBlockPos();
		if (!(mc.level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
			return null;
		}
		var eye = player.getEyePosition();
		if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS
				&& vanillaHit.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) {
			return null;
		}
		FluidState fluid = mc.level.getFluidState(pos);
		return fluid.isEmpty() ? null : BlockInfoProvider.fluid(config, pos, fluid);
	}

	// ---------------------------------------------------------------- layout

	/** Bottom edge of the panel: above the taller of the two status bar columns plus the configured gap. */
	public float defaultBottom(float screenHeight) {
		return screenHeight - statusHeight() - config.barGap;
	}

	/** Vanilla-position bounds for the HUD editor and the HUD customizer pivot. */
	public Rect defaultBounds(float screenWidth, float screenHeight) {
		float w = renderer.width() > 0 ? renderer.width() : PLACEHOLDER_WIDTH;
		float h = renderer.height() > 0 ? renderer.height() : PLACEHOLDER_HEIGHT;
		float bottom = defaultBottom(screenHeight);
		return new Rect(screenWidth / 2f - w / 2f, bottom - h, w, h);
	}

	private static int statusHeight() {
		if (Minecraft.getInstance().player == null) {
			return DEFAULT_STATUS_HEIGHT;
		}
		try {
			return Math.max(
					Math.max(HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR),
							HudStatusBarHeightRegistry.getHeight(VanillaHudElements.HEALTH_BAR)),
					Math.max(HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR),
							HudStatusBarHeightRegistry.getHeight(VanillaHudElements.FOOD_BAR)));
		} catch (RuntimeException e) {
			// The registry throws when there is no camera player (e.g. spectating a non-player).
			return DEFAULT_STATUS_HEIGHT;
		}
	}
}
