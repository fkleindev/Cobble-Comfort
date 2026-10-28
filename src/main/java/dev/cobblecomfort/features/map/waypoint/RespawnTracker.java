package dev.cobblecomfort.features.map.waypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Keeps a waypoint at the bed or respawn anchor the player respawns at. The server does not tell the client its
 * respawn point, so it is taken from the "Respawn point set" message (at the bed or anchor just clicked) and checked
 * after each respawn; it is removed when the server reports it invalid or the block is seen gone (or the anchor empty).
 */
public final class RespawnTracker {
	private static final String SET_SPAWN = "block.minecraft.set_spawn";
	private static final String NOT_VALID = "block.minecraft.spawn.not_valid";
	/** How long after a right click the "set" message still belongs to the clicked block. */
	private static final long CLICK_MS = 5000;
	private static final int CHECK_TICKS = 20;
	/** Ticks after respawning before looking around, so the chunks have arrived. */
	private static final int RESPAWN_DELAY = 40;
	/** Players respawn on a free block next to the bed, so the bed is searched around them. */
	private static final int SEARCH_RADIUS = 3;
	/** Within this distance of the waypoint after respawning it is confirmed. */
	private static final double CONFIRM_DISTANCE = 4.0;
	/** The chat colour (0-15) closest to each bed colour, in {@link DyeColor} order. */
	private static final int[] BED_COLORS = {15, 6, 13, 11, 14, 10, 13, 8, 7, 3, 5, 9, 6, 2, 12, 8};
	private static final int ANCHOR_COLOR = 5;
	private static final int STRAW_COLOR = 6;

	private final WaypointStore store;
	private BlockPos clicked;
	private long clickedAt;
	private boolean wasDead;
	private boolean invalidSinceDeath;
	private int respawnCheckAt = -1;
	private int ticks;

	public RespawnTracker(WaypointStore store) {
		this.store = store;
	}

	/** Game messages: "Respawn point set" and "You have no home bed ...". */
	public void onMessage(Minecraft mc, Component message) {
		if (!store.loaded() || mc.level == null || !(message.getContents() instanceof TranslatableContents translatable)) {
			return;
		}
		if (translatable.getKey().equals(NOT_VALID)) {
			invalidSinceDeath = true;
			store.removeRespawn();
		} else if (translatable.getKey().equals(SET_SPAWN)) {
			BlockPos pos = recentClick(mc);
			if (pos != null) {
				set(mc.level, pos, dimension(mc.level));
			}
		}
	}

	public void tick(Minecraft mc, LocalPlayer player, String dimension, boolean dead) {
		ticks++;
		if (!store.loaded()) {
			return;
		}
		if (mc.options.keyUse.isDown() && mc.gui.screen() == null && mc.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK && isSpawnBlock(mc.level.getBlockState(hit.getBlockPos()).getBlock())) {
			clicked = hit.getBlockPos().immutable();
			clickedAt = System.currentTimeMillis();
		}
		if (dead) {
			if (!wasDead) {
				invalidSinceDeath = false;
			}
			wasDead = true;
		} else if (wasDead) {
			wasDead = false;
			respawnCheckAt = ticks + RESPAWN_DELAY;
		}
		if (ticks == respawnCheckAt) {
			respawnCheckAt = -1;
			checkRespawn(mc.level, player, dimension);
		}
		if (ticks % CHECK_TICKS == 0) {
			checkStillThere(mc.level, dimension);
		}
	}

	/** After respawning next to a bed or anchor that is not the waypoint yet (e.g. after /spawnpoint), it becomes it. */
	private void checkRespawn(ClientLevel level, LocalPlayer player, String dimension) {
		if (invalidSinceDeath) {
			return;
		}
		Waypoint current = store.respawn();
		if (current != null && current.dimension.equals(dimension)
				&& player.distanceToSqr(current.x + 0.5, current.yOr(player.getY()), current.z + 0.5) <= CONFIRM_DISTANCE * CONFIRM_DISTANCE) {
			return;
		}
		BlockPos center = player.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS), center.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
			if (isSpawnBlock(level.getBlockState(pos).getBlock())) {
				double distance = pos.distSqr(center);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		if (best != null) {
			set(level, best, dimension);
		}
	}

	private void checkStillThere(ClientLevel level, String dimension) {
		Waypoint current = store.respawn();
		if (current == null || !current.dimension.equals(dimension) || current.y == null) {
			return;
		}
		BlockPos pos = new BlockPos(current.x, current.y, current.z);
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		boolean valid = state.getBlock() instanceof AbstractBedBlock
				|| state.getBlock() instanceof RespawnAnchorBlock && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
		if (!valid) {
			store.removeRespawn();
		}
	}

	private BlockPos recentClick(Minecraft mc) {
		if (clicked != null && System.currentTimeMillis() - clickedAt < CLICK_MS) {
			return clicked;
		}
		// A click shorter than a tick is not seen by the tick; the crosshair is the next best guess.
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& isSpawnBlock(mc.level.getBlockState(hit.getBlockPos()).getBlock())) {
			return hit.getBlockPos();
		}
		return null;
	}

	private static boolean isSpawnBlock(Block block) {
		return block instanceof AbstractBedBlock || block instanceof RespawnAnchorBlock;
	}

	/** Makes the bed (its head) or anchor at {@code pos} the respawn waypoint. */
	private void set(ClientLevel level, BlockPos pos, String dimension) {
		BlockState state = level.getBlockState(pos);
		Waypoint waypoint;
		if (state.getBlock() instanceof AbstractBedBlock bed) {
			boolean foot = state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
					&& state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT;
			BlockPos head = foot ? pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : pos;
			waypoint = new Waypoint(Component.translatable("cobblecomfort.waypoint.respawn_bed").getString(),
					head.getX(), head.getY(), head.getZ(), dimension);
			waypoint.color = bed instanceof BedBlock dyed ? BED_COLORS[dyed.getColor().ordinal() % BED_COLORS.length] : STRAW_COLOR;
		} else if (state.getBlock() instanceof RespawnAnchorBlock) {
			waypoint = new Waypoint(Component.translatable("cobblecomfort.waypoint.respawn_anchor").getString(),
					pos.getX(), pos.getY(), pos.getZ(), dimension);
			waypoint.color = ANCHOR_COLOR;
		} else {
			return;
		}
		Waypoint current = store.respawn();
		if (current != null && current.dimension.equals(dimension) && current.x == waypoint.x && current.z == waypoint.z
				&& current.y != null && current.y.equals(waypoint.y)) {
			return;
		}
		waypoint.icon = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
		store.setRespawn(waypoint);
	}

	private static String dimension(ClientLevel level) {
		return level.dimension().identifier().toString();
	}
}
