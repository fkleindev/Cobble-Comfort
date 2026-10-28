package dev.cobblecomfort.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceMenu.class)
public interface AbstractFurnaceMenuAccessor {
	/** Lit time left, lit total, cooking progress, cooking total (ticks). */
	@Accessor("data")
	ContainerData cobblecomfort$data();
}
