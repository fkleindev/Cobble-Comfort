package dev.cobblecomfort.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wire format shared with the mod (see PositionSync in the mod). Everything is big endian like Minecraft's
 * FriendlyByteBuf; VarInts and strings (VarInt byte length + UTF-8) are written the same way.
 *
 * <pre>
 * cobblecomfort:share   client -> server   VarInt version, boolean sharing
 * cobblecomfort:players server -> client   VarInt version, VarInt interval ticks, VarInt count, then per player:
 *                                          UUID (2 longs), String name, String dimension, double x, y, z,
 *                                          float yaw, float health (-1 = not shared), float max health, VarInt armour
 * cobblecomfort:plush   client -> server   VarInt version, then the player's plush look (the rest of the message)
 * cobblecomfort:plushes server -> client   VarInt version, VarInt count, then per player: UUID (2 longs),
 *                                          VarInt length, look
 *
 * A look is passed on as it is; only the mod reads it (PlushLook): per slot (charm, backpack) a boolean, and if set
 * String type ("wolf"/"cat"), String variant id, VarInt collar colour id + 1 (0 = none).
 * </pre>
 */
public final class Protocol {
	public static final int VERSION = 1;
	public static final String SHARE = "cobblecomfort:share";
	public static final String PLAYERS = "cobblecomfort:players";
	public static final int PLUSH_VERSION = 1;
	public static final String PLUSH = "cobblecomfort:plush";
	public static final String PLUSHES = "cobblecomfort:plushes";

	/** Longest plush look passed on (a look is a few dozen bytes). */
	public static final int MAX_LOOK = 512;

	private Protocol() {
	}

	/** One shared player as sent to clients. */
	public record Entry(UUID uuid, String name, String dimension, double x, double y, double z, float yaw,
			float health, float maxHealth, int armor) {
	}

	/** Reads a share message; null if it is malformed or from another protocol version. */
	public static Boolean readShare(byte[] data) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			if (readVarInt(in) != VERSION) {
				return null;
			}
			return in.readBoolean();
		} catch (IOException e) {
			return null;
		}
	}

	/** The look in a plush message; null if it is malformed, too long or from another protocol version. */
	public static byte[] readPlush(byte[] data) {
		try {
			DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
			if (readVarInt(in) != PLUSH_VERSION) {
				return null;
			}
			byte[] look = in.readAllBytes();
			return look.length > MAX_LOOK ? null : look;
		} catch (IOException e) {
			return null;
		}
	}

	public static byte[] writePlushes(Map<UUID, byte[]> looks) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + looks.size() * 64);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writeVarInt(out, PLUSH_VERSION);
			writeVarInt(out, looks.size());
			for (Map.Entry<UUID, byte[]> entry : looks.entrySet()) {
				out.writeLong(entry.getKey().getMostSignificantBits());
				out.writeLong(entry.getKey().getLeastSignificantBits());
				writeVarInt(out, entry.getValue().length);
				out.write(entry.getValue());
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return bytes.toByteArray();
	}

	public static byte[] writePlayers(int intervalTicks, List<Entry> entries) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + entries.size() * 64);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writeVarInt(out, VERSION);
			writeVarInt(out, intervalTicks);
			writeVarInt(out, entries.size());
			for (Entry entry : entries) {
				out.writeLong(entry.uuid().getMostSignificantBits());
				out.writeLong(entry.uuid().getLeastSignificantBits());
				writeString(out, entry.name());
				writeString(out, entry.dimension());
				out.writeDouble(entry.x());
				out.writeDouble(entry.y());
				out.writeDouble(entry.z());
				out.writeFloat(entry.yaw());
				out.writeFloat(entry.health());
				out.writeFloat(entry.maxHealth());
				writeVarInt(out, entry.armor());
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return bytes.toByteArray();
	}

	private static void writeString(DataOutputStream out, String value) throws IOException {
		byte[] utf = value.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, utf.length);
		out.write(utf);
	}

	private static void writeVarInt(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			byte b = in.readByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		throw new IOException("VarInt too long");
	}
}
