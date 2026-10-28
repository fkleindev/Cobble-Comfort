package dev.cobblecomfort.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.effects.EffectHudFeature;
import dev.cobblecomfort.features.effects.EffectStyle;
import dev.cobblecomfort.features.enchanting.EnchantingFeature;
import dev.cobblecomfort.features.fullbright.FullbrightFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.cobblecomfort.features.storage.StorageScreen;
import dev.cobblecomfort.features.map.screen.MapScreen;
import dev.cobblecomfort.features.map.screen.WaypointEditScreen;
import dev.cobblecomfort.features.map.screen.WaypointListScreen;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import dev.cobblecomfort.features.map.waypoint.XaeroChat;
import dev.cobblecomfort.features.map.sync.PositionSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.EditBox;
import dev.cobblecomfort.features.fullbright.FullbrightMethod;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.tracker.RecipeLookup;
import dev.cobblecomfort.features.tracker.TrackerFeature;
import dev.cobblecomfort.features.tracker.TrackerScreen;
import dev.cobblecomfort.features.zoom.ZoomFeature;
import net.minecraft.world.item.Items;
import dev.cobblecomfort.features.zoom.ZoomStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Opens a test world and takes screenshots of each feature so they can be checked visually.
 * Not an assertion test: it only fails if the game crashes.
 */
public class FeatureScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort GameTest");

	/** Two players as the sync server plugin would share them: one nearby (beyond render distance on the minimap) and one far. */
	private static void sendSharedPlayers(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				List<PositionSync.Remote> remotes = List.of(
						new PositionSync.Remote(UUID.nameUUIDFromBytes("Alex".getBytes()), "Alex", "minecraft:overworld",
								player.getX() + 40, player.getY(), player.getZ() - 25, 90f, 14f, 20f, 8),
						new PositionSync.Remote(UUID.nameUUIDFromBytes("Steve".getBytes()), "Steve", "minecraft:overworld",
								player.getX() - 300, player.getY(), player.getZ() + 120, 0f, 20f, 20f, 20));
				ServerPlayNetworking.send(player, new PositionSync.PlayersPayload(true, 10, remotes));
			}
		});
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// Stands in for the Spigot plugin: answers a client that shares with the list above.
		ServerPlayNetworking.registerGlobalReceiver(PositionSync.SharePayload.TYPE, (payload, ctx) -> LOGGER.info("Client shares: {}", payload.sharing()));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("give @a minecraft:experience_bottle 16");
			world.getServer().runCommand("give @a minecraft:golden_apple 3");
			world.getServer().runCommand("give @a minecraft:enchanted_golden_apple");
			world.getServer().runCommand("give @a minecraft:diamond_sword[enchantments={sharpness:5}]");
			world.getServer().runCommand("give @a minecraft:dragon_egg");
			world.getServer().runCommand("give @a minecraft:stone 32");

			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			BlockPos log = player.offset(0, 1, 3);
			world.getServer().runCommand("setblock " + log.getX() + " " + log.getY() + " " + log.getZ() + " minecraft:oak_log");
			BlockPos wheat = player.offset(-3, 0, 2);
			world.getServer().runCommand("setblock " + wheat.getX() + " " + (wheat.getY() - 1) + " " + wheat.getZ() + " minecraft:farmland");
			world.getServer().runCommand("setblock " + wheat.getX() + " " + wheat.getY() + " " + wheat.getZ() + " minecraft:wheat[age=4]");
			// Within the 3 block survival entity reach, otherwise the crosshair does not target it.
			BlockPos cow = player.offset(2, 0, 1);
			world.getServer().runCommand("summon minecraft:cow " + cow.getX() + " " + cow.getY() + " " + cow.getZ() + " {NoAI:1b}");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(10);

			context.getInput().lookAt(log);
			context.waitTicks(20);
			shot(context, "look_info_block");

			context.getInput().lookAt(wheat);
			context.waitTicks(20);
			shot(context, "look_info_crop");

			context.getInput().lookAt(cow);
			context.waitTicks(20);
			shot(context, "look_info_entity");

			// Zoom: hold the key, then scroll further in, then the cinematic style.
			context.getInput().lookAt(log);
			context.getInput().holdKey(ZoomFeature.INSTANCE.key());
			context.waitTicks(20);
			shot(context, "zoom_normal");
			context.getInput().scroll(4);
			context.waitTicks(20);
			shot(context, "zoom_scrolled");
			context.runOnClient(mc -> ZoomFeature.INSTANCE.config().style = ZoomStyle.CINEMATIC);
			context.waitTicks(20);
			shot(context, "zoom_cinematic");
			context.runOnClient(mc -> ZoomFeature.INSTANCE.config().style = ZoomStyle.NORMAL);
			context.getInput().releaseKey(ZoomFeature.INSTANCE.key());
			context.waitTicks(20);

			// Armor HUD: a full set with different wear, then a bow with arrows in hand.
			world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
			world.getServer().runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate[damage=180]");
			world.getServer().runCommand("item replace entity @a armor.legs with minecraft:golden_leggings[damage=100]");
			world.getServer().runCommand("item replace entity @a armor.feet with minecraft:leather_boots[damage=60]");
			world.getServer().runCommand("item replace entity @a hotbar.6 with minecraft:bow");
			world.getServer().runCommand("item replace entity @a hotbar.7 with minecraft:arrow 23");
			context.waitTicks(20);
			shot(context, "armor_hud");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(6));
			context.waitTicks(20);
			shot(context, "armor_hud_bow");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTick();
			shot(context, "hotbar_gliding");
			context.waitTicks(10);
			shot(context, "hotbar_selected");

			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(10);
			shot(context, "rarity_inventory");

			// Inventory effects: pick up the enchanted apple, drag it across the inventory, drop it.
			hoverInventorySlot(context, 8 + 2 * 18, 142);
			context.waitTicks(2);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			shot(context, "invfx_pickup");
			for (int i = 1; i <= 6; i++) {
				hoverInventorySlot(context, 8 + 2 * 18 + i * 14, 142 - i * 9);
				context.waitTick();
			}
			shot(context, "invfx_drag");
			hoverInventorySlot(context, 8 + 7 * 18, 84);
			context.waitTicks(2);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			shot(context, "invfx_drop");
			context.waitTicks(20);

			// Hover the enchanted sword (hotbar slot 3) to show its tooltip.
			hoverInventorySlot(context, 8 + 3 * 18, 142);
			context.waitTicks(5);
			shot(context, "rarity_tooltip");

			// Enchantment descriptions: a book with a curse, without and with Shift held.
			world.getServer().runCommand("item replace entity @a hotbar.8 with minecraft:enchanted_book"
					+ "[stored_enchantments={mending:1,unbreaking:3,binding_curse:1}]");
			context.waitTicks(5);
			hoverInventorySlot(context, 8 + 8 * 18, 142);
			context.waitTicks(5);
			shot(context, "enchant_desc_hint");
			context.getInput().holdKey(InputConstants.KEY_LSHIFT);
			context.waitTicks(5);
			shot(context, "enchant_desc_shift");
			hoverInventorySlot(context, 8 + 3 * 18, 142);
			context.waitTicks(5);
			shot(context, "enchant_desc_sword");

			// Shortcut effects: shift-click the stone from the hotbar into the main inventory.
			// Test input doesn't pass Shift to clicks, so send the quick move like a real shift-click does (slot 41 = hotbar 5).
			hoverInventorySlot(context, 8, 84);
			context.waitTicks(2);
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 41, 0,
					ContainerInput.QUICK_MOVE, mc.player));
			context.waitTick();
			shot(context, "invfx_shift_flying");
			context.waitTicks(6);
			shot(context, "invfx_shift_landed");
			context.getInput().releaseKey(InputConstants.KEY_LSHIFT);

			// Shulker preview: compact tooltip, full grid with Shift, fill bar; then the HUD panel while holding it.
			world.getServer().runCommand("item replace entity @a hotbar.5 with minecraft:red_shulker_box[container=["
					+ "{slot:0,item:{id:\"minecraft:diamond\",count:24}},{slot:1,item:{id:\"minecraft:iron_ingot\",count:64}},"
					+ "{slot:2,item:{id:\"minecraft:iron_ingot\",count:30}},{slot:5,item:{id:\"minecraft:enchanted_golden_apple\",count:2}},"
					+ "{slot:9,item:{id:\"minecraft:oak_log\",count:64}},{slot:20,item:{id:\"minecraft:diamond_sword\",count:1}}]]");
			context.waitTicks(5);
			hoverInventorySlot(context, 8 + 5 * 18, 142);
			context.waitTicks(10);
			shot(context, "shulker_compact");
			context.getInput().holdKey(InputConstants.KEY_LSHIFT);
			context.waitTicks(10);
			shot(context, "shulker_full");
			context.getInput().releaseKey(InputConstants.KEY_LSHIFT);

			context.setScreen(() -> null);
			context.waitTicks(5);
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(5));
			context.waitTicks(15);
			shot(context, "shulker_hud");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(5);

			// Resource tracker: goals from the menu and a recipe, a chest with stone nearby, then complete one goal.
			BlockPos chest = player.offset(-1, 0, 2);
			world.getServer().runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ()
					+ " minecraft:chest{Items:[{Slot:0b,id:\"minecraft:stone\",count:40}]}");
			context.runOnClient(mc -> {
				TrackerFeature.INSTANCE.addGoal(Items.STONE, 64);
				TrackerFeature.INSTANCE.addGoal(Items.DIAMOND, 9);
				RecipeLookup.find(Items.BEACON).ifPresent(found ->
						found.scaledFor(1).forEach(TrackerFeature.INSTANCE::addGoal));
			});
			context.setScreen(() -> new TrackerScreen(TrackerFeature.INSTANCE, null));
			context.waitTicks(10);
			shot(context, "tracker_screen");
			context.setScreen(() -> null);
			context.getInput().lookAt(chest);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.setScreen(() -> null);
			context.waitTicks(10);
			shot(context, "tracker_hud");
			world.getServer().runCommand("give @a minecraft:diamond 9");
			context.waitTicks(12);
			shot(context, "tracker_completed");
			context.waitTicks(20);

			// Fullbright: midnight without and with fullbright (lightmap method), then the night vision method.
			world.getServer().runCommand("time set midnight");
			context.getInput().lookAt(log);
			context.waitTicks(20);
			shot(context, "fullbright_off");
			context.getInput().pressKey(FullbrightFeature.INSTANCE.key());
			context.waitTicks(30);
			shot(context, "fullbright_on");
			context.runOnClient(mc -> FullbrightFeature.INSTANCE.config().method = FullbrightMethod.NIGHT_VISION);
			context.waitTicks(10);
			shot(context, "fullbright_night_vision");
			context.runOnClient(mc -> {
				FullbrightFeature.INSTANCE.config().method = FullbrightMethod.AUTO;
				FullbrightFeature.INSTANCE.config().active = false;
			});
			world.getServer().runCommand("time set noon");
			context.waitTicks(20);

			// Enchanting table: 9 counted shelves, 2 blocked by a stone block, then the GUI with the chances panel.
			BlockPos table = player.offset(0, 0, -4);
			fill(world, table.offset(-3, 0, -3), table.offset(3, 2, 3), "air");
			fill(world, table.offset(-2, 0, -2), table.offset(2, 0, -2), "bookshelf");
			fill(world, table.offset(-2, 0, -2), table.offset(-2, 0, 2), "bookshelf");
			fill(world, table.offset(2, 0, 0), table.offset(2, 0, 1), "bookshelf");
			fill(world, table.offset(1, 0, 0), table.offset(1, 0, 0), "stone");
			fill(world, table, table, "enchanting_table");
			world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:lapis_lazuli 16");
			world.getServer().runCommand("item replace entity @a hotbar.4 with minecraft:diamond_pickaxe");
			world.getServer().runCommand("experience add @a 30 levels");
			world.getConnection().waitForClientboundPackets();
			context.getInput().lookAt(table);
			context.waitTicks(10);
			shot(context, "enchanting_markers");
			context.runOnClient(mc -> EnchantingFeature.INSTANCE.config().panelOpen = true);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			// Pickaxe into the item slot, lapis into the lapis slot.
			clickInventorySlot(context, 8 + 4 * 18, 142);
			clickInventorySlot(context, 15, 47);
			clickInventorySlot(context, 8 + 2 * 18, 142);
			clickInventorySlot(context, 35, 47);
			context.waitTicks(10);
			hoverInventorySlot(context, 60 + 40, 14 + 2 * 19);
			context.waitTicks(5);
			shot(context, "enchanting_slot_tooltip");
			hoverInventorySlot(context, 176 + 4 + 30, 40 + 11);
			context.waitTicks(5);
			shot(context, "enchanting_panel_hover");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Effect HUD: a mix of effects (regeneration is in its warning time) in all three styles, then the inventory.
			world.getServer().runCommand("effect give @a minecraft:speed 90 1");
			world.getServer().runCommand("effect give @a minecraft:regeneration 8 0");
			world.getServer().runCommand("effect give @a minecraft:poison 30 0");
			world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0");
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.waitTicks(30);
			shot(context, "effects_list");
			context.runOnClient(mc -> EffectHudFeature.INSTANCE.config().style = EffectStyle.RINGS);
			context.waitTicks(5);
			shot(context, "effects_rings");
			context.runOnClient(mc -> EffectHudFeature.INSTANCE.config().style = EffectStyle.COMPACT);
			context.waitTicks(5);
			shot(context, "effects_compact");
			context.runOnClient(mc -> EffectHudFeature.INSTANCE.config().style = EffectStyle.LIST);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			hoverInventorySlot(context, 190, 0);
			context.waitTicks(5);
			shot(context, "effects_inventory");
			context.setScreen(() -> null);
			world.getServer().runCommand("effect clear @a");
			context.waitTicks(10);

			// Inventory tools: a messy chest, search for "dia", lock the stone slot, then sort chest and inventory.
			BlockPos messy = player.offset(1, 0, -1);
			world.getServer().runCommand("setblock " + messy.getX() + " " + messy.getY() + " " + messy.getZ()
					+ " minecraft:chest{Items:[{Slot:5b,id:\"minecraft:stone\",count:12},{Slot:2b,id:\"minecraft:dirt\",count:30},"
					+ "{Slot:10b,id:\"minecraft:diamond\",count:3},{Slot:15b,id:\"minecraft:stone\",count:20},"
					+ "{Slot:20b,id:\"minecraft:oak_log\",count:8},{Slot:25b,id:\"minecraft:dirt\",count:34},"
					+ "{Slot:7b,id:\"minecraft:iron_ingot\",count:5},{Slot:13b,id:\"minecraft:diamond_sword\",count:1}]}");
			world.getServer().runCommand("give @a minecraft:diamond 5");
			world.getServer().runCommand("give @a minecraft:dirt 7");
			world.getConnection().waitForClientboundPackets();
			context.getInput().lookAt(messy);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.runOnClient(mc -> {
				for (var widget : Screens.getWidgets(mc.gui.screen())) {
					if (widget instanceof EditBox box) {
						box.setValue("dia");
					}
				}
				InventoryToolsFeature.INSTANCE.toggleLock(9);
			});
			context.waitTicks(5);
			shot(context, "invtools_search");
			context.runOnClient(mc -> {
				for (var widget : Screens.getWidgets(mc.gui.screen())) {
					if (widget instanceof EditBox box) {
						box.setValue("");
					}
				}
			});
			hoverInventorySlot(context, 8, 18);
			context.waitTicks(2);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
			context.waitTicks(2);
			hoverInventorySlot(context, 8 + 18, 104);
			context.waitTicks(2);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
			context.waitTicks(3);
			hoverInventorySlot(context, 200, -40);
			context.waitTicks(10);
			shot(context, "invtools_sorted");
			context.runOnClient(mc -> InventoryToolsFeature.INSTANCE.toggleLock(9));
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Food HUD: lose some food and health, hold bread (preview), cooked beef in the hotbar (best food), then the tooltip.
			world.getServer().runCommand("effect give @a minecraft:hunger 4 120");
			world.getServer().runCommand("damage @a 7 minecraft:generic");
			world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:bread 5");
			world.getServer().runCommand("item replace entity @a hotbar.4 with minecraft:cooked_beef 3");
			context.waitTicks(90);
			world.getServer().runCommand("effect clear @a minecraft:hunger");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(2));
			context.waitTicks(10);
			shot(context, "food_hud");
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			hoverInventorySlot(context, 8 + 4 * 18, 142);
			context.waitTicks(5);
			shot(context, "food_tooltip");
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(5);

			// Storage memory: open a double chest and a barrel, name the chest, search, mark and look at it.
			BlockPos storageLeft = player.offset(-3, 0, -2);
			String loot = "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:24},{Slot:1b,id:\"minecraft:gold_ingot\",count:40},"
					+ "{Slot:2b,id:\"minecraft:redstone\",count:64},{Slot:3b,id:\"minecraft:redstone\",count:50},"
					+ "{Slot:9b,id:\"minecraft:emerald\",count:7},{Slot:10b,id:\"minecraft:lapis_lazuli\",count:33}]}";
			world.getServer().runCommand("setblock " + storageLeft.getX() + " " + storageLeft.getY() + " " + storageLeft.getZ()
					+ " minecraft:chest[type=left,facing=north]" + loot);
			world.getServer().runCommand("setblock " + (storageLeft.getX() + 1) + " " + storageLeft.getY() + " " + storageLeft.getZ()
					+ " minecraft:chest[type=right,facing=north]{Items:[{Slot:4b,id:\"minecraft:iron_ingot\",count:64}]}");
			BlockPos barrel = player.offset(3, 0, -2);
			world.getServer().runCommand("setblock " + barrel.getX() + " " + barrel.getY() + " " + barrel.getZ()
					+ " minecraft:barrel{Items:[{Slot:0b,id:\"minecraft:diamond\",count:3},{Slot:1b,id:\"minecraft:cobblestone\",count:64}]}");
			world.getConnection().waitForClientboundPackets();
			context.getInput().lookAt(storageLeft);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(15);
			context.runOnClient(mc -> {
				EditBox nameBox = null;
				for (var widget : Screens.getWidgets(mc.gui.screen())) {
					if (widget instanceof EditBox box && !box.isVisible()) {
						nameBox = box;
					}
				}
				if (nameBox != null) {
					nameBox.setVisible(true);
					nameBox.setValue("Erze");
					mc.gui.screen().setFocused(nameBox);
				}
			});
			context.waitTicks(3);
			shot(context, "storage_naming");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			shot(context, "storage_named");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.getInput().lookAt(barrel);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(15);
			context.setScreen(() -> null);
			context.waitTicks(5);
			// Ender chest: open it once, then hover an ender chest item in the inventory.
			world.getServer().runCommand("item replace entity @a enderchest.0 with minecraft:diamond 10");
			world.getServer().runCommand("item replace entity @a enderchest.1 with minecraft:totem_of_undying");
			world.getServer().runCommand("item replace entity @a enderchest.5 with minecraft:ender_pearl 16");
			world.getServer().runCommand("item replace entity @a enderchest.6 with minecraft:shulker_box");
			BlockPos enderChest = player.offset(3, 0, 0);
			world.getServer().runCommand("setblock " + enderChest.getX() + " " + enderChest.getY() + " " + enderChest.getZ() + " minecraft:ender_chest");
			world.getServer().runCommand("item replace entity @a inventory.0 with minecraft:ender_chest");
			world.getConnection().waitForClientboundPackets();
			context.getInput().lookAt(enderChest);
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(15);
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			hoverInventorySlot(context, 8, 84);
			context.waitTicks(5);
			shot(context, "storage_ender_tooltip");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.setScreen(() -> new StorageScreen(null, ""));
			context.waitTicks(5);
			shot(context, "storage_items");
			context.setScreen(() -> new StorageScreen(null, "dia"));
			context.waitTicks(5);
			shot(context, "storage_search");
			context.setScreen(() -> null);
			context.runOnClient(mc -> StorageFeature.INSTANCE.highlight(new java.util.ArrayList<>(StorageFeature.INSTANCE.store().all())));
			context.getInput().lookAt(player.offset(6, 0, 6));
			context.waitTicks(5);
			shot(context, "storage_highlight");
			context.getInput().lookAt(storageLeft);
			context.waitTicks(5);
			shot(context, "storage_look");
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			context.runOnClient(mc -> {
				for (var widget : Screens.getWidgets(mc.gui.screen())) {
					if (widget instanceof EditBox box) {
						box.setValue("redst");
					}
				}
			});
			context.waitTicks(3);
			shot(context, "storage_hint");
			context.runOnClient(mc -> {
				for (var widget : Screens.getWidgets(mc.gui.screen())) {
					if (widget instanceof EditBox box) {
						box.setValue("");
					}
				}
			});
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Map: a waypoint nearby, one shared through chat (Xaero format), the minimap, then the full map.
			context.runOnClient(mc -> {
				Waypoint home = new Waypoint("Home", (int) mc.player.getX() + 12, (int) mc.player.getY(), (int) mc.player.getZ() - 8,
						"minecraft:overworld");
				home.color = 14;
				home.set = "Basis";
				MapFeature.INSTANCE.waypoints().add(home);
				// A second set that is hidden: off in the layer sidebar, and its waypoint shows on neither map.
				Waypoint farm = new Waypoint("Weizen", (int) mc.player.getX() + 25, (int) mc.player.getY(), (int) mc.player.getZ() + 15,
						"minecraft:overworld");
				farm.color = 10;
				farm.set = "Farmen";
				MapFeature.INSTANCE.waypoints().add(farm);
				MapFeature.INSTANCE.waypoints().sets().put("Farmen", false);
				Waypoint shared = new Waypoint("Mine", (int) mc.player.getX() - 30, 40, (int) mc.player.getZ() + 20, "minecraft:overworld");
				shared.color = 12;
				mc.player.connection.sendChat(XaeroChat.format(shared));
			});
			for (String mob : new String[]{"husk ~9 ~ ~-5", "villager ~-7 ~ ~6", "pig ~5 ~ ~9", "sheep ~-10 ~ ~-4", "creeper ~14 ~ ~3",
					"bat ~-4 ~1 ~-12", "chicken ~-9 ~12 ~8", "cow ~10 ~25 ~10"}) {
				world.getServer().runCommand("execute at @p run summon minecraft:" + mob + " {NoAI:1b}");
			}
			// A tree canopy above the player must not switch the minimap to the cave view.
			world.getServer().runCommand("execute at @p run fill ~-3 ~6 ~-3 ~3 ~8 ~3 minecraft:oak_leaves[persistent=true]");
			context.waitTicks(60);
			shot(context, "map_minimap");
			boolean caveUnderTree = context.computeOnClient(mc -> MapFeature.INSTANCE.world().caveActive());
			if (caveUnderTree) {
				throw new AssertionError("Cave view switched on under a tree canopy");
			}
			world.getServer().runCommand("execute at @p run fill ~-3 ~6 ~-3 ~3 ~8 ~3 minecraft:air");
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(20);
			shot(context, "map_full");
			// Position sharing: switch it on, get two shared players, see them on the minimap and the map (hovering Alex).
			boolean syncSupported = context.computeOnClient(mc -> MapFeature.INSTANCE.sync().supported());
			LOGGER.info("Position sync supported: {}", syncSupported);
			context.setScreen(() -> null);
			context.runOnClient(mc -> MapFeature.INSTANCE.config().sharePosition = true);
			context.waitTicks(5);
			sendSharedPlayers(world);
			context.waitTicks(10);
			int sharedCount = context.computeOnClient(mc -> MapFeature.INSTANCE.sync().count());
			LOGGER.info("Shared players received: {}", sharedCount);
			shot(context, "map_sync_minimap");
			context.setScreen(() -> new MapScreen(null));
			sendSharedPlayers(world);
			context.waitTicks(10);
			double[] alex = context.computeOnClient(mc -> {
				double scale = mc.getWindow().getGuiScale();
				return new double[]{mc.getWindow().getWidth() / 2.0 + 80 * scale, mc.getWindow().getHeight() / 2.0 - 50 * scale};
			});
			context.getInput().setCursorPos(alex[0], alex[1]);
			context.waitTicks(3);
			shot(context, "map_sync_full");
			context.runOnClient(mc -> MapFeature.INSTANCE.config().sharePosition = false);
			context.setScreen(() -> null);
			context.waitTicks(3);
			// Window mode: the world shows around and through the map, and W still walks.
			context.runOnClient(mc -> MapFeature.INSTANCE.config().mapWindowed = true);
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(5);
			double[] before = context.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getZ()});
			context.getInput().holdKeyFor(InputConstants.KEY_W, 20);
			double[] after = context.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getZ()});
			LOGGER.info("Walked with the map open: {} blocks", Math.hypot(after[0] - before[0], after[1] - before[1]));
			shot(context, "map_window");
			context.runOnClient(mc -> MapFeature.INSTANCE.config().mapWindowed = false);
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(5);
			// Right-click menu in the middle of the map.
			double[] middle = context.computeOnClient(mc -> new double[]{mc.getWindow().getWidth() / 2.0 + 40, mc.getWindow().getHeight() / 2.0 + 30});
			context.getInput().setCursorPos(middle[0], middle[1]);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(3);
			shot(context, "map_menu");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.setScreen(() -> new MapScreen(null));
			context.waitTicks(5);
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(40);
			shot(context, "map_nether");
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(20);
			shot(context, "map_end");
			context.setScreen(() -> new WaypointListScreen(null));
			context.waitTicks(5);
			shot(context, "map_waypoint_list");
			context.setScreen(() -> new WaypointEditScreen(null, MapFeature.INSTANCE.waypoints().all().getFirst(), false));
			context.waitTicks(5);
			shot(context, "map_waypoint_edit");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// Smooth GUI: right after opening (mid animation) and when settled.
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			shot(context, "smooth_gui_opening");
			context.waitTicks(10);
			shot(context, "smooth_gui_open");
			context.setScreen(() -> null);
			context.waitTicks(5);

			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(10);
			shot(context, "hud_editor");
			context.setScreen(() -> null);
		}
	}

	/** Moves the cursor to the centre of an inventory slot given in container coordinates. */
	private static void hoverInventorySlot(ClientGameTestContext context, int slotX, int slotY) {
		double[] pos = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			return new double[]{(left + slotX + 8) * scale, (top + slotY + 8) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void clickInventorySlot(ClientGameTestContext context, int slotX, int slotY) {
		hoverInventorySlot(context, slotX, slotY);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void fill(TestSingleplayerContext world, BlockPos from, BlockPos to, String block) {
		world.getServer().runCommand("fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " "
				+ to.getX() + " " + to.getY() + " " + to.getZ() + " minecraft:" + block);
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOGGER.info("Screenshot {} -> {}", name, context.takeScreenshot(name));
	}
}
