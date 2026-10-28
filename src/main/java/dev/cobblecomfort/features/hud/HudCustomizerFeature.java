package dev.cobblecomfort.features.hud;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.ElementState;
import dev.cobblecomfort.features.hud.render.HudFrame;
import dev.cobblecomfort.features.hud.render.TransformingHudElement;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Makes every vanilla HUD element movable, scalable, stylable and animatable. */
public final class HudCustomizerFeature extends Feature<HudConfig> {
	public static final HudCustomizerFeature INSTANCE = new HudCustomizerFeature();

	private final Map<String, ElementState> states = new HashMap<>();

	private HudCustomizerFeature() {
		super("hud", HudConfig.class, HudConfig::new);
	}

	@Override
	public void init() {
		HudElementRegistry.addFirst(CobbleComfortClient.id("frame_counter"), (g, delta) -> HudFrame.next());
		for (HudElementDef def : HudElements.all()) {
			for (Identifier layer : def.vanillaIds()) {
				HudElementRegistry.replaceElement(layer, original -> new TransformingHudElement(def, original));
			}
		}
	}

	@Override
	public ConfigCategory buildCategory() {
		return HudConfigUi.buildCategory(this);
	}

	public ElementState state(HudElementDef def) {
		return states.computeIfAbsent(def.key(), k -> new ElementState());
	}

	public void resetElement(HudElementDef def) {
		config.elements.put(def.key(), HudConfig.defaultsFor(def));
		state(def).resetAnimation();
	}

	public void applyLayout(Map<String, HudElementConfig> elements) {
		config.elements.clear();
		config.elements.putAll(elements);
		states.values().forEach(ElementState::resetAnimation);
		save();
	}
}
