package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.hudanim.HudAnimConfig;
import dev.cobblecomfort.features.hudanim.HudAnimFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HUD animations, slowed down so the screenshots catch them: damage trail, heal pop, experience, food, armour, boss bar. */
public class HudAnimScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_monsters false");
			server.runCommand("gamerule show_advancement_messages false");
			server.runCommand("item replace entity @a armor.chest with diamond_chestplate");
			server.runCommand("item replace entity @a armor.head with iron_helmet");
			server.runCommand("xp add @a 5 levels");
			context.runOnClient(mc -> {
				HudAnimConfig c = HudAnimFeature.INSTANCE.config();
				c.speed = 0.1f;
				c.trailDelay = 30f;
			});
			// Past the spawn protection, so the damage counts.
			context.waitTicks(80);
			shot(context, "hud_anim_base");

			server.runCommand("damage @p 7");
			context.waitTicks(3);
			shot(context, "hud_anim_damage_trail");

			server.runCommand("xp add @a 40 points");
			context.waitTicks(4);
			shot(context, "hud_anim_xp");

			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.getFoodData().setFoodLevel(11)));
			server.runCommand("item replace entity @a armor.head with air");
			// Ghosts start as the full icon, so let them shrink a while first.
			context.runOnClient(mc -> HudAnimFeature.INSTANCE.config().speed = 0.3f);
			context.waitTicks(8);
			shot(context, "hud_anim_food_armor_loss");
			context.runOnClient(mc -> HudAnimFeature.INSTANCE.config().speed = 0.1f);

			server.runCommand("effect give @a instant_health 1 0");
			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.getFoodData().setFoodLevel(16)));
			context.waitTicks(3);
			shot(context, "hud_anim_heal_pop");

			// Normal speed, so the bar is full before it is hit; the trail still waits.
			context.runOnClient(mc -> HudAnimFeature.INSTANCE.config().speed = 1f);
			server.runCommand("bossbar add cobblecomfort:test \"Test Boss\"");
			server.runCommand("bossbar set cobblecomfort:test players @a");
			server.runCommand("bossbar set cobblecomfort:test color red");
			server.runCommand("bossbar set cobblecomfort:test value 100");
			context.waitTicks(10);
			server.runCommand("bossbar set cobblecomfort:test value 35");
			context.waitTicks(4);
			shot(context, "hud_anim_boss_trail");

			context.runOnClient(mc -> {
				HudAnimConfig c = HudAnimFeature.INSTANCE.config();
				HudAnimConfig d = new HudAnimConfig();
				c.speed = d.speed;
				c.trailDelay = d.trailDelay;
			});
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
