package dev.cobblecomfort.features.tracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the tracker stores for one world or server. */
public class TrackerData {
	public List<Goal> goals = new ArrayList<>();
	/** Containers the player has looked into, keyed by {@link #containerKey}. */
	public Map<String, RememberedContainer> containers = new LinkedHashMap<>();

	public static class Goal {
		/** Item registry id, e.g. "minecraft:diamond". */
		public String item;
		public int target;
		/** Set once the goal was reached, so the celebration only plays once. */
		public boolean celebrated;

		public Goal() {
		}

		public Goal(String item, int target) {
			this.item = item;
			this.target = target;
		}
	}

	public static class RememberedContainer {
		public String dimension;
		public int x, y, z;
		public Map<String, Integer> items = new LinkedHashMap<>();
	}

	public static String containerKey(String dimension, int x, int y, int z) {
		return dimension + "|" + x + "|" + y + "|" + z;
	}

	public Goal goal(String item) {
		for (Goal goal : goals) {
			if (goal.item.equals(item)) {
				return goal;
			}
		}
		return null;
	}
}
