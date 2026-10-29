package dev.cobblecomfort.mixin;

import com.mojang.authlib.GameProfile;
import dev.cobblecomfort.features.chat.ChatFeature;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	/** Signed player chat knows its sender and type; chat heads and tabs use them instead of guessing from the text. */
	@Inject(method = "showMessageToPlayer", at = @At("HEAD"))
	private void cobblecomfort$rememberSender(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
			GameProfile sender, boolean onlyShowSecureChat, Instant received, CallbackInfoReturnable<Boolean> cir) {
		ChatFeature.INSTANCE.setPendingSender(sender);
		ChatFeature.INSTANCE.setPendingType(bound);
	}

	/** /msg from the console or a plugin arrives disguised, with its chat type but no signed sender. */
	@Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
	private void cobblecomfort$disguised(Component message, ChatType.Bound bound, CallbackInfo ci) {
		ChatFeature.INSTANCE.onDisguised(message, bound);
	}

	@Inject(method = "showMessageToPlayer", at = @At("RETURN"))
	private void cobblecomfort$forgetSender(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
			GameProfile sender, boolean onlyShowSecureChat, Instant received, CallbackInfoReturnable<Boolean> cir) {
		ChatFeature.INSTANCE.setPendingSender(null);
		ChatFeature.INSTANCE.setPendingType(null);
	}
}
