package dev.cobblecomfort.features.chat;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.network.chat.Component;

public final class ChatConfigUi {
	private ChatConfigUi() {
	}

	public static ConfigCategory buildCategory(ChatFeature feature) {
		ChatConfig d = new ChatConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("chat.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.chat.heads"))
						.option(Opts.bool("chat.heads", d.heads, () -> c(feature).heads, v -> c(feature).heads = v))
						.option(Opts.bool("chat.head_name_search", d.headNameSearch,
								() -> c(feature).headNameSearch, v -> c(feature).headNameSearch = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.chat.lines"))
						.option(Opts.bool("chat.timestamps", d.timestamps, () -> c(feature).timestamps, v -> c(feature).timestamps = v))
						.option(Opts.bool("chat.timestamp_seconds", d.timestampSeconds,
								() -> c(feature).timestampSeconds, v -> c(feature).timestampSeconds = v))
						.option(Opts.color("chat.timestamp_color", d.timestampColor, false,
								() -> c(feature).timestampColor, v -> c(feature).timestampColor = v | 0xFF000000))
						.option(Opts.bool("chat.stack_repeats", d.stackRepeats,
								() -> c(feature).stackRepeats, v -> c(feature).stackRepeats = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.chat.mentions"))
						.option(Opts.bool("chat.highlight_mentions", d.highlightMentions,
								() -> c(feature).highlightMentions, v -> c(feature).highlightMentions = v))
						.option(Option.<String>createBuilder()
								.name(Opts.name("chat.mention_keywords"))
								.description(Opts.desc("chat.mention_keywords"))
								.binding(d.mentionKeywords, () -> c(feature).mentionKeywords, v -> c(feature).mentionKeywords = v)
								.controller(StringControllerBuilder::create)
								.build())
						.option(Opts.color("chat.mention_color", d.mentionColor, false,
								() -> c(feature).mentionColor, v -> c(feature).mentionColor = v | 0xFF000000))
						.option(Opts.bool("chat.mention_sound", d.mentionSound,
								() -> c(feature).mentionSound, v -> c(feature).mentionSound = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.chat.history"))
						.option(Opts.intSlider("chat.history_length", d.historyLength, 100, 2000, 100,
								() -> c(feature).historyLength, v -> c(feature).historyLength = v))
						.option(Opts.bool("chat.keep_on_disconnect", d.keepOnDisconnect,
								() -> c(feature).keepOnDisconnect, v -> c(feature).keepOnDisconnect = v))
						.option(Opts.bool("chat.search", d.search, () -> c(feature).search, v -> c(feature).search = v))
						.option(Opts.bool("chat.right_click_copy", d.rightClickCopy,
								() -> c(feature).rightClickCopy, v -> c(feature).rightClickCopy = v))
						.build())
				.build();
	}

	private static ChatConfig c(ChatFeature feature) {
		return feature.config();
	}
}
