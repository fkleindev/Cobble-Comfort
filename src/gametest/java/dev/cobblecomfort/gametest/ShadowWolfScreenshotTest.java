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

import java.util.HashSet;
import java.util.Set;

/** The shadow wolf at night: fading into the shadows, howling, bowing, stepping through the shadows to your other side. */
public class ShadowWolfScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		WardrobeFeature wardrobe = WardrobeFeature.INSTANCE;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestShaders.enableIfRequested(context);
			world.getServer().runCommand("time set 18000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("weather clear");
			context.runOnClient(mc -> {
				wardrobe.config().announce = false;
				wardrobe.unlockAllCompanions();
				wardrobe.wearCompanion(Companion.SHADOW_WOLF_SOUL);
				wardrobe.nameCompanion("Shade");
				dev.cobblecomfort.features.lookinfo.LookInfoFeature.INSTANCE.config().enabled = false;
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setYRot(180);
				mc.player.setXRot(20);
			});
			Set<String> seen = new HashSet<>();
			int steps = 0;
			Vec3 last = null;
			for (int i = 0; i < 400; i++) {
				context.waitTicks(5);
				String action = context.computeOnClient(mc -> wardrobe.companionAction());
				Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
				if (last != null && at != null && at.distanceTo(last) > 1.0) {
					steps++;
					LOGGER.info("Shadow step {} -> {}", last, at);
				}
				last = at;
				String kind = action.split(" ")[0];
				if (!kind.equals("0") && seen.add(kind)) {
					LOGGER.info("Shadow wolf action {} (shade, kind) at tick {}", action, i * 5);
					context.waitTicks(kind.equals("9") ? 6 : 30);
					LOGGER.info("Screenshot -> {}", context.takeScreenshot("shadow_wolf_action_" + kind));
				}
				if (i == 60) {
					LOGGER.info("Screenshot -> {} ({})", context.takeScreenshot("shadow_wolf_night"), action);
				}
				if (seen.size() >= 4 && steps > 0) {
					break;
				}
			}
			LOGGER.info("Shadow wolf actions seen {}, shadow steps {}", seen, steps);
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(null);
				wardrobe.nameCompanion("");
				dev.cobblecomfort.features.lookinfo.LookInfoFeature.INSTANCE.config().enabled = true;
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
