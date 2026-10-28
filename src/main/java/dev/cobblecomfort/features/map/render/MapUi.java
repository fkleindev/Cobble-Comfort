package dev.cobblecomfort.features.map.render;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/** The look of the map screens: dark, slightly see-through floating panels with rounded corners and an accent colour. */
public final class MapUi {
	public static final int PANEL = 0xD8161A22;
	public static final int PANEL_SOLID = 0xF0161A22;
	public static final int PANEL_EDGE_TOP = 0x40FFFFFF;
	public static final int PANEL_EDGE_BOTTOM = 0x14FFFFFF;
	public static final int SHADOW = 0x50000000;
	public static final int HOVER = 0x22FFFFFF;
	public static final int FIELD = 0xFF0C0E13;
	public static final int TEXT = 0xFFF0F0F4;
	public static final int TEXT_DIM = 0xFF9AA0AE;
	public static final int TEXT_FAINT = 0xFF646A78;
	public static final int ACCENT = 0xFF5AA9FF;
	public static final int DANGER = 0xFFE5534B;
	public static final int RADIUS = 4;

	private MapUi() {
	}

	/** A floating panel with a soft shadow and a light top edge. */
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		panel(g, x0, y0, x1, y1, PANEL);
	}

	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		RenderUtil.roundedFill(g, x0, y0 + 1, x1, y1 + 2, RADIUS + 1, SHADOW);
		RenderUtil.roundedFill(g, x0, y0, x1, y1, RADIUS, color);
		RenderUtil.roundedOutline(g, x0, y0, x1, y1, RADIUS, PANEL_EDGE_TOP, PANEL_EDGE_BOTTOM);
	}

	/** A fully rounded pill, used for small labels. */
	public static void pill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		RenderUtil.roundedFill(g, x0, y0, x1, y1, (y1 - y0) / 2, color);
	}

	/** A small rounded square button face: transparent until hovered, accent tinted when selected. */
	public static void buttonFace(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean hovered, boolean selected, int accent) {
		if (selected) {
			RenderUtil.roundedFill(g, x0, y0, x1, y1, 3, RenderUtil.withAlpha(accent, 0.35f));
			RenderUtil.roundedOutline(g, x0, y0, x1, y1, 3, RenderUtil.withAlpha(accent, 0.9f), RenderUtil.withAlpha(accent, 0.6f));
		} else if (hovered) {
			RenderUtil.roundedFill(g, x0, y0, x1, y1, 3, HOVER);
		}
	}

	/** A text button with a filled face (used in the waypoint screens). */
	public static void textButton(GuiGraphicsExtractor g, Font font, Component label, int x0, int y0, int x1, int y1,
			boolean hovered, boolean active, int color) {
		int face = !active ? 0x18FFFFFF : hovered ? RenderUtil.withAlpha(color, 0.55f) : RenderUtil.withAlpha(color, 0.3f);
		RenderUtil.roundedFill(g, x0, y0, x1, y1, 3, face);
		RenderUtil.roundedOutline(g, x0, y0, x1, y1, 3, active ? RenderUtil.withAlpha(color, hovered ? 1f : 0.7f) : 0x20FFFFFF,
				active ? RenderUtil.withAlpha(color, 0.4f) : 0x10FFFFFF);
		int textColor = active ? TEXT : TEXT_FAINT;
		g.centeredText(font, label, (x0 + x1) / 2, (y0 + y1 - font.lineHeight) / 2 + 1, textColor);
	}

	/** A whole square texture of {@code size} pixels stretched over a rectangle, faded to {@code alpha}. */
	public static void blitFaded(GuiGraphicsExtractor g, Identifier texture, int x0, int y0, int x1, int y1, int size, float alpha) {
		if (alpha >= 1f) {
			g.blit(texture, x0, y0, x1, y1, 0f, 1f, 0f, 1f);
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0f, 0f, x1 - x0, y1 - y0, size, size, size, size,
				RenderUtil.withAlpha(0xFFFFFFFF, alpha));
	}

	/** An item icon drawn at a smaller size, centred on (cx, cy). */
	public static void icon(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float size) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx - size / 2f, cy - size / 2f);
		pose.scale(size / 16f);
		g.item(stack, 0, 0);
		pose.popMatrix();
	}

	/** Text drawn at a smaller scale; returns the width it took. */
	public static int smallText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, float scale) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale);
		g.text(font, text, 0, 0, color, false);
		pose.popMatrix();
		return Math.round(font.width(text) * scale);
	}

	/**
	 * Text whose visible glyphs are centred on (cx, cy) at a scale. Font widths include one pixel of spacing after the
	 * last glyph and capitals are 7 pixels tall, so both are taken out to centre what is actually drawn.
	 */
	public static void centeredText(GuiGraphicsExtractor g, Font font, Component text, float cx, float cy, int color, float scale) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx - (font.width(text) - 1) * scale / 2f, cy - 3.5f * scale);
		pose.scale(scale);
		g.text(font, text, 0, 0, color, false);
		pose.popMatrix();
	}

	/** A small dark pill with text centred in it, horizontally centred on cx with its top at y. */
	public static void label(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color, float scale, int background) {
		int w = Math.round((font.width(text) - 1) * scale) + 6;
		int x0 = cx - w / 2;
		RenderUtil.roundedFill(g, x0, y, x0 + w, y + 9, 3, background);
		centeredText(g, font, text, x0 + w / 2f, y + 4.5f, color, scale);
	}

	// ---------------------------------------------------------------- dimensions

	/** The vanilla dimensions the map can switch between, with their accent colour and icon. */
	public enum Dim {
		OVERWORLD("minecraft:overworld", 0xFF6CC24A, 1.0),
		NETHER("minecraft:the_nether", 0xFFE0563D, 8.0),
		END("minecraft:the_end", 0xFFB58BE0, 1.0);

		public final String id;
		public final int accent;
		/** Blocks in the overworld per block in this dimension. */
		public final double scale;

		Dim(String id, int accent, double scale) {
			this.id = id;
			this.accent = accent;
			this.scale = scale;
		}

		public ItemStack icon() {
			return new ItemStack(switch (this) {
				case OVERWORLD -> Items.GRASS_BLOCK;
				case NETHER -> Items.NETHERRACK;
				case END -> Items.END_STONE;
			});
		}

		public Component label() {
			return Component.translatable("cobblecomfort.storage.dim." + id.substring(id.indexOf(':') + 1));
		}

		/** The vanilla dimension with this id, or null for other (modded) dimensions. */
		public static Dim of(String id) {
			for (Dim dim : values()) {
				if (dim.id.equals(id)) {
					return dim;
				}
			}
			return null;
		}

		public static int accentOf(String id) {
			Dim dim = of(id);
			return dim == null ? ACCENT : dim.accent;
		}

		public static double scaleOf(String id) {
			Dim dim = of(id);
			return dim == null ? 1.0 : dim.scale;
		}
	}
}
