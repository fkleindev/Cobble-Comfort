package dev.cobblecomfort.gametest;

import com.mojang.authlib.GameProfile;
import dev.cobblecomfort.features.petplush.PlushLook;
import dev.cobblecomfort.features.wardrobe.Companion;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.RemotePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/** Two players' companions meeting: walking up to each other, sniffing, hopping with hearts, maybe playing tag. */
public class CompanionMeetScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		WardrobeFeature wardrobe = WardrobeFeature.INSTANCE;
		UUID friend = UUID.nameUUIDFromBytes("Friend".getBytes());
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			context.runOnClient(mc -> {
				wardrobe.config().announce = false;
				wardrobe.unlockAllCompanions();
				wardrobe.wearCompanion(Companion.RED_PANDA_CLASSIC);
				dev.cobblecomfort.features.lookinfo.LookInfoFeature.INSTANCE.config().enabled = false;
				// A friend standing three blocks to the side, with a turtle.
				TestLooks.LOOKS.put(friend, new PlushLook(null, null, List.of(), dev.cobblecomfort.features.belt.BeltGear.NONE,
						List.of(), null, null, null, null, null, null, null, null, null, null,
						new Companion.Look(Companion.TURTLE_GREEN, "Shelly", Companion.Accessory.BOW, 9)));
				RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(friend, "Friend"));
				other.snapTo(mc.player.getX() + 3, mc.player.getY(), mc.player.getZ(), 90, 0);
				other.setYHeadRot(90);
				other.yBodyRot = 90;
				other.setId(-2001);
				mc.level.addEntity(other);
				// Looking at both from the side and a little above.
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setYRot(0);
				mc.player.setXRot(35);
			});
			int maxMeetings = 0;
			int shot = 0;
			for (int i = 0; i < 60; i++) {
				context.waitTicks(5);
				int meetings = context.computeOnClient(mc -> wardrobe.companionMeetings());
				maxMeetings = Math.max(maxMeetings, meetings);
				if (meetings > 0 && i % 4 == 0 && shot < 6) {
					LOGGER.info("Screenshot {} -> {}", "companion_meet_" + shot, context.takeScreenshot("companion_meet_" + shot));
					shot++;
				}
			}
			LOGGER.info("Companion meetings at most: {}, shots {}", maxMeetings, shot);
			context.runOnClient(mc -> {
				TestLooks.LOOKS.clear();
				wardrobe.wearCompanion(null);
				dev.cobblecomfort.features.lookinfo.LookInfoFeature.INSTANCE.config().enabled = true;
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
