package dev.cobblecomfort.features.lookinfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One row of detail information below the target's name. */
public sealed interface InfoLine {
	record Text(Component text) implements InfoLine {
	}

	/** Health drawn as heart sprites. */
	record Hearts(float health, float maxHealth) implements InfoLine {
	}

	/** Item icons with counts in a grid; more than columns x rows shows "+N" in the last cell. */
	record Items(List<ItemStack> stacks, int columns, int rows) implements InfoLine {
	}

	/** A label with a thin progress bar underneath. */
	record Bar(Component label, float progress, int color) implements InfoLine {
	}
}
