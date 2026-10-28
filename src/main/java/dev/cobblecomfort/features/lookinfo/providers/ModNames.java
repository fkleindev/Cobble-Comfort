package dev.cobblecomfort.features.lookinfo.providers;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/** Resolves a registry namespace to the display name of the mod that owns it. */
public final class ModNames {
	private static final Map<String, String> CACHE = new HashMap<>();

	private ModNames() {
	}

	public static Component of(String namespace, int color) {
		String name = CACHE.computeIfAbsent(namespace, ns -> {
			if (ns.equals("minecraft")) {
				return "Minecraft";
			}
			return FabricLoader.getInstance().getModContainer(ns)
					.map(ModContainer::getMetadata)
					.map(meta -> meta.getName())
					.orElse(ns);
		});
		return Component.literal(name).withStyle(ChatFormatting.ITALIC).withColor(color & 0xFFFFFF);
	}
}
