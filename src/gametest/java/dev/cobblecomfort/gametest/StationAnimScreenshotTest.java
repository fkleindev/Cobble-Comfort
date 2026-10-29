package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Workstation animations: a smelting furnace with its glint, an anvil result with its clonk, a trader levelling up. */
public class StationAnimScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("xp set @a 30 levels");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());

			// Furnace: iron almost done, then the next one cooking.
			BlockPos furnace = player.offset(0, 0, 2);
			world.getServer().runCommand("setblock " + furnace.getX() + " " + furnace.getY() + " " + furnace.getZ()
					+ " minecraft:furnace{Items:[{Slot:0b,id:\"minecraft:raw_iron\",count:5},{Slot:1b,id:\"minecraft:coal\",count:4}]}");
			open(context, furnace);
			context.waitTicks(30);
			shot(context, "station_furnace_progress");
			waitForResult(context, 2, 260);
			context.waitTicks(4);
			shot(context, "station_furnace_glint");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Anvil: a sword and a book go in, the result lands with a clonk and the cost counts up.
			BlockPos anvil = player.offset(2, 0, 1);
			world.getServer().runCommand("setblock " + anvil.getX() + " " + anvil.getY() + " " + anvil.getZ() + " minecraft:anvil");
			world.getServer().runCommand("item replace entity @a inventory.0 with minecraft:diamond_sword");
			world.getServer().runCommand("item replace entity @a inventory.1 with minecraft:enchanted_book[stored_enchantments={\"minecraft:sharpness\":4}]");
			open(context, anvil);
			quickMoveInventory(context, 9);
			quickMoveInventory(context, 10);
			context.waitTicks(3);
			shot(context, "station_anvil_clonk");
			context.waitTicks(6);
			shot(context, "station_anvil_done");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Trading: a farmer one trade away from the next level.
			BlockPos villagerAt = player.offset(-2, 0, 2);
			world.getServer().runCommand("summon villager " + villagerAt.getX() + " " + villagerAt.getY() + " " + villagerAt.getZ()
					+ " {NoAI:1b,VillagerData:{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"},Xp:4,"
					+ "Offers:{Recipes:[{buy:{id:\"minecraft:dirt\",count:1},sell:{id:\"minecraft:emerald\",count:1},maxUses:12,xp:8}]}}");
			world.getServer().runCommand("item replace entity @a inventory.2 with minecraft:dirt 16");
			context.waitTicks(10);
			context.getInput().lookAt(villagerAt.above());
			context.waitTicks(3);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			logTrader(context, "opened");
			clickFirstOffer(context);
			context.waitTicks(5);
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 2, 0, ContainerInput.QUICK_MOVE, mc.player));
			context.waitTicks(10);
			shot(context, "station_trader_bar");
			for (int i = 0; i < 80; i++) {
				context.waitTick();
				int offers = context.computeOnClient(mc -> mc.player.containerMenu instanceof MerchantMenu m ? m.getOffers().size() : 0);
				if (offers > 1) {
					break;
				}
			}
			shot(context, "station_trader_slide_start");
			logTrader(context, "after levelling");
			shot(context, "station_trader_slide");
			context.waitTicks(30);
			shot(context, "station_trader_done");
			context.setScreen(() -> null);
		}
	}

	private static void open(ClientGameTestContext context, BlockPos pos) {
		context.getInput().lookAt(pos);
		context.waitTicks(3);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(10);
	}

	/** Waits until one more item has been made in the output slot. */
	private static void waitForResult(ClientGameTestContext context, int slot, int maxTicks) {
		int before = context.computeOnClient(mc -> mc.player.containerMenu.getSlot(slot).getItem().getCount());
		for (int i = 0; i < maxTicks; i++) {
			context.waitTick();
			if (context.computeOnClient(mc -> mc.player.containerMenu.getSlot(slot).getItem().getCount()) > before) {
				LOGGER.info("Station result after {} ticks", i);
				return;
			}
		}
		LOGGER.info("Station result never came");
	}

	/** Shift-clicks the player's inventory slot with this index in the open menu. */
	private static void quickMoveInventory(ClientGameTestContext context, int index) {
		context.runOnClient(mc -> {
			for (Slot slot : mc.player.containerMenu.slots) {
				if (slot.container instanceof Inventory && slot.getContainerSlot() == index) {
					mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
					return;
				}
			}
		});
		context.waitTicks(2);
	}

	private static void clickFirstOffer(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && widget.getWidth() == 88) {
					return new double[]{(widget.getX() + 10) * scale, (widget.getY() + 10) * scale};
				}
			}
			throw new AssertionError("No offer button");
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void logTrader(ClientGameTestContext context, String step) {
		String text = context.computeOnClient(mc -> mc.player.containerMenu instanceof MerchantMenu m
				? "level " + m.getTraderLevel() + " xp " + m.getTraderXp() + " offers " + m.getOffers().size()
				: "no trading screen (" + mc.gui.screen() + ")");
		LOGGER.info("Trader {}: {}", step, text);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
