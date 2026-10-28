package dev.cobblecomfort.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pickup log: items dropped onto the player, merged rows, slide-in and fade-out. Screenshots only. */
public class PickupLogScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("give @a minecraft:cobblestone 400");

			drop(world, "cobblestone", 32);
			drop(world, "diamond", 1);
			drop(world, "enchanted_golden_apple", 2);
			drop(world, "oak_log", 16);
			context.waitTicks(10);
			shot(context, "pickup_log");

			// A second pickup of the same item merges into its row (+32 → +64) and moves it to the bottom.
			drop(world, "cobblestone", 32);
			context.waitTicks(3);
			shot(context, "pickup_log_merge");

			context.waitTicks(90);
			shot(context, "pickup_log_fading");
		}
	}

	private static void drop(TestSingleplayerContext world, String item, int count) {
		world.getServer().runCommand("execute at @p run summon minecraft:item ~ ~ ~ {PickupDelay:0s,Item:{id:\"minecraft:"
				+ item + "\",count:" + count + "}}");
		world.getConnection().waitForClientboundPackets();
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
