package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.lightoverlay.LightOverlayFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Light overlay: yellow crosses outdoors with a lit patch and blocks nothing spawns on, red ones in a closed room. */
public class LightOverlayScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			int x = p.getX();
			int y = p.getY();
			int z = p.getZ();

			// Outdoors: a torch further away, a glass block, a bottom slab and a carpet in front of the player.
			cmd(world, "setblock " + (x + 2) + " " + y + " " + (z + 16) + " minecraft:torch");
			cmd(world, "setblock " + (x - 2) + " " + (y - 1) + " " + (z + 3) + " minecraft:glass");
			cmd(world, "setblock " + (x - 1) + " " + y + " " + (z + 3) + " minecraft:stone_slab");
			cmd(world, "setblock " + x + " " + y + " " + (z + 3) + " minecraft:white_carpet");
			cmd(world, "tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 30");
			context.runOnClient(mc -> LightOverlayFeature.INSTANCE.config().active = true);
			context.waitTicks(3);
			shot(context, "light_overlay_fade_in");
			context.waitTicks(20);
			shot(context, "light_overlay_outdoors");

			context.runOnClient(mc -> LightOverlayFeature.INSTANCE.config().numbers = true);
			context.waitTicks(10);
			shot(context, "light_overlay_numbers");
			context.runOnClient(mc -> LightOverlayFeature.INSTANCE.config().numbers = false);

			// A closed stone room with one torch: red crosses, a lit circle around the torch.
			int rz = z + 30;
			cmd(world, "fill " + (x - 16) + " " + (y - 1) + " " + (rz - 16) + " " + (x + 16) + " " + (y + 5) + " " + (rz + 16) + " minecraft:stone hollow");
			cmd(world, "setblock " + (x + 7) + " " + y + " " + (rz + 2) + " minecraft:torch");
			cmd(world, "tp @a " + (x + 0.5) + " " + (y + 3) + " " + (rz - 14.5) + " 0 30");
			context.waitTicks(40);
			shot(context, "light_overlay_room");

			// Placing a second torch: the crosses around it fade out.
			cmd(world, "setblock " + (x - 4) + " " + y + " " + (rz - 6) + " minecraft:torch");
			context.waitTicks(4);
			shot(context, "light_overlay_torch_fading");
			context.waitTicks(20);
			shot(context, "light_overlay_torch_done");

			context.runOnClient(mc -> LightOverlayFeature.INSTANCE.config().active = false);
			context.waitTicks(20);
		}
	}

	private static void cmd(TestSingleplayerContext world, String command) {
		world.getServer().runCommand(command);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
