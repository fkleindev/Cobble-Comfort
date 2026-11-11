package dev.cobblecomfort.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Switches on a shader pack for the screenshots when the tests run with Iris and a pack:
 * {@code -Pextra_runtime_mod=<iris jar>;<sodium jar> -Ptest_shaderpack=<pack zip>}. Does nothing otherwise.
 */
@Environment(EnvType.CLIENT)
final class TestShaders {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	private TestShaders() {
	}

	static void enableIfRequested(ClientGameTestContext context) {
		String pack = System.getProperty("cobblecomfort.test.shaderpack");
		if (pack == null || !FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}
		context.runOnClient(mc -> {
			try {
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				Path source = Path.of(pack);
				Path dir = (Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
				Files.createDirectories(dir);
				Files.copy(source, dir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				Object config = iris.getMethod("getIrisConfig").invoke(null);
				config.getClass().getMethod("setShaderPackName", String.class).invoke(config, source.getFileName().toString());
				config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
				config.getClass().getMethod("save").invoke(config);
				iris.getMethod("reload").invoke(null);
				LOGGER.info("Shader pack on: {}", source.getFileName());
			} catch (Exception e) {
				throw new IllegalStateException("Could not switch on the shader pack", e);
			}
		});
		context.waitTicks(40);
	}
}
