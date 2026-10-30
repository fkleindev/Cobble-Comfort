package dev.cobblecomfort.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Totem plush: in the hotbar and inventory, in first and third person, in the world and when a totem pops. */
public class PlushScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			cmd(world, "difficulty peaceful");
			cmd(world, "time set noon");
			cmd(world, "gamerule advance_time false");
			cmd(world, "gamemode survival @a");
			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			int x = p.getX();
			int y = p.getY();
			int z = p.getZ();
			cmd(world, "tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 10");

			// Hotbar: slot 2 holds a totem, slot 1 is selected, then the totem is taken into the hand.
			cmd(world, "item replace entity @a hotbar.1 with minecraft:totem_of_undying");
			cmd(world, "item replace entity @a weapon.offhand with minecraft:totem_of_undying");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(10);
			shot(context, "plush_offhand");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
			context.waitTicks(2);
			shot(context, "plush_equip_squish");
			context.waitTicks(15);
			shot(context, "plush_first_person");
			motion(context, "first_person");

			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(10);
			shot(context, "plush_inventory");
			context.setScreen(() -> null);

			// Third person, from the front.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			shot(context, "plush_third_person");
			motion(context, "third_person");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// The world: an item frame, an armour stand holding one and a dropped one.
			cmd(world, "fill " + (x - 3) + " " + y + " " + (z + 4) + " " + (x + 3) + " " + (y + 2) + " " + (z + 4) + " minecraft:white_wool");
			cmd(world, "summon item_frame " + (x - 2) + " " + (y + 1) + " " + (z + 3)
					+ " {Facing:2b,Item:{id:\"minecraft:totem_of_undying\",count:1}}");
			cmd(world, "summon armor_stand " + (x + 0.5) + " " + y + " " + (z + 2.5)
					+ " {Rotation:[180f,0f],ShowArms:1b,equipment:{mainhand:{id:\"minecraft:totem_of_undying\",count:1}}}");
			cmd(world, "summon item " + (x + 2.5) + " " + y + " " + (z + 2.5)
					+ " {Item:{id:\"minecraft:totem_of_undying\",count:1},PickupDelay:32767,Age:-32768}");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(20);
			shot(context, "plush_world");

			// A totem saves the player: the plush pops up in the middle of the screen.
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
			context.waitTicks(5);
			cmd(world, "damage @p 1000 minecraft:generic");
			context.waitTicks(2);
			shot(context, "plush_activation_squish");
			context.waitTicks(8);
			shot(context, "plush_activation");
		}
	}

	/** Turns quickly, stops (the plush overshoots), then walks forwards. */
	private static void motion(ClientGameTestContext context, String view) {
		for (int i = 0; i < 4; i++) {
			context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 12f));
			context.waitTick();
		}
		shot(context, "plush_turning_" + view);
		context.waitTicks(3);
		shot(context, "plush_turn_overshoot_" + view);
		context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() - 48f));
		context.waitTicks(20);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(8);
		shot(context, "plush_walking_" + view);
		context.waitTicks(3);
		shot(context, "plush_walking2_" + view);
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(3);
		shot(context, "plush_stop_" + view);
		context.waitTicks(20);
	}

	private static void cmd(TestSingleplayerContext world, String command) {
		world.getServer().runCommand(command);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
