package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.death.DeathRecord;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Death inventory: items remembered at death, partly picked up again, warning and expiry; nothing with keepInventory. */
public class DeathInventoryScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			BlockPos spawn = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos death = spawn.offset(20, 0, 0);
			world.getServer().runCommand("tp @a " + death.getX() + " " + death.getY() + " " + death.getZ());
			world.getServer().runCommand("give @a minecraft:diamond_sword[enchantments={sharpness:5,looting:3}]");
			world.getServer().runCommand("give @a minecraft:totem_of_undying");
			world.getServer().runCommand("give @a minecraft:cobblestone 192");
			world.getServer().runCommand("give @a minecraft:oak_log 40");
			world.getServer().runCommand("give @a minecraft:bread 12");
			world.getServer().runCommand("give @a minecraft:torch 50");
			world.getServer().runCommand("give @a minecraft:iron_pickaxe");
			world.getServer().runCommand("give @a minecraft:elytra");
			world.getServer().runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(20);

			world.getServer().runCommand("kill @a");
			context.waitTicks(40);
			log(context, "after death");
			context.runOnClient(mc -> mc.player.respawn());
			context.waitTicks(60);
			log(context, "after respawn");

			// Carry the cobblestone and logs off, then pick up the rest.
			world.getServer().runCommand("execute as @e[type=item] if items entity @s contents minecraft:cobblestone run tp @s ~ ~ ~30");
			world.getServer().runCommand("execute as @e[type=item] if items entity @s contents minecraft:oak_log run tp @s ~ ~ ~30");
			String at = death.getX() + " " + death.getY() + " " + death.getZ();
			world.getServer().runCommand("execute positioned " + at + " run tp @e[type=item,distance=..8] " + at);
			world.getServer().runCommand("tp @a " + at);
			context.waitTicks(60);
			log(context, "after picking up");

			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(10);
			double[] center = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(center[0], center[1]);
			context.waitTicks(10);
			shot(context, "death_tooltip");
			context.setScreen(() -> null);

			// Fast-forward to the warning, then to the despawn.
			context.runOnClient(mc -> record().loadedRemaining = 1201);
			context.waitTicks(10);
			shot(context, "death_warning");
			context.runOnClient(mc -> record().loadedRemaining = 1);
			context.waitTicks(10);
			log(context, "after expiry");
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(10);
			context.getInput().setCursorPos(center[0], center[1]);
			context.waitTicks(10);
			shot(context, "death_expired");
			context.setScreen(() -> null);

			// keepInventory: nothing is dropped, so nothing is remembered.
			world.getServer().runCommand("gamerule keep_inventory true");
			world.getServer().runCommand("give @a minecraft:stone 10");
			world.getConnection().waitForClientboundPackets();
			world.getServer().runCommand("kill @a");
			context.waitTicks(40);
			context.runOnClient(mc -> mc.player.respawn());
			context.waitTicks(40);
			log(context, "after keepInventory death");
		}
	}

	/** The record of the newest death waypoint. */
	private static DeathRecord record() {
		Waypoint newest = null;
		for (Waypoint waypoint : MapFeature.INSTANCE.waypoints().all()) {
			if (waypoint.death && (newest == null || waypoint.created > newest.created)) {
				newest = waypoint;
			}
		}
		return newest == null ? null : MapFeature.INSTANCE.deaths().of(newest);
	}

	private static void log(ClientGameTestContext context, String step) {
		String text = context.computeOnClient(mc -> {
			DeathRecord record = record();
			int carried = 0;
			for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
				carried += mc.player.getInventory().getItem(i).isEmpty() ? 0 : 1;
			}
			int items = mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(64)).size();
			return (record == null ? "no record" : record.state + " stacks=" + record.stacks().size() + " back=" + record.recoveredStacks()
					+ " loaded=" + record.loadedRemaining + " worst=" + record.worstRemaining) + " carried=" + carried + " itemEntities=" + items
					+ " at=" + mc.player.blockPosition().toShortString();
		});
		LOGGER.info("Death inventory {}: {}", step, text);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
