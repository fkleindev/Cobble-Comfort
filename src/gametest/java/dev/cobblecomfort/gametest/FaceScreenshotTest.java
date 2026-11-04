package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.Face;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import dev.cobblecomfort.features.wardrobe.WardrobeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Face cosmetics: pairs of a piece over the eyes and one on the nose and mouth, seen from the front in third person;
 * hidden under a helmet; and the wardrobe's face tab.
 */
public class FaceScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");
	private static final Face[][] PAIRS = {
			{Face.GLASSES_GOLD, Face.MUSTACHE_CURLED_BROWN},
			{Face.SUNGLASSES_AVIATOR, Face.BANDANA_RED},
			{Face.MASQUERADE_PURPLE, Face.CARNIVAL_CLOWN_NOSE},
			{Face.EYEPATCH_BLACK, Face.MUSTACHE_WALRUS_GREY},
			{Face.HALLOWEEN_MASK_PUMPKIN, Face.WINTER_REINDEER_NOSE},
			{Face.HALLOWEEN_MASK_SKULL, Face.BANDANA_BLACK},
			{Face.GOGGLES_BRASS, Face.PIPE_WOOD, Face.FRECKLES_BROWN},
			{Face.GOGGLES_COPPER, Face.PIPE_CORNCOB, Face.BLUSH_PINK},
			{Face.GOGGLES_IRON, Face.PIPE_DARK, Face.WAR_PAINT_RED},
			{null, null, Face.WAR_PAINT_BLUE},
			{null, null, Face.FRECKLES_GINGER},
			{null, Face.MUSTACHE_CURLED_BROWN, Face.BLUSH_ROSE}};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			cmd(world, "difficulty peaceful");
			cmd(world, "time set noon");
			cmd(world, "gamerule advance_time false");
			WardrobeFeature feature = WardrobeFeature.INSTANCE;
			feature.config().announce = false;
			// The seasonal pieces, as if earned in their months.
			context.runOnClient(mc -> {
				feature.earned(Face.GLASSES_GOLD);
				List<String> keys = Arrays.stream(Face.values()).map(Face::key).toList();
				feature.config().unlocks.values().forEach(unlocks -> unlocks.faces.addAll(keys));
			});
			BlockPos p = context.computeOnClient(mc -> mc.player.blockPosition());
			cmd(world, "tp @a " + (p.getX() + 0.5) + " " + p.getY() + " " + (p.getZ() + 0.5) + " 0 0");
			context.waitTicks(2);
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.options.fov().set(30);
			});
			for (Face[] pair : PAIRS) {
				context.runOnClient(mc -> {
					feature.wearFace(Face.Place.EYES, pair[0]);
					feature.wearFace(Face.Place.MOUTH, pair[1]);
					feature.wearFace(Face.Place.SKIN, pair.length > 2 ? pair[2] : null);
				});
				context.waitTicks(3);
				shot(context, "face_" + String.join("_", Arrays.stream(pair).map(face -> face == null ? "none" : face.key()).toList()));
			}
			// The goggles flip up while looking up: half way, then up; the pipe puffs now and then.
			context.runOnClient(mc -> {
				feature.wearFace(Face.Place.EYES, Face.GOGGLES_BRASS);
				feature.wearFace(Face.Place.MOUTH, Face.PIPE_WOOD);
				feature.wearFace(Face.Place.SKIN, null);
				mc.player.setXRot(-45f);
			});
			context.waitTicks(2);
			shot(context, "face_goggles_flipping");
			context.waitTicks(30);
			shot(context, "face_goggles_up");
			context.runOnClient(mc -> mc.player.setXRot(0f));
			context.waitTicks(60);
			shot(context, "face_pipe_smoke");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(20);
			shot(context, "face_goggles_back");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			// The swinging parts: still, then while turning, then the knot's ends from behind.
			context.runOnClient(mc -> {
				feature.wearFace(Face.Place.EYES, Face.MASQUERADE_GOLD);
				feature.wearFace(Face.Place.MOUTH, Face.BANDANA_BLUE);
				feature.wearFace(Face.Place.SKIN, null);
			});
			context.waitTicks(20);
			shot(context, "face_swing_still");
			for (int i = 0; i < 5; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(mc.player.getYRot() + 14f);
					mc.player.setYHeadRot(mc.player.getYRot());
				});
				context.waitTick();
			}
			shot(context, "face_swing_turning");
			context.runOnClient(mc -> {
				mc.player.setYRot(0f);
				mc.player.setYHeadRot(0f);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(20);
			for (int i = 0; i < 4; i++) {
				context.runOnClient(mc -> {
					mc.player.setYRot(mc.player.getYRot() - 15f);
					mc.player.setYHeadRot(mc.player.getYRot());
				});
				context.waitTick();
			}
			shot(context, "face_swing_back");
			context.runOnClient(mc -> {
				mc.player.setYRot(0f);
				mc.player.setYHeadRot(0f);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});

			cmd(world, "item replace entity @a armor.head with minecraft:iron_helmet");
			context.waitTicks(3);
			shot(context, "face_helmet_hides");
			cmd(world, "item replace entity @a armor.head with minecraft:air");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.options.fov().set(70);
			});

			context.setScreen(() -> new WardrobeScreen(null));
			context.waitTicks(5);
			context.runOnClient(mc -> clickTab(mc.gui.screen(), 2));
			context.waitTicks(5);
			shot(context, "face_wardrobe_eyes");
			context.setScreen(() -> null);
		}
	}

	/** Clicks the wardrobe's tab with this index. */
	private static void clickTab(net.minecraft.client.gui.screens.Screen screen, int index) {
		int i = 0;
		for (var child : List.copyOf(screen.children())) {
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
