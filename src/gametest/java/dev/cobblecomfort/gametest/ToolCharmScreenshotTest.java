package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.ToolCharm;
import dev.cobblecomfort.features.wardrobe.ToolKind;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import dev.cobblecomfort.features.wardrobe.WardrobeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Weapon and tool charms: a different charm on each kind of tool in first person, swinging, in third person and in the
 * wardrobe. Run with {@code -Pextra_runtime_mod=<punchy jar>} to see them in Punchy's arm.
 */
public class ToolCharmScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");
	private static final Object[][] TOOLS = {
			{"diamond_sword", ToolKind.SWORD, ToolCharm.NAME_TAG},
			{"iron_pickaxe", ToolKind.PICKAXE, ToolCharm.ORE_DIAMOND},
			{"netherite_axe", ToolKind.AXE, ToolCharm.TASSEL_RED},
			{"golden_shovel", ToolKind.SHOVEL, ToolCharm.GLOW_BERRY},
			{"stone_hoe", ToolKind.HOE, ToolCharm.TASSEL_GOLD},
			{"iron_spear", ToolKind.SPEAR, ToolCharm.FEATHERS_PARROT_RED},
			{"mace", ToolKind.MACE, ToolCharm.BELL_GOLD},
			{"trident", ToolKind.TRIDENT, ToolCharm.ORE_AMETHYST},
			{"bow", ToolKind.BOW, ToolCharm.FEATHERS_CHICKEN},
			{"crossbow", ToolKind.CROSSBOW, ToolCharm.TASSEL_BLACK},
			{"fishing_rod", ToolKind.FISHING_ROD, ToolCharm.LURE_TROPICAL}};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			cmd(world, "difficulty peaceful");
			cmd(world, "time set noon");
			cmd(world, "gamerule advance_time false");
			cmd(world, "gamemode survival @a");
			WardrobeFeature.INSTANCE.config().announce = false;
			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			cmd(world, "tp @a " + (p.getX() + 0.5) + " " + p.getY() + " " + (p.getZ() + 0.5) + " 0 10");
			context.waitTicks(2);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			for (Object[] tool : TOOLS) {
				context.runOnClient(mc -> WardrobeFeature.INSTANCE.wearToolCharm((ToolKind) tool[1], (ToolCharm) tool[2]));
				cmd(world, "item replace entity @a weapon.mainhand with minecraft:" + tool[0] + "[custom_name=\"Bob\"]");
				context.waitTicks(60);
				shot(context, "tool_charm_" + tool[0]);
			}
			// Turning swings the charm out.
			cmd(world, "item replace entity @a weapon.mainhand with minecraft:iron_pickaxe");
			context.waitTicks(15);
			for (int i = 0; i < 4; i++) {
				context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 12f));
				context.waitTick();
			}
			shot(context, "tool_charm_turning");
			context.runOnClient(mc -> mc.player.setYRot(0f));

			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			cmd(world, "item replace entity @a weapon.mainhand with minecraft:netherite_axe");
			context.waitTicks(15);
			shot(context, "tool_charm_third_person");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			context.setScreen(() -> new WardrobeScreen(null));
			context.waitTicks(5);
			// The weapons and tools tab, then a kind of charm opened.
			context.runOnClient(mc -> clickTab(mc.gui.screen(), 6));
			context.waitTicks(5);
			shot(context, "tool_charm_wardrobe");
			context.setScreen(() -> null);
		}
	}

	/** Clicks the wardrobe's tab with this index. */
	private static void clickTab(net.minecraft.client.gui.screens.Screen screen, int index) {
		int i = 0;
		for (var child : java.util.List.copyOf(screen.children())) {
			if (child instanceof dev.cobblecomfort.features.map.screen.TabButton tab && i++ == index) {
				tab.onClick(null, false);
			}
		}
	}

	private static void cmd(TestSingleplayerContext world, String command) {
		world.getServer().runCommand(command);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
