package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bundle panels: shown next to the inventory, taking an item out and putting it back, shift-click, switched off. */
public class BundlePanelsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");
	private static final String[] MANY = {"oak_log", "birch_log", "spruce_log", "dirt", "sand", "gravel", "clay_ball", "flint",
			"feather", "string", "bone", "arrow", "coal", "iron_nugget", "gold_nugget", "redstone", "lapis_lazuli", "quartz",
			"slime_ball", "leather"};
	/** Left shift in the input modifiers. */
	private static final int SHIFT = 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("item replace entity @a inventory.0 with minecraft:bundle[minecraft:bundle_contents="
					+ "[{id:\"minecraft:diamond\",count:5},{id:\"minecraft:apple\",count:12},{id:\"minecraft:torch\",count:8}]]");
			StringBuilder many = new StringBuilder();
			for (String id : MANY) {
				many.append(many.isEmpty() ? "" : ",").append("{id:\"minecraft:").append(id).append("\",count:1}");
			}
			world.getServer().runCommand("item replace entity @a inventory.1 with minecraft:red_bundle[minecraft:bundle_contents=[" + many + "]]");
			world.getServer().runCommand("item replace entity @a inventory.2 with minecraft:blue_bundle");
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 20");
			world.getServer().runCommand("effect give @a minecraft:speed 600");
			// Until the advancement toasts are gone.
			context.waitTicks(150);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			shot(context, "bundles_on");

			// Hovering the red bundle's slot outlines its panel.
			double[] slot = context.computeOnClient(mc -> {
				ContainerScreenAccessor gui = (ContainerScreenAccessor) mc.gui.screen();
				double scale = mc.getWindow().getGuiScale();
				return new double[]{(gui.cobblecomfort$leftPos() + 8 + 18 + 8) * scale, (gui.cobblecomfort$topPos() + 84 + 8) * scale};
			});
			context.getInput().setCursorPos(slot[0], slot[1]);
			context.waitTicks(3);
			shot(context, "bundles_link");

			// The first item of the first bundle: hovered, then taken onto the cursor.
			move(context, 0, 0);
			shot(context, "bundles_hover_item");
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			shot(context, "bundles_taken");
			check(carried(context).is(Items.DIAMOND) && carried(context).getCount() == 5, "diamonds on the cursor");
			check(serverBundle(world, 0).size() == 2, "the server's bundle lost the diamonds");

			// Clicking the panel with the diamonds on the cursor puts them back.
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(5);
			check(carried(context).isEmpty(), "cursor empty after putting back");
			check(serverBundle(world, 0).size() == 3, "the server's bundle has the diamonds again");

			// Shift-click on the third item (torches): straight into the inventory.
			move(context, 0, 2);
			shiftClick(context);
			context.waitTicks(5);
			check(carried(context).isEmpty(), "cursor empty after shift-click");
			int torches = world.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.TORCH));
			check(torches == 8, "8 torches in the server's inventory, got " + torches);
			shot(context, "bundles_shift");

			clickButton(context, "cobblecomfort.invtools.bundles");
			context.waitTicks(5);
			shot(context, "bundles_off");
			clickButton(context, "cobblecomfort.invtools.bundles");
			context.setScreen(() -> null);
		}
	}

	/** Moves the mouse over an item cell of the first panel (right of the GUI, at its top). */
	private static void move(ClientGameTestContext context, int row, int column) {
		double[] pos = context.computeOnClient(mc -> {
			ContainerScreenAccessor gui = (ContainerScreenAccessor) mc.gui.screen();
			double scale = mc.getWindow().getGuiScale();
			int x = gui.cobblecomfort$leftPos() + gui.cobblecomfort$imageWidth() + 4 + 6 + column * 18 + 8;
			int y = gui.cobblecomfort$topPos() + 5 + 18 + 1 + row * 18 + 8;
			return new double[]{x * scale, y * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
	}

	/** The test input always clicks without modifiers, so the shift-click goes to the mouse handler directly. */
	private static void shiftClick(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
			long window = mc.getWindow().handle();
			mouse.invokeOnButton(window, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, SHIFT), 1);
			mouse.invokeOnButton(window, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, SHIFT), 0);
		});
	}

	private static ItemStack carried(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.player.containerMenu.getCarried().copy());
	}

	private static BundleContents serverBundle(TestSingleplayerContext world, int index) {
		return world.getServer().computeOnServer(server -> player(server).getInventory().getItem(9 + index)
				.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Bundle panels: expected " + what);
		}
		LOGGER.info("Bundle panels OK: {}", what);
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

	private static void shot(ClientGameTestContext context, String name) {
		String size = context.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> screen
				? ((ContainerScreenAccessor) screen).cobblecomfort$imageWidth() + "x" + ((ContainerScreenAccessor) screen).cobblecomfort$imageHeight()
				: "no container");
		LOGGER.info("Screenshot {} ({}) -> {}", name, size, context.takeScreenshot(name));
	}
}
