package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.furnace.FurnaceEntry;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.screen.MapScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Furnace memory: a smoker and a brewing stand looked into, the label counting down, the toast when done. */
public class FurnaceScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());

			// Two raw beef in a smoker: 2 × 5 s with one coal.
			BlockPos smoker = player.offset(1, 0, 3);
			setblock(world, smoker, "smoker[facing=north]{Items:[{Slot:0b,id:\"minecraft:beef\",count:2},{Slot:1b,id:\"minecraft:coal\",count:1}]}");
			// Water bottles with nether wart: one 20 s brew.
			BlockPos stand = player.offset(-1, 0, 3);
			String water = "id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:water\"}}";
			setblock(world, stand, "brewing_stand{Fuel:20b,Items:[{Slot:0b," + water + "},{Slot:1b," + water + "},{Slot:2b," + water
					+ "},{Slot:3b,id:\"minecraft:nether_wart\",count:1}]}");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);

			open(context, smoker);

			// An empty furnace filled while its screen is open is remembered too.
			BlockPos furnace = player.offset(3, 0, 3);
			setblock(world, furnace, "furnace[facing=north]");
			world.getConnection().waitForClientboundPackets();
			context.getInput().lookAt(furnace);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(25);
			world.getServer().runCommand("item replace block " + furnace.getX() + " " + furnace.getY() + " " + furnace.getZ() + " container.0 with minecraft:raw_iron 3");
			world.getServer().runCommand("item replace block " + furnace.getX() + " " + furnace.getY() + " " + furnace.getZ() + " container.1 with minecraft:coal 1");
			context.waitTicks(5);
			context.setScreen(() -> null);
			context.waitTicks(5);
			String filled = context.computeOnClient(mc -> {
				FurnaceEntry entry = FurnaceFeature.INSTANCE.at(MapFeature.dimensionId(mc.level), furnace);
				return entry == null ? "none" : entry.count + "x remaining=" + entry.remaining + " done=" + entry.done;
			});
			LOGGER.info("Furnace filled while open: {}", filled);
			open(context, stand);
			context.getInput().lookAt(player.offset(0, 1, 3));
			context.waitTicks(20);
			shot(context, "furnace_labels");

			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(10);
			shot(context, "furnace_map");
			context.setScreen(() -> null);

			// The smoker is done after ~10 s: toast and a green label.
			context.waitTicks(25);
			shot(context, "furnace_toast");
			context.waitTicks(290);
			shot(context, "furnace_done");
		}
	}

	private static void open(ClientGameTestContext context, BlockPos pos) {
		context.getInput().lookAt(pos);
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(15);
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private static void setblock(TestSingleplayerContext world, BlockPos pos, String block) {
		world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
