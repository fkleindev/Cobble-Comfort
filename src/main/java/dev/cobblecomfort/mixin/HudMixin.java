package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.chat.ChatFeature;
import dev.cobblecomfort.features.effects.EffectHudFeature;
import dev.cobblecomfort.features.food.FoodHudFeature;
import dev.cobblecomfort.features.hotbar.HotbarFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.cobblecomfort.features.shulker.ShulkerPreviewFeature;
import dev.cobblecomfort.features.zoom.ZoomFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	/** Hotbar slots 1-9 are drawn with seeds 1-9; the offhand slot uses a higher seed. */
	private static final int HOTBAR_SEEDS = 9;

	@Shadow
	protected abstract Player getCameraPlayer();

	/** Hides the whole HUD while zooming, but keeps the zoom overlay (bars, scope, zoom level). */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$hideHudWhileZooming(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ZoomFeature.INSTANCE.hideHud()) {
			ZoomFeature.INSTANCE.drawOverlay(g);
			ci.cancel();
		}
	}

	/** The effect HUD replaces vanilla's effect icons. */
	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$hideVanillaEffects(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (EffectHudFeature.INSTANCE.replacesVanilla()) {
			ci.cancel();
		}
	}

	// ---- food HUD (inside vanilla's food and heart drawing, so HUD editor transforms apply)

	@Inject(method = "extractFood", at = @At("HEAD"))
	private void cobblecomfort$beforeFood(GuiGraphicsExtractor g, Player player, int top, int right, CallbackInfo ci) {
		FoodHudFeature.INSTANCE.beforeFood(g, player, top, right);
	}

	@Inject(method = "extractFood", at = @At("TAIL"))
	private void cobblecomfort$afterFood(GuiGraphicsExtractor g, Player player, int top, int right, CallbackInfo ci) {
		FoodHudFeature.INSTANCE.afterFood(g, player, top, right);
	}

	@Inject(method = "extractHearts", at = @At("HEAD"))
	private void cobblecomfort$captureHearts(GuiGraphicsExtractor g, Player player, int x, int y, int rowHeight, int regenIndex,
			float maxHealth, int health, int displayHealth, int absorption, boolean blink, CallbackInfo ci) {
		FoodHudFeature.INSTANCE.captureHearts(x, y, rowHeight, maxHealth);
	}

	@Inject(method = "extractHearts", at = @At("TAIL"))
	private void cobblecomfort$afterHearts(GuiGraphicsExtractor g, Player player, int x, int y, int rowHeight, int regenIndex,
			float maxHealth, int health, int displayHealth, int absorption, boolean blink, CallbackInfo ci) {
		FoodHudFeature.INSTANCE.afterHearts(g, player);
	}

	// ---- smooth hotbar

	@Inject(method = "extractItemHotbar", at = @At("HEAD"))
	private void cobblecomfort$beginHotbar(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		HotbarFeature.INSTANCE.beginHotbar(getCameraPlayer());
	}

	/** The second sprite of the hotbar is the selection frame; let it glide. */
	@WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			ordinal = 1))
	private void cobblecomfort$glideSelection(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
			int x, int y, int width, int height, Operation<Void> original) {
		float offset = HotbarFeature.INSTANCE.selectionOffset();
		g.pose().pushMatrix();
		g.pose().translate(offset, 0f);
		original.call(g, pipeline, sprite, x, y, width, height);
		g.pose().popMatrix();
	}

	@Inject(method = "extractItemHotbar", at = @At("RETURN"))
	private void cobblecomfort$endHotbar(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		HotbarFeature.INSTANCE.endHotbar(g, getCameraPlayer());
	}

	/** Scale and lift of the slot; declared before the rarity indicator so it wraps it too. */
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void cobblecomfort$beginHotbarSlot(GuiGraphicsExtractor g, int x, int y, DeltaTracker deltaTracker,
			Player player, ItemStack stack, int seed, CallbackInfo ci) {
		HotbarFeature.INSTANCE.beginSlot(g, seed <= HOTBAR_SEEDS ? seed - 1 : -1, x, y);
	}

	/** Rarity indicator behind hotbar items. */
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void cobblecomfort$rarityIndicator(GuiGraphicsExtractor g, int x, int y, DeltaTracker deltaTracker,
			Player player, ItemStack stack, int seed, CallbackInfo ci) {
		RarityFeature.INSTANCE.drawSlot(g, stack, x, y, false, RarityFeature.Place.HOTBAR);
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void cobblecomfort$endHotbarSlot(GuiGraphicsExtractor g, int x, int y, DeltaTracker deltaTracker,
			Player player, ItemStack stack, int seed, CallbackInfo ci) {
		ShulkerPreviewFeature.INSTANCE.drawFillBar(g, stack, x, y, true);
		if (seed <= HOTBAR_SEEDS) {
			InventoryToolsFeature.INSTANCE.decorateHotbarSlot(g, seed - 1, x, y);
			FoodHudFeature.INSTANCE.decorateHotbarSlot(g, seed - 1, x, y);
		}
		HotbarFeature.INSTANCE.endSlot(g, seed <= HOTBAR_SEEDS ? seed - 1 : -1, x, y, stack);
	}

	/** Chat comfort keeps the chat across disconnects and server switches, with a separator line. */
	@WrapOperation(method = "onDisconnected",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private void cobblecomfort$keepChat(ChatComponent chat, boolean clearHistory, Operation<Void> original) {
		if (ChatFeature.INSTANCE.keepOnDisconnect()) {
			ChatFeature.INSTANCE.markDisconnect(chat);
		} else {
			original.call(chat, clearHistory);
		}
	}
}
