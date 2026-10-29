package dev.cobblecomfort;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.core.FeatureManager;
import dev.cobblecomfort.core.config.CobbleConfigScreen;
import dev.cobblecomfort.features.armorhud.ArmorHudFeature;
import dev.cobblecomfort.features.chat.ChatFeature;
import dev.cobblecomfort.features.crosshair.CrosshairFeature;
import dev.cobblecomfort.features.enchantdesc.EnchantDescFeature;
import dev.cobblecomfort.features.enchanting.EnchantingFeature;
import dev.cobblecomfort.features.effects.EffectHudFeature;
import dev.cobblecomfort.features.food.FoodHudFeature;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.fullbright.FullbrightFeature;
import dev.cobblecomfort.features.hotbar.HotbarFeature;
import dev.cobblecomfort.features.infohud.InfoHudFeature;
import dev.cobblecomfort.features.hud.HudCustomizerFeature;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hudanim.HudAnimFeature;
import dev.cobblecomfort.features.itembars.ItemBarsFeature;
import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.lookinfo.LookInfoFeature;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.pickup.PickupLogFeature;
import dev.cobblecomfort.features.preview.ItemPreviewFeature;
import dev.cobblecomfort.features.storage.StorageFeature;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.phantom.PhantomFeature;
import dev.cobblecomfort.features.trade.TradeFeature;
import dev.cobblecomfort.features.toasts.ToastsFeature;
import dev.cobblecomfort.features.transitions.TransitionsFeature;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.cobblecomfort.features.shulker.ShulkerPreviewFeature;
import dev.cobblecomfort.features.smoothgui.SmoothGuiFeature;
import dev.cobblecomfort.features.tracker.TrackerFeature;
import dev.cobblecomfort.features.zoom.ZoomFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CobbleComfortClient implements ClientModInitializer {
	public static final String MOD_ID = "cobblecomfort";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort");

	private static KeyMapping.Category keyCategory;
	private static KeyMapping openConfigKey;
	private static KeyMapping openHudEditorKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** The "Cobble Comfort" category in the controls screen, shared by all features. */
	public static KeyMapping.Category keyCategory() {
		return keyCategory;
	}

	@Override
	public void onInitializeClient() {
		keyCategory = KeyMapping.Category.register(id("main"));

		// Features that add their own HUD layers must come before the HUD customizer, which wraps all layers.
		FeatureManager.register(LookInfoFeature.INSTANCE);
		FeatureManager.register(ArmorHudFeature.INSTANCE);
		FeatureManager.register(TrackerFeature.INSTANCE);
		FeatureManager.register(ZoomFeature.INSTANCE);
		FeatureManager.register(FullbrightFeature.INSTANCE);
		FeatureManager.register(ShulkerPreviewFeature.INSTANCE);
		FeatureManager.register(ItemPreviewFeature.INSTANCE);
		FeatureManager.register(EffectHudFeature.INSTANCE);
		FeatureManager.register(InfoHudFeature.INSTANCE);
		FeatureManager.register(MapFeature.INSTANCE);
		FeatureManager.register(PickupLogFeature.INSTANCE);
		FeatureManager.register(PhantomFeature.INSTANCE);
		FeatureManager.register(HudCustomizerFeature.INSTANCE);
		FeatureManager.register(RarityFeature.INSTANCE);
		FeatureManager.register(InventoryFxFeature.INSTANCE);
		FeatureManager.register(InventoryToolsFeature.INSTANCE);
		FeatureManager.register(SmoothGuiFeature.INSTANCE);
		FeatureManager.register(EnchantDescFeature.INSTANCE);
		FeatureManager.register(EnchantingFeature.INSTANCE);
		FeatureManager.register(HotbarFeature.INSTANCE);
		FeatureManager.register(FoodHudFeature.INSTANCE);
		FeatureManager.register(HudAnimFeature.INSTANCE);
		FeatureManager.register(ItemBarsFeature.INSTANCE);
		FeatureManager.register(ToastsFeature.INSTANCE);
		FeatureManager.register(CrosshairFeature.INSTANCE);
		FeatureManager.register(TransitionsFeature.INSTANCE);
		FeatureManager.register(StorageFeature.INSTANCE);
		FeatureManager.register(FurnaceFeature.INSTANCE);
		FeatureManager.register(TradeFeature.INSTANCE);
		FeatureManager.register(PetFeature.INSTANCE);
		FeatureManager.register(ChatFeature.INSTANCE);
		FeatureManager.init();

		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.open_config", InputConstants.UNKNOWN.getValue(), keyCategory));
		openHudEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.open_hud_editor", InputConstants.UNKNOWN.getValue(), keyCategory));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigKey.consumeClick()) {
				client.gui.setScreen(CobbleConfigScreen.create(client.gui.screen()));
			}
			while (openHudEditorKey.consumeClick()) {
				client.gui.setScreen(new HudEditorScreen(client.gui.screen()));
			}
		});
	}
}
