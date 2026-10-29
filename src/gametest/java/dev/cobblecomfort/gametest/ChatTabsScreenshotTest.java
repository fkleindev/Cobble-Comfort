package dev.cobblecomfort.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.chat.ChatFeature;
import dev.cobblecomfort.mixin.ChatComponentAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chat tabs: messages sorted into chat, private, trade, system and a custom tab; replying in the private tab. */
public class ChatTabsScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> ChatFeature.INSTANCE.config().customTabs = new ArrayList<>(List.of("Clan: [Clan]")));
			// A second player in the tab list, as on a real server, so plugin chat can be told from system messages.
			context.runOnClient(mc -> {
				try {
					Field field = ClientPacketListener.class.getDeclaredField("playerInfoMap");
					field.setAccessible(true);
					@SuppressWarnings("unchecked")
					Map<UUID, PlayerInfo> players = (Map<UUID, PlayerInfo>) field.get(mc.getConnection());
					UUID alex = UUID.nameUUIDFromBytes("Alex".getBytes());
					players.put(alex, new PlayerInfo(new GameProfile(alex, "Alex"), false));
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});

			context.runOnClient(mc -> mc.player.connection.sendChat("Hallo zusammen!"));
			context.waitTicks(5);
			tellraw(world, "[VIP] Alex » Wer kommt mit in die Mine?");
			tellraw(world, "[Handel] Alex » Verkaufe 32 Diamanten für 10 Smaragde");
			tellraw(world, "Welt wird gespeichert...");
			world.getServer().runCommand("tellraw @a {\"translate\":\"multiplayer.player.joined\",\"with\":[\"Steve\"],\"color\":\"yellow\"}");
			tellraw(world, "[Clan] Alex » Raid heute Abend um acht");
			world.getServer().runCommand("msg @a Server-Neustart in 5 Minuten");
			tellraw(world, "[Alex -> mir] Hast du noch Eisen übrig?");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);
			shot(context, "chattabs_hud");

			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(10);
			logShown(context, "all");
			shot(context, "chattabs_all");

			for (String tab : List.of("private", "trade", "system", "custom:Clan", "chat")) {
				clickTab(context, tab);
				logShown(context, tab);
				if (tab.equals("private")) {
					shot(context, "chattabs_private");
					// Typed text goes to the partner as /msg; Alex is not really online, so the server answers with an error.
					context.getInput().typeChars("Ja, bring ich dir");
					context.getInput().pressKey(InputConstants.KEY_RETURN);
					context.waitTicks(10);
					context.setScreen(() -> new ChatScreen("", false));
					context.waitTicks(5);
					logShown(context, "private after reply");
				}
				if (tab.equals("trade")) {
					shot(context, "chattabs_trade");
				}
			}
			clickTab(context, "all");
			context.setScreen(() -> null);
		}
	}

	private static void tellraw(TestSingleplayerContext world, String text) {
		world.getServer().runCommand("tellraw @a \"" + text + "\"");
	}

	/** Moves the mouse along the tab row until the tab is under it and clicks it, as a player would. */
	private static void clickTab(ClientGameTestContext context, String id) {
		double[] pos = context.computeOnClient(mc -> {
			int y = mc.getWindow().getGuiScaledHeight() - 23;
			String before = ChatFeature.INSTANCE.selectedTab();
			for (int x = 3; x < mc.getWindow().getGuiScaledWidth(); x += 3) {
				if (ChatFeature.INSTANCE.clickTab(x, y) && ChatFeature.INSTANCE.selectedTab().equals(id)) {
					return new double[]{x * mc.getWindow().getGuiScale(), y * mc.getWindow().getGuiScale()};
				}
			}
			throw new AssertionError("No tab " + id + " (was on " + before + ")");
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
	}

	private static void logShown(ClientGameTestContext context, String tab) {
		String text = context.computeOnClient(mc -> {
			ChatComponentAccessor chat = (ChatComponentAccessor) mc.gui.hud.getChat();
			StringBuilder builder = new StringBuilder();
			for (GuiMessage.Line line : chat.cobblecomfort$trimmedMessages()) {
				if (line.endOfEntry()) {
					builder.insert(0, "| " + line.parent().content().getString() + " ");
				}
			}
			return builder.toString();
		});
		LOGGER.info("Chat tab {}: {}", tab, text);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
