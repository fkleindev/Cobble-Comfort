package dev.cobblecomfort.gametest;

import com.mojang.authlib.GameProfile;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint.Icon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Locator bar: map waypoints as pins, other players as framed heads, the name of what is straight ahead. */
public class LocatorScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("xp add @a 5 levels");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			context.getInput().lookAt(player.offset(0, 1, 40));
			context.waitTicks(10);
			shot(context, "locator_none");

			context.runOnClient(mc -> {
				String dim = MapFeature.dimensionId(mc.level);
				var store = MapFeature.INSTANCE.waypoints();
				Waypoint base = new Waypoint("Base", player.getX(), player.getY(), player.getZ() + 40, dim);
				base.color = 10;
				store.add(base);
				Waypoint mine = new Waypoint("Mine", player.getX() + 35, player.getY() - 40, player.getZ() + 40, dim);
				mine.color = 6;
				store.add(mine);
				Waypoint hidden = new Waypoint("Hidden", player.getX() - 10, player.getY(), player.getZ() + 50, dim);
				hidden.locatorBar = false;
				store.add(hidden);
				store.addDeath(new Waypoint("Death", player.getX() - 30, player.getY(), player.getZ() + 45, dim), 3);
				// Two other players, as a server would send them: Alex near on the right, Steve far on the left.
				addPlayer(mc.getConnection(), "Alex", 0xFF5555, player.offset(18, 0, 30));
				addPlayer(mc.getConnection(), "Steve", 0x55AAFF, player.offset(-260, 0, 300));
			});
			context.waitTicks(5);
			shot(context, "locator_waypoints");

			context.getInput().lookAt(player.offset(18, 1, 30));
			context.waitTicks(5);
			shot(context, "locator_name_player");

			context.runOnClient(mc -> MapFeature.INSTANCE.config().locatorHeads = false);
			context.waitTicks(2);
			shot(context, "locator_dots");
			context.runOnClient(mc -> MapFeature.INSTANCE.config().locatorHeads = true);
		}
	}

	private static void addPlayer(ClientPacketListener connection, String name, int color, BlockPos pos) {
		UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
		try {
			Field field = ClientPacketListener.class.getDeclaredField("playerInfoMap");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<UUID, PlayerInfo> players = (Map<UUID, PlayerInfo>) field.get(connection);
			players.put(uuid, new PlayerInfo(new GameProfile(uuid, name), false));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Icon icon = new Icon();
		icon.color = Optional.of(color);
		connection.getWaypointManager().trackWaypoint(TrackedWaypoint.setPosition(uuid, icon, pos));
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
