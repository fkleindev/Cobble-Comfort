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

/** Map animations: a waypoint dropping onto the minimap, an edge arrow pulsing, the map opening out of the minimap. */
public class MapAnimScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("tp @a ~ ~ ~ 135 0");
			context.waitTicks(20);
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());

			// A new waypoint drops onto the minimap.
			context.runOnClient(mc -> {
				Waypoint home = new Waypoint("Home", player.getX() + 15, player.getY(), player.getZ() + 10, MapFeature.dimensionId(mc.level));
				home.color = 10;
				MapFeature.INSTANCE.waypoints().add(home);
			});
			context.waitTicks(2);
			shot(context, "mapanim_drop_falling");
			context.waitTicks(3);
			shot(context, "mapanim_drop_impact");
			context.waitTicks(15);
			shot(context, "mapanim_drop_landed");

			// Walking away: the waypoint leaves the minimap and its edge arrow pulses.
			world.getServer().runCommand("tp @a ~-120 ~ ~-60");
			context.waitTicks(4);
			shot(context, "mapanim_edge_pulse");
			context.waitTicks(20);

			// The map opens out of the minimap.
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(1);
			shot(context, "mapanim_open_1");
			context.waitTicks(2);
			shot(context, "mapanim_open_2");
			context.waitTicks(2);
			shot(context, "mapanim_open_3");
			context.waitTicks(10);
			shot(context, "mapanim_open_done");
			context.setScreen(() -> null);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
