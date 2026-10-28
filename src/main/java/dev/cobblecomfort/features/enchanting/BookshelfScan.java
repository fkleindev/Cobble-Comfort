package dev.cobblecomfort.features.enchanting;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;

import java.util.ArrayList;
import java.util.List;

/** The bookshelves around an enchanting table, split into counted, blocked and free spots. */
public record BookshelfScan(int count, List<BlockPos> valid, List<BlockPos> blocked, List<BlockPos> empty) {
	/** Bookshelves above this count make no difference. */
	public static final int MAX_SHELVES = 15;

	public static BookshelfScan of(Level level, BlockPos table) {
		List<BlockPos> valid = new ArrayList<>();
		List<BlockPos> blocked = new ArrayList<>();
		List<BlockPos> empty = new ArrayList<>();
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			BlockPos pos = table.offset(offset);
			if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				valid.add(pos);
			} else if (level.getBlockState(pos).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) {
				blocked.add(pos);
			} else if (level.getBlockState(pos).canBeReplaced() && transmitterFree(level, table, offset)) {
				empty.add(pos);
			}
		}
		return new BookshelfScan(valid.size(), valid, blocked, empty);
	}

	/** Whether the block between table and shelf spot lets the power through, i.e. a shelf there would count. */
	private static boolean transmitterFree(Level level, BlockPos table, BlockPos offset) {
		return level.getBlockState(table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2))
				.is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
	}

	/** Highest cost the bottom slot can roll with this many shelves (vanilla EnchantmentHelper.getEnchantmentCost). */
	public static int maxLevel(int shelves) {
		int b = Math.min(shelves, MAX_SHELVES);
		return Math.max(8 + b / 2 + b, b * 2);
	}
}
