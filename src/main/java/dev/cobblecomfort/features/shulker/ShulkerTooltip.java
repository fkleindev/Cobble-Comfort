package dev.cobblecomfort.features.shulker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The shulker preview inside an item tooltip: compact summary, or the full 9x3 grid while the key is held. */
final class ShulkerTooltip implements ClientTooltipComponent {
	private static final int COLUMNS = 9;
	private static final int HINT_GAP = 2;

	/**
	 * Tooltip data handed out by ItemStack.getTooltipImage for shulker boxes (and ender chests, from the storage memory).
	 *
	 * @param note an extra grey line below the grid, or null
	 */
	record Data(ItemStack box, ShulkerContents contents, Component note) implements TooltipComponent {
	}

	private final ShulkerPreviewFeature feature;
	private final Data data;
	private final boolean full;
	private final List<ShulkerGrid.Cell> cells = new ArrayList<>();
	private final int columns;
	private final int rows;
	private final Component hint;

	ShulkerTooltip(ShulkerPreviewFeature feature, Data data) {
		this.feature = feature;
		this.data = data;
		ShulkerPreviewConfig config = feature.config();
		full = !config.compactByDefault || config.fullKey.isDown();
		if (full) {
			data.contents().slots().forEach(stack -> cells.add(new ShulkerGrid.Cell(stack, null)));
			columns = COLUMNS;
		} else {
			for (ShulkerContents.Entry entry : data.contents().summary(config.sortByCount)) {
				cells.add(new ShulkerGrid.Cell(entry.stack(), entry.total() > 1 ? ShulkerGrid.shortCount(entry.total()) : null));
			}
			columns = Math.max(1, Math.min(config.compactColumns, cells.size()));
		}
		rows = Math.max(1, (cells.size() + columns - 1) / columns);
		hint = !full && config.compactByDefault && config.keyHint
				? Component.translatable("cobblecomfort.shulker.hint", config.fullKey.getDisplayName()).withColor(0xFF808080)
				: null;
	}

	@Override
	public int getWidth(Font font) {
		int noteWidth = data.note() == null ? 0 : font.width(data.note());
		return Math.max(Math.max(ShulkerGrid.width(columns), hint == null ? 0 : font.width(hint)), noteWidth);
	}

	@Override
	public int getHeight(Font font) {
		return ShulkerGrid.height(rows) + HINT_GAP + (hint == null ? 0 : font.lineHeight + HINT_GAP)
				+ (data.note() == null ? 0 : font.lineHeight + HINT_GAP);
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		ShulkerPreviewConfig config = feature.config();
		long anim = config.popIn ? feature.tooltipAnimStart(data.box(), full) : 0L;
		ShulkerGrid.draw(g, font, x, y, cells, columns, rows, data.contents().color(),
				config.tint ? config.tintStrength : 0f, anim, config);
		if (hint != null) {
			g.text(font, hint, x, y + ShulkerGrid.height(rows) + HINT_GAP, 0xFFFFFFFF, true);
		}
		if (data.note() != null) {
			int noteY = y + ShulkerGrid.height(rows) + HINT_GAP + (hint == null ? 0 : font.lineHeight + HINT_GAP);
			g.text(font, data.note(), x, noteY, 0xFFFFFFFF, true);
		}
	}
}
