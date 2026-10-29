package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.phantom.PhantomFeature;
import dev.cobblecomfort.features.pickup.PickupLogFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** Item pickups by the local player feed the pickup log (after the hop to the client thread, before vanilla shrinks the item). */
	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void cobblecomfort$logPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || packet.getPlayerId() != mc.player.getId()) {
			return;
		}
		Entity entity = mc.level.getEntity(packet.getItemId());
		if (entity instanceof ItemEntity item) {
			PickupLogFeature.INSTANCE.onPickup(item.getItem(), packet.getAmount());
			if (MapFeature.INSTANCE.isEnabled()) {
				MapFeature.INSTANCE.deaths().onPickup(mc, item.getItem(), packet.getAmount());
			}
		}
	}

	/** Statistics the server sent on request: the time since the last rest for the phantom warning. */
	@Inject(method = "handleAwardStats", at = @At("TAIL"))
	private void cobblecomfort$stats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
		PhantomFeature.INSTANCE.onStats(packet.stats());
	}

	/** The own death: the inventory is still intact here, before the server's slot updates empty it. */
	@Inject(method = "handlePlayerCombatKill", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void cobblecomfort$death(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && packet.playerId() == mc.player.getId() && MapFeature.INSTANCE.isEnabled()) {
			MapFeature.INSTANCE.deaths().onKilled(mc.player);
		}
	}
}
