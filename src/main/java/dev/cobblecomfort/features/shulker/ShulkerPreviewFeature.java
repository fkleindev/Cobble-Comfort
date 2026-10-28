package dev.cobblecomfort.features.shulker;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shulker box contents in tooltips, a HUD panel for a held box and a fill bar on shulker slots. */
public final class ShulkerPreviewFeature extends Feature<ShulkerPreviewConfig> {
	public static final ShulkerPreviewFeature INSTANCE = new ShulkerPreviewFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("shulker_preview");

	/** A tooltip not drawn for this long counts as closed, so hovering again replays the pop-in. */
	private static final long ANIM_RESET_MS = 150;
	private static final int HUD_MARGIN = 4;
	private static final int BAR_WIDTH = 13;
	private static final int UNDYED_SAMPLE_COLOR = 0xFF9A6C9A;

	private ItemStack animBox;
	private boolean animFull;
	private long animStartMs;
	private long animLastMs;

	private ItemStack hudBox;
	private long hudAnimStartMs;
	private int hudWidth = ShulkerGrid.width(9);
	private int hudHeight = ShulkerGrid.height(1);

	private ShulkerPreviewFeature() {
		super("shulker_preview", ShulkerPreviewConfig.class, ShulkerPreviewConfig::new);
	}

	@Override
	public void init() {
		ClientTooltipComponentCallback.EVENT.register(data ->
				data instanceof ShulkerTooltip.Data shulker ? new ShulkerTooltip(this, shulker) : null);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, LAYER_ID, (g, delta) -> drawHud(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return ShulkerPreviewConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- tooltip

	/** Tooltip image for a shulker box with contents, or empty to keep vanilla's. */
	public Optional<TooltipComponent> tooltipImage(ItemStack stack) {
		if (isEnabled() && config.tooltip && stack.is(Items.ENDER_CHEST)) {
			ShulkerContents ender = StorageFeature.INSTANCE.enderContents();
			return ender == null ? Optional.empty() : Optional.of(new ShulkerTooltip.Data(stack, ender, StorageFeature.INSTANCE.enderNote()));
		}
		if (!replacesTooltip(stack)) {
			return Optional.empty();
		}
		return Optional.of(new ShulkerTooltip.Data(stack, ShulkerContents.of(stack), null));
	}

	/** Whether the preview replaces vanilla's text list of the contents for this stack. */
	public boolean replacesTooltip(Object stack) {
		return isEnabled() && config.tooltip && stack instanceof ItemStack item && ShulkerContents.of(item) != null;
	}

	/** Start of the pop-in for the hovered box; restarts when another box (or mode) is shown. */
	long tooltipAnimStart(ItemStack box, boolean full) {
		long now = System.currentTimeMillis();
		if (box != animBox || full != animFull || now - animLastMs > ANIM_RESET_MS) {
			animBox = box;
			animFull = full;
			animStartMs = now;
		}
		animLastMs = now;
		return animStartMs;
	}

	// ---------------------------------------------------------------- HUD

	public Rect defaultBounds(float width, float height) {
		return new Rect(width - hudWidth - HUD_MARGIN, height - hudHeight - HUD_MARGIN, hudWidth, hudHeight);
	}

	private void drawHud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !config.hud || mc.player == null) {
			return;
		}
		ItemStack box = heldBox(mc.player);
		ShulkerContents contents = box == null ? null : ShulkerContents.of(box);
		if (contents == null && HudEditorScreen.isOpen()) {
			contents = sample();
		}
		if (contents == null) {
			hudBox = null;
			return;
		}
		if (box != hudBox) {
			hudBox = box;
			hudAnimStartMs = System.currentTimeMillis();
		}
		List<ShulkerGrid.Cell> cells = new ArrayList<>();
		for (ShulkerContents.Entry entry : contents.summary(config.sortByCount)) {
			cells.add(new ShulkerGrid.Cell(entry.stack(), entry.total() > 1 ? ShulkerGrid.shortCount(entry.total()) : null));
		}
		int columns = Math.max(1, Math.min(config.hudColumns, cells.size()));
		int rows = (cells.size() + columns - 1) / columns;
		hudWidth = ShulkerGrid.width(columns);
		hudHeight = ShulkerGrid.height(rows);
		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		ShulkerGrid.draw(g, mc.font, (int) bounds.x(), (int) bounds.y(), cells, columns, rows, contents.color(),
				config.tint ? config.tintStrength : 0f, config.popIn ? hudAnimStartMs : 0L, config);
	}

	private ItemStack heldBox(LocalPlayer player) {
		if (ShulkerContents.isShulkerBox(player.getMainHandItem())) {
			return player.getMainHandItem();
		}
		if (config.hudOffhand && ShulkerContents.isShulkerBox(player.getOffhandItem())) {
			return player.getOffhandItem();
		}
		return null;
	}

	/** Example contents for positioning the panel in the HUD editor. */
	private static ShulkerContents sample() {
		List<ItemStack> slots = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 24), new ItemStack(Items.IRON_INGOT, 64),
				new ItemStack(Items.IRON_INGOT, 40), new ItemStack(Items.GOLDEN_APPLE, 5), new ItemStack(Items.OAK_LOG, 64)));
		while (slots.size() < ShulkerContents.SLOTS) {
			slots.add(ItemStack.EMPTY);
		}
		return new ShulkerContents(slots, UNDYED_SAMPLE_COLOR);
	}

	// ---------------------------------------------------------------- fill bar

	/** Draws the fill bar on a shulker box in an inventory or hotbar slot at (x, y). */
	public void drawFillBar(GuiGraphicsExtractor g, ItemStack stack, int x, int y, boolean hotbar) {
		if (!isEnabled() || !config.fillBar || (hotbar && !config.fillBarInHotbar) || !ShulkerContents.isShulkerBox(stack)) {
			return;
		}
		ShulkerContents contents = ShulkerContents.of(stack);
		if (contents == null) {
			return;
		}
		float fill = Math.clamp(contents.fill(config.fillMode), 0f, 1f);
		int filled = Math.max(1, Math.round(fill * BAR_WIDTH));
		int color = fill >= 0.999f ? config.fullColor : RenderUtil.lerpColor(config.fillColor, config.fullColor, fill * fill);
		int bx = x + 2;
		int by = y + 13;
		g.fill(bx, by, bx + BAR_WIDTH, by + 2, 0xFF000000);
		g.fill(bx, by, bx + filled, by + 1, color);
	}
}
