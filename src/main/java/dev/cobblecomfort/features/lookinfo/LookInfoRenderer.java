package dev.cobblecomfort.features.lookinfo;

import dev.cobblecomfort.features.hud.anim.ShowAnimation;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.lookinfo.providers.ModNames;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Draws the look info panel with its show/hide and resize animations. */
public final class LookInfoRenderer implements HudElement {
	private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
	private static final int ICON_SIZE = 16;
	private static final int ICON_GAP = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int HEARTS_PER_ROW = 10;
	private static final int BAR_HEIGHT = 2;
	private static final int BAR_MIN_WIDTH = 60;
	private static final float RESIZE_SPEED = 18f;
	private static final int ITEM_CELL = 18;

	private final LookInfoFeature feature;

	private LookTarget shown;
	private long lastSeenMs;
	private long lastFrameMs;
	private float progress;
	private float width;
	private float height;

	LookInfoRenderer(LookInfoFeature feature) {
		this.feature = feature;
	}

	/** Size of the box drawn last frame, or 0 when nothing is visible. */
	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long now = System.currentTimeMillis();
		float dt = lastFrameMs == 0 ? 0f : Math.min((now - lastFrameMs) / 1000f, 0.1f);
		lastFrameMs = now;
		if (!feature.isEnabled()) {
			shown = null;
			progress = 0f;
			width = height = 0f;
			return;
		}
		LookInfoConfig cfg = feature.config();

		LookTarget current = feature.current();
		if (current == null && HudEditorScreen.isOpen()) {
			current = sample(cfg);
		}
		if (current != null) {
			shown = current;
			lastSeenMs = now;
		}
		boolean wanted = current != null || now - lastSeenMs < (long) (cfg.lingerTime * 1000);
		if (cfg.animation == ShowAnimation.NONE || cfg.animationDuration <= 0f) {
			progress = wanted ? 1f : 0f;
		} else {
			float step = dt / cfg.animationDuration;
			progress = Math.clamp(progress + (wanted ? step : -step), 0f, 1f);
		}
		if (shown == null || progress <= 0f) {
			shown = wanted ? shown : null;
			width = height = 0f;
			return;
		}

		Font font = Minecraft.getInstance().font;
		int[] content = measure(font, cfg, shown);
		int pad = cfg.layout == LookLayout.PANEL ? cfg.padding : 0;
		float targetW = content[0] + pad * 2;
		float targetH = content[1] + pad * 2;
		if (cfg.smoothResize && width > 0f && height > 0f) {
			float k = 1f - (float) Math.exp(-RESIZE_SPEED * dt);
			width += (targetW - width) * k;
			height += (targetH - height) * k;
			if (Math.abs(targetW - width) < 0.5f) {
				width = targetW;
			}
			if (Math.abs(targetH - height) < 0.5f) {
				height = targetH;
			}
		} else {
			width = targetW;
			height = targetH;
		}

		float eased = cfg.easing.apply(progress);
		float alpha = cfg.animation.fade ? Math.clamp(eased, 0f, 1f) : 1f;
		float scale = cfg.animation.scale ? 0.6f + 0.4f * eased : 1f;
		float slide = cfg.animation.slide ? (1f - eased) * 10f : 0f;

		float screenW = g.guiWidth();
		float screenH = g.guiHeight();
		float bottom = feature.defaultBottom(screenH);
		int x0 = Math.round(screenW / 2f - width / 2f);
		int y0 = Math.round(bottom - height);
		int x1 = x0 + Math.round(width);
		int y1 = Math.round(bottom);

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(screenW / 2f, bottom + slide);
		pose.scale(scale);
		pose.translate(-screenW / 2f, -bottom);
		try {
			if (cfg.layout == LookLayout.PANEL) {
				drawPanelBackground(g, cfg, x0, y0, x1, y1, alpha);
			}
			g.enableScissor(x0, y0, x1, y1);
			try {
				int cx = Math.round(screenW / 2f - targetW / 2f) + pad;
				int cy = y1 - Math.round(targetH) + pad;
				if (cfg.layout == LookLayout.PANEL) {
					drawPanelContent(g, font, cfg, shown, cx, cy, content[1], alpha);
				} else {
					drawCompactContent(g, font, cfg, shown, cx, cy, content[1], alpha);
				}
			} finally {
				g.disableScissor();
			}
		} finally {
			pose.popMatrix();
		}
	}

	// ---------------------------------------------------------------- layout

	/** Returns {width, height} of the content without padding. */
	private static int[] measure(Font font, LookInfoConfig cfg, LookTarget target) {
		boolean icon = cfg.showIcon && !target.icon().isEmpty();
		if (cfg.layout == LookLayout.COMPACT) {
			int textWidth = font.width(compactLine(cfg, target));
			return new int[]{(icon ? ICON_SIZE + ICON_GAP : 0) + textWidth, icon ? ICON_SIZE : font.lineHeight};
		}
		int textWidth = font.width(target.name());
		int textHeight = font.lineHeight;
		for (InfoLine line : target.lines()) {
			textWidth = Math.max(textWidth, lineWidth(font, line));
			textHeight += 1 + lineHeight(font, line);
		}
		if (cfg.showModName) {
			textWidth = Math.max(textWidth, font.width(target.modName()));
			textHeight += 1 + font.lineHeight;
		}
		int width = (icon ? ICON_SIZE + ICON_GAP : 0) + textWidth;
		return new int[]{width, Math.max(textHeight, icon ? ICON_SIZE : 0)};
	}

	private static int lineWidth(Font font, InfoLine line) {
		return switch (line) {
			case InfoLine.Text text -> font.width(text.text());
			case InfoLine.Hearts hearts -> Math.min(heartCount(hearts), HEARTS_PER_ROW) * 8 + 1;
			case InfoLine.Bar bar -> Math.max(BAR_MIN_WIDTH, font.width(bar.label()));
			case InfoLine.Items items -> Math.min(items.stacks().size(), items.columns()) * ITEM_CELL;
		};
	}

	private static int lineHeight(Font font, InfoLine line) {
		return switch (line) {
			case InfoLine.Text text -> font.lineHeight;
			case InfoLine.Hearts hearts -> ((heartCount(hearts) + HEARTS_PER_ROW - 1) / HEARTS_PER_ROW) * LINE_HEIGHT - 1;
			case InfoLine.Bar bar -> font.lineHeight + 1 + BAR_HEIGHT;
			case InfoLine.Items items -> itemRows(items) * ITEM_CELL;
		};
	}

	private static int itemRows(InfoLine.Items items) {
		int needed = (items.stacks().size() + items.columns() - 1) / items.columns();
		return Math.max(1, Math.min(needed, items.rows()));
	}

	private static int heartCount(InfoLine.Hearts hearts) {
		return Math.max(1, (int) Math.ceil(hearts.maxHealth() / 2f));
	}

	private static Component compactLine(LookInfoConfig cfg, LookTarget target) {
		MutableComponent line = target.name().copy().withColor(cfg.nameColor & 0xFFFFFF);
		for (InfoLine info : target.lines()) {
			Component part = switch (info) {
				case InfoLine.Text text -> text.text();
				case InfoLine.Hearts hearts -> Component.literal("❤ " + Math.round(hearts.health()) + "/"
						+ Math.round(hearts.maxHealth())).withStyle(ChatFormatting.RED);
				case InfoLine.Bar bar -> bar.label();
				case InfoLine.Items items -> Component.translatable("cobblecomfort.look.items", items.stacks().size())
						.withStyle(ChatFormatting.GRAY);
			};
			line.append(Component.literal("  ")).append(part.copy().withColor(colorOf(part, cfg.detailColor)));
		}
		return line;
	}

	/** Keeps explicit colours of styled components, otherwise uses the fallback. */
	private static int colorOf(Component component, int fallback) {
		var color = component.getStyle().getColor();
		return color != null ? color.getValue() : fallback & 0xFFFFFF;
	}

	// ---------------------------------------------------------------- drawing

	private static void drawPanelBackground(GuiGraphicsExtractor g, LookInfoConfig cfg, int x0, int y0, int x1, int y1,
			float alpha) {
		int radius = cfg.cornerRadius;
		if (cfg.border) {
			RenderUtil.roundedFill(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Math.max(0, radius - 1),
					RenderUtil.withAlpha(cfg.backgroundColor, alpha));
			RenderUtil.roundedOutline(g, x0, y0, x1, y1, radius,
					RenderUtil.withAlpha(cfg.borderColorTop, alpha), RenderUtil.withAlpha(cfg.borderColorBottom, alpha));
		} else {
			RenderUtil.roundedFill(g, x0, y0, x1, y1, radius, RenderUtil.withAlpha(cfg.backgroundColor, alpha));
		}
	}

	private static void drawPanelContent(GuiGraphicsExtractor g, Font font, LookInfoConfig cfg, LookTarget target,
			int x, int y, int contentHeight, float alpha) {
		boolean icon = cfg.showIcon && !target.icon().isEmpty();
		if (icon) {
			drawIcon(g, target.icon(), x, y + (contentHeight - ICON_SIZE) / 2, alpha);
			x += ICON_SIZE + ICON_GAP;
		}
		int detail = RenderUtil.withAlpha(cfg.detailColor, alpha);
		g.text(font, target.name(), x, y, RenderUtil.withAlpha(cfg.nameColor, alpha), cfg.textShadow);
		y += font.lineHeight + 1;
		for (InfoLine line : target.lines()) {
			switch (line) {
				case InfoLine.Text text -> g.text(font, text.text(), x, y, detail, cfg.textShadow);
				case InfoLine.Hearts hearts -> drawHearts(g, hearts, x, y, alpha);
				case InfoLine.Bar bar -> drawBar(g, font, cfg, bar, x, y, detail, alpha);
				case InfoLine.Items items -> drawItems(g, font, items, x, y, alpha);
			}
			y += lineHeight(font, line) + 1;
		}
		if (cfg.showModName) {
			g.text(font, target.modName(), x, y, RenderUtil.withAlpha(cfg.modColor, alpha), cfg.textShadow);
		}
	}

	private static void drawCompactContent(GuiGraphicsExtractor g, Font font, LookInfoConfig cfg, LookTarget target,
			int x, int y, int contentHeight, float alpha) {
		if (cfg.showIcon && !target.icon().isEmpty()) {
			drawIcon(g, target.icon(), x, y, alpha);
			x += ICON_SIZE + ICON_GAP;
		}
		g.text(font, compactLine(cfg, target), x, y + (contentHeight - font.lineHeight) / 2 + 1,
				RenderUtil.withAlpha(0xFFFFFFFF, alpha), cfg.textShadow);
	}

	/** Items cannot be faded, so the icon is skipped while the panel is mostly transparent. */
	private static void drawIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, float alpha) {
		if (alpha >= 0.5f) {
			g.item(icon, x, y);
		}
	}

	private static void drawHearts(GuiGraphicsExtractor g, InfoLine.Hearts hearts, int x, int y, float alpha) {
		int count = heartCount(hearts);
		int points = (int) Math.ceil(hearts.health());
		int color = RenderUtil.withAlpha(0xFFFFFFFF, alpha);
		for (int i = 0; i < count; i++) {
			int hx = x + (i % HEARTS_PER_ROW) * 8;
			int hy = y + (i / HEARTS_PER_ROW) * LINE_HEIGHT;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, hy, 9, 9, color);
			if (points >= i * 2 + 2) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, hx, hy, 9, 9, color);
			} else if (points == i * 2 + 1) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, hx, hy, 9, 9, color);
			}
		}
	}

	private static void drawBar(GuiGraphicsExtractor g, Font font, LookInfoConfig cfg, InfoLine.Bar bar, int x, int y,
			int textColor, float alpha) {
		g.text(font, bar.label(), x, y, textColor, cfg.textShadow);
		int barY = y + font.lineHeight + 1;
		int barWidth = Math.max(BAR_MIN_WIDTH, font.width(bar.label()));
		g.fill(x, barY, x + barWidth, barY + BAR_HEIGHT, RenderUtil.withAlpha(0x60000000, alpha));
		g.fill(x, barY, x + Math.round(barWidth * Math.clamp(bar.progress(), 0f, 1f)), barY + BAR_HEIGHT,
				RenderUtil.withAlpha(bar.color(), alpha));
	}

	private static void drawItems(GuiGraphicsExtractor g, Font font, InfoLine.Items items, int x, int y, float alpha) {
		if (alpha < 0.5f) {
			return;
		}
		int cells = items.columns() * itemRows(items);
		int count = items.stacks().size();
		for (int i = 0; i < Math.min(count, cells); i++) {
			int cx = x + (i % items.columns()) * ITEM_CELL;
			int cy = y + (i / items.columns()) * ITEM_CELL;
			if (i == cells - 1 && count > cells) {
				g.centeredText(font, "+" + (count - cells + 1), cx + 8, cy + 4, 0xFFFFFFFF);
				continue;
			}
			ItemStack stack = items.stacks().get(i);
			g.item(stack, cx, cy);
			g.itemDecorations(font, stack, cx, cy, stack.getCount() > 1 ? dev.cobblecomfort.features.storage.StorageItems.shortCount(stack.getCount()) : null);
		}
	}

	/** Placeholder shown in the HUD editor when nothing is targeted. */
	private static LookTarget sample(LookInfoConfig cfg) {
		Component tool = Component.translatable("cobblecomfort.look.tool",
				Component.translatable("cobblecomfort.look.tool.axe"))
				.append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN));
		return new LookTarget("sample", new ItemStack(Items.OAK_LOG), Blocks.OAK_LOG.getName(),
				ModNames.of("minecraft", cfg.modColor), List.of(new InfoLine.Text(tool)));
	}
}
