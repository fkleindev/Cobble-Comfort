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

/**
 * A companion resting while its player stands still: it stays put when the player only looks around, keeps a little
 * distance, and now and then strolls over to another place nearby.
 */
public class CompanionRestTest implements FabricClientGameTest {
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
				wardrobe.wearCompanion(Companion.RED_PANDA_CLASSIC);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(40);
			});
			context.waitTicks(100);
			Vec3 settled = context.computeOnClient(mc -> wardrobe.companionPosition());
			Vec3 me = context.computeOnClient(mc -> mc.player.position());
			LOGGER.info("Rest: settled at {}, {} blocks from the player", settled, horizontal(settled, me));
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("companion_rest"));
			// Looking round on the spot.
			double moved = 0;
			for (int i = 0; i < 40; i++) {
				float yaw = i * 23f;
				context.runOnClient(mc -> {
					mc.player.setYRot(yaw);
					mc.player.setYHeadRot(yaw);
					mc.player.yBodyRot = yaw;
				});
				context.waitTicks(3);
				Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
				moved = Math.max(moved, horizontal(at, settled));
			}
			LOGGER.info("Rest: moved at most {} while the player looked round", moved);
			// Standing still for a while: strolling now and then.
			int strolls = 0;
			Vec3 last = context.computeOnClient(mc -> wardrobe.companionPosition());
			double nearest = 99, farthest = 0;
			for (int i = 0; i < 60; i++) {
				context.waitTicks(10);
				Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
				double d = horizontal(at, me);
				nearest = Math.min(nearest, d);
				farthest = Math.max(farthest, d);
				if (horizontal(at, last) > 0.3) {
					strolls++;
				}
				last = at;
			}
			LOGGER.info("Rest: {} samples with strolling, distance {} to {}", strolls, nearest, farthest);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("companion_rest_after"));
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return a == null || b == null ? -1 : Math.hypot(a.x - b.x, a.z - b.z);
	}
}
