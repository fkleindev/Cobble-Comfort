package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
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

	@Mutable
	@Accessor("imageWidth")
	void cobblecomfort$setImageWidth(int width);

	@Mutable
	@Accessor("imageHeight")
	void cobblecomfort$setImageHeight(int height);

	@Accessor("titleLabelX")
	int cobblecomfort$titleLabelX();

	@Accessor("titleLabelY")
	int cobblecomfort$titleLabelY();

	@Accessor("hoveredSlot")
	Slot cobblecomfort$hoveredSlot();
}
