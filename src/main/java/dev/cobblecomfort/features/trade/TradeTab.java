package dev.cobblecomfort.features.trade;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.invtools.ItemSearch;
import dev.cobblecomfort.features.map.MapFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The "Traders" tab of the storage memory screen: remembered traders whose offers (or name or job) match the search,
 * cheapest first, each with the matching offers.
 */
public final class TradeTab {
	private static final int ICON = 18;
	private static final int ARROW = 10;
	private static final int OFFER_GAP = 6;
	private static final int ROW_HOVER = 0x22FFFFFF;
	private static final int SOLD_OUT = 0xA0101018;

	/** A trader in the list, with the offers that made it match. */
	public record Result(TradeMerchant merchant, List<TradeOffer> shown) {
	}

	/** What the mouse is over: a row, and possibly one of its offers. */
	public record Hovered(Result result, TradeOffer offer) {
	}

	private final ItemSearch search = new ItemSearch();
	private int builtVersion = -1;
	private String builtQuery;
	private List<Result> results = List.of();

	public List<Result> results() {
		return results;
	}

	public void rebuild(String query) {
		TradeStore store = TradeFeature.INSTANCE.store();
		if (store.version() == builtVersion && query.equals(builtQuery)) {
			return;
		}
		builtVersion = store.version();
		builtQuery = query;
		search.setQuery(query);
		Minecraft mc = Minecraft.getInstance();
		List<Result> list = new ArrayList<>();
		for (TradeMerchant merchant : store.all()) {
			if (!search.active() || nameMatches(merchant)) {
				list.add(new Result(merchant, merchant.offers));
				continue;
			}
			List<TradeOffer> matching = new ArrayList<>();
			for (TradeOffer offer : merchant.offers) {
				if (matches(offer)) {
					matching.add(offer);
				}
			}
			if (!matching.isEmpty()) {
				matching.sort(Comparator.comparingInt(TradeOffer::price));
				list.add(new Result(merchant, matching));
			}
		}
		Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
		String dim = mc.level == null ? "" : MapFeature.dimensionId(mc.level);
		Comparator<Result> order = Comparator.comparingInt((Result r) -> r.merchant().dimension.equals(dim) ? 0 : 1);
		if (search.active()) {
			order = order.thenComparingInt(r -> r.shown().isEmpty() ? Integer.MAX_VALUE : r.shown().getFirst().price());
		}
		list.sort(order.thenComparingDouble(r -> r.merchant().distanceTo(eye)));
		results = list;
	}

	private boolean nameMatches(TradeMerchant merchant) {
		String query = search.query();
		return merchant.name.toLowerCase(Locale.ROOT).contains(query)
				|| TradeFeature.INSTANCE.title(merchant).getString().toLowerCase(Locale.ROOT).contains(query);
	}

	private boolean matches(TradeOffer offer) {
		var config = InventoryToolsFeature.INSTANCE.config();
		return search.matches(offer.result(), config) || offer.buying() && (search.matches(offer.costA(), config)
				|| search.matches(offer.costB(), config));
	}

	public Component emptyText() {
		return TradeFeature.INSTANCE.store().all().isEmpty()
				? Component.translatable("cobblecomfort.trade.nothing_yet")
				: Component.translatable("cobblecomfort.storage.no_results");
	}

	// ---------------------------------------------------------------- drawing

	/** Draws the rows from {@code y} on; returns what the mouse is over. */
	public Hovered draw(GuiGraphicsExtractor g, Font font, int x0, int x1, int y, int bodyTop, int bodyBottom, int row,
			int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
		String dim = mc.level == null ? "" : MapFeature.dimensionId(mc.level);
		Hovered hovered = null;
		for (Result result : results) {
			if (y + row >= bodyTop && y <= bodyBottom) {
				TradeMerchant merchant = result.merchant();
				boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + row - 2;
				if (hover) {
					RenderUtil.roundedFill(g, x0 - 2, y - 1, x1 + 2, y + row - 1, 3, ROW_HOVER);
				}
				g.item(TradeFeature.icon(merchant), x0, y + 2);
				int textX = x0 + 20;
				Component name = nameLine(merchant);
				g.text(font, name, textX, y + 1, 0xFFFFFFFF, true);
				Component location = locationLine(merchant, eye, dim);
				g.text(font, location, textX, y + 12, 0xFF9AA0AE, false);
				int left = textX + Math.max(font.width(name), font.width(location)) + 8;
				TradeOffer offer = drawOffers(g, font, result, left, x1, y + 3, hover ? mouseX : -1);
				if (hover) {
					hovered = new Hovered(result, offer);
				}
			}
			y += row;
		}
		return hovered;
	}

	/** "Librarian · Master „Bob“". */
	private static Component nameLine(TradeMerchant merchant) {
		MutableComponent text = TradeFeature.INSTANCE.title(merchant).copy();
		if (!merchant.name.isEmpty()) {
			text.append(" ").append(TradeFeature.INSTANCE.displayName(merchant));
		}
		return text;
	}

	/** "12, 64, -30 · 34m · 2 h ago". */
	private static Component locationLine(TradeMerchant merchant, Vec3 eye, String dim) {
		return Component.literal((int) Math.floor(merchant.x) + ", " + (int) Math.floor(merchant.y) + ", " + (int) Math.floor(merchant.z) + " · ")
				.append(TradeFeature.where(merchant, eye, dim)).append(" · ").append(TradeFeature.age(merchant));
	}

	private static int offerWidth(TradeOffer offer) {
		return ICON + (offer.costB().isEmpty() ? 0 : ICON) + ARROW + ICON;
	}

	/** Right-aligned offers ("cost → result") that fit between {@code left} and {@code right}; returns the hovered one. */
	private static TradeOffer drawOffers(GuiGraphicsExtractor g, Font font, Result result, int left, int right, int y, int mouseX) {
		List<TradeOffer> fitting = new ArrayList<>();
		int width = 0;
		for (TradeOffer offer : result.shown()) {
			int needed = offerWidth(offer) + (fitting.isEmpty() ? 0 : OFFER_GAP);
			if (right - (width + needed) < left + font.width("+99") + 4) {
				break;
			}
			fitting.add(offer);
			width += needed;
		}
		int x = right - width;
		if (fitting.size() < result.shown().size()) {
			String more = "+" + (result.shown().size() - fitting.size());
			g.text(font, more, x - font.width(more) - 4, y + 5, 0xFF9AA0AE, false);
		}
		TradeOffer hovered = null;
		for (TradeOffer offer : fitting) {
			int start = x;
			g.item(offer.costA(), x, y);
			g.itemDecorations(font, offer.costA(), x, y);
			x += ICON;
			if (!offer.costB().isEmpty()) {
				g.item(offer.costB(), x, y);
				g.itemDecorations(font, offer.costB(), x, y);
				x += ICON;
			}
			g.text(font, "→", x + 1, y + 4, 0xFF9AA0AE, false);
			x += ARROW;
			g.item(offer.result(), x, y);
			g.itemDecorations(font, offer.result(), x, y);
			x += ICON;
			if (offer.outOfStock()) {
				g.fill(start, y - 1, x, y + 17, SOLD_OUT);
			}
			if (mouseX >= start && mouseX < x) {
				hovered = offer;
			}
			x += OFFER_GAP;
		}
		return hovered;
	}

	/** The result's tooltip with price, stock and when it was seen. */
	public static List<Component> offerTooltip(TradeMerchant merchant, TradeOffer offer) {
		List<Component> lines = new ArrayList<>(ItemSearch.plainTooltip(offer.result()));
		lines.add(Component.translatable("cobblecomfort.trade.price", TradeFeature.priceText(offer)).withStyle(ChatFormatting.YELLOW));
		lines.add(offer.outOfStock()
				? Component.translatable("cobblecomfort.trade.sold_out").withStyle(ChatFormatting.RED)
				: Component.translatable("cobblecomfort.trade.stock", offer.maxUses - offer.uses, offer.maxUses).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("cobblecomfort.storage.seen", TradeFeature.age(merchant)).withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}
}
