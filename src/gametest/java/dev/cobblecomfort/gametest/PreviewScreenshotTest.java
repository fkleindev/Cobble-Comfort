package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.preview.ItemPreviewFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Map and book previews: a fresh map (small, with Shift, large), a written book turned by scrolling, a book and quill, an enchanted book. */
public class PreviewScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");

			// Use an empty map to draw the surroundings; the client learns its centre while carrying it.
			world.getServer().runCommand("tp @a 200 -60 70");
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:map");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(60);
			String center = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				MapItemSavedData data = MapItem.getSavedData(player.getMainHandItem(), player.level());
				return data == null ? "none" : data.centerX + ", " + data.centerZ + " scale " + data.scale;
			});
			LOGGER.info("Server map centre: {}", center);

			world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:written_book[written_book_content={title:\"Notes\","
					+ "author:\"Alex\",pages:[\"Day 1: Found a village to the north. The farmer sells carrots for emeralds.\\n\\nBring a bed next time!\","
					+ "\"Day 2: Built a small house by the river. Storage is in the basement.\",\"Day 3: Diamonds at Y -58!\"]}]");
			world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:writable_book[writable_book_content={pages:["
					+ "\"Shopping list:\\n- 64 torches\\n- iron pickaxe\\n- bread\"]}]");
			world.getServer().runCommand("item replace entity @a hotbar.3 with minecraft:enchanted_book"
					+ "[stored_enchantments={sharpness:4,looting:3,mending:1,feather_falling:4,vanishing_curse:1}]");
			context.waitTicks(5);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);

			hoverInventorySlot(context, 8, 142);
			context.waitTicks(5);
			shot(context, "preview_map_small");
			context.getInput().holdKey(InputConstants.KEY_LSHIFT);
			context.waitTicks(5);
			shot(context, "preview_map_large");
			context.getInput().releaseKey(InputConstants.KEY_LSHIFT);

			hoverInventorySlot(context, 8 + 18, 142);
			context.waitTicks(5);
			shot(context, "preview_book_small");
			context.getInput().scroll(-1);
			context.waitTicks(5);
			context.getInput().holdKey(InputConstants.KEY_LSHIFT);
			context.waitTicks(5);
			shot(context, "preview_book_page2_large");
			context.getInput().releaseKey(InputConstants.KEY_LSHIFT);

			hoverInventorySlot(context, 8 + 2 * 18, 142);
			context.waitTicks(5);
			shot(context, "preview_writable");

			hoverInventorySlot(context, 8 + 3 * 18, 142);
			context.waitTicks(5);
			shot(context, "preview_enchanted");
			context.getInput().holdKey(InputConstants.KEY_LSHIFT);
			context.waitTicks(5);
			shot(context, "preview_enchanted_shift");
			context.getInput().releaseKey(InputConstants.KEY_LSHIFT);

			// In a chest the map is not carried, so vanilla has no player marker: the own position comes from the learned centre.
			context.runOnClient(mc -> ItemPreviewFeature.INSTANCE.config().compactByDefault = false);
			context.setScreen(() -> null);
			world.getServer().runCommand("setblock 202 -60 70 minecraft:chest");
			world.getServer().runCommand("item replace block 202 -60 70 container.0 from entity @a hotbar.0");
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:air");
			world.getServer().runCommand("tp @a 204 -60 73");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);
			context.getInput().lookAt(new BlockPos(202, -60, 70));
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			hoverInventorySlot(context, 8, 18);
			context.waitTicks(5);
			shot(context, "preview_map_chest_large");
			context.runOnClient(mc -> ItemPreviewFeature.INSTANCE.config().compactByDefault = true);
			context.setScreen(() -> null);
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
