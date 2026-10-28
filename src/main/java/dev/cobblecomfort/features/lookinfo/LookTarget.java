package dev.cobblecomfort.features.lookinfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Everything shown for the current target.
 *
 * @param identity identifies the target (block position + state or entity id) so the renderer can tell
 *                 a new target from an update of the same one
 */
public record LookTarget(Object identity, ItemStack icon, Component name, Component modName, List<InfoLine> lines) {
}
