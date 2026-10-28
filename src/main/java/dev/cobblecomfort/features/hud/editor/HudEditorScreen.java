package dev.cobblecomfort.features.hud.editor;

import dev.cobblecomfort.core.FeatureManager;
import dev.cobblecomfort.core.config.CobbleConfigScreen;
import dev.cobblecomfort.features.hud.Anchor;
import dev.cobblecomfort.features.hud.HudConfig;
import dev.cobblecomfort.features.hud.HudConfigUi;
import dev.cobblecomfort.features.hud.HudCustomizerFeature;
import dev.cobblecomfort.features.hud.HudElementConfig;
import dev.cobblecomfort.features.hud.HudElementDef;
import dev.cobblecomfort.features.hud.HudElements;
import dev.cobblecomfort.features.hud.HudLayout;
import dev.cobblecomfort.features.hud.HudPresets;
import dev.cobblecomfort.features.hud.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/** Drag &amp; drop editor shown on top of the live HUD. */
public class HudEditorScreen extends Screen {
	private static final int COLOR_GUIDE = 0xFF33CCFF;
	private static final int COLOR_OUTLINE = 0x90FFFFFF;
	private static final int COLOR_OUTLINE_ACTIVE = 0xFFFFFFFF;
	private static final int COLOR_OUTLINE_HIDDEN = 0x90FF5555;
	private static final int COLOR_OUTLINE_EMPTY = 0x50FFFFFF;
	private static final int COLOR_FILL = 0x18FFFFFF;
	private static final int COLOR_FILL_ACTIVE = 0x30FFFFFF;
	private static final float SCALE_STEP = 0.05f;

	private final Screen parent;
	private final HudCustomizerFeature feature = HudCustomizerFeature.INSTANCE;

	private HudElementDef dragging;
	private double dragStartMouseX, dragStartMouseY;
	private float dragStartTx, dragStartTy;
	private Rect dragStartRect;
	private final List<Float> guidesX = new ArrayList<>();
	private final List<Float> guidesY = new ArrayList<>();
	private int mouseX, mouseY;

	public HudEditorScreen(Screen parent) {
		super(Component.translatable("cobblecomfort.editor.title"));
		this.parent = parent;
	}

	public static boolean isOpen() {
		return Minecraft.getInstance().gui.screen() instanceof HudEditorScreen;
	}

	@Override
	protected void init() {
		int buttonWidth = 90;
		int gap = 4;
		int total = buttonWidth * 4 + gap * 3;
		int x = width / 2 - total / 2;
		int y = height / 2 + 24;
		addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.editor.reset_all"), b -> {
			feature.applyLayout(HudPresets.BuiltIn.VANILLA.create());
		}).bounds(x, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.editor.toggle_feature"), b -> {
			feature.config().enabled = !feature.config().enabled;
		}).bounds(x + (buttonWidth + gap), y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.editor.settings"), b -> {
			minecraft.gui.setScreen(CobbleConfigScreen.create(this));
		}).bounds(x + (buttonWidth + gap) * 2, y, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(x + (buttonWidth + gap) * 3, y, buttonWidth, 20).build());
	}

	// ---------------------------------------------------------------- geometry

	private HudElementConfig cfg(HudElementDef def) {
		return feature.config().element(def);
	}

	/** On-screen rectangle of an element: what it drew last frame, or its vanilla bounds as a placeholder. */
	private Rect screenRect(HudElementDef def) {
		HudLayout.Placement placement = HudLayout.place(def, cfg(def), width, height);
		Rect local = feature.state(def).lastBounds();
		if (local == null) {
			local = def.vanillaBounds().get(width, height);
		}
		return placement.toScreen(local);
	}

	private boolean drewLastFrame(HudElementDef def) {
		return feature.state(def).lastBounds() != null;
	}

	/** The smallest element under the cursor, preferring elements that are currently drawn. */
	private HudElementDef pick(double x, double y) {
		HudElementDef best = null;
		float bestScore = Float.MAX_VALUE;
		for (HudElementDef def : HudElements.all()) {
			Rect r = screenRect(def);
			if (!r.contains(x, y)) {
				continue;
			}
			float score = r.w() * r.h() * (drewLastFrame(def) ? 1f : 100f);
			if (score < bestScore) {
				bestScore = score;
				best = def;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int grid = feature.config().gridSize;
		if (grid >= 4) {
			for (int x = 0; x < width; x += grid) {
				g.verticalLine(x, 0, height, 0x14FFFFFF);
			}
			for (int y = 0; y < height; y += grid) {
				g.horizontalLine(0, width, y, 0x14FFFFFF);
			}
		}
		g.verticalLine(width / 2, 0, height, 0x20FFFFFF);
		g.horizontalLine(0, width, height / 2, 0x20FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		HudElementDef hovered = dragging != null ? dragging : pick(mouseX, mouseY);

		for (HudElementDef def : HudElements.all()) {
			if (def != hovered) {
				drawBox(g, def, false);
			}
		}
		if (hovered != null) {
			drawBox(g, hovered, true);
		}

		for (float x : guidesX) {
			g.verticalLine(Math.round(x), 0, height, COLOR_GUIDE);
		}
		for (float y : guidesY) {
			g.horizontalLine(0, width, Math.round(y), COLOR_GUIDE);
		}

		if (!feature.isEnabled()) {
			g.centeredText(font, Component.translatable("cobblecomfort.editor.disabled"), width / 2, height / 2 - 20, 0xFFFF5555);
		}
		int helpY = 4;
		for (FormattedCharSequence line : font.split(Component.translatable("cobblecomfort.editor.help"), width - 8)) {
			g.centeredText(font, line, width / 2, helpY, 0xFFFFFFFF);
			helpY += font.lineHeight + 1;
		}
		if (hovered != null) {
			drawInfo(g, hovered);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void drawBox(GuiGraphicsExtractor g, HudElementDef def, boolean active) {
		Rect r = screenRect(def);
		int x = Math.round(r.x());
		int y = Math.round(r.y());
		int w = Math.max(2, Math.round(r.w()));
		int h = Math.max(2, Math.round(r.h()));
		int outline;
		if (active) {
			outline = COLOR_OUTLINE_ACTIVE;
		} else if (!cfg(def).visible) {
			outline = COLOR_OUTLINE_HIDDEN;
		} else if (!drewLastFrame(def)) {
			outline = COLOR_OUTLINE_EMPTY;
		} else {
			outline = COLOR_OUTLINE;
		}
		g.fill(x, y, x + w, y + h, active ? COLOR_FILL_ACTIVE : COLOR_FILL);
		g.outline(x, y, w, h, outline);
		if (!drewLastFrame(def) && !active) {
			g.centeredText(font, def.displayName(), x + w / 2, y + h / 2 - 4, 0x90FFFFFF);
		}
	}

	private void drawInfo(GuiGraphicsExtractor g, HudElementDef def) {
		HudElementConfig cfg = cfg(def);
		Rect r = screenRect(def);
		Component info = Component.translatable("cobblecomfort.editor.info", def.displayName(),
				Math.round(cfg.scale * 100), HudLayout.effectiveAnchor(def, cfg).getDisplayName());
		int textWidth = font.width(info);
		int x = Math.round(Math.clamp(r.x(), 2, width - textWidth - 2));
		int y = r.y() > 14 ? Math.round(r.y()) - 11 : Math.round(r.bottom()) + 3;
		g.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0xC0000000);
		g.text(font, info, x, y, 0xFFFFFFFF, false);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		HudElementDef def = pick(event.x(), event.y());
		if (def == null) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			minecraft.gui.setScreen(HudConfigUi.elementScreen(feature, def, this));
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			HudElementConfig cfg = cfg(def);
			HudLayout.makeCustom(def, cfg, width, height);
			HudLayout.Placement placement = HudLayout.place(def, cfg, width, height);
			dragging = def;
			dragStartMouseX = event.x();
			dragStartMouseY = event.y();
			dragStartTx = placement.tx();
			dragStartTy = placement.ty();
			dragStartRect = screenRect(def);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		float moveX = (float) (event.x() - dragStartMouseX);
		float moveY = (float) (event.y() - dragStartMouseY);
		guidesX.clear();
		guidesY.clear();
		HudConfig config = feature.config();
		if (config.snapping && !event.hasShiftDown()) {
			Rect moved = dragStartRect.offset(moveX, moveY);
			moveX += snap(moved.x(), moved.w(), width, true, guidesX);
			moveY += snap(moved.y(), moved.h(), height, false, guidesY);
		}
		HudLayout.moveTo(cfg(dragging), dragStartTx + moveX, dragStartTy + moveY, width, height);
		return true;
	}

	/**
	 * Finds the smallest correction that aligns the start, centre or end of [pos, pos+size] with a screen
	 * edge, the screen centre, another element's edges or the grid.
	 */
	private float snap(float pos, float size, float screenSize, boolean horizontal, List<Float> guides) {
		HudConfig config = feature.config();
		List<Float> targets = new ArrayList<>(List.of(0f, screenSize / 2f, screenSize));
		for (HudElementDef other : HudElements.all()) {
			if (other == dragging || !drewLastFrame(other) || !cfg(other).visible) {
				continue;
			}
			Rect r = screenRect(other);
			float start = horizontal ? r.x() : r.y();
			float length = horizontal ? r.w() : r.h();
			targets.add(start);
			targets.add(start + length / 2f);
			targets.add(start + length);
		}
		float[] edges = {pos, pos + size / 2f, pos + size};
		float best = Float.MAX_VALUE;
		float bestTarget = 0f;
		for (float target : targets) {
			for (float edge : edges) {
				float diff = target - edge;
				if (Math.abs(diff) <= config.snapThreshold && Math.abs(diff) < Math.abs(best)) {
					best = diff;
					bestTarget = target;
				}
			}
		}
		if (best != Float.MAX_VALUE) {
			guides.add(bestTarget);
			return best;
		}
		if (config.gridSize > 1) {
			float snapped = Math.round(pos / config.gridSize) * (float) config.gridSize;
			return snapped - pos;
		}
		return 0f;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			Rect r = screenRect(dragging);
			Anchor anchor = Anchor.forPoint(r.x() + r.w() / 2f, r.y() + r.h() / 2f, width, height);
			HudLayout.reanchor(dragging, cfg(dragging), anchor, width, height);
			dragging = null;
			guidesX.clear();
			guidesY.clear();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		HudElementDef def = pick(x, y);
		if (def == null || scrollY == 0) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}
		HudElementConfig cfg = cfg(def);
		float scale = cfg.scale + (float) Math.signum(scrollY) * SCALE_STEP;
		cfg.scale = Math.clamp(Math.round(scale * 20f) / 20f, 0.25f, 3f);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		HudElementDef def = dragging != null ? dragging : pick(mouseX, mouseY);
		if (def != null) {
			HudElementConfig cfg = cfg(def);
			int step = event.hasShiftDown() ? 10 : 1;
			switch (event.key()) {
				case InputConstants.KEY_R -> {
					feature.resetElement(def);
					return true;
				}
				case InputConstants.KEY_H -> {
					cfg.visible = !cfg.visible;
					return true;
				}
				case InputConstants.KEY_LEFT -> {
					return nudge(def, -step, 0);
				}
				case InputConstants.KEY_RIGHT -> {
					return nudge(def, step, 0);
				}
				case InputConstants.KEY_UP -> {
					return nudge(def, 0, -step);
				}
				case InputConstants.KEY_DOWN -> {
					return nudge(def, 0, step);
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
	}

	private boolean nudge(HudElementDef def, int dx, int dy) {
		HudElementConfig cfg = cfg(def);
		HudLayout.makeCustom(def, cfg, width, height);
		cfg.offsetX += dx;
		cfg.offsetY += dy;
		return true;
	}

	@Override
	public void onClose() {
		FeatureManager.saveAll();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
