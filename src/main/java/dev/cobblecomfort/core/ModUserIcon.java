package dev.cobblecomfort.core;

import dev.cobblecomfort.features.petplush.PetPlushFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * The little Cobble Comfort block put in front of the name of every player with the mod (chat, player list, name tag).
 * Others are known through the Cobble Comfort Sync plugin, which lists everyone whose client speaks the plush channel;
 * without the plugin only the own name gets it.
 */
public final class ModUserIcon {
	private static final Identifier SPRITE = Identifier.fromNamespaceAndPath("cobblecomfort", "icon/mod_user");

	private ModUserIcon() {
	}

	public static boolean hasMod(UUID player) {
		if (player == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null ? player.equals(mc.player.getUUID()) : player.equals(mc.getUser().getProfileId())) {
			return true;
		}
		return PetPlushFeature.INSTANCE.hasMod(player);
	}

	public static boolean shows(UUID player) {
		return FeatureManager.general().enabled && FeatureManager.general().modUserIcon && hasMod(player);
	}

	/** The name with the icon in front if this player has the mod, else the name as it is. */
	public static Component decorate(UUID player, Component name) {
		if (name == null || !shows(player)) {
			return name;
		}
		return Component.empty().append(icon()).append(" ").append(name);
	}

	/** The icon as text; white so no name colour tints it, without shadow, and silent for the narrator. */
	public static MutableComponent icon() {
		return Component.object(new AtlasSprite(AtlasIds.GUI, SPRITE), Component.empty())
				.setStyle(Style.EMPTY.withColor(0xFFFFFF).withoutShadow());
	}
}
