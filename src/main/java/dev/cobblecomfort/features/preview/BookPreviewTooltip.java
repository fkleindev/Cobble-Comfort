package dev.cobblecomfort.features.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.ArrayList;
import java.util.List;

/** One page of a written book or book and quill in its tooltip, on paper like in the book screen. */
final class BookPreviewTooltip implements ClientTooltipComponent {
	/** Text width and lines per page of the vanilla book screen. */
	private static final int TEXT_WIDTH = 114;
	private static final int MAX_LINES = 14;
	private static final float SMALL_SCALE = 0.75f;
	private static final int PADDING = 6;
	private static final int GAP = 2;
	private static final int PAPER_COLOR = 0xFFF3EAD3;
	private static final int EDGE_COLOR = 0xFF8C7451;
	private static final int TEXT_COLOR = 0xFF000000;
	private static final int INDICATOR_COLOR = 0xFF7A6A50;
	private static final int HINT_COLOR = 0xFF808080;

	/** @param key identifies the book, for remembering its page */
	record Data(List<Component> pages, int key) implements TooltipComponent {
	}

	private final float scale;
	private final List<FormattedCharSequence> text;
	private final Component indicator;
	private final List<Component> hints = new ArrayList<>();

	BookPreviewTooltip(ItemPreviewFeature feature, Data data) {
		scale = feature.large() ? 1f : SMALL_SCALE;
		int count = data.pages().size();
		int page = feature.bookPage(data.key(), count);
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = font.split(data.pages().get(page), TEXT_WIDTH);
		text = lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
		indicator = count > 1 ? Component.translatable("book.pageIndicator", page + 1, count) : null;
		if (count > 1 && feature.scrollPages()) {
			hints.add(Component.translatable("cobblecomfort.preview.book_scroll").withColor(HINT_COLOR));
		}
		Component hint = feature.keyHint();
		if (hint != null) {
			hints.add(hint);
		}
	}

	private int paperWidth() {
		return Math.round((TEXT_WIDTH + PADDING * 2) * scale);
	}

	private int headerHeight(Font font) {
		return indicator == null ? 0 : font.lineHeight + GAP;
	}

	private int paperHeight(Font font) {
		int lines = Math.max(1, text.size());
		return Math.round((PADDING * 2 + headerHeight(font) + lines * font.lineHeight) * scale);
	}

	@Override
	public int getWidth(Font font) {
		int width = paperWidth();
		for (Component hint : hints) {
			width = Math.max(width, font.width(hint));
		}
		return width;
	}

	@Override
	public int getHeight(Font font) {
		return paperHeight(font) + GAP + hints.size() * (font.lineHeight + 1);
	}

	@Override
	public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
		int paperWidth = paperWidth();
		int paperHeight = paperHeight(font);
		g.fill(x, y, x + paperWidth, y + paperHeight, EDGE_COLOR);
		g.fill(x + 1, y + 1, x + paperWidth - 1, y + paperHeight - 1, PAPER_COLOR);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		int right = TEXT_WIDTH + PADDING;
		if (indicator != null) {
			g.text(font, indicator, right - font.width(indicator), PADDING, INDICATOR_COLOR, false);
		}
		int lineY = PADDING + headerHeight(font);
		for (FormattedCharSequence line : text) {
			g.text(font, line, PADDING, lineY, TEXT_COLOR, false);
			lineY += font.lineHeight;
		}
		g.pose().popMatrix();
		int hintY = y + paperHeight + GAP;
		for (Component hint : hints) {
			g.text(font, hint, x, hintY, 0xFFFFFFFF, true);
			hintY += font.lineHeight + 1;
		}
	}
}
