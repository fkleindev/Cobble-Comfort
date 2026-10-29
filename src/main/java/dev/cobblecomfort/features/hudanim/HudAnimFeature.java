package dev.cobblecomfort.features.hudanim;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Livelier vanilla HUD bars: lost health stays bright for a moment and then runs down, healed hearts pop, the
 * experience bar fills smoothly with a counting and pulsing level, shanks and armour pop and fade, bubbles pop back,
 * and boss bars glide and run down like the hearts.
 */
public final class HudAnimFeature extends Feature<HudAnimConfig> {
	public static final HudAnimFeature INSTANCE = new HudAnimFeature();

	/** The rows of icons vanilla draws below the experience bar. */
	public enum Row {
		HEARTS, FOOD, ARMOR, AIR
	}

	private static final int ICON = 9;
	private static final float HALF_ICON = ICON / 2f;
	private static final int ICON_STEP = 8;
	private static final float POP_SECONDS = 0.3f;
	private static final float GHOST_SECONDS = 0.45f;
	private static final float LEVEL_PULSE_SECONDS = 0.45f;
	private static final float LEVEL_PULSE = 0.45f;
	private static final float XP_FLASH_SECONDS = 0.5f;
	/** Level up: a light beam rising from the level number; at 30, an enchanting table glows next to it. */
	private static final float BEAM_SECONDS = 0.9f;
	private static final float BEAM_HEIGHT = 46f;
	private static final int ENCHANT_LEVEL = 30;
	private static final float ENCHANT_HINT_SECONDS = 4f;
	private static final int ENCHANT_GLOW = 0xFFB070FF;
	private static final long XP_GAIN_WINDOW_MS = 1500;
	private static final float XP_GAIN_RISE = 10f;
	private static final float XP_GAIN_SCALE = 0.75f;
	private static final int XP_COLOR = 0xFF80FF20;
	private static final int XP_BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	private static final float BOSS_SPEED = 10f;
	private static final long BOSS_FORGET_MS = 2000;
	private static final int TRAIL_TINT = 0x90FFFFFF;
	private static final int ARMOR_FLASH = 0xFF5050;
	private static final Identifier HEART_TRAIL = Identifier.withDefaultNamespace("hud/heart/full_blinking");
	private static final Identifier HEART_TRAIL_HARDCORE = Identifier.withDefaultNamespace("hud/heart/hardcore_full_blinking");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");

	private final Map<Row, IconRow> rows = new HashMap<>();
	// Hearts: the loop counts down from the last heart, starting with each heart's container.
	private int heartIndex;
	private int maxHearts;
	private int health = -1;
	private float trail;
	private long trailHold;
	private long heartNanos;
	private Player heartPlayer;

	// Experience
	private double xpShown = -1;
	private int levelShown = -1;
	private long levelUpAt;
	/** When the level reached 30, for the enchanting table hint. */
	private long enchantReadyAt;
	private int lastTotal = -1;
	private int gain;
	private long gainAt;
	private Player xpPlayer;
	private long xpNanos;

	private final Map<UUID, BossBar> bosses = new HashMap<>();

	private static final class BossBar {
		float target;
		float shown;
		float trail;
		long hold;
		long seen;
		long nanos;
	}

	private HudAnimFeature() {
		super("hud_anim", HudAnimConfig.class, HudAnimConfig::new);
		rows.put(Row.HEARTS, new IconRow(false));
		rows.put(Row.FOOD, new IconRow(false));
		rows.put(Row.ARMOR, new IconRow(true));
		rows.put(Row.AIR, new IconRow(false));
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return HudAnimConfigUi.buildCategory(this);
	}

	private boolean rowEnabled(Row row) {
		return isEnabled() && switch (row) {
			case HEARTS -> config.healPop;
			case FOOD -> config.food;
			case ARMOR -> config.armor;
			case AIR -> config.air;
		};
	}

	private float seconds(float base) {
		return base / Math.max(0.05f, config.speed);
	}

	/** Seconds from {@code last} to {@code now} (0 the first time), at most a twentieth so a hitch does not jump. */
	private static float elapsed(long last, long now) {
		return last == 0 ? 0f : Math.min((now - last) / 1e9f, 0.05f);
	}

	// ---------------------------------------------------------------- icon rows

	public void beginRow(Row row, int icons) {
		IconRow r = rows.get(row);
		if (rowEnabled(row)) {
			r.begin(icons);
		} else {
			r.reset();
		}
	}

	/** How full a vanilla icon sprite is. */
	public static int level(Identifier sprite) {
		String path = sprite.getPath();
		if (path.contains("empty") || path.contains("bursting")) {
			return IconRow.EMPTY;
		}
		return path.contains("half") ? IconRow.HALF : IconRow.FULL;
	}

	/** Vanilla draws icon {@code index} of a row with {@code draw}; a filled icon that just filled pops. */
	public void icon(Row row, GuiGraphicsExtractor g, int index, Identifier sprite, int x, int y, Runnable draw) {
		IconRow r = rows.get(row);
		int level = level(sprite);
		if (!rowEnabled(row) || !r.contains(index)) {
			draw.run();
			return;
		}
		r.blit(index, sprite, level, x, y);
		float t = level == IconRow.EMPTY ? -1f : r.gainProgress(index, System.currentTimeMillis(), seconds(POP_SECONDS));
		if (t < 0f) {
			draw.run();
			return;
		}
		float scale = 1f + config.popStrength * (float) Math.sin(Math.PI * t);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + HALF_ICON, y + HALF_ICON);
		pose.scale(scale);
		pose.translate(-x - HALF_ICON, -y - HALF_ICON);
		draw.run();
		pose.popMatrix();
	}

	/** End of a row: starts new animations and draws the ghosts of emptied icons. */
	public void endRow(Row row, GuiGraphicsExtractor g) {
		IconRow r = rows.get(row);
		if (!rowEnabled(row) || r.size() == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		r.end(now);
		if (row == Row.HEARTS || row == Row.AIR) {
			// Lost health has its trail, and vanilla bursts its bubbles itself.
			return;
		}
		float seconds = seconds(GHOST_SECONDS);
		for (int i = 0; i < r.size(); i++) {
			float t = r.lossProgress(i, now, seconds);
			if (t < 0f || r.ghost(i) == null) {
				continue;
			}
			int x = r.x(i);
			int y = r.y(i);
			if (row == Row.ARMOR) {
				// Two red blinks.
				float blink = (float) Math.abs(Math.sin(Math.PI * 2 * t));
				g.blitSprite(RenderPipelines.GUI_TEXTURED, r.ghost(i), x, y, ICON, ICON, ARGB.color(Math.round(255 * blink * (1f - t)), ARMOR_FLASH));
			} else {
				// The shank shrinks, drifts up a little and fades.
				float scale = 1f - 0.5f * t;
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(x + HALF_ICON, y + HALF_ICON - 3f * t);
				pose.scale(scale);
				pose.translate(-x - HALF_ICON, -y - HALF_ICON);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, r.ghost(i), x, y, ICON, ICON, 1f - t);
				pose.popMatrix();
			}
		}
	}

	// ---------------------------------------------------------------- hearts

	public boolean healthTrail() {
		return isEnabled() && config.healthTrail;
	}

	/** Start of vanilla's hearts: follows the health and lets the trail run down. */
	public void beginHearts(Player player, float maxHealth, int currentHealth, int absorption) {
		long nanos = System.nanoTime();
		float dt = elapsed(heartNanos, nanos);
		heartNanos = nanos;
		maxHearts = (int) Math.ceil(maxHealth / 2.0);
		heartIndex = maxHearts + (int) Math.ceil(absorption / 2.0);
		beginRow(Row.HEARTS, heartIndex);
		long now = System.currentTimeMillis();
		if (player != heartPlayer || health < 0) {
			heartPlayer = player;
			trail = currentHealth;
		} else if (currentHealth < health) {
			trail = Math.max(trail, health);
			trailHold = now + (long) (config.trailDelay * 1000f);
		}
		if (currentHealth >= trail) {
			trail = currentHealth;
		} else if (now >= trailHold) {
			float speed = Math.max(6f, (trail - currentHealth) * 3f) * config.speed;
			trail = Math.max(currentHealth, trail - speed * dt);
		}
		health = currentHealth;
	}

	/**
	 * Vanilla draws one heart (container, blinking or filled). The container comes first for each heart and tells which
	 * heart is next; the lost health is drawn right after it, under the filled half.
	 */
	public void heart(GuiGraphicsExtractor g, boolean container, boolean blinking, boolean half, boolean hardcore,
			int x, int y, Runnable draw) {
		if (container) {
			heartIndex--;
			draw.run();
			if (healthTrail() && heartIndex < maxHearts) {
				drawTrail(g, heartIndex, x, y, hardcore);
			}
			return;
		}
		if (blinking) {
			draw.run();
			return;
		}
		icon(Row.HEARTS, g, heartIndex, half ? HEART_HALF : HEART_FULL, x, y, draw);
	}

	/** The part of heart {@code index} between the health and the trail, in the white of vanilla's blinking hearts. */
	private void drawTrail(GuiGraphicsExtractor g, int index, int x, int y, boolean hardcore) {
		float low = Math.max(index * 2, health);
		float high = Math.min(index * 2 + 2, trail);
		if (high <= low) {
			return;
		}
		int from = Math.round((low - index * 2) * HALF_ICON);
		int to = Math.round((high - index * 2) * HALF_ICON);
		if (to > from) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, hardcore ? HEART_TRAIL_HARDCORE : HEART_TRAIL, ICON, ICON, from, 0, x + from, y,
					to - from, ICON);
		}
	}

	// ---------------------------------------------------------------- experience

	/** Follows the experience (cheap to repeat within a frame): the shown value glides, levels count and gains add up. */
	private void updateXp(Player player) {
		long now = System.currentTimeMillis();
		double target = player.experienceLevel + player.experienceProgress;
		if (player != xpPlayer || xpShown < 0 || !config.smoothXp) {
			xpShown = target;
			levelShown = player.experienceLevel;
			lastTotal = player.totalExperience;
			xpPlayer = player;
			return;
		}
		double diff = target - xpShown;
		if (Math.abs(diff) > 1.5) {
			// Many levels at once: wrap around the bar only once.
			xpShown = target - Math.signum(diff);
		}
		long nanos = System.nanoTime();
		float frame = elapsed(xpNanos, nanos);
		xpNanos = nanos;
		xpShown += (target - xpShown) * (1.0 - Math.exp(-frame * 7.0 * config.speed));
		if (Math.abs(target - xpShown) < 0.002) {
			xpShown = target;
		}
		int level = (int) Math.floor(xpShown + 1e-6);
		if (level > levelShown) {
			levelUpAt = now;
			if (levelShown < ENCHANT_LEVEL && level >= ENCHANT_LEVEL) {
				enchantReadyAt = now;
			}
		}
		levelShown = level;
		if (player.totalExperience > lastTotal) {
			if (now - gainAt > XP_GAIN_WINDOW_MS) {
				gain = 0;
			}
			gain += player.totalExperience - lastTotal;
			gainAt = now;
		}
		lastTotal = player.totalExperience;
	}

	/** The fill of the experience bar, gliding instead of jumping. */
	public float xpProgress(Player player, float vanilla) {
		if (!isEnabled() || !config.smoothXp || player == null) {
			return vanilla;
		}
		updateXp(player);
		return (float) (xpShown - Math.floor(xpShown + 1e-6));
	}

	/** A short flash over the experience bar on a level up. */
	public void afterXpBar(GuiGraphicsExtractor g) {
		if (!isEnabled() || !config.levelPulse) {
			return;
		}
		float t = since(levelUpAt, seconds(XP_FLASH_SECONDS));
		if (t >= 0f) {
			int left = (g.guiWidth() - XP_BAR_WIDTH) / 2;
			int top = g.guiHeight() - 24 - BAR_HEIGHT;
			g.fill(left + 1, top + 1, left + XP_BAR_WIDTH - 1, top + BAR_HEIGHT - 1, RenderUtil.withAlpha(0xFFFFFFFF, 0.7f * (1f - t)));
		}
	}

	/** Vanilla's level number, counting along with the bar and pulsing on a level up. */
	public void level(GuiGraphicsExtractor g, Player player, int level, IntConsumer draw) {
		if (!isEnabled() || player == null) {
			draw.accept(level);
			return;
		}
		updateXp(player);
		int shown = config.smoothXp ? levelShown : level;
		if (shown <= 0) {
			return;
		}
		if (config.levelBeam) {
			drawBeam(g);
		}
		float t = config.levelPulse ? since(levelUpAt, seconds(LEVEL_PULSE_SECONDS)) : -1f;
		if (t < 0f) {
			draw.accept(shown);
			drawEnchantHint(g, shown);
			return;
		}
		float scale = 1f + LEVEL_PULSE * (float) Math.sin(Math.PI * t);
		float cx = g.guiWidth() / 2f;
		float cy = levelY(g) + 4f;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(scale);
		pose.translate(-cx, -cy);
		draw.accept(shown);
		pose.popMatrix();
		drawEnchantHint(g, shown);
	}

	/** A soft column of light rising from the level number and fading, wider at the bottom. */
	private void drawBeam(GuiGraphicsExtractor g) {
		float t = since(levelUpAt, seconds(BEAM_SECONDS));
		if (t < 0f) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int bottom = levelY(g) + 4;
		float rise = 1f - (1f - t) * (1f - t);
		int top = Math.round(bottom - BEAM_HEIGHT * rise);
		float alpha = t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f;
		int core = RenderUtil.withAlpha(0xFFFFFFE0, 0.85f * alpha);
		int glow = RenderUtil.withAlpha(XP_COLOR, 0.45f * alpha);
		int half = 1 + Math.round(3 * (1f - t));
		g.fillGradient(cx - half - 3, top, cx + half + 3, bottom, 0, glow);
		g.fillGradient(cx - half, top, cx + half, bottom, 0, core);
	}

	/** At level 30 a small enchanting table glows above the level number for a few seconds. */
	private void drawEnchantHint(GuiGraphicsExtractor g, int level) {
		float t = config.levelBeam ? since(enchantReadyAt, seconds(ENCHANT_HINT_SECONDS)) : -1f;
		if (t < 0f || level < ENCHANT_LEVEL) {
			return;
		}
		float fade = Math.min(1f, Math.min(t * 6f, (1f - t) * 4f));
		// Above the number, in the gap between hearts and food.
		float x = g.guiWidth() / 2f;
		float y = levelY(g) - 6f;
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 6);
		int glow = RenderUtil.withAlpha(ENCHANT_GLOW, (0.4f + 0.4f * pulse) * fade);
		RenderUtil.roundedFill(g, Math.round(x - 8), Math.round(y - 8), Math.round(x + 8), Math.round(y + 8), 6, glow);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y - 2f * (float) Math.sin(Math.PI * Math.min(1f, t * 4f)));
		pose.scale(0.75f * (0.7f + 0.3f * fade));
		g.item(new ItemStack(Items.ENCHANTING_TABLE), -8, -8);
		pose.popMatrix();
	}

	private static int levelY(GuiGraphicsExtractor g) {
		return g.guiHeight() - 24 - 9 - 2;
	}

	/** "+7" above the level number, rising through the gap between hearts and food and fading. */
	public void drawXpGain(GuiGraphicsExtractor g, Player player) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !config.xpGain || player == null || mc.gameMode == null || !mc.gameMode.hasExperience()) {
			return;
		}
		updateXp(player);
		float t = since(gainAt, XP_GAIN_WINDOW_MS / 1000f);
		if (t < 0f || gain <= 0) {
			return;
		}
		Font font = mc.font;
		String text = "+" + gain;
		float rise = 1f - (1f - t) * (1f - t);
		float alpha = Math.min(1f, (1f - t) * 3f);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(g.guiWidth() / 2f, levelY(g) - 3f - XP_GAIN_RISE * rise);
		pose.scale(XP_GAIN_SCALE);
		g.text(font, text, -font.width(text) / 2, 0, RenderUtil.withAlpha(XP_COLOR, alpha), true);
		pose.popMatrix();
	}

	/** 0 to 1 through an animation that started at {@code start}, or -1 outside of it. */
	private static float since(long start, float seconds) {
		if (start == 0) {
			return -1f;
		}
		float t = (System.currentTimeMillis() - start) / (seconds * 1000f);
		return t >= 1f ? -1f : Math.max(0f, t);
	}

	// ---------------------------------------------------------------- boss bars

	private BossBar boss(BossEvent event, float target) {
		long now = System.currentTimeMillis();
		bosses.values().removeIf(b -> now - b.seen > BOSS_FORGET_MS);
		BossBar bar = bosses.get(event.getId());
		if (bar == null) {
			bar = new BossBar();
			bar.target = bar.shown = bar.trail = target;
			bosses.put(event.getId(), bar);
		}
		bar.seen = now;
		return bar;
	}

	/** Before vanilla's boss bar fill: glides the fill and draws the lost part behind it. */
	public void beforeBossFill(GuiGraphicsExtractor g, int x, int y, BossEvent event, Identifier fill) {
		if (!isEnabled() || !config.bossSmooth && !config.bossTrail) {
			return;
		}
		float target = event.getProgress();
		BossBar bar = boss(event, target);
		long now = System.currentTimeMillis();
		long nanos = System.nanoTime();
		float dt = elapsed(bar.nanos, nanos);
		bar.nanos = nanos;
		if (target < bar.target) {
			bar.trail = Math.max(bar.trail, bar.shown);
			bar.hold = now + (long) (config.trailDelay * 1000f);
		}
		bar.target = target;
		bar.shown = config.bossSmooth ? bar.shown + (target - bar.shown) * (1f - (float) Math.exp(-dt * BOSS_SPEED * config.speed)) : target;
		if (Math.abs(target - bar.shown) < 0.001f) {
			bar.shown = target;
		}
		if (bar.trail <= bar.shown) {
			bar.trail = bar.shown;
		} else if (now >= bar.hold) {
			float speed = Math.max(0.15f, (bar.trail - bar.shown) * 2.5f) * config.speed;
			bar.trail = Math.max(bar.shown, bar.trail - speed * dt);
		}
		if (!config.bossTrail) {
			return;
		}
		int from = Math.round(bar.shown * XP_BAR_WIDTH);
		int to = Math.round(bar.trail * XP_BAR_WIDTH);
		if (to > from) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, fill, XP_BAR_WIDTH, BAR_HEIGHT, from, 0, x + from, y, to - from, BAR_HEIGHT);
			g.fill(x + from, y + 1, x + to, y + BAR_HEIGHT - 1, TRAIL_TINT);
		}
	}

	/** The fill vanilla draws for a boss bar. */
	public float bossProgress(BossEvent event, float vanilla) {
		if (!isEnabled() || !config.bossSmooth) {
			return vanilla;
		}
		BossBar bar = bosses.get(event.getId());
		return bar == null ? vanilla : bar.shown;
	}
}
