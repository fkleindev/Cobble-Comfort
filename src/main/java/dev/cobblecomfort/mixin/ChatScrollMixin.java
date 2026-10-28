package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth chat scrolling: after scrolling, the chat is drawn between the old and new position for a moment, with one
 * more line than fits so the edges stay filled, cut off at the chat's edges.
 */
@Mixin(ChatComponent.class)
public abstract class ChatScrollMixin {
	/** Space between the chat and the bottom of the screen, as vanilla has it. */
	@Unique
	private static final int BOTTOM = 40;

	@Shadow
	private int chatScrollbarPos;

	/** Where the chat is drawn relative to where it is scrolled to, in lines. */
	@Unique
	private double cobblecomfort$offset;
	@Unique
	private long cobblecomfort$nanos;
	@Unique
	private int cobblecomfort$before;
	@Unique
	private int cobblecomfort$saved = -1;
	@Unique
	private boolean cobblecomfort$extraLine;

	@Shadow
	public abstract int getLinesPerPage();

	@Shadow
	private double getScale() {
		throw new AssertionError();
	}

	@Shadow
	private int getLineHeight() {
		throw new AssertionError();
	}

	@Inject(method = "scrollChat", at = @At("HEAD"))
	private void cobblecomfort$beforeScroll(int lines, CallbackInfo ci) {
		cobblecomfort$before = chatScrollbarPos;
	}

	/** Only scrolling glides; vanilla also moves the position to keep the view when messages arrive. */
	@Inject(method = "scrollChat", at = @At("RETURN"))
	private void cobblecomfort$afterScroll(int lines, CallbackInfo ci) {
		if (SmoothScroll.enabled(SmoothScroll.Kind.CHAT)) {
			int page = getLinesPerPage();
			cobblecomfort$offset = Math.clamp(cobblecomfort$offset - (chatScrollbarPos - cobblecomfort$before), -page, page);
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("HEAD"))
	private void cobblecomfort$beginGlide(GuiGraphicsExtractor g, Font font, int tickCount, int mouseX, int mouseY,
			ChatComponent.DisplayMode mode, boolean changeCursor, CallbackInfo ci) {
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(cobblecomfort$nanos, now);
		cobblecomfort$nanos = now;
		if (!mode.foreground || !SmoothScroll.enabled(SmoothScroll.Kind.CHAT)) {
			cobblecomfort$offset = 0;
			return;
		}
		cobblecomfort$offset -= cobblecomfort$offset * SmoothScroll.approach(seconds);
		if (Math.abs(cobblecomfort$offset) < 0.02) {
			cobblecomfort$offset = 0;
			return;
		}
		double shown = Math.max(0, chatScrollbarPos + cobblecomfort$offset);
		int line = Mth.floor(shown);
		float fraction = (float) (shown - line);
		cobblecomfort$saved = chatScrollbarPos;
		chatScrollbarPos = line;
		cobblecomfort$extraLine = fraction > 0.001f;
		float scale = (float) getScale();
		int lineHeight = getLineHeight();
		int bottom = Mth.floor((g.guiHeight() - BOTTOM) / scale);
		int top = bottom - getLinesPerPage() * lineHeight;
		g.enableScissor(0, Mth.floor(top * scale), g.guiWidth(), Mth.ceil(bottom * scale));
		g.pose().pushMatrix();
		g.pose().translate(0, fraction * lineHeight * scale);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("RETURN"))
	private void cobblecomfort$endGlide(GuiGraphicsExtractor g, Font font, int tickCount, int mouseX, int mouseY,
			ChatComponent.DisplayMode mode, boolean changeCursor, CallbackInfo ci) {
		if (cobblecomfort$saved < 0) {
			return;
		}
		g.pose().popMatrix();
		g.disableScissor();
		chatScrollbarPos = cobblecomfort$saved;
		cobblecomfort$saved = -1;
		cobblecomfort$extraLine = false;
	}

	@WrapOperation(method = "forEachLine", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
	private int cobblecomfort$extraLine(ChatComponent chat, Operation<Integer> original) {
		return original.call(chat) + (cobblecomfort$extraLine ? 1 : 0);
	}
}
