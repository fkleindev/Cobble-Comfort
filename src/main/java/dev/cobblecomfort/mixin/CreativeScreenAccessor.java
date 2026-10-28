package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {
	/** The container behind the item grid of the creative tabs. */
	@Accessor("CONTAINER")
	static SimpleContainer cobblecomfort$grid() {
		throw new AssertionError();
	}

	@Accessor("scrollOffs")
	float cobblecomfort$scrollOffs();
}
