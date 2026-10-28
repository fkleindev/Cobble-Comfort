package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.map.screen.MapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Map screenshots in a normal (not flat) world with a fixed seed: explored map, seed terrain, structures, caves, Nether. */
public class MapScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(state -> state.setSeed("1234567890"))
				.create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamemode spectator @a");
			context.waitTicks(300);
			shot(context, "map2_minimap");

			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(5);
			double[] center = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(center[0], center[1]);
			context.getInput().scroll(-9);
			context.waitTicks(400);
			shot(context, "map2_zoomed_out");
			context.setScreen(() -> null);

			world.getServer().runCommand("tp @a ~ 0 ~");
			context.waitTicks(120);
			shot(context, "map2_cave");

			world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 70 0");
			context.waitTicks(300);
			shot(context, "map2_nether");
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
