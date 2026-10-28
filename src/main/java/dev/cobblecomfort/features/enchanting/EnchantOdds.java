package dev.cobblecomfort.features.enchanting;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chances of the enchanting table, estimated by rolling the vanilla selection with many random seeds.
 * Deliberately ignores the real seed the server sends, so nothing is predicted exactly.
 */
public final class EnchantOdds {
	public static final int SLOTS = 3;

	/** One enchantment the item can get; chance per slot (0..1) and the level range seen per slot. */
	public record Row(Holder<Enchantment> enchantment, float[] chance, int[] minLevel, int[] maxLevel) {
		float bestChance() {
			float best = 0f;
			for (float c : chance) {
				best = Math.max(best, c);
			}
			return best;
		}
	}

	/** Another enchantment that may come along with the shown clue. */
	public record Extra(Holder<Enchantment> enchantment, int minLevel, int maxLevel, float chance) {
	}

	private final ItemStack item;
	private final int[] costs;
	private final int samples;
	private final List<Row> rows;
	private final List<List<List<EnchantmentInstance>>> rolls;

	private EnchantOdds(ItemStack item, int[] costs, int samples, List<Row> rows, List<List<List<EnchantmentInstance>>> rolls) {
		this.item = item;
		this.costs = costs;
		this.samples = samples;
		this.rows = rows;
		this.rolls = rolls;
	}

	public List<Row> rows() {
		return rows;
	}

	public boolean noItem() {
		return item.isEmpty();
	}

	public int cost(int slot) {
		return costs[slot];
	}

	public boolean matches(ItemStack stack, int[] currentCosts, int currentSamples) {
		return samples == currentSamples && Arrays.equals(costs, currentCosts)
				&& ItemStack.isSameItemSameComponents(item, stack);
	}

	public static EnchantOdds compute(RegistryAccess access, ItemStack stack, int[] costs, int samples) {
		ItemStack item = stack.copyWithCount(1);
		int[] costCopy = costs.clone();
		Optional<HolderSet.Named<Enchantment>> pool = access.lookupOrThrow(Registries.ENCHANTMENT)
				.get(EnchantmentTags.IN_ENCHANTING_TABLE);
		Map<Holder<Enchantment>, Row> byEnchantment = new LinkedHashMap<>();
		List<List<List<EnchantmentInstance>>> rolls = new ArrayList<>();
		if (pool.isEmpty() || item.isEmpty()) {
			return new EnchantOdds(item, costCopy, samples, List.of(), rolls);
		}
		// Everything the item could get at the highest table level, so impossible-for-now rows still show up.
		for (EnchantmentInstance possible : EnchantmentHelper.getAvailableEnchantmentResults(30, item, pool.get().stream())) {
			row(byEnchantment, possible.enchantment());
		}
		RandomSource seeds = RandomSource.create();
		for (int slot = 0; slot < SLOTS; slot++) {
			List<List<EnchantmentInstance>> slotRolls = new ArrayList<>();
			rolls.add(slotRolls);
			if (costCopy[slot] <= 0) {
				continue;
			}
			for (int i = 0; i < samples; i++) {
				RandomSource random = RandomSource.create(seeds.nextLong());
				List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(random, item, costCopy[slot], pool.get().stream());
				if (item.is(Items.BOOK) && list.size() > 1) {
					list.remove(random.nextInt(list.size()));
				}
				slotRolls.add(list);
				for (EnchantmentInstance instance : list) {
					Row row = row(byEnchantment, instance.enchantment());
					row.chance[slot] += 1f / samples;
					row.minLevel[slot] = Math.min(row.minLevel[slot], instance.level());
					row.maxLevel[slot] = Math.max(row.maxLevel[slot], instance.level());
				}
			}
		}
		List<Row> rows = new ArrayList<>(byEnchantment.values());
		rows.sort(Comparator.comparingDouble((Row r) -> -r.bestChance())
				.thenComparing(r -> r.enchantment().value().description().getString()));
		return new EnchantOdds(item, costCopy, samples, rows, rolls);
	}

	private static Row row(Map<Holder<Enchantment>, Row> rows, Holder<Enchantment> enchantment) {
		return rows.computeIfAbsent(enchantment, e -> {
			int[] min = new int[SLOTS];
			Arrays.fill(min, Integer.MAX_VALUE);
			return new Row(e, new float[SLOTS], min, new int[SLOTS]);
		});
	}

	/**
	 * Given the clue vanilla shows for a slot, the chance of getting more than the clue and the most likely extras.
	 * The clue is one entry of the rolled list picked uniformly, so each roll that contains it is weighted by 1/size.
	 */
	public float extraChance(int slot, Holder<Enchantment> clue, int clueLevel, List<Extra> extras, int maxExtras) {
		if (slot >= rolls.size()) {
			return 0f;
		}
		double total = 0;
		double withExtras = 0;
		Map<Holder<Enchantment>, double[]> other = new LinkedHashMap<>();
		Map<Holder<Enchantment>, int[]> levels = new LinkedHashMap<>();
		for (List<EnchantmentInstance> roll : rolls.get(slot)) {
			if (roll.stream().noneMatch(i -> i.enchantment().is(clue) && i.level() == clueLevel)) {
				continue;
			}
			double weight = 1.0 / roll.size();
			total += weight;
			if (roll.size() > 1) {
				withExtras += weight;
			}
			for (EnchantmentInstance instance : roll) {
				if (instance.enchantment().is(clue)) {
					continue;
				}
				other.computeIfAbsent(instance.enchantment(), e -> new double[1])[0] += weight;
				int[] range = levels.computeIfAbsent(instance.enchantment(), e -> new int[]{Integer.MAX_VALUE, 0});
				range[0] = Math.min(range[0], instance.level());
				range[1] = Math.max(range[1], instance.level());
			}
		}
		if (total <= 0) {
			return 0f;
		}
		double sum = total;
		other.entrySet().stream()
				.sorted(Comparator.comparingDouble(e -> -e.getValue()[0]))
				.limit(maxExtras)
				.forEach(e -> {
					int[] range = levels.get(e.getKey());
					extras.add(new Extra(e.getKey(), range[0], range[1], (float) (e.getValue()[0] / sum)));
				});
		return (float) (withExtras / total);
	}
}
