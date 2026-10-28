package dev.cobblecomfort.features.map.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.features.map.data.MapRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** GPU textures of map regions for the full map, rebuilt when a region changes (a few per frame at most). */
public final class RegionTextures {
	private static final int MAX_BAKES_PER_FRAME = 3;
	private static final long UNUSED_MS = 20_000L;

	private static final class Entry {
		final Identifier id;
		final DynamicTexture texture;
		int version = -1;
		boolean shading;
		long lastUsedMs;

		Entry(Identifier id, DynamicTexture texture) {
			this.id = id;
			this.texture = texture;
		}
	}

	private final Map<MapRegion, Entry> entries = new IdentityHashMap<>();
	private final String name;
	private int counter;
	private int bakesThisFrame;

	public RegionTextures(String name) {
		this.name = name;
	}

	public void beginFrame() {
		bakesThisFrame = 0;
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

	/** The texture for a region, (re)baked if it changed; null if it has never been baked and the budget is used up. */
	public Identifier texture(MapRegion region, boolean shading) {
		Entry entry = entries.get(region);
		if (entry == null) {
			if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
				return null;
			}
			Identifier id = CobbleComfortClient.id("map/" + name + "_" + counter++);
			DynamicTexture texture = new DynamicTexture(() -> "Cobble Comfort map region", MapRegion.SIZE, MapRegion.SIZE, false);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			entry = new Entry(id, texture);
			entries.put(region, entry);
		}
		entry.lastUsedMs = System.currentTimeMillis();
		if ((entry.version != region.version() || entry.shading != shading) && bakesThisFrame < MAX_BAKES_PER_FRAME) {
			bake(region, entry, shading);
			bakesThisFrame++;
		}
		return entry.version == -1 ? null : entry.id;
	}

	private static void bake(MapRegion region, Entry entry, boolean shading) {
		NativeImage image = entry.texture.getPixels();
		if (image == null) {
			return;
		}
		for (int z = 0; z < MapRegion.SIZE; z++) {
			for (int x = 0; x < MapRegion.SIZE; x++) {
				image.setPixel(x, z, region.shadedColor(x, z, shading));
			}
		}
		entry.texture.upload();
		entry.version = region.version();
		entry.shading = shading;
	}

	public void clear() {
		for (Entry entry : entries.values()) {
			Minecraft.getInstance().getTextureManager().release(entry.id);
		}
		entries.clear();
	}
}
