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
 * A companion's own doings: sniffing at an item on the ground, warming itself by a campfire, begging while its player
 * eats, galloping after them when they sprint.
 */
public class CompanionBehaviourTest implements FabricClientGameTest {
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
				mc.player.setXRot(35);
			});
			context.waitTicks(120);
			Vec3 me = context.computeOnClient(mc -> mc.player.position());

			// An apple on the ground nearby.
			world.getServer().runCommand("execute as @p at @s run summon item ~2.5 ~ ~-2 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:32767}");
			Vec3 apple = me.add(2.5, 0, -2);
			double closest = 99;
			for (int i = 0; i < 80 && closest > 0.8; i++) {
				context.waitTicks(10);
				closest = Math.min(closest, horizontal(context.computeOnClient(mc -> wardrobe.companionPosition()), apple));
			}
			LOGGER.info("Behaviour: came within {} of the apple", closest);
			context.waitTicks(20);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("companion_curious"));
			world.getServer().runCommand("kill @e[type=item]");

			// A campfire.
			world.getServer().runCommand("execute as @p at @s run setblock ~-3 ~ ~1 campfire");
			Vec3 fire = me.add(-3, 0, 1);
			context.waitTicks(200);
			LOGGER.info("Behaviour: {} from the campfire after 10 s", horizontal(context.computeOnClient(mc -> wardrobe.companionPosition()), fire));
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("companion_campfire"));
			world.getServer().runCommand("execute as @p at @s run setblock ~-3 ~ ~1 air");

			// Eating: it comes begging in front.
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:golden_apple 16");
			context.waitTicks(10);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			context.waitTicks(28);
			LOGGER.info("Behaviour: {} from the player while they eat", horizontal(context.computeOnClient(mc -> wardrobe.companionPosition()),
					context.computeOnClient(mc -> mc.player.position())));
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("companion_begging"));
			context.waitTicks(30);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));
			context.waitTicks(40);

			// Sprinting: it gallops after them, bounding.
			context.runOnClient(mc -> {
				mc.options.keyUp.setDown(true);
				mc.options.keySprint.setDown(true);
			});
			double low = 99, high = -99, sum = 0;
			int samples = 0;
			for (int i = 0; i < 80; i++) {
				context.waitTicks(1);
				if (i > 30) {
					Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
					Vec3 player = context.computeOnClient(mc -> mc.player.position());
					low = Math.min(low, at.y);
					high = Math.max(high, at.y);
					sum += horizontal(at, player);
					samples++;
				}
				if (i == 60) {
					LOGGER.info("Screenshot -> {}", context.takeScreenshot("companion_gallop"));
				}
			}
			context.runOnClient(mc -> {
				mc.options.keyUp.setDown(false);
				mc.options.keySprint.setDown(false);
			});
			LOGGER.info("Behaviour: galloping {} behind, bounding {} high", sum / samples, high - low);
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
