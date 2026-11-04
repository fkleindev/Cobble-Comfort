package dev.cobblecomfort.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Slot positions are only used for drawing and mouse hits on this client, so a view may move them. */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void cobblecomfort$setX(int x);

	@Mutable
	@Accessor("y")
	void cobblecomfort$setY(int y);
}
