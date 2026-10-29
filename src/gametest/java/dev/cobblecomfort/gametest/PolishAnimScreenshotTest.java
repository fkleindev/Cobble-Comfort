package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.chat.ChatFeature;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.phantom.PhantomFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The smaller animations: items flying on deposit, phantoms in the warning panel, the level-up beam with the
 * enchanting hint, a tool shattering in the hotbar, a pet grave rising from dust, and the chat tab effects.
 */
public class PolishAnimScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());

			// Deposit: the matching items fly into the chest one after another.
			BlockPos chest = player.offset(0, 0, 2);
			world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ()
					+ " minecraft:chest{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:1},{Slot:1b,id:\"minecraft:oak_log\",count:1}]}");
			for (int i = 0; i < 6; i++) {
				world.getServer().runCommand("item replace entity @a inventory." + i + " with minecraft:" + (i % 2 == 0 ? "cobblestone" : "oak_log") + " 32");
			}
			context.waitTicks(5);
			context.getInput().lookAt(chest);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			clickButton(context, "cobblecomfort.invtools.deposit");
			context.waitTicks(2);
			shot(context, "polish_deposit_flying");
			context.waitTicks(20);
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Phantom warning over the threshold: silhouettes glide through the panel.
			world.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				p.getStats().setValue(p, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), PhantomFeature.THRESHOLD + 2000);
			});
			context.runOnClient(mc -> mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS)));
			context.waitTicks(90);

			// Level 29 to 30: the beam and the enchanting table hint.
			world.getServer().runCommand("xp set @a 29 levels");
			context.waitTicks(20);
			world.getServer().runCommand("xp add @a 1 levels");
			context.waitTicks(22);
			shot(context, "polish_levelup_beam");
			context.waitTicks(10);
			shot(context, "polish_enchant_hint");

			// A nearly broken pickaxe breaks on mining stone.
			world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:wooden_pickaxe[damage=58]");
			BlockPos stone = player.offset(1, 0, 1);
			world.getServer().runCommand("setblock " + stone.getX() + " " + stone.getY() + " " + stone.getZ() + " minecraft:stone");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.getInput().lookAt(stone);
			context.waitTicks(5);
			context.getInput().holdKey(options -> options.keyAttack);
			boolean broken = false;
			for (int i = 0; i < 80 && !broken; i++) {
				context.waitTick();
				broken = context.computeOnClient(mc -> mc.player.getInventory().getItem(0).isEmpty());
			}
			context.getInput().releaseKey(options -> options.keyAttack);
			shot(context, "polish_tool_shatter");
			LOGGER.info("Pickaxe broke: {}", broken);
			context.waitTicks(20);

			// A pet dies: its grave rises from a little dust on the map.
			String wolfAt = (player.getX() + 4) + " " + player.getY() + " " + (player.getZ() - 3);
			world.getServer().runCommand("summon wolf " + wolfAt + " {CustomName:\"Bello\",Sitting:1b,NoAI:1b}");
			world.getServer().runCommand("data modify entity @e[type=wolf,limit=1] Owner set from entity @p UUID");
			context.waitTicks(30);
			world.getServer().runCommand("kill @e[type=wolf]");
			context.waitTicks(130);
			context.setScreen(() -> new MapScreen(null, player.getX() + 4.5, player.getZ() - 2.5, null));
			context.waitTicks(4);
			shot(context, "polish_grave_dust");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Chat: a private message blinks violet; in the open chat the counts hop and the marker glides.
			world.getServer().runCommand("msg @a Treffen am Spawn?");
			context.waitTicks(3);
			shot(context, "polish_private_blink");
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(5);
			world.getServer().runCommand("tellraw @a \"[Handel] Verkaufe Eisen\"");
			context.waitTicks(2);
			shot(context, "polish_count_hop");
			clickTab(context, "trade");
			context.waitTicks(1);
			shot(context, "polish_marker_glide");
			context.setScreen(() -> null);
		}
	}

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
	}

	/** Finds the tab's place along the tab row and clicks there; the scan itself must not switch tabs. */
	private static void clickTab(ClientGameTestContext context, String id) {
		double[] pos = context.computeOnClient(mc -> {
			int y = mc.getWindow().getGuiScaledHeight() - 23;
			String before = ChatFeature.INSTANCE.selectedTab();
			for (int x = 3; x < mc.getWindow().getGuiScaledWidth(); x += 3) {
				if (ChatFeature.INSTANCE.clickTab(x, y) && ChatFeature.INSTANCE.selectedTab().equals(id)) {
					// Back to where it was, so the real click below starts the glide.
					for (int back = 3; back < mc.getWindow().getGuiScaledWidth(); back += 3) {
						if (ChatFeature.INSTANCE.clickTab(back, y) && ChatFeature.INSTANCE.selectedTab().equals(before)) {
							break;
						}
					}
					return new double[]{x * mc.getWindow().getGuiScale(), y * mc.getWindow().getGuiScale()};
				}
			}
			throw new AssertionError("No tab " + id);
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
