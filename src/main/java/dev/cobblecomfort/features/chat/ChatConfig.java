package dev.cobblecomfort.features.chat;

import dev.cobblecomfort.core.FeatureConfig;

import java.util.ArrayList;
import java.util.List;

public class ChatConfig extends FeatureConfig {
	// Chat heads
	public boolean heads = true;
	public boolean headNameSearch = true;

	// Timestamps and stacking
	public boolean timestamps = true;
	public boolean timestampSeconds = false;
	public int timestampColor = 0xFF808080;
	public boolean stackRepeats = true;

	// Mentions
	public boolean highlightMentions = true;
	public String mentionKeywords = "";
	public int mentionColor = 0xFFFFD35C;
	public boolean mentionSound = true;

	// Tabs
	public boolean tabs = true;
	/** Comma-separated; letters-only keywords match whole words, others anywhere. */
	public String tradeKeywords = "[Handel], [Trade], [Market], [Markt], WTS, WTB, WTT, verkaufe, kaufe, biete, tausche, selling, buying";
	/** Plugin private message formats; the group "from" (incoming) or "to" (outgoing) is the other player. */
	public List<String> privatePatterns = new ArrayList<>(List.of(
			"^\\[?(?<from>\\w{3,16}) ?(?:->|→|») ?(?:me|mir|ich|you|dir)\\]?",
			"^\\[?(?:me|mir|ich|you|du) ?(?:->|→|») ?(?<to>\\w{3,16})\\]?",
			"^(?<from>\\w{3,16}) (?:whispers to you|flüstert dir)",
			"^(?:you whisper to|du flüsterst) (?<to>\\w{3,16})"));
	/** Own tabs as "Name: keyword, keyword". */
	public List<String> customTabs = new ArrayList<>();
	public boolean privateReply = true;
	public boolean privateSound = true;

	// History
	public int historyLength = 500;
	public boolean keepOnDisconnect = true;
	public boolean search = true;
	public boolean rightClickCopy = true;
}
