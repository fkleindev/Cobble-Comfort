package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Inventory tools: restock and deposit buttons in a chest, then shift-dragging over several slots. */
public class TransferScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");
	private static final int SHIFT = 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos chest = player.offset(0, 0, 2);
			world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ()
					+ " minecraft:chest{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:10},{Slot:5b,id:\"minecraft:dirt\",count:1},"
					+ "{Slot:1b,id:\"minecraft:torch\",count:20},{Slot:2b,id:\"minecraft:bread\",count:64}]}");
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 64");
			world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:torch 5");
			world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:bread 3");
			world.getServer().runCommand("item replace entity @a inventory.0 with minecraft:cobblestone 30");
			world.getServer().runCommand("item replace entity @a inventory.1 with minecraft:dirt 64");
			world.getServer().runCommand("item replace entity @a inventory.2 with minecraft:diamond 2");
			for (int i = 9; i < 14; i++) {
				world.getServer().runCommand("item replace entity @a inventory." + i + " with minecraft:oak_log 16");
			}
			context.waitTicks(10);
			context.getInput().lookAt(chest);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			log(context, "opened");
			shot(context, "transfer_before");

			clickButton(context, "cobblecomfort.invtools.restock");
			log(context, "after restock");
			clickButton(context, "cobblecomfort.invtools.deposit");
			log(context, "after deposit");
			shot(context, "transfer_deposited");

			// Shift-drag from the first oak log slot over the next four.
			double[] first = slotPos(context, 18);
			context.getInput().setCursorPos(first[0], first[1]);
			context.waitTicks(2);
			// The test input sends mouse buttons without modifiers, so the shift-click goes to the mouse handler directly.
			shiftButton(context, true);
			context.waitTicks(2);
			for (int i = 19; i < 23; i++) {
				double[] pos = slotPos(context, i);
				context.getInput().setCursorPos(pos[0], pos[1]);
				context.waitTicks(2);
			}
			shiftButton(context, false);
			context.waitTicks(5);
			log(context, "after shift-drag");
			shot(context, "transfer_dragged");
			context.setScreen(() -> null);
		}
	}

	/** Presses or releases the left mouse button with Shift held. */
	private static void shiftButton(ClientGameTestContext context, boolean press) {
		context.runOnClient(mc -> ((MouseHandlerAccessor) mc.mouseHandler).invokeOnButton(mc.getWindow().handle(),
				new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, SHIFT), press ? 1 : 0));
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

	/** Screen position of the player inventory slot with this index (9-35 main inventory). */
	private static double[] slotPos(ClientGameTestContext context, int index) {
		return context.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
			ContainerScreenAccessor gui = (ContainerScreenAccessor) screen;
			double scale = mc.getWindow().getGuiScale();
			for (Slot slot : screen.getMenu().slots) {
				if (slot.container instanceof Inventory && slot.getContainerSlot() == index) {
					return new double[]{(gui.cobblecomfort$leftPos() + slot.x + 8) * scale, (gui.cobblecomfort$topPos() + slot.y + 8) * scale};
				}
			}
			throw new AssertionError("No slot " + index);
		});
	}

	private static void log(ClientGameTestContext context, String step) {
		String text = context.computeOnClient(mc -> describe(mc));
		LOGGER.info("Transfer {}: {}", step, text);
	}

	private static String describe(Minecraft mc) {
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return "no container open";
		}
		StringBuilder chest = new StringBuilder("chest[");
		StringBuilder inventory = new StringBuilder(" inv[");
		for (Slot slot : screen.getMenu().slots) {
			if (slot.hasItem()) {
				StringBuilder target = slot.container instanceof Inventory ? inventory : chest;
				target.append(slot.getItem().getItem().toString().replace("minecraft:", "")).append('x').append(slot.getItem().getCount()).append(' ');
			}
		}
		return chest.append(']').append(inventory).append("] carried=").append(screen.getMenu().getCarried()).toString();
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
