package dev.cobblecomfort.core;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cobblecomfort.CobbleComfortClient;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Blocks drawn on their own (swaying plants, swinging doors) go through Iris' terrain program, but without the block
 * id ({@code mc_Entity}) that chunk meshes carry, so shader packs don't know what they are: plants are then lit and
 * shadowed like solid blocks and come out darker. Iris writes that id into the vertices of a buffer it is told the block
 * of, as Sodium's chunk builder does. Does nothing without Iris.
 */
public final class IrisCompat {
	/** Iris' render type for blocks (as opposed to fluids). */
	private static final byte BLOCK_RENDER_TYPE = -1;

	private static final MethodHandle IN_USE;
	private static final MethodHandle IDS;
	private static final Class<?> SENSITIVE;
	private static final MethodHandle BEGIN;
	private static final MethodHandle END;
	private static final MethodHandle IGNORE_MID_BLOCK;
	private static final MethodHandle SHADOW_PASS;

	static {
		MethodHandle inUse = null;
		MethodHandle ids = null;
		Class<?> sensitive = null;
		MethodHandle begin = null;
		MethodHandle end = null;
		MethodHandle ignoreMidBlock = null;
		MethodHandle shadowPass = null;
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			try {
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				inUse = lookup.findStatic(Class.forName("net.irisshaders.iris.Iris"), "isPackInUseQuick", MethodType.methodType(boolean.class));
				Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
				ids = lookup.findVirtual(settings, "getBlockStateIds", MethodType.methodType(Object2IntMap.class))
						.bindTo(settings.getField("INSTANCE").get(null));
				sensitive = Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
				begin = lookup.findVirtual(sensitive, "beginBlock",
						MethodType.methodType(void.class, int.class, byte.class, byte.class, int.class, int.class, int.class));
				end = lookup.findVirtual(sensitive, "endBlock", MethodType.methodType(void.class));
				ignoreMidBlock = lookup.findVirtual(sensitive, "ignoreMidBlock", MethodType.methodType(void.class, boolean.class));
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				shadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class))
						.bindTo(api.getMethod("getInstance").invoke(null));
			} catch (ReflectiveOperationException | LinkageError e) {
				CobbleComfortClient.LOGGER.warn("Iris changed, blocks drawn on their own may look off with shaders", e);
				inUse = null;
			}
		}
		IN_USE = inUse;
		IDS = ids;
		SENSITIVE = sensitive;
		BEGIN = begin;
		END = end;
		IGNORE_MID_BLOCK = ignoreMidBlock;
		SHADOW_PASS = shadowPass;
	}

	private IrisCompat() {
	}

	/**
	 * Whether Iris is drawing the shadow map right now. Entities are drawn there a second time each frame, seen from the
	 * sun; things simulated while being drawn (swinging charms) must not take a step there.
	 */
	public static boolean inShadowPass() {
		if (SHADOW_PASS == null) {
			return false;
		}
		try {
			return (boolean) SHADOW_PASS.invokeExact();
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * Tells Iris which block the following vertices belong to; true if it took it, and {@link #endBlock} must follow.
	 * The vertices are not in chunk space, so no per-vertex offset to the block's middle is written.
	 */
	@SuppressWarnings("unchecked")
	public static boolean beginBlock(VertexConsumer consumer, BlockState state) {
		if (IN_USE == null || !SENSITIVE.isInstance(consumer)) {
			return false;
		}
		try {
			if (!(boolean) IN_USE.invokeExact()) {
				return false;
			}
			Object2IntMap<BlockState> ids = (Object2IntMap<BlockState>) IDS.invoke();
			if (ids == null || !ids.containsKey(state)) {
				return false;
			}
			BEGIN.invoke(consumer, ids.getInt(state), BLOCK_RENDER_TYPE, (byte) state.getLightEmission(), 0, 0, 0);
			IGNORE_MID_BLOCK.invoke(consumer, true);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	public static void endBlock(VertexConsumer consumer) {
		try {
			IGNORE_MID_BLOCK.invoke(consumer, false);
			END.invoke(consumer);
		} catch (Throwable ignored) {
			// Nothing left to undo.
		}
	}
}
