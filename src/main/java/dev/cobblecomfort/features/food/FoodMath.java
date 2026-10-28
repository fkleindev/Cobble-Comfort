package dev.cobblecomfort.features.food;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;

/** Food values and a small simulation of vanilla's natural regeneration. */
final class FoodMath {
	static final int MAX_FOOD = 20;
	/** Natural regeneration needs at least this much food. */
	private static final int REGEN_FOOD = 18;
	private static final float EXHAUSTION_PER_POINT = 4f;
	private static final float EXHAUSTION_PER_HEAL = 6f;
	private static final int MAX_STEPS = 2000;

	private FoodMath() {
	}

	static FoodProperties food(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(DataComponents.FOOD);
	}

	/** Food that also applies effects (golden apples, rotten flesh, ...) is never recommended as "best". */
	static boolean hasSideEffects(ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		return consumable != null && !consumable.onConsumeEffects().isEmpty();
	}

	static int foodAfter(int food, FoodProperties props) {
		return Math.min(MAX_FOOD, food + props.nutrition());
	}

	static float saturationAfter(int food, float saturation, FoodProperties props) {
		return Math.min(foodAfter(food, props), saturation + props.saturation());
	}

	/**
	 * Health natural regeneration gives back until food drops below 18, starting from the given values.
	 * Mirrors FoodData.tick: with full food, saturation heals fast (up to 6 exhaustion per heal); else 1 health per 6 exhaustion.
	 */
	static float regenHealth(int food, float saturation, float exhaustion, float missingHealth) {
		float healed = 0f;
		for (int step = 0; step < MAX_STEPS && healed < missingHealth && food >= REGEN_FOOD; step++) {
			float cost;
			if (food >= MAX_FOOD && saturation > 0f) {
				cost = Math.min(saturation, EXHAUSTION_PER_HEAL);
				healed += cost / EXHAUSTION_PER_HEAL;
			} else {
				cost = EXHAUSTION_PER_HEAL;
				healed += 1f;
			}
			exhaustion += cost;
			while (exhaustion >= EXHAUSTION_PER_POINT) {
				exhaustion -= EXHAUSTION_PER_POINT;
				if (saturation > 0f) {
					saturation = Math.max(0f, saturation - 1f);
				} else {
					food = Math.max(0, food - 1);
				}
			}
		}
		return Math.min(healed, missingHealth);
	}
}
