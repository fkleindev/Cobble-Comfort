package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int cobblecomfort$leftPos();

	@Accessor("topPos")
	int cobblecomfort$topPos();

	@Accessor("imageWidth")
	int cobblecomfort$imageWidth();

	@Accessor("imageHeight")
	int cobblecomfort$imageHeight();

	@Accessor("titleLabelX")
	int cobblecomfort$titleLabelX();

	@Accessor("titleLabelY")
	int cobblecomfort$titleLabelY();

	@Accessor("hoveredSlot")
	Slot cobblecomfort$hoveredSlot();
}
