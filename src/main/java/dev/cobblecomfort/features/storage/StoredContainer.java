package dev.cobblecomfort.features.storage;

import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** One remembered container: where it is, what it is, what was in it when last seen and an optional name. */
public final class StoredContainer {
	public enum Kind {
		BLOCK, ENTITY, ENDER
	}

	public Kind kind = Kind.BLOCK;
	/** Dimension id; empty for the ender chest, which is the same everywhere. */
	public String dimension = "";
	public int x, y, z;
	/** The other half of a double chest, or null. */
	public int[] other;
	/** Entity UUID for minecarts, boats and animals with chests. */
	public String uuid;
	/** Block or entity type id. */
	public String type = "";
	public String name = "";
	/** When the contents were last seen (ms since epoch). */
	public long seen;
	/** Saved form of {@link #items}. */
	List<JsonElement> stacks = new ArrayList<>();

	transient List<ItemStack> items = new ArrayList<>();
	private transient ItemStack icon;

	public List<ItemStack> items() {
		return items;
	}

	public BlockPos pos() {
		return new BlockPos(x, y, z);
	}

	/** The other half of a double chest, or null. */
	public BlockPos otherPos() {
		return other == null || other.length < 3 ? null : new BlockPos(other[0], other[1], other[2]);
	}

	public boolean at(String dim, BlockPos pos) {
		return kind == Kind.BLOCK && dimension.equals(dim)
				&& ((pos.getX() == x && pos.getY() == y && pos.getZ() == z) || pos.equals(otherPos()));
	}

	/** The middle of the container (between both halves of a double chest). */
	public Vec3 center() {
		if (kind == Kind.ENTITY) {
			return new Vec3(x + 0.5, y + 0.5, z + 0.5);
		}
		double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
		if (other != null) {
			cx = (cx + other[0] + 0.5) / 2;
			cz = (cz + other[2] + 0.5) / 2;
		}
		return new Vec3(cx, cy, cz);
	}

	public boolean located() {
		return kind != Kind.ENDER;
	}

	/** The block or entity type name ("Chest", "Donkey", ...). */
	public Component typeName() {
		Identifier id = Identifier.tryParse(type);
		if (kind == Kind.ENTITY && id != null) {
			return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(Component.literal(type));
		}
		if (id != null) {
			return BuiltInRegistries.BLOCK.getOptional(id).map(Block::getName).orElse(Component.literal(type));
		}
		return Component.literal(type);
	}

	/** The given name, or the type name. */
	public Component displayName() {
		return name.isEmpty() ? typeName() : Component.literal(name);
	}

	public ItemStack icon() {
		if (icon == null) {
			icon = computeIcon();
		}
		return icon;
	}

	private ItemStack computeIcon() {
		Identifier id = Identifier.tryParse(type);
		if (id == null) {
			return new ItemStack(Items.CHEST);
		}
		if (kind == Kind.ENTITY) {
			EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			if (entityType == null) {
				return new ItemStack(Items.CHEST);
			}
			if (id.getPath().equals("chest_minecart")) {
				return new ItemStack(Items.CHEST_MINECART);
			}
			if (id.getPath().equals("hopper_minecart")) {
				return new ItemStack(Items.HOPPER_MINECART);
			}
			ItemStack egg = SpawnEggItem.byId(entityType).map(ItemStack::new).orElse(null);
			if (egg != null) {
				return egg;
			}
			// Chest boats and rafts have an item of the same name.
			var item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.CHEST);
			return new ItemStack(item);
		}
		Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		ItemStack stack = block == null ? ItemStack.EMPTY : new ItemStack(block.asItem());
		return stack.isEmpty() ? new ItemStack(Items.CHEST) : stack;
	}

	public double distanceTo(Vec3 point) {
		return center().distanceTo(point);
	}
}
