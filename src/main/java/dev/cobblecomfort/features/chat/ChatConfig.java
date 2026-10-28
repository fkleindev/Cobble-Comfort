package dev.cobblecomfort.features.chat;

import dev.cobblecomfort.core.FeatureConfig;

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

	// History
	public int historyLength = 500;
	public boolean keepOnDisconnect = true;
	public boolean search = true;
	public boolean rightClickCopy = true;
}
