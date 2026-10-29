package dev.cobblecomfort.features.map.death;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The death inventory in a death waypoint's tooltip: despawn timer, progress and the dropped items. Valuable items
 * come first with a golden frame; items that are back are dimmed and ticked, partly recovered ones show what is left.
 */
public final class DeathTooltip implements ClientTooltipComponent {
	private static final int COLUMNS = 9;
	private static final int CELL = 18;
	private static final int LINE = 10;
	private static final int GAP = 3;
	private static final int SLOT = 0x30FFFFFF;
	private static final int DIM = 0xB0202020;
	private static final int VALUABLE = 0xFFE8B83A;
	private static final int TICK = 0xFF6EE06E;

	public record Data(DeathRecord record) implements TooltipComponent {
	}

	private final DeathRecord record;
	private final List<Component> lines = new ArrayList<>();
	private final List<Integer> order;
	private final int columns;
	private final int rows;

	public DeathTooltip(Data data) {
		record = data.record();
		lines.add(DeathInventory.status(record));
		Component worst = DeathInventory.worstCase(record);
		if (worst != null) {
			lines.add(worst);
		}
		if (record.state != DeathRecord.State.RECOVERED) {
			lines.add(DeathInventory.progress(record));
		}
		order = DeathInventory.sorted(record);
		columns = Math.max(1, Math.min(COLUMNS, order.size()));
		rows = (order.size() + columns - 1) / columns;
	}

	@Override
	public int getWidth(Font font) {
		int width = columns * CELL;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return lines.size() * LINE + GAP + rows * CELL + GAP;
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		int ly = y;
		for (Component line : lines) {
			g.text(font, line, x, ly, 0xFFFFFFFF, true);
			ly += LINE;
		}
		int top = ly + GAP;
		boolean anyBack = false;
		for (int n = 0; n < order.size(); n++) {
			int index = order.get(n);
			ItemStack stack = record.stacks().get(index);
			int cx = x + (n % columns) * CELL;
			int cy = top + (n / columns) * CELL;
			int left = record.remaining(index);
			g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, SLOT);
			if (left > 0 && DeathInventory.valuable(stack)) {
				g.outline(cx, cy, CELL - 1, CELL - 1, VALUABLE);
			}
			g.item(stack, cx + 1, cy + 1);
			if (left > 0) {
				g.itemDecorations(font, stack, cx + 1, cy + 1, left == stack.getCount() ? null : String.valueOf(left));
			}
			anyBack |= left == 0;
		}
		if (!anyBack) {
			return;
		}
		// Items draw above fills of the same stratum, so the dimming needs a new one.
		g.nextStratum();
		for (int n = 0; n < order.size(); n++) {
			if (record.remaining(order.get(n)) == 0) {
				int cx = x + (n % columns) * CELL;
				int cy = top + (n / columns) * CELL;
				g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, DIM);
				g.text(font, "✔", cx + CELL - 1 - font.width("✔"), cy + CELL - 1 - font.lineHeight, TICK, true);
			}
		}
	}
}
