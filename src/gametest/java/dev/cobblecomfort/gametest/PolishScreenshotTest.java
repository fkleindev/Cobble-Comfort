package dev.cobblecomfort.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Button hover, stacked toasts and the player list with ping numbers and team frames. */
public class PolishScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamerule show_advancement_messages false");

			// Advancements in a row stack onto one toast; recipes show their count.
			server.runCommand("advancement grant @p only minecraft:story/root");
			server.runCommand("advancement grant @p only minecraft:story/mine_stone");
			server.runCommand("advancement grant @p only minecraft:story/upgrade_tools");
			server.runCommand("advancement grant @p only minecraft:story/smelt_iron");
			server.runCommand("recipe give @p minecraft:oak_planks");
			server.runCommand("recipe give @p minecraft:stick");
			server.runCommand("recipe give @p minecraft:crafting_table");
			context.waitTicks(30);
			shot(context, "polish_toasts");

			// Player list with ping numbers and team frames.
			server.runCommand("team add red");
			server.runCommand("team modify red color red");
			server.runCommand("team join red @p");
			context.runOnClient(mc -> {
				addPlayer(mc.getConnection(), "Alex", 42);
				addPlayer(mc.getConnection(), "Steve", 180);
				addPlayer(mc.getConnection(), "Notch", 720);
			});
			context.waitTicks(5);
			context.getInput().holdKey(InputConstants.KEY_TAB);
			context.waitTicks(1);
			shot(context, "polish_tab_opening");
			context.waitTicks(10);
			shot(context, "polish_tab");
			context.runOnClient(mc -> addPlayer(mc.getConnection(), "Bea", 90));
			context.waitTicks(2);
			shot(context, "polish_tab_join");
			context.getInput().releaseKey(InputConstants.KEY_TAB);

			// A hovered button fading its highlight.
			context.setScreen(() -> new OptionsScreen(null, context.computeOnClient(mc -> mc.options)));
			context.waitTicks(5);
			double[] center = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0 - 80 * mc.getWindow().getGuiScale(),
					mc.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(center[0], center[1]);
			context.waitTicks(1);
			shot(context, "polish_button_hover");
			context.setScreen(() -> null);
		}
	}

	private static void addPlayer(ClientPacketListener connection, String name, int latency) {
		UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
		try {
			Field field = ClientPacketListener.class.getDeclaredField("playerInfoMap");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<UUID, PlayerInfo> players = (Map<UUID, PlayerInfo>) field.get(connection);
			PlayerInfo info = new PlayerInfo(new GameProfile(uuid, name), false);
			Field ping = PlayerInfo.class.getDeclaredField("latency");
			ping.setAccessible(true);
			ping.setInt(info, latency);
			players.put(uuid, info);
			Field listedField = ClientPacketListener.class.getDeclaredField("listedPlayers");
			listedField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Set<PlayerInfo> listed = (Set<PlayerInfo>) listedField.get(connection);
			listed.add(info);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
