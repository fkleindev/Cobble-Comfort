package dev.cobblecomfort.features.lookinfo.providers;

import dev.cobblecomfort.features.lookinfo.HealthDisplay;
import dev.cobblecomfort.features.lookinfo.InfoLine;
import dev.cobblecomfort.features.lookinfo.LookInfoConfig;
import dev.cobblecomfort.features.lookinfo.LookTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EntityInfoProvider {
	private static final int HEALTH_COLOR = 0xFFFF5555;
	/** Converts the movement speed attribute to blocks per second. */
	private static final double SPEED_TO_BLOCKS_PER_SECOND = 42.16;

	private EntityInfoProvider() {
	}

	public static LookTarget entity(LookInfoConfig cfg, Entity entity) {
		ItemStack icon = icon(entity);
		Component name = entity.getDisplayName();
		List<InfoLine> lines = new ArrayList<>();

		if (entity instanceof ItemEntity item) {
			name = item.getItem().getHoverName();
			if (cfg.showEntityDetails && item.getItem().getCount() > 1) {
				lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.count", item.getItem().getCount())));
			}
		}

		if (entity instanceof LivingEntity living) {
			addHealth(cfg, living, lines);
			if (cfg.showArmor && living.getArmorValue() > 0) {
				lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.armor", living.getArmorValue())));
			}
			if (cfg.showEntityDetails) {
				addDetails(living, lines);
			}
			if (cfg.showHorseStats && living instanceof AbstractHorse horse) {
				addHorseStats(horse, lines);
			}
		}

		String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
		return new LookTarget(entity.getId(), icon, name, ModNames.of(namespace, cfg.modColor), lines);
	}

	private static ItemStack icon(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return item.getItem();
		}
		if (entity instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
			return frame.getItem();
		}
		ItemStack egg = SpawnEggItem.byId(entity.getType()).map(ItemStack::new).orElse(null);
		if (egg != null) {
			return egg;
		}
		ItemStack pick = entity.getPickResult();
		return pick != null ? pick : ItemStack.EMPTY;
	}

	private static void addHealth(LookInfoConfig cfg, LivingEntity living, List<InfoLine> lines) {
		float health = living.getHealth();
		float max = living.getMaxHealth();
		HealthDisplay mode = cfg.healthDisplay;
		if (mode == HealthDisplay.HEARTS && Math.ceil(max / 2f) > cfg.maxHearts) {
			mode = HealthDisplay.NUMBER;
		}
		Component text = Component.literal("❤ " + format(health) + " / " + format(max)).withColor(HEALTH_COLOR & 0xFFFFFF);
		switch (mode) {
			case HEARTS -> lines.add(new InfoLine.Hearts(health, max));
			case BAR -> lines.add(new InfoLine.Bar(text, max > 0 ? health / max : 0f, HEALTH_COLOR));
			case NUMBER -> lines.add(new InfoLine.Text(text));
			case OFF -> {
			}
		}
	}

	private static void addDetails(LivingEntity living, List<InfoLine> lines) {
		List<Component> tags = new ArrayList<>();
		if (living.isBaby()) {
			tags.add(Component.translatable("cobblecomfort.look.baby"));
		}
		boolean tamed = living instanceof TamableAnimal tamable && tamable.isTame()
				|| living instanceof AbstractHorse horse && horse.isTamed();
		if (tamed) {
			LivingEntity owner = living instanceof TamableAnimal tamable ? tamable.getOwner()
					: ((AbstractHorse) living).getOwner();
			tags.add(owner != null
					? Component.translatable("cobblecomfort.look.tamed_by", owner.getName())
					: Component.translatable("cobblecomfort.look.tamed"));
		}
		if (!tags.isEmpty()) {
			MutableComponent joined = Component.empty();
			for (int i = 0; i < tags.size(); i++) {
				if (i > 0) {
					joined.append(" · ");
				}
				joined.append(tags.get(i));
			}
			lines.add(new InfoLine.Text(joined));
		}
		if (living instanceof Villager villager) {
			var data = villager.getVillagerData();
			lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.villager",
					data.profession().value().name(), data.level()).withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void addHorseStats(AbstractHorse horse, List<InfoLine> lines) {
		lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.horse_speed",
				String.format(Locale.ROOT, "%.1f", horseSpeed(horse)))));
		lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.horse_jump",
				String.format(Locale.ROOT, "%.1f", horseJump(horse)))));
	}

	/** Top speed in blocks per second. */
	public static double horseSpeed(AbstractHorse horse) {
		return horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * SPEED_TO_BLOCKS_PER_SECOND;
	}

	/** Jump height in blocks. */
	public static double horseJump(AbstractHorse horse) {
		double jump = horse.getAttributeValue(Attributes.JUMP_STRENGTH);
		// Empirical fit of jump strength to jump height in blocks.
		return -0.1817584952 * jump * jump * jump + 3.689713992 * jump * jump + 2.128599134 * jump - 0.343930367;
	}

	private static String format(float value) {
		return value == Math.floor(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}
}
