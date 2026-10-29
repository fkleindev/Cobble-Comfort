package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.phantom.PhantomFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phantom warning: the info line from the second day, the countdown in the last day, the popup at three days. */
public class PhantomScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			context.waitTicks(60);
			log(context, "fresh world");

			setRest(context, world, 30000);
			shot(context, "phantom_day2");
			setRest(context, world, 60000);
			shot(context, "phantom_soon");
			setRest(context, world, PhantomFeature.THRESHOLD - 40);
			context.waitTicks(60);
			log(context, "after the threshold");
			shot(context, "phantom_active");
		}
	}

	/** Sets the statistic on the server and has the client ask for it, as it does every few minutes. */
	private static void setRest(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), ticks);
		});
		context.runOnClient(mc -> mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS)));
		context.waitTicks(10);
		log(context, "set to " + ticks);
	}

	private static void log(ClientGameTestContext context, String step) {
		int ticks = context.computeOnClient(mc -> PhantomFeature.INSTANCE.ticks());
		LOGGER.info("Phantom {}: ticks={}", step, ticks);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
