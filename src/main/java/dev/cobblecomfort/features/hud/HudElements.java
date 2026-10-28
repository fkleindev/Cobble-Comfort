package dev.cobblecomfort.features.hud;

import dev.cobblecomfort.features.armorhud.ArmorHudFeature;
import dev.cobblecomfort.features.effects.EffectHudFeature;
import dev.cobblecomfort.features.fullbright.FullbrightFeature;
import dev.cobblecomfort.features.infohud.InfoHudFeature;
import dev.cobblecomfort.features.lookinfo.LookInfoFeature;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.pickup.PickupLogFeature;
import dev.cobblecomfort.features.shulker.ShulkerPreviewFeature;
import dev.cobblecomfort.features.tracker.TrackerFeature;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** All vanilla HUD elements the customizer can control, with their vanilla layout. */
public final class HudElements {
	private static final List<HudElementDef> ALL = new ArrayList<>();

	public static final HudElementDef HOTBAR = add("hotbar", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 91, h - 22, 182, 22), null, VanillaHudElements.HOTBAR);
	public static final HudElementDef HEALTH = add("health", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 91, h - 39, 81, 9), HudElements::lowHealth, VanillaHudElements.HEALTH_BAR);
	public static final HudElementDef ARMOR = add("armor", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 91, h - 49, 81, 9), null, VanillaHudElements.ARMOR_BAR);
	public static final HudElementDef FOOD = add("food", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 + 10, h - 39, 81, 9), HudElements::lowFood, VanillaHudElements.FOOD_BAR);
	public static final HudElementDef AIR = add("air", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 + 10, h - 49, 81, 9), HudElements::lowAir, VanillaHudElements.AIR_BAR);
	public static final HudElementDef MOUNT_HEALTH = add("mount_health", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 + 10, h - 39, 81, 9), null, VanillaHudElements.MOUNT_HEALTH);
	public static final HudElementDef INFO_BAR = add("info_bar", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 91, h - 29, 182, 5), null,
			VanillaHudElements.INFO_BAR, VanillaHudElements.INFO_BAR_FOREGROUND);
	public static final HudElementDef EXPERIENCE_LEVEL = add("experience_level", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 20, h - 36, 40, 9), null, VanillaHudElements.EXPERIENCE_LEVEL);
	public static final HudElementDef HELD_ITEM_TOOLTIP = add("held_item_tooltip", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 75, h - 59, 150, 9), null, VanillaHudElements.HELD_ITEM_TOOLTIP);
	/** Not vanilla: the look info panel of {@link LookInfoFeature}, registered as its own HUD layer. */
	public static final HudElementDef LOOK_INFO = add("look_info", Anchor.BOTTOM_CENTER,
			(w, h) -> LookInfoFeature.INSTANCE.defaultBounds(w, h), null, LookInfoFeature.LAYER_ID);
	/** Not vanilla: the armor HUD of {@link ArmorHudFeature}. */
	public static final HudElementDef ARMOR_HUD = add("armor_hud", Anchor.BOTTOM_CENTER,
			(w, h) -> ArmorHudFeature.INSTANCE.defaultBounds(w, h), null, ArmorHudFeature.LAYER_ID);
	/** Not vanilla: the resource tracker of {@link TrackerFeature}. */
	public static final HudElementDef TRACKER = add("tracker", Anchor.CENTER_LEFT,
			(w, h) -> TrackerFeature.INSTANCE.hud().defaultBounds(w, h), null, TrackerFeature.LAYER_ID);
	/** Not vanilla: the "Fullbright" indicator of {@link FullbrightFeature}. */
	public static final HudElementDef FULLBRIGHT = add("fullbright", Anchor.TOP_LEFT,
			(w, h) -> FullbrightFeature.INSTANCE.defaultBounds(w, h), null, FullbrightFeature.LAYER_ID);
	/** Not vanilla: the held shulker box panel of {@link ShulkerPreviewFeature}. */
	public static final HudElementDef SHULKER_PREVIEW = add("shulker_preview", Anchor.BOTTOM_RIGHT,
			(w, h) -> ShulkerPreviewFeature.INSTANCE.defaultBounds(w, h), null, ShulkerPreviewFeature.LAYER_ID);
	/** Not vanilla: the effect list of {@link EffectHudFeature}, replacing the vanilla effect icons. */
	public static final HudElementDef EFFECTS = add("effects", Anchor.TOP_RIGHT,
			(w, h) -> EffectHudFeature.INSTANCE.defaultBounds(w, h), null, EffectHudFeature.LAYER_ID);
	/** Not vanilla: the coordinates and info panel of {@link InfoHudFeature}. */
	public static final HudElementDef INFO_HUD = add("info_hud", Anchor.TOP_LEFT,
			(w, h) -> InfoHudFeature.INSTANCE.defaultBounds(w, h), null, InfoHudFeature.LAYER_ID);
	/** Not vanilla: the minimap of {@link MapFeature}. */
	public static final HudElementDef MINIMAP = add("minimap", Anchor.TOP_RIGHT,
			(w, h) -> MapFeature.INSTANCE.defaultBounds(w, h), null, MapFeature.LAYER_ID);
	/** Not vanilla: the pickup list of {@link PickupLogFeature}. */
	public static final HudElementDef PICKUP_LOG = add("pickup_log", Anchor.CENTER_RIGHT,
			(w, h) -> PickupLogFeature.INSTANCE.defaultBounds(w, h), null, PickupLogFeature.LAYER_ID);
	public static final HudElementDef OVERLAY_MESSAGE = add("overlay_message", Anchor.BOTTOM_CENTER,
			(w, h) -> new Rect(w / 2 - 100, h - 72, 200, 9), null, VanillaHudElements.OVERLAY_MESSAGE);
	public static final HudElementDef CROSSHAIR = add("crosshair", Anchor.CENTER,
			(w, h) -> new Rect(w / 2 - 7, h / 2 - 7, 15, 15), null, VanillaHudElements.CROSSHAIR);
	public static final HudElementDef TITLE = add("title", Anchor.CENTER,
			(w, h) -> new Rect(w / 2 - 150, h / 2 - 40, 300, 60), null, VanillaHudElements.TITLE_AND_SUBTITLE);
	public static final HudElementDef MOB_EFFECTS = add("mob_effects", Anchor.TOP_RIGHT,
			(w, h) -> new Rect(w - 150, 0, 150, 52), null, VanillaHudElements.MOB_EFFECTS);
	public static final HudElementDef BOSS_BAR = add("boss_bar", Anchor.TOP_CENTER,
			(w, h) -> new Rect(w / 2 - 91, 0, 182, 40), null, VanillaHudElements.BOSS_BAR);
	public static final HudElementDef PLAYER_LIST = add("player_list", Anchor.TOP_CENTER,
			(w, h) -> new Rect(w / 2 - 150, 10, 300, 120), null, VanillaHudElements.PLAYER_LIST);
	public static final HudElementDef SCOREBOARD = add("scoreboard", Anchor.CENTER_RIGHT,
			(w, h) -> new Rect(w - 100, h / 2 - 60, 100, 120), null, VanillaHudElements.SCOREBOARD);
	public static final HudElementDef SUBTITLES = add("subtitles", Anchor.BOTTOM_RIGHT,
			(w, h) -> new Rect(w - 150, h - 110, 150, 80), null, VanillaHudElements.SUBTITLES);

	private HudElements() {
	}

	private static HudElementDef add(String key, Anchor anchor, HudElementDef.BoundsFn bounds,
			BooleanSupplier low, Identifier... ids) {
		HudElementDef def = new HudElementDef(key, List.of(ids), bounds, anchor, low);
		ALL.add(def);
		return def;
	}

	public static List<HudElementDef> all() {
		return Collections.unmodifiableList(ALL);
	}

	private static boolean lowHealth() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getHealth() <= player.getMaxHealth() * 0.3f;
	}

	private static boolean lowFood() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getFoodData().getFoodLevel() <= 6;
	}

	private static boolean lowAir() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getAirSupply() <= player.getMaxAirSupply() * 0.3f;
	}
}
