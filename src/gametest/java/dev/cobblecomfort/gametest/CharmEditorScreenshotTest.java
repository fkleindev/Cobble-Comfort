package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.CharmEditorScreen;
import dev.cobblecomfort.features.wardrobe.ToolCharm;
import dev.cobblecomfort.features.wardrobe.ToolKind;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import dev.cobblecomfort.features.wardrobe.WardrobeScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The charm editor: the sword in 3D with its charm, a click tying it somewhere else, turned, in first-person mode; then
 * the charm where it was tied, in third and first person, and the wardrobe's tools tab.
 */
public class CharmEditorScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			cmd(world, "difficulty peaceful");
			cmd(world, "time set noon");
			cmd(world, "gamerule advance_time false");
			WardrobeFeature feature = WardrobeFeature.INSTANCE;
			feature.config().announce = false;
			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			cmd(world, "tp @a " + (p.getX() + 0.5) + " " + p.getY() + " " + (p.getZ() + 0.5) + " 0 10");
			context.waitTicks(2);
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				feature.wearToolCharm(ToolKind.SWORD, ToolCharm.TASSEL_RED);
				feature.resetCharmSpot(ToolKind.SWORD);
			});
			cmd(world, "item replace entity @a weapon.mainhand with minecraft:diamond_sword");
			context.waitTicks(5);

			context.setScreen(() -> new CharmEditorScreen(null, ToolKind.SWORD));
			context.waitTicks(5);
			shot(context, "charm_editor_default");
			// A click a little up and right of the view's middle ties it there.
			context.runOnClient(mc -> {
				var screen = mc.gui.screen();
				int w = screen.width;
				int h = screen.height;
				int pw = Math.min(380, w - 20);
				int ph = Math.min(250, h - 20);
				int left = (w - pw) / 2;
				int top = (h - ph) / 2;
				double cx = (left + 8 + left + pw - 8 - 132 - 8) / 2.0;
				double cy = (top + 26 + top + ph - 8) / 2.0;
				MouseButtonEvent click = new MouseButtonEvent(cx + 25, cy - 45, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
				screen.mouseClicked(click, false);
				screen.mouseReleased(click);
				LOGGER.info("Charm spot after click: {}", feature.charmSpot(ToolKind.SWORD, false));
			});
			context.waitTicks(3);
			shot(context, "charm_editor_clicked");
			context.runOnClient(mc -> {
				var screen = mc.gui.screen();
				MouseButtonEvent right = new MouseButtonEvent(screen.width / 2.0 - 60, screen.height / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0));
				screen.mouseClicked(right, false);
				screen.mouseDragged(right, 60, -10);
				screen.mouseReleased(right);
			});
			context.waitTicks(3);
			shot(context, "charm_editor_turned");
			context.setScreen(() -> null);

			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			shot(context, "charm_editor_third_person");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				feature.moveCharmSpot(ToolKind.SWORD, true, new Vector3f(13f, 13.5f, 0f));
			});
			context.waitTicks(20);
			shot(context, "charm_editor_first_person");

			// Each kind of charm close up, on the item it is shown on.
			for (ToolCharm.Style style : ToolCharm.Style.values()) {
				ToolCharm charm = ToolCharm.variants(style).get(0);
				ToolKind kind = style.showcase;
				context.runOnClient(mc -> feature.wearToolCharm(kind, charm));
				context.setScreen(() -> new CharmEditorScreen(null, kind));
				context.waitTicks(2);
				context.runOnClient(mc -> {
					var screen = mc.gui.screen();
					for (int i = 0; i < 4; i++) {
						screen.mouseScrolled(screen.width / 2.0 - 60, screen.height / 2.0, 0, 1);
					}
				});
				context.waitTicks(3);
				shot(context, "charm_close_" + charm.key());
			}
			context.setScreen(() -> null);

			context.setScreen(() -> new WardrobeScreen(null));
			context.waitTicks(5);
			context.runOnClient(mc -> clickTab(mc.gui.screen(), 6));
			context.waitTicks(5);
			shot(context, "charm_editor_wardrobe");
			context.setScreen(() -> null);
			context.runOnClient(mc -> feature.resetCharmSpot(ToolKind.SWORD));
		}
	}

	/** Clicks the wardrobe's tab with this index. */
	private static void clickTab(net.minecraft.client.gui.screens.Screen screen, int index) {
		int i = 0;
		for (var child : java.util.List.copyOf(screen.children())) {
			if (child instanceof dev.cobblecomfort.features.map.screen.TabButton tab && i++ == index) {
				tab.onClick(null, false);
			}
		}
	}

	private static void cmd(TestSingleplayerContext world, String command) {
		world.getServer().runCommand(command);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
