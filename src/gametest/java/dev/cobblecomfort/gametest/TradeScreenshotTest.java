package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.storage.StorageScreen;
import dev.cobblecomfort.features.trade.TradeFeature;
import dev.cobblecomfort.features.trade.TradeMerchant;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Trade memory: trading with a librarian and a wandering trader, the Traders tab, the tooltip hint, the map, forgetting. */
public class TradeScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");
	private static final String MENDING_BOOK = "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:mending\":1}}}";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos librarian = player.offset(0, 0, 3);
			BlockPos trader = player.offset(3, 0, 1);
			world.getServer().runCommand("summon villager " + librarian.getX() + " " + librarian.getY() + " " + librarian.getZ()
					+ " {NoAI:1b,VillagerData:{profession:\"minecraft:librarian\",level:5,type:\"minecraft:plains\"},Offers:{Recipes:["
					+ "{buy:{id:\"minecraft:emerald\",count:12},buyB:{id:\"minecraft:book\",count:1},sell:" + MENDING_BOOK + ",maxUses:12},"
					+ "{buy:{id:\"minecraft:paper\",count:24},sell:{id:\"minecraft:emerald\",count:1},maxUses:16},"
					+ "{buy:{id:\"minecraft:emerald\",count:1},sell:{id:\"minecraft:bookshelf\",count:1},maxUses:12,uses:12}]}}");
			world.getServer().runCommand("summon wandering_trader " + trader.getX() + " " + trader.getY() + " " + trader.getZ()
					+ " {NoAI:1b,Offers:{Recipes:[{buy:{id:\"minecraft:emerald\",count:5},sell:{id:\"minecraft:blue_ice\",count:1},maxUses:6}]}}");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);

			trade(context, librarian.above());
			shot(context, "trade_screen");
			clickButton(context, "Name this trader");
			context.waitTicks(2);
			context.getInput().typeChars("Bob");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(2);
			shot(context, "trade_screen_named");
			context.setScreen(() -> null);
			context.waitTicks(5);
			trade(context, trader.above());
			context.setScreen(() -> null);
			context.waitTicks(5);
			log(context, "after trading");

			// The Traders tab of the storage memory, searching for Mending (German name too).
			context.setScreen(() -> new StorageScreen(null, ""));
			context.waitTicks(5);
			clickButton(context, "Traders");
			context.waitTicks(5);
			shot(context, "trade_tab_all");
			context.runOnClient(mc -> {
				for (var child : mc.gui.screen().children()) {
					if (child instanceof net.minecraft.client.gui.components.EditBox box) {
						box.setValue("mending");
					}
				}
			});
			context.waitTicks(5);
			shot(context, "trade_tab_mending");
			hoverOffer(context);
			context.waitTicks(5);
			shot(context, "trade_tab_tooltip");

			// The tooltip hint on a Mending book in the inventory.
			context.setScreen(() -> null);
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:enchanted_book[stored_enchantments={mending:1}]");
			world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:paper 10");
			context.waitTicks(5);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			hoverInventorySlot(context, 8, 142);
			context.waitTicks(5);
			shot(context, "trade_hint_book");
			hoverInventorySlot(context, 8 + 18, 142);
			context.waitTicks(5);
			shot(context, "trade_hint_paper");
			context.setScreen(() -> null);

			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(10);
			double[] marker = context.computeOnClient(mc -> {
				double scale = mc.getWindow().getGuiScale();
				return new double[]{mc.getWindow().getGuiScaledWidth() / 2.0 * scale, mc.getWindow().getGuiScaledHeight() / 2.0 * scale};
			});
			context.getInput().setCursorPos(marker[0], marker[1]);
			context.waitTicks(5);
			shot(context, "trade_map");
			context.setScreen(() -> null);

			// A dead trader is forgotten.
			world.getServer().runCommand("kill @e[type=villager]");
			context.waitTicks(60);
			log(context, "after the librarian died");
		}
	}

	private static void trade(ClientGameTestContext context, BlockPos at) {
		context.getInput().lookAt(at);
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(25);
	}

	private static void clickButton(ClientGameTestContext context, String label) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			for (AbstractWidget widget : Screens.getWidgets(mc.gui.screen())) {
				if (widget.getMessage().getString().equals(label)) {
					return new double[]{(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
				}
			}
			return new double[]{0, 0};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	/** The first offer of the first row sits at the right end of the row. */
	private static void hoverOffer(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int panelWidth = Math.min(380, mc.getWindow().getGuiScaledWidth() - 20);
			int right = (mc.getWindow().getGuiScaledWidth() - panelWidth) / 2 + panelWidth - 8;
			return new double[]{(right - 8) * scale, (10 + 30 + 2 + 11) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
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

	private static void log(ClientGameTestContext context, String step) {
		String text = context.computeOnClient(mc -> {
			StringBuilder out = new StringBuilder();
			for (TradeMerchant merchant : TradeFeature.INSTANCE.store().all()) {
				out.append(TradeFeature.INSTANCE.title(merchant).getString()).append(" '").append(merchant.name).append("' offers=")
						.append(merchant.offers.size()).append("; ");
			}
			return out.isEmpty() ? "none" : out.toString();
		});
		LOGGER.info("Trade memory {}: {}", step, text);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
