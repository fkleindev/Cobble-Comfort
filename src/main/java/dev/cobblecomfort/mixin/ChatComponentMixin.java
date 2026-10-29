package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cobblecomfort.features.chat.ChatFeature;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	/** Chat comfort decorates, stacks and adds the message itself. */
	@Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$addMessage(Component content, MessageSignature signature, GuiMessageSource source,
			GuiMessageTag tag, CallbackInfo ci) {
		if (ChatFeature.INSTANCE.addMessage((ChatComponent) (Object) this, content, signature, source, tag)) {
			ci.cancel();
		}
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int cobblecomfort$historyLength(int vanilla) {
		return ChatFeature.INSTANCE.historyLength(vanilla);
	}

	/** The background behind each chat line: new private messages blink violet. */
	@WrapOperation(method = "lambda$extractRenderState$1", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V"))
	private static void cobblecomfort$lineBackground(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color,
			Operation<Void> original, @Local(argsOnly = true) GuiMessage.Line line) {
		original.call(access, x0, y0, x1, y1, ChatFeature.INSTANCE.lineBackground(line, color));
	}

	/** Rebuilding the visible lines also applies the chat search and, while the chat is open, the selected tab. */
	@WrapOperation(method = "refreshTrimmedMessages",
			at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean cobblecomfort$searchFilter(Predicate<Object> filter, Object message, Operation<Boolean> original) {
		return original.call(filter, message) && ChatFeature.INSTANCE.isShown((GuiMessage) message);
	}
}
