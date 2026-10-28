package dev.cobblecomfort.features.inventoryfx;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.cobblecomfort.features.rarity.RarityLevel;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/** Makes moving items in inventories feel lively: a springy carried item, slot bounces and particles. */
public final class InventoryFxFeature extends Feature<InventoryFxConfig> {
	public static final InventoryFxFeature INSTANCE = new InventoryFxFeature();

	private static final int HALF_ITEM = 8;
	private static final float SLOT_ANIM_SECONDS = 0.6f;
	private static final float MAX_TILT = 0.5f;
	private static final float MOVE_PARTICLES_PER_PIXEL = 0.12f;

	/** Draws the carried item (with vanilla's count text) at the given position in the current pose. */
	@FunctionalInterface
	public interface CarriedDrawer {
		void draw(int x, int y);
	}

	private enum SlotAnimType {
		DROP, POP
	}

	private record SlotAnim(SlotAnimType type, long startMs) {
	}

	/**
	 * An item flying between slots after a shortcut. {@code to} null means it flies to the cursor.
	 * {@code hideTarget}: the target slot was empty before, so its item stays hidden until the ghost lands.
	 */
	private record Ghost(ItemStack stack, Slot from, Slot to, boolean hideTarget, long startMs) {
	}

	private final ScreenParticles particles = new ScreenParticles();
	private final Map<Slot, SlotAnim> slotAnims = new WeakHashMap<>();
	private final List<Ghost> ghosts = new ArrayList<>();
	private List<ItemStack> beforeInput;
	private final Random random = new Random();

	private Object screen;
	private long lastFrameNanos;
	private Item prevItem;
	private int prevCount;
	private Set<Slot> prevQuickCraft = new HashSet<>();
	private float x, y, prevX, prevY;
	private float tilt;
	private long pickupMs;
	private float moveAccumulator;
	private int lastLeft, lastTop;

	private InventoryFxFeature() {
		super("inventory_fx", InventoryFxConfig.class, InventoryFxConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return InventoryFxConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- carried item & particles

	/**
	 * Called every frame from the container screen instead of vanilla's carried item rendering.
	 * Returns false when the feature is off and vanilla should draw the item itself.
	 */
	public boolean frame(GuiGraphicsExtractor g, Object currentScreen, int mouseX, int mouseY, ItemStack carried,
			Slot hovered, int leftPos, int topPos, Set<Slot> quickCraftSlots, CarriedDrawer drawer) {
		if (!isEnabled()) {
			screen = null;
			return false;
		}
		long nanos = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((nanos - lastFrameNanos) / 1e9f, 0.05f);
		lastFrameNanos = nanos;
		long now = System.currentTimeMillis();

		lastLeft = leftPos;
		lastTop = topPos;
		if (currentScreen != screen) {
			screen = currentScreen;
			particles.clear();
			slotAnims.clear();
			ghosts.clear();
			x = prevX = mouseX;
			y = prevY = mouseY;
			tilt = 0f;
			prevItem = carried.isEmpty() ? null : carried.getItem();
			prevCount = carried.getCount();
			prevQuickCraft = new HashSet<>(quickCraftSlots);
		}

		detectChanges(carried, hovered, leftPos, topPos, mouseX, mouseY, now);
		detectSpread(quickCraftSlots, leftPos, topPos, now);

		// Spring-like follow: exponential approach, slower with more lag.
		prevX = x;
		prevY = y;
		float speed = 40f + (6f - 40f) * Math.clamp(config.followLag, 0f, 1f);
		float k = 1f - (float) Math.exp(-speed * dt);
		x += (mouseX - x) * k;
		y += (mouseY - y) * k;
		float vx = dt > 0 ? (x - prevX) / dt : 0f;
		float vy = dt > 0 ? (y - prevY) / dt : 0f;
		float targetTilt = config.tilt ? Math.clamp(vx * 0.0025f * config.tiltStrength, -MAX_TILT, MAX_TILT) : 0f;
		tilt += (targetTilt - tilt) * (1f - (float) Math.exp(-12f * dt));

		if (!carried.isEmpty() && config.particles && config.moveParticles) {
			float moved = (float) Math.hypot(x - prevX, y - prevY);
			moveAccumulator += moved * MOVE_PARTICLES_PER_PIXEL * config.density;
			while (moveAccumulator >= 1f) {
				moveAccumulator -= 1f;
				emit(carried, x + (random.nextFloat() - 0.5f) * 12f, y + (random.nextFloat() - 0.5f) * 12f,
						-vx * 0.1f, -vy * 0.1f, 20f);
			}
		}
		particles.update(dt, config.gravity);

		g.nextStratum();
		particles.draw(g);
		drawGhosts(g, leftPos, topPos, mouseX, mouseY, now);

		if (!carried.isEmpty()) {
			g.nextStratum();
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y);
			if (tilt != 0f) {
				pose.rotate(tilt);
			}
			float pop = popScale(now);
			if (pop != 1f) {
				pose.scale(pop);
			}
			drawer.draw(-HALF_ITEM, -HALF_ITEM);
			pose.popMatrix();
		}
		return true;
	}

	private float popScale(long now) {
		if (!config.pickupPop) {
			return 1f;
		}
		float t = (now - pickupMs) / 1000f;
		if (t > 1f) {
			return 1f;
		}
		return 1f + config.popStrength * (float) (Math.exp(-7 * t) * Math.cos(14 * t));
	}

	private void detectChanges(ItemStack carried, Slot hovered, int leftPos, int topPos, int mouseX, int mouseY, long now) {
		Item item = carried.isEmpty() ? null : carried.getItem();
		int count = carried.getCount();
		boolean had = prevItem != null;
		boolean has = item != null;
		boolean dropped = had && (!has || item != prevItem || count < prevCount);
		boolean picked = has && (!had || item != prevItem);

		if (dropped) {
			ItemStack droppedStack = new ItemStack(prevItem);
			if (hovered != null) {
				if (config.dropSquash || config.slotFlash) {
					slotAnims.put(hovered, new SlotAnim(SlotAnimType.DROP, now));
				}
				burst(droppedStack, leftPos + hovered.x + HALF_ITEM, topPos + hovered.y + HALF_ITEM, config.dropBurst);
			} else {
				burst(droppedStack, mouseX, mouseY, config.dropBurst);
			}
			sound(1.2f);
		}
		if (picked) {
			pickupMs = now;
			x = prevX = mouseX;
			y = prevY = mouseY;
			burst(carried, mouseX, mouseY, config.pickupBurst);
			sound(1.7f);
		}
		prevItem = item;
		prevCount = count;
	}

	private void detectSpread(Set<Slot> quickCraftSlots, int leftPos, int topPos, long now) {
		if (quickCraftSlots.size() > prevQuickCraft.size() || !prevQuickCraft.containsAll(quickCraftSlots)) {
			for (Slot slot : quickCraftSlots) {
				if (!prevQuickCraft.contains(slot) && config.spreadPop) {
					slotAnims.put(slot, new SlotAnim(SlotAnimType.POP, now));
					if (prevItem != null) {
						ItemStack stack = new ItemStack(prevItem);
						for (int i = 0; i < config.burstSize / 3; i++) {
							emit(stack, leftPos + slot.x + HALF_ITEM, topPos + slot.y + HALF_ITEM, 0f, -20f, 40f);
						}
					}
				}
			}
		}
		prevQuickCraft = new HashSet<>(quickCraftSlots);
	}

	private void burst(ItemStack stack, float bx, float by, boolean enabled) {
		if (!enabled || !config.particles) {
			return;
		}
		for (int i = 0; i < config.burstSize; i++) {
			emit(stack, bx + (random.nextFloat() - 0.5f) * 8f, by + (random.nextFloat() - 0.5f) * 8f, 0f, -40f, 70f);
		}
	}

	/** Emits one particle in the item's style: a texture crumb, or a rarity spark for rare items. */
	private void emit(ItemStack stack, float px, float py, float vx, float vy, float spread) {
		boolean rare = RarityLevel.of(stack.getRarity()).atLeast(config.sparksFrom);
		boolean spark = switch (config.particleStyle) {
			case CRUMBS -> false;
			case SPARKS -> true;
			case BOTH -> rare && random.nextFloat() < 0.4f;
		};
		int color;
		if (spark) {
			color = RarityFeature.INSTANCE.rarityColor(stack);
		} else {
			int[] palette = ItemColors.palette(stack);
			color = palette[random.nextInt(palette.length)];
		}
		particles.spawn(px, py, vx, vy, spread, color, spark, config.particleLifetime, config.maxParticles);
	}

	private void sound(float pitch) {
		if (!config.sound) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP,
				pitch + random.nextFloat() * 0.3f, config.soundVolume));
	}

	// ---------------------------------------------------------------- shortcuts

	/** Remembers the slot contents before a container input, which the client applies locally right away. */
	public void beforeInput(AbstractContainerMenu menu) {
		if (!isEnabled() || !config.shortcutFx) {
			beforeInput = null;
			return;
		}
		beforeInput = new ArrayList<>(menu.slots.size());
		for (Slot slot : menu.slots) {
			beforeInput.add(slot.getItem().copy());
		}
	}

	/**
	 * Compares the slots after the input and animates what moved. Plain clicks and drag-splitting are left out:
	 * they move the carried item, which the carried-item effects already cover.
	 */
	public void afterInput(AbstractContainerMenu menu, ContainerInput input) {
		List<ItemStack> before = beforeInput;
		beforeInput = null;
		if (before == null || before.size() != menu.slots.size() || screen == null
				|| input == ContainerInput.PICKUP || input == ContainerInput.QUICK_CRAFT || input == ContainerInput.CLONE) {
			return;
		}
		List<Slot> lost = new ArrayList<>();
		List<Slot> gained = new ArrayList<>();
		for (int i = 0; i < before.size(); i++) {
			Slot slot = menu.slots.get(i);
			ItemStack old = before.get(i);
			ItemStack now = slot.getItem();
			boolean same = ItemStack.isSameItemSameComponents(old, now);
			if (!old.isEmpty() && (!same || now.getCount() < old.getCount())) {
				lost.add(slot);
			}
			if (!now.isEmpty() && (!same || now.getCount() > old.getCount())) {
				gained.add(slot);
			}
		}
		long now = System.currentTimeMillis();
		if (input == ContainerInput.PICKUP_ALL) {
			for (Slot slot : lost) {
				launch(before.get(menu.slots.indexOf(slot)), slot, null, false, now);
			}
			return;
		}
		if (input == ContainerInput.THROW) {
			for (Slot slot : lost) {
				ItemStack thrown = before.get(menu.slots.indexOf(slot));
				burst(thrown, screenX(slot), screenY(slot), true);
				slotAnims.put(slot, new SlotAnim(SlotAnimType.POP, now));
			}
			sound(1.0f);
			return;
		}
		// QUICK_MOVE and SWAP: pair every slot that gained items with a slot that lost the same item.
		for (Slot target : gained) {
			ItemStack stack = target.getItem();
			Slot source = lost.stream().filter(s -> ItemStack.isSameItemSameComponents(before.get(menu.slots.indexOf(s)), stack))
					.findFirst().orElse(null);
			boolean wasEmpty = before.get(menu.slots.indexOf(target)).isEmpty();
			if (source == null) {
				land(stack, target);
			} else {
				launch(stack.copyWithCount(1), source, target, wasEmpty, now);
			}
		}
		for (Slot source : lost) {
			if (config.particles && config.pickupBurst) {
				ItemStack old = before.get(menu.slots.indexOf(source));
				for (int i = 0; i < config.burstSize / 3; i++) {
					emit(old, screenX(source), screenY(source), 0f, -20f, 40f);
				}
			}
		}
	}

	private void launch(ItemStack stack, Slot from, Slot to, boolean hideTarget, long now) {
		if (config.flyingItems) {
			ghosts.add(new Ghost(stack.copyWithCount(1), from, to, hideTarget, now));
		} else if (to != null) {
			land(stack, to);
		}
	}

	/** Arrival effects at a slot: squash, flash, particle burst and sound. */
	private void land(ItemStack stack, Slot slot) {
		if (config.dropSquash || config.slotFlash) {
			slotAnims.put(slot, new SlotAnim(SlotAnimType.DROP, System.currentTimeMillis()));
		}
		burst(stack, screenX(slot), screenY(slot), config.dropBurst);
		sound(1.2f);
	}

	private void drawGhosts(GuiGraphicsExtractor g, int leftPos, int topPos, int mouseX, int mouseY, long now) {
		if (ghosts.isEmpty()) {
			return;
		}
		g.nextStratum();
		float duration = Math.max(0.05f, config.flyDuration) * 1000f;
		Iterator<Ghost> it = ghosts.iterator();
		while (it.hasNext()) {
			Ghost ghost = it.next();
			float t = (now - ghost.startMs()) / duration;
			if (t >= 1f) {
				it.remove();
				if (ghost.to() != null) {
					land(ghost.stack(), ghost.to());
				} else {
					burst(ghost.stack(), mouseX, mouseY, config.pickupBurst);
				}
				continue;
			}
			float eased = 1f - (float) Math.pow(1f - t, 3);
			float fx = screenX(ghost.from());
			float fy = screenY(ghost.from());
			float tx = ghost.to() != null ? screenX(ghost.to()) : mouseX;
			float ty = ghost.to() != null ? screenY(ghost.to()) : mouseY;
			float gx = fx + (tx - fx) * eased;
			float gy = fy + (ty - fy) * eased - (float) Math.sin(Math.PI * eased) * config.flyArc;
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(gx, gy);
			pose.scale(1f + 0.15f * (float) Math.sin(Math.PI * t));
			g.item(ghost.stack(), -HALF_ITEM, -HALF_ITEM);
			pose.popMatrix();
		}
	}

	private float screenX(Slot slot) {
		return lastLeft + slot.x + HALF_ITEM;
	}

	private float screenY(Slot slot) {
		return lastTop + slot.y + HALF_ITEM;
	}

	private boolean hiddenByGhost(Slot slot) {
		for (Ghost ghost : ghosts) {
			if (ghost.to() == slot && ghost.hideTarget()) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- slot animations

	/** Lets the given slots pop one after another (e.g. after sorting). */
	public void popSlots(List<Slot> slots, int staggerMs) {
		if (!isEnabled() || !config.spreadPop) {
			return;
		}
		long now = System.currentTimeMillis();
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).hasItem()) {
				slotAnims.put(slots.get(i), new SlotAnim(SlotAnimType.POP, now + (long) i * staggerMs));
			}
		}
	}

	/** Applies the slot's bounce, if any. Must be paired with {@link #endSlot}. */
	public void beginSlot(GuiGraphicsExtractor g, Slot slot) {
		g.pose().pushMatrix();
		if (!isEnabled()) {
			return;
		}
		if (hiddenByGhost(slot)) {
			// Collapse the slot's item to nothing until its flying copy lands.
			g.pose().translate(slot.x + HALF_ITEM, slot.y + HALF_ITEM);
			g.pose().scale(0f);
			return;
		}
		SlotAnim anim = slotAnims.get(slot);
		if (anim == null) {
			return;
		}
		float t = (System.currentTimeMillis() - anim.startMs()) / 1000f;
		if (t < 0f || t > SLOT_ANIM_SECONDS) {
			return;
		}
		float wave = (float) (Math.exp(-9 * t) * Math.cos(22 * t));
		float sx;
		float sy;
		if (anim.type() == SlotAnimType.DROP) {
			if (!config.dropSquash) {
				return;
			}
			float squash = config.squashStrength * wave;
			sx = 1f + squash;
			sy = 1f - squash;
		} else {
			sx = sy = 1f + 0.25f * wave;
		}
		float cx = slot.x + HALF_ITEM;
		float cy = slot.y + HALF_ITEM;
		g.pose().translate(cx, cy);
		g.pose().scale(sx, sy);
		g.pose().translate(-cx, -cy);
	}

	public void endSlot(GuiGraphicsExtractor g, Slot slot) {
		if (isEnabled() && config.slotFlash) {
			SlotAnim anim = slotAnims.get(slot);
			if (anim != null && anim.type() == SlotAnimType.DROP) {
				float t = (System.currentTimeMillis() - anim.startMs()) / 1000f;
				if (t < SLOT_ANIM_SECONDS) {
					g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
							RenderUtil.withAlpha(0xFFFFFFFF, 0.5f * (float) Math.exp(-8 * t)));
				}
			}
		}
		g.pose().popMatrix();
	}
}
