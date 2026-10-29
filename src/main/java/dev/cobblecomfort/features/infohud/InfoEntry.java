package dev.cobblecomfort.features.infohud;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** One line of the info HUD, with the item shown as its icon. */
public enum InfoEntry implements NameableEnum {
	COORDS(Items.COMPASS),
	DIRECTION(Items.RECOVERY_COMPASS),
	BIOME(Items.OAK_SAPLING),
	LIGHT(Items.TORCH),
	TIME(Items.CLOCK),
	WEATHER(Items.WATER_BUCKET),
	FPS(Items.COMPARATOR),
	PING(Items.ENDER_PEARL),
	CHUNK(Items.MAP),
	SPEED(Items.FEATHER),
	REAL_TIME(Items.CLOCK),
	TARGET(Items.SPYGLASS),
	REST(Items.PHANTOM_MEMBRANE);

	private final Item icon;

	InfoEntry(Item icon) {
		this.icon = icon;
	}

	public Item icon() {
		return icon;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.info_entry." + name().toLowerCase(Locale.ROOT));
	}
}
