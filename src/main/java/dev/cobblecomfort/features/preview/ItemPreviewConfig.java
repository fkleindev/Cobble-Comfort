package dev.cobblecomfort.features.preview;

import dev.cobblecomfort.core.FeatureConfig;
import dev.cobblecomfort.features.shulker.PreviewKey;

public class ItemPreviewConfig extends FeatureConfig {
	// General
	public boolean compactByDefault = true;
	public PreviewKey largeKey = PreviewKey.SHIFT;
	public boolean keyHint = true;

	// Maps
	public boolean maps = true;
	public boolean mapMarkers = true;
	public boolean mapInfo = true;

	// Books
	public boolean writtenBooks = true;
	public boolean writableBooks = true;
	public boolean scrollPages = true;
	public boolean enchantedBooks = true;
}
