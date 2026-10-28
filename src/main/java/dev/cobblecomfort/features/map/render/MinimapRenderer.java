package dev.cobblecomfort.features.map.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.MapConfig;
import dev.cobblecomfort.features.map.data.MapLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Composes the minimap image on the CPU each time the view changes: map pixels around the player, rotated with the
 * view if enabled, cut to a circle or square with a border. Overlays (arrow, letters, markers) are drawn on top as GUI.
 */
public final class MinimapRenderer {
	private static final Identifier TEXTURE_ID = CobbleComfortClient.id("map/minimap");
	private static final int MIN_RES = 64;
	private static final int MAX_RES = 320;
	private static final int BORDER = 3;
	private static final int INNER_SHADOW = 5;
	private static final int CORNER = 6;
	private static final int BACKGROUND = 0xFF0E0E12;
	private static final long REFRESH_MS = 400L;

	/** Gives the colour of unexplored blocks (seed terrain), or 0. */
	@FunctionalInterface
	public interface Fallback {
		int color(int bx, int bz);
	}

	private DynamicTexture texture;
	private int res;
	private double lastX = Double.NaN, lastZ, lastYaw;
	private float lastZoom;
	private int lastSize;
	private long lastMs;
	private int lastLayerVersion;

	public Identifier texture() {
		return TEXTURE_ID;
	}

	/** Rebuilds the image if the view changed; returns false if there is nothing to show yet. */
	public boolean update(MapConfig config, MapLayer layer, Fallback fallback, double px, double pz, float yaw, int size, int layerVersion) {
		int wanted = Math.clamp(size * (int) Math.ceil(Minecraft.getInstance().getWindow().getGuiScale()), MIN_RES, MAX_RES);
		if (texture == null || wanted != res) {
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
			}
			res = wanted;
			texture = new DynamicTexture(() -> "Cobble Comfort minimap", res, res, false);
			Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
			lastX = Double.NaN;
		}
		long now = System.currentTimeMillis();
		boolean moved = Double.isNaN(lastX) || Math.abs(px - lastX) > 0.05 || Math.abs(pz - lastZ) > 0.05
				|| (config.rotateMinimap && Math.abs(yaw - lastYaw) > 0.2) || lastZoom != config.minimapZoom || lastSize != size;
		if (!moved && layerVersion == lastLayerVersion && now - lastMs < REFRESH_MS) {
			return true;
		}
		lastX = px;
		lastZ = pz;
		lastYaw = yaw;
		lastZoom = config.minimapZoom;
		lastSize = size;
		lastMs = now;
		lastLayerVersion = layerVersion;
		compose(config, layer, fallback, px, pz, yaw, size);
		return true;
	}

	private void compose(MapConfig config, MapLayer layer, Fallback fallback, double px, double pz, float yaw, int size) {
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		double guiPerPixel = size / (double) res;
		double pixelsPerGui = res / (double) size;
		double blocksPerGui = 1.0 / Math.max(0.05f, config.minimapZoom);
		double half = res / 2.0;
		double border = BORDER * pixelsPerGui;
		double shadow = INNER_SHADOW * pixelsPerGui;
		double corner = CORNER * pixelsPerGui;
		int borderLight = RenderUtil.lerpColor(config.borderColor, 0xFFFFFFFF, 0.3f);
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad);
		double rx = -Math.cos(rad), rz = -Math.sin(rad);
		for (int y = 0; y < res; y++) {
			for (int x = 0; x < res; x++) {
				double dx = x + 0.5 - half;
				double dy = y + 0.5 - half;
				// Signed distance to the outer edge in pixels: negative inside.
				double edge;
				if (config.roundMinimap) {
					edge = Math.sqrt(dx * dx + dy * dy) - half;
				} else {
					double qx = Math.abs(dx) - (half - corner);
					double qy = Math.abs(dy) - (half - corner);
					edge = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - corner;
				}
				if (edge > 0.5) {
					image.setPixel(x, y, 0);
					continue;
				}
				float coverage = (float) Math.min(1.0, 0.5 - edge);
				if (edge > -border) {
					// Frame: a light line on the inside fading into the frame colour.
					float t = (float) Math.min(1.0, (edge + border) / Math.max(1.0, pixelsPerGui));
					image.setPixel(x, y, withAlpha(RenderUtil.lerpColor(borderLight, config.borderColor, t), coverage));
					continue;
				}
				double sx = dx * guiPerPixel * blocksPerGui;
				double sy = dy * guiPerPixel * blocksPerGui;
				double wx;
				double wz;
				if (config.rotateMinimap) {
					wx = px + rx * sx - fx * sy;
					wz = pz + rz * sx - fz * sy;
				} else {
					wx = px + sx;
					wz = pz + sy;
				}
				int bx = (int) Math.floor(wx);
				int bz = (int) Math.floor(wz);
				int color = layer.colorAt(bx, bz, config.heightShading);
				if (color == 0 && fallback != null) {
					color = fallback.color(bx, bz);
				}
				color = color == 0 ? BACKGROUND : color;
				// Inner shadow along the frame, so the map looks set into it.
				double fromFrame = -edge - border;
				if (fromFrame < shadow) {
					color = RenderUtil.lerpColor(color, 0xFF000000, (float) (0.4 * (1 - fromFrame / shadow)));
				}
				image.setPixel(x, y, color);
			}
		}
		texture.upload();
	}

	private static int withAlpha(int argb, float alpha) {
		return Math.round(((argb >>> 24) & 0xFF) * alpha) << 24 | (argb & 0xFFFFFF);
	}

	public void close() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
			texture = null;
		}
	}
}
