package dev.cobblecomfort.features.effects;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Replaces the vanilla effect icons with a configurable list (bars, rings or compact icons), also in inventories. */
public final class EffectHudFeature extends Feature<EffectHudConfig> {
	public static final EffectHudFeature INSTANCE = new EffectHudFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("effects");

	private static final int MARGIN = 2;
	private static final int GROUP_GAP = 4;
	private static final float SLIDE_PIXELS = 16f;
	private static final int TOOLTIP_WIDTH = 170;
	private static final int INVENTORY_LIST_MIN = 80;

	/** Animation state of one effect on the HUD; kept for a moment after the effect ends so it can fade out. */
	private static final class Anim {
		MobEffectInstance effect;
		float x, y, targetX, targetY;
		float appear;
		boolean leaving;
		boolean warned;
	}

	private final Map<Holder<MobEffect>, Anim> anims = new HashMap<>();
	/** Longest duration seen per effect, the 100 % of its bar (the client never learns the original duration). */
	private final Map<Holder<MobEffect>, Integer> maxDurations = new HashMap<>();
	private long lastFrameNanos;
	private int contentWidth = 120;
	private int contentHeight = EffectRenderer.ROW_HEIGHT;

	private EffectHudFeature() {
		super("effect_hud", EffectHudConfig.class, EffectHudConfig::new);
	}

	@Override
	public void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, LAYER_ID, (g, delta) -> drawHud(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return EffectHudConfigUi.buildCategory(this);
	}

	/** Whether vanilla's effect icons are hidden in favour of this HUD. */
	public boolean replacesVanilla() {
		return isEnabled();
	}

	public Rect defaultBounds(float width, float height) {
		// Left of the minimap when both sit at the top right.
		float right = width - MARGIN - MapFeature.INSTANCE.topRightReserved();
		return new Rect(right - contentWidth, MARGIN, contentWidth, contentHeight);
	}

	// ---------------------------------------------------------------- effects

	/** The effects to show, sorted and with the beneficial (and neutral) ones before the harmful ones if grouped. */
	private List<MobEffectInstance> effects(Player player) {
		List<MobEffectInstance> list = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (!config.hideIconless || effect.showIcon()) {
				list.add(effect);
			}
		}
		Comparator<MobEffectInstance> order = switch (config.sort) {
			case LONGEST_FIRST -> Comparator.comparingInt((MobEffectInstance e) -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()).reversed();
			case SHORTEST_FIRST -> Comparator.comparingInt((MobEffectInstance e) -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration());
			case NAME -> Comparator.comparing((MobEffectInstance e) -> e.getEffect().value().getDisplayName().getString());
		};
		if (config.separateGroups) {
			order = Comparator.comparingInt((MobEffectInstance e) -> isHarmful(e) ? 1 : 0).thenComparing(order);
		}
		list.sort(order);
		return list;
	}

	private static boolean isHarmful(MobEffectInstance effect) {
		return effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
	}

	private float progress(MobEffectInstance effect) {
		if (effect.isInfiniteDuration() || effect.isAmbient()) {
			return 1f;
		}
		int max = maxDurations.merge(effect.getEffect(), effect.getDuration(), Math::max);
		return max <= 0 ? 0f : effect.getDuration() / (float) max;
	}

	private boolean inWarning(MobEffectInstance effect) {
		return config.warn && !effect.isInfiniteDuration() && !effect.isAmbient() && effect.endsWithin(config.warnSeconds * 20);
	}

	private static float pulse() {
		return 0.55f + 0.45f * (float) Math.cos(System.currentTimeMillis() / 1000.0 * Math.PI * 4);
	}

	// ---------------------------------------------------------------- HUD

	private void drawHud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		long nanos = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((nanos - lastFrameNanos) / 1e9f, 0.1f);
		lastFrameNanos = nanos;
		if (!isEnabled() || mc.player == null) {
			anims.clear();
			return;
		}
		Screen screen = mc.gui.screen();
		if (screen != null && screen.showsActiveEffects()) {
			return;
		}
		List<MobEffectInstance> effects = effects(mc.player);
		if (effects.isEmpty() && HudEditorScreen.isOpen()) {
			effects = sample();
		}
		maxDurations.keySet().removeIf(type -> mc.player.getEffect(type) == null);
		layout(effects);
		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		animate(dt);
		Font font = mc.font;
		EffectStyle style = config.style;
		for (Anim anim : anims.values()) {
			float alpha = config.animations ? anim.appear : (anim.leaving ? 0f : 1f);
			if (alpha <= 0.02f) {
				continue;
			}
			if (!anim.leaving && inWarning(anim.effect)) {
				alpha *= pulse();
			}
			float slide = config.animations ? (1f - anim.appear) * SLIDE_PIXELS : 0f;
			int x = Math.round(bounds.x() + anim.x + slide);
			int y = Math.round(bounds.y() + anim.y);
			EffectRenderer.draw(g, font, style, config, x, y, config.listWidth, anim.effect, progress(anim.effect), alpha);
		}
	}

	/** Computes target positions for the current effects, adds new ones and marks ended ones as leaving. */
	private void layout(List<MobEffectInstance> effects) {
		EffectStyle style = config.style;
		int cellW = EffectRenderer.width(style, config);
		int cellH = EffectRenderer.height(style);
		boolean vertical = style == EffectStyle.LIST;
		int perRow = Math.max(1, config.perRow);
		int width = 0;
		int height = 0;
		int line = 0;
		int column = 0;
		int offset = 0;
		Boolean lastHarmful = null;
		for (Anim anim : anims.values()) {
			anim.leaving = true;
		}
		for (MobEffectInstance effect : effects) {
			boolean harmful = isHarmful(effect);
			if (config.separateGroups && lastHarmful != null && harmful != lastHarmful) {
				if (vertical) {
					offset += GROUP_GAP;
				} else if (column > 0) {
					line++;
					column = 0;
				}
			}
			lastHarmful = harmful;
			int tx;
			int ty;
			if (vertical) {
				tx = 0;
				ty = line * (cellH + EffectRenderer.GAP) + offset;
				line++;
			} else {
				if (column >= perRow) {
					line++;
					column = 0;
				}
				tx = column * (cellW + EffectRenderer.GAP);
				ty = line * (cellH + EffectRenderer.GAP);
				column++;
			}
			width = Math.max(width, tx + cellW);
			height = Math.max(height, ty + cellH);
			Anim anim = anims.get(effect.getEffect());
			if (anim == null) {
				anim = new Anim();
				anim.x = tx;
				anim.y = ty;
				anims.put(effect.getEffect(), anim);
			}
			anim.effect = effect;
			anim.targetX = tx;
			anim.targetY = ty;
			anim.leaving = false;
			warn(anim);
		}
		contentWidth = Math.max(width, cellW);
		contentHeight = Math.max(height, cellH);
	}

	private void animate(float dt) {
		float speed = Math.max(0.1f, config.animSpeed);
		float follow = config.animations ? 1f - (float) Math.exp(-12f * speed * dt) : 1f;
		Iterator<Anim> it = anims.values().iterator();
		while (it.hasNext()) {
			Anim anim = it.next();
			if (!anim.leaving) {
				anim.x += (anim.targetX - anim.x) * follow;
				anim.y += (anim.targetY - anim.y) * follow;
			}
			float step = config.animations ? 6f * speed * dt : 1f;
			anim.appear = Math.clamp(anim.appear + (anim.leaving ? -step : step), 0f, 1f);
			if (anim.leaving && anim.appear <= 0f) {
				it.remove();
			}
		}
	}

	/** Plays the warning sound once when an effect enters its last seconds. */
	private void warn(Anim anim) {
		boolean warning = inWarning(anim.effect);
		if (warning && !anim.warned && config.warnSound && anim.appear > 0f) {
			Minecraft.getInstance().getSoundManager().play(
					SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, config.warnVolume));
		}
		anim.warned = warning;
	}

	/** Example effects for positioning the element in the HUD editor. */
	private static List<MobEffectInstance> sample() {
		return List.of(new MobEffectInstance(MobEffects.SPEED, 1660, 1), new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0),
				new MobEffectInstance(MobEffects.REGENERATION, 180, 0), new MobEffectInstance(MobEffects.POISON, 400, 0));
	}

	// ---------------------------------------------------------------- inventory

	/**
	 * Draws the effect list next to a container screen in this feature's look.
	 * Returns false when vanilla should draw it instead.
	 */
	public boolean drawInventory(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !config.inventory || mc.player == null) {
			return false;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		int x = gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() + MARGIN;
		int available = screen.width - x - MARGIN;
		List<MobEffectInstance> effects = effects(mc.player);
		if (effects.isEmpty() || available < EffectRenderer.CELL_WIDTH) {
			return true;
		}
		EffectStyle style = available >= INVENTORY_LIST_MIN ? EffectStyle.LIST : EffectStyle.COMPACT;
		int listWidth = Math.min(config.listWidth, available);
		int cellH = EffectRenderer.height(style);
		int y = gui.cobblecomfort$topPos();
		MobEffectInstance hovered = null;
		Font font = mc.font;
		for (MobEffectInstance effect : effects) {
			if (y + cellH > screen.height) {
				break;
			}
			float alpha = inWarning(effect) ? pulse() : 1f;
			EffectRenderer.draw(g, font, style, config, x, y, listWidth, effect, progress(effect), alpha);
			int w = style == EffectStyle.LIST ? listWidth : EffectRenderer.CELL_WIDTH;
			if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + cellH) {
				hovered = effect;
			}
			y += cellH + EffectRenderer.GAP;
		}
		if (hovered != null && config.inventoryTooltip) {
			g.setComponentTooltipForNextFrame(font, tooltip(hovered, font), mouseX, mouseY);
		}
		return true;
	}

	private List<Component> tooltip(MobEffectInstance effect, Font font) {
		List<Component> lines = new ArrayList<>();
		lines.add(EffectRenderer.name(effect, config).copy().withStyle(EffectRenderer.categoryFormatting(effect)));
		lines.add(Component.translatable(effect.isInfiniteDuration() ? "cobblecomfort.effect_hud.infinite"
				: "cobblecomfort.effect_hud.remaining", EffectRenderer.time(effect)).withStyle(ChatFormatting.GRAY));
		if (effect.isAmbient()) {
			lines.add(Component.translatable("cobblecomfort.effect_hud.ambient").withStyle(ChatFormatting.AQUA));
		}
		String description = description(effect.getEffect());
		if (description != null) {
			for (String line : wrap(font, description)) {
				lines.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		return lines;
	}

	/** Description from the lang key {@code effect.<namespace>.<path>.desc}, or null. */
	private static String description(Holder<MobEffect> effect) {
		String key = effect.value().getDescriptionId() + ".desc";
		return Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : null;
	}

	private static List<String> wrap(Font font, String text) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && font.width(candidate) > TOOLTIP_WIDTH) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}
}
