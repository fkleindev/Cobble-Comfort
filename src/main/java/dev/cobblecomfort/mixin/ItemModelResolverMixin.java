package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.features.plush.PlushFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totems use the plush model instead of the one their item (or a resource pack) names. Only the model lookup changes:
 * the plush still becomes a normal item layer, so everything that handles held items afterwards sees it as usual.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@Shadow
	private ItemModel getItemModel(Identifier id) {
		throw new AssertionError();
	}

	@Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$totemPlush(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
			Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		PlushFeature plush = PlushFeature.INSTANCE;
		if (!plush.begin(item, displayContext, owner)) {
			return;
		}
		try {
			output.setOversizedInGui(false);
			getItemModel(PlushFeature.MODEL_ID).update(output, item, (ItemModelResolver) (Object) this, displayContext,
					level instanceof ClientLevel clientLevel ? clientLevel : null, owner, seed);
			if (plush.animating()) {
				output.setAnimated();
			}
		} finally {
			plush.end();
		}
		ci.cancel();
	}

	/** Renamed weapons and tools get the name charm as an extra layer on top of their own model. */
	@Inject(method = "appendItemLayers", at = @At("RETURN"))
	private void cobblecomfort$nameCharm(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
			Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		NameCharmFeature.INSTANCE.appendLayer(output, item, displayContext, owner);
	}
}
