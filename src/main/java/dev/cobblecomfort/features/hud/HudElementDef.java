package dev.cobblecomfort.features.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Describes one configurable HUD element.
 *
 * @param key           config key and translation suffix
 * @param vanillaIds    Fabric HUD layers rendered as this element (several layers can share one element)
 * @param vanillaBounds where vanilla draws the element for a given scaled screen size; used as the
 *                      pivot for scaling/positioning and as a placeholder when nothing is drawn
 * @param naturalAnchor the screen anchor vanilla positions the element against
 * @param lowCondition  optional "needs attention" check that drives the low-value pulse, or null
 */
public record HudElementDef(String key, List<Identifier> vanillaIds, BoundsFn vanillaBounds,
		Anchor naturalAnchor, BooleanSupplier lowCondition) {

	@FunctionalInterface
	public interface BoundsFn {
		Rect get(float width, float height);
	}

	public Component displayName() {
		return Component.translatable("cobblecomfort.hud.element." + key);
	}

	public boolean supportsPulse() {
		return lowCondition != null;
	}
}
