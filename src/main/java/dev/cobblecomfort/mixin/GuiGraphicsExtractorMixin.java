package dev.cobblecomfort.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.cobblecomfort.features.hud.render.HudRenderContext;
import dev.cobblecomfort.features.smoothgui.ScreenFade;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the few methods every GUI draw call ends up in, so a wrapped HUD element can be faded,
 * tinted and measured. All hooks are no-ops unless {@link HudRenderContext} is active.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	private static final String INNER_BLIT = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"
			+ "Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V";
	private static final String TEXT = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V";
	private static final String ITEM = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/world/item/ItemStack;III)V";

	@Shadow
	public abstract Matrix3x2fStack pose();

	// ---- rectangles

	@Inject(method = "innerFill", at = @At("HEAD"))
	private void cobblecomfort$captureFill(RenderPipeline pipeline, TextureSetup textureSetup, int x0, int y0,
			int x1, int y1, int col1, Integer col2, CallbackInfo ci) {
		HudRenderContext.capture(pose(), x0, y0, x1, y1, col1 & 0xFFFFFF);
	}

	@ModifyVariable(method = "innerFill", at = @At("HEAD"), ordinal = 4, argsOnly = true)
	private int cobblecomfort$fillColor(int color) {
		return ScreenFade.modulate(HudRenderContext.modulate(color));
	}

	@ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true)
	private Integer cobblecomfort$fillColor2(Integer color) {
		return color == null ? null : ScreenFade.modulate(HudRenderContext.modulate(color));
	}

	// ---- textured quads

	@Inject(method = INNER_BLIT, at = @At("HEAD"))
	private void cobblecomfort$captureBlit(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler,
			int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
		HudRenderContext.capture(pose(), x0, y0, x1, y1,
				Float.floatToIntBits(u0) * 31L + Float.floatToIntBits(v0) + System.identityHashCode(texture));
	}

	@ModifyVariable(method = INNER_BLIT, at = @At("HEAD"), ordinal = 4, argsOnly = true)
	private int cobblecomfort$blitColor(int color) {
		return ScreenFade.modulate(HudRenderContext.modulate(color));
	}

	@Inject(method = "innerTiledBlit", at = @At("HEAD"))
	private void cobblecomfort$captureTiledBlit(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler,
			int tileWidth, int tileHeight, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1,
			int color, CallbackInfo ci) {
		HudRenderContext.capture(pose(), x0, y0, x1, y1,
				Float.floatToIntBits(u0) * 31L + Float.floatToIntBits(v0) + System.identityHashCode(texture));
	}

	@ModifyVariable(method = "innerTiledBlit", at = @At("HEAD"), ordinal = 6, argsOnly = true)
	private int cobblecomfort$tiledBlitColor(int color) {
		return ScreenFade.modulate(HudRenderContext.modulate(color));
	}

	// ---- text

	@Inject(method = TEXT, at = @At("HEAD"))
	private void cobblecomfort$captureText(Font font, FormattedCharSequence text, int x, int y, int color,
			boolean dropShadow, CallbackInfo ci) {
		if (!HudRenderContext.active()) {
			return;
		}
		long[] hash = {font.width(text)};
		text.accept((index, style, codepoint) -> {
			hash[0] = hash[0] * 31 + codepoint;
			return true;
		});
		HudRenderContext.capture(pose(), x, y, x + font.width(text), y + font.lineHeight, hash[0] * 31 + (color & 0xFFFFFF));
	}

	@ModifyVariable(method = TEXT, at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private int cobblecomfort$textColor(int color) {
		return ScreenFade.modulate(HudRenderContext.modulate(color));
	}

	@ModifyVariable(method = TEXT, at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private boolean cobblecomfort$textShadow(boolean dropShadow) {
		return HudRenderContext.shadow(dropShadow);
	}

	// ---- 3D models (entity in the inventory, enchanting book) can't be faded either; hide them while a screen fades in

	@Inject(method = {"entity", "book"}, at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$hideModelWhileFading(CallbackInfo ci) {
		if (ScreenFade.suppressItems()) {
			ci.cancel();
		}
	}

	// ---- items (cannot be faded, so they are skipped while an element is mostly hidden)

	@Inject(method = ITEM, at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$item(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed,
			CallbackInfo ci) {
		if (ScreenFade.suppressItems()) {
			ci.cancel();
			return;
		}
		if (!HudRenderContext.active()) {
			return;
		}
		HudRenderContext.capture(pose(), x, y, x + 16, y + 16, stack.getItem().hashCode() * 31L + stack.getCount());
		if (HudRenderContext.suppressItems()) {
			ci.cancel();
		}
	}
}
