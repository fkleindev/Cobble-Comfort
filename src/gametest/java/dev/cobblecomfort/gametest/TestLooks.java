package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.petplush.PlushLook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What other (made-up) players share in a test, as if told by the Sync plugin. */
public final class TestLooks {
	public static final Map<UUID, PlushLook> LOOKS = new ConcurrentHashMap<>();

	private TestLooks() {
	}
}
