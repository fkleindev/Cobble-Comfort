package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.features.belt.BeltBundle;
import dev.cobblecomfort.features.belt.BeltGear;
import dev.cobblecomfort.features.wardrobe.Badge;
import dev.cobblecomfort.features.wardrobe.BackPiece;
import dev.cobblecomfort.features.wardrobe.Face;
import dev.cobblecomfort.features.wardrobe.Hat;
import dev.cobblecomfort.features.wardrobe.Neck;
import dev.cobblecomfort.features.wardrobe.ToolCharm;
import dev.cobblecomfort.features.wardrobe.ToolKind;
import dev.cobblecomfort.features.wardrobe.TotemSkin;
import dev.cobblecomfort.features.wardrobe.Trinket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a player wears: a plush charm hanging on the back and/or a plush backpack (either may be null), and the bundles
 * and weapons on their belt, the badges pinned to their chest, the hat on their head, what they wear round the
 * neck, what they carry on the back and hang from it in place of the pets, and the charm on each kind of weapon and tool
 * (each may be null; the charms are null when not told, a kind maps to null for no charm).
 */
public record PlushLook(PetPlush charm, PetPlush pack, List<BeltBundle> bundles, BeltGear gear, List<Badge> badges, Hat hat,
		Neck neck, BackPiece back, Trinket trinket, TotemSkin totem, Map<ToolKind, ToolCharm> toolCharms, Face eyes, Face mouth,
		Map<ToolKind, Vector3fc> charmSpots, Face skin) {
	/** Charm spots are shared in quarters of an item pixel. */
	private static final float SPOT_STEPS = 4f;
	public static final PlushLook NONE = new PlushLook(null, null, List.of(), BeltGear.NONE, List.of(), null, null, null, null, null, null, null, null, null, null);

	public PlushLook(PetPlush charm, PetPlush pack) {
		this(charm, pack, List.of(), BeltGear.NONE, List.of(), null, null, null, null, null, null, null, null, null, null);
	}

	public boolean hasPets() {
		return charm != null || pack != null;
	}

	/**
	 * Shared format (opaque to the server plugin): per plush slot a boolean, then type, variant and collar id + 1; then
	 * the number of belt bundles and for each its colour id + 1 (0: undyed) and fullness (0-255); then the belt weapons
	 * ({@link BeltGear}); then the number of badges and the id of each ({@link Badge} order); then the hat's id + 1
	 * (0: none, {@link Hat} order); then the neck piece's id + 1 (0: none, {@link Neck} order), the back piece's and the back charm's likewise; then how
	 * their totems look, its id + 1 (0: the plush of them, {@link TotemSkin} order); then the number of tool charms and for
	 * each the kind ({@link ToolKind} order) and the charm's id + 1 (0: none, {@link ToolCharm} order); then what is worn over the eyes and on the nose
	 * and mouth, each its id + 1 (0: none, {@link Face} order); then the number of kinds whose charms are tied somewhere
	 * else than by default and for each the kind and where, in quarters of an item pixel across, up (unsigned bytes) and
	 * towards the front (a signed byte); then the paint on the skin of the face, its id + 1 (0: none).
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
		buf.writeVarInt(toolCharms == null ? 0 : toolCharms.size());
		if (toolCharms != null) {
			for (Map.Entry<ToolKind, ToolCharm> entry : toolCharms.entrySet()) {
				buf.writeVarInt(entry.getKey().ordinal());
				buf.writeVarInt(entry.getValue() == null ? 0 : entry.getValue().ordinal() + 1);
			}
		}
		buf.writeVarInt(eyes == null ? 0 : eyes.ordinal() + 1);
		buf.writeVarInt(mouth == null ? 0 : mouth.ordinal() + 1);
		buf.writeVarInt(charmSpots == null ? 0 : charmSpots.size());
		if (charmSpots != null) {
			for (Map.Entry<ToolKind, Vector3fc> entry : charmSpots.entrySet()) {
				Vector3fc spot = entry.getValue();
				buf.writeVarInt(entry.getKey().ordinal());
				buf.writeByte(Mth.clamp(Math.round(spot.x() * SPOT_STEPS), 0, 255));
				buf.writeByte(Mth.clamp(Math.round(spot.y() * SPOT_STEPS), 0, 255));
				buf.writeByte(Mth.clamp(Math.round(spot.z() * SPOT_STEPS), -128, 127));
			}
		}
		buf.writeVarInt(skin == null ? 0 : skin.ordinal() + 1);
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
		Map<ToolKind, ToolCharm> toolCharms = null;
		if (buf.isReadable()) {
			toolCharms = new EnumMap<>(ToolKind.class);
			int kinds = Math.min(buf.readVarInt(), 64);
			for (int i = 0; i < kinds; i++) {
				ToolKind kind = ToolKind.byId(buf.readVarInt());
				int id = buf.readVarInt();
				ToolCharm tool = ToolCharm.byId(id - 1);
				// A charm this version doesn't know yet: the name tag in its place.
				if (kind != null) {
					toolCharms.put(kind, id == 0 ? null : tool == null ? ToolCharm.NAME_TAG : tool);
				}
			}
		}
		Face eyes = buf.isReadable() ? Face.byId(buf.readVarInt() - 1) : null;
		Face mouth = buf.isReadable() ? Face.byId(buf.readVarInt() - 1) : null;
		Map<ToolKind, Vector3fc> charmSpots = new EnumMap<>(ToolKind.class);
		int spots = buf.isReadable() ? Math.min(buf.readVarInt(), 64) : 0;
		for (int i = 0; i < spots; i++) {
			ToolKind kind = ToolKind.byId(buf.readVarInt());
			Vector3f spot = new Vector3f(buf.readUnsignedByte() / SPOT_STEPS, buf.readUnsignedByte() / SPOT_STEPS, buf.readByte() / SPOT_STEPS);
			if (kind != null) {
				charmSpots.put(kind, spot);
			}
		}
		Face skin = buf.isReadable() ? Face.byId(buf.readVarInt() - 1) : null;
		return new PlushLook(charm, pack, List.copyOf(bundles), gear, List.copyOf(badges), hat, neck, back, trinket, totem, toolCharms, eyes, mouth,
				charmSpots, skin);
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
