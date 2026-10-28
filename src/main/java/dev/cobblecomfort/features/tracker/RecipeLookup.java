package dev.cobblecomfort.features.tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the ingredients of a crafting recipe. In singleplayer all recipes are known through the
 * integrated server; on servers only recipes unlocked in the player's recipe book are available.
 */
public final class RecipeLookup {
	private RecipeLookup() {
	}

	/** A recipe as ingredient counts per craft and how many items one craft makes. */
	public record Found(Map<Item, Integer> ingredients, int resultCount) {
		/** Ingredients needed to craft at least {@code amount} result items. */
		public Map<Item, Integer> scaledFor(int amount) {
			int crafts = Math.max(1, (amount + resultCount - 1) / resultCount);
			Map<Item, Integer> scaled = new LinkedHashMap<>();
			ingredients.forEach((item, count) -> scaled.put(item, count * crafts));
			return scaled;
		}
	}

	public static Optional<Found> find(Item target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return Optional.empty();
		}
		ContextMap context = SlotDisplayContext.fromLevel(mc.level);
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			Optional<Found> found = fromServer(server, target, context);
			if (found.isPresent()) {
				return found;
			}
		}
		return mc.player != null ? fromRecipeBook(target, context) : Optional.empty();
	}

	public static boolean hasRecipe(Item target) {
		return find(target).isPresent();
	}

	private static Optional<Found> fromServer(IntegratedServer server, Item target, ContextMap context) {
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			Recipe<?> recipe = holder.value();
			if (!(recipe instanceof CraftingRecipe) || recipe.placementInfo().isImpossibleToPlace()) {
				continue;
			}
			for (RecipeDisplay display : recipe.display()) {
				ItemStack result = display.result().resolveForFirstStack(context);
				if (!result.isEmpty() && result.getItem() == target) {
					return Optional.of(new Found(count(recipe.placementInfo().ingredients()), result.getCount()));
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<Found> fromRecipeBook(Item target, ContextMap context) {
		for (RecipeCollection collection : Minecraft.getInstance().player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				List<ItemStack> results = entry.resultItems(context);
				if (results.isEmpty() || results.getFirst().getItem() != target) {
					continue;
				}
				Optional<List<Ingredient>> requirements = entry.craftingRequirements();
				if (requirements.isPresent() && !requirements.get().isEmpty()) {
					return Optional.of(new Found(count(requirements.get()), results.getFirst().getCount()));
				}
			}
		}
		return Optional.empty();
	}

	/** Each ingredient is one item slot; ingredients accepting several items use the first one. */
	private static Map<Item, Integer> count(List<Ingredient> ingredients) {
		Map<Item, Integer> counts = new LinkedHashMap<>();
		for (Ingredient ingredient : ingredients) {
			ingredient.items().findFirst().map(Holder::value).ifPresent(item -> counts.merge(item, 1, Integer::sum));
		}
		return counts;
	}
}
