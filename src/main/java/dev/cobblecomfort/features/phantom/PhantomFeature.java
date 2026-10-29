package dev.cobblecomfort.features.phantom;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.infohud.InfoHudFeature;
import dev.cobblecomfort.features.tracker.TrackerToast;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import org.joml.Matrix3x2fStack;

/**
 * Phantom warning: the time since the player last slept, from the "time since rest" statistic. The server only sends
 * statistics on request, so they are asked for on joining and every few minutes, and counted on locally in between:
 * one tick per tick while alive and awake, back to zero when sleeping or dying (like the server does).
 */
public final class PhantomFeature extends Feature<PhantomConfig> {
	public static final PhantomFeature INSTANCE = new PhantomFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("phantom");

	/** Phantoms can spawn above this many ticks without sleep (three days). */
	public static final int THRESHOLD = 72000;
	/** From here on the HUD element shows the countdown (the last day before). */
	public static final int SOON = 48000;
	/** The info HUD line shows from the second day on. */
	public static final int SHOWN = 24000;
	private static final int DAY = 24000;
	private static final int REFRESH_TICKS = 6000;
	private static final int RETRY_TICKS = 200;
	private static final int PAD = 4;
	private static final int ICON = 10;
	private static final int DEFAULT_X = 2;
	/** Phantom silhouettes in the active panel: speed (px/s), start offset (share of the way) and height (share). */
	private static final float[][] PHANTOMS = {{22f, 0f, 0.3f}, {15f, 0.45f, 0.65f}, {28f, 0.75f, 0.45f}};
	private static final int PHANTOM_WIDTH = 11;
	private static final int PHANTOM_BODY = 0xB08A9CCC;
	private static final int PHANTOM_EYE = 0xFF8CFF6C;

	/** Ticks since the last rest, or -1 while not known yet. */
	private int ticks = -1;
	private int requestIn;
	private boolean warned;
	private boolean wasSleeping;
	private float width = 90;

	private PhantomFeature() {
		super("phantom", PhantomConfig.class, PhantomConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(this::reset));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(this::reset));
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, LAYER_ID, (g, delta) -> draw(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return PhantomConfigUi.buildCategory(this);
	}

	private void reset() {
		ticks = -1;
		requestIn = 40;
		warned = false;
		wasSleeping = false;
	}

	/** Ticks since the last rest, or -1 while not known. */
	public int ticks() {
		return ticks;
	}

	private void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!isEnabled() || player == null || level == null || mc.getConnection() == null) {
			return;
		}
		if (--requestIn <= 0) {
			mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
			requestIn = ticks < 0 ? RETRY_TICKS : REFRESH_TICKS;
		}
		if (ticks < 0) {
			return;
		}
		boolean sleeping = player.isSleeping();
		if (sleeping || player.isDeadOrDying()) {
			ticks = 0;
		} else if (!mc.isPaused() && level.tickRateManager().runsNormally()) {
			ticks++;
		}
		if (wasSleeping && !sleeping) {
			// Woken up: ask again, in case the night was skipped or the rest was not counted.
			requestIn = Math.min(requestIn, 20);
		}
		wasSleeping = sleeping;
		checkWarning(mc);
	}

	/** Called on the client thread with every statistics packet; it only holds the statistics changed since the last. */
	public void onStats(Object2IntMap<Stat<?>> stats) {
		Stat<?> rest = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);
		if (stats.containsKey(rest)) {
			ticks = stats.getInt(rest);
			checkWarning(Minecraft.getInstance());
		}
	}

	private void checkWarning(Minecraft mc) {
		if (ticks < THRESHOLD) {
			warned = false;
			return;
		}
		if (warned || !phantomsPossible(mc)) {
			return;
		}
		warned = true;
		if (config.warning) {
			mc.gui.toastManager().addToast(new TrackerToast(new ItemStack(Items.PHANTOM_MEMBRANE),
					Component.translatable("cobblecomfort.phantom.toast.title"),
					Component.translatable("cobblecomfort.phantom.toast.text", ticks / DAY)));
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PHANTOM_AMBIENT, 1f, 0.6f));
		}
	}

	/**
	 * Whether phantoms can come here at all: a dimension with a sky, not peaceful, and (in singleplayer, where the rule
	 * is known) phantom spawning switched on.
	 */
	public static boolean phantomsPossible(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || !level.dimensionType().hasSkyLight() || level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		MinecraftServer server = mc.getSingleplayerServer();
		return server == null || server.getGameRules().get(GameRules.SPAWN_PHANTOMS);
	}

	/** "2 d 5 h ago": the time since the rest in game days and hours. */
	private static MutableComponent since(int ticks) {
		return Component.translatable("cobblecomfort.phantom.since", ticks / DAY, ticks % DAY / 1000);
	}

	/** Real time until phantoms can spawn, as m:ss. */
	private static String countdown(int ticks) {
		int seconds = (Math.max(0, THRESHOLD - ticks) + 19) / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	/**
	 * The info HUD line: the time since the rest and when phantoms come, or null before the second day, in dimensions
	 * without phantoms, and while not known.
	 */
	public Component infoValue(Minecraft mc, boolean colors) {
		if (!isEnabled() || ticks < SHOWN || mc.level == null || !mc.level.dimensionType().hasSkyLight()) {
			return null;
		}
		MutableComponent text = since(ticks);
		if (!phantomsPossible(mc)) {
			return text;
		}
		if (ticks >= THRESHOLD) {
			text.append(Component.translatable("cobblecomfort.phantom.info.active"));
		} else {
			text.append(Component.translatable("cobblecomfort.phantom.info.soon", countdown(ticks)));
		}
		if (colors && ticks >= SOON) {
			text.withStyle(ticks >= THRESHOLD ? ChatFormatting.RED : ChatFormatting.YELLOW);
		}
		return text;
	}

	// ---------------------------------------------------------------- HUD element

	public Rect defaultBounds(float screenWidth, float screenHeight) {
		return new Rect(DEFAULT_X, defaultY(), width, height());
	}

	private static int defaultY() {
		return Math.max(16, Math.round(InfoHudFeature.INSTANCE.defaultBottom())) + 4;
	}

	private static int height() {
		return 2 * PAD + 9 + 3;
	}

	/**
	 * Small phantom silhouettes gliding through the panel behind the text, each at its own speed and height, with
	 * flapping wings and green eyes.
	 */
	private static void drawPhantoms(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		long now = System.currentTimeMillis();
		int width = x1 - x0 + 2 * PHANTOM_WIDTH;
		g.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		for (int i = 0; i < PHANTOMS.length; i++) {
			float[] phantom = PHANTOMS[i];
			float travel = (now / 1000f * phantom[0] + phantom[1] * width) % width;
			int x = x0 - PHANTOM_WIDTH + Math.round(travel);
			int y = y0 + Math.round((y1 - y0) * phantom[2] + 1.5f * (float) Math.sin(now / 300.0 + i * 2));
			// Wings flap: tips up two rows above the body, or down two rows below it.
			int wing = (now / 160 + i) % 2 == 0 ? -1 : 1;
			int w = PHANTOM_WIDTH;
			g.fill(x, y + 2 * wing, x + 1, y + 2 * wing + 1, PHANTOM_BODY);
			g.fill(x + w - 1, y + 2 * wing, x + w, y + 2 * wing + 1, PHANTOM_BODY);
			g.fill(x + 1, y + wing, x + 3, y + wing + 1, PHANTOM_BODY);
			g.fill(x + w - 3, y + wing, x + w - 1, y + wing + 1, PHANTOM_BODY);
			g.fill(x + 2, y, x + w - 2, y + 1, PHANTOM_BODY);
			g.fill(x + 4, y + 1, x + w - 4, y + 2, PHANTOM_BODY);
			g.fill(x + w - 4, y, x + w - 3, y + 1, PHANTOM_EYE);
		}
		g.disableScissor();
	}

	private void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !config.hudElement || mc.player == null) {
			return;
		}
		boolean preview = HudEditorScreen.isOpen();
		int shown = ticks;
		if (ticks < SOON || !phantomsPossible(mc)) {
			if (!preview) {
				return;
			}
			shown = SOON + (THRESHOLD - SOON) / 2;
		}
		boolean active = shown >= THRESHOLD;
		Component text = active
				? Component.translatable("cobblecomfort.phantom.hud.active").withStyle(ChatFormatting.RED)
				: Component.translatable("cobblecomfort.phantom.hud.soon", Component.literal(countdown(shown)).withStyle(ChatFormatting.YELLOW));
		Font font = mc.font;
		width = 2 * PAD + ICON + 3 + font.width(text);
		int x0 = DEFAULT_X;
		int y0 = defaultY();
		int x1 = x0 + Math.round(width);
		int y1 = y0 + height();
		RenderUtil.roundedFill(g, x0, y0, x1, y1, 3, 0xB0100010);
		if (active) {
			drawPhantoms(g, x0, y0, x1, y1);
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x0 + PAD, y0 + PAD - 1);
		pose.scale(ICON / 16f);
		g.item(new ItemStack(Items.PHANTOM_MEMBRANE), 0, 0);
		pose.popMatrix();
		float pulse = active ? 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 250.0) : 1f;
		g.text(font, text, x0 + PAD + ICON + 3, y0 + PAD, RenderUtil.withAlpha(0xFFFFFFFF, pulse), true);
		// The last day before the threshold as a bar filling up; full and red once phantoms can come.
		int barTop = y1 - PAD - 1;
		float progress = Math.clamp((shown - SOON) / (float) (THRESHOLD - SOON), 0f, 1f);
		g.fill(x0 + PAD, barTop, x1 - PAD, barTop + 2, 0x60FFFFFF);
		int color = active ? RenderUtil.withAlpha(0xFFFF5050, pulse) : 0xFFFFD040;
		g.fill(x0 + PAD, barTop, x0 + PAD + Math.round((x1 - x0 - 2 * PAD) * progress), barTop + 2, color);
	}
}
