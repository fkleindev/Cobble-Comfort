package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.hudanim.HudAnimFeature;
import dev.cobblecomfort.features.hudanim.HudAnimFeature.Row;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD animations: hearts, food, armour and air icons, and the experience level. */
@Mixin(Hud.class)
public abstract class HudAnimMixin {
	private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
	private static final int ICON = 9;
	private static final int ICON_STEP = 8;
	private static final int ICONS = 10;

	// ---- hearts

	/** The health trail replaces vanilla's blinking lost hearts. */
	@WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"))
	private void cobblecomfort$hearts(Hud hud, GuiGraphicsExtractor g, Player player, int x, int y, int rowHeight, int regenIndex,
			float maxHealth, int health, int displayHealth, int absorption, boolean blink, Operation<Void> original) {
		HudAnimFeature anim = HudAnimFeature.INSTANCE;
		anim.beginHearts(player, maxHealth, health, absorption);
		original.call(hud, g, player, x, y, rowHeight, regenIndex, maxHealth, health, anim.healthTrail() ? health : displayHealth,
				absorption, blink);
		anim.endRow(Row.HEARTS, g);
	}

	@WrapOperation(method = "extractHearts", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractHeart(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Hud$HeartType;IIZZZ)V"))
	private void cobblecomfort$heart(Hud hud, GuiGraphicsExtractor g, @Coerce Object type, int x, int y, boolean hardcore,
			boolean blinking, boolean half, Operation<Void> original) {
		boolean container = "CONTAINER".equals(((Enum<?>) type).name());
		HudAnimFeature.INSTANCE.heart(g, container, blinking, half, hardcore, x, y,
				() -> original.call(hud, g, type, x, y, hardcore, blinking, half));
	}

	// ---- food

	@Inject(method = "extractFood", at = @At("HEAD"))
	private void cobblecomfort$beginFood(GuiGraphicsExtractor g, Player player, int top, int right, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.beginRow(Row.FOOD, ICONS);
	}

	@WrapOperation(method = "extractFood", at = @At(value = "INVOKE", target = BLIT))
	private void cobblecomfort$food(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, Operation<Void> original, @Local(argsOnly = true, ordinal = 1) int right) {
		HudAnimFeature.INSTANCE.icon(Row.FOOD, g, (right - ICON - x) / ICON_STEP, sprite, x, y,
				() -> original.call(g, pipeline, sprite, x, y, width, height));
	}

	@Inject(method = "extractFood", at = @At("RETURN"))
	private void cobblecomfort$endFood(GuiGraphicsExtractor g, Player player, int top, int right, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.endRow(Row.FOOD, g);
	}

	// ---- armour

	@Inject(method = "extractArmor", at = @At("HEAD"))
	private static void cobblecomfort$beginArmor(GuiGraphicsExtractor g, Player player, int top, int rows, int rowHeight, int left,
			CallbackInfo ci) {
		HudAnimFeature.INSTANCE.beginRow(Row.ARMOR, ICONS);
	}

	@WrapOperation(method = "extractArmor", at = @At(value = "INVOKE", target = BLIT))
	private static void cobblecomfort$armor(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y,
			int width, int height, Operation<Void> original, @Local(argsOnly = true, ordinal = 3) int left) {
		HudAnimFeature.INSTANCE.icon(Row.ARMOR, g, (x - left) / ICON_STEP, sprite, x, y,
				() -> original.call(g, pipeline, sprite, x, y, width, height));
	}

	@Inject(method = "extractArmor", at = @At("RETURN"))
	private static void cobblecomfort$endArmor(GuiGraphicsExtractor g, Player player, int top, int rows, int rowHeight, int left,
			CallbackInfo ci) {
		HudAnimFeature.INSTANCE.endRow(Row.ARMOR, g);
	}

	// ---- air

	@Inject(method = "extractAirBubbles", at = @At("HEAD"))
	private void cobblecomfort$beginAir(GuiGraphicsExtractor g, Player player, int vehicleHearts, int top, int right, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.beginRow(Row.AIR, ICONS);
	}

	@WrapOperation(method = "extractAirBubbles", at = @At(value = "INVOKE", target = BLIT))
	private void cobblecomfort$air(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, Operation<Void> original, @Local(argsOnly = true, ordinal = 2) int right) {
		HudAnimFeature.INSTANCE.icon(Row.AIR, g, (right - ICON - x) / ICON_STEP, sprite, x, y,
				() -> original.call(g, pipeline, sprite, x, y, width, height));
	}

	@Inject(method = "extractAirBubbles", at = @At("RETURN"))
	private void cobblecomfort$endAir(GuiGraphicsExtractor g, Player player, int vehicleHearts, int top, int right, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.endRow(Row.AIR, g);
	}

	// ---- experience level

	@WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
	private void cobblecomfort$level(GuiGraphicsExtractor g, Font font, int level, Operation<Void> original) {
		HudAnimFeature.INSTANCE.level(g, Minecraft.getInstance().player, level, shown -> original.call(g, font, shown));
	}

	@Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
	private void cobblecomfort$xpGain(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		HudAnimFeature.INSTANCE.drawXpGain(g, Minecraft.getInstance().player);
	}
}
