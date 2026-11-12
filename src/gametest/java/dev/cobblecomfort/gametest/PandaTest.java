package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.Companion;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The baby panda: waddling along, then trotting over to bamboo nearby to munch some. */
public class PandaTest implements FabricClientGameTest {
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
				wardrobe.wearCompanion(Companion.PANDA_CLASSIC);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(30);
			});
			context.waitTicks(120);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("panda_idle"));
			Vec3 me = context.computeOnClient(mc -> mc.player.position());
			world.getServer().runCommand("execute as @p at @s run setblock ~2 ~ ~2 bamboo");
			world.getServer().runCommand("execute as @p at @s run setblock ~2 ~1 ~2 bamboo");
			Vec3 bamboo = me.add(2, 0, 2);
			double closest = 99;
			int i = 0;
			for (; i < 90 && closest > 0.8; i++) {
				context.waitTicks(10);
				Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
				closest = Math.min(closest, Math.hypot(at.x - bamboo.x, at.z - bamboo.z));
			}
			LOGGER.info("Panda: came within {} of the bamboo after {} s", closest, i / 2);
			context.waitTicks(50);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("panda_bamboo"));
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
