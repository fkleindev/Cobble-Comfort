package dev.cobblecomfort.features.petplush;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Plush looks shared through the Cobble Comfort Sync server plugin (wire format in its Protocol class): each client
 * tells the server what it wears, the server passes the looks of all players with the mod on to everyone. The plugin
 * doesn't read a look, it only passes the bytes on, so new styles need no plugin update.
 */
public final class PetPlushSync {
	public static final int VERSION = 1;
	/** Longest look the plugin passes on. */
	public static final int MAX_LOOK_BYTES = 512;

	/** Client to server: this player's look. */
	public record PlushPayload(PlushLook look) implements CustomPacketPayload {
		public static final Type<PlushPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cobblecomfort", "plush"));
		public static final StreamCodec<FriendlyByteBuf, PlushPayload> CODEC = StreamCodec.of((buf, payload) -> {
			buf.writeVarInt(VERSION);
			payload.look().write(buf);
		}, buf -> {
			buf.readVarInt();
			return new PlushPayload(PlushLook.read(buf));
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: the looks of all players with the mod, replacing the previous list. */
	public record PlushesPayload(boolean known, Map<UUID, PlushLook> looks) implements CustomPacketPayload {
		public static final Type<PlushesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cobblecomfort", "plushes"));
		public static final StreamCodec<FriendlyByteBuf, PlushesPayload> CODEC = StreamCodec.of((buf, payload) -> {
			buf.writeVarInt(VERSION);
			buf.writeVarInt(payload.looks().size());
			payload.looks().forEach((uuid, look) -> {
				buf.writeUUID(uuid);
				FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
				look.write(bytes);
				byte[] data = new byte[bytes.readableBytes()];
				bytes.readBytes(data);
				buf.writeByteArray(data);
			});
		}, buf -> {
			if (buf.readVarInt() != VERSION) {
				buf.skipBytes(buf.readableBytes());
				return new PlushesPayload(false, Map.of());
			}
			int count = buf.readVarInt();
			Map<UUID, PlushLook> looks = new HashMap<>();
			for (int i = 0; i < count; i++) {
				UUID uuid = buf.readUUID();
				byte[] bytes = buf.readByteArray(MAX_LOOK_BYTES);
				try {
					looks.put(uuid, PlushLook.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes))));
				} catch (RuntimeException e) {
					// A look from a newer mod version this one can't read: skip that player.
				}
			}
			return new PlushesPayload(true, looks);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private final Map<UUID, PlushLook> looks = new HashMap<>();
	/** What the server was last told; {@code sentAny} is false until the first message on this connection. */
	private PlushLook sent;
	private boolean sentAny;

	public void register() {
		PayloadTypeRegistry.serverboundPlay().register(PlushPayload.TYPE, PlushPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlushesPayload.TYPE, PlushesPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(PlushesPayload.TYPE, (payload, context) -> {
			if (payload.known()) {
				looks.clear();
				looks.putAll(payload.looks());
			}
		});
	}

	/** Tells the server when this player's look changed (or once after joining). */
	public void tick(PlushLook own) {
		if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(PlushPayload.TYPE)) {
			clear();
			return;
		}
		if (!sentAny || !Objects.equals(sent, own)) {
			ClientPlayNetworking.send(new PlushPayload(own));
			sent = own;
			sentAny = true;
		}
	}

	public void clear() {
		looks.clear();
		sent = null;
		sentAny = false;
	}

	/** The look another player shared, or null. */
	public PlushLook get(UUID player) {
		return looks.get(player);
	}
}
