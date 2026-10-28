package dev.cobblecomfort.features.map.sync;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player positions shared through the Cobble Comfort Sync server plugin (server-plugin/ in this repository; the wire
 * format is documented in its Protocol class). Sharing is opt-in: the client tells the server whether its player
 * shares, and only then gets the other sharing players, a few times a second. Between updates the positions are
 * interpolated so the markers glide.
 */
public final class PositionSync {
	public static final int VERSION = 1;
	/** Forget everyone when no update came for this long (server gone, plugin reloaded ...). */
	private static final long STALE_MS = 5000L;

	/** One shared player as received. Health is -1 when the server does not share it. */
	public record Remote(UUID uuid, String name, String dimension, double x, double y, double z, float yaw,
			float health, float maxHealth, int armor) {
	}

	/** A shared player with its interpolated position. */
	public record Shown(Remote data, double x, double y, double z, float yaw) {
	}

	/** The last two updates of a player, to interpolate between. */
	private static final class Tracked {
		Remote previous;
		Remote current;
		long received;
	}

	public record SharePayload(boolean sharing) implements CustomPacketPayload {
		public static final Type<SharePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cobblecomfort", "share"));
		public static final StreamCodec<FriendlyByteBuf, SharePayload> CODEC = StreamCodec.of((buf, payload) -> {
			buf.writeVarInt(VERSION);
			buf.writeBoolean(payload.sharing());
		}, buf -> {
			buf.readVarInt();
			return new SharePayload(buf.readBoolean());
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The shared players; a message from another protocol version is read as "unknown" and ignored. */
	public record PlayersPayload(boolean known, int intervalTicks, List<Remote> players) implements CustomPacketPayload {
		public static final Type<PlayersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cobblecomfort", "players"));
		public static final StreamCodec<FriendlyByteBuf, PlayersPayload> CODEC = StreamCodec.of((buf, payload) -> {
			buf.writeVarInt(VERSION);
			buf.writeVarInt(payload.intervalTicks());
			buf.writeVarInt(payload.players().size());
			for (Remote remote : payload.players()) {
				buf.writeUUID(remote.uuid());
				buf.writeUtf(remote.name());
				buf.writeUtf(remote.dimension());
				buf.writeDouble(remote.x());
				buf.writeDouble(remote.y());
				buf.writeDouble(remote.z());
				buf.writeFloat(remote.yaw());
				buf.writeFloat(remote.health());
				buf.writeFloat(remote.maxHealth());
				buf.writeVarInt(remote.armor());
			}
		}, buf -> {
			if (buf.readVarInt() != VERSION) {
				buf.skipBytes(buf.readableBytes());
				return new PlayersPayload(false, 0, List.of());
			}
			int interval = buf.readVarInt();
			int count = buf.readVarInt();
			List<Remote> players = new ArrayList<>(Math.min(count, 1024));
			for (int i = 0; i < count; i++) {
				players.add(new Remote(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
						buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
			}
			return new PlayersPayload(true, interval, players);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private final Map<UUID, Tracked> players = new HashMap<>();
	private long intervalMs = 500L;
	private long lastUpdate;
	/** What the server was last told (null = nothing yet on this connection). */
	private Boolean sent;

	public void register() {
		PayloadTypeRegistry.serverboundPlay().register(SharePayload.TYPE, SharePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayersPayload.TYPE, PlayersPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(PlayersPayload.TYPE, (payload, context) -> receive(payload));
	}

	/** Whether the server has the plugin (it accepts share messages). */
	public boolean supported() {
		return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(SharePayload.TYPE);
	}

	/** Tells the server when the sharing switch changed (or once after joining), and drops stale players. */
	public void tick(boolean share) {
		if (!supported()) {
			sent = null;
			players.clear();
			return;
		}
		if (sent == null || sent != share) {
			ClientPlayNetworking.send(new SharePayload(share));
			sent = share;
			if (!share) {
				players.clear();
			}
		}
		if (!players.isEmpty() && System.currentTimeMillis() - lastUpdate > Math.max(STALE_MS, intervalMs * 6)) {
			players.clear();
		}
	}

	public void clear() {
		players.clear();
		sent = null;
	}

	private void receive(PlayersPayload payload) {
		if (!payload.known() || !Boolean.TRUE.equals(sent)) {
			return;
		}
		long now = System.currentTimeMillis();
		intervalMs = Math.max(50L, payload.intervalTicks() * 50L);
		lastUpdate = now;
		Set<UUID> seen = new HashSet<>();
		for (Remote remote : payload.players()) {
			seen.add(remote.uuid());
			Tracked tracked = players.computeIfAbsent(remote.uuid(), uuid -> new Tracked());
			// Glide on from where the marker is now; jump on a dimension change or teleport.
			Shown at = tracked.current == null ? null : shown(tracked, now);
			boolean jump = at == null || !tracked.current.dimension().equals(remote.dimension())
					|| Math.abs(at.x() - remote.x()) + Math.abs(at.z() - remote.z()) > 64;
			tracked.previous = jump ? remote : new Remote(remote.uuid(), remote.name(), remote.dimension(), at.x(), at.y(), at.z(), at.yaw(),
					remote.health(), remote.maxHealth(), remote.armor());
			tracked.current = remote;
			tracked.received = now;
		}
		players.keySet().retainAll(seen);
	}

	private Shown shown(Tracked tracked, long now) {
		float t = Math.clamp((now - tracked.received) / (float) intervalMs, 0f, 1f);
		Remote a = tracked.previous;
		Remote b = tracked.current;
		return new Shown(b, Mth.lerp(t, a.x(), b.x()), Mth.lerp(t, a.y(), b.y()), Mth.lerp(t, a.z(), b.z()), Mth.rotLerp(t, a.yaw(), b.yaw()));
	}

	/** The shared players in a dimension, at their interpolated positions. */
	public List<Shown> players(String dimension) {
		long now = System.currentTimeMillis();
		List<Shown> list = new ArrayList<>();
		for (Tracked tracked : players.values()) {
			if (tracked.current.dimension().equals(dimension)) {
				list.add(shown(tracked, now));
			}
		}
		return list;
	}

	public Remote get(UUID uuid) {
		Tracked tracked = players.get(uuid);
		return tracked == null ? null : tracked.current;
	}

	public int count() {
		return players.size();
	}
}
