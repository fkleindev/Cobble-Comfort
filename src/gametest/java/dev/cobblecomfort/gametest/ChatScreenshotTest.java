package dev.cobblecomfort.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.chat.ChatFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/** Chat comfort: heads, timestamps, stacking, mentions, search and copy. Screenshots only. */
public class ChatScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> ChatFeature.INSTANCE.config().mentionKeywords = "Basis");
			String self = context.computeOnClient(mc -> mc.player.getGameProfile().name());

			// A second player in the tab list, as on a real server, so name detection has someone to find.
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
			world.getServer().runCommand("tellraw @a [{\"text\":\"[VIP] \",\"color\":\"gold\"},{\"text\":\"Alex\",\"color\":\"white\"},"
					+ "{\"text\":\" » Hey " + self + ", kommst du zur Basis?\",\"color\":\"gray\"}]");
			world.getServer().runCommand("tellraw @a {\"text\":\"[Admin] Steve » Server-Neustart in 10 Minuten\",\"color\":\"red\"}");
			for (int i = 0; i < 3; i++) {
				world.getServer().runCommand("tellraw @a {\"text\":\"Welt wird gespeichert...\",\"color\":\"yellow\"}");
			}
			world.getServer().runCommand("tellraw @a \"Alex hat den Fortschritt [Steinzeit] erzielt\"");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);
			shot(context, "chat_hud");

			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(10);
			shot(context, "chat_open");

			// Test input does not report held modifiers, so click into the search field instead of Ctrl+F.
			double[] searchPos = context.computeOnClient(mc -> new double[]{
					(mc.getWindow().getGuiScaledWidth() - 74) * mc.getWindow().getGuiScale(),
					(mc.getWindow().getGuiScaledHeight() - 23) * mc.getWindow().getGuiScale()});
			context.getInput().setCursorPos(searchPos[0], searchPos[1]);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.getInput().typeChars("alex");
			context.waitTicks(5);
			int hits = context.computeOnClient(mc -> ChatFeature.INSTANCE.searchHits());
			LOGGER.info("Chat search hits: {}", hits);
			shot(context, "chat_search");

			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			// The bottom chat line sits right above the input bar (40 GUI pixels from the bottom).
			double[] pos = context.computeOnClient(mc -> new double[]{
					30 * mc.getWindow().getGuiScale(), (mc.getWindow().getGuiScaledHeight() - 44) * mc.getWindow().getGuiScale()});
			context.getInput().setCursorPos(pos[0], pos[1]);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			String copied = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
			LOGGER.info("Chat copied: {}", copied);
			shot(context, "chat_copied");
			context.setScreen(() -> null);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
