package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.map.screen.MapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Saved cave layers: the automatic layer underground, the depth slider, and the Nether roof and below it. */
public class CaveLayersScreenshotTest implements FabricClientGameTest {
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
			world.getServer().runCommand("tp @a ~ 8 ~");
			context.waitTicks(400);
			shot(context, "layers_minimap_cave");

			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(20);
			shot(context, "layers_auto");
			press(context, InputConstants.KEY_PAGEDOWN, 3);
			shot(context, "layers_deep");
			press(context, InputConstants.KEY_PAGEUP, 30);
			shot(context, "layers_surface");
			press(context, InputConstants.KEY_PAGEDOWN, 4);
			shot(context, "layers_high");
			press(context, InputConstants.KEY_HOME, 1);
			shot(context, "layers_back_auto");
			context.setScreen(() -> null);

			world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 140 0");
			context.waitTicks(300);
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(20);
			shot(context, "layers_nether_roof");
			context.setScreen(() -> null);
			world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 40 0");
			context.waitTicks(200);
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(20);
			shot(context, "layers_nether_below");
			context.setScreen(() -> null);
			context.runOnClient(mc -> dev.cobblecomfort.features.map.MapFeature.INSTANCE.world().saveAsync());
			context.waitTicks(60);
			String files = context.computeOnClient(mc -> listFiles(ConfigManager.dir().resolve("map")));
			LOGGER.info("Map files:\n{}", files);
		}
	}

	private static String listFiles(Path root) {
		StringBuilder out = new StringBuilder();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(Files::isRegularFile).forEach(p -> {
				try {
					out.append(root.relativize(p)).append(' ').append(Files.size(p)).append('\n');
				} catch (IOException ignored) {
				}
			});
		} catch (IOException e) {
			return e.toString();
		}
		return out.toString();
	}

	private static void press(ClientGameTestContext context, int key, int times) {
		for (int i = 0; i < times; i++) {
			context.getInput().pressKey(key);
		}
		context.waitTicks(20);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
