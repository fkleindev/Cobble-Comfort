package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Inventory section view: switched on with its button, a new item sliding into its section, switched off again. */
public class SectionsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");
	private static final String[] MAIN = {"cobblestone 64", "iron_pickaxe", "bread 12", "dirt 40", "torch 30", "diamond 3",
			"oak_log 16", "stick 9", "diamond_sword", "crafting_table", "apple 5", "iron_ingot 7", "paper 4", "oak_planks 32",
			"shield", "iron_axe", "chest 2"};
	private static final String[] HOTBAR = {"stone_sword", "cobblestone 64", "torch 16", "cooked_beef 20", "water_bucket",
			"oak_planks 64", "bow", "arrow 32", "ender_pearl 4"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			for (int i = 0; i < MAIN.length; i++) {
				world.getServer().runCommand("item replace entity @a inventory." + i + " with minecraft:" + MAIN[i]);
			}
			for (int i = 0; i < HOTBAR.length; i++) {
				world.getServer().runCommand("item replace entity @a hotbar." + i + " with minecraft:" + HOTBAR[i]);
			}
			// Until the advancement toasts are gone.
			context.waitTicks(150);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			shot(context, "sections_off");

			clickButton(context, "cobblecomfort.invtools.sections_on");
			context.waitTicks(10);
			shot(context, "sections_on");

			// A new kind of item lands in a free slot and slides over to its section.
			world.getServer().runCommand("give @a minecraft:golden_apple 2");
			context.waitTicks(5);
			shot(context, "sections_sliding");
			context.waitTicks(20);
			shot(context, "sections_moved");

			hoverLabel(context);
			shot(context, "sections_label");

			clickButton(context, "cobblecomfort.invtools.sections_off");
			context.waitTicks(10);
			shot(context, "sections_back");
			context.setScreen(() -> null);
		}
	}

	/** Clicks the tool button with this tooltip key. */
	private static void clickButton(ClientGameTestContext context, String key) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().equals(Component.translatable(key))) {
					return new double[]{(widget.getX() + 5) * scale, (widget.getY() + 5) * scale};
				}
			}
			throw new AssertionError("No button " + key);
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
	}

	/** Moves the mouse over the first section's label. */
	private static void hoverLabel(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(mc -> {
			ContainerScreenAccessor gui = (ContainerScreenAccessor) mc.gui.screen();
			double scale = mc.getWindow().getGuiScale();
			return new double[]{(gui.cobblecomfort$leftPos() + 12) * scale, (gui.cobblecomfort$topPos() + 89) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
	}

	private static void shot(ClientGameTestContext context, String name) {
		String size = context.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> screen
				? ((ContainerScreenAccessor) screen).cobblecomfort$imageWidth() + "x" + ((ContainerScreenAccessor) screen).cobblecomfort$imageHeight()
				: "no container");
		LOGGER.info("Screenshot {} ({}) -> {}", name, size, context.takeScreenshot(name));
	}
}
