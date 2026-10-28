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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	/** Signed player chat knows its sender; chat heads use it instead of guessing from the text. */
	@Inject(method = "showMessageToPlayer", at = @At("HEAD"))
	private void cobblecomfort$rememberSender(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
			GameProfile sender, boolean onlyShowSecureChat, Instant received, CallbackInfoReturnable<Boolean> cir) {
		ChatFeature.INSTANCE.setPendingSender(sender);
	}

	@Inject(method = "showMessageToPlayer", at = @At("RETURN"))
	private void cobblecomfort$forgetSender(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
			GameProfile sender, boolean onlyShowSecureChat, Instant received, CallbackInfoReturnable<Boolean> cir) {
		ChatFeature.INSTANCE.setPendingSender(null);
	}
}
