package dev.cobblecomfort.features.belt;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import dev.cobblecomfort.features.petplush.PetPlushFeature;
import dev.cobblecomfort.features.petplush.PlushLook;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;

/**
 * A belt with what you carry: bundles hang from it and puff up as they fill (those in the hotbar first, then the
 * fullest ones from the inventory), a sword from the hotbar on the left hip, a bow or crossbow with a quiver on the
 * right. What is in your hand stays in your hand. Other players with the mod see it through the Cobble Comfort Sync
 * plugin, which passes it on together with the pet plushes ({@link PlushLook}).
 */
public final class BeltFeature extends Feature<BeltConfig> {
	public static final BeltFeature INSTANCE = new BeltFeature();

	private static final long FORGET_NANOS = 2_000_000_000L;

	private final Int2ObjectOpenHashMap<CharmPendulum[]> pendulums = new Int2ObjectOpenHashMap<>();
	private List<BeltBundle> own = List.of();
	private BeltGear ownGear = BeltGear.NONE;
	private final Map<GearItem, ItemStack> stacks = new HashMap<>();

	private BeltFeature() {
		super("belt", BeltConfig.class, BeltConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean on = mc.player != null && isEnabled();
			own = on ? carried(mc.player.getInventory()) : List.of();
			ownGear = on ? BeltGear.of(mc.player) : BeltGear.NONE;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			pendulums.clear();
			stacks.clear();
			own = List.of();
			ownGear = BeltGear.NONE;
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new BeltLayer(parent));
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return BeltConfigUi.buildCategory(this);
	}

	/** The bundles in this inventory, in belt order: hotbar first (not the one in hand), then the fullest. */
	private static List<BeltBundle> carried(Inventory inventory) {
		List<BeltBundle> bundles = new ArrayList<>();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			BeltBundle bundle = slot == inventory.getSelectedSlot() ? null : BeltBundle.of(inventory.getItem(slot));
			if (bundle != null) {
				bundles.add(bundle);
			}
		}
		List<BeltBundle> rest = new ArrayList<>();
		for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
			BeltBundle bundle = BeltBundle.of(inventory.getItem(slot));
			if (bundle != null) {
				rest.add(bundle);
			}
		}
		rest.sort(Comparator.comparingDouble(BeltBundle::fill).reversed());
		bundles.addAll(rest);
		return List.copyOf(bundles.subList(0, Math.min(bundles.size(), BeltBundle.MAX)));
	}

	/** The bundles this player has on their belt, as far as they are shown here. */
	List<BeltBundle> bundlesFor(AbstractClientPlayer player) {
		if (!isEnabled() || !config.bundles) {
			return List.of();
		}
		List<BeltBundle> bundles;
		if (player == Minecraft.getInstance().player) {
			bundles = config.showOwn ? own : List.of();
		} else {
			PlushLook shared = config.showOthers ? PetPlushFeature.INSTANCE.shared(player.getUUID()) : null;
			bundles = shared == null ? List.of() : shared.bundles();
		}
		int count = Mth.clamp(config.count, 1, BeltBundle.MAX);
		return bundles.size() > count ? bundles.subList(0, count) : bundles;
	}

	/** The weapons this player has on their belt, as far as they are shown here. */
	BeltGear gearFor(AbstractClientPlayer player) {
		if (!isEnabled() || (!config.melee && !config.ranged)) {
			return BeltGear.NONE;
		}
		BeltGear gear;
		if (player == Minecraft.getInstance().player) {
			gear = config.showOwn ? ownGear : BeltGear.NONE;
		} else {
			PlushLook shared = config.showOthers ? PetPlushFeature.INSTANCE.shared(player.getUUID()) : null;
			gear = shared == null ? BeltGear.NONE : shared.gear();
		}
		return new BeltGear(config.melee ? gear.melee() : null, config.ranged ? gear.ranged() : null,
				config.ranged ? gear.arrow() : null, gear.arrows());
	}

	/** The bundles to tell the server about (all of them; each viewer picks how many to show). */
	public List<BeltBundle> shared() {
		return isEnabled() && config.share ? own : List.of();
	}

	/** The weapons to tell the server about. */
	public BeltGear sharedGear() {
		return isEnabled() && config.share ? ownGear : BeltGear.NONE;
	}

	/** Speed lost per second: little with a long swing, a lot with a short one. */
	double damping() {
		return Mth.lerp(Mth.clamp(config.swing, 0f, 1f), 7.0, 0.6);
	}

	/** The swinging of one player's belt: one pendulum per bundle place, then the weapon, the bow and the quiver. */
	CharmPendulum[] pendulums(int entityId) {
		long now = System.nanoTime();
		CharmPendulum[] list = pendulums.get(entityId);
		if (list == null) {
			list = new CharmPendulum[BeltLayer.PENDULUMS];
			for (int i = 0; i < list.length; i++) {
				list[i] = new CharmPendulum();
				list[i].lastSeen = now;
			}
			pendulums.put(entityId, list);
			if (pendulums.size() > 64) {
				pendulums.int2ObjectEntrySet().removeIf(e -> now - lastSeen(e.getValue()) > FORGET_NANOS);
			}
		}
		return list;
	}

	private static long lastSeen(CharmPendulum[] list) {
		long last = 0;
		for (CharmPendulum pendulum : list) {
			last = Math.max(last, pendulum.lastSeen);
		}
		return last;
	}

	/** A stack to draw for a worn item, made once. */
	ItemStack stack(GearItem gear) {
		if (stacks.size() > 64) {
			stacks.clear();
		}
		return stacks.computeIfAbsent(gear, GearItem::toStack);
	}
}
