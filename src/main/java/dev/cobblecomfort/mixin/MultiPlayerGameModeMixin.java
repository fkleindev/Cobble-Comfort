package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.crosshair.CrosshairFeature;
import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Container inputs: slot locks can veto an input; inventory effects diff the slots around it
 * (the client applies each input locally right away).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	/** Crosshair hit marker: remembers the attack, before the attack charge resets. */
	@Inject(method = "attack", at = @At("HEAD"))
	private void cobblecomfort$attack(Player player, Entity target, CallbackInfo ci) {
		CrosshairFeature.INSTANCE.onAttack(player, target);
	}

	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$beforeInput(int containerId, int slot, int button, ContainerInput input, Player player,
			CallbackInfo ci) {
		if (InventoryToolsFeature.INSTANCE.blocksInput(player.containerMenu, slot, button, input)) {
			ci.cancel();
			return;
		}
		InventoryFxFeature.INSTANCE.beforeInput(player.containerMenu);
	}

	@Inject(method = "handleContainerInput", at = @At("RETURN"))
	private void cobblecomfort$afterInput(int containerId, int slot, int button, ContainerInput input, Player player,
			CallbackInfo ci) {
		InventoryFxFeature.INSTANCE.afterInput(player.containerMenu, input);
	}

	/** Locked hotbar slots can't be dropped with the drop key. */
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$blockLockedDrop(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
		if (InventoryToolsFeature.INSTANCE.blocksWorldDrop(player.getInventory().getSelectedSlot())) {
			ci.cancel();
		}
	}
}
