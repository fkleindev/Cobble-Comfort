package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.smoothgui.SmoothGuiFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cooldown ring, durability warning, tooltip animation and smooth scrolling (slowed down for the screenshots). */
public class SmoothUiScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("gamerule show_advancement_messages false");
			server.runCommand("item replace entity @p hotbar.0 with diamond_pickaxe[damage=1550]");
			server.runCommand("item replace entity @p hotbar.1 with ender_pearl 16");
			server.runCommand("item replace entity @p hotbar.2 with iron_sword[damage=100]");
			context.waitTicks(5);
			context.runOnClient(mc -> mc.player.getCooldowns().addCooldown(mc.player.getInventory().getItem(1), 80));
			context.waitTicks(30);
			shot(context, "smooth_ui_hotbar");

			// Tooltips: appear, then glide over to the next item.
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			hoverInventorySlot(context, 8, 142);
			context.waitTicks(1);
			shot(context, "smooth_ui_tooltip_appear");
			context.waitTicks(10);
			hoverInventorySlot(context, 44, 142);
			context.waitTicks(1);
			shot(context, "smooth_ui_tooltip_morph");
			context.setScreen(() -> null);

			context.runOnClient(mc -> SmoothGuiFeature.INSTANCE.config().scrollSpeed = 0.15f);

			// Creative grid sliding a row.
			server.runCommand("gamemode creative @a");
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			double[] grid = context.computeOnClient(mc -> {
				double scale = mc.getWindow().getGuiScale();
				int left = (mc.getWindow().getGuiScaledWidth() - 195) / 2;
				int top = (mc.getWindow().getGuiScaledHeight() - 136) / 2;
				return new double[]{(left + 80) * scale, (top + 50) * scale};
			});
			context.getInput().setCursorPos(grid[0], grid[1]);
			context.getInput().scroll(-1);
			context.waitTicks(1);
			shot(context, "smooth_ui_creative_scroll");
			context.setScreen(() -> null);

			// Chat history gliding.
			context.runOnClient(mc -> {
				for (int i = 1; i <= 40; i++) {
					mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Message number " + i));
				}
			});
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(5);
			double[] chat = context.computeOnClient(mc -> new double[]{100 * mc.getWindow().getGuiScale(),
					(mc.getWindow().getGuiScaledHeight() - 80) * mc.getWindow().getGuiScale()});
			context.getInput().setCursorPos(chat[0], chat[1]);
			context.getInput().scroll(1);
			context.waitTicks(1);
			shot(context, "smooth_ui_chat_scroll");
			context.setScreen(() -> null);

			// A vanilla list.
			context.setScreen(() -> new KeyBindsScreen(null, context.computeOnClient(mc -> mc.options)));
			context.waitTicks(5);
			double[] center = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0,
					mc.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(center[0], center[1]);
			context.getInput().scroll(-3);
			context.waitTicks(2);
			shot(context, "smooth_ui_list_scroll");
			context.waitTicks(40);
			shot(context, "smooth_ui_list_scrolled");
			context.setScreen(() -> null);
			context.runOnClient(mc -> SmoothGuiFeature.INSTANCE.config().scrollSpeed = 1f);
		}
	}

	private static void hoverInventorySlot(ClientGameTestContext context, int slotX, int slotY) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			return new double[]{(left + slotX + 8) * scale, (top + slotY + 8) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
