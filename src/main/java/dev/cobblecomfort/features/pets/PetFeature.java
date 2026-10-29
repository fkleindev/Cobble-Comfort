package dev.cobblecomfort.features.pets;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.lookinfo.providers.EntityInfoProvider;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.storage.StorageItems;
import dev.cobblecomfort.features.tracker.TrackerStorage;
import dev.cobblecomfort.features.tracker.TrackerToast;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Remembers the player's pets and mounts with their name and last known position, for the map and the "Pets" tab of
 * the storage memory. Wolves, cats, parrots and nautiluses carry their owner, so only the player's own are taken; for
 * horses, llamas, camels and happy ghasts the client does not know the owner, so they count once the player rode
 * them or opened their inventory. A pet that dies (seen, or reported by the server's death message) leaves a grave.
 */
public final class PetFeature extends Feature<PetConfig> {
	public static final PetFeature INSTANCE = new PetFeature();

	private static final int SCAN_TICKS = 10;
	private static final int SAVE_TICKS = 600;
	/** Updates closer than this (blocks) do not count as a change worth saving. */
	private static final double MOVE = 1.0;
	public static final int ALIVE_COLOR = 0xFFFF8FB8;
	public static final int DEAD_COLOR = 0xFF7A7A7A;
	private static final int NAME_COLOR = 0xFFFFD27A;

	private enum Kind {
		COMPANION, MOUNT, OTHER
	}

	private final PetStore store = new PetStore();
	/** UUIDs of remembered pets that are loaded right now. */
	private final Set<String> nearby = new HashSet<>();
	private final Map<String, ItemStack> icons = new HashMap<>();
	private int ticks;

	private PetFeature() {
		super("pets", PetConfig.class, PetConfig::new);
	}

	@Override
	public void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> store.load(TrackerStorage.currentWorldKey()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			store.unload();
			nearby.clear();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof AbstractMountInventoryScreen<?>) {
				onMountInventory(mc);
			}
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message);
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return PetConfigUi.buildCategory(this);
	}

	public PetStore store() {
		return store;
	}

	public boolean active() {
		return isEnabled() && store.loaded();
	}

	// ---------------------------------------------------------------- which entities

	private static Kind kind(Entity entity) {
		if (entity instanceof Wolf || entity instanceof Cat || entity instanceof Parrot) {
			return Kind.COMPANION;
		}
		if (entity instanceof AbstractHorse) {
			return Kind.MOUNT;
		}
		return entity instanceof AbstractNautilus || entity instanceof HappyGhast ? Kind.OTHER : null;
	}

	private boolean wanted(Kind kind) {
		return switch (kind) {
			case COMPANION -> config.companions;
			case MOUNT -> config.mounts;
			case OTHER -> config.others;
		};
	}

	/** Tamed by the player (for animals that know their owner). */
	private static boolean ownedBy(TamableAnimal animal, LocalPlayer player) {
		EntityReference<LivingEntity> owner = animal.getOwnerReference();
		return animal.isTame() && owner != null && owner.getUUID().equals(player.getUUID());
	}

	/** Whether an entity not remembered yet becomes the player's: tamed by them, or ridden (mounts without owner info). */
	private static boolean claims(Entity entity, LocalPlayer player) {
		if (entity instanceof TamableAnimal animal) {
			return ownedBy(animal, player);
		}
		return player.getVehicle() == entity && (!(entity instanceof AbstractHorse horse) || horse.isTamed());
	}

	/** Opening a mount's inventory (riding it, or sneak-clicking it) makes it the player's. */
	private void onMountInventory(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!active() || player == null || mc.level == null) {
			return;
		}
		Entity entity = player.getVehicle() != null ? player.getVehicle() : mc.crosshairPickEntity;
		Kind kind = entity == null ? null : kind(entity);
		if (kind == null || !wanted(kind) || entity instanceof TamableAnimal animal && !ownedBy(animal, player)) {
			return;
		}
		if (entity instanceof AbstractHorse horse && !horse.isTamed()) {
			return;
		}
		remember((LivingEntity) entity, MapFeature.dimensionId(mc.level));
	}

	// ---------------------------------------------------------------- following

	private void tick(Minecraft mc) {
		if (!active() || mc.player == null || mc.level == null) {
			return;
		}
		ticks++;
		if (ticks % SCAN_TICKS == 0) {
			scan(mc.level, mc.player);
		}
		if (ticks % SAVE_TICKS == 0) {
			store.save();
		}
	}

	private void scan(ClientLevel level, LocalPlayer player) {
		String dim = MapFeature.dimensionId(level);
		nearby.clear();
		for (Entity entity : level.entitiesForRendering()) {
			Kind kind = kind(entity);
			if (kind == null || !wanted(kind)) {
				continue;
			}
			String uuid = entity.getStringUUID();
			PetEntry pet = store.byUuid(uuid);
			if (pet != null && entity instanceof TamableAnimal animal && !ownedBy(animal, player)) {
				// Given away or set free.
				store.remove(pet);
				continue;
			}
			if (pet == null && !claims(entity, player)) {
				continue;
			}
			LivingEntity living = (LivingEntity) entity;
			pet = remember(living, dim);
			nearby.add(uuid);
			if (living.isDeadOrDying()) {
				die(Minecraft.getInstance(), pet, "");
			}
		}
	}

	/** Adds or updates a pet from the live entity. */
	private PetEntry remember(LivingEntity entity, String dim) {
		PetEntry pet = store.byUuid(entity.getStringUUID());
		boolean changed = pet == null;
		if (pet == null) {
			pet = new PetEntry();
			pet.uuid = entity.getStringUUID();
			pet.type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		}
		if (!entity.isDeadOrDying() && pet.dead()) {
			// Seen alive after all (e.g. the death was someone else's pet with the same UUID in a message).
			pet.died = 0;
			pet.deathMessage = "";
			changed = true;
		}
		String name = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : "";
		boolean sitting = entity instanceof TamableAnimal animal && animal.isInSittingPose();
		boolean leashed = entity instanceof Leashable leashable && leashable.isLeashed();
		changed |= !pet.dimension.equals(dim) || pet.position().distanceTo(entity.position()) > MOVE || !pet.name.equals(name)
				|| pet.sitting != sitting || pet.leashed != leashed || Math.abs(pet.health - entity.getHealth()) >= 1f;
		pet.name = name;
		pet.dimension = dim;
		pet.x = entity.getX();
		pet.y = entity.getY();
		pet.z = entity.getZ();
		pet.health = entity.getHealth();
		pet.maxHealth = entity.getMaxHealth();
		pet.sitting = sitting;
		pet.leashed = leashed;
		if (entity instanceof AbstractHorse horse) {
			pet.speed = EntityInfoProvider.horseSpeed(horse);
			pet.jump = EntityInfoProvider.horseJump(horse);
		}
		pet.seen = System.currentTimeMillis();
		if (changed) {
			store.put(pet);
		}
		return pet;
	}

	// ---------------------------------------------------------------- dying

	/** Death messages name the victim with a hover that carries its UUID; the server sends them to the owner. */
	private void onMessage(Component message) {
		if (!active() || !(message.getContents() instanceof TranslatableContents death) || !death.getKey().startsWith("death.")
				|| death.getArgs().length == 0 || !(death.getArgs()[0] instanceof Component victim)) {
			return;
		}
		String uuid = entityUuid(victim);
		PetEntry pet = uuid == null ? null : store.byUuid(uuid);
		if (pet != null) {
			die(Minecraft.getInstance(), pet, message.getString());
		}
	}

	private static String entityUuid(Component component) {
		if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowEntity show) {
			return show.entity().uuid.toString();
		}
		for (Component sibling : component.getSiblings()) {
			String uuid = entityUuid(sibling);
			if (uuid != null) {
				return uuid;
			}
		}
		return null;
	}

	private void die(Minecraft mc, PetEntry pet, String message) {
		if (pet.dead()) {
			if (!message.isEmpty() && pet.deathMessage.isEmpty()) {
				pet.deathMessage = message;
				store.changed();
			}
			return;
		}
		pet.died = System.currentTimeMillis();
		pet.deathMessage = message;
		store.changed();
		store.save();
		if (config.deathToast) {
			mc.gui.toastManager().addToast(new TrackerToast(icon(pet), Component.translatable("cobblecomfort.pets.toast.died", displayName(pet)),
					Component.literal((int) Math.floor(pet.x) + ", " + (int) Math.floor(pet.y) + ", " + (int) Math.floor(pet.z))));
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6f, 0.7f));
		}
	}

	/** Forgets a pet (or removes its grave). */
	public void forget(PetEntry pet) {
		store.remove(pet);
		store.save();
	}

	// ---------------------------------------------------------------- texts

	public static Component typeName(PetEntry pet) {
		Identifier id = Identifier.tryParse(pet.type);
		return id == null ? Component.literal(pet.type)
				: BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(Component.literal(pet.type));
	}

	/** The name tag's name, or the kind of animal. */
	public static Component displayName(PetEntry pet) {
		return pet.name.isEmpty() ? typeName(pet) : Component.literal(pet.name);
	}

	/** "Bello (Wolf)" in gold for named pets, otherwise just "Wolf"; grey when dead. */
	public static Component title(PetEntry pet) {
		MutableComponent text = displayName(pet).copy().withColor(pet.dead() ? DEAD_COLOR : pet.name.isEmpty() ? 0xFFFFFFFF : NAME_COLOR);
		if (!pet.name.isEmpty()) {
			text.append(Component.literal(" (").append(typeName(pet)).append(")").withStyle(ChatFormatting.GRAY));
		}
		return text;
	}

	/** "❤ 18/20 · sitting · leashed", or "✝ died 5 min ago". */
	public static Component status(PetEntry pet) {
		if (pet.dead()) {
			return Component.translatable("cobblecomfort.pets.died", StorageItems.age(pet.died)).withColor(DEAD_COLOR);
		}
		MutableComponent text = Component.literal("❤ " + Math.round(pet.health) + "/" + Math.round(pet.maxHealth)).withColor(ALIVE_COLOR);
		if (pet.sitting) {
			text.append(Component.literal(" · ").append(Component.translatable("cobblecomfort.pets.sitting")).withStyle(ChatFormatting.GRAY));
		}
		if (pet.leashed) {
			text.append(Component.literal(" · ").append(Component.translatable("cobblecomfort.pets.leashed")).withStyle(ChatFormatting.GRAY));
		}
		return text;
	}

	/** "34m" in this dimension, else the dimension name. */
	public static Component where(PetEntry pet, Vec3 eye, String dim) {
		if (pet.dimension.equals(dim)) {
			return Component.literal(MapFeature.formatDistance(pet.distanceTo(eye)));
		}
		String path = pet.dimension.contains(":") ? pet.dimension.substring(pet.dimension.indexOf(':') + 1) : pet.dimension;
		String key = "cobblecomfort.storage.dim." + path;
		Component translated = Component.translatable(key);
		return translated.getString().equals(key) ? Component.literal(path) : translated;
	}

	/** "here" while loaded, otherwise how long ago it was seen. */
	public Component seen(PetEntry pet) {
		return nearby.contains(pet.uuid) && !pet.dead() ? Component.translatable("cobblecomfort.pets.here")
				: Component.translatable("cobblecomfort.storage.seen", StorageItems.age(pet.seen));
	}

	/** The lines of the map tooltip. */
	public List<Component> tooltip(PetEntry pet, Vec3 eye, String dim) {
		List<Component> lines = new ArrayList<>();
		lines.add(title(pet));
		lines.add(status(pet));
		if (!pet.deathMessage.isEmpty()) {
			lines.add(Component.literal(pet.deathMessage).withStyle(ChatFormatting.GRAY));
		}
		if (!pet.dead() && pet.speed >= 0) {
			lines.add(Component.translatable("cobblecomfort.look.horse_speed", String.format(Locale.ROOT, "%.1f", pet.speed))
					.append(" · ").append(Component.translatable("cobblecomfort.look.horse_jump", String.format(Locale.ROOT, "%.1f", pet.jump)))
					.withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.literal((int) Math.floor(pet.x) + ", " + (int) Math.floor(pet.y) + ", " + (int) Math.floor(pet.z) + " · ")
				.append(where(pet, eye, dim)).withStyle(ChatFormatting.GRAY));
		if (!pet.dead()) {
			lines.add(seen(pet).copy().withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	/** The spawn egg of the pet's kind. */
	public ItemStack icon(PetEntry pet) {
		return icons.computeIfAbsent(pet.type, type -> {
			Identifier id = Identifier.tryParse(type);
			Identifier egg = id == null ? null : Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
			return egg == null ? new ItemStack(Items.LEAD)
					: BuiltInRegistries.ITEM.getOptional(egg).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.LEAD));
		});
	}

	// ---------------------------------------------------------------- map

	/** Remembered pets in a dimension, for the map. */
	public List<PetEntry> mapEntries(String dimension) {
		List<PetEntry> list = new ArrayList<>();
		if (!active() || !config.mapMarkers) {
			return list;
		}
		for (PetEntry pet : store.all()) {
			if (pet.dimension.equals(dimension)) {
				list.add(pet);
			}
		}
		return list;
	}
}
