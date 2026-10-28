package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cobblecomfort.features.toasts.ToastsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The player list fades in, shows the ping as a number, lets rows glide and frames heads in team colours. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	/** Space vanilla adds to a column for the gaps and the ping bars. */
	private static final int COLUMN_EXTRA = 13;

	@Shadow
	private boolean visible;

	@Inject(method = "setVisible", at = @At("HEAD"))
	private void cobblecomfort$opened(boolean show, CallbackInfo ci) {
		if (show && !visible) {
			ToastsFeature.INSTANCE.tabOpened();
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cobblecomfort$beginList(GuiGraphicsExtractor g, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		ToastsFeature.INSTANCE.beginList(g);
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void cobblecomfort$endList(GuiGraphicsExtractor g, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		ToastsFeature.INSTANCE.endList(g);
	}

	@ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = COLUMN_EXTRA))
	private int cobblecomfort$pingWidth(int extra) {
		return extra + ToastsFeature.INSTANCE.pingExtraWidth();
	}

	/** The row's y; locals: 5 the players, 22 the cell index, 25 the row's x. */
	@ModifyVariable(method = "extractRenderState", at = @At("STORE"), index = 26)
	private int cobblecomfort$rowY(int y, @Local(index = 5) List<PlayerInfo> players, @Local(index = 22) int cell,
			@Local(index = 25) int x) {
		return cell < players.size() ? ToastsFeature.INSTANCE.rowY(y, x, players.get(cell)) : y;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
	private void cobblecomfort$headFrame(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size, boolean hat, boolean upsideDown,
			int color, Operation<Void> original, @Local(index = 27) PlayerInfo info) {
		ToastsFeature.INSTANCE.drawHeadFrame(g, x, y, size, info);
		original.call(g, skin, x, y, size, hat, upsideDown, color);
	}

	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$pingNumber(GuiGraphicsExtractor g, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		if (ToastsFeature.INSTANCE.drawPing(g, width, x, y, info)) {
			ci.cancel();
		}
	}
}
