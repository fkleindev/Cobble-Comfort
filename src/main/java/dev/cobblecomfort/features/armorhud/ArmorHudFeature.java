package dev.cobblecomfort.features.armorhud;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Shows worn armor with durability (and arrows for bows) next to the hotbar. */
public final class ArmorHudFeature extends Feature<ArmorHudConfig> {
	public static final Identifier LAYER_ID = CobbleComfortClient.id("armor_hud");

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Four armor slots plus the arrow counter. */
	private static final int SLOT_COUNT = ARMOR.length + 1;
	private static final int ICON = 16;
	/** Room left of the hotbar for the offhand slot, so the HUD does not jump when the offhand changes. */
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int OFFHAND_ROOM = 29;
	private static final int VERTICAL_TEXT_WIDTH = 30;
	private static final float SHAKE_SECONDS = 0.3f;
	private static final float MOVE_SPEED = 14f;
	private static final int BAR_WIDTH = 13;

	// Declared after the constants above: the constructor needs SLOT_COUNT to be initialised.
	public static final ArmorHudFeature INSTANCE = new ArmorHudFeature();

	private final SlotState[] states = new SlotState[SLOT_COUNT];
	private long lastFrameMs;

	private static final class SlotState {
		ItemStack shown = ItemStack.EMPTY;
		float presence;
		float position = Float.NaN;
		int lastDamage = -1;
		long shakeStartMs;
		boolean warned;
	}

	private ArmorHudFeature() {
		super("armor_hud", ArmorHudConfig.class, ArmorHudConfig::new);
		for (int i = 0; i < SLOT_COUNT; i++) {
			states[i] = new SlotState();
		}
	}

	@Override
	public void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, LAYER_ID, (g, delta) -> render(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return ArmorHudConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- layout

	/** Vanilla-position bounds: a fixed-size box left of the hotbar, so the pivot never moves. */
	public Rect defaultBounds(float width, float height) {
		float right = width / 2f - HOTBAR_HALF_WIDTH - OFFHAND_ROOM;
		if (config.layout == ArmorLayout.VERTICAL) {
			float w = ICON + 2 + VERTICAL_TEXT_WIDTH;
			float h = SLOT_COUNT * config.spacing;
			return new Rect(Math.max(2f, right - w), height - 2 - h, w, h);
		}
		float w = SLOT_COUNT * config.spacing;
		// On very narrow screens stay inside the screen, even if that overlaps the offhand slot.
		return new Rect(Math.max(2f, right - w), height - 27, w, 26);
	}

	// ---------------------------------------------------------------- rendering

	private void render(GuiGraphicsExtractor g) {
		long now = System.currentTimeMillis();
		float dt = lastFrameMs == 0 ? 0f : Math.min((now - lastFrameMs) / 1000f, 0.1f);
		lastFrameMs = now;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!isEnabled() || player == null) {
			return;
		}

		ItemStack[] current = new ItemStack[SLOT_COUNT];
		for (int i = 0; i < ARMOR.length; i++) {
			current[i] = player.getItemBySlot(ARMOR[i]);
		}
		int arrows = config.showArrows ? arrowCount(player) : Integer.MIN_VALUE;
		current[ARMOR.length] = arrows == Integer.MIN_VALUE ? ItemStack.EMPTY : arrowIcon(player);
		if (HudEditorScreen.isOpen() && allEmpty(current)) {
			current = sampleItems();
		}

		List<Integer> visible = new ArrayList<>();
		for (int i = 0; i < SLOT_COUNT; i++) {
			SlotState state = states[i];
			ItemStack stack = current[i];
			boolean present = !stack.isEmpty() || (config.showEmptySlots && i < ARMOR.length);
			if (present) {
				trackDamage(mc, state, stack, now);
				state.shown = stack;
			}
			if (config.animateEquip && config.equipDuration > 0f) {
				float step = dt / config.equipDuration;
				state.presence = Math.clamp(state.presence + (present ? step : -step), 0f, 1f);
			} else {
				state.presence = present ? 1f : 0f;
			}
			if (state.presence <= 0f) {
				state.shown = ItemStack.EMPTY;
				state.position = Float.NaN;
			} else {
				visible.add(i);
			}
		}

		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		Font font = mc.font;
		for (int v = 0; v < visible.size(); v++) {
			int index = visible.get(v);
			SlotState state = states[index];
			// Packed towards the hotbar: the last visible slot sits closest to it.
			float target = visible.size() - 1 - v;
			if (Float.isNaN(state.position) || !config.smoothMovement) {
				state.position = target;
			} else {
				state.position += (target - state.position) * (1f - (float) Math.exp(-MOVE_SPEED * dt));
			}
			boolean isArrows = index == ARMOR.length;
			drawSlot(g, font, bounds, state, isArrows ? arrows : Integer.MIN_VALUE, now);
		}
	}

	private void drawSlot(GuiGraphicsExtractor g, Font font, Rect bounds, SlotState state, int arrowCount, long now) {
		float eased = Easing.EASE_OUT_CUBIC.apply(state.presence);
		float shake = 0f;
		float shakeAge = (now - state.shakeStartMs) / 1000f;
		if (config.shakeOnDamage && shakeAge < SHAKE_SECONDS) {
			shake = (float) Math.sin(shakeAge * 60) * 1.5f * (1f - shakeAge / SHAKE_SECONDS);
		}

		int spacing = config.spacing;
		float x;
		float y;
		if (config.layout == ArmorLayout.VERTICAL) {
			x = bounds.x();
			y = bounds.bottom() - (state.position + 1) * spacing + (spacing - ICON) / 2f;
		} else {
			x = bounds.right() - (state.position + 1) * spacing + (spacing - ICON) / 2f;
			y = bounds.y();
		}
		x += shake;

		ItemStack stack = state.shown;
		boolean empty = stack.isEmpty();
		float fraction = !empty && stack.isDamageableItem() && stack.getMaxDamage() > 0
				? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : -1f;
		boolean low = fraction >= 0f && fraction < config.warnBelow || arrowCount == 0;
		boolean blinkOn = low && config.warnBlink && (now / 250) % 2 == 0;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		float scale = 0.6f + 0.4f * eased;
		pose.translate(x + ICON / 2f, y + ICON / 2f);
		pose.scale(scale);
		pose.translate(-(x + ICON / 2f), -(y + ICON / 2f));
		int ix = Math.round(x);
		int iy = Math.round(y);
		if (blinkOn) {
			g.fill(ix - 1, iy - 1, ix + ICON + 1, iy + ICON + 1, RenderUtil.withAlpha(config.warnColor, 0.45f * eased));
		}
		if (empty) {
			g.outline(ix, iy, ICON, ICON, RenderUtil.withAlpha(0x60FFFFFF, eased));
		} else if (eased >= 0.5f) {
			RarityFeature.INSTANCE.drawSlot(g, stack, ix, iy, false, RarityFeature.Place.ARMOR_HUD);
			g.item(stack, ix, iy);
		}
		pose.popMatrix();

		String text = label(stack, fraction, arrowCount);
		int color = low ? config.warnColor
				: config.colorByDurability && fraction >= 0f ? stack.getBarColor() | 0xFF000000 : config.textColor;
		color = RenderUtil.withAlpha(color, eased);
		if (config.durabilityDisplay == DurabilityDisplay.BAR && fraction >= 0f) {
			int bx = ix + (ICON - BAR_WIDTH) / 2;
			int by = config.layout == ArmorLayout.VERTICAL ? iy + ICON / 2 - 1 : iy + ICON + 1;
			if (config.layout == ArmorLayout.VERTICAL) {
				bx = ix + ICON + 2;
			}
			g.fill(bx, by, bx + BAR_WIDTH, by + 2, RenderUtil.withAlpha(0xFF000000, eased));
			g.fill(bx, by, bx + Math.round(BAR_WIDTH * fraction), by + 1, color);
		} else if (text != null) {
			drawLabel(g, font, text, ix, iy, color);
		}
	}

	private String label(ItemStack stack, float fraction, int arrowCount) {
		if (arrowCount != Integer.MIN_VALUE) {
			return arrowCount < 0 ? "∞" : Integer.toString(arrowCount);
		}
		if (fraction < 0f) {
			return null;
		}
		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		return switch (config.durabilityDisplay) {
			case PERCENT -> Math.round(fraction * 100) + "%";
			case REMAINING -> Integer.toString(remaining);
			case FULL -> remaining + "/" + stack.getMaxDamage();
			case BAR, NONE -> null;
		};
	}

	private void drawLabel(GuiGraphicsExtractor g, Font font, String text, int ix, int iy, int color) {
		float s = config.textScale;
		float tx;
		float ty;
		if (config.layout == ArmorLayout.VERTICAL) {
			tx = ix + ICON + 2;
			ty = iy + (ICON - font.lineHeight * s) / 2f + 1;
		} else {
			tx = ix + ICON / 2f - font.width(text) * s / 2f;
			ty = iy + ICON + 1;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(tx, ty);
		pose.scale(s);
		g.text(font, text, 0, 0, color, config.textShadow);
		pose.popMatrix();
	}

	private void trackDamage(Minecraft mc, SlotState state, ItemStack stack, long now) {
		if (stack.isEmpty() || state.shown.isEmpty() || stack.getItem() != state.shown.getItem()) {
			state.lastDamage = stack.getDamageValue();
			state.warned = false;
			return;
		}
		int damage = stack.getDamageValue();
		if (state.lastDamage >= 0 && damage > state.lastDamage) {
			state.shakeStartMs = now;
		}
		state.lastDamage = damage;
		if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			boolean low = 1f - damage / (float) stack.getMaxDamage() < config.warnBelow;
			if (low && !state.warned && config.warnSound) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK.value(), 1.6f, 0.5f));
			}
			state.warned = low;
		}
	}

	// ---------------------------------------------------------------- arrows

	private static ProjectileWeaponItem heldWeapon(LocalPlayer player, ItemStack[] stackOut) {
		for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
			if (stack.getItem() instanceof ProjectileWeaponItem weapon) {
				stackOut[0] = stack;
				return weapon;
			}
		}
		return null;
	}

	/** Arrows usable by the held bow or crossbow, -1 for unlimited, MIN_VALUE when no such weapon is held. */
	private static int arrowCount(LocalPlayer player) {
		ItemStack[] weaponStack = new ItemStack[1];
		ProjectileWeaponItem weapon = heldWeapon(player, weaponStack);
		if (weapon == null) {
			return Integer.MIN_VALUE;
		}
		if (player.hasInfiniteMaterials()) {
			return -1;
		}
		Predicate<ItemStack> supported = weapon.getAllSupportedProjectiles();
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (supported.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static ItemStack arrowIcon(LocalPlayer player) {
		ItemStack[] weaponStack = new ItemStack[1];
		heldWeapon(player, weaponStack);
		ItemStack projectile = weaponStack[0] != null ? player.getProjectile(weaponStack[0]) : ItemStack.EMPTY;
		return projectile.isEmpty() ? new ItemStack(Items.ARROW) : projectile;
	}

	private static boolean allEmpty(ItemStack[] stacks) {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Placeholder armor shown in the HUD editor so the element can be positioned without wearing any. */
	private static ItemStack[] sampleItems() {
		return new ItemStack[]{new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
				new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS), ItemStack.EMPTY};
	}
}
