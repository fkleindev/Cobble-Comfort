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
 * The lantern spirit: floating along outside, circling its player in a dark cave, guttering at a hissing creeper,
 * dimmed and close when its player is badly hurt, all but out next to a warden.
 */
public class LanternSpiritTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		WardrobeFeature wardrobe = WardrobeFeature.INSTANCE;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamemode survival @a");
			context.runOnClient(mc -> {
				wardrobe.config().announce = false;
				wardrobe.unlockAllCompanions();
				wardrobe.wearCompanion(Companion.LANTERN_CLASSIC);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(25);
			});
			context.waitTicks(120);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("lantern_day"));

			// A dark cave: a closed stone room round the player.
			world.getServer().runCommand("execute as @p at @s run fill ~-5 ~-1 ~-5 ~5 ~4 ~5 stone hollow");
			context.waitTicks(160);
			double minR = 99, maxR = 0, sumY = 0;
			int samples = 0;
			for (int i = 0; i < 40; i++) {
				context.waitTicks(2);
				Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
				Vec3 me = context.computeOnClient(mc -> mc.player.position());
				double r = Math.hypot(at.x - me.x, at.z - me.z);
				minR = Math.min(minR, r);
				maxR = Math.max(maxR, r);
				sumY += at.y - me.y;
				samples++;
			}
			LOGGER.info("Lantern: circling in the cave at {}..{} blocks, {} above the feet", minR, maxR, sumY / samples);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("lantern_cave"));

			// A hissing creeper (it cannot move; killed before it goes off).
			world.getServer().runCommand("execute as @p at @s run summon creeper ~3 ~ ~2 {NoAI:1b,ignited:1b,Fuse:400}");
			context.waitTicks(30);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("lantern_creeper"));
			world.getServer().runCommand("kill @e[type=creeper]");
			context.waitTicks(80);

			// Badly hurt: dimmed, close by.
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("damage @p 18 minecraft:generic");
			context.waitTicks(30);
			Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
			Vec3 me = context.computeOnClient(mc -> mc.player.position());
			LOGGER.info("Lantern: hurt (health {}), {} blocks from the player", context.computeOnClient(mc -> mc.player.getHealth()),
					Math.hypot(at.x - me.x, at.z - me.z));
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("lantern_hurt"));
			world.getServer().runCommand("effect give @p instant_health 1 3");
			context.waitTicks(60);

			// A warden near.
			world.getServer().runCommand("execute as @p at @s run summon warden ~3 ~ ~-3 {NoAI:1b}");
			context.waitTicks(80);
			LOGGER.info("Screenshot -> {}", context.takeScreenshot("lantern_warden"));
			world.getServer().runCommand("kill @e[type=warden]");
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(null);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
