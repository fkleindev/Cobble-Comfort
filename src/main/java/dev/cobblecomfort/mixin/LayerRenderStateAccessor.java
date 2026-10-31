package dev.cobblecomfort.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("itemTransform")
	ItemTransform cobblecomfort$itemTransform();

	@Accessor("localTransform")
	Matrix4f cobblecomfort$localTransform();

	@Accessor("extents")
	Supplier<Vector3fc[]> cobblecomfort$extents();
}
