package dev.cobblecomfort.features.lookinfo.providers;

import dev.cobblecomfort.features.lookinfo.InfoLine;
import dev.cobblecomfort.features.lookinfo.LookInfoConfig;
import dev.cobblecomfort.features.lookinfo.LookTarget;
import dev.cobblecomfort.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BlockInfoProvider {
	private static final Map<TagKey<Block>, String> TOOLS = Map.of(
			BlockTags.MINEABLE_WITH_PICKAXE, "pickaxe",
			BlockTags.MINEABLE_WITH_AXE, "axe",
			BlockTags.MINEABLE_WITH_SHOVEL, "shovel",
			BlockTags.MINEABLE_WITH_HOE, "hoe");
	private static final int BREAK_COLOR = 0xFFFFAA00;

	private BlockInfoProvider() {
	}

	public static LookTarget block(Minecraft mc, LookInfoConfig cfg, BlockPos pos) {
		Level level = mc.level;
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		Block block = state.getBlock();
		ItemStack icon = state.getCloneItemStack(level, pos, false);
		List<InfoLine> lines = new ArrayList<>();

		if (cfg.showHarvestTool) {
			addHarvestLine(mc, state, lines);
		}
		if (cfg.showGrowth) {
			addGrowthLine(state, lines);
		}
		if (cfg.showRedstone && state.hasProperty(BlockStateProperties.POWER)) {
			lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.redstone",
					state.getValue(BlockStateProperties.POWER))));
		}
		if (cfg.showBreakProgress && mc.gameMode != null) {
			MultiPlayerGameModeAccessor gameMode = (MultiPlayerGameModeAccessor) mc.gameMode;
			if (mc.gameMode.isDestroying() && pos.equals(gameMode.cobblecomfort$destroyBlockPos())) {
				float progress = Math.clamp(gameMode.cobblecomfort$destroyProgress(), 0f, 1f);
				lines.add(new InfoLine.Bar(Component.translatable("cobblecomfort.look.breaking",
						Math.round(progress * 100)), progress, BREAK_COLOR));
			}
		}
		if (cfg.showBlockStates) {
			for (Property<?> property : state.getProperties()) {
				lines.add(new InfoLine.Text(Component.literal(property.getName() + ": " + valueName(state, property))
						.withStyle(ChatFormatting.DARK_GRAY)));
			}
		}

		String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
		return new LookTarget(List.of(pos.immutable(), state), icon, block.getName(),
				ModNames.of(namespace, cfg.modColor), lines);
	}

	public static LookTarget fluid(LookInfoConfig cfg, BlockPos pos, FluidState fluid) {
		ItemStack icon = new ItemStack(fluid.getType().getBucket());
		Component name = fluid.createLegacyBlock().getBlock().getName();
		String namespace = BuiltInRegistries.FLUID.getKey(fluid.getType()).getNamespace();
		List<InfoLine> lines = new ArrayList<>();
		if (!fluid.isSource()) {
			lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.flowing")));
		}
		return new LookTarget(List.of(pos.immutable(), fluid), icon, name, ModNames.of(namespace, cfg.modColor), lines);
	}

	private static void addHarvestLine(Minecraft mc, BlockState state, List<InfoLine> lines) {
		String tool = null;
		for (Map.Entry<TagKey<Block>, String> entry : TOOLS.entrySet()) {
			if (state.is(entry.getKey())) {
				tool = entry.getValue();
				break;
			}
		}
		if (tool == null && !state.requiresCorrectToolForDrops()) {
			return;
		}
		boolean harvestable = !state.requiresCorrectToolForDrops()
				|| mc.player.getMainHandItem().isCorrectToolForDrops(state);
		Component toolName = tool != null
				? Component.translatable("cobblecomfort.look.tool." + tool)
				: Component.translatable("cobblecomfort.look.tool.special");
		Component mark = harvestable
				? Component.literal(" ✔").withStyle(ChatFormatting.GREEN)
				: Component.literal(" ✘").withStyle(ChatFormatting.RED);
		lines.add(new InfoLine.Text(Component.translatable("cobblecomfort.look.tool", toolName).append(mark)));
	}

	private static void addGrowthLine(BlockState state, List<InfoLine> lines) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty age && property.getName().equals("age")) {
				List<Integer> values = age.getPossibleValues();
				int max = values.getLast();
				int value = state.getValue(age);
				if (max <= 0) {
					return;
				}
				lines.add(new InfoLine.Text(value >= max
						? Component.translatable("cobblecomfort.look.growth.mature").withStyle(ChatFormatting.GREEN)
						: Component.translatable("cobblecomfort.look.growth", Math.round(value * 100f / max))));
				return;
			}
		}
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}
}
