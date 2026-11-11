package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.core.FeatureConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	/** The hat worn instead of the helmet (key of {@link Hat}, "" none). */
	public String hat = "";
	/** What is worn round the neck (key of {@link Neck}, "" none). */
	public String neck = "";
	/** What is worn over the eyes and on the nose and mouth (keys of {@link Face}, "" none). */
	public String eyes = "";
	public String mouth = "";
	/** Paint on the skin of the face (a key of {@link Face}, "" none). */
	public String skin = "";
	/** What is carried on the back (key of {@link BackPiece}, "" none). */
	public String back = "";
	/** The charm hanging on the back (key of {@link Trinket}, "" none). */
	public String trinket = "";
	/** How totems look (key of {@link TotemSkin}, "" the plush of whoever holds them). */
	public String totem = "";
	/** The charm on each kind of weapon and tool (keys of {@link ToolKind} to keys of {@link ToolCharm}, "" none; not listed: the name tag). */
	public Map<String, String> toolCharms = new HashMap<>();
	/** Where the charm is tied on each kind of weapon and tool, if moved in the charm editor (keys of {@link ToolKind}). */
	public Map<String, CharmSpot> charmSpots = new HashMap<>();
	/** Whether the name charm switched off before tool charms came is carried over (as no charm on any kind). */
	public boolean toolCharmsMigrated;
	/** The companion following you (key of {@link Companion}, "" none), its name, what it wears and in which dye colour. */
	public String companion = "";
	public String companionName = "";
	public String companionAccessory = "none";
	public int companionColor = 14;
	/** The companions of other players with the mod (needs the Cobble Comfort Sync plugin on the server). */
	public boolean showOtherCompanions = true;
	/** What has been earned, per world (singleplayer) or server (multiplayer); it stays earned there. */
	public Map<String, Unlocks> unlocks = new HashMap<>();
	/** How long the swinging parts of hats and neck pieces keep swinging: 0 settles almost at once, 1 for a long time. */
	public float hatSwing = 0.6f;
	/** Now and then a petal falls from a flower wreath. */
	public boolean petals = true;
	/** A neck bell rings softly as its wearer walks. */
	public boolean bellSound = true;
	/** Saved outfits. */
	public List<Outfit> outfits = new ArrayList<>();

	/** The cosmetics earned in one world or server (one colour stands for its whole kind where kinds come together). */
	public static class Unlocks {
		/** Badges earned with an advancement. */
		public List<String> badges = new ArrayList<>();
		/** Hats earned by an advancement, an item or a biome. */
		public List<String> hats = new ArrayList<>();
		public List<String> necks = new ArrayList<>();
		public List<String> backs = new ArrayList<>();
		public List<String> trinkets = new ArrayList<>();
		public List<String> totems = new ArrayList<>();
		public List<String> toolCharms = new ArrayList<>();
		public List<String> faces = new ArrayList<>();
		public List<String> companions = new ArrayList<>();
	}

	/**
	 * Where a charm is tied on a kind of weapon or tool: across and up a flat 16 × 16 item, in its pixels from the bottom
	 * left, and towards its front from the middle; once for third person (also on the ground and in frames, and as
	 * others see it), once for first person.
	 */
	public static class CharmSpot {
		public float x, y, z;
		public float nearX, nearY, nearZ;
	}

	/** Everything worn, saved under a name. Pets are remembered by UUID and skipped where they are unknown. */
	public static class Outfit {
		public String name = "";
		public List<String> badges = new ArrayList<>();
		public String hat = "";
		public String neck = "";
		public String eyes = "";
		public String mouth = "";
		public String skin = "";
		public String back = "";
		public String trinket = "";
		public String totem = "";
		/** The companion taken along; null in outfits from before companions (they leave it as it is). */
		public String companion;
		public boolean bundles = true;
		public boolean melee = true;
		public boolean ranged = true;
		/** From before tool charms: false took the name charm off every kind. */
		public boolean nameCharm = true;
		/** The charm on each kind of weapon and tool, as in {@link WardrobeConfig#toolCharms}. */
		public Map<String, String> toolCharms = new HashMap<>();
	}
}
