package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> cobblecomfort$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> cobblecomfort$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int cobblecomfort$scrollPos();

	@Accessor("visibleMessageFilter")
	Predicate<GuiMessage> cobblecomfort$visibleFilter();

	@Invoker("getWidth")
	int cobblecomfort$width();

	@Invoker("getScale")
	double cobblecomfort$scale();

	@Invoker("getLineHeight")
	int cobblecomfort$lineHeight();

	@Invoker("refreshTrimmedMessages")
	void cobblecomfort$refresh();

	@Invoker("logChatMessage")
	void cobblecomfort$log(GuiMessage message);

	@Invoker("addMessageToDisplayQueue")
	void cobblecomfort$addToDisplay(GuiMessage message);

	@Invoker("addMessageToQueue")
	void cobblecomfort$addToHistory(GuiMessage message);
}
