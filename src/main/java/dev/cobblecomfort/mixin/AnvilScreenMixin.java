package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The anvil's XP cost counts up instead of appearing at once. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	@WrapOperation(method = "extractLabels", at = @At(value = "INVOKE", target = "Ljava/lang/Integer;valueOf(I)Ljava/lang/Integer;"))
	private Integer cobblecomfort$countingCost(int cost, Operation<Integer> original) {
		return original.call(InventoryFxFeature.INSTANCE.stations().anvilCost(this, cost));
	}
}
