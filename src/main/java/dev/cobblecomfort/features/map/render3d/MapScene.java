package dev.cobblecomfort.features.map.render3d;

import dev.cobblecomfort.features.map.data.MapLayer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * The 3D map for one frame, handed to the GUI as a picture-in-picture element and drawn by {@link MapSceneRenderer}:
 * the screen area and the GUI transform it is drawn with, the camera (a copy, so later changes don't reach a frame being
 * drawn), the layer, the colour behind the terrain (non-premultiplied ARGB) and the shape it is cut to: a rounded square
 * whose corner radius is {@code corner} times half its width (1 = a circle), or not cut at all if negative.
 */
public record MapScene(int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds,
		Matrix3x2fc pose, MapCamera camera, MapLayer layer, int background, float corner) implements PictureInPictureRenderState {

	public static MapScene of(int x0, int y0, int x1, int y1, ScreenRectangle scissor, MapCamera camera, MapLayer layer, int background) {
		return of(x0, y0, x1, y1, PictureInPictureRenderState.IDENTITY_POSE, scissor, camera, layer, background, -1f);
	}

	public static MapScene of(int x0, int y0, int x1, int y1, Matrix3x2fc pose, ScreenRectangle scissor, MapCamera camera, MapLayer layer,
			int background, float corner) {
		Matrix3x2f copy = new Matrix3x2f(pose);
		return new MapScene(x0, y0, x1, y1, 1f, scissor, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, copy, scissor), copy,
				camera.copy(), layer, background, corner);
	}
}
