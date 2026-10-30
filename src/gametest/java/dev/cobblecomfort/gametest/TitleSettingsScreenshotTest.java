package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.core.FeatureManager;
import dev.cobblecomfort.core.config.CobbleConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The settings opened from the title screen, as Mod Menu does: no world, item components not bound yet. */
public class TitleSettingsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> CobbleConfigScreen.create(null));
		context.waitTicks(5);
		shot(context, "title_settings");
		int sections = context.computeOnClient(mc -> FeatureManager.features().size() + 1);
		for (int i = 0; i < sections; i++) {
			clickSidebar(context, i);
			context.waitTicks(2);
		}
		shot(context, "title_settings_last");
		// The HUD editor button works without a world too (the tracker button is disabled there).
		context.setScreen(() -> new dev.cobblecomfort.features.hud.editor.HudEditorScreen(null));
		context.waitTicks(5);
		shot(context, "title_hud_editor");
		context.setScreen(() -> null);
	}

	/** Clicks the sidebar row with this index, scrolling the sidebar to it first. */
	private static void clickSidebar(ClientGameTestContext context, int index) {
		int[] pos = context.computeOnClient(mc -> {
			int width = mc.gui.screen().width;
			int height = mc.gui.screen().height;
			int panelW = Math.min(480, width - 16);
			int left = (width - panelW) / 2;
			int listTop = 8 + 28 + 4;
			int visible = (height - 8 - listTop - 8) / 16;
			int row = Math.min(index, visible - 1);
			return new int[]{left + 40, listTop + row * 16 + 8, Math.max(0, index - (visible - 1))};
		});
		moveTo(context, pos[0], pos[1]);
		if (pos[2] > 0) {
			context.getInput().scroll(-pos[2] * 2);
			context.waitTicks(10);
		}
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
