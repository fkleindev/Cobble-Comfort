package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AdvancementToast.class)
public interface AdvancementToastInvoker {
	@Invoker("isChallengeAdvancement")
	boolean cobblecomfort$isChallenge();
}
