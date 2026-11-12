package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.atmosphere.AtmosphereFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Butterflies over a patch of flowers and dragonflies over a pond, by day; gone at night. */
public class InsectsTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		AtmosphereFeature atmosphere = AtmosphereFeature.INSTANCE;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 3000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("weather clear");
			// Flowers all round in front, a pond further off to the side.
			String[] flowers = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "azure_bluet"};
			for (int i = 0; i < 160; i++) {
				int x = (i * 7919) % 15 - 7, z = (i * 104729) % 12 + 2;
				world.getServer().runCommand("execute as @p at @s run setblock ~" + x + " ~ ~" + z + " " + flowers[i % flowers.length]);
			}
			world.getServer().runCommand("execute as @p at @s run fill ~6 ~-1 ~-12 ~14 ~-1 ~-4 water");
			world.getServer().runCommand("execute as @p at @s run fill ~8 ~ ~-10 ~8 ~ ~-10 lily_pad");
			world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 15");
			context.waitTicks(300);
			LOGGER.info("Insects: {} butterflies, {} dragonflies by day", context.computeOnClient(mc -> atmosphere.insects(false)),
					context.computeOnClient(mc -> atmosphere.insects(true)));
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("insects_butterflies"));
			String where = context.computeOnClient(mc -> atmosphere.butterflies().stream()
					.map(v -> String.format(java.util.Locale.ROOT, "(%.1f %.1f %.1f)", v.x - mc.player.getX(), v.y - mc.player.getY(), v.z - mc.player.getZ())).toList().toString());
			LOGGER.info("Insects: butterflies at {}", where);
			for (int i = 0; i < 3; i++) {
				// Right next to one, looking at it.
				net.minecraft.world.phys.Vec3 bug = context.computeOnClient(mc -> atmosphere.butterflies().isEmpty() ? null : atmosphere.butterflies().get(0));
				if (bug != null) {
					context.runOnClient(mc -> {
						mc.player.setPos(bug.x + 1.0, bug.y - 1.3, bug.z - 1.0);
						mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, bug);
					});
				}
				context.waitTicks(2);
				LOGGER.info("Screenshot -> {}", context.takeScreenshot("insects_close" + i));
				context.waitTicks(40);
			}
			world.getServer().runCommand("execute as @p at @s run tp @s ~10 ~ ~-1 180 35");
			context.waitTicks(100);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("insects_dragonflies"));
			world.getServer().runCommand("time set 14000");
			context.waitTicks(80);
			LOGGER.info("Insects: {} butterflies, {} dragonflies at night", context.computeOnClient(mc -> atmosphere.insects(false)),
					context.computeOnClient(mc -> atmosphere.insects(true)));
		}
	}
}
