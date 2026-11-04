package dev.cobblecomfort.features.namecharm;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.VanillaAssets;
import dev.cobblecomfort.features.plush.PlushFeature;
import dev.cobblecomfort.features.wardrobe.ToolCharm;
import dev.cobblecomfort.features.wardrobe.ToolKind;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
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
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;

/**
 * Weapons and tools get the charm chosen for their kind in the wardrobe ({@link ToolCharm}): renamed ones by default a
 * little tag of their material with their name, others a tassel, a crystal, feathers and more. It hangs on a string from
 * where the handle meets the head or blade (in view also in Punchy's arm) and swings with physics as the item swings
 * and moves. The charm is added as an extra layer of the item's render state
 * ({@code ItemModelResolverMixin}), on top of whatever model the item or resource pack has, so other mods that move
 * held items (like Punchy) carry it along.
 */
public final class NameCharmFeature extends Feature<NameCharmConfig> {
	public static final NameCharmFeature INSTANCE = new NameCharmFeature();

	/** Widest name on the tag in font pixels; longer names are cut off with "…". */
	private static final int MAX_TEXT_WIDTH = 46;
	private static final long FORGET_NANOS = 2_000_000_000L;
	/** Room on a wardrobe tile for the longest charm below the item and a wide tag either side. */
	private static final Vector3fc[] TILE_EXTENTS = {new Vector3f(-0.25f, -0.55f, 0f), new Vector3f(1.25f, 1f, 1f)};

	private final NameCharmRenderer renderer = new NameCharmRenderer();
	private final Long2ObjectOpenHashMap<CharmPendulum> pendulums = new Long2ObjectOpenHashMap<>();
	private boolean firstPersonPass;
	/** Set while the wardrobe draws a weapon or tool as a tile: the charm to show on it, hanging still. */
	public ToolCharm preview;

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
		boolean tile = context == ItemDisplayContext.GUI && preview != null;
		if (!isEnabled() || context == ItemDisplayContext.NONE || context == ItemDisplayContext.GUI && !tile) {
			return;
		}
		ToolKind kind = ToolKind.of(stack);
		if (kind == null) {
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
		boolean shown = tile || (firstPerson ? config.firstPerson : held ? config.thirdPerson : config.inWorld);
		ToolCharm charm = tile ? preview : WardrobeFeature.INSTANCE.toolCharmFor(holder, kind);
		if (!shown || charm == null) {
			return;
		}
		CharmPendulum pendulum = held && !tile ? pendulum(holder, context, firstPerson) : null;
		Vector3fc spot = WardrobeFeature.INSTANCE.charmSpotFor(holder, kind, firstPerson);
		ItemStackRenderState.LayerRenderState layer = addLayer(output, model, stack, charm, anchor(model, spot), pendulum, firstPerson,
				tile, null);
		if (layer != null && tile) {
			// The charm hangs below the item: the tile's picture is made big enough for it.
			layer.setExtents(() -> TILE_EXTENTS);
			output.setOversizedInGui(true);
		}
	}

	/**
	 * For the charm editor: adds {@code charm} to an item drawn without any display transform, tied at {@code spot} (item
	 * pixels, {@link ToolKind#defaultSpot}) and hanging still along {@code hang}, a direction in the space it is drawn in.
	 */
	public void appendEditorLayer(ItemStackRenderState output, ItemStack stack, ToolCharm charm, Vector3fc spot, Vector3fc hang) {
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
		if (charm == null || state.cobblecomfort$activeLayerCount() == 0) {
			return;
		}
		LayerRenderStateAccessor model = (LayerRenderStateAccessor) state.cobblecomfort$layers()[0];
		addLayer(output, model, stack, charm, anchor(model, spot), null, false, true, hang);
	}

	/** Appends the charm's layer; null if there is nothing to hang (a name tag on an item with no name). */
	private ItemStackRenderState.LayerRenderState addLayer(ItemStackRenderState output, LayerRenderStateAccessor model, ItemStack stack, ToolCharm charm,
			Vector3fc anchor, CharmPendulum pendulum, boolean firstPerson, boolean sample, Vector3fc hang) {
		FormattedCharSequence line = FormattedCharSequence.EMPTY;
		Material material = null;
		if (charm == ToolCharm.NAME_TAG) {
			Component name = stack.get(DataComponents.CUSTOM_NAME);
			if (name == null && sample) {
				name = Component.translatable("cobblecomfort.wardrobe.tool_charm_sample");
			}
			if (name == null) {
				return null;
			}
			Font font = Minecraft.getInstance().font;
			FormattedText text = name;
			if (font.width(name) > MAX_TEXT_WIDTH) {
				text = FormattedText.composite(font.substrByWidth(name, MAX_TEXT_WIDTH - font.width("…")), FormattedText.of("…"));
			}
			line = Language.getInstance().getVisualOrder(text);
			material = material(stack);
		}
		NameCharmRenderer.Args args = new NameCharmRenderer.Args(charm, line, Minecraft.getInstance().font.width(line),
				material == null ? null : VanillaAssets.texture(material.texture()), material == null ? PAPER_INK : material.ink(),
				anchor, pendulum, firstPerson, sample, hang);

		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setItemTransform(model.cobblecomfort$itemTransform());
		layer.setLocalTransform(model.cobblecomfort$localTransform());
		layer.setupSpecialModel(renderer, args);
		output.appendModelIdentityElement(args);
		output.setAnimated();
		return layer;
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
		if (stack.is(Items.MACE)) {
			return IRON;
		}
		return null;
	}

	/**
	 * Where the charm is tied ({@link ToolKind#defaultSpot}, or where it was moved in the charm editor): a point of a flat
	 * item, moved into the box the model fills, so that it lands about there also in resource packs with 3D models.
	 */
	private static Vector3fc anchor(LayerRenderStateAccessor model, Vector3fc spot) {
		return anchor(box(model), spot);
	}

	/** {@code spot} (item pixels) in the box {min, max} of an item's model, in the item's unit cube. */
	public static Vector3f anchor(Vector3fc[] box, Vector3fc spot) {
		Vector3fc min = box[0];
		Vector3fc max = box[1];
		return new Vector3f(Mth.lerp(spot.x() / 16f, min.x(), max.x()), Mth.lerp(spot.y() / 16f, min.y(), max.y()),
				(min.z() + max.z()) / 2f + spot.z() / 16f);
	}

	/** The reverse of {@link #anchor(Vector3fc[], Vector3fc)}: the spot, in item pixels, at a point of the unit cube. */
	public static Vector3f spot(Vector3fc[] box, Vector3fc point) {
		Vector3fc min = box[0];
		Vector3fc max = box[1];
		return new Vector3f(16f * (point.x() - min.x()) / Math.max(1e-4f, max.x() - min.x()),
				16f * (point.y() - min.y()) / Math.max(1e-4f, max.y() - min.y()), 16f * (point.z() - (min.z() + max.z()) / 2f));
	}

	/** The box {min, max} the model of this layer fills in the unit cube (a flat item if it doesn't tell). */
	public static Vector3fc[] box(LayerRenderStateAccessor model) {
		Vector3fc[] extents = model.cobblecomfort$extents().get();
		if (extents == null || extents.length == 0) {
			return new Vector3fc[]{new Vector3f(0f, 0f, 7.5f / 16f), new Vector3f(1f, 1f, 8.5f / 16f)};
		}
		Vector3f min = new Vector3f(Float.MAX_VALUE);
		Vector3f max = new Vector3f(-Float.MAX_VALUE);
		for (Vector3fc corner : extents) {
			min.min(corner);
			max.max(corner);
		}
		return new Vector3fc[]{min, max};
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
