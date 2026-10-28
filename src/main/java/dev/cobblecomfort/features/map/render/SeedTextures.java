package dev.cobblecomfort.features.map.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.features.map.data.BlockColorCache;
import dev.cobblecomfort.features.map.seed.SeedTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Small darkened textures of calculated (unexplored) terrain for the full map. */
public final class SeedTextures {
	private static final int MAX_BAKES_PER_FRAME = 6;
	private static final long UNUSED_MS = 20_000L;

	private static final class Entry {
		final Identifier id;
		final DynamicTexture texture;
		float brightness = -1f;
		long lastUsedMs;

		Entry(Identifier id, DynamicTexture texture) {
			this.id = id;
			this.texture = texture;
		}
	}

	private final Map<SeedTerrain.Region, Entry> entries = new IdentityHashMap<>();
	private int counter;
	private int bakes;

	public void beginFrame() {
		bakes = 0;
		long now = System.currentTimeMillis();
		Iterator<Entry> it = entries.values().iterator();
		while (it.hasNext()) {
			Entry entry = it.next();
			if (now - entry.lastUsedMs > UNUSED_MS) {
				Minecraft.getInstance().getTextureManager().release(entry.id);
				it.remove();
			}
		}
	}

	public Identifier texture(SeedTerrain.Region region, float brightness) {
		Entry entry = entries.get(region);
		if (entry == null) {
			if (bakes >= MAX_BAKES_PER_FRAME) {
				return null;
			}
			Identifier id = CobbleComfortClient.id("map/seed_" + counter++);
			DynamicTexture texture = new DynamicTexture(() -> "Cobble Comfort seed terrain", SeedTerrain.SAMPLES, SeedTerrain.SAMPLES, false);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			entry = new Entry(id, texture);
			entries.put(region, entry);
		}
		entry.lastUsedMs = System.currentTimeMillis();
		if (entry.brightness != brightness && bakes < MAX_BAKES_PER_FRAME) {
			NativeImage image = entry.texture.getPixels();
			if (image != null) {
				for (int z = 0; z < SeedTerrain.SAMPLES; z++) {
					for (int x = 0; x < SeedTerrain.SAMPLES; x++) {
						int color = region.color(x, z);
						image.setPixel(x, z, color == 0 ? 0 : BlockColorCache.scale(color, brightness));
					}
				}
				entry.texture.upload();
				entry.brightness = brightness;
				bakes++;
			}
		}
		return entry.brightness < 0 ? null : entry.id;
	}

	public void clear() {
		for (Entry entry : entries.values()) {
			Minecraft.getInstance().getTextureManager().release(entry.id);
		}
		entries.clear();
	}
}
