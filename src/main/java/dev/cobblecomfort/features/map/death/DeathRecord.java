package dev.cobblecomfort.features.map.death;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What the player dropped at one death, how much of it is back and how long the rest has left. Saved as JSON. */
public final class DeathRecord {
	/** Ticks a dropped item lives while its chunk is ticking (vanilla ItemEntity lifetime). */
	public static final int LIFETIME = 6000;

	public enum State {
		/** Items are lying there. */
		ACTIVE,
		/** Everything was picked up again. */
		RECOVERED,
		/** The despawn timer ran out while the chunk was loaded. */
		EXPIRED,
		/** The player was close and no matching items were left (burnt, taken, despawned earlier). */
		GONE
	}

	/** The death waypoint's creation time, which links the two. */
	public long waypoint;
	public String dimension = "";
	public int x, y, z;
	/** The dropped stacks in inventory order, as saved JSON. */
	public List<JsonElement> items = new ArrayList<>();
	/** How many of each stack were picked up again. */
	public int[] recovered = new int[0];
	/** Ticks left while the chunk is simulated (the player is near). */
	public int loadedRemaining = LIFETIME;
	/** Ticks left if the chunk stayed loaded the whole time (worst case). */
	public int worstRemaining = LIFETIME;
	public State state = State.ACTIVE;
	/** The "runs out soon" warning was shown. */
	public boolean warned;
	/** Last time this was updated (ms), to count time spent offline on servers. */
	public long seen = System.currentTimeMillis();

	transient List<ItemStack> stacks = new ArrayList<>();
	/** Ticks since death, for the keepInventory check; -1 once checked. */
	transient int checkTicks;
	/** Ticks the player has been close without seeing any of the items. */
	transient int missingTicks;

	public List<ItemStack> stacks() {
		return stacks;
	}

	public int remaining(int index) {
		return Math.max(0, stacks.get(index).getCount() - recovered(index));
	}

	public int recovered(int index) {
		return index < recovered.length ? recovered[index] : 0;
	}

	/** Stacks that are fully back. */
	public int recoveredStacks() {
		int count = 0;
		for (int i = 0; i < stacks.size(); i++) {
			if (remaining(i) == 0) {
				count++;
			}
		}
		return count;
	}

	public boolean allRecovered() {
		return recoveredStacks() == stacks.size();
	}
}
