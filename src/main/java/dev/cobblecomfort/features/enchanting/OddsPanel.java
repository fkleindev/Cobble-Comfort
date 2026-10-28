package dev.cobblecomfort.features.enchanting;

import dev.cobblecomfort.features.enchantdesc.EnchantDescFeature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Side panel next to the enchanting table listing every possible enchantment with its chance per slot. */
final class OddsPanel {
	private static final int WIDTH = 176;
	private static final int GAP = 4;
	private static final int PAD = 6;
	private static final int ROW_HEIGHT = 11;
	private static final int HEADER_HEIGHT = 40;
	private static final int COLUMN_WIDTH = 24;
	private static final int BORDER = 0xFF4A3B6B;
	private static final int BACKGROUND = 0xF0140F1E;
	private static final int ROW_HOVER = 0x30FFFFFF;
	private static final int ZERO_COLOR = 0xFF505050;
	private static final int LOW_COLOR = 0xFF9A9AA8;
	private static final int HIGH_COLOR = 0xFF6CFF6C;
	private static final String[] SLOT_LABELS = {"I", "II", "III"};

	private final EnchantingFeature feature;
	private int scroll;

	OddsPanel(EnchantingFeature feature) {
		this.feature = feature;
	}

	private record Bounds(int x, int y, int height) {
	}

	private static Bounds bounds(ContainerScreenAccessor gui) {
		int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int x = gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() + GAP;
		if (x + WIDTH > screenWidth - 2) {
			x = gui.cobblecomfort$leftPos() - WIDTH - GAP;
		}
		if (x < 2) {
			x = screenWidth - WIDTH - 2;
		}
		return new Bounds(x, gui.cobblecomfort$topPos(), gui.cobblecomfort$imageHeight());
	}

	private static int visibleRows(Bounds b) {
		return (b.height() - HEADER_HEIGHT - PAD) / ROW_HEIGHT;
	}

	/** Scrolls the list when the mouse is over the panel; returns true if the scroll was used. */
	boolean scroll(ContainerScreenAccessor gui, double mouseX, double mouseY, double amount) {
		Bounds b = bounds(gui);
		if (mouseX < b.x() || mouseX >= b.x() + WIDTH || mouseY < b.y() || mouseY >= b.y() + b.height()) {
			return false;
		}
		scroll -= (int) Math.signum(amount);
		return true;
	}

	void draw(GuiGraphicsExtractor g, ContainerScreenAccessor gui, EnchantOdds odds, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		Bounds b = bounds(gui);
		int x0 = b.x();
		int y0 = b.y();
		int x1 = x0 + WIDTH;
		int y1 = y0 + b.height();
		RenderUtil.roundedFill(g, x0, y0, x1, y1, 4, BORDER);
		RenderUtil.roundedFill(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 3, BACKGROUND);
		g.text(font, Component.translatable("cobblecomfort.enchanting.panel_title").withStyle(ChatFormatting.LIGHT_PURPLE),
				x0 + PAD, y0 + PAD, 0xFFFFFFFF, true);

		if (odds == null || odds.rows().isEmpty()) {
			g.text(font, Component.translatable(odds == null || odds.noItem()
					? "cobblecomfort.enchanting.panel_empty" : "cobblecomfort.enchanting.panel_none"),
					x0 + PAD, y0 + HEADER_HEIGHT, 0xFFAAAAAA, false);
			return;
		}

		int columnsX = x1 - PAD - COLUMN_WIDTH * EnchantOdds.SLOTS;
		for (int slot = 0; slot < EnchantOdds.SLOTS; slot++) {
			int cx = columnsX + COLUMN_WIDTH * slot + COLUMN_WIDTH / 2;
			g.centeredText(font, Component.literal(SLOT_LABELS[slot]).withStyle(ChatFormatting.WHITE), cx, y0 + 19, 0xFFFFFFFF);
			String cost = odds.cost(slot) > 0 ? String.valueOf(odds.cost(slot)) : "–";
			g.centeredText(font, Component.literal(cost).withStyle(ChatFormatting.GREEN), cx, y0 + 29, 0xFFFFFFFF);
		}
		g.text(font, Component.translatable("cobblecomfort.enchanting.panel_slot").withStyle(ChatFormatting.GRAY),
				x0 + PAD, y0 + 19, 0xFFFFFFFF, false);
		g.text(font, Component.translatable("cobblecomfort.enchanting.panel_cost").withStyle(ChatFormatting.DARK_GRAY),
				x0 + PAD, y0 + 29, 0xFFFFFFFF, false);
		g.fill(x0 + PAD, y0 + HEADER_HEIGHT - 2, x1 - PAD, y0 + HEADER_HEIGHT - 1, 0x40FFFFFF);

		List<EnchantOdds.Row> rows = odds.rows();
		int visible = visibleRows(b);
		scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - visible));
		int nameWidth = columnsX - x0 - PAD - 2;
		EnchantOdds.Row hovered = null;
		for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
			EnchantOdds.Row row = rows.get(scroll + i);
			int ry = y0 + HEADER_HEIGHT + i * ROW_HEIGHT;
			if (mouseX >= x0 && mouseX < x1 && mouseY >= ry - 1 && mouseY < ry + ROW_HEIGHT - 1) {
				g.fill(x0 + 2, ry - 1, x1 - 2, ry + ROW_HEIGHT - 1, ROW_HOVER);
				hovered = row;
			}
			boolean possible = row.bestChance() > 0f;
			String name = font.plainSubstrByWidth(row.enchantment().value().description().getString(), nameWidth);
			g.text(font, Component.literal(name).withStyle(possible ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
					x0 + PAD, ry, 0xFFFFFFFF, false);
			for (int slot = 0; slot < EnchantOdds.SLOTS; slot++) {
				float chance = row.chance()[slot];
				int color = chance <= 0f ? ZERO_COLOR : RenderUtil.lerpColor(LOW_COLOR, HIGH_COLOR, Math.min(1f, chance * 1.5f));
				g.centeredText(font, EnchantingFeature.percent(chance), columnsX + COLUMN_WIDTH * slot + COLUMN_WIDTH / 2, ry, color);
			}
		}
		if (rows.size() > visible) {
			drawScrollbar(g, x1 - 3, y0 + HEADER_HEIGHT - 1, visible * ROW_HEIGHT, rows.size(), visible);
		}
		if (hovered != null) {
			g.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int height, int total, int visible) {
		int thumb = Math.max(8, height * visible / total);
		int top = y + (height - thumb) * scroll / Math.max(1, total - visible);
		g.fill(x, y, x + 1, y + height, 0x30FFFFFF);
		g.fill(x, top, x + 1, top + thumb, 0xA0FFFFFF);
	}

	private List<Component> tooltip(EnchantOdds.Row row) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(row.enchantment().value().description().getString()).withStyle(ChatFormatting.WHITE));
		for (int slot = 0; slot < EnchantOdds.SLOTS; slot++) {
			float chance = row.chance()[slot];
			String levels = EnchantingFeature.levelRange(row.minLevel()[slot], row.maxLevel()[slot]);
			lines.add(Component.translatable("cobblecomfort.enchanting.panel_row", SLOT_LABELS[slot],
					EnchantingFeature.percent(chance), levels.isEmpty() ? "–" : levels)
					.withStyle(chance > 0f ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
		}
		if (feature.config().panelDescriptions) {
			EnchantDescFeature.INSTANCE.addDetails(row.enchantment(), lines::add);
		}
		return lines;
	}
}
