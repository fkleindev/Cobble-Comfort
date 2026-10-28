package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Respawn waypoint: set by sleeping in a bed, kept after respawning there, removed when the bed is gone. */
public class RespawnScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("time set midnight");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos bed = player.offset(0, 0, 3);
			world.getServer().runCommand("setblock " + bed.getX() + " " + bed.getY() + " " + bed.getZ() + " minecraft:blue_bed[facing=south,part=foot]");
			world.getServer().runCommand("setblock " + bed.getX() + " " + bed.getY() + " " + (bed.getZ() + 1) + " minecraft:blue_bed[facing=south,part=head]");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);

			context.getInput().lookAt(bed);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			log(context, "after sleeping");
			world.getServer().runCommand("time set noon");
			context.waitTicks(20);
			// Wake up and step away so the marker is visible next to the player.
			context.runOnClient(mc -> mc.player.stopSleeping());
			world.getServer().runCommand("tp @a " + (player.getX() + 6) + " " + player.getY() + " " + player.getZ());
			context.waitTicks(20);
			shot(context, "respawn_minimap");
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(10);
			shot(context, "respawn_map");
			context.setScreen(() -> null);

			// Dying and respawning at the bed keeps the waypoint.
			world.getServer().runCommand("kill @a");
			context.waitTicks(20);
			context.runOnClient(mc -> mc.player.respawn());
			context.waitTicks(60);
			log(context, "after respawn at bed");

			// Without the bed the next respawn reports it invalid; seeing the bed gone removes it as well.
			world.getServer().runCommand("setblock " + bed.getX() + " " + bed.getY() + " " + (bed.getZ() + 1) + " minecraft:air");
			context.waitTicks(30);
			log(context, "after breaking the bed");
		}
	}

	private static void log(ClientGameTestContext context, String step) {
		String text = context.computeOnClient(mc -> {
			Waypoint respawn = MapFeature.INSTANCE.waypoints().respawn();
			return respawn == null ? "none" : respawn.name + " " + respawn.x + "," + respawn.y + "," + respawn.z + " icon=" + respawn.icon
					+ " color=" + respawn.color;
		});
		LOGGER.info("Respawn waypoint {}: {}", step, text);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
