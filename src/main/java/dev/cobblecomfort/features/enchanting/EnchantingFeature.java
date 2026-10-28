package dev.cobblecomfort.features.enchanting;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.enchantdesc.EnchantDescFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Bookshelf counter and markers, enchantment chances and descriptions for the enchanting table. */
public final class EnchantingFeature extends Feature<EnchantingConfig> {
	public static final EnchantingFeature INSTANCE = new EnchantingFeature();

	private static final int SLOT_X = 60;
	private static final int SLOT_Y = 14;
	private static final int SLOT_STEP = 19;
	private static final int SLOT_WIDTH = 108;
	private static final int SLOT_HEIGHT = 17;
	private static final float MARKER_INFLATE = 0.02f;
	private static final float MARKER_STROKE = 2f;
	private static final float MARKER_FILL_ALPHA = 0.18f;

	private final OddsPanel panel = new OddsPanel(this);
	/** The table the open enchanting screen belongs to, or null if unknown. */
	private BlockPos screenTable;
	private BookshelfScan scan;
	private EnchantOdds odds;

	private EnchantingFeature() {
		super("enchanting", EnchantingConfig.class, EnchantingConfig::new);
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof EnchantmentScreen enchantScreen) {
				onScreenInit(mc, enchantScreen);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	@Override
	public ConfigCategory buildCategory() {
		return EnchantingConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- screen

	private void onScreenInit(Minecraft mc, EnchantmentScreen screen) {
		screenTable = lookedAtTable(mc);
		scan = screenTable != null && mc.level != null ? BookshelfScan.of(mc.level, screenTable) : null;
		if (!isEnabled()) {
			return;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		if (config.oddsPanel) {
			Button toggle = Button.builder(Component.literal("%"), b -> {
						config.panelOpen = !config.panelOpen;
						save();
					})
					.bounds(gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() - 21, gui.cobblecomfort$topPos() + 3, 16, 12)
					.tooltip(Tooltip.create(Component.translatable("cobblecomfort.enchanting.panel_button")))
					.build();
			Screens.getWidgets(screen).add(toggle);
		}
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
				!(panelVisible() && panel.scroll(gui, mouseX, mouseY, vertical)));
		ScreenEvents.remove(screen).register(s -> {
			screenTable = null;
			odds = null;
		});
	}

	private boolean panelVisible() {
		return isEnabled() && config.oddsPanel && config.panelOpen;
	}

	/** Draws the shelf info and the panel; called at the end of the screen's extractRenderState, before tooltips. */
	public void drawOverlay(EnchantmentScreen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (!isEnabled()) {
			return;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
		if (scan != null && (config.shelfCounter || config.maxLevelInfo)) {
			drawShelfInfo(g, gui);
		}
		if (panelVisible()) {
			panel.draw(g, gui, oddsFor(screen.getMenu()), mouseX, mouseY);
		}
	}

	private void drawShelfInfo(GuiGraphicsExtractor g, ContainerScreenAccessor gui) {
		Font font = Minecraft.getInstance().font;
		MutableComponent text = Component.empty();
		if (config.shelfCounter) {
			text.append(Component.translatable("cobblecomfort.enchanting.shelves",
					Math.min(scan.count(), BookshelfScan.MAX_SHELVES), BookshelfScan.MAX_SHELVES)
					.withStyle(scan.count() >= BookshelfScan.MAX_SHELVES ? ChatFormatting.GREEN : ChatFormatting.WHITE));
		}
		if (config.maxLevelInfo) {
			if (config.shelfCounter) {
				text.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
			}
			text.append(Component.translatable("cobblecomfort.enchanting.max_level", BookshelfScan.maxLevel(scan.count()))
					.withStyle(ChatFormatting.AQUA));
			int missing = BookshelfScan.MAX_SHELVES - scan.count();
			if (missing > 0) {
				text.append(Component.literal("  ")).append(Component.translatable("cobblecomfort.enchanting.missing", missing)
						.withStyle(ChatFormatting.GRAY));
			}
		}
		int centerX = gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() / 2;
		int above = InventoryToolsFeature.INSTANCE.searchTabHeight();
		g.centeredText(font, text, centerX, gui.cobblecomfort$topPos() - above - font.lineHeight - 3, 0xFFFFFFFF);
	}

	/** The cached chances for the current item and costs, recomputed when either changes. */
	EnchantOdds oddsFor(EnchantmentMenu menu) {
		Minecraft mc = Minecraft.getInstance();
		ItemStack item = menu.getSlot(0).getItem();
		if (mc.level == null) {
			return null;
		}
		if (odds == null || !odds.matches(item, menu.costs, config.samples)) {
			odds = EnchantOdds.compute(mc.level.registryAccess(), item, menu.costs, config.samples);
		}
		return odds;
	}

	// ---------------------------------------------------------------- slot tooltip and runes

	/** Adds description, extra chances and the Shift hint to the tooltip of a hovered enchantment slot. */
	public void decorateSlotTooltip(EnchantmentScreen screen, List<Component> lines, int mouseX, int mouseY) {
		if (!isEnabled() || (!config.slotDescriptions && !config.slotOdds) || lines.isEmpty()) {
			return;
		}
		EnchantmentMenu menu = screen.getMenu();
		int slot = hoveredSlot((ContainerScreenAccessor) screen, mouseX, mouseY);
		Optional<Holder<Enchantment>> clue = slot < 0 ? Optional.empty() : clue(menu, slot);
		if (clue.isEmpty()) {
			return;
		}
		List<Component> added = new ArrayList<>();
		EnchantDescFeature desc = EnchantDescFeature.INSTANCE;
		boolean details = desc.detailsShown();
		if (config.slotDescriptions && details) {
			desc.addDetails(clue.get(), added::add);
		}
		if (config.slotOdds) {
			EnchantOdds current = oddsFor(menu);
			List<EnchantOdds.Extra> extras = new ArrayList<>();
			float chance = current == null ? 0f
					: current.extraChance(slot, clue.get(), menu.levelClue[slot], extras, config.maxExtras);
			added.add(Component.translatable("cobblecomfort.enchanting.extra_chance", percent(chance))
					.withStyle(ChatFormatting.DARK_AQUA));
			for (EnchantOdds.Extra extra : extras) {
				added.add(Component.literal("  + ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(extra.enchantment().value().description().getString() + " "
								+ levelRange(extra.minLevel(), extra.maxLevel())).withStyle(ChatFormatting.GRAY))
						.append(Component.literal("  " + percent(extra.chance())).withStyle(ChatFormatting.DARK_AQUA)));
			}
		}
		if (config.slotDescriptions && !details && desc.hint() != null) {
			added.add(desc.hint());
		}
		lines.addAll(1, added);
	}

	/** Replaces the galactic text on a slot with the readable clue, or returns null to keep vanilla's text. */
	public FormattedText readableRunes(EnchantmentMenu menu, int slot) {
		if (!isEnabled() || !config.readableRunes || slot < 0 || slot >= EnchantOdds.SLOTS) {
			return null;
		}
		return clue(menu, slot)
				.map(h -> (FormattedText) Component.literal(Component.translatable("container.enchant.clue",
						Enchantment.getFullname(h, menu.levelClue[slot])).getString()))
				.orElse(null);
	}

	private static Optional<Holder<Enchantment>> clue(EnchantmentMenu menu, int slot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || menu.enchantClue[slot] < 0) {
			return Optional.empty();
		}
		return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(menu.enchantClue[slot]).<Holder<Enchantment>>map(h -> h);
	}

	private static int hoveredSlot(ContainerScreenAccessor gui, int mouseX, int mouseY) {
		int x = mouseX - gui.cobblecomfort$leftPos() - SLOT_X;
		for (int i = 0; i < EnchantOdds.SLOTS; i++) {
			int y = mouseY - gui.cobblecomfort$topPos() - SLOT_Y - SLOT_STEP * i;
			if (x >= 0 && x < SLOT_WIDTH && y >= 0 && y < SLOT_HEIGHT) {
				return i;
			}
		}
		return -1;
	}

	static String percent(float chance) {
		if (chance <= 0f) {
			return "–";
		}
		int value = Math.round(chance * 100);
		return value == 0 ? "<1%" : value + "%";
	}

	static String levelRange(int min, int max) {
		if (min == Integer.MAX_VALUE || max <= 0) {
			return "";
		}
		String from = Component.translatable("enchantment.level." + min).getString();
		return min == max ? from : from + "–" + Component.translatable("enchantment.level." + max).getString();
	}

	// ---------------------------------------------------------------- world markers

	private void tick(Minecraft mc) {
		if (!isEnabled() || !config.worldMarkers || mc.level == null || mc.player == null) {
			return;
		}
		BlockPos table = mc.gui.screen() instanceof EnchantmentScreen ? screenTable : lookedAtTable(mc);
		if (table == null) {
			return;
		}
		BookshelfScan current = BookshelfScan.of(mc.level, table);
		if (mc.gui.screen() instanceof EnchantmentScreen) {
			scan = current;
		}
		try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
			if (config.markValid) {
				current.valid().forEach(pos -> marker(pos, config.validColor));
			}
			if (config.markBlocked) {
				current.blocked().forEach(pos -> marker(pos, config.blockedColor));
			}
			if (config.markEmpty && current.count() < BookshelfScan.MAX_SHELVES) {
				current.empty().forEach(pos -> marker(pos, config.emptyColor));
			}
		}
	}

	private static void marker(BlockPos pos, int color) {
		int fill = ((int) (MARKER_FILL_ALPHA * 255) << 24) | (color & 0xFFFFFF);
		Gizmos.cuboid(pos, MARKER_INFLATE, GizmoStyle.strokeAndFill(color, MARKER_STROKE, fill));
	}

	private static BlockPos lookedAtTable(Minecraft mc) {
		if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
		return mc.level.getBlockState(pos).getBlock() instanceof EnchantingTableBlock ? pos : null;
	}
}
