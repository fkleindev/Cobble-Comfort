package dev.cobblecomfort.features.food;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Locale;

/** Food values in an item tooltip: shanks for hunger, golden outlines for saturation, plus the numbers. */
final class FoodTooltip implements ClientTooltipComponent {
	private static final int ICON = 9;
	private static final int STEP = 8;
	private static final int GROUP_GAP = 6;
	private static final int TEXT_GAP = 4;
	private static final int MAX_ICONS = 10;

	record Data(int nutrition, float saturation) implements TooltipComponent {
	}

	private final Data data;
	private final int shanks;
	private final int outlines;
	private final String text;

	FoodTooltip(Data data) {
		this.data = data;
		shanks = Math.min(MAX_ICONS, (data.nutrition() + 1) / 2);
		outlines = Math.min(MAX_ICONS, (int) Math.ceil(data.saturation() / 2f));
		text = "+" + data.nutrition() + "  +" + String.format(Locale.ROOT, "%.1f", data.saturation());
	}

	private int iconsWidth(int count) {
		return count <= 0 ? 0 : (count - 1) * STEP + ICON;
	}

	@Override
	public int getWidth(Font font) {
		return iconsWidth(shanks) + (outlines > 0 ? GROUP_GAP + iconsWidth(outlines) : 0) + TEXT_GAP + font.width(text);
	}

	@Override
	public int getHeight(Font font) {
		return ICON + 2;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		int cx = x;
		for (int i = 0; i < shanks; i++) {
			boolean half = data.nutrition() % 2 == 1 && i == shanks - 1;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, half ? FoodHudFeature.FOOD_HALF : FoodHudFeature.FOOD_FULL, cx, y, ICON, ICON);
			cx += STEP;
		}
		if (shanks > 0) {
			cx += ICON - STEP;
		}
		if (outlines > 0) {
			cx += GROUP_GAP;
			for (int i = 0; i < outlines; i++) {
				boolean half = data.saturation() - i * 2 < 1.5f;
				g.blitSprite(RenderPipelines.GUI_TEXTURED, half ? FoodHudFeature.SATURATION_HALF : FoodHudFeature.SATURATION_FULL,
						cx, y, ICON, ICON);
				cx += STEP;
			}
			cx += ICON - STEP;
		}
		g.text(font, text, cx + TEXT_GAP, y + 1, 0xFFAAAAAA, true);
	}
}
