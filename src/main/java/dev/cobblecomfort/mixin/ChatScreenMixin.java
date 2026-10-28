package dev.cobblecomfort.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.chat.ChatFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected EditBox input;

	@Unique
	private EditBox cobblecomfort$search;

	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void cobblecomfort$addSearch(CallbackInfo ci) {
		cobblecomfort$search = ChatFeature.INSTANCE.createSearchBox(font, width, height);
		if (cobblecomfort$search != null) {
			// Vanilla pins the focus to the input; it has to give it up for the search field.
			input.setCanLoseFocus(true);
			addRenderableWidget(cobblecomfort$search);
		}
	}

	/** Ctrl+F jumps to the search; while it is focused, keys edit it instead of sending or scrolling the chat. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$searchKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		EditBox search = cobblecomfort$search;
		if (search == null) {
			return;
		}
		if (event.hasControlDownWithQuirk() && (event.shortcutKey() == 'f' || event.key() == InputConstants.KEY_F)) {
			setFocused(search);
			cir.setReturnValue(true);
			return;
		}
		if (getFocused() == null) {
			setFocused(input);
		}
		if (getFocused() != search) {
			return;
		}
		if (event.isEscape()) {
			if (search.getValue().isEmpty()) {
				setFocused(input);
			} else {
				search.setValue("");
			}
		} else if (event.isConfirmation() || event.isCycleFocus()) {
			setFocused(input);
		} else {
			search.keyPressed(event);
		}
		cir.setReturnValue(true);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$copyLine(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && ChatFeature.INSTANCE.copyAt(event.x(), event.y())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cobblecomfort$searchHits(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		EditBox search = cobblecomfort$search;
		if (search == null || ChatFeature.INSTANCE.search().isEmpty()) {
			return;
		}
		Component hits = Component.translatable("cobblecomfort.chat.search_hits", ChatFeature.INSTANCE.searchHits());
		g.text(font, hits, search.getX() - 6 - font.width(hits), search.getY() + 3, 0xFFAAAAAA, true);
	}

	@Inject(method = "removed", at = @At("TAIL"))
	private void cobblecomfort$clearSearch(CallbackInfo ci) {
		if (cobblecomfort$search != null) {
			ChatFeature.INSTANCE.setSearch("");
		}
	}
}
