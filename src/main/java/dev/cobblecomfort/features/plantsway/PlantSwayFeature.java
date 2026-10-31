package dev.cobblecomfort.features.plantsway;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.HiddenBlocks;
import dev.cobblecomfort.core.IrisCompat;
import dev.cobblecomfort.core.Movers;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.ShortDryGrassBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.TallDryGrassBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;

/**
 * Plants bend softly away from players and mobs walking through them and swing back. Plants are part of the chunk
 * meshes, so a touched plant is taken out of its mesh (see {@link HiddenBlocks}) and drawn on its own, sheared from its base, until
 * it has settled; then it goes back into the mesh. Only the few plants around entities are ever drawn this way.
 */
public final class PlantSwayFeature extends Feature<PlantSwayConfig> {
	public static final PlantSwayFeature INSTANCE = new PlantSwayFeature();

	private static final double SCAN_RANGE = 32;
	private static final int MAX_PLANTS = 96;
	/** Plants are taken out of the mesh a little before they are touched, so the rebuild is done when they bend. */
	private static final double ACTIVATE_MARGIN = 0.6;
	/** How far a plant's tip moves at most (blocks), and how far a pot tilts (radians), at full strength. */
	private static final float MAX_BEND = 0.7f;
	private static final float MAX_TILT = 0.3f;
	/** A plant goes back into the mesh this long after the last entity left, once it has settled. */
	private static final long SETTLE_NANOS = 400_000_000L;
	/** After that it is still drawn a moment longer, until the rebuilt mesh with it is on screen. */
	private static final long RETURN_NANOS = 150_000_000L;
	private static final float STEP = 1f / 240f;

	enum Kind {
		GRASS, FLOWER, CROP, POT
	}

	/** One touched plant: its base, its blocks, and its bend on a spring. */
	private static final class Plant {
		final BlockPos base;
		final BlockState state;
		final int height;
		final Kind kind;
		float bendX;
		float bendZ;
		float velX;
		float velZ;
		float targetX;
		float targetZ;
		long lastNear;
		/** When it was given back to the mesh, or 0 while it is still out. */
		long returned;

		Plant(BlockPos base, BlockState state, int height, Kind kind, long now) {
			this.base = base;
			this.state = state;
			this.height = height;
			this.kind = kind;
			this.lastNear = now;
		}
	}

	private final Long2ObjectLinkedOpenHashMap<Plant> plants = new Long2ObjectLinkedOpenHashMap<>();
	private final LeafRustle leaves = new LeafRustle();
	private final LilyPads lilyPads = new LilyPads();
	private long lastFrame;
	private float pending;

	private PlantSwayFeature() {
		super("plant_sway", PlantSwayConfig.class, PlantSwayConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			plants.clear();
			leaves.clear();
			lilyPads.clear();
			HiddenBlocks.clear();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	public ConfigCategory buildCategory() {
		return PlantSwayConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- which plants

	private Kind kind(BlockState state) {
		Block block = state.getBlock();
		if (!state.getFluidState().isEmpty()) {
			return null;
		}
		if (block instanceof FlowerPotBlock pot) {
			return config.pots && pot.getPotted() != Blocks.AIR ? Kind.POT : null;
		}
		if (block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock || block instanceof NetherWartBlock) {
			return config.crops ? Kind.CROP : null;
		}
		if (block instanceof TallGrassBlock || block instanceof BushBlock || block instanceof DryVegetationBlock
				|| block instanceof ShortDryGrassBlock || block instanceof TallDryGrassBlock
				|| (block instanceof DoublePlantBlock && !(block instanceof TallFlowerBlock))) {
			return config.grass ? Kind.GRASS : null;
		}
		// Flat ground covers and water plants stay as they are.
		if (block instanceof VegetationBlock && !(block instanceof FlowerBedBlock) && !(block instanceof SeagrassBlock)
				&& !state.is(Blocks.LILY_PAD)) {
			return config.flowers ? Kind.FLOWER : null;
		}
		return null;
	}

	// ---------------------------------------------------------------- touching

	private void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}
		if (!isEnabled()) {
			if (!plants.isEmpty()) {
				releaseAll();
			}
			leaves.release();
			lilyPads.release();
			return;
		}
		long now = System.nanoTime();
		for (Plant plant : plants.values()) {
			plant.targetX = 0f;
			plant.targetZ = 0f;
		}
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		AABB area = new AABB(camera, camera).inflate(SCAN_RANGE);
		for (Entity entity : level.getEntities((Entity) null, area, this::pushes)) {
			touch(level, entity, now);
		}
		if (config.leaves || config.lilyPads) {
			List<Movers.Mover> movers = Movers.around(level, camera, SCAN_RANGE, config.players, config.mobs, config.other);
			if (config.leaves) {
				leaves.tick(level, movers, config, now);
			}
			if (config.lilyPads) {
				lilyPads.tick(level, movers, config, now);
			}
		}
		if (!config.leaves) {
			leaves.release();
		}
		if (!config.lilyPads) {
			lilyPads.release();
		}
		// Plants whose block changed (broken, grown, picked) go back at once; settled ones go back to the mesh.
		Iterator<Plant> it = plants.values().iterator();
		while (it.hasNext()) {
			Plant plant = it.next();
			if (level.getBlockState(plant.base) != plant.state) {
				show(plant);
				it.remove();
				continue;
			}
			if (plant.returned != 0) {
				if (now - plant.returned > RETURN_NANOS) {
					it.remove();
				}
				continue;
			}
			boolean settled = Math.abs(plant.bendX) + Math.abs(plant.bendZ) < 0.004f && Math.abs(plant.velX) + Math.abs(plant.velZ) < 0.02f;
			if (now - plant.lastNear > SETTLE_NANOS && settled) {
				plant.bendX = plant.bendZ = plant.velX = plant.velZ = 0f;
				plant.returned = now;
				show(plant);
			}
		}
	}

	private boolean pushes(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity.isSpectator() || !entity.isAlive()) {
			return false;
		}
		return entity instanceof Player ? config.players : config.mobs;
	}

	private void touch(ClientLevel level, Entity entity, long now) {
		AABB box = entity.getBoundingBox();
		double reach = entity.getBbWidth() / 2.0 + 0.65;
		int x0 = Mth.floor(box.minX - ACTIVATE_MARGIN);
		int x1 = Mth.floor(box.maxX + ACTIVATE_MARGIN);
		int z0 = Mth.floor(box.minZ - ACTIVATE_MARGIN);
		int z1 = Mth.floor(box.maxZ + ACTIVATE_MARGIN);
		int y0 = Mth.floor(box.minY - 0.2);
		int y1 = Mth.floor(Math.min(box.maxY, box.minY + 1.5));
		Vec3 center = entity.position();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					Kind kind = kind(state);
					if (kind == null) {
						continue;
					}
					BlockPos base = state.getBlock() instanceof DoublePlantBlock
							&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
					double dx = base.getX() + 0.5 - center.x;
					double dz = base.getZ() + 0.5 - center.z;
					double distance = Math.sqrt(dx * dx + dz * dz);
					if (distance > reach + ACTIVATE_MARGIN) {
						continue;
					}
					Plant plant = plant(level, base, kind, now);
					if (plant == null) {
						continue;
					}
					plant.lastNear = now;
					if (distance >= reach) {
						continue;
					}
					// Pushed away from the entity, more the closer it is; straight through the middle: along its motion.
					Vec3 motion = entity.getDeltaMovement();
					if (distance < 0.05) {
						dx = motion.x;
						dz = motion.z;
						distance = Math.max(1e-4, Math.sqrt(dx * dx + dz * dz));
					}
					// Falls off softly towards the edge, so plants beside the path still lean a little.
					float amount = (float) Math.sqrt(1.0 - distance / reach);
					float max = (kind == Kind.POT ? MAX_TILT : MAX_BEND) * config.strength;
					plant.targetX = Mth.clamp(plant.targetX + (float) (dx / distance) * amount * max, -max, max);
					plant.targetZ = Mth.clamp(plant.targetZ + (float) (dz / distance) * amount * max, -max, max);
				}
			}
		}
	}

	/** The plant at this base, taking it out of the mesh if it isn't swaying yet; null if too many already are. */
	private Plant plant(ClientLevel level, BlockPos base, Kind kind, long now) {
		Plant plant = plants.get(base.asLong());
		if (plant != null) {
			if (plant.returned != 0) {
				// Touched again while going back: take it out once more.
				plant.returned = 0;
				hide(plant);
			}
			return plant;
		}
		if (plants.size() >= MAX_PLANTS) {
			return null;
		}
		BlockState state = level.getBlockState(base);
		int height = state.getBlock() instanceof DoublePlantBlock ? 2 : 1;
		plant = new Plant(base, state, height, kind, now);
		plants.put(base.asLong(), plant);
		hide(plant);
		return plant;
	}

	private static void hide(Plant plant) {
		for (int i = 0; i < plant.height; i++) {
			HiddenBlocks.hide(plant.base.above(i));
		}
	}

	private static void show(Plant plant) {
		for (int i = 0; i < plant.height; i++) {
			HiddenBlocks.show(plant.base.above(i));
		}
	}

	private void releaseAll() {
		for (Plant plant : plants.values()) {
			show(plant);
		}
		plants.clear();
	}

	// ---------------------------------------------------------------- swaying and drawing

	private void step(float dt) {
		float frequency = 1.6f;
		float omega = Mth.TWO_PI * frequency;
		float stiffness = omega * omega;
		float damping = 2f * Mth.lerp(Mth.clamp(config.swing, 0f, 1f), 0.9f, 0.12f) * omega;
		pending = Math.min(pending + dt, 0.1f);
		while (pending >= STEP) {
			pending -= STEP;
			for (Plant plant : plants.values()) {
				if (plant.returned != 0) {
					continue;
				}
				plant.velX += (stiffness * (plant.targetX - plant.bendX) - damping * plant.velX) * STEP;
				plant.velZ += (stiffness * (plant.targetZ - plant.bendZ) - damping * plant.velZ) * STEP;
				plant.bendX += plant.velX * STEP;
				plant.bendZ += plant.velZ * STEP;
			}
		}
	}

	private void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : (now - lastFrame) / 1e9f;
		lastFrame = now;
		if (level == null || plants.isEmpty() && leaves.isEmpty() && lilyPads.isEmpty()) {
			return;
		}
		step(dt);
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		leaves.render(context, level, camera, now);
		lilyPads.render(context, level, camera, dt, config.swing);
		PoseStack pose = context.poseStack();
		for (Plant plant : plants.values()) {
			pose.pushPose();
			pose.translate(plant.base.getX() - camera.x, plant.base.getY() - camera.y, plant.base.getZ() - camera.z);
			if (plant.kind == Kind.POT) {
				tilt(pose, plant.bendX, plant.bendZ);
			} else {
				shear(pose, plant.bendX / plant.height, plant.bendZ / plant.height);
			}
			for (int i = 0; i < plant.height; i++) {
				BlockPos pos = plant.base.above(i);
				BlockState state = level.getBlockState(pos);
				pose.pushPose();
				pose.translate(0f, i, 0f);
				context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (base, consumer) -> {
					boolean tagged = IrisCompat.beginBlock(consumer, state);
					HiddenBlocks.tesselate(level, pos, state, (x, y, z, quad, instance) -> consumer.putBakedQuad(base, quad, instance));
					if (tagged) {
						IrisCompat.endBlock(consumer);
					}
				});
				pose.popPose();
			}
			pose.popPose();
		}
	}

	/** Leans a plant from its base: every point moves sideways in proportion to its height, a little lower as it bends. */
	private static void shear(PoseStack pose, float perBlockX, float perBlockZ) {
		float lean = perBlockX * perBlockX + perBlockZ * perBlockZ;
		Matrix4f shear = new Matrix4f();
		shear.m10(perBlockX);
		shear.m12(perBlockZ);
		shear.m11(1f / (float) Math.sqrt(1f + lean));
		pose.mulPose(shear);
	}

	/** Tips a flower pot around the middle of its bottom, away from whoever brushed it. */
	private static void tilt(PoseStack pose, float x, float z) {
		float angle = (float) Math.sqrt(x * x + z * z);
		if (angle < 1e-5f) {
			return;
		}
		Vector3f axis = new Vector3f(z, 0f, -x).normalize();
		pose.translate(0.5f, 0f, 0.5f);
		pose.mulPose(new Matrix4f().rotation(angle, axis));
		pose.translate(-0.5f, 0f, -0.5f);
	}
}
