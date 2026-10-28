package dev.cobblecomfort.features.transitions;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.smoothgui.ScreenFade;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;

/**
 * Softer transitions: the world fades in from black after loading and after changing dimension, the death screen fades
 * in over a greyed world, falling asleep and waking up fade smoothly, and titles and action bar messages slide in (a new
 * action bar message cross-fades with the old one).
 */
public final class TransitionsFeature extends Feature<TransitionsConfig> {
	public static final TransitionsFeature INSTANCE = new TransitionsFeature();

	private static final float DEATH_SECONDS = 0.5f;
	private static final int DEATH_GREY = 0x303030;
	private static final float DEATH_GREY_ALPHA = 0.35f;
	private static final float WAKE_SECONDS = 0.6f;
	private static final float SLEEP_MAX_ALPHA = 220f / 255f;
	private static final int SLEEP_COLOR = 0x101020;
	private static final float TEXT_SECONDS = 0.3f;
	private static final float TITLE_SLIDE = 6f;
	private static final float MESSAGE_SLIDE = 4f;
	/** Vanilla fades the action bar out over its last this many ticks. */
	private static final int MESSAGE_FADE_TICKS = 20;

	private Level lastLevel;
	private boolean worldPending;
	private long worldFadeAt;

	private long deathAt;
	private float deathSaved = 1f;
	private boolean deathFading;

	private float sleepShown;
	private long sleepNanos;

	private long titleAt;
	private Component oldMessage;
	private int oldMessageAlpha;
	private long messageAt;

	private TransitionsFeature() {
		super("transitions", TransitionsConfig.class, TransitionsConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return TransitionsConfigUi.buildCategory(this);
	}

	private static float progress(long start, float seconds) {
		return start == 0 ? 1f : Math.min(1f, (System.currentTimeMillis() - start) / (seconds * 1000f));
	}

	// ---------------------------------------------------------------- world and death

	public boolean animatesDeath() {
		return isEnabled() && config.deathFade;
	}

	public void onScreenChange(Screen next) {
		if (next instanceof DeathScreen) {
			deathAt = System.currentTimeMillis();
		}
	}

	private static boolean loading(Screen screen) {
		return screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof GenericMessageScreen
				|| screen instanceof ConnectScreen;
	}

	/** On top of everything: the world fading in from black once it has loaded. */
	public void drawWorldFade(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			worldPending = mc.level != null;
		}
		if (worldPending && !loading(mc.gui.screen())) {
			worldPending = false;
			worldFadeAt = System.currentTimeMillis();
		}
		if (!isEnabled() || !config.worldFade || mc.level == null) {
			return;
		}
		float t = progress(worldFadeAt, config.worldFadeSeconds);
		if (t < 1f) {
			g.nextStratum();
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), RenderUtil.withAlpha(0xFF000000, 1f - Easing.EASE_IN_OUT_SINE.apply(t)));
		}
	}

	/** Before a screen: the death screen fades in over a darkened, greyed world. */
	public void beginScreen(GuiGraphicsExtractor g, Screen screen) {
		deathFading = false;
		if (!animatesDeath() || !(screen instanceof DeathScreen)) {
			return;
		}
		float eased = Easing.EASE_OUT_CUBIC.apply(progress(deathAt, DEATH_SECONDS));
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color(Math.round(255 * DEATH_GREY_ALPHA * eased), DEATH_GREY));
		if (eased < 1f) {
			deathSaved = ScreenFade.push(eased);
			deathFading = true;
		}
	}

	public void endScreen() {
		if (deathFading) {
			ScreenFade.restore(deathSaved);
			deathFading = false;
		}
	}

	// ---------------------------------------------------------------- sleep

	/**
	 * Draws the sleep overlay in place of vanilla's; returns false to leave it to vanilla. It follows vanilla's fade into
	 * sleep and fades out on waking instead of vanishing.
	 */
	public boolean drawSleep(GuiGraphicsExtractor g, int sleepTimer) {
		if (!isEnabled() || !config.sleepFade) {
			return false;
		}
		float target = 0f;
		if (sleepTimer > 0) {
			float f = sleepTimer / 100f;
			if (f > 1f) {
				f = 1f - (sleepTimer - 100) / 10f;
			}
			target = Math.clamp(f, 0f, 1f);
		}
		long now = System.nanoTime();
		float seconds = sleepNanos == 0 ? 0f : Math.min((now - sleepNanos) / 1e9f, 0.05f);
		sleepNanos = now;
		sleepShown = target >= sleepShown ? target : Math.max(target, sleepShown - seconds / WAKE_SECONDS);
		if (sleepShown > 0.004f) {
			g.nextStratum();
			float alpha = SLEEP_MAX_ALPHA * Easing.EASE_IN_OUT_SINE.apply(sleepShown);
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.color(Math.round(255 * alpha), SLEEP_COLOR));
		}
		return true;
	}

	// ---------------------------------------------------------------- titles and action bar

	public void onTitle() {
		titleAt = System.currentTimeMillis();
	}

	/** Start of the title: it slides up into place. Paired with {@link #endTitle}. */
	public void beginTitle(GuiGraphicsExtractor g) {
		g.pose().pushMatrix();
		if (isEnabled() && config.titleSlide) {
			g.pose().translate(0f, TITLE_SLIDE * (1f - Easing.EASE_OUT_CUBIC.apply(progress(titleAt, TEXT_SECONDS))));
		}
	}

	public void endTitle(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	/** A new action bar message: the one still showing is kept to fade out while the new one fades in. */
	public void onMessage(Component current, int currentTime, Component next) {
		if (next.equals(current) && currentTime > 0) {
			return;
		}
		oldMessage = currentTime > 0 ? current : null;
		oldMessageAlpha = Math.min(255, currentTime * 255 / MESSAGE_FADE_TICKS);
		messageAt = System.currentTimeMillis();
	}

	/**
	 * Draws the action bar message through {@code draw} (text, colour): the old one moving up and fading out, the new
	 * one sliding up and fading in.
	 */
	public void drawMessage(GuiGraphicsExtractor g, Component message, int color, MessageDraw draw) {
		if (!isEnabled() || !config.titleSlide) {
			draw.draw(message, color);
			return;
		}
		float t = progress(messageAt, TEXT_SECONDS);
		float eased = Easing.EASE_OUT_CUBIC.apply(t);
		if (oldMessage != null && t < 1f) {
			fadedAt(g, -MESSAGE_SLIDE * eased, 1f - eased, () -> draw.draw(oldMessage, ARGB.white(oldMessageAlpha)));
		}
		fadedAt(g, MESSAGE_SLIDE * (1f - eased), eased, () -> draw.draw(message, color));
	}

	private static void fadedAt(GuiGraphicsExtractor g, float dy, float alpha, Runnable draw) {
		g.pose().pushMatrix();
		g.pose().translate(0f, dy);
		float saved = ScreenFade.push(alpha);
		draw.run();
		ScreenFade.restore(saved);
		g.pose().popMatrix();
	}

	@FunctionalInterface
	public interface MessageDraw {
		void draw(Component message, int color);
	}
}
