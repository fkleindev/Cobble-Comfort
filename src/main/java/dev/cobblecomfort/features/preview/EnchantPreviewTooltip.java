package dev.cobblecomfort.features.preview;

import dev.cobblecomfort.features.enchantdesc.EnchantDescFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** An enchanted book's enchantments as a compact list: an item they go on, the name and the level as pips. */
final class EnchantPreviewTooltip implements ClientTooltipComponent {
	private static final int ROW_HEIGHT = 12;
	private static final float ICON_SCALE = 0.625f;
	private static final int TEXT_X = 13;
	private static final int PIP_WIDTH = 3;
	private static final int PIP_HEIGHT = 5;
	private static final int PIP_GAP = 1;
	private static final int PIPS_X_GAP = 4;
	private static final int GAP = 2;
	private static final int PIP_EMPTY = 0xFF3A3A3A;
	private static final int PIP_FILLED = 0xFFC8C8C8;
	private static final int PIP_MAX = 0xFFFFAA00;

	/** @param icon an item the enchantment goes on, @param name the vanilla name with level */
	record Row(ItemStack icon, Component name, int level, int maxLevel) {
	}

	record Data(List<Row> rows) implements TooltipComponent {
	}

	private final Data data;
	private final Component hint;

	EnchantPreviewTooltip(ItemPreviewFeature feature, Data data) {
		this.data = data;
		// Holding the key shows vanilla's lines with the enchantment descriptions, so point to that when they are on.
		hint = feature.config().keyHint && EnchantDescFeature.INSTANCE.isEnabled() ? EnchantDescFeature.INSTANCE.hint() : null;
	}

	private static int pipsWidth(Row row) {
		return row.maxLevel() > 1 ? PIPS_X_GAP + row.maxLevel() * (PIP_WIDTH + PIP_GAP) - PIP_GAP : 0;
	}

	@Override
	public int getWidth(Font font) {
		int width = hint == null ? 0 : font.width(hint);
		for (Row row : data.rows()) {
			width = Math.max(width, TEXT_X + font.width(row.name()) + pipsWidth(row));
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return data.rows().size() * ROW_HEIGHT + (hint == null ? 0 : font.lineHeight + GAP);
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		int rowY = y;
		for (Row row : data.rows()) {
			g.pose().pushMatrix();
			g.pose().translate(x, rowY + 1);
			g.pose().scale(ICON_SCALE, ICON_SCALE);
			g.fakeItem(row.icon(), 0, 0);
			g.pose().popMatrix();
			int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
			g.text(font, row.name(), x + TEXT_X, textY, 0xFFAAAAAA, true);
			if (row.maxLevel() > 1) {
				int pipX = x + TEXT_X + font.width(row.name()) + PIPS_X_GAP;
				int pipY = rowY + (ROW_HEIGHT - PIP_HEIGHT) / 2;
				int filled = row.level() >= row.maxLevel() ? PIP_MAX : PIP_FILLED;
				for (int i = 0; i < row.maxLevel(); i++) {
					g.fill(pipX, pipY, pipX + PIP_WIDTH, pipY + PIP_HEIGHT, i < row.level() ? filled : PIP_EMPTY);
					pipX += PIP_WIDTH + PIP_GAP;
				}
			}
			rowY += ROW_HEIGHT;
		}
		if (hint != null) {
			g.text(font, hint, x, rowY + GAP, 0xFFFFFFFF, true);
		}
	}
}
