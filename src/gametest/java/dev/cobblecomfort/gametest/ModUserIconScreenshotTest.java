package dev.cobblecomfort.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import dev.cobblecomfort.features.petplush.PetPlushFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The Cobble Comfort icon in front of the names of players with the mod: chat, player list, name tag. Screenshots only. */
public class ModUserIconScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			// Alex without the mod in the tab list, to compare.
			context.runOnClient(mc -> {
				try {
					Field field = ClientPacketListener.class.getDeclaredField("playerInfoMap");
					field.setAccessible(true);
					@SuppressWarnings("unchecked")
					Map<UUID, PlayerInfo> players = (Map<UUID, PlayerInfo>) field.get(mc.getConnection());
					UUID alex = UUID.nameUUIDFromBytes("Alex".getBytes());
					PlayerInfo info = new PlayerInfo(new GameProfile(alex, "Alex"), false);
					players.put(alex, info);
					Field listed = ClientPacketListener.class.getDeclaredField("listedPlayers");
					listed.setAccessible(true);
					@SuppressWarnings("unchecked")
					Set<PlayerInfo> shown = (Set<PlayerInfo>) listed.get(mc.getConnection());
					shown.add(info);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			context.runOnClient(mc -> mc.player.connection.sendChat("Hallo zusammen!"));
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("tellraw @a \"<Alex> Hi!\"");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);
			LOGGER.info("Screenshot {} -> {}", "mod_icon_chat", context.takeScreenshot("mod_icon_chat"));

			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(10);
			LOGGER.info("Screenshot {} -> {}", "mod_icon_tab", context.takeScreenshot("mod_icon_tab"));
			context.getInput().releaseKey(options -> options.keyPlayerList);

			// Two other players in front: Steve has the mod (as the Sync plugin would list him), Alex not.
			context.runOnClient(mc -> {
				UUID steve = UUID.nameUUIDFromBytes("Steve".getBytes());
				try {
					Field syncField = PetPlushFeature.class.getDeclaredField("sync");
					syncField.setAccessible(true);
					Object sync = syncField.get(PetPlushFeature.INSTANCE);
					Field playersField = sync.getClass().getDeclaredField("players");
					playersField.setAccessible(true);
					@SuppressWarnings("unchecked")
					Set<UUID> modPlayers = (Set<UUID>) playersField.get(sync);
					// Without the plugin the sync forgets the list every tick; put Steve back after it.
					ClientTickEvents.END_CLIENT_TICK.register(client -> modPlayers.add(steve));
					modPlayers.add(steve);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				mc.player.snapTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), 0, 0);
				spawn(mc, steve, "Steve", -0.9);
				spawn(mc, UUID.nameUUIDFromBytes("Alex2".getBytes()), "Alex", 0.9);
			});
			context.waitTicks(10);
			LOGGER.info("Screenshot {} -> {}", "mod_icon_nametag", context.takeScreenshot("mod_icon_nametag"));
		}
	}

	/** A client-side player standing three blocks south of the own one, {@code side} blocks to the side, facing it. */
	private static void spawn(Minecraft mc, UUID id, String name, double side) {
		RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(id, name));
		player.snapTo(mc.player.getX() + side, mc.player.getY(), mc.player.getZ() + 3, 180, 0);
		player.setYHeadRot(180);
		player.setId(-1000 - (int) Math.round(side * 10));
		mc.level.addEntity(player);
	}
}
