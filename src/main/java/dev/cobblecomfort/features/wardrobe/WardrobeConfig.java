package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.core.FeatureConfig;

import java.util.ArrayList;
import java.util.List;

public class WardrobeConfig extends FeatureConfig {
	/** Your own badges (third person, mirrors, the wardrobe). */
	public boolean showOwn = true;
	/** The badges of other players with the mod (needs the Cobble Comfort Sync plugin on the server). */
	public boolean showOthers = true;
	/** Tell the server (Cobble Comfort Sync) which badges you wear, so others see them. */
	public boolean share = true;
	/** A small button on the player in the inventory opens the wardrobe. */
	public boolean inventoryButton = true;
	/** A button in the map's navigation opens the wardrobe. */
	public boolean mapButton = true;
	/** A little message when an advancement earns a new badge. */
	public boolean announce = true;

	/** The badges worn, left to right (keys of {@link Badge}). */
	public List<String> worn = new ArrayList<>();
	/** Badges earned with an advancement, in any world; they stay earned. */
	public List<String> earned = new ArrayList<>();
	/** The hat worn instead of the helmet (key of {@link Hat}, "" none). */
	public String hat = "";
	/** Hats earned by an advancement, an item or a biome, in any world; they stay earned. */
	public List<String> earnedHats = new ArrayList<>();
	/** How long wreath strands keep swinging: 0 settles almost at once, 1 swings for a long time. */
	public float hatSwing = 0.6f;
	/** Now and then a petal falls from a flower wreath. */
	public boolean petals = true;
	/** Saved outfits. */
	public List<Outfit> outfits = new ArrayList<>();

	/** Everything worn, saved under a name. Pets are remembered by UUID and skipped where they are unknown. */
	public static class Outfit {
		public String name = "";
		public List<String> badges = new ArrayList<>();
		public String hat = "";
		/** The pet worn as charm ("" automatic, "none"), and as backpack ("" none). */
		public String charm = "";
		public String pack = "";
		public boolean plush = true;
		public boolean bundles = true;
		public boolean melee = true;
		public boolean ranged = true;
		public boolean nameCharm = true;
	}
}
