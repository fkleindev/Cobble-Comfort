package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

	/** Rebuilding the visible lines also applies the chat search. */
	@WrapOperation(method = "refreshTrimmedMessages",
			at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean cobblecomfort$searchFilter(Predicate<Object> filter, Object message, Operation<Boolean> original) {
		return original.call(filter, message) && ChatFeature.INSTANCE.matchesSearch((GuiMessage) message);
	}
}
