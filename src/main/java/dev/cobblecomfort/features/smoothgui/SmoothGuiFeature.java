package dev.cobblecomfort.features.smoothgui;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.transitions.TransitionsFeature;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix3x2fStack;

/**
 * Screens pop in: a short scale + fade (chat slides up), with the background dimming and blur fading in on their
 * own and container slots popping in a quick wave. Input works right away; closing is instant.
 */
public final class SmoothGuiFeature extends Feature<SmoothGuiConfig> {
	public static final SmoothGuiFeature INSTANCE = new SmoothGuiFeature();

	/** Share of the duration over which the slot wave starts; each slot pops for the rest. */
	private static final float STAGGER_SHARE = 0.55f;
	private static final float SLOT_START_SCALE = 0.4f;
	private static final float FADE_SHARE = 0.7f;

	private enum Kind {
		CONTAINER, MENU, CHAT
	}

	private Screen animated;
	private Kind kind;
	private long startMs;
	/** Transform applied to the current screen, needed to undo it for the background. */
	private float scale = 1f;
	private float offsetY;
	private float centerX, centerY;
	private boolean inScreen;
	private float guiAlpha = 1f;

	private SmoothGuiFeature() {
		super("smooth_gui", SmoothGuiConfig.class, SmoothGuiConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return SmoothGuiConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- lifecycle

	/** Called when a screen is set; decides whether the new screen animates in. */
	public void onScreenChange(Screen previous, Screen next) {
		animated = null;
		if (next == null || !isEnabled() || Minecraft.getInstance().level == null) {
			return;
		}
		if (config.onlyFromGame && previous != null) {
			return;
		}
		// The death screen has its own, slower fade.
		if (next instanceof DeathScreen && TransitionsFeature.INSTANCE.animatesDeath()) {
			return;
		}
		Kind nextKind = next instanceof AbstractContainerScreen<?> ? Kind.CONTAINER
				: next instanceof ChatScreen ? Kind.CHAT : Kind.MENU;
		boolean enabled = switch (nextKind) {
			case CONTAINER -> config.containers;
			case CHAT -> config.chat;
			case MENU -> config.menus;
		};
		if (enabled) {
			animated = next;
			kind = nextKind;
			startMs = System.currentTimeMillis();
		}
	}

	private float progress() {
		return Math.min(1f, (System.currentTimeMillis() - startMs) / (Math.max(0.03f, config.duration) * 1000f));
	}

	private boolean animating(Screen screen) {
		return screen == animated && progress() < 1f;
	}

	// ---------------------------------------------------------------- screen transform

	/** Wraps the whole screen render: applies scale/slide and the fade. Call {@link #end} afterwards. */
	public void begin(GuiGraphicsExtractor g, Screen screen) {
		inScreen = false;
		if (!isEnabled() || screen != animated) {
			return;
		}
		float t = progress();
		if (t >= 1f && backgroundProgress() >= 1f) {
			animated = null;
			return;
		}
		inScreen = true;
		Easing easing = config.bounce ? Easing.EASE_OUT_BACK : Easing.EASE_OUT_CUBIC;
		float eased = easing.apply(t);
		centerX = g.guiWidth() / 2f;
		centerY = g.guiHeight() / 2f;
		if (kind == Kind.CHAT) {
			scale = 1f;
			offsetY = (1f - Easing.EASE_OUT_CUBIC.apply(t)) * config.chatSlide;
		} else {
			scale = config.startScale + (1f - config.startScale) * eased;
			offsetY = 0f;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY + offsetY);
		pose.scale(scale);
		pose.translate(-centerX, -centerY);
		guiAlpha = config.fade ? Easing.EASE_OUT_CUBIC.apply(Math.min(1f, t / FADE_SHARE)) : 1f;
		ScreenFade.set(guiAlpha);
	}

	public void end(GuiGraphicsExtractor g) {
		if (inScreen) {
			g.pose().popMatrix();
			inScreen = false;
		}
		ScreenFade.set(1f);
	}

	// ---------------------------------------------------------------- background

	private float backgroundProgress() {
		if (!config.backgroundFade) {
			return 1f;
		}
		float t = (System.currentTimeMillis() - startMs) / (Math.max(0.03f, config.backgroundDuration) * 1000f);
		return Math.min(1f, t);
	}

	/** Undoes the screen transform for the background (it fills the screen) and gives it its own fade. */
	public void beginBackground(GuiGraphicsExtractor g) {
		if (!inScreen) {
			return;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(1f / scale);
		pose.translate(-centerX, -centerY - offsetY);
		ScreenFade.set(Easing.EASE_OUT_CUBIC.apply(backgroundProgress()));
	}

	public void endBackground(GuiGraphicsExtractor g) {
		if (!inScreen) {
			return;
		}
		g.pose().popMatrix();
		ScreenFade.set(guiAlpha);
	}

	/** Blur strength scaled by the background fade while a screen opens. */
	public int blurriness(int vanilla) {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (!isEnabled() || screen == null || screen != animated) {
			return vanilla;
		}
		return Math.round(vanilla * backgroundProgress());
	}

	// ---------------------------------------------------------------- slot wave

	/** Scales a container slot for the wave; applied inside the slot's own pose push. */
	public void slotTransform(GuiGraphicsExtractor g, Object screen, Slot slot) {
		if (!config.staggerSlots || !(screen instanceof AbstractContainerScreen<?> container) || !animating(container)) {
			return;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) container;
		float position = (slot.x + slot.y) / (float) Math.max(1, gui.cobblecomfort$imageWidth() + gui.cobblecomfort$imageHeight());
		float delay = position * STAGGER_SHARE;
		float local = Math.clamp((progress() - delay) / (1f - STAGGER_SHARE), 0f, 1f);
		float s = SLOT_START_SCALE + (1f - SLOT_START_SCALE) * Easing.EASE_OUT_BACK.apply(local);
		float cx = slot.x + 8;
		float cy = slot.y + 8;
		Matrix3x2fStack pose = g.pose();
		pose.translate(cx, cy);
		pose.scale(Math.max(0.01f, s));
		pose.translate(-cx, -cy);
	}
}
