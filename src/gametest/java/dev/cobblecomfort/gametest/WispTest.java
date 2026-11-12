package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.Companion;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shadow wisp: gliding along (faint in daylight), slipping into a wall next to it while its player rests, nearly
 * solid in the dark, flickering in the Nether.
 */
public class WispTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		WardrobeFeature wardrobe = WardrobeFeature.INSTANCE;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			context.runOnClient(mc -> {
				wardrobe.config().announce = false;
				wardrobe.unlockAllCompanions();
				wardrobe.wearCompanion(Companion.WISP_SHADOW);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(25);
			});
			context.waitTicks(120);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("wisp_day"));

			// Walls all round, close by: resting, it slips into one of them now and then.
			world.getServer().runCommand("execute as @p at @s run fill ~-3 ~ ~-3 ~3 ~3 ~3 stone hollow");
			world.getServer().runCommand("execute as @p at @s run fill ~-2 ~ ~-2 ~2 ~3 ~2 air");
			world.getServer().runCommand("execute as @p at @s run fill ~-3 ~4 ~-3 ~3 ~4 ~3 air");
			int inWall = 0;
			for (int i = 0; i < 80; i++) {
				context.waitTicks(10);
				boolean solid = context.computeOnClient(mc -> {
					Vec3 at = wardrobe.companionPosition();
					BlockPos pos = BlockPos.containing(at.x, at.y + 0.3, at.z);
					return !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
				});
				if (solid && inWall++ == 3) {
					LOGGER.info("Screenshot -> {}", context.takeScreenshot("wisp_wall"));
				}
			}
			LOGGER.info("Wisp: {} of 80 samples inside a wall", inWall);

			// Dark: a roof over it.
			world.getServer().runCommand("execute as @p at @s run fill ~-3 ~4 ~-3 ~3 ~4 ~3 stone");
			context.waitTicks(200);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("wisp_dark"));

			// The Nether.
			world.getServer().runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
			world.getServer().runCommand("effect give @p slow_falling 30");
			context.waitTicks(60);
			world.getServer().runCommand("execute in minecraft:the_nether run fill -4 64 -4 4 69 4 air");
			world.getServer().runCommand("execute in minecraft:the_nether run fill -4 63 -4 4 63 4 netherrack");
			world.getServer().runCommand("execute in minecraft:the_nether run tp @p 0 64 0 0 25");
			context.waitTicks(200);
			world.getConnection().waitForChunksRender();
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("wisp_nether"));
			context.waitTicks(3);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("wisp_nether2"));
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
