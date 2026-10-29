package dev.cobblecomfort.features.chat;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat tabs: every message is sorted into one category (private, trade, system or chat) and into the custom tabs whose
 * keywords it contains. While the chat is open, tabs above the input show only one of them; the closed chat always
 * shows everything. Private messages, trade and custom tabs count what arrived unseen.
 */
final class ChatTabs {
	enum Category {
		CHAT, PRIVATE, TRADE, SYSTEM
	}

	/** Where a message belongs; {@code partner} is the other player of a private message (null if unknown). */
	record Sorting(Category category, int custom, String partner, boolean incoming) {
		static final Sorting CHAT = new Sorting(Category.CHAT, 0, null, false);
	}

	/** A tab: everything ({@code category} and {@code custom} unset), a category, or a custom tab by index. */
	private record Tab(String id, Component label, Category category, int custom) {
		boolean shows(Sorting sorting) {
			if (category != null) {
				return sorting.category() == category;
			}
			return custom < 0 || (sorting.custom() & (1 << custom)) != 0;
		}

		boolean counts() {
			return custom >= 0 || category == Category.PRIVATE || category == Category.TRADE;
		}
	}

	private record Custom(String name, List<String> keywords) {
	}

	private static final int MAX_CUSTOM = 8;
	private static final int MAX_DISGUISED = 32;
	static final int PRIVATE_COLOR = 0xFFA060FF;
	private static final long FLASH_MS = 1200;
	private static final long BUMP_MS = 350;
	private static final float MARKER_SPEED = 18f;
	private static final int TAB_HEIGHT = 12;
	private static final int TAB_GAP = 2;
	private static final int SCAN_CHARS = 40;
	private static final String SEPARATORS = ":>»|";

	private final ChatFeature feature;
	private final Map<String, Integer> unread = new HashMap<>();
	/** When each tab's count last went up, for the hop. */
	private final Map<String, Long> bumpedAt = new HashMap<>();
	/** When the last private message came in, for the violet blink. */
	private long privateAt;
	/** The gliding marker under the selected tab. */
	private float markerX0, markerX1;
	private long lastFrameNanos;
	private final List<int[]> bounds = new ArrayList<>();
	private String selected = "all";
	private boolean open;
	private ChatType.Bound pendingType;
	private final Map<String, ChatType.Bound> disguised = new HashMap<>();
	private String partner;

	private List<String> parsedCustomFrom;
	private List<Custom> custom = List.of();
	private List<String> parsedPatternsFrom;
	private List<Pattern> patterns = List.of();

	ChatTabs(ChatFeature feature) {
		this.feature = feature;
	}

	private ChatConfig config() {
		return feature.config();
	}

	// ---------------------------------------------------------------- sorting

	/** The chat type of the player message being shown right now (vanilla /msg has its own types). */
	void setPendingType(ChatType.Bound bound) {
		pendingType = bound;
	}

	/**
	 * The chat type of a disguised message (/msg from the console or a plugin). Vanilla may show it later, so it is
	 * looked up by its text.
	 */
	void addDisguised(Component decorated, ChatType.Bound bound) {
		if (disguised.size() >= MAX_DISGUISED) {
			disguised.clear();
		}
		disguised.put(decorated.getString(), bound);
	}

	Sorting sort(GuiMessageSource source, Component content, String raw) {
		ChatType.Bound bound = pendingType != null ? pendingType : disguised.remove(raw);
		pendingType = null;
		int customMask = customMask(raw);
		if (bound != null && source != GuiMessageSource.SYSTEM_CLIENT) {
			if (bound.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
				return new Sorting(Category.PRIVATE, customMask, bound.name().getString(), true);
			}
			if (bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
				return new Sorting(Category.PRIVATE, customMask, bound.targetName().map(Component::getString).orElse(null), false);
			}
		}
		if (source != GuiMessageSource.SYSTEM_CLIENT) {
			for (Pattern pattern : patterns()) {
				Matcher matcher = pattern.matcher(raw);
				if (matcher.find()) {
					String from = group(pattern, matcher, "from");
					return new Sorting(Category.PRIVATE, customMask, from != null ? from : group(pattern, matcher, "to"), from != null);
				}
			}
			for (String keyword : config().tradeKeywords.split(",")) {
				if (contains(raw, keyword.trim())) {
					return new Sorting(Category.TRADE, customMask, null, false);
				}
			}
		}
		return new Sorting(system(source, content, raw) ? Category.SYSTEM : Category.CHAT, customMask, null, false);
	}

	/**
	 * Server messages count as system messages unless they look like chat: a player's name near the start followed by
	 * ':', '>', '»' or '|' (as plugins format chat). Translated vanilla messages (joins, deaths, advancements) are
	 * system messages, vanilla chat formats aside.
	 */
	private static boolean system(GuiMessageSource source, Component content, String raw) {
		if (source == GuiMessageSource.PLAYER) {
			return false;
		}
		if (source == GuiMessageSource.SYSTEM_CLIENT) {
			return true;
		}
		if (content.getContents() instanceof TranslatableContents translatable) {
			return !translatable.getKey().startsWith("chat.type.");
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return true;
		}
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name == null || name.isEmpty()) {
				continue;
			}
			int index = ChatFeature.wordIndex(raw, name);
			if (index < 0 || index > SCAN_CHARS) {
				continue;
			}
			int i = index + name.length();
			while (i < raw.length() && (raw.charAt(i) == ']' || raw.charAt(i) == ')' || raw.charAt(i) == ' ')) {
				i++;
			}
			if (i < raw.length() && SEPARATORS.indexOf(raw.charAt(i)) >= 0) {
				return false;
			}
		}
		return true;
	}

	private static String group(Pattern pattern, Matcher matcher, String name) {
		return pattern.namedGroups().containsKey(name) ? matcher.group(name) : null;
	}

	private int customMask(String raw) {
		List<Custom> tabs = customTabs();
		int mask = 0;
		for (int i = 0; i < tabs.size(); i++) {
			for (String keyword : tabs.get(i).keywords()) {
				if (contains(raw, keyword)) {
					mask |= 1 << i;
					break;
				}
			}
		}
		return mask;
	}

	/** Case-insensitive; keywords of letters and digits only match whole words, others anywhere ("[Trade]"). */
	private static boolean contains(String text, String keyword) {
		if (keyword.isEmpty()) {
			return false;
		}
		String lowerText = text.toLowerCase(Locale.ROOT);
		String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
		if (!lowerKeyword.chars().allMatch(Character::isLetterOrDigit)) {
			return lowerText.contains(lowerKeyword);
		}
		int from = 0;
		while (true) {
			int index = lowerText.indexOf(lowerKeyword, from);
			if (index < 0) {
				return false;
			}
			int end = index + lowerKeyword.length();
			if ((index == 0 || !Character.isLetterOrDigit(lowerText.charAt(index - 1)))
					&& (end >= lowerText.length() || !Character.isLetterOrDigit(lowerText.charAt(end)))) {
				return true;
			}
			from = index + 1;
		}
	}

	/** "Name: keyword, keyword" per entry; without a colon the name is the keyword. */
	private List<Custom> customTabs() {
		List<String> source = config().customTabs;
		if (!source.equals(parsedCustomFrom)) {
			parsedCustomFrom = List.copyOf(source);
			List<Custom> list = new ArrayList<>();
			for (String entry : source) {
				int colon = entry.indexOf(':');
				String name = (colon < 0 ? entry : entry.substring(0, colon)).trim();
				List<String> keywords = new ArrayList<>();
				for (String keyword : (colon < 0 ? entry : entry.substring(colon + 1)).split(",")) {
					if (!keyword.isBlank()) {
						keywords.add(keyword.trim());
					}
				}
				if (!name.isEmpty() && !keywords.isEmpty() && list.size() < MAX_CUSTOM) {
					list.add(new Custom(name, keywords));
				}
			}
			custom = list;
		}
		return custom;
	}

	/** The private message patterns; broken ones are skipped. */
	private List<Pattern> patterns() {
		List<String> source = config().privatePatterns;
		if (!source.equals(parsedPatternsFrom)) {
			parsedPatternsFrom = List.copyOf(source);
			List<Pattern> list = new ArrayList<>();
			for (String regex : source) {
				try {
					list.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
				} catch (PatternSyntaxException ignored) {
					// Typed into the settings; a broken pattern just does nothing.
				}
			}
			patterns = list;
		}
		return patterns;
	}

	// ---------------------------------------------------------------- tabs

	private List<Tab> tabs() {
		List<Tab> tabs = new ArrayList<>();
		tabs.add(new Tab("all", Component.translatable("cobblecomfort.chat.tab.all"), null, -1));
		for (Category category : Category.values()) {
			String key = category.name().toLowerCase(Locale.ROOT);
			tabs.add(new Tab(key, Component.translatable("cobblecomfort.chat.tab." + key), category, -1));
		}
		List<Custom> customs = customTabs();
		for (int i = 0; i < customs.size(); i++) {
			tabs.add(new Tab("custom:" + customs.get(i).name(), Component.literal(customs.get(i).name()), null, i));
		}
		return tabs;
	}

	String selected() {
		return selected;
	}

	private Tab selectedTab() {
		for (Tab tab : tabs()) {
			if (tab.id().equals(selected)) {
				return tab;
			}
		}
		selected = "all";
		return tabs().getFirst();
	}

	/** Whether a message shows in the chat right now: all of them while closed, the selected tab's while open. */
	boolean shows(Sorting sorting) {
		return !open || !config().tabs || selectedTab().shows(sorting == null ? Sorting.CHAT : sorting);
	}

	/** Counts a new message for the tabs it is not seen in, plays the private message sound and notes the partner. */
	void onAdded(Sorting sorting, boolean repeat) {
		if (sorting.category() == Category.PRIVATE && sorting.partner() != null) {
			partner = sorting.partner();
			Minecraft mc = Minecraft.getInstance();
			if (sorting.incoming() && !repeat && config().privateSound) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.7f));
			}
		}
		if (repeat || !config().tabs) {
			return;
		}
		long now = System.currentTimeMillis();
		if (sorting.category() == Category.PRIVATE && sorting.incoming()) {
			privateAt = now;
		}
		for (Tab tab : tabs()) {
			if (tab.counts() && tab.shows(sorting) && !(open && tab.id().equals(selected))) {
				unread.merge(tab.id(), 1, Integer::sum);
				bumpedAt.put(tab.id(), now);
			}
		}
	}

	/** The chat screen opened or closed: the filter only applies while it is open. */
	void setOpen(boolean open) {
		this.open = open;
		if (!selected.equals("all")) {
			feature.refresh();
		}
		if (open) {
			unread.remove(selected);
		}
	}

	/**
	 * In the private tab, plain text goes to the last partner as /msg. Returns the message to send.
	 */
	String rewriteInput(String message) {
		if (open && config().tabs && config().privateReply && partner != null && selected.equals("private")
				&& !message.isBlank() && !message.startsWith("/")) {
			return "/msg " + partner + " " + message;
		}
		return message;
	}

	/** Draws the tabs left of {@code right} in the row above the chat input. */
	void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenHeight, int right) {
		bounds.clear();
		if (!config().tabs) {
			return;
		}
		long now = System.currentTimeMillis();
		long nanos = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 1f : Math.min((nanos - lastFrameNanos) / 1e9f, 0.1f);
		lastFrameNanos = nanos;
		int x = 2;
		int y = screenHeight - 29;
		List<Tab> tabs = tabs();
		// Lay out and draw the backgrounds first, so the gliding marker can go over them and under the text.
		int activeX0 = -1;
		int activeX1 = -1;
		for (int i = 0; i < tabs.size(); i++) {
			Tab tab = tabs.get(i);
			int count = unread.getOrDefault(tab.id(), 0);
			int width = font.width(tab.label()) + (count > 0 ? font.width(countText(count)) + 3 : 0) + 6;
			if (x + width > right) {
				break;
			}
			boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
			RenderUtil.roundedFill(g, x, y, x + width, y + TAB_HEIGHT, 2, hover ? 0xC0282828 : 0xA0000000);
			float flash = tab.id().equals("private") ? privateFlash(now) : 0f;
			if (flash > 0f) {
				RenderUtil.roundedFill(g, x, y, x + width, y + TAB_HEIGHT, 2, RenderUtil.withAlpha(PRIVATE_COLOR, 0.75f * flash));
			}
			if (tab.id().equals(selected)) {
				activeX0 = x;
				activeX1 = x + width;
			}
			bounds.add(new int[]{x, y, x + width, y + TAB_HEIGHT, i});
			x += width + TAB_GAP;
		}
		if (activeX0 >= 0) {
			if (markerX1 <= markerX0) {
				markerX0 = activeX0;
				markerX1 = activeX1;
			}
			float follow = 1f - (float) Math.exp(-MARKER_SPEED * dt);
			markerX0 += (activeX0 - markerX0) * follow;
			markerX1 += (activeX1 - markerX1) * follow;
			int m0 = Math.round(markerX0);
			int m1 = Math.round(markerX1);
			RenderUtil.roundedFill(g, m0, y, m1, y + TAB_HEIGHT, 2, 0xE0303A4C);
			g.fill(m0 + 2, y + TAB_HEIGHT - 1, m1 - 2, y + TAB_HEIGHT, 0xFF7FB2FF);
		}
		for (int[] box : bounds) {
			Tab tab = tabs.get(box[4]);
			boolean active = tab.id().equals(selected);
			boolean hover = mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3];
			g.text(font, tab.label(), box[0] + 3, y + 2, active ? 0xFFFFFFFF : hover ? 0xFFE0E0E0 : 0xFFA0A0A0, true);
			int count = unread.getOrDefault(tab.id(), 0);
			if (count > 0) {
				// A new message makes the count hop.
				float t = (now - bumpedAt.getOrDefault(tab.id(), 0L)) / (float) BUMP_MS;
				float hop = t >= 0f && t < 1f ? -3f * (float) Math.sin(Math.PI * t) : 0f;
				g.pose().pushMatrix();
				g.pose().translate(0, hop);
				g.text(font, Component.literal(countText(count)).withStyle(ChatFormatting.YELLOW),
						box[0] + 3 + font.width(tab.label()) + 3, y + 2, 0xFFFFFFFF, true);
				g.pose().popMatrix();
			}
		}
		if (selected.equals("private") && config().privateReply && partner != null) {
			Component to = Component.translatable("cobblecomfort.chat.reply_to", partner);
			if (x + 4 + font.width(to) <= right) {
				g.text(font, to, x + 4, y + 2, 0xFFC890FF, true);
			}
		}
	}

	private static String countText(int count) {
		return count > 99 ? "99+" : String.valueOf(count);
	}

	/** How strongly the private tab and a new private message blink violet right now (0 to 1): twice, fading. */
	float privateFlash(long now) {
		return flash(now - privateAt);
	}

	static float flash(long age) {
		float t = age / (float) FLASH_MS;
		if (t < 0f || t >= 1f) {
			return 0f;
		}
		float wave = (float) Math.sin(Math.PI * 2 * t);
		return wave * wave * (1f - t);
	}

	/** Selects the tab under the mouse; returns whether one was hit. */
	boolean click(double mouseX, double mouseY) {
		List<Tab> tabs = tabs();
		for (int[] box : bounds) {
			if (mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3] && box[4] < tabs.size()) {
				select(tabs.get(box[4]).id());
				return true;
			}
		}
		return false;
	}

	private void select(String id) {
		if (id.equals(selected)) {
			return;
		}
		selected = id;
		unread.remove(id);
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.25f));
		feature.refresh();
	}
}
