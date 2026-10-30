package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.core.config.CobbleConfigScreen;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.storage.StorageScreen;
import dev.cobblecomfort.features.tracker.TrackerFeature;
import dev.cobblecomfort.features.tracker.TrackerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The map's navigation bar and the settings screen: categories, a list option, the search. */
public class SettingsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");

			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(15);
			// Hover the second navigation button for its tooltip.
			moveTo(context, 8 + 9, 6 + 2 + 20 + 9);
			context.waitTicks(3);
			shot(context, "settings_map_nav");
			context.setScreen(() -> null);
			context.waitTicks(3);

			// The other screens in the same look.
			world.getServer().runCommand("setblock ~2 ~ ~2 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:12},{Slot:1b,id:\"minecraft:iron_ingot\",count:30}]}");
			context.setScreen(() -> new StorageScreen(null, ""));
			context.waitTicks(5);
			shot(context, "restyle_storage");
			context.setScreen(() -> new StorageScreen(null, "").showTraders());
			context.waitTicks(5);
			shot(context, "restyle_traders");
			context.runOnClient(mc -> TrackerFeature.INSTANCE.addGoal(net.minecraft.world.item.Items.IRON_INGOT, 64));
			context.setScreen(() -> new TrackerScreen(TrackerFeature.INSTANCE, null));
			context.waitTicks(5);
			shot(context, "restyle_tracker");

			context.setScreen(() -> CobbleConfigScreen.create(null));
			context.waitTicks(5);
			shot(context, "settings_general");
			clickSidebar(context, 5);
			context.waitTicks(3);
			shot(context, "settings_category");
			clickSidebar(context, 9);
			context.waitTicks(3);
			shot(context, "settings_list");
			// Click into the search field and type.
			int searchX = context.computeOnClient(mc -> {
				int width = mc.gui.screen().width;
				int panelW = Math.min(480, width - 16);
				return (width - panelW) / 2 + panelW - 60;
			});
			moveTo(context, searchX, 14);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.getInput().typeChars("zoom");
			context.waitTicks(3);
			shot(context, "settings_search");
			for (int i = 0; i < 4; i++) {
				context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			}
			context.getInput().typeChars("own tabs");
			context.waitTicks(3);
			shot(context, "settings_search_list");
			// Add an entry, then edit it.
			moveTo(context, 230, 110);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			moveTo(context, 200, 110);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.getInput().typeChars(" Gilde");
			context.waitTicks(3);
			shot(context, "settings_list_edit");
			String tabs = context.computeOnClient(mc -> dev.cobblecomfort.features.chat.ChatFeature.INSTANCE.config().customTabs.toString());
			LOGGER.info("Custom tabs now: {}", tabs);
			context.setScreen(() -> null);
		}
	}

	/** Clicks the sidebar row with this index. */
	private static void clickSidebar(ClientGameTestContext context, int index) {
		int[] pos = context.computeOnClient(mc -> {
			int width = mc.gui.screen().width;
			int panelW = Math.min(480, width - 16);
			int left = (width - panelW) / 2;
			return new int[]{left + 40, 8 + 28 + 4 + index * 16 + 8};
		});
		moveTo(context, pos[0], pos[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void moveTo(ClientGameTestContext context, double guiX, double guiY) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTicks(1);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
