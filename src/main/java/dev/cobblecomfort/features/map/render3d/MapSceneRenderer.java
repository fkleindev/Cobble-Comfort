package dev.cobblecomfort.features.map.render3d;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.cobblecomfort.features.map.data.MapLayer;
import dev.cobblecomfort.features.map.data.MapRegion;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws a {@link MapScene}: picks the terrain tiles in view (finer close to the camera, coarser far away), builds the
 * missing ones within a time budget and draws them with a perspective camera into a texture of its own, which then goes
 * into the GUI like any picture-in-picture element. Unlike the vanilla ones it draws its own GPU buffers instead of
 * submitting models, so the whole explored map stays cheap to draw every frame.
 */
public final class MapSceneRenderer extends PictureInPictureRenderer<MapScene> {
	/** A tile is split into four finer ones while the camera is closer than this many tile widths. */
	private static final float SPLIT = 2.5f;
	private static final long BUILD_BUDGET_NANOS = 6_000_000L;
	private static final float MIN_Y = -64f;
	private static final float MAX_Y = 400f;
	/** Regions further from the target than this are not looked at (blocks, beyond what the far plane allows). */
	private static final double MAX_RADIUS = 8192;

	/**
	 * Flat coloured, opaque quads that write depth (the vanilla debug pipeline with the same shaders tests depth but
	 * doesn't write it, so tiles drawn later would cover nearer ones).
	 */
	private static final RenderPipeline TERRAIN = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("cobblecomfort", "pipeline/map_terrain"))
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(ColorTargetState.DEFAULT)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			.withCull(false)
			.build();

	private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Cobble Comfort 3D map");
	private GpuTexture color;
	private GpuTextureView colorView;
	private GpuTexture depth;
	private GpuTextureView depthView;
	private long buildDeadline;

	private record Draw(MapMeshes.Mesh mesh, int x, int z) {
	}

	@Override
	public Class<MapScene> getRenderStateClass() {
		return MapScene.class;
	}

	@Override
	protected void renderToTexture(MapScene scene, PoseStack pose, SubmitNodeCollector collector) {
		// Everything happens in prepare: the terrain is drawn from cached buffers, not submitted.
	}

	@Override
	protected String getTextureLabel() {
		return "Cobble Comfort 3D map";
	}

	@Override
	public void prepare(MapScene scene, GuiRenderState gui, FeatureRenderDispatcher dispatcher, int guiScale) {
		int width = (scene.x1() - scene.x0()) * guiScale;
		int height = (scene.y1() - scene.y0()) * guiScale;
		if (width <= 0 || height <= 0) {
			return;
		}
		GpuDevice device = RenderSystem.getDevice();
		textures(device, width, height);
		MapCamera camera = scene.camera();
		MapMeshes.beginFrame();
		buildDeadline = System.nanoTime() + BUILD_BUDGET_NANOS;
		List<Draw> draws = new ArrayList<>();
		select(scene.layer(), camera, width, height, draws);

		Matrix4f view = camera.view();
		Matrix4f projection = camera.projection(width, height, device.getDeviceInfo().isZZeroToOne());
		int bg = scene.background();
		float alpha = (bg >>> 24) / 255f;
		Vector4f clear = new Vector4f((bg >> 16 & 0xFF) / 255f * alpha, (bg >> 8 & 0xFF) / 255f * alpha, (bg & 0xFF) / 255f * alpha, alpha);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
		try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Cobble Comfort 3D map", colorView, Optional.of(clear),
				depthView, OptionalDouble.of(0.0))) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setPipeline(RenderSystem.getCompiledPipeline(TERRAIN));
			int maxIndices = scene.corner() >= 0 ? MASK_SEGMENTS * 6 : 0;
			for (Draw draw : draws) {
				maxIndices = Math.max(maxIndices, draw.mesh().indexCount);
			}
			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indexBuffer = indices.getBuffer(maxIndices);
			Vector4f white = new Vector4f(1f, 1f, 1f, 1f);
			for (Draw draw : draws) {
				MapMeshes.Mesh mesh = draw.mesh();
				Matrix4f modelView = new Matrix4f(view).translate((float) (draw.x() - camera.targetX), (float) -camera.targetY,
						(float) (draw.z() - camera.targetZ));
				pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(modelView, white));
				pass.setVertexBuffer(0, mesh.buffer().slice());
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(mesh.indexCount, 1, 0, 0, 0);
			}
			if (scene.corner() >= 0) {
				// The mask is given in clip space: undo the projection the shader applies.
				Matrix4f clip = new Matrix4f(projection).invert();
				pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(clip, white));
				pass.setVertexBuffer(0, mask(scene.corner()).slice());
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(MASK_SEGMENTS * 6, 1, 0, 0, 0);
			}
		} finally {
			RenderSystem.restoreProjectionMatrix();
		}
		gui.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
				TextureSetup.singleTexture(colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)), scene.pose(),
				scene.x0(), scene.y0(), scene.x1(), scene.y1(), 0f, 1f, 1f, 0f, -1, scene.scissorArea(), null));
	}

	// ---------------------------------------------------------------- cutting to shape

	private static final int MASK_SEGMENTS = 128;
	/** Nearly see-through black: the shader drops fully transparent fragments, so the cut-away part keeps a trace of alpha. */
	private static final int CUT = 0x01000000;
	private GpuBuffer mask;
	private float maskCorner = Float.NaN;

	/**
	 * Quads covering what lies outside a rounded square (corner radius {@code corner} times half its width; 1 = a
	 * circle) within the view, in clip space at the near plane: drawn over the terrain, they make the corners clear.
	 */
	private GpuBuffer mask(float corner) {
		if (mask != null && corner == maskCorner) {
			return mask;
		}
		if (mask != null) {
			mask.close();
		}
		maskCorner = corner;
		int vertexSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(MASK_SEGMENTS * 4 * vertexSize)) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			for (int i = 0; i < MASK_SEGMENTS; i++) {
				double a0 = Math.PI * 2 * i / MASK_SEGMENTS;
				double a1 = Math.PI * 2 * (i + 1) / MASK_SEGMENTS;
				float[] in0 = boundary(a0, corner);
				float[] in1 = boundary(a1, corner);
				float[] out0 = square(a0);
				float[] out1 = square(a1);
				builder.addVertex(in0[0], in0[1], 1f).setColor(CUT);
				builder.addVertex(out0[0], out0[1], 1f).setColor(CUT);
				builder.addVertex(out1[0], out1[1], 1f).setColor(CUT);
				builder.addVertex(in1[0], in1[1], 1f).setColor(CUT);
			}
			try (MeshData data = builder.buildOrThrow()) {
				mask = RenderSystem.getDevice().createBuffer(() -> "Cobble Comfort 3D minimap mask", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
			}
		}
		return mask;
	}

	/** Where a ray from the centre at angle {@code a} leaves the rounded square (half width 1), found by bisection. */
	private static float[] boundary(double a, float corner) {
		double dx = Math.cos(a);
		double dy = Math.sin(a);
		double inside = 0;
		double outside = 1.5;
		for (int i = 0; i < 30; i++) {
			double t = (inside + outside) / 2;
			if (roundedSquare(dx * t, dy * t, corner) < 0) {
				inside = t;
			} else {
				outside = t;
			}
		}
		return new float[]{(float) (dx * inside), (float) (dy * inside)};
	}

	private static double roundedSquare(double x, double y, float corner) {
		double qx = Math.abs(x) - (1 - corner);
		double qy = Math.abs(y) - (1 - corner);
		return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - corner;
	}

	/** Where a ray from the centre at angle {@code a} leaves the view (a little beyond, to leave no seam). */
	private static float[] square(double a) {
		double dx = Math.cos(a);
		double dy = Math.sin(a);
		double t = 1.02 / Math.max(Math.abs(dx), Math.abs(dy));
		return new float[]{(float) (dx * t), (float) (dy * t)};
	}

	private void textures(GpuDevice device, int width, int height) {
		if (color != null && color.getWidth(0) == width && color.getHeight(0) == height) {
			return;
		}
		closeTextures();
		color = device.createTexture(() -> "Cobble Comfort 3D map", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
				| GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		colorView = device.createTextureView(color);
		depth = device.createTexture(() -> "Cobble Comfort 3D map depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
				GpuFormat.D32_FLOAT, width, height, 1, 1);
		depthView = device.createTextureView(depth);
	}

	private void closeTextures() {
		if (color != null) {
			colorView.close();
			color.close();
			depthView.close();
			depth.close();
			color = null;
		}
	}

	@Override
	public void close() {
		closeTextures();
		if (mask != null) {
			mask.close();
			mask = null;
		}
		projectionBuffer.close();
		super.close();
	}

	// ---------------------------------------------------------------- choosing tiles

	/** Walks the explored regions around the target as quadtrees: each region tile is split while the camera is close. */
	private void select(MapLayer layer, MapCamera camera, int width, int height, List<Draw> draws) {
		// Culled against a plain (not reversed) projection, which the frustum helper expects.
		Matrix4f cull = new Matrix4f().setPerspective((float) Math.toRadians(MapCamera.FOV_DEGREES), (float) width / height, camera.near(),
				camera.far()).mul(camera.view());
		FrustumIntersection frustum = new FrustumIntersection(cull);
		double[] eye = camera.eye();
		double radius = Math.min(MAX_RADIUS, camera.far());
		int top = MapMeshes.STEPS.length - 1;
		int regionSize = MapMeshes.CELLS * MapMeshes.STEPS[top];
		int minRx = (int) Math.floor((camera.targetX - radius) / regionSize);
		int maxRx = (int) Math.floor((camera.targetX + radius) / regionSize);
		int minRz = (int) Math.floor((camera.targetZ - radius) / regionSize);
		int maxRz = (int) Math.floor((camera.targetZ + radius) / regionSize);
		for (int rz = minRz; rz <= maxRz; rz++) {
			for (int rx = minRx; rx <= maxRx; rx++) {
				if (layer.region(rx, rz, false) != null) {
					tile(layer, camera, frustum, eye, rx, rz, top, draws);
				}
			}
		}
	}

	private void tile(MapLayer layer, MapCamera camera, FrustumIntersection frustum, double[] eye, int x, int z, int level, List<Draw> draws) {
		int step = MapMeshes.STEPS[level];
		int size = MapMeshes.CELLS * step;
		int x0 = x * size;
		int z0 = z * size;
		float minX = (float) (x0 - camera.targetX);
		float minZ = (float) (z0 - camera.targetZ);
		float minY = (float) (MIN_Y - camera.targetY);
		float maxY = (float) (MAX_Y - camera.targetY);
		if (!frustum.testAab(minX, minY, minZ, minX + size, maxY, minZ + size)) {
			return;
		}
		if (level < MapMeshes.STEPS.length - 1 && !MapMeshes.explored(layer, x, z, step)) {
			return;
		}
		if (level > 0) {
			// Distance from the camera to the nearest point of the tile, on the ground plane and in height.
			double dx = Math.max(0, Math.max(minX - eye[0], eye[0] - (minX + size)));
			double dz = Math.max(0, Math.max(minZ - eye[2], eye[2] - (minZ + size)));
			double dy = Math.max(0, eye[1]);
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (distance < size * SPLIT) {
				if (!childrenReady(layer, x, z, level - 1)) {
					// Coarse first: this tile stands in until its finer ones are built, in this or later frames.
					MapMeshes.Key key = new MapMeshes.Key(layer, x, z, step);
					MapMeshes.Mesh own = MapMeshes.get(key, false);
					if (own == null && System.nanoTime() < buildDeadline) {
						own = MapMeshes.get(key, true);
					}
					if (own != null && System.nanoTime() >= buildDeadline) {
						add(own, x0, z0, draws);
						return;
					}
				}
				for (int i = 0; i < 4; i++) {
					tile(layer, camera, frustum, eye, x * 2 + (i & 1), z * 2 + (i >> 1), level - 1, draws);
				}
				return;
			}
		}
		MapMeshes.Mesh mesh = MapMeshes.get(new MapMeshes.Key(layer, x, z, step), System.nanoTime() < buildDeadline);
		if (mesh != null) {
			add(mesh, x0, z0, draws);
		}
	}

	private static boolean childrenReady(MapLayer layer, int x, int z, int level) {
		int step = MapMeshes.STEPS[level];
		for (int i = 0; i < 4; i++) {
			int cx = x * 2 + (i & 1);
			int cz = z * 2 + (i >> 1);
			if (MapMeshes.explored(layer, cx, cz, step) && MapMeshes.get(new MapMeshes.Key(layer, cx, cz, step), false) == null) {
				return false;
			}
		}
		return true;
	}

	private static void add(MapMeshes.Mesh mesh, int x, int z, List<Draw> draws) {
		if (mesh.buffer() != null && mesh.indexCount > 0) {
			draws.add(new Draw(mesh, x, z));
		}
	}

	/** Regions are 512 blocks: the coarsest tiles line up with them. */
	static {
		if (MapMeshes.CELLS * MapMeshes.STEPS[MapMeshes.STEPS.length - 1] != MapRegion.SIZE) {
			throw new IllegalStateException("3D map tiles must line up with map regions");
		}
	}
}
