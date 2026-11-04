package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.map.screen.FlatButton;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.mixin.GuiGraphicsExtractorAccessor;
import dev.cobblecomfort.mixin.ItemStackRenderStateAccessor;
import dev.cobblecomfort.mixin.LayerRenderStateAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Locale;

/**
 * Where the charm is tied on one kind of weapon or tool, chosen on the item in 3D: a click on the item ties it there
 * (dragging moves it), dragging with the right button (or from beside the item) turns the item, the wheel zooms and,
 * with shift, moves the charm in front of the item or behind it; the arrow keys nudge it. Third and first person each
 * have their own spot: in first person the item is big and only its upper part is in view.
 */
public final class CharmEditorScreen extends Screen {
	private static final int MAX_WIDTH = 380;
	private static final int MAX_HEIGHT = 250;
	private static final int SIDE_WIDTH = 132;
	/** Spots are kept to quarters of an item pixel (as they are shared). */
	private static final float STEP = 0.25f;
	private static final float NUDGE = 0.5f;
	private static final float MIN_ZOOM = 0.6f;
	private static final float MAX_ZOOM = 3f;
	/** Below this, the item is seen too nearly edge-on to place the charm on it: a drag turns it instead. */
	private static final float EDGE_ON = 0.12f;
	private static final int MARK = 0xFFFFD25A;
	/** Items are drawn centred: their unit cube moved back by half a block. */
	private static final float HALF = 0.5f;

	private final Screen parent;
	private final ToolKind kind;
	private final ItemStack stack;
	private final ItemStackRenderState probe = new ItemStackRenderState();
	private boolean firstPerson;
	private float yaw = 30f;
	private float pitch = 12f;
	/** How big the item is drawn, as a share of the view's size. */
	private float zoom = 1f;
	private boolean placing;
	private boolean turning;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private FlatButton modeButton;

	/**
	 * This frame's view: its box, the item's turn and scale, its model's box, how the unit cube maps to the space the
	 * item is drawn in and the point of that space in the view's middle.
	 */
	private int viewX0;
	private int viewY0;
	private int viewX1;
	private int viewY1;
	private float scale;
	private final Quaternionf rotation = new Quaternionf();
	private Vector3fc[] box = {new Vector3f(0f, 0f, 7.5f / 16f), new Vector3f(1f, 1f, 8.5f / 16f)};
	private final Matrix4f local = new Matrix4f();
	private final Vector3f centre = new Vector3f(0.5f);

	public CharmEditorScreen(Screen parent, ToolKind kind) {
		super(Component.translatable("cobblecomfort.charm_editor.title", kind.title()));
		this.parent = parent;
		this.kind = kind;
		this.stack = heldOr(kind);
	}

	/** The weapon or tool of this kind in your hand, so you see your own; else the kind's own item. */
	private static ItemStack heldOr(ToolKind kind) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player != null) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack held = player.getItemInHand(hand);
				if (ToolKind.of(held) == kind) {
					return held.copy();
				}
			}
		}
		return kind.icon();
	}

	private WardrobeFeature feature() {
		return WardrobeFeature.INSTANCE;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(MAX_WIDTH, width - 20);
		panelHeight = Math.min(MAX_HEIGHT, height - 20);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		int sx = sideLeft();
		int bw = left + panelWidth - 8 - sx;
		modeButton = addRenderableWidget(new FlatButton(sx, top + 26, bw, 18, modeLabel(), MapUi.ACCENT, () -> {
			firstPerson = !firstPerson;
			modeButton.setMessage(modeLabel());
		}));
		int bottom = top + panelHeight - 8;
		addRenderableWidget(new FlatButton(sx, bottom - 40, bw, 18, Component.translatable("cobblecomfort.charm_editor.reset"),
				MapUi.ACCENT, () -> feature().resetCharmSpot(kind)));
		addRenderableWidget(new FlatButton(sx, bottom - 18, bw, 18, Component.translatable("cobblecomfort.charm_editor.done"),
				MapUi.ACCENT, this::onClose));
	}

	private Component modeLabel() {
		return Component.translatable(firstPerson ? "cobblecomfort.charm_editor.first_person" : "cobblecomfort.charm_editor.third_person");
	}

	private int sideLeft() {
		return left + panelWidth - 8 - SIDE_WIDTH;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		MapUi.panel(g, left, top, left + panelWidth, top + panelHeight, MapUi.PANEL_SOLID);
		g.item(kind.icon(), left + 8, top + 6);
		g.text(font, title, left + 28, top + 10, MapUi.TEXT, false);
		viewX0 = left + 8;
		viewY0 = top + 26;
		viewX1 = sideLeft() - 8;
		viewY1 = top + panelHeight - 8;
		RenderUtil.roundedFill(g, viewX0, viewY0, viewX1, viewY1, 4, 0x30000000);
		updateView();
		Vector3f spot = spot();
		ScreenRectangle area = new ScreenRectangle(viewX0, viewY0, viewX1 - viewX0, viewY1 - viewY0);
		((GuiGraphicsExtractorAccessor) g).cobblecomfort$guiRenderState().addPicturesInPictureState(CharmScene.of(viewX0, viewY0, viewX1,
				viewY1, area, stack, charm(), spot, rotation, centre, scale));

		// Where it is tied, and where a click would tie it.
		g.enableScissor(viewX0, viewY0, viewX1, viewY1);
		float[] at = toScreen(NameCharmFeature.anchor(box, spot));
		mark(g, at[0], at[1], MARK);
		if (!placing && !turning) {
			Vector3f hover = pick(mouseX, mouseY, spot.z);
			if (hover != null && inItem(hover)) {
				float[] h = toScreen(NameCharmFeature.anchor(box, hover));
				mark(g, h[0], h[1], 0x80FFFFFF);
			}
		}
		g.disableScissor();

		int sx = sideLeft();
		int y = top + 50;
		y = line(g, Component.translatable("cobblecomfort.charm_editor.across", number(spot.x)), sx, y, MapUi.TEXT);
		y = line(g, Component.translatable("cobblecomfort.charm_editor.up", number(spot.y)), sx, y, MapUi.TEXT);
		y = line(g, Component.translatable("cobblecomfort.charm_editor.depth", number(spot.z)), sx, y, MapUi.TEXT);
		y += 4;
		for (var text : font.split(Component.translatable(firstPerson ? "cobblecomfort.charm_editor.hint_first_person"
				: "cobblecomfort.charm_editor.hint"), SIDE_WIDTH)) {
			g.text(font, text, sx, y, MapUi.TEXT_DIM, false);
			y += 10;
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	private int line(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		g.text(font, text, x, y, color, false);
		return y + 11;
	}

	private static void mark(GuiGraphicsExtractor g, float x, float y, int color) {
		int cx = Math.round(x);
		int cy = Math.round(y);
		g.fill(cx - 4, cy, cx - 1, cy + 1, color);
		g.fill(cx + 2, cy, cx + 5, cy + 1, color);
		g.fill(cx, cy - 4, cx + 1, cy - 1, color);
		g.fill(cx, cy + 2, cx + 1, cy + 5, color);
	}

	private static String number(float value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		text = text.replaceAll("0+$", "");
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}

	/** The charm shown: the one chosen for the kind, or the name tag if none is (something has to hang there). */
	private ToolCharm charm() {
		ToolCharm charm = feature().toolCharm(kind);
		return charm == null ? ToolCharm.NAME_TAG : charm;
	}

	private Vector3f spot() {
		return feature().charmSpot(kind, firstPerson);
	}

	/** Sets up this frame's mapping from the item's unit cube to the screen, from the model of the item as drawn. */
	private void updateView() {
		probe.clear();
		minecraft.getItemModelResolver().updateForTopItem(probe, stack, ItemDisplayContext.NONE, minecraft.level, null, 0);
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) probe;
		local.identity();
		if (state.cobblecomfort$activeLayerCount() > 0) {
			LayerRenderStateAccessor model = (LayerRenderStateAccessor) state.cobblecomfort$layers()[0];
			box = NameCharmFeature.box(model);
			if (model.cobblecomfort$localTransform() != null) {
				local.set(model.cobblecomfort$localTransform());
			}
		}
		Vector3f middle = new Vector3f(box[0]).add(box[1]).mul(0.5f);
		// The charm hangs below the item: the view looks a little lower than the item's middle.
		local.transformPosition(middle, centre).sub(HALF, HALF, HALF);
		centre.y -= 0.12f;
		rotation.identity().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(-yaw));
		scale = Math.min(viewX1 - viewX0, viewY1 - viewY0) * 0.62f * zoom;
	}

	/** A point of the item's unit cube on the screen. */
	private float[] toScreen(Vector3fc point) {
		Vector3f v = local.transformPosition(new Vector3f(point)).sub(HALF, HALF, HALF).sub(centre);
		rotation.transform(v);
		return new float[]{(viewX0 + viewX1) / 2f + v.x * scale, (viewY0 + viewY1) / 2f - v.y * scale};
	}

	/**
	 * The spot (item pixels) under the mouse at this depth: where the line of sight through it crosses the item's plane
	 * there; null if the item is seen too nearly edge-on.
	 */
	private Vector3f pick(double mouseX, double mouseY, float depth) {
		Quaternionf back = new Quaternionf(rotation).conjugate();
		Matrix4f inverse = new Matrix4f(local).invert();
		Vector3f origin = new Vector3f((float) (mouseX - (viewX0 + viewX1) / 2f) / scale, (float) -(mouseY - (viewY0 + viewY1) / 2f) / scale, 0f);
		back.transform(origin).add(centre).add(HALF, HALF, HALF);
		inverse.transformPosition(origin);
		Vector3f dir = back.transform(new Vector3f(0f, 0f, 1f));
		inverse.transformDirection(dir);
		float planeZ = NameCharmFeature.anchor(box, new Vector3f(0f, 0f, depth)).z;
		if (Math.abs(dir.z) < EDGE_ON * dir.length()) {
			return null;
		}
		float t = (planeZ - origin.z) / dir.z;
		Vector3f hit = origin.fma(t, dir);
		Vector3f spot = NameCharmFeature.spot(box, hit);
		spot.z = depth;
		return spot;
	}

	private static boolean inItem(Vector3fc spot) {
		return spot.x() >= -0.5f && spot.x() <= 16.5f && spot.y() >= -0.5f && spot.y() <= 16.5f;
	}

	private void move(Vector3fc to) {
		Vector3f snapped = new Vector3f(Math.round(to.x() / STEP) * STEP, Math.round(to.y() / STEP) * STEP, Math.round(to.z() / STEP) * STEP);
		feature().moveCharmSpot(kind, firstPerson, snapped);
	}

	// ---------------------------------------------------------------- input

	private boolean overView(double x, double y) {
		return x >= viewX0 && x < viewX1 && y >= viewY0 && y < viewY1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (overView(event.x(), event.y())) {
			setFocused(null);
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				Vector3f hit = pick(event.x(), event.y(), spot().z);
				if (hit != null && inItem(hit)) {
					placing = true;
					move(hit);
				} else {
					turning = true;
				}
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT || event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
				turning = true;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (placing) {
			Vector3f hit = pick(event.x(), event.y(), spot().z);
			if (hit != null) {
				move(hit);
			}
			return true;
		}
		if (turning) {
			yaw = Mth.wrapDegrees(yaw + (float) dx * 1.2f);
			pitch = Mth.clamp(pitch + (float) dy * 1.2f, -89f, 89f);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (placing) {
			feature().save();
		}
		placing = false;
		turning = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!overView(mouseX, mouseY)) {
			return false;
		}
		if (minecraft.hasShiftDown()) {
			nudge(0f, 0f, (float) Math.signum(scrollY) * NUDGE);
		} else {
			zoom = Mth.clamp(zoom * (float) Math.pow(1.15, scrollY), MIN_ZOOM, MAX_ZOOM);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		float step = event.hasShiftDown() ? STEP : NUDGE;
		switch (event.key()) {
			case InputConstants.KEY_LEFT -> nudge(-step, 0f, 0f);
			case InputConstants.KEY_RIGHT -> nudge(step, 0f, 0f);
			case InputConstants.KEY_UP -> nudge(0f, step, 0f);
			case InputConstants.KEY_DOWN -> nudge(0f, -step, 0f);
			case InputConstants.KEY_PAGEUP -> nudge(0f, 0f, step);
			case InputConstants.KEY_PAGEDOWN -> nudge(0f, 0f, -step);
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	private void nudge(float x, float y, float z) {
		move(spot().add(x, y, z));
		feature().save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		feature().save();
		minecraft.gui.setScreen(parent);
	}
}
