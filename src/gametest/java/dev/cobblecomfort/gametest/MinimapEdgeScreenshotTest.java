package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimap: a near waypoint as its marker, far ones as small arrows on the edge, and hidden when switched off. */
public class MinimapEdgeScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			context.runOnClient(mc -> {
				String dim = MapFeature.dimensionId(mc.level);
				var store = MapFeature.INSTANCE.waypoints();
				Waypoint home = new Waypoint("Home", player.getX() + 12, player.getY(), player.getZ() - 8, dim);
				home.color = 10;
				store.add(home);
				int[][] far = {{300, 0, 12}, {-400, 250, 6}, {80, 900, 14}, {-200, -700, 9}};
				for (int i = 0; i < far.length; i++) {
					Waypoint waypoint = new Waypoint("Far " + i, player.getX() + far[i][0], player.getY(), player.getZ() + far[i][1], dim);
					waypoint.color = far[i][2];
					store.add(waypoint);
				}
			});
			context.waitTicks(40);
			shot(context, "minimap_edge_arrows");

			context.runOnClient(mc -> MapFeature.INSTANCE.config().minimapEdgeWaypoints = false);
			context.waitTicks(5);
			shot(context, "minimap_edge_hidden");
			context.runOnClient(mc -> MapFeature.INSTANCE.config().minimapEdgeWaypoints = true);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
