package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.map.render.LocatorBarMarkers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Map waypoints in the locator bar, player heads instead of dots, and the name of what is straight ahead. */
@Mixin(LocatorBar.class)
public abstract class LocatorBarMixin implements ContextualBar {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cobblecomfort$begin(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		LocatorBarMarkers.begin();
	}

	/** Per vanilla waypoint (the lambda that draws one dot): a head for players. */
	@Inject(method = "lambda$extractRenderState$1", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$playerHead(Entity camera, Level level, PartialTickSupplier ticks, GuiGraphicsExtractor g, int top,
			TrackedWaypoint waypoint, CallbackInfo ci) {
		if (LocatorBarMarkers.drawTracked(g, top, camera, level, ticks, waypoint)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cobblecomfort$waypoints(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		Entity camera = minecraft.getCameraEntity();
		if (camera == null) {
			return;
		}
		Level level = camera.level();
		int top = top(minecraft.getWindow());
		PartialTickSupplier ticks = entity -> deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
		LocatorBarMarkers.drawWaypoints(g, top, camera, level, ticks);
		LocatorBarMarkers.drawLabel(g, top);
	}
}
