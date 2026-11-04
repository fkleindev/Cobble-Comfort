package dev.cobblecomfort.features.wardrobe;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The charm editor's view for one frame, handed to the GUI as a picture-in-picture element and drawn by
 * {@link CharmSceneRenderer}: a weapon or tool with {@code charm} tied at {@code spot} (item pixels), turned by
 * {@code rotation} about the point {@code centre} of the space it is drawn in, {@code zoom} GUI pixels to a block.
 */
public record CharmScene(int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds,
		ItemStack stack, ToolCharm charm, Vector3fc spot, Quaternionfc rotation, Vector3fc centre, float zoom)
		implements PictureInPictureRenderState {

	public static CharmScene of(int x0, int y0, int x1, int y1, ScreenRectangle scissor, ItemStack stack, ToolCharm charm, Vector3fc spot,
			Quaternionfc rotation, Vector3fc centre, float zoom) {
		return new CharmScene(x0, y0, x1, y1, 1f, scissor, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor), stack, charm,
				new Vector3f(spot), new Quaternionf(rotation), new Vector3f(centre), zoom);
	}
}
