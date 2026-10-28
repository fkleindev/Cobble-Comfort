package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Crosshair hit marker and cooldown ring, world fade-in, titles, action bar and the death screen. */
public class CombatScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			shot(context, "combat_world_fade");
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_monsters false");
			server.runCommand("gamerule show_death_messages false");
			context.runOnClient(mc -> mc.options.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR));
			server.runCommand("item replace entity @p weapon.mainhand with iron_sword");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());

			// A hit on a sturdy target: white marker, and the ring filling while the sword recharges.
			server.runCommand("summon zombie " + player.getX() + " " + player.getY() + " " + (player.getZ() + 2)
					+ " {NoAI:1b,Silent:1b,Health:100f,attributes:[{id:\"minecraft:max_health\",base:100}]}");
			context.waitTicks(80);
			context.getInput().lookAt(player.offset(0, 1, 2));
			context.waitTicks(3);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			shot(context, "combat_hit");
			server.runCommand("kill @e[type=zombie]");

			// A kill: red and larger.
			server.runCommand("summon pig " + player.getX() + " " + player.getY() + " " + (player.getZ() + 2) + " {NoAI:1b,Silent:1b,Health:1f}");
			context.waitTicks(30);
			context.getInput().lookAt(player.offset(0, 0, 2));
			context.waitTicks(3);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			shot(context, "combat_kill");

			// Title sliding in, action bar cross-fading.
			server.runCommand("title @a title \"Willkommen\"");
			context.waitTicks(2);
			shot(context, "combat_title");
			server.runCommand("title @a clear");
			server.runCommand("title @a actionbar \"Erste Nachricht\"");
			context.waitTicks(20);
			server.runCommand("title @a actionbar \"Zweite Nachricht\"");
			context.waitTicks(2);
			shot(context, "combat_actionbar");

			// The death screen fading in over a greyed world.
			server.runCommand("kill @p");
			context.waitTicks(4);
			shot(context, "combat_death_fading");
			context.waitTicks(30);
			shot(context, "combat_death");
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
