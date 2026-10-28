package dev.cobblecomfort.features.fullbright;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Toggleable full brightness that also works with shader packs. */
public final class FullbrightFeature extends Feature<FullbrightConfig> {
	public static final FullbrightFeature INSTANCE = new FullbrightFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("fullbright");
	private static final long SHADER_CHECK_MS = 1000;

	/** Handed out for the local player while faking night vision; hidden and without an icon. */
	private static final MobEffectInstance FAKE_NIGHT_VISION = new MobEffectInstance(MobEffects.NIGHT_VISION,
			MobEffectInstance.INFINITE_DURATION, 0, true, false, false);

	private KeyMapping key;
	private float progress;
	private long lastFrameNanos;
	private boolean shadersActive;
	private long lastShaderCheckMs;

	private FullbrightFeature() {
		super("fullbright", FullbrightConfig.class, FullbrightConfig::new);
	}

	@Override
	public void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.fullbright", InputConstants.KEY_G, CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				toggle(mc);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, LAYER_ID, (g, delta) -> drawIndicator(g));
	}

	@Override
	protected void onConfigLoaded() {
		if (!config.rememberState) {
			config.active = false;
		}
		progress = config.active ? 1f : 0f;
	}

	@Override
	public ConfigCategory buildCategory() {
		return FullbrightConfigUi.buildCategory(this);
	}

	public KeyMapping key() {
		return key;
	}

	private void toggle(Minecraft mc) {
		if (!isEnabled()) {
			return;
		}
		config.active = !config.active;
		if (config.rememberState) {
			save();
		}
		if (config.toggleMessage) {
			mc.gui.hud.setOverlayMessage(Component.translatable(config.active
					? "cobblecomfort.fullbright.on" : "cobblecomfort.fullbright.off")
					.withStyle(config.active ? ChatFormatting.YELLOW : ChatFormatting.GRAY), false);
		}
	}

	// ---------------------------------------------------------------- brightness

	/** Advances the fade; called once per frame from the lightmap update. Returns the current intensity. */
	public float updateIntensity() {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((now - lastFrameNanos) / 1e9f, 0.1f);
		lastFrameNanos = now;
		boolean on = isEnabled() && config.active;
		if (!config.smoothFade || config.fadeDuration <= 0f) {
			progress = on ? 1f : 0f;
		} else {
			float step = dt / config.fadeDuration;
			progress = Math.clamp(progress + (on ? step : -step), 0f, 1f);
		}
		return intensity();
	}

	public float intensity() {
		return progress <= 0f ? 0f : config.strength * config.easing.apply(progress);
	}

	/** True while the lightmap has to be recalculated every frame (on, or fading). */
	public boolean needsLightmapUpdate() {
		return progress > 0f || (isEnabled() && config.active);
	}

	/** Whether the brightness is applied through faked night vision instead of the lightmap. */
	public boolean usesNightVision() {
		return switch (config.method) {
			case LIGHTMAP -> false;
			case NIGHT_VISION -> true;
			case AUTO -> shaderPackInUse();
		};
	}

	/** The fake night vision effect for the local player, or null when not faking. */
	public MobEffectInstance fakeNightVision() {
		return intensity() > 0f && usesNightVision() ? FAKE_NIGHT_VISION : null;
	}

	/** Asks Iris (if installed) whether a shader pack is in use, via reflection to avoid a hard dependency. */
	private boolean shaderPackInUse() {
		long now = System.currentTimeMillis();
		if (now - lastShaderCheckMs < SHADER_CHECK_MS) {
			return shadersActive;
		}
		lastShaderCheckMs = now;
		shadersActive = false;
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object instance = api.getMethod("getInstance").invoke(null);
				shadersActive = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
			} catch (ReflectiveOperationException | ClassCastException e) {
				shadersActive = false;
			}
		}
		return shadersActive;
	}

	// ---------------------------------------------------------------- HUD indicator

	public Rect defaultBounds(float width, float height) {
		Font font = Minecraft.getInstance().font;
		return new Rect(4, 4, font.width(indicatorText()) + 2, font.lineHeight + 1);
	}

	private Component indicatorText() {
		return Component.literal("☀ ").withStyle(ChatFormatting.YELLOW)
				.append(Component.translatable("cobblecomfort.fullbright.hud").withStyle(ChatFormatting.WHITE));
	}

	private void drawIndicator(GuiGraphicsExtractor g) {
		if (!isEnabled() || !config.showHud) {
			return;
		}
		float alpha = HudEditorScreen.isOpen() ? 1f : Math.min(1f, progress);
		if (alpha <= 0.02f) {
			return;
		}
		g.text(Minecraft.getInstance().font, indicatorText(), 5, 5, RenderUtil.withAlpha(0xFFFFFFFF, alpha), true);
	}
}
