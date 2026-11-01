package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.features.belt.BeltBundle;
import dev.cobblecomfort.features.belt.BeltGear;
import dev.cobblecomfort.features.wardrobe.Badge;
import dev.cobblecomfort.features.wardrobe.BackPiece;
import dev.cobblecomfort.features.wardrobe.Hat;
import dev.cobblecomfort.features.wardrobe.Neck;
import dev.cobblecomfort.features.wardrobe.TotemSkin;
import dev.cobblecomfort.features.wardrobe.Trinket;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player wears: a plush charm hanging on the back and/or a plush backpack (either may be null), and the bundles
 * and weapons on their belt, the badges pinned to their chest, the hat on their head, what they wear round the
 * neck, and what they carry on the back and hang from it in place of the pets (each may be null).
 */
public record PlushLook(PetPlush charm, PetPlush pack, List<BeltBundle> bundles, BeltGear gear, List<Badge> badges, Hat hat,
		Neck neck, BackPiece back, Trinket trinket, TotemSkin totem) {
	public static final PlushLook NONE = new PlushLook(null, null, List.of(), BeltGear.NONE, List.of(), null, null, null, null, null);

	public PlushLook(PetPlush charm, PetPlush pack) {
		this(charm, pack, List.of(), BeltGear.NONE, List.of(), null, null, null, null, null);
	}

	public boolean hasPets() {
		return charm != null || pack != null;
	}

	/**
	 * Shared format (opaque to the server plugin): per plush slot a boolean, then type, variant and collar id + 1; then
	 * the number of belt bundles and for each its colour id + 1 (0: undyed) and fullness (0-255); then the belt weapons
	 * ({@link BeltGear}); then the number of badges and the id of each ({@link Badge} order); then the hat's id + 1
	 * (0: none, {@link Hat} order); then the neck piece's id + 1 (0: none, {@link Neck} order), the back piece's and the back charm's likewise; then how
	 * their totems look, its id + 1 (0: the plush of them, {@link TotemSkin} order).
	 */
	public void write(FriendlyByteBuf buf) {
		write(buf, charm);
		write(buf, pack);
		buf.writeVarInt(bundles.size());
		for (BeltBundle bundle : bundles) {
			buf.writeVarInt(bundle.color() + 1);
			buf.writeByte(Math.round(bundle.fill() * 255f));
		}
		gear.write(buf);
		buf.writeVarInt(badges.size());
		for (Badge badge : badges) {
			buf.writeVarInt(badge.ordinal());
		}
		buf.writeVarInt(hat == null ? 0 : hat.ordinal() + 1);
		buf.writeVarInt(neck == null ? 0 : neck.ordinal() + 1);
		buf.writeVarInt(back == null ? 0 : back.ordinal() + 1);
		buf.writeVarInt(trinket == null ? 0 : trinket.ordinal() + 1);
		buf.writeVarInt(totem == null ? 0 : totem.ordinal() + 1);
	}

	public static PlushLook read(FriendlyByteBuf buf) {
		PetPlush charm = read1(buf);
		PetPlush pack = read1(buf);
		List<BeltBundle> bundles = new ArrayList<>();
		int count = buf.isReadable() ? Math.min(buf.readVarInt(), BeltBundle.MAX) : 0;
		for (int i = 0; i < count; i++) {
			bundles.add(new BeltBundle(buf.readVarInt() - 1, buf.readUnsignedByte() / 255f));
		}
		BeltGear gear = BeltGear.read(buf);
		List<Badge> badges = new ArrayList<>();
		int pins = buf.isReadable() ? Math.min(buf.readVarInt(), Badge.MAX_WORN) : 0;
		for (int i = 0; i < pins; i++) {
			// Badges this version doesn't know yet are left out.
			Badge badge = Badge.byId(buf.readVarInt());
			if (badge != null) {
				badges.add(badge);
			}
		}
		// Anything worn that this version doesn't know yet is left out.
		Hat hat = buf.isReadable() ? Hat.byId(buf.readVarInt() - 1) : null;
		Neck neck = buf.isReadable() ? Neck.byId(buf.readVarInt() - 1) : null;
		BackPiece back = buf.isReadable() ? BackPiece.byId(buf.readVarInt() - 1) : null;
		Trinket trinket = buf.isReadable() ? Trinket.byId(buf.readVarInt() - 1) : null;
		TotemSkin totem = buf.isReadable() ? TotemSkin.byId(buf.readVarInt() - 1) : null;
		return new PlushLook(charm, pack, List.copyOf(bundles), gear, List.copyOf(badges), hat, neck, back, trinket, totem);
	}

	private static void write(FriendlyByteBuf buf, PetPlush plush) {
		buf.writeBoolean(plush != null);
		if (plush != null) {
			buf.writeUtf(plush.type());
			buf.writeUtf(plush.variant());
			buf.writeVarInt(plush.collar() + 1);
		}
	}

	private static PetPlush read1(FriendlyByteBuf buf) {
		if (!buf.isReadable() || !buf.readBoolean()) {
			return null;
		}
		return new PetPlush(buf.readUtf(), buf.readUtf(), buf.readVarInt() - 1);
	}
}
