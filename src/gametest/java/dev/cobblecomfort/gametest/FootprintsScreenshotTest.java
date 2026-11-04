package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.footprints.FootprintsFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Footprints: the player walks over snow into sand, animals roam, then a look back at the trail. */
public class FootprintsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule spawn_mobs false");
			// Sand everywhere, the northern half under a thin snow cover.
			world.getServer().runCommand("execute at @p run fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:sand");
			world.getServer().runCommand("execute at @p run fill ~-14 ~ ~-14 ~14 ~ ~-1 minecraft:snow[layers=2]");
			world.getServer().runCommand("execute at @p run fill ~-14 ~ ~0 ~14 ~3 ~14 minecraft:air");
			world.getServer().runCommand("execute at @p run summon minecraft:cow ~6 ~ ~6");
			world.getServer().runCommand("execute at @p run summon minecraft:wolf ~-6 ~ ~-6");
			world.getServer().runCommand("execute at @p run summon minecraft:chicken ~-5 ~ ~5");
			world.getServer().runCommand("execute at @p run summon minecraft:horse ~6 ~ ~-6");
			world.getServer().runCommand("execute at @p run tp @p ~ ~0.25 ~-12 0 0");
			// Until the advancement toasts are gone.
			context.waitTicks(100);

			// Walk south over the snow and on into the sand, then a few steps sideways.
			context.getInput().holdKeyFor(options -> options.keyUp, 70);
			context.getInput().holdKeyFor(options -> options.keyRight, 15);
			context.waitTicks(40);

			LOGGER.info("Footprints after walking: {}", prints());

			// Look back at the trail, then straight down on it from above.
			world.getServer().runCommand("gamerule fall_damage false");
			world.getServer().runCommand("execute at @p run tp @p ~2 ~2.5 ~3 170 35");
			context.waitTicks(10);
			shot(context, "footprints_trail");
			world.getServer().runCommand("execute at @p run tp @p ~-2 ~10 ~-9 180 90");
			context.waitTicks(10);
			shot(context, "footprints_above");

			// Animals wander about and leave their own prints.
			context.waitTicks(300);
			LOGGER.info("Footprints after the animals: {}", prints());
			shot(context, "footprints_animals");
		}
	}

	private static int prints() {
		try {
			java.lang.reflect.Field field = FootprintsFeature.class.getDeclaredField("prints");
			field.setAccessible(true);
			return ((java.util.Collection<?>) field.get(FootprintsFeature.INSTANCE)).size();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
