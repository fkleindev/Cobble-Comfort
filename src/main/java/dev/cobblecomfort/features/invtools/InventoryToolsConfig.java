package dev.cobblecomfort.features.invtools;

import dev.cobblecomfort.core.FeatureConfig;

public class InventoryToolsConfig extends FeatureConfig {
	// Search
	public boolean search = true;
	public boolean searchTooltip = true;
	public boolean searchShulkers = true;
	public boolean rememberQuery = false;
	public int highlightColor = 0xFFFFE066;
	public int dimColor = 0xB0000000;

	// Sorting
	public boolean sortButtons = true;
	public boolean middleClickSort = true;
	public SortOrder sortOrder = SortOrder.CREATIVE;
	public boolean sortHotbar = false;
	public int actionsPerTick = 0;
	public boolean sortAnimation = true;

	// Moving items
	/** Buttons in storage containers: deposit what the container already holds, top up the inventory's stacks. */
	public boolean transferButtons = true;
	public boolean depositHotbar = true;
	/** Shift-click a slot and drag over more slots to shift-click them all. */
	public boolean shiftDrag = true;

	// Slot locks
	public boolean locks = true;
	public boolean lockProtection = true;
	public boolean lockWorldDrop = true;
	public boolean lockInHotbar = true;
	public int lockColor = 0xFFFFAA00;
}
