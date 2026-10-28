package dev.cobblecomfort.features.furnace;

import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** A remembered furnace or brewing stand and the estimate of when it will be done. Saved as JSON. */
public final class FurnaceEntry {
	public enum Kind {
		FURNACE(200), BLAST_FURNACE(100), SMOKER(100), BREWING_STAND(400);

		/** Vanilla ticks per item (per brew for the brewing stand). */
		public final int cookTime;

		Kind(int cookTime) {
			this.cookTime = cookTime;
		}

		public boolean halvesFuel() {
			return this == BLAST_FURNACE || this == SMOKER;
		}
	}

	/** Why the furnace stops (or stopped) working. */
	public enum Reason {
		/** Everything is cooked. */
		DONE,
		/** The fuel runs out first. */
		FUEL,
		/** The output slot fills up first. */
		FULL,
		/** It went out earlier than expected (items taken, hopper, ...). */
		STOPPED
	}

	public Kind kind = Kind.FURNACE;
	public String dimension = "";
	public int x, y, z;
	/** The result (or, before the first result exists, the input) as saved JSON. */
	public JsonElement icon;
	/** Whether {@link #icon} is the result rather than the input. */
	public boolean result;
	/** How many results (or inputs, see {@link #result}) there will be when it stops. */
	public int count;
	/** The brewing ingredient's name (brewing stands only). */
	public String ingredient = "";
	/** Loaded ticks left until it stops; only counted while the chunk is simulated. */
	public int remaining;
	/** Loaded ticks from the last look into it until it stops, for the progress. */
	public int total;
	public Reason reason = Reason.DONE;
	/** True once it has stopped (or was found finished). */
	public boolean done;
	/** True once the player was told (or saw it themselves). */
	public boolean notified;
	/** When it was last opened (ms). */
	public long seen;

	transient ItemStack stack = ItemStack.EMPTY;

	public BlockPos pos() {
		return new BlockPos(x, y, z);
	}

	public boolean at(String dim, BlockPos pos) {
		return dimension.equals(dim) && x == pos.getX() && y == pos.getY() && z == pos.getZ();
	}

	public ItemStack stack() {
		return stack;
	}

	public float progress() {
		return done || total <= 0 ? 1f : 1f - remaining / (float) total;
	}
}
