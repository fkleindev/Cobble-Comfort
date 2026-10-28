package dev.cobblecomfort.features.map.render;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Small mob faces for the map: the front of the head cut out of the mob's current texture (so variants like warm cows
 * show correctly). Mobs without a known face layout, and babies (their textures are laid out differently), get null.
 */
public final class EntityHeads {
	private static final long TEXTURE_REFRESH_MS = 5000L;

	/** Where the face is in a texture of the given vanilla size (pixels); resource packs scale proportionally. */
	private record Face(int u, int v, int w, int h, int texW, int texH) {
	}

	private record Cached(Identifier texture, long time) {
	}

	private static final Map<EntityType<?>, Face> FACES = new HashMap<>();
	private static final Map<Integer, Cached> TEXTURES = new HashMap<>();

	static {
		Face humanoid64 = new Face(8, 8, 8, 8, 64, 64);
		Face humanoid32 = new Face(8, 8, 8, 8, 64, 32);
		Face villager = new Face(8, 8, 8, 10, 64, 64);
		Face piglin = new Face(8, 8, 10, 8, 64, 64);
		Face spider = new Face(40, 12, 8, 8, 64, 32);
		Face cat = new Face(5, 5, 5, 4, 64, 32);
		for (EntityType<?> type : new EntityType<?>[]{EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED, EntityTypes.PIG}) {
			FACES.put(type, humanoid64);
		}
		for (EntityType<?> type : new EntityType<?>[]{EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.WITHER_SKELETON, EntityTypes.BOGGED,
				EntityTypes.CREEPER, EntityTypes.ENDERMAN, EntityTypes.SLIME}) {
			FACES.put(type, humanoid32);
		}
		for (EntityType<?> type : new EntityType<?>[]{EntityTypes.VILLAGER, EntityTypes.WANDERING_TRADER, EntityTypes.ZOMBIE_VILLAGER,
				EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.EVOKER, EntityTypes.ILLUSIONER}) {
			FACES.put(type, villager);
		}
		for (EntityType<?> type : new EntityType<?>[]{EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE, EntityTypes.ZOMBIFIED_PIGLIN}) {
			FACES.put(type, piglin);
		}
		FACES.put(EntityTypes.SPIDER, spider);
		FACES.put(EntityTypes.CAVE_SPIDER, spider);
		FACES.put(EntityTypes.WITCH, new Face(8, 8, 8, 10, 64, 128));
		FACES.put(EntityTypes.COW, new Face(6, 6, 8, 8, 64, 64));
		FACES.put(EntityTypes.MOOSHROOM, new Face(6, 6, 8, 8, 64, 64));
		FACES.put(EntityTypes.SHEEP, new Face(8, 8, 6, 6, 64, 32));
		FACES.put(EntityTypes.CHICKEN, new Face(3, 3, 4, 6, 64, 32));
		FACES.put(EntityTypes.WOLF, new Face(4, 4, 6, 6, 64, 32));
		FACES.put(EntityTypes.IRON_GOLEM, new Face(8, 8, 8, 10, 128, 128));
		FACES.put(EntityTypes.FOX, new Face(7, 11, 8, 6, 48, 32));
		FACES.put(EntityTypes.CAT, cat);
		FACES.put(EntityTypes.OCELOT, cat);
	}

	private EntityHeads() {
	}

	/** Forgets cached textures (on leaving a world). */
	public static void clear() {
		TEXTURES.clear();
	}

	/**
	 * Draws the entity's face centred on (cx, cy), fitting into a square of {@code size}, with a frame in
	 * {@code frame}. Returns false if this entity has no face to show.
	 */
	public static boolean draw(GuiGraphicsExtractor g, Entity entity, int cx, int cy, int size, int frame) {
		return draw(g, entity, cx, cy, size, frame, 1f);
	}

	/** Like {@link #draw(GuiGraphicsExtractor, Entity, int, int, int, int)}, faded to {@code alpha}. */
	public static boolean draw(GuiGraphicsExtractor g, Entity entity, int cx, int cy, int size, int frame, float alpha) {
		if (entity instanceof AbstractClientPlayer player) {
			drawFace(g, player.getSkin(), cx, cy, size, frame, alpha);
			return true;
		}
		int tint = RenderUtil.withAlpha(0xFFFFFFFF, alpha);
		frame = RenderUtil.withAlpha(frame, alpha);
		Face face = FACES.get(entity.getType());
		if (face == null || (entity instanceof LivingEntity living && living.isBaby())) {
			return false;
		}
		Identifier texture = texture(entity);
		if (texture == null) {
			return false;
		}
		int longest = Math.max(face.w, face.h);
		int w = Math.max(1, Math.round(size * face.w / (float) longest));
		int h = Math.max(1, Math.round(size * face.h / (float) longest));
		int x = cx - w / 2;
		int y = cy - h / 2;
		thinFrame(g, x, y, w, h, frame);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, face.u, face.v, w, h, face.w, face.h, face.texW, face.texH, tint);
		return true;
	}

	/** A player's face from its skin centred on (cx, cy), with a full pixel frame in {@code frame}. */
	public static void drawFace(GuiGraphicsExtractor g, PlayerSkin skin, int cx, int cy, int size, int frame, float alpha) {
		int x = cx - size / 2;
		int y = cy - size / 2;
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, RenderUtil.withAlpha(frame, alpha));
		PlayerFaceExtractor.extractRenderState(g, skin, x, y, size, RenderUtil.withAlpha(0xFFFFFFFF, alpha));
	}

	/** A frame half a GUI pixel wide around a mob's face (players keep a full pixel, it carries their colour). */
	private static void thinFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(0.5f);
		g.fill(x * 2 - 1, y * 2 - 1, (x + w) * 2 + 1, (y + h) * 2 + 1, color);
		pose.popMatrix();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Identifier texture(Entity entity) {
		long now = System.currentTimeMillis();
		Cached cached = TEXTURES.get(entity.getId());
		if (cached != null && now - cached.time < TEXTURE_REFRESH_MS) {
			return cached.texture;
		}
		Identifier texture = null;
		try {
			EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
			if (renderer instanceof LivingEntityRenderer living) {
				texture = living.getTextureLocation((LivingEntityRenderState) living.createRenderState(entity, 1f));
			}
		} catch (RuntimeException e) {
			// A renderer that cannot be asked outside of rendering; fall back to a dot.
		}
		if (TEXTURES.size() > 512) {
			TEXTURES.clear();
		}
		TEXTURES.put(entity.getId(), new Cached(texture, now));
		return texture;
	}
}
