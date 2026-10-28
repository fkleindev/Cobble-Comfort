package dev.cobblecomfort.mixin;

import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapRenderer.class)
public interface MapRendererInvoker {
	/** Render state for a marker that is not on the map data, such as the player's own position in the map preview. */
	@Invoker("extractDecorationRenderState")
	MapRenderState.MapDecorationRenderState cobblecomfort$extractDecoration(MapDecoration decoration);
}
