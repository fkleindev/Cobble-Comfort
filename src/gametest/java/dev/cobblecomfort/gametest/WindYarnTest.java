package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.Companion;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ball of yarn (its thread laid out as it rolls after its player, wound up again as it rests, a cat watching it)
 * and the little whirlwind (spinning along, running wild after a sprinting player).
 */
public class WindYarnTest implements FabricClientGameTest {
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
				wardrobe.wearCompanion(Companion.YARN_RED);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(40);
			});
			context.waitTicks(120);
			world.getServer().runCommand("execute as @p at @s run summon cat ~-2 ~ ~3 {NoAI:1b,variant:\"minecraft:red\"}");

			// Walking off: the thread unwinds behind it.
			context.runOnClient(mc -> mc.options.keyUp.setDown(true));
			context.waitTicks(70);
			context.runOnClient(mc -> mc.options.keyUp.setDown(false));
			context.waitTicks(15);
			context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180));
			context.waitTicks(5);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("yarn_thread"));
			// Resting: it winds itself up again.
			context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180));
			context.waitTicks(140);
			context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180));
			context.waitTicks(5);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("yarn_wound"));
			// Back to the cat.
			world.getServer().runCommand("execute as @e[type=cat] at @s run tp @p ~2 ~ ~-1 facing entity @s");
			context.waitTicks(80);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("yarn_cat"));
			String cats = context.computeOnClient(mc -> {
				net.minecraft.world.phys.Vec3 ball = wardrobe.companionPosition();
				return mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.feline.Cat.class, mc.player.getBoundingBox().inflate(10)).stream()
						.map(c -> "cat head " + net.minecraft.util.Mth.wrapDegrees(c.getYHeadRot()) + ", towards the ball "
								+ net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(ball.z - c.getZ(), ball.x - c.getX())) - 90)
								+ ", " + Math.hypot(ball.x - c.getX(), ball.z - c.getZ()) + " away").toList().toString();
			});
			LOGGER.info("Yarn: {}", cats);

			// The whirlwind, walking and sprinting.
			world.getServer().runCommand("kill @e[type=cat]");
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(Companion.WIND_AUTUMN);
				mc.player.setXRot(25);
			});
			context.waitTicks(80);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("wind_still"));
			context.runOnClient(mc -> {
				mc.options.keyUp.setDown(true);
				mc.options.keySprint.setDown(true);
			});
			context.waitTicks(60);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(2);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("wind_sprint"));
			context.runOnClient(mc -> {
				mc.options.keyUp.setDown(false);
				mc.options.keySprint.setDown(false);
				wardrobe.wearCompanion(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
