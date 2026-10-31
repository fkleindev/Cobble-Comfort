package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.belt.BeltConfig;
import dev.cobblecomfort.features.belt.BeltFeature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.map.screen.FlatButton;
import dev.cobblecomfort.features.map.screen.TabButton;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.features.petplush.PetPlushFeature;
import dev.cobblecomfort.features.pets.PetEntry;
import dev.cobblecomfort.features.pets.PetFeature;
import dev.cobblecomfort.features.pets.PetStore;
import dev.cobblecomfort.features.plush.PlushFeature;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The wardrobe: the player on a turntable on the left (drag to turn), and on the right tabs for badges, hats, plush
 * pets, the belt and saved outfits. Every change is worn at once, so the figure shows it.
 */
public final class WardrobeScreen extends Screen {
	private static final int MAX_WIDTH = 440;
	private static final int MAX_HEIGHT = 270;
	private static final int PREVIEW_WIDTH = 132;
	private static final int TABS_HEIGHT = 22;
	private static final int CELL = 28;
	/** A hat tile: big enough to show the head wearing it. */
	private static final int HAT_CELL = 46;
	private static final int ROW = 22;
	private static final int LINE = 0x20FFFFFF;
	private static final int WORN = 0xFFFF7FB0;
	private static final int LOCKED_TINT = 0xFF5C5C66;

	enum Tab {
		BADGES, HATS, PLUSH, BELT, OUTFITS
	}

	/** Something clickable drawn this frame: its box, what a click does, and its tooltip (may be empty). */
	private record Hit(int x0, int y0, int x1, int y1, Runnable action, List<Component> tooltip) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private Tab tab = Tab.BADGES;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	/** The figure's turn (degrees, 0 = facing us) and whether it is being dragged. */
	private float spin = 25f;
	private float spinVelocity;
	private boolean dragging;
	private long lastFrame;
	/** How far the list is scrolled this frame, gliding towards where the wheel sent it. */
	private int scroll;
	private final SmoothScroll smoothScroll = new SmoothScroll(SmoothScroll.Kind.MOD_SCREENS);
	/** Where the drawn list ended, without scrolling, and so how far it may scroll (as of the last frame). */
	private int contentBottom;
	private int maxScroll;
	private EditBox name;
	private FlatButton saveButton;
	/** Where the mouse is this frame. */
	private int hoverX;
	private int hoverY;
	/** The kind of hat whose colours are shown, or null. */
	private Hat.Style openStyle;
	/** Set when a kind of hat is opened: its colours are scrolled into view once drawn. */
	private boolean reveal;

	public WardrobeScreen(Screen parent) {
		super(Component.translatable("cobblecomfort.wardrobe.title"));
		this.parent = parent;
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
		int x = bodyLeft();
		for (Tab t : Tab.values()) {
			Component label = Component.translatable("cobblecomfort.wardrobe.tab." + t.name().toLowerCase(java.util.Locale.ROOT));
			TabButton button = new TabButton(x, top + 8, TabButton.widthFor(font, label), 18, icon(t), label, () -> tab == t, () -> {
				tab = t;
				smoothScroll.set(0);
				scroll = 0;
				updateWidgets();
			});
			addRenderableWidget(button);
			x += button.getWidth() + 2;
		}
		String value = name == null ? "" : name.getValue();
		name = new EditBox(font, bodyLeft() + 6, bodyBottom() - 15, bodyRight() - bodyLeft() - 90, 10,
				Component.translatable("cobblecomfort.wardrobe.outfit_name"));
		name.setBordered(false);
		name.setTextColor(MapUi.TEXT);
		name.setHint(Component.translatable("cobblecomfort.wardrobe.outfit_name").withColor(MapUi.TEXT_FAINT));
		name.setMaxLength(32);
		name.setValue(value);
		addRenderableWidget(name);
		saveButton = addRenderableWidget(new FlatButton(bodyRight() - 78, bodyBottom() - 20, 78, 18,
				Component.translatable("cobblecomfort.wardrobe.save_outfit"), MapUi.ACCENT, this::saveOutfit));
		updateWidgets();
	}

	private static ItemStack icon(Tab t) {
		return new ItemStack(switch (t) {
			case BADGES -> Items.GOLD_NUGGET;
			case HATS -> Items.GOLDEN_HELMET;
			case PLUSH -> Items.LEAD;
			case BELT -> Items.BUNDLE;
			case OUTFITS -> Items.ARMOR_STAND;
		});
	}

	private void updateWidgets() {
		boolean outfits = tab == Tab.OUTFITS;
		name.visible = outfits;
		name.active = outfits;
		saveButton.visible = outfits;
		if (!outfits && getFocused() == name) {
			setFocused(null);
		}
	}

	private int bodyLeft() {
		return left + PREVIEW_WIDTH + 8;
	}

	private int bodyRight() {
		return left + panelWidth - 8;
	}

	private int bodyTop() {
		return top + 8 + TABS_HEIGHT + 6;
	}

	private int bodyBottom() {
		return top + panelHeight - 8;
	}

	/** The bottom of the scrolling list: above the name field on the outfits tab. */
	private int visibleBottom() {
		return tab == Tab.OUTFITS ? bodyBottom() - 26 : bodyBottom();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		hits.clear();
		hoverX = mouseX;
		hoverY = mouseY;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		if (!dragging) {
			// A flick keeps it turning a little, slowing down.
			spin += spinVelocity * dt;
			spinVelocity *= (float) Math.exp(-3.0 * dt);
		}
		MapUi.panel(g, left, top, left + panelWidth, top + panelHeight, MapUi.PANEL_SOLID);
		drawPreview(g);
		g.fill(bodyLeft() - 4, top + 8, bodyLeft() - 3, bodyBottom(), LINE);
		g.fill(bodyLeft(), bodyTop() - 4, bodyRight(), bodyTop() - 3, LINE);
		scroll = (int) Math.round(smoothScroll.update(maxScroll));
		contentBottom = 0;
		g.enableScissor(bodyLeft() - 2, bodyTop(), bodyRight() + 2, visibleBottom());
		try {
			switch (tab) {
				case BADGES -> drawBadges(g);
				case HATS -> drawHats(g);
				case PLUSH -> drawPlush(g);
				case BELT -> drawBelt(g);
				case OUTFITS -> drawOutfits(g);
			}
		} finally {
			g.disableScissor();
		}
		maxScroll = Math.max(0, contentBottom - (visibleBottom() - bodyTop()));
		if (tab == Tab.OUTFITS) {
			MapUi.field(g, bodyLeft(), bodyBottom() - 20, bodyRight() - 84, bodyBottom() - 2, name.isFocused());
			saveButton.active = !name.getValue().isBlank();
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		for (Hit hit : hits) {
			if (hit.contains(mouseX, mouseY) && !hit.tooltip().isEmpty()) {
				g.setComponentTooltipForNextFrame(font, hit.tooltip(), mouseX, mouseY);
				break;
			}
		}
	}

	private void drawPreview(GuiGraphicsExtractor g) {
		int x0 = left + 8;
		int y0 = top + 8;
		int x1 = left + PREVIEW_WIDTH;
		int y1 = bodyBottom() - 12;
		RenderUtil.roundedFill(g, x0, y0, x1, y1, 4, 0x30000000);
		EntityRenderState state = figure(spin);
		if (state != null) {
			figure(g, state, (y1 - y0) * 0.38f, state.boundingBoxHeight / 2f + 0.0625f, 8f, x0, y0, x1, y1);
		}
		MapUi.centeredText(g, font, Component.translatable("cobblecomfort.wardrobe.drag"), (x0 + x1) / 2f, bodyBottom() - 4, MapUi.TEXT_FAINT, 0.75f);
	}

	/** The player as they look now, turned {@code turn} degrees from facing us, at full size; null without a player. */
	private EntityRenderState figure(float turn) {
		if (minecraft.player == null) {
			return null;
		}
		EntityRenderState state = minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player).createRenderState(minecraft.player, 1f);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f + turn;
			living.yRot = 0f;
			living.xRot = 0f;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1f;
		}
		return state;
	}

	/** Draws a figure {@code size} pixels a block, with the height {@code centre} in the middle of the box. */
	private void figure(GuiGraphicsExtractor g, EntityRenderState state, float size, float centre, float pitch, int x0, int y0, int x1, int y1) {
		Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
		g.entity(state, size, new Vector3f(0f, centre, 0f), rotation, camera, x0, y0, x1, y1);
	}

	/** A close-up of your head wearing {@code hat}. */
	private void hatPreview(GuiGraphicsExtractor g, Hat hat, int x0, int y0, int x1, int y1, float turn) {
		EntityRenderState state = figure(turn);
		if (state != null) {
			state.setData(HatLayer.PREVIEW, hat);
			// The head's top is about at the height of the bounding box: the head below it, the hat (up to a crown's cross)
			// above.
			figure(g, state, (y1 - y0) / 1.06f, state.boundingBoxHeight - 0.06f, 15f, x0, y0, x1, y1);
		}
	}

	// ---- badges

	private void drawBadges(GuiGraphicsExtractor g) {
		WardrobeFeature feature = feature();
		int x0 = bodyLeft();
		int y = bodyTop() + 2 - scroll;
		List<Badge> worn = feature.worn();
		g.text(font, Component.translatable("cobblecomfort.wardrobe.worn", worn.size(), Badge.MAX_WORN), x0, y, MapUi.TEXT_DIM, false);
		if (!worn.isEmpty()) {
			MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.click_to_remove"), x0 + 100, y + 1, MapUi.TEXT_FAINT, 0.75f);
		}
		y += 12;
		y = badgeGroup(g, Component.translatable("cobblecomfort.wardrobe.group.free"), true, x0, y);
		y = badgeGroup(g, Component.translatable("cobblecomfort.wardrobe.group.earned"), false, x0, y + 4);
		contentBottom = y + scroll - bodyTop();
	}

	private int badgeGroup(GuiGraphicsExtractor g, Component title, boolean free, int x0, int y) {
		WardrobeFeature feature = feature();
		g.text(font, title, x0, y, MapUi.TEXT, false);
		y += 11;
		int columns = Math.max(1, (bodyRight() - x0) / CELL);
		int i = 0;
		for (Badge badge : Badge.values()) {
			if (badge.free() != free) {
				continue;
			}
			int cx = x0 + (i % columns) * CELL;
			int cy = y + (i / columns) * CELL;
			boolean earned = feature.earned(badge);
			boolean worn = feature.isWorn(badge);
			if (worn) {
				RenderUtil.roundedFill(g, cx, cy, cx + CELL - 2, cy + CELL - 2, 4, RenderUtil.withAlpha(WORN, 0.3f));
				RenderUtil.roundedOutline(g, cx, cy, cx + CELL - 2, cy + CELL - 2, 4, WORN, RenderUtil.withAlpha(WORN, 0.6f));
			}
			int size = Badge.CELL * 2;
			int px = cx + (CELL - 2 - size) / 2;
			int py = cy + (CELL - 2 - size) / 2;
			drawBadge(g, badge, px, py, 2, earned);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(badge.title().copy().withStyle(earned ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			if (!earned) {
				tooltip.add(Component.translatable("cobblecomfort.wardrobe.locked", advancementTitle(badge)).withStyle(ChatFormatting.GRAY));
			} else if (worn) {
				tooltip.add(Component.translatable("cobblecomfort.wardrobe.take_off").withStyle(ChatFormatting.GRAY));
			} else if (feature.worn().size() >= Badge.MAX_WORN) {
				tooltip.add(Component.translatable("cobblecomfort.wardrobe.full", Badge.MAX_WORN).withStyle(ChatFormatting.GRAY));
			} else {
				tooltip.add(Component.translatable("cobblecomfort.wardrobe.put_on").withStyle(ChatFormatting.GRAY));
			}
			hits.add(new Hit(cx, cy, cx + CELL - 2, cy + CELL - 2, () -> feature.toggle(badge), tooltip));
			i++;
		}
		return y + ((i + columns - 1) / columns) * CELL;
	}

	/**
	 * A pin {@code scale} times its texture size: the disc, then its symbol. A locked one is dimmed and shows its own art
	 * even where it has a vanilla item, which can't be dimmed.
	 */
	private void drawBadge(GuiGraphicsExtractor g, Badge badge, int x, int y, int scale, boolean earned) {
		int tint = earned ? -1 : LOCKED_TINT;
		int size = Badge.CELL * scale;
		g.blit(RenderPipelines.GUI_TEXTURED, Badge.TEXTURE, x, y, badge.u(), badge.v(), size, size, Badge.CELL, Badge.CELL,
				Badge.ATLAS_WIDTH, Badge.ATLAS_HEIGHT, tint);
		ItemStack item = badge.item();
		if (earned && !item.isEmpty()) {
			float inset = (Badge.CELL - Badge.ITEM_PIXELS) / 2f * scale;
			g.pose().pushMatrix();
			g.pose().translate(x + inset, y + inset);
			g.pose().scale(Badge.ITEM_PIXELS * scale / 16f);
			PlushFeature.INSTANCE.plain = true;
			try {
				g.item(item, 0, 0);
			} finally {
				PlushFeature.INSTANCE.plain = false;
			}
			g.pose().popMatrix();
		} else {
			g.blit(RenderPipelines.GUI_TEXTURED, Badge.TEXTURE, x, y, badge.u(), badge.symbolV(), size, size, Badge.CELL, Badge.CELL,
					Badge.ATLAS_WIDTH, Badge.ATLAS_HEIGHT, tint);
		}
	}

	/** The name of the advancement that earns a badge, as the game shows it (or the badge's own hint). */
	private Component advancementTitle(Badge badge) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection != null) {
			AdvancementHolder holder = connection.getAdvancements().get(badge.advancement());
			if (holder != null && holder.value().display().isPresent()) {
				return holder.value().display().get().title();
			}
		}
		return Component.translatable("cobblecomfort.badge." + badge.key() + ".how");
	}

	// ---- hats

	private void drawHats(GuiGraphicsExtractor g) {
		int x0 = bodyLeft();
		int x1 = bodyRight();
		int y = bodyTop() + 2 - scroll;
		for (var line : font.split(Component.translatable("cobblecomfort.wardrobe.hat_hint"), x1 - x0)) {
			g.text(font, line, x0, y, MapUi.TEXT_DIM, false);
			y += 10;
		}
		y += 4;
		for (Hat.Group group : Hat.Group.values()) {
			y = hatGroup(g, group, x0, y) + 4;
		}
		contentBottom = y + scroll - bodyTop();
	}

	/** A group's kinds of hat, one tile each; under them the colours of the kind clicked open. */
	private int hatGroup(GuiGraphicsExtractor g, Hat.Group group, int x0, int y) {
		g.text(font, group.title(), x0, y, MapUi.TEXT, false);
		if (group.seasonal()) {
			MapUi.smallText(g, font, group.season(), x0 + font.width(group.title()) + 6, y + 1,
					group.inSeason() ? WORN : MapUi.TEXT_FAINT, 0.75f);
		}
		y += 11;
		int columns = Math.max(1, (bodyRight() - x0) / HAT_CELL);
		List<Hat.Style> styles = new ArrayList<>();
		for (Hat.Style style : Hat.Style.values()) {
			if (style.group == group) {
				styles.add(style);
			}
		}
		for (int row = 0; row * columns < styles.size(); row++) {
			List<Hat.Style> line = styles.subList(row * columns, Math.min(styles.size(), (row + 1) * columns));
			for (int i = 0; i < line.size(); i++) {
				int cx = x0 + i * HAT_CELL;
				styleTile(g, line.get(i), cx, y, cx + HAT_CELL - 2, y + HAT_CELL - 2);
			}
			y += HAT_CELL;
			if (line.contains(openStyle)) {
				y = colours(g, openStyle, x0, y, columns) + 4;
			}
		}
		return y;
	}

	/** The colours of the open kind of hat, in a panel of their own right under its row. */
	private int colours(GuiGraphicsExtractor g, Hat.Style style, int x0, int y, int columns) {
		List<Hat> variants = Hat.variants(style);
		int rows = (variants.size() + columns - 1) / columns;
		int bottom = y + 14 + rows * HAT_CELL + 2;
		RenderUtil.roundedFill(g, x0 - 2, y, bodyRight() + 2, bottom, 4, 0x20FFFFFF);
		MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.hat_colors_title", style.title()), x0 + 2, y + 4,
				MapUi.TEXT_DIM, 0.75f);
		for (int k = 0; k < variants.size(); k++) {
			int cx = x0 + (k % columns) * HAT_CELL;
			int cy = y + 14 + (k / columns) * HAT_CELL;
			hatTile(g, variants.get(k), cx, cy, cx + HAT_CELL - 2, cy + HAT_CELL - 2);
		}
		if (reveal && bottom > visibleBottom()) {
			// Just opened below the bottom edge: scroll it into view.
			smoothScroll.scroll(bottom - visibleBottom());
		}
		reveal = false;
		return bottom;
	}

	/** One kind of hat: shown in the colour you wear, else the first you have; a dot below for each of its colours. */
	private void styleTile(GuiGraphicsExtractor g, Hat.Style style, int cx, int cy, int cx1, int cy1) {
		WardrobeFeature feature = feature();
		List<Hat> variants = Hat.variants(style);
		Hat worn = feature.hat() != null && feature.hat().style == style ? feature.hat() : null;
		Hat shown = worn;
		int earned = 0;
		for (Hat hat : variants) {
			if (feature.earned(hat)) {
				earned++;
				if (shown == null) {
					shown = hat;
				}
			}
		}
		if (shown == null) {
			shown = variants.getFirst();
		}
		boolean season = style.group.inSeason();
		boolean open = openStyle == style;
		tileBack(g, cx, cy, cx1, cy1, worn != null);
		if (open) {
			RenderUtil.roundedOutline(g, cx, cy, cx1, cy1, 4, MapUi.TEXT, RenderUtil.withAlpha(MapUi.TEXT, 0.5f));
		}
		preview(g, shown, cx, cy, cx1, cy1);
		if (earned == 0 || !season) {
			veil(g, cx, cy, cx1, cy1, earned == 0);
		}
		int dx = (cx + cx1) / 2 - (variants.size() * 4 - 1) / 2;
		for (Hat hat : variants) {
			g.fill(dx - 1, cy1 - 6, dx + 4, cy1 - 1, 0xC0101014);
			g.fill(dx, cy1 - 5, dx + 3, cy1 - 2, feature.earned(hat) ? hat.main[0] : RenderUtil.withAlpha(hat.main[0], 0.35f));
			dx += 4;
		}
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(style.title().copy().withStyle(earned > 0 && season ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (!season) {
			tooltip.add(Component.translatable("cobblecomfort.wardrobe.hat_out_of_season", style.group.season()).withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("cobblecomfort.wardrobe.hat_count", earned, variants.size()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(open ? "cobblecomfort.wardrobe.hat_colors_hide" : "cobblecomfort.wardrobe.hat_colors")
				.withStyle(ChatFormatting.GRAY));
		hits.add(new Hit(cx, cy, cx1, cy1, () -> {
			openStyle = openStyle == style ? null : style;
			reveal = openStyle != null;
		}, tooltip));
	}

	/** One colour of a hat: click to put it on or take it off. */
	private void hatTile(GuiGraphicsExtractor g, Hat hat, int cx, int cy, int cx1, int cy1) {
		WardrobeFeature feature = feature();
		boolean earned = feature.earned(hat);
		boolean season = hat.inSeason();
		boolean worn = feature.hat() == hat;
		tileBack(g, cx, cy, cx1, cy1, worn);
		preview(g, hat, cx, cy, cx1, cy1);
		if (!earned || !season) {
			veil(g, cx, cy, cx1, cy1, !earned);
		}
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(hat.title().copy().withStyle(earned && season ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (!season) {
			tooltip.add(Component.translatable("cobblecomfort.wardrobe.hat_out_of_season", hat.style.group.season()).withStyle(ChatFormatting.GRAY));
		}
		if (!earned) {
			tooltip.add(unlockHint(hat).copy().withStyle(ChatFormatting.GRAY));
		} else if (season) {
			tooltip.add(Component.translatable(worn ? "cobblecomfort.wardrobe.hat_off" : "cobblecomfort.wardrobe.hat_on").withStyle(ChatFormatting.GRAY));
		}
		hits.add(new Hit(cx, cy, cx1, cy1, () -> {
			if (feature.earned(hat) && hat.inSeason()) {
				feature.wearHat(feature.hat() == hat ? null : hat);
			}
		}, tooltip));
	}

	private void tileBack(GuiGraphicsExtractor g, int cx, int cy, int cx1, int cy1, boolean worn) {
		if (worn) {
			RenderUtil.roundedFill(g, cx, cy, cx1, cy1, 4, RenderUtil.withAlpha(WORN, 0.3f));
			RenderUtil.roundedOutline(g, cx, cy, cx1, cy1, 4, WORN, RenderUtil.withAlpha(WORN, 0.6f));
		} else {
			RenderUtil.roundedFill(g, cx, cy, cx1, cy1, 4, 0x18FFFFFF);
		}
	}

	/** The hat on your own head, three-quarter on; the one under the mouse turns. */
	private void preview(GuiGraphicsExtractor g, Hat hat, int cx, int cy, int cx1, int cy1) {
		boolean hovered = hoverX >= cx && hoverX < cx1 && hoverY >= cy && hoverY < cy1;
		float turn = hovered ? (System.nanoTime() / 1e9f * 90f + 35f) % 360f : 35f;
		hatPreview(g, hat, cx + 1, cy + 1, cx1 - 1, cy1 - 1, turn);
	}

	/** Locked: a dark veil, with a question mark while not earned (out of season it is only veiled). */
	private void veil(GuiGraphicsExtractor g, int cx, int cy, int cx1, int cy1, boolean unknown) {
		RenderUtil.roundedFill(g, cx, cy, cx1, cy1, 4, 0xA0101014);
		if (unknown) {
			g.centeredText(font, "?", (cx + cx1) / 2, (cy + cy1) / 2 - 4, MapUi.TEXT);
		}
	}

	/** How a locked hat is earned, with the name of the advancement, item or biome as the game shows it. */
	private Component unlockHint(Hat hat) {
		Identifier id = hat.unlock.id();
		return switch (hat.unlock.kind()) {
			case FREE -> Component.empty();
			case ADVANCEMENT -> {
				ClientPacketListener connection = minecraft.getConnection();
				AdvancementHolder holder = connection == null ? null : connection.getAdvancements().get(id);
				Component title = holder != null && holder.value().display().isPresent() ? holder.value().display().get().title()
						: Component.translatable("advancements." + id.getPath().replace('/', '.') + ".title");
				yield Component.translatable("cobblecomfort.wardrobe.unlock.advancement", title);
			}
			case ITEM -> Component.translatable("cobblecomfort.wardrobe.unlock.item", new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName());
			case BIOME -> Component.translatable("cobblecomfort.wardrobe.unlock.biome", Component.translatable(id.toLanguageKey("biome")));
		};
	}

	// ---- plush

	private void drawPlush(GuiGraphicsExtractor g) {
		PetPlushFeature plush = PetPlushFeature.INSTANCE;
		int x0 = bodyLeft();
		int x1 = bodyRight();
		int y = bodyTop() + 2 - scroll;
		y = toggleRow(g, Component.translatable("cobblecomfort.wardrobe.plush_on"), plush.config().enabled, true, x0, x1, y, () -> {
			plush.config().enabled = !plush.config().enabled;
			plush.save();
		});
		PetStore store = PetFeature.INSTANCE.store();
		if (!PetFeature.INSTANCE.active() || !store.loaded()) {
			drawHint(g, Component.translatable("cobblecomfort.wardrobe.no_pet_memory"), x0, x1, y + 8);
			return;
		}
		List<PetEntry> pets = new ArrayList<>();
		for (PetEntry pet : store.all()) {
			if (PetPlushFeature.canWear(pet)) {
				pets.add(pet);
			}
		}
		if (pets.isEmpty()) {
			drawHint(g, Component.translatable("cobblecomfort.wardrobe.no_pets"), x0, x1, y + 8);
			return;
		}
		MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.plush_hint"), x0, y + 2, MapUi.TEXT_FAINT, 0.75f);
		y += 12;
		boolean active = plush.config().enabled;
		for (PetEntry pet : pets) {
			g.item(PetFeature.INSTANCE.icon(pet), x0, y + 2);
			g.text(font, PetFeature.title(pet), x0 + 20, y + 6, active ? MapUi.TEXT : MapUi.TEXT_DIM, false);
			boolean charm = plush.isCharm(pet);
			boolean pack = plush.isPack(pet);
			int bx = x1 - 2 * 58;
			chip(g, Component.translatable("cobblecomfort.wardrobe.as_pack"), pack, bx, y + 2, 56,
					() -> plush.togglePack(pet), Component.translatable(pack ? "cobblecomfort.pets.pack_off" : "cobblecomfort.pets.pack_on"));
			chip(g, Component.translatable("cobblecomfort.wardrobe.as_charm"), charm, bx + 58, y + 2, 56,
					() -> plush.toggleCharm(pet), Component.translatable(charm ? "cobblecomfort.pets.charm_off" : "cobblecomfort.pets.charm_on"));
			y += ROW;
		}
		contentBottom = y + scroll - bodyTop();
	}

	/** A small pill button that lights up while on. */
	private void chip(GuiGraphicsExtractor g, Component label, boolean on, int x, int y, int w, Runnable action, Component tooltip) {
		int x1 = x + w;
		int y1 = y + 16;
		MapUi.buttonFace(g, x, y, x1, y1, false, on, WORN);
		if (!on) {
			RenderUtil.roundedOutline(g, x, y, x1, y1, 3, 0x30FFFFFF, 0x18FFFFFF);
		}
		g.centeredText(font, label, (x + x1) / 2, y + 4, on ? MapUi.TEXT : MapUi.TEXT_DIM);
		hits.add(new Hit(x, y, x1, y1, action, List.of(tooltip)));
	}

	// ---- belt

	private void drawBelt(GuiGraphicsExtractor g) {
		BeltFeature belt = BeltFeature.INSTANCE;
		BeltConfig c = belt.config();
		int x0 = bodyLeft();
		int x1 = bodyRight();
		int y = bodyTop() + 2 - scroll;
		y = toggleRow(g, Component.translatable("cobblecomfort.wardrobe.belt_on"), c.enabled, true, x0, x1, y, () -> {
			c.enabled = !c.enabled;
			belt.save();
		});
		y = toggleRow(g, Component.translatable("cobblecomfort.option.belt.bundles"), c.bundles, c.enabled, x0 + 10, x1, y, () -> {
			c.bundles = !c.bundles;
			belt.save();
		});
		y = toggleRow(g, Component.translatable("cobblecomfort.option.belt.melee"), c.melee, c.enabled, x0 + 10, x1, y, () -> {
			c.melee = !c.melee;
			belt.save();
		});
		y = toggleRow(g, Component.translatable("cobblecomfort.option.belt.ranged"), c.ranged, c.enabled, x0 + 10, x1, y, () -> {
			c.ranged = !c.ranged;
			belt.save();
		});
		y += 6;
		NameCharmFeature charm = NameCharmFeature.INSTANCE;
		y = toggleRow(g, Component.translatable("cobblecomfort.wardrobe.name_charm"), charm.config().enabled, true, x0, x1, y, () -> {
			charm.config().enabled = !charm.config().enabled;
			charm.save();
		});
		MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.name_charm_hint"), x0 + 10, y, MapUi.TEXT_FAINT, 0.75f);
		contentBottom = y + 10 + scroll - bodyTop();
	}

	/** A label with an on/off switch on the right; the whole row toggles. */
	private int toggleRow(GuiGraphicsExtractor g, Component label, boolean on, boolean active, int x0, int x1, int y, Runnable toggle) {
		g.text(font, label, x0, y + 5, active ? MapUi.TEXT : MapUi.TEXT_FAINT, false);
		MapUi.toggle(g, x1 - 16, y + 9, on, active, MapUi.ACCENT);
		hits.add(new Hit(x0, y, x1, y + 18, toggle, List.of()));
		return y + 18;
	}

	private void drawHint(GuiGraphicsExtractor g, Component text, int x0, int x1, int y) {
		for (var line : font.split(text, x1 - x0)) {
			g.text(font, line, x0, y, MapUi.TEXT_DIM, false);
			y += 10;
		}
	}

	// ---- outfits

	private void drawOutfits(GuiGraphicsExtractor g) {
		WardrobeFeature feature = feature();
		int x0 = bodyLeft();
		int x1 = bodyRight();
		int y = bodyTop() + 2 - scroll;
		List<WardrobeConfig.Outfit> outfits = feature.config().outfits;
		if (outfits.isEmpty()) {
		contentBottom = 0;
			drawHint(g, Component.translatable("cobblecomfort.wardrobe.no_outfits"), x0, x1, y + 4);
			return;
		}
		for (WardrobeConfig.Outfit outfit : List.copyOf(outfits)) {
			boolean wearing = feature.wearing(outfit);
			if (wearing) {
				RenderUtil.roundedFill(g, x0 - 2, y, x1 + 2, y + ROW - 2, 3, RenderUtil.withAlpha(WORN, 0.2f));
			}
			// The outfit's badges in front of its name.
			int bx = x0;
			for (String key : outfit.badges) {
				Badge badge = Badge.byKey(key);
				if (badge != null) {
					drawBadge(g, badge, bx, y + 4, 1, true);
					bx += Badge.CELL + 1;
				}
			}
			Hat hat = Hat.byKey(outfit.hat);
			int hatX = x0 + Badge.MAX_WORN * (Badge.CELL + 1);
			if (hat != null) {
				g.pose().pushMatrix();
				g.pose().translate(hatX, y + 4);
				g.pose().scale(0.75f);
				g.item(hat.icon(), 0, 0);
				g.pose().popMatrix();
			}
			int textX = hatX + Badge.CELL + 4;
			g.text(font, outfit.name, textX, y + 6, wearing ? MapUi.TEXT : MapUi.TEXT_DIM, false);
			if (wearing) {
				MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.wearing"), textX + font.width(outfit.name) + 6, y + 7, WORN, 0.75f);
			}
			int del = x1 - 14;
			hits.add(new Hit(del, y, x1, y + ROW - 2, () -> feature.deleteOutfit(outfit),
					List.of(Component.translatable("cobblecomfort.wardrobe.delete_outfit").withStyle(ChatFormatting.RED))));
			g.centeredText(font, "✕", del + 7, y + 6, MapUi.TEXT_FAINT);
			hits.add(new Hit(x0 - 2, y, del, y + ROW - 2, () -> feature.wear(outfit),
					List.of(Component.translatable("cobblecomfort.wardrobe.wear_outfit"))));
			y += ROW;
		}
		contentBottom = y + scroll - bodyTop();
	}

	private void saveOutfit() {
		String value = name.getValue().trim();
		if (value.isEmpty()) {
			return;
		}
		feature().saveOutfit(value);
		name.setValue("");
	}

	// ---------------------------------------------------------------- input

	private boolean overPreview(double x, double y) {
		return x >= left + 8 && x < left + PREVIEW_WIDTH && y >= top + 8 && y < bodyBottom();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Our own areas first: they don't overlap the widgets, and other mods may claim clicks on the screen as handled.
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overPreview(event.x(), event.y())) {
			dragging = true;
			spinVelocity = 0f;
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() >= bodyTop() && event.y() < visibleBottom()) {
			for (Hit hit : List.copyOf(hits)) {
				if (hit.contains(event.x(), event.y())) {
					hit.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			spin -= (float) dx * 1.6f;
			spinVelocity = (float) -dx * 1.6f * 30f;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (overPreview(mouseX, mouseY)) {
			spin -= (float) scrollY * 20f;
			return true;
		}
		smoothScroll.scroll(-scrollY * 24);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (name.isFocused() && event.key() == InputConstants.KEY_RETURN) {
			saveOutfit();
			return true;
		}
		if (!name.isFocused() && feature().key().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
