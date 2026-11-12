package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.atmosphere.AtmosphereFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dripping water (a player out of the water, drying off; a roof edge in the rain, a pond below) and dust on landing
 * (sheep dropping onto sand from different heights). Each in its own spot of the flat world.
 */
public class DripsTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		AtmosphereFeature atmosphere = AtmosphereFeature.INSTANCE;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("effect give @a resistance 600 4 true");
			context.waitTicks(100);

			// Into the water and out again, seen from the front: dripping, drying.
			world.getServer().runCommand("fill 18 -62 -1 22 -61 3 water");
			world.getServer().runCommand("tp @p 20 -61 1");
			context.waitTicks(40);
			world.getServer().runCommand("tp @p 20 -60 -3 0 10");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			LOGGER.info("Drips: wet {} just out of the water", (Object) context.computeOnClient(mc -> (Float) atmosphere.wetness(mc.player.getId())));
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("drips_player"));
			context.waitTicks(300);
			LOGGER.info("Drips: wet {} after 15 s", (Object) context.computeOnClient(mc -> (Float) atmosphere.wetness(mc.player.getId())));
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// A roof in the rain with a pond under its edge.
			world.getServer().runCommand("fill -23 -57 0 -17 -57 4 oak_planks");
			world.getServer().runCommand("fill -23 -61 5 -17 -61 6 water");
			world.getServer().runCommand("tp @p -20 -60 9 180 8");
			world.getServer().runCommand("weather rain");
			context.waitTicks(200);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("drips_roof"));
			world.getServer().runCommand("weather clear");
			context.waitTicks(100);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("drips_after_rain"));

			// Sheep dropping onto sand.
			world.getServer().runCommand("fill -3 -61 -26 3 -61 -18 sand");
			world.getServer().runCommand("tp @p 0 -60 -14 180 25");
			context.waitTicks(20);
			for (int height : new int[]{3, 10}) {
				world.getServer().runCommand("summon sheep 0 " + (-60 + height) + " -21");
				context.waitTicks(height == 3 ? 11 : 24);
				LOGGER.info("Screenshot -> {}", context.takeScreenshot("dust_" + height));
				context.waitTicks(40);
			}
		}
	}
}
