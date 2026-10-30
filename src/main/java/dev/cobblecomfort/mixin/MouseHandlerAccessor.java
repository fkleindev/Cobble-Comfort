package dev.cobblecomfort.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Grabbing the mouse without closing the screen (MouseHandler.grabMouse closes it). */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("mouseGrabbed")
	void cobblecomfort$setMouseGrabbed(boolean grabbed);

	@Accessor("ignoreFirstMove")
	void cobblecomfort$setIgnoreFirstMove(boolean ignore);

	@Accessor("xpos")
	void cobblecomfort$setXpos(double x);

	@Accessor("ypos")
	void cobblecomfort$setYpos(double y);
}
