package dev.cobblecomfort.features.food;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.mixin.FoodDataAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.Optional;

/**
 * AppleSkin-like food info: saturation and exhaustion on the food bar, a preview of what held food restores
 * (also as hearts from regeneration), a hunger warning, food values in tooltips and the best food in the hotbar.
 */
public final class FoodHudFeature extends Feature<FoodHudConfig> {
	public static final FoodHudFeature INSTANCE = new FoodHudFeature();

	static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
	static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
	static final Identifier SATURATION_FULL = CobbleComfortClient.id("hud/saturation_full");
	static final Identifier SATURATION_HALF = CobbleComfortClient.id("hud/saturation_half");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");

	private static final int ICONS = 10;
	private static final int ICON_STEP = 8;
	private static final int ICON_SIZE = 9;
	private static final int BAR_WIDTH = 81;
	private static final float MAX_EXHAUSTION = 4f;
	private static final float SPRINT_EXHAUSTION = 0.1f;
	private static final float SWIM_EXHAUSTION = 0.01f;
	private static final float JUMP_EXHAUSTION = 0.05f;
	private static final float SPRINT_JUMP_EXHAUSTION = 0.2f;
	private static final float HUNGER_EFFECT_EXHAUSTION = 0.005f;

	private float exhaustion;
	private int lastFood = -1;
	private float lastSaturation = -1f;
	private double lastX, lastY, lastZ;
	private boolean wasOnGround = true;
	private int bestSlot = -1;

	// Heart row of the current frame, captured when vanilla draws the hearts.
	private int heartX, heartY, heartRowHeight;
	private float heartMax;

	private FoodHudFeature() {
		super("food_hud", FoodHudConfig.class, FoodHudConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientTooltipComponentCallback.EVENT.register(data -> data instanceof FoodTooltip.Data food ? new FoodTooltip(food) : null);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (isEnabled() && config.bestFood && isBest(stack)) {
				lines.add(Component.translatable("cobblecomfort.food.best").withColor(config.bestFoodColor));
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return FoodHudConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- tick: exhaustion estimate, best food

	private void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			lastFood = -1;
			return;
		}
		FoodData data = player.getFoodData();
		// The server applied exhaustion (food or saturation dropped): start counting again.
		if (lastFood >= 0 && (data.getFoodLevel() < lastFood || data.getSaturationLevel() < lastSaturation)) {
			exhaustion = 0f;
		}
		lastFood = data.getFoodLevel();
		lastSaturation = data.getSaturationLevel();
		if (!player.getAbilities().invulnerable) {
			double dx = player.getX() - lastX;
			double dz = player.getZ() - lastZ;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (horizontal < 1.0) {
				if (player.isSwimming() || player.isUnderWater()) {
					addExhaustion((float) Math.sqrt(horizontal * horizontal + Math.pow(player.getY() - lastY, 2)) * SWIM_EXHAUSTION);
				} else if (player.isSprinting()) {
					addExhaustion((float) horizontal * SPRINT_EXHAUSTION);
				}
			}
			if (wasOnGround && !player.onGround() && player.getY() > lastY && !player.isInWater()) {
				addExhaustion(player.isSprinting() ? SPRINT_JUMP_EXHAUSTION : JUMP_EXHAUSTION);
			}
			MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
			if (hunger != null) {
				addExhaustion(HUNGER_EFFECT_EXHAUSTION * (hunger.getAmplifier() + 1));
			}
		}
		lastX = player.getX();
		lastY = player.getY();
		lastZ = player.getZ();
		wasOnGround = player.onGround();
		bestSlot = findBest(player);
	}

	/** Exhaustion the client knows about (attacks), also called from the player mixin. */
	public void addExhaustion(float amount) {
		exhaustion = Math.min(MAX_EXHAUSTION, exhaustion + amount);
	}

	private float exhaustion(Player player) {
		if (Minecraft.getInstance().getSingleplayerServer() != null) {
			var serverPlayer = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayer(player.getUUID());
			if (serverPlayer != null) {
				return Math.min(MAX_EXHAUSTION, ((FoodDataAccessor) serverPlayer.getFoodData()).cobblecomfort$exhaustion());
			}
		}
		return config.estimateExhaustion ? exhaustion : 0f;
	}

	/** The hotbar slot with the food that restores the most without waste (no food with side effects). */
	private int findBest(LocalPlayer player) {
		int food = player.getFoodData().getFoodLevel();
		if (food >= FoodMath.MAX_FOOD) {
			return -1;
		}
		int best = -1;
		float bestScore = 0f;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			FoodProperties props = FoodMath.food(stack);
			if (props == null || FoodMath.hasSideEffects(stack)) {
				continue;
			}
			int restored = FoodMath.foodAfter(food, props) - food;
			int wasted = props.nutrition() - restored;
			float score = restored * 10f + props.saturation() - wasted * 4f;
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	private boolean isBest(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && bestSlot >= 0 && player.getInventory().getItem(bestSlot) == stack;
	}

	private static ItemStack heldFood(Player player) {
		if (FoodMath.food(player.getMainHandItem()) != null) {
			return player.getMainHandItem();
		}
		return FoodMath.food(player.getOffhandItem()) != null ? player.getOffhandItem() : ItemStack.EMPTY;
	}

	private static float pulse() {
		return 0.35f + 0.45f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2.5));
	}

	// ---------------------------------------------------------------- food bar

	/** Before vanilla's food icons: exhaustion bar and hunger warning glow (underneath the icons). */
	public void beforeFood(GuiGraphicsExtractor g, Player player, int top, int right) {
		if (!isEnabled()) {
			return;
		}
		int food = player.getFoodData().getFoodLevel();
		if (config.hungerWarning && food <= config.warnBelow) {
			float strength = 0.25f + 0.5f * (config.warnBelow - food) / Math.max(1f, config.warnBelow);
			int color = RenderUtil.withAlpha(config.warningColor, strength * pulse());
			RenderUtil.roundedFill(g, right - BAR_WIDTH - 2, top - 2, right + 1, top + ICON_SIZE + 1, 2, color);
		}
		if (config.exhaustion) {
			float ex = exhaustion(player);
			int width = Math.round(BAR_WIDTH * Math.clamp(ex / MAX_EXHAUSTION, 0f, 1f));
			if (width > 0) {
				g.fill(right - width, top, right, top + ICON_SIZE, config.exhaustionColor);
			}
		}
	}

	/** After vanilla's food icons: saturation outlines, the eating preview and the numbers. */
	public void afterFood(GuiGraphicsExtractor g, Player player, int top, int right) {
		if (!isEnabled()) {
			return;
		}
		FoodData data = player.getFoodData();
		int food = data.getFoodLevel();
		float saturation = data.getSaturationLevel();
		if (config.saturationOverlay) {
			drawSaturation(g, top, right, 0f, saturation, 0xFFFFFFFF);
		}
		ItemStack held = heldFood(player);
		FoodProperties props = FoodMath.food(held);
		if (config.eatPreview && props != null) {
			int alpha = RenderUtil.withAlpha(0xFFFFFFFF, pulse());
			int after = FoodMath.foodAfter(food, props);
			for (int i = 0; i < ICONS; i++) {
				int now = food - i * 2;
				int then = after - i * 2;
				if (then > now && then > 0 && now < 2) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, then >= 2 ? FOOD_FULL : FOOD_HALF,
							right - i * ICON_STEP - ICON_SIZE, top, ICON_SIZE, ICON_SIZE, alpha);
				}
			}
			if (config.saturationOverlay) {
				drawSaturation(g, top, right, saturation, FoodMath.saturationAfter(food, saturation, props), alpha);
			}
		}
		if (config.numbers) {
			Font font = Minecraft.getInstance().font;
			String text = food + " + " + String.format(java.util.Locale.ROOT, "%.1f", saturation);
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(right + 3, top + 2);
			pose.scale(0.75f);
			g.text(font, Component.literal(text).withStyle(ChatFormatting.GOLD), 0, 0, 0xFFFFFFFF, true);
			pose.popMatrix();
		}
	}

	/** Golden outlines for the saturation between {@code from} and {@code to} (in half-shank units). */
	private static void drawSaturation(GuiGraphicsExtractor g, int top, int right, float from, float to, int color) {
		for (int i = 0; i < ICONS; i++) {
			float amountTo = Math.clamp(to - i * 2, 0f, 2f);
			float amountFrom = Math.clamp(from - i * 2, 0f, 2f);
			if (amountTo <= amountFrom || amountTo < 0.25f) {
				continue;
			}
			g.blitSprite(RenderPipelines.GUI_TEXTURED, amountTo >= 1.5f ? SATURATION_FULL : SATURATION_HALF,
					right - i * ICON_STEP - ICON_SIZE, top, ICON_SIZE, ICON_SIZE, color);
		}
	}

	// ---------------------------------------------------------------- hearts

	public void captureHearts(int x, int y, int rowHeight, float maxHealth) {
		heartX = x;
		heartY = y;
		heartRowHeight = rowHeight;
		heartMax = maxHealth;
	}

	/** Blinking hearts for the health that natural regeneration would give back after eating the held food. */
	public void afterHearts(GuiGraphicsExtractor g, Player player) {
		if (!isEnabled() || !config.healPreview) {
			return;
		}
		ItemStack held = heldFood(player);
		FoodProperties props = FoodMath.food(held);
		if (props == null) {
			return;
		}
		FoodData data = player.getFoodData();
		float health = player.getHealth();
		float missing = heartMax - health;
		if (missing <= 0f) {
			return;
		}
		float ex = exhaustion(player);
		float without = FoodMath.regenHealth(data.getFoodLevel(), data.getSaturationLevel(), ex, missing);
		float with = FoodMath.regenHealth(FoodMath.foodAfter(data.getFoodLevel(), props),
				FoodMath.saturationAfter(data.getFoodLevel(), data.getSaturationLevel(), props), ex, missing);
		int from = (int) Math.ceil(health);
		int to = (int) Math.floor(Math.min(heartMax, health + Math.max(without, with)));
		if (to <= from) {
			return;
		}
		int color = RenderUtil.withAlpha(0xFFFFFFFF, pulse());
		for (int point = from; point < to; point += 2) {
			int heart = point / 2;
			int hx = heartX + heart % 10 * ICON_STEP;
			int hy = heartY - heart / 10 * heartRowHeight;
			boolean half = point % 2 == 1 || to - point < 2;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, half ? HEART_HALF : HEART_FULL, hx, hy, ICON_SIZE, ICON_SIZE, color);
		}
	}

	// ---------------------------------------------------------------- items

	/** Tooltip image with the food values, or empty for non-food. */
	public Optional<TooltipComponent> tooltipImage(ItemStack stack) {
		FoodProperties props = isEnabled() && config.tooltipValues ? FoodMath.food(stack) : null;
		return props == null ? Optional.empty() : Optional.of(new FoodTooltip.Data(props.nutrition(), props.saturation()));
	}

	/** A small green marker on the hotbar slot holding the best food right now. */
	public void decorateHotbarSlot(GuiGraphicsExtractor g, int index, int x, int y) {
		if (!isEnabled() || !config.bestFood || index != bestSlot) {
			return;
		}
		int color = RenderUtil.withAlpha(config.bestFoodColor, 0.6f + 0.4f * pulse());
		g.fill(x + 12, y - 1, x + 17, y + 4, 0xC0000000);
		g.fill(x + 13, y, x + 16, y + 3, color);
	}
}
