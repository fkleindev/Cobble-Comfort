package dev.cobblecomfort.features.hud.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RenderUtil {
	private RenderUtil() {
	}

	/** Fills a rectangle with rounded corners, built from one-pixel rows at the corners. */
	public static void roundedFill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius, int color) {
		int r = Math.max(0, Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
		if (r == 0) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		g.fill(x0, y0 + r, x1, y1 - r, color);
		for (int i = 0; i < r; i++) {
			int inset = cornerInset(r, i);
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, color);
		}
	}

	/** A one-pixel rounded border whose colour fades from top to bottom. */
	public static void roundedOutline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int radius,
			int colorTop, int colorBottom) {
		int height = y1 - y0;
		if (height < 2 || x1 - x0 < 2) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min((x1 - x0) / 2, height / 2)));
		for (int row = 0; row < height; row++) {
			int color = lerpColor(colorTop, colorBottom, height == 1 ? 0f : row / (float) (height - 1));
			int y = y0 + row;
			int fromEdge = Math.min(row, height - 1 - row);
			int outer = fromEdge < r ? cornerInset(r, fromEdge) : 0;
			if (fromEdge == 0) {
				g.fill(x0 + outer, y, x1 - outer, y + 1, color);
				continue;
			}
			int innerRow = fromEdge - 1;
			int inner = 1 + (innerRow < r - 1 ? cornerInset(r - 1, innerRow) : 0);
			inner = Math.max(inner, outer + 1);
			g.fill(x0 + outer, y, x0 + inner, y + 1, color);
			g.fill(x1 - inner, y, x1 - outer, y + 1, color);
		}
	}

	private static int cornerInset(int radius, int row) {
		float dy = radius - row - 0.5f;
		return radius - (int) Math.round(Math.sqrt(radius * radius - dy * dy));
	}

	public static int lerpColor(int a, int b, float t) {
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	public static int withAlpha(int argb, float alphaFactor) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alphaFactor, 0f, 1f));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
