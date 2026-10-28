package dev.cobblecomfort.features.trade;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** A remembered villager or wandering trader: who it is, where it was last seen and its offers when last traded with. */
public final class TradeMerchant {
	/** Entity UUID. */
	public String uuid = "";
	public boolean wandering;
	/** Profession id (empty for wandering traders). */
	public String profession = "";
	/** 1 (novice) to 5 (master); 0 for wandering traders. */
	public int level;
	public String name = "";
	public String dimension = "";
	public double x, y, z;
	/** When the offers were last seen (ms since epoch). */
	public long seen;
	/** When the entity itself was last seen nearby (ms since epoch). */
	public long alive;
	public List<TradeOffer> offers = new ArrayList<>();

	/** Checks in a row in which it should have been visible but was not. */
	transient int missing;

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	public double distanceTo(Vec3 point) {
		return position().distanceTo(point);
	}
}
