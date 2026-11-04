package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.atmosphere.AtmosphereFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Atmosphere: breath and snow on the player in snowfall, wading rings and wet prints, fireflies at night. */
public class AtmosphereScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("time set noon");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));

			// Snowfall in a snowy biome: the player and a cow and villager breathe, snow settles on the player.
			world.getServer().runCommand("execute at @p run fillbiome ~-12 -64 ~-12 ~12 -40 ~12 minecraft:snowy_plains");
			world.getServer().runCommand("execute at @p run summon minecraft:cow ~2.5 ~ ~1.5 {NoAI:1b,Rotation:[200f,0f]}");
			world.getServer().runCommand("execute at @p run summon minecraft:villager ~-2 ~ ~1.5 {NoAI:1b,Rotation:[160f,0f]}");
			world.getServer().runCommand("weather rain");
			context.waitTicks(60);
			int most = 0;
			for (int i = 0; i < 6; i++) {
				shot(context, "atmosphere_breath_" + i);
				for (int t = 0; t < 9; t++) {
					context.waitTick();
					most = Math.max(most, motes(context, "PUFF"));
				}
			}
			LOGGER.info("Atmosphere: most breath puffs at once: {}", most);
			// Close up from the side, against the dark ground, the moment a breath comes out.
			world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 90 0");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			for (int t = 0; t < 80 && motes(context, "PUFF") == 0; t++) {
				context.waitTick();
			}
			context.waitTicks(6);
			shot(context, "atmosphere_breath_close");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 0 0");
			// Long enough for a good layer of snow.
			context.waitTicks(900);
			LOGGER.info("Atmosphere: snow on the player: {}", snow(context));
			shot(context, "atmosphere_snow");
			context.getInput().holdKeyFor(options -> options.keyJump, 2);
			context.waitTicks(1);
			LOGGER.info("Atmosphere: snow after jumping: {}", snow(context));
			shot(context, "atmosphere_snow_shaken");
			context.waitTicks(30);
			shot(context, "atmosphere_snow_after");

			// A shallow pool in plains: wading rings, then wet prints on the grass.
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("tp @e[type=!player] ~ -200 ~");
			world.getServer().runCommand("execute at @p run fillbiome ~-12 -64 ~-12 ~12 -40 ~12 minecraft:plains");
			world.getServer().runCommand("execute at @p run fill ~-12 ~ ~-12 ~12 ~ ~24 minecraft:air replace minecraft:snow");
			world.getServer().runCommand("execute at @p run fill ~-3 ~-1 ~2 ~3 ~-1 ~9 minecraft:water");
			world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 0 20");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(20);
			context.getInput().holdKeyFor(options -> options.keyUp, 22);
			shot(context, "atmosphere_wading");
			// Straight onto the grass past the edge (walking up a block out of water needs a jump), then a few wet steps.
			context.getInput().holdKeyFor(options -> options.keyUp, 25);
			world.getServer().runCommand("execute at @p run tp @p ~ -60 10.5");
			context.getInput().holdKeyFor(options -> options.keyUp, 20);
			context.waitTicks(5);
			String at = context.computeOnClient(mc -> mc.player.position().toString());
			LOGGER.info("Atmosphere: out of the pool at {}", at);
			world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 180 50");
			context.waitTicks(5);
			shot(context, "atmosphere_wet_prints");

			// A clear night over a meadow.
			world.getServer().runCommand("execute at @p run fillbiome ~-12 -64 ~-12 ~12 -40 ~12 minecraft:meadow");
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("execute at @p run tp @p ~ ~ ~ 0 10");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(160);
			LOGGER.info("Atmosphere: fireflies: {} (dark {}, raining {}, biome {})", motes(context, "GLOW"),
					context.computeOnClient(mc -> mc.level.isDarkOutside()), context.computeOnClient(mc -> mc.level.isRaining()),
					context.computeOnClient(mc -> mc.level.getBiome(mc.player.blockPosition()).getRegisteredName()));
			shot(context, "atmosphere_fireflies");
		}
	}

	/** How many motes of a shape are alive (read through reflection: the particle system is internal). */
	private static int motes(ClientGameTestContext context, String shape) {
		return context.computeOnClient(mc -> {
			try {
				Field field = AtmosphereFeature.class.getDeclaredField("motes");
				field.setAccessible(true);
				Object motes = field.get(AtmosphereFeature.INSTANCE);
				Class<?> shapes = Class.forName("dev.cobblecomfort.features.atmosphere.Motes$Shape");
				Method count = motes.getClass().getDeclaredMethod("count", shapes);
				count.setAccessible(true);
				for (Object constant : shapes.getEnumConstants()) {
					if (((Enum<?>) constant).name().equals(shape)) {
						return (int) count.invoke(motes, constant);
					}
				}
				throw new AssertionError("No shape " + shape);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static float snow(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			try {
				Method snowOn = AtmosphereFeature.class.getDeclaredMethod("snowOn", int.class);
				snowOn.setAccessible(true);
				return (float) snowOn.invoke(AtmosphereFeature.INSTANCE, mc.player.getId());
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
