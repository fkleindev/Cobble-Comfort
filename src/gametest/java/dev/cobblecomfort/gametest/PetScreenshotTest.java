package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.pets.PetEntry;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.storage.StorageScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pet memory: a tamed, named wolf and a ridden horse are remembered; the wolf dies and leaves a grave. */
public class PetScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			String wolfAt = (player.getX() + 6) + " " + player.getY() + " " + (player.getZ() + 4);
			String horseAt = (player.getX() - 8) + " " + player.getY() + " " + (player.getZ() - 5);
			world.getServer().runCommand("summon wolf " + wolfAt + " {CustomName:\"Bello\",Sitting:1b,NoAI:1b}");
			world.getServer().runCommand("data modify entity @e[type=wolf,limit=1] Owner set from entity @p UUID");
			world.getServer().runCommand("summon horse " + horseAt + " {Tame:1b,CustomName:\"Blitz\"}");
			// A stranger's horse that was never ridden stays out.
			world.getServer().runCommand("summon horse " + (player.getX() + 10) + " " + player.getY() + " " + (player.getZ() - 10) + " {Tame:1b}");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(20);
			world.getServer().runCommand("ride @p mount @e[type=horse,name=Blitz,limit=1]");
			context.waitTicks(20);
			world.getServer().runCommand("ride @p dismount");
			world.getServer().runCommand("tp @a " + player.getX() + " " + player.getY() + " " + player.getZ());
			context.waitTicks(20);
			log(context, "after taming and riding");

			context.setScreen(() -> new MapScreen(null, player.getX() + 6.5, player.getZ() + 4.5, null));
			context.waitTicks(10);
			double[] center = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(center[0], center[1]);
			context.waitTicks(10);
			shot(context, "pets_map_wolf");
			context.setScreen(() -> null);

			world.getServer().runCommand("kill @e[type=wolf]");
			context.waitTicks(40);
			log(context, "after the wolf died");
			shot(context, "pets_death_toast");
			// Let the popup go, it covers the tabs.
			context.waitTicks(120);

			context.setScreen(() -> new StorageScreen(null, ""));
			context.waitTicks(5);
			double[] tab = context.computeOnClient(mc -> {
				double scale = mc.getWindow().getGuiScale();
				int width = mc.getWindow().getGuiScaledWidth();
				int panel = Math.min(380, width - 20);
				int left = (width - panel) / 2;
				// Four tabs of 62 px; the pets tab is the last one.
				return new double[]{(left + panel - 8 - 62 + 4 + 29) * scale, (10 + 6 + 10) * scale};
			});
			context.getInput().setCursorPos(tab[0], tab[1]);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			double[] row = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0, (10 + 30 + 8) * mc.getWindow().getGuiScale()});
			context.getInput().setCursorPos(row[0], row[1]);
			context.waitTicks(10);
			shot(context, "pets_tab");
			context.setScreen(() -> null);
		}
	}

	private static void log(ClientGameTestContext context, String step) {
		String text = context.computeOnClient(mc -> {
			StringBuilder builder = new StringBuilder();
			for (PetEntry pet : PetFeature.INSTANCE.store().all()) {
				builder.append(pet.type).append(" '").append(pet.name).append("' dead=").append(pet.dead())
						.append(" sitting=").append(pet.sitting).append(" speed=").append(String.format("%.1f", pet.speed))
						.append(" msg='").append(pet.deathMessage).append("'; ");
			}
			return builder.isEmpty() ? "none" : builder.toString();
		});
		LOGGER.info("Pets {}: {} (map {})", step, text, MapFeature.INSTANCE.isEnabled());
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
