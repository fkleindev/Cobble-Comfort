package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.atmosphere.AtmosphereFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Birds in the sky: every kind up close, and flocks coming by on their own. Screenshots only. */
public class BirdsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestShaders.enableIfRequested(context);
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("weather clear");
			context.runOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(-8);
				AtmosphereFeature.INSTANCE.config().birdAmount = 300;
				AtmosphereFeature.INSTANCE.showcaseBirds(-1);
			});
			context.waitTicks(3);
			shot(context, "birds_showcase");
			int kinds = context.computeOnClient(mc -> AtmosphereFeature.INSTANCE.birdKinds());
			for (int i = 0; i < kinds; i++) {
				int kind = i;
				context.runOnClient(mc -> AtmosphereFeature.INSTANCE.showcaseBirds(kind));
				context.waitTicks(2);
				shot(context, "birds_" + context.computeOnClient(mc -> AtmosphereFeature.INSTANCE.birdKind(kind)));
			}

			// The showcase hangs on; a fresh world for flocks on their own.
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			context.runOnClient(mc -> {
				mc.player.setXRot(-25);
			});
			int seen = 0;
			for (int i = 0; i < 30; i++) {
				context.waitTicks(40);
				seen = Math.max(seen, context.computeOnClient(mc -> AtmosphereFeature.INSTANCE.birdFlocks()));
			}
			LOGGER.info("Bird flocks in the air at most: {}", seen);
			context.waitTicks(100);
			int with = context.computeOnClient(mc -> mc.getFps());
			LOGGER.info("FPS with birds: {}", with);
			context.runOnClient(mc -> AtmosphereFeature.INSTANCE.config().birds = false);
			context.waitTicks(100);
			int without = context.computeOnClient(mc -> mc.getFps());
			LOGGER.info("FPS without birds: {}", without);
			context.runOnClient(mc -> AtmosphereFeature.INSTANCE.config().birds = true);
			shot(context, "birds_sky");
			context.runOnClient(mc -> {
				AtmosphereFeature.INSTANCE.config().birdAmount = 100;
			});
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
