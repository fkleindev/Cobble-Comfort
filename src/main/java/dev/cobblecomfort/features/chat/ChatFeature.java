package dev.cobblecomfort.features.chat;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.mixin.ChatComponentAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat comfort: player heads in front of messages, timestamps, stacked repeats, highlighted mentions, a longer history
 * that survives server switches, a search field and right-click to copy. Messages are decorated once when they arrive;
 * the undecorated text is kept alongside for search, copy and stacking.
 */
public final class ChatFeature extends Feature<ChatConfig> {
	public static final ChatFeature INSTANCE = new ChatFeature();

	private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
	private static final String NAME_CHARS = "A-Za-z0-9_";
	private static final int MIN_NAME_LENGTH = 3;
	private static final int SEARCH_WIDTH = 140;
	private static final int SEARCH_HEIGHT = 14;

	/** What a decorated message was before decorating, keyed by identity (records compare by value). */
	private record Entry(String raw, UUID sender, String senderName, int count) {
	}

	private final Map<GuiMessage, Entry> entries = new MapMaker().weakKeys().makeMap();
	private GameProfile pendingSender;
	private String search = "";
	private int searchHits;
	private String mentionKey;
	private Pattern mentionPattern;

	private ChatFeature() {
		super("chat", ChatConfig.class, ChatConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return ChatConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- incoming messages

	/** The signed sender of the player message being shown right now (set around vanilla's showMessageToPlayer). */
	public void setPendingSender(GameProfile profile) {
		pendingSender = profile;
	}

	/**
	 * Replaces vanilla's addMessage: decorates the message, stacks it onto an identical previous one and adds it.
	 * Returns false to let vanilla handle it (feature off).
	 */
	public boolean addMessage(ChatComponent chat, Component content, MessageSignature signature, GuiMessageSource source,
			GuiMessageTag tag) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		int ticks = mc.gui.hud.getGuiTicks();
		GuiMessage plain = new GuiMessage(ticks, content, signature, source, tag);
		if (!access.cobblecomfort$visibleFilter().test(plain)) {
			return true;
		}
		String raw = content.getString();
		GameProfile sender = sender(source, raw);
		UUID senderId = sender == null ? null : sender.id();

		int count = 1;
		List<GuiMessage> history = access.cobblecomfort$allMessages();
		if (config.stackRepeats && !history.isEmpty()) {
			GuiMessage previous = history.getFirst();
			Entry before = entries.get(previous);
			if (before != null && before.raw.equals(raw) && Objects.equals(before.sender, senderId)) {
				count = before.count + 1;
				history.removeFirst();
				access.cobblecomfort$trimmedMessages().removeIf(line -> line.parent() == previous);
			}
		}

		Component body = content;
		boolean self = mc.player != null && mc.player.getUUID().equals(senderId);
		if (config.highlightMentions && !self && source != GuiMessageSource.SYSTEM_CLIENT) {
			Pattern pattern = mentionPattern(mc);
			if (pattern != null && pattern.matcher(raw).find()) {
				body = highlight(content, pattern, config.mentionColor);
				if (tag == null) {
					tag = new GuiMessageTag(config.mentionColor & 0xFFFFFF,
							null, Component.translatable("cobblecomfort.chat.mention"), null);
				}
				if (config.mentionSound && count == 1) {
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f, 0.6f));
				}
			}
		}

		MutableComponent line = Component.empty();
		if (config.timestamps) {
			LocalDateTime now = LocalDateTime.now();
			line.append(Component.literal((config.timestampSeconds ? SECONDS : MINUTES).format(now) + " ")
					.withStyle(Style.EMPTY.withColor(config.timestampColor & 0xFFFFFF)
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(FULL.format(now))))));
		}
		if (config.heads && sender != null) {
			line.append(Component.object(new PlayerSprite(profile(mc, sender), true))).append(" ");
		}
		line.append(body);
		if (count > 1) {
			line.append(Component.literal(" ×" + count).withStyle(ChatFormatting.GRAY));
		}

		GuiMessage message = new GuiMessage(ticks, line, signature, source, tag);
		entries.put(message, new Entry(raw, senderId, sender == null ? null : sender.name(), count));
		access.cobblecomfort$log(plain);
		if (matchesSearch(message)) {
			access.cobblecomfort$addToDisplay(message);
		}
		access.cobblecomfort$addToHistory(message);
		if (!search.isEmpty() && matchesSearch(message)) {
			searchHits++;
		}
		return true;
	}

	/** The signed sender, or (for plugin-formatted chat) the online player whose name comes first in the text. */
	private GameProfile sender(GuiMessageSource source, String raw) {
		GameProfile signed = pendingSender;
		pendingSender = null;
		if (signed != null && source == GuiMessageSource.PLAYER) {
			return signed;
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (!config.headNameSearch || source == GuiMessageSource.SYSTEM_CLIENT || connection == null) {
			return null;
		}
		GameProfile best = null;
		int bestIndex = Integer.MAX_VALUE;
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name == null || name.length() < MIN_NAME_LENGTH) {
				continue;
			}
			int index = wordIndex(raw, name);
			if (index >= 0 && (index < bestIndex || index == bestIndex && name.length() > best.name().length())) {
				best = info.getProfile();
				bestIndex = index;
			}
		}
		return best;
	}

	/** First occurrence of {@code word} in {@code text} that is not part of a longer name, or -1. */
	private static int wordIndex(String text, String word) {
		int from = 0;
		while (true) {
			int index = text.indexOf(word, from);
			if (index < 0) {
				return -1;
			}
			int end = index + word.length();
			if ((index == 0 || !isNameChar(text.charAt(index - 1))) && (end >= text.length() || !isNameChar(text.charAt(end)))) {
				return index;
			}
			from = index + 1;
		}
	}

	private static boolean isNameChar(char c) {
		return c == '_' || c < 128 && Character.isLetterOrDigit(c);
	}

	/** The tab list profile carries the skin textures; signed senders that are not listed fall back to their bare profile. */
	private static ResolvableProfile profile(Minecraft mc, GameProfile sender) {
		ClientPacketListener connection = mc.getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(sender.id());
		return ResolvableProfile.createResolved(info != null ? info.getProfile() : sender);
	}

	// ---------------------------------------------------------------- mentions

	private Pattern mentionPattern(Minecraft mc) {
		String own = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
		String key = own + "\n" + config.mentionKeywords;
		if (!key.equals(mentionKey)) {
			mentionKey = key;
			List<String> words = new ArrayList<>();
			words.add(own);
			for (String keyword : config.mentionKeywords.split(",")) {
				if (!keyword.isBlank()) {
					words.add(keyword.trim());
				}
			}
			StringBuilder alternatives = new StringBuilder();
			for (String word : words) {
				if (word == null || word.isEmpty()) {
					continue;
				}
				alternatives.append(alternatives.isEmpty() ? "" : "|").append(Pattern.quote(word));
			}
			mentionPattern = alternatives.isEmpty() ? null : Pattern.compile(
					"(?<![" + NAME_CHARS + "])(?:" + alternatives + ")(?![" + NAME_CHARS + "])",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		}
		return mentionPattern;
	}

	/** Rebuilds the message as flat styled text with every match recoloured and bold; unchanged if nothing matches. */
	private static Component highlight(Component content, Pattern pattern, int color) {
		MutableComponent out = Component.empty();
		boolean[] hit = {false};
		content.visit((style, text) -> {
			Matcher matcher = pattern.matcher(text);
			int last = 0;
			while (matcher.find()) {
				hit[0] = true;
				if (matcher.start() > last) {
					out.append(Component.literal(text.substring(last, matcher.start())).withStyle(style));
				}
				out.append(Component.literal(matcher.group()).withStyle(style.withColor(color & 0xFFFFFF).withBold(true)));
				last = matcher.end();
			}
			if (last < text.length()) {
				out.append(Component.literal(text.substring(last)).withStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return hit[0] ? out : content;
	}

	// ---------------------------------------------------------------- history

	/** Vanilla keeps 100 messages and 100 lines. */
	public int historyLength(int vanilla) {
		return isEnabled() ? Math.max(vanilla, config.historyLength) : vanilla;
	}

	public boolean keepOnDisconnect() {
		return isEnabled() && config.keepOnDisconnect;
	}

	/** A separator instead of clearing, so the old server's chat stays readable but distinct. */
	public void markDisconnect(ChatComponent chat) {
		chat.addClientSystemMessage(Component.translatable("cobblecomfort.chat.disconnected")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---------------------------------------------------------------- search

	public boolean searchEnabled() {
		return isEnabled() && config.search;
	}

	public boolean matchesSearch(GuiMessage message) {
		if (search.isEmpty()) {
			return true;
		}
		Entry entry = entries.get(message);
		String text = entry != null ? entry.raw : message.content().getString();
		if (entry != null && entry.senderName != null) {
			text = entry.senderName + " " + text;
		}
		return text.toLowerCase(Locale.ROOT).contains(search);
	}

	public void setSearch(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals(search)) {
			return;
		}
		search = normalized;
		ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		searchHits = 0;
		if (!search.isEmpty()) {
			for (GuiMessage message : access.cobblecomfort$allMessages()) {
				if (matchesSearch(message)) {
					searchHits++;
				}
			}
		}
		access.cobblecomfort$refresh();
		chat.resetChatScroll();
	}

	public String search() {
		return search;
	}

	public int searchHits() {
		return searchHits;
	}

	/** The search field above the chat input, at the right edge; null when search is off. */
	public EditBox createSearchBox(Font font, int screenWidth, int screenHeight) {
		if (!searchEnabled()) {
			return null;
		}
		EditBox box = new EditBox(font, screenWidth - 4 - SEARCH_WIDTH, screenHeight - 16 - SEARCH_HEIGHT,
				SEARCH_WIDTH, SEARCH_HEIGHT, Component.translatable("cobblecomfort.chat.search"));
		box.setMaxLength(64);
		box.setHint(Component.translatable("cobblecomfort.chat.search_hint").withStyle(ChatFormatting.DARK_GRAY));
		box.setValue(search);
		box.setResponder(this::setSearch);
		return box;
	}

	// ---------------------------------------------------------------- copy

	/** Copies the message under the mouse (in the open chat) to the clipboard. */
	public boolean copyAt(double mouseX, double mouseY) {
		if (!isEnabled() || !config.rightClickCopy) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		GuiMessage.Line line = lineAt(mc, mouseX, mouseY);
		if (line == null) {
			return false;
		}
		Entry entry = entries.get(line.parent());
		mc.keyboardHandler.setClipboard(entry != null ? entry.raw : line.parent().content().getString());
		mc.gui.hud.setOverlayMessage(Component.translatable("cobblecomfort.chat.copied"), false);
		return true;
	}

	/** The chat line under the mouse, using the same layout as vanilla's chat drawing. */
	private static GuiMessage.Line lineAt(Minecraft mc, double mouseX, double mouseY) {
		ChatComponent chat = mc.gui.hud.getChat();
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		float scale = (float) access.cobblecomfort$scale();
		int chatBottom = Mth.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
		int width = Mth.ceil(access.cobblecomfort$width() / scale);
		double x = mouseX / scale - 4;
		double y = mouseY / scale;
		if (x < -4 || x > width + 8 || y > chatBottom) {
			return null;
		}
		int index = Mth.floor((chatBottom - y) / access.cobblecomfort$lineHeight());
		List<GuiMessage.Line> lines = access.cobblecomfort$trimmedMessages();
		int scroll = access.cobblecomfort$scrollPos();
		int visible = Math.min(lines.size() - scroll, chat.getLinesPerPage());
		return index >= 0 && index < visible ? lines.get(index + scroll) : null;
	}
}
