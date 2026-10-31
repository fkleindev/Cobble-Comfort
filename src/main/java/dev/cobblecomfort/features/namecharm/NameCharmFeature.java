package dev.cobblecomfort.features.namecharm;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.plush.PlushFeature;
import dev.cobblecomfort.mixin.ItemStackRenderStateAccessor;
import dev.cobblecomfort.mixin.LayerRenderStateAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;

/**
 * Renamed weapons and tools get a little paper tag with their name, hanging from the handle on a string and swinging
 * with physics as the item swings and moves. The charm is added as an extra layer of the item's render state
 * ({@code ItemModelResolverMixin}), on top of whatever model the item or resource pack has, so other mods that move
 * held items (like Punchy) carry it along.
 */
public final class NameCharmFeature extends Feature<NameCharmConfig> {
	public static final NameCharmFeature INSTANCE = new NameCharmFeature();

	/** Widest name on the tag in font pixels; longer names are cut off with "…". */
	private static final int MAX_TEXT_WIDTH = 46;
	/** Where the handle is when the model doesn't say: the bottom left corner of a normal item, one pixel in. */
	private static final float DEFAULT_ANCHOR = 1.5f / 16f;
	private static final long FORGET_NANOS = 2_000_000_000L;

	private final NameCharmRenderer renderer = new NameCharmRenderer();
	private final Long2ObjectOpenHashMap<CharmPendulum> pendulums = new Long2ObjectOpenHashMap<>();
	private boolean firstPersonPass;

	private NameCharmFeature() {
		super("name_charm", NameCharmConfig.class, NameCharmConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return NameCharmConfigUi.buildCategory(this);
	}

	/** Set while the first-person hands are drawn ({@code GameRendererMixin}); they are drawn relative to the camera. */
	public void setFirstPersonPass(boolean active) {
		firstPersonPass = active;
	}

	boolean inFirstPersonPass() {
		return firstPersonPass;
	}

	/** Speed lost per second: little with a long swing, a lot with a short one. */
	double damping() {
		return Mth.lerp(Mth.clamp(config.swing, 0f, 1f), 7.0, 0.6);
	}

	/** Appends the charm layer after the item's own layers, if this stack gets one here. */
	public void appendLayer(ItemStackRenderState output, ItemStack stack, ItemDisplayContext context, ItemOwner owner) {
		if (!isEnabled() || context == ItemDisplayContext.GUI || context == ItemDisplayContext.NONE
				|| !stack.has(DataComponents.CUSTOM_NAME) || !weaponOrTool(stack)) {
			return;
		}
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
		if (state.cobblecomfort$activeLayerCount() == 0) {
			return;
		}
		LayerRenderStateAccessor model = (LayerRenderStateAccessor) state.cobblecomfort$layers()[0];

		LivingEntity holder = owner == null ? null : owner.asLivingEntity();
		boolean firstPerson = context.firstPerson() || PlushFeature.INSTANCE.inPunchyArm();
		boolean held = holder != null && (firstPerson || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
		boolean shown = firstPerson ? config.firstPerson : held ? config.thirdPerson : config.inWorld;
		if (!shown) {
			return;
		}

		Component name = stack.get(DataComponents.CUSTOM_NAME);
		Font font = Minecraft.getInstance().font;
		FormattedText text = name;
		if (font.width(name) > MAX_TEXT_WIDTH) {
			text = FormattedText.composite(font.substrByWidth(name, MAX_TEXT_WIDTH - font.width("…")), FormattedText.of("…"));
		}
		FormattedCharSequence line = Language.getInstance().getVisualOrder(text);
		CharmPendulum pendulum = held ? pendulum(holder, context, firstPerson) : null;
		Material material = material(stack);
		NameCharmRenderer.Args args = new NameCharmRenderer.Args(line, font.width(line),
				material == null ? null : material.texture(), material == null ? PAPER_INK : material.ink(), anchor(model),
				pendulum);

		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setItemTransform(model.cobblecomfort$itemTransform());
		layer.setLocalTransform(model.cobblecomfort$localTransform());
		layer.setupSpecialModel(renderer, args);
		output.appendModelIdentityElement(args);
		output.setAnimated();
	}

	/** The block a tag of this item's material is cut from, and a text colour that reads well on it. */
	private record Material(Identifier texture, int ink) {
		static Material of(String block, int ink) {
			return new Material(Identifier.withDefaultNamespace("textures/block/" + block + ".png"), ink);
		}
	}

	private static final int DARK_INK = 0xFF2B1D12;
	private static final int LIGHT_INK = 0xFFEDE4D3;
	private static final int PAPER_INK = 0xFF3B2A1C;
	private static final Material WOOD = Material.of("oak_planks", DARK_INK);
	private static final Material IRON = Material.of("iron_block", DARK_INK);
	/** By the item name's prefix, so modded tools named the same way get their material too. */
	private static final Map<String, Material> BY_PREFIX = Map.of(
			"wooden_", WOOD,
			"stone_", Material.of("cobblestone", DARK_INK),
			"copper_", Material.of("copper_block", DARK_INK),
			"iron_", IRON,
			"golden_", Material.of("gold_block", DARK_INK),
			"diamond_", Material.of("diamond_block", DARK_INK),
			"netherite_", Material.of("netherite_block", LIGHT_INK));

	/** The item's material, or null for plain paper (items of no known material). */
	private static Material material(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (Map.Entry<String, Material> entry : BY_PREFIX.entrySet()) {
			if (path.startsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.FISHING_ROD)) {
			return WOOD;
		}
		if (stack.is(Items.TRIDENT)) {
			return Material.of("prismarine", DARK_INK);
		}
		if (stack.is(Items.MACE) || stack.is(Items.SHEARS)) {
			return IRON;
		}
		return null;
	}

	private static boolean weaponOrTool(ItemStack stack) {
		return stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL)
				|| stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.TRIDENT) || stack.is(Items.MACE)
				|| stack.is(Items.FISHING_ROD);
	}

	/**
	 * The handle: the model's corner nearest the bottom left, which is where swords and tools are held, also in most
	 * resource packs with 3D models. Flat items cover the whole square, so it is moved in onto the handle's pixel.
	 */
	private static Vector3fc anchor(LayerRenderStateAccessor model) {
		Vector3fc[] extents = model.cobblecomfort$extents().get();
		if (extents == null || extents.length == 0) {
			return new Vector3f(DEFAULT_ANCHOR, DEFAULT_ANCHOR, 0.5f);
		}
		Vector3fc best = extents[0];
		float minZ = Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		for (Vector3fc v : extents) {
			if (v.x() + v.y() < best.x() + best.y()) {
				best = v;
			}
			minZ = Math.min(minZ, v.z());
			maxZ = Math.max(maxZ, v.z());
		}
		return new Vector3f(Math.max(best.x(), DEFAULT_ANCHOR), Math.max(best.y(), DEFAULT_ANCHOR), (minZ + maxZ) / 2f);
	}

	private CharmPendulum pendulum(LivingEntity holder, ItemDisplayContext context, boolean firstPerson) {
		boolean left = context.leftHand();
		long key = (long) holder.getId() << 2 | (left ? 2 : 0) | (firstPerson ? 1 : 0);
		long now = System.nanoTime();
		CharmPendulum pendulum = pendulums.get(key);
		if (pendulum == null) {
			pendulum = new CharmPendulum();
			pendulum.lastSeen = now;
			pendulums.put(key, pendulum);
			if (pendulums.size() > 64) {
				pendulums.long2ObjectEntrySet().removeIf(e -> now - e.getValue().lastSeen > FORGET_NANOS);
			}
		}
		return pendulum;
	}
}
