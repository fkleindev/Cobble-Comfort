package dev.cobblecomfort.gametest;

import dev.cobblecomfort.features.wardrobe.Companion;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import dev.cobblecomfort.features.wardrobe.WardrobeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/** Companions: every kind next to the player, one following up a staircase, the wardrobe's companion tab. */
public class CompanionScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		WardrobeFeature wardrobe = WardrobeFeature.INSTANCE;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestShaders.enableIfRequested(context);
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("weather clear");
			context.runOnClient(mc -> {
				wardrobe.config().announce = false;
				wardrobe.unlockAllCompanions();
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				dev.cobblecomfort.features.lookinfo.LookInfoFeature.INSTANCE.config().enabled = false;
				mc.player.setYRot(180);
				mc.player.setXRot(15);
			});
			context.waitTicks(5);

			// Every kind, sitting next to you after a moment; a few with a name and something to wear.
			for (Companion.Style style : Companion.Style.values()) {
				Companion companion = Companion.variants(style).getFirst();
				context.runOnClient(mc -> {
					wardrobe.wearCompanion(companion);
					wardrobe.nameCompanion(style.ordinal() % 2 == 0 ? style.title().getString() : "");
					wardrobe.dressCompanion(Companion.Accessory.values()[style.ordinal() % Companion.Accessory.values().length]);
				});
				context.waitTicks(70);
				Vec3 at = context.computeOnClient(mc -> wardrobe.companionPosition());
				Vec3 me = context.computeOnClient(mc -> mc.player.position());
				LOGGER.info("Companion {} at {} (player {})", style, at, me);
				shot(context, "companion_" + style.name().toLowerCase());
			}

			// Following up a staircase and across a gap.
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(Companion.GOLEM_COBBLE);
				wardrobe.nameCompanion("");
				wardrobe.dressCompanion(Companion.Accessory.NONE);
			});
			for (int i = 0; i < 5; i++) {
				world.getServer().runCommand("fill -3 -60 " + (4 + i) + " 3 " + (-60 + i) + " " + (4 + i) + " stone");
			}
			world.getServer().runCommand("fill -3 -60 9 3 -56 14 stone");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(5);
			for (int step = 0; step <= 26; step++) {
				double z = step * 0.5;
				int y = z < 4 ? -60 : -60 + Math.min(5, (int) Math.floor(z - 4) + 1);
				world.getServer().runCommand("tp @p 0 " + y + " " + z + " 0 20");
				context.waitTicks(3);
			}
			context.waitTicks(40);
			Vec3 own = context.computeOnClient(mc -> mc.player.position());
			Vec3 companion = context.computeOnClient(mc -> wardrobe.companionPosition());
			LOGGER.info("Companion after the stairs: player {}, companion {}", own, companion);
			context.runOnClient(mc -> {
				mc.player.setYRot(180);
				mc.player.setXRot(25);
			});
			context.waitTicks(5);
			shot(context, "companion_stairs");

			// The wardrobe on the companion tab.
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(Companion.DRAGON_GREEN);
				wardrobe.nameCompanion("Fauchi");
				wardrobe.dressCompanion(Companion.Accessory.COLLAR);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			context.setScreen(() -> {
				WardrobeScreen screen = new WardrobeScreen(null);
				try {
					Field tab = WardrobeScreen.class.getDeclaredField("tab");
					tab.setAccessible(true);
					Class<?> tabs = Class.forName("dev.cobblecomfort.features.wardrobe.WardrobeScreen$Tab");
					for (Object t : tabs.getEnumConstants()) {
						if (t.toString().equals("COMPANION")) {
							tab.set(screen, t);
						}
					}
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				return screen;
			});
			context.waitTicks(10);
			shot(context, "companion_wardrobe");
			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				wardrobe.wearCompanion(null);
				dev.cobblecomfort.features.lookinfo.LookInfoFeature.INSTANCE.config().enabled = true;
				wardrobe.nameCompanion("");
				wardrobe.dressCompanion(Companion.Accessory.NONE);
			});
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
