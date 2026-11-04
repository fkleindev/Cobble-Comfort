package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.features.belt.BeltConfig;
import dev.cobblecomfort.features.belt.BeltFeature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.render.MapUi;
import dev.cobblecomfort.features.map.screen.FlatButton;
import dev.cobblecomfort.features.map.screen.TabButton;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.features.plush.PlushFeature;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The wardrobe: the player on a turntable on the left (drag to turn), and on the right tabs for badges, hats, the face, what
 * is worn round the neck and on the back, totems, the charms on weapons and tools, the belt and saved outfits. Every change is worn at once, so the figure shows it.
 */
public final class WardrobeScreen extends Screen {
	private static final int MAX_WIDTH = 440;
	private static final int MAX_HEIGHT = 270;
	private static final int PREVIEW_WIDTH = 132;
	private static final int TABS_HEIGHT = 22;
	private static final int CELL = 28;
	/** A tab showing just its icon. */
	private static final int TAB_ICON_WIDTH = 20;
	/** A hat tile: big enough to show the head wearing it. */
	private static final int HAT_CELL = 46;
	/** A button for a kind of weapon or tool, showing its item. */
	private static final int KIND_BUTTON = 22;
	/** How much bigger than its tile a weapon or tool is drawn, for its charm to show. */
	private static final float TOOL_ZOOM = 1.5f;
	private static final int ROW = 22;
	private static final int LINE = 0x20FFFFFF;
	private static final int WORN = 0xFFFF7FB0;
	private static final int LOCKED_TINT = 0xFF5C5C66;
	private static final ItemStack TOTEM = new ItemStack(Items.TOTEM_OF_UNDYING);

	/**
	 * On a figure drawn as a tile: it wears nothing but the piece the tile offers, so the other layers (belt, plushes,
	 * badges, armour, what is held) skip it. Tiles are many, and each is drawn every frame.
	 */
	public static final RenderStateDataKey<Boolean> TILE = RenderStateDataKey.create(() -> "cobblecomfort:wardrobe_tile");

	enum Tab {
		BADGES, HATS, FACE, NECK, BACK, TOTEM, TOOLS, BELT, OUTFITS
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
	/** The kind of neck piece, back piece or charm whose colours are shown, or null. */
	private Object openKind;
	/** On the back tab: the charms rather than what is carried. */
	private boolean charms;
	/** On the face tab: the place whose pieces are shown. */
	private Face.Place facePlace = Face.Place.EYES;
	/** On the weapons and tools tab: the kind whose charm is shown. */
	private ToolKind toolKind = ToolKind.SWORD;
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
		// Too many to label them all: the ones not open show just their icon, their name as a tooltip.
		int full = -2;
		for (Tab t : Tab.values()) {
			full += TabButton.widthFor(font, tabLabel(t)) + 2;
		}
		boolean compact = full > bodyRight() - bodyLeft();
		for (Tab t : Tab.values()) {
			Component label = tabLabel(t);
			boolean iconOnly = compact && t != tab;
			TabButton button = new TabButton(x, top + 8, iconOnly ? TAB_ICON_WIDTH : TabButton.widthFor(font, label), 18, icon(t),
					iconOnly ? Component.empty() : label, () -> tab == t, () -> {
						tab = t;
						smoothScroll.set(0);
						scroll = 0;
						rebuildWidgets();
					});
			if (iconOnly) {
				button.setTooltip(Tooltip.create(label));
			}
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

	private static Component tabLabel(Tab t) {
		return Component.translatable("cobblecomfort.wardrobe.tab." + t.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static ItemStack icon(Tab t) {
		return new ItemStack(switch (t) {
			case BADGES -> Items.GOLD_NUGGET;
			case HATS -> Items.GOLDEN_HELMET;
			case FACE -> Items.CARVED_PUMPKIN;
			case NECK -> Items.STRING;
			case BACK -> Items.ELYTRA;
			case TOTEM -> Items.TOTEM_OF_UNDYING;
			case TOOLS -> Items.IRON_SWORD;
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
				case FACE -> drawFace(g);
				case NECK -> drawNecks(g);
				case BACK -> drawBack(g);
				case TOTEM -> drawTotems(g);
				case TOOLS -> drawTools(g);
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
	static EntityRenderState figure(float turn) {
		Minecraft minecraft = Minecraft.getInstance();
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

	/** The figure for a tile, turned {@code turn} degrees: only the helmet left on, for the hat to show it. */
	static EntityRenderState tile(float turn) {
		EntityRenderState state = figure(turn);
		if (state instanceof AvatarRenderState avatar) {
			avatar.setData(TILE, true);
			avatar.chestEquipment = ItemStack.EMPTY;
			avatar.legsEquipment = ItemStack.EMPTY;
			avatar.feetEquipment = ItemStack.EMPTY;
			avatar.rightHandItemStack = ItemStack.EMPTY;
			avatar.leftHandItemStack = ItemStack.EMPTY;
			avatar.rightHandItemState.clear();
			avatar.leftHandItemState.clear();
			avatar.heldOnHead.clear();
			avatar.showCape = false;
			avatar.parrotOnLeftShoulder = null;
			avatar.parrotOnRightShoulder = null;
			avatar.arrowCount = 0;
			avatar.stingerCount = 0;
		}
		return state;
	}

	/** Whether a figure is drawn as a tile, wearing only what the tile offers. */
	public static boolean isTile(EntityRenderState state) {
		return state.getData(TILE) != null;
	}

	/** Draws a figure {@code size} pixels a block, with the height {@code centre} in the middle of the box. */
	static void figure(GuiGraphicsExtractor g, EntityRenderState state, float size, float centre, float pitch, int x0, int y0, int x1, int y1) {
		Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
		g.entity(state, size, new Vector3f(0f, centre, 0f), rotation, camera, x0, y0, x1, y1);
	}

	/** A close-up of your head wearing {@code hat}. */
	private void hatPreview(GuiGraphicsExtractor g, Hat hat, int x0, int y0, int x1, int y1, float turn) {
		hatPreview(g, hat, x0, y0, x1, y1, tile(turn));
	}

	static void hatPreview(GuiGraphicsExtractor g, Hat hat, int x0, int y0, int x1, int y1, EntityRenderState state) {
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
	static void drawBadge(GuiGraphicsExtractor g, Badge badge, int x, int y, int scale, boolean earned) {
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
		if (Arrays.stream(Hat.Style.values()).noneMatch(style -> style.group == group)) {
			return y - 4;
		}
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
		if (earned == 0) {
			veil(g, cx, cy, cx1, cy1, true);
		}
		int dx = (cx + cx1) / 2 - (variants.size() * 4 - 1) / 2;
		for (Hat hat : variants) {
			g.fill(dx - 1, cy1 - 6, dx + 4, cy1 - 1, 0xC0101014);
			g.fill(dx, cy1 - 5, dx + 3, cy1 - 2, feature.earned(hat) ? hat.main[0] : RenderUtil.withAlpha(hat.main[0], 0.35f));
			dx += 4;
		}
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(style.title().copy().withStyle(earned > 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (earned < variants.size() && !season) {
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
		if (!earned) {
			veil(g, cx, cy, cx1, cy1, true);
		}
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(hat.title().copy().withStyle(earned ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (!earned) {
			lockedHint(tooltip, hat.unlock, season, hat.style.group);
		} else {
			tooltip.add(Component.translatable(worn ? "cobblecomfort.wardrobe.hat_off" : "cobblecomfort.wardrobe.hat_on").withStyle(ChatFormatting.GRAY));
		}
		hits.add(new Hit(cx, cy, cx1, cy1, () -> {
			if (feature.earned(hat)) {
				feature.wearHat(feature.hat() == hat ? null : hat);
			}
		}, tooltip));
	}

	/** Whether a tile from y0 to y1 shows in the list now: those scrolled out of it don't draw their figure. */
	private boolean visible(int y0, int y1) {
		return y1 > bodyTop() && y0 < visibleBottom();
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
		if (!visible(cy, cy1)) {
			return;
		}
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

	/** How something locked is earned, with the name of the advancement, item or biome as the game shows it. */
	private Component unlockHint(Hat.Unlock unlock) {
		Identifier id = unlock.id();
		return switch (unlock.kind()) {
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
			case MUSIC_DISC -> Component.translatable("cobblecomfort.wardrobe.unlock.music_disc");
			case TAMED -> Component.translatable("cobblecomfort.wardrobe.unlock.tamed", Component.translatable(id.toLanguageKey("entity")));
		};
	}

	// ---- neck, back and back charms: kinds of piece, each opening its colours

	/** Draws a piece on the figure in a tile. */
	private interface Preview<P> {
		void draw(GuiGraphicsExtractor g, P piece, int cx, int cy, int cx1, int cy1);
	}

	/** One place pieces are worn: its kinds, their colours, which are yours, which is worn and how to show them. */
	private record Slot<S, P>(S[] styles, Function<S, Hat.Group> group, Function<S, Component> styleTitle,
			Function<S, List<P>> variants, Function<P, S> styleOf, Function<P, Component> title, Function<P, Hat.Unlock> unlock,
			Predicate<P> earned, Supplier<P> worn, Consumer<P> wear, ToIntFunction<P> swatch, Preview<P> preview, String on,
			String off) {
	}

	private Slot<Neck.Style, Neck> neckSlot() {
		WardrobeFeature f = feature();
		return new Slot<>(Neck.Style.values(), s -> s.group, Neck.Style::title, Neck::variants, n -> n.style, Neck::title,
				n -> n.unlock, f::earned, f::neck, f::wearNeck, n -> n.style == Neck.Style.NECKLACE ? n.accent : n.main[0],
				this::neckPreview, "cobblecomfort.wardrobe.neck_on", "cobblecomfort.wardrobe.neck_off");
	}

	private Slot<BackPiece.Style, BackPiece> backSlot() {
		WardrobeFeature f = feature();
		return new Slot<>(BackPiece.Style.values(), s -> s.group, BackPiece.Style::title, BackPiece::variants, b -> b.style,
				BackPiece::title, b -> b.unlock, f::earned, f::back, f::wearBack, b -> b.main[0], this::backPreview,
				"cobblecomfort.wardrobe.back_on", "cobblecomfort.wardrobe.back_off");
	}

	private Slot<Trinket.Style, Trinket> trinketSlot() {
		WardrobeFeature f = feature();
		return new Slot<>(Trinket.Style.values(), s -> s.group, Trinket.Style::title, Trinket::variants, t -> t.style,
				Trinket::title, t -> t.unlock, f::earned, f::trinket, f::wearTrinket, t -> t.main[0], this::trinketPreview,
				"cobblecomfort.wardrobe.trinket_on", "cobblecomfort.wardrobe.trinket_off");
	}

	/** A slot's kinds by group, as for hats: the all-year ones, then the seasonal ones with their months. */
	private <S, P> int kinds(GuiGraphicsExtractor g, Slot<S, P> slot, int x0, int y) {
		for (Hat.Group group : Hat.Group.values()) {
			List<S> styles = new ArrayList<>();
			for (S style : slot.styles()) {
				if (slot.group().apply(style) == group) {
					styles.add(style);
				}
			}
			if (styles.isEmpty()) {
				continue;
			}
			g.text(font, group.title(), x0, y, MapUi.TEXT, false);
			if (group.seasonal()) {
				MapUi.smallText(g, font, group.season(), x0 + font.width(group.title()) + 6, y + 1,
						group.inSeason() ? WORN : MapUi.TEXT_FAINT, 0.75f);
			}
			y = kindGrid(g, slot, styles, x0, y + 11) + 4;
		}
		return y;
	}

	/** A grid of kinds, one tile each, with the colours of the open kind right under its row; returns the y below. */
	private <S, P> int kindGrid(GuiGraphicsExtractor g, Slot<S, P> slot, List<S> styles, int x0, int y) {
		int columns = Math.max(1, (bodyRight() - x0) / HAT_CELL);
		for (int row = 0; row * columns < styles.size(); row++) {
			int end = Math.min(styles.size(), (row + 1) * columns);
			S opened = null;
			for (int i = row * columns; i < end; i++) {
				int cx = x0 + (i - row * columns) * HAT_CELL;
				kindTile(g, slot, styles.get(i), cx, y, cx + HAT_CELL - 2, y + HAT_CELL - 2);
				if (styles.get(i) == openKind) {
					opened = styles.get(i);
				}
			}
			y += HAT_CELL;
			if (opened != null) {
				y = kindColours(g, slot, opened, x0, y, columns) + 4;
			}
		}
		return y;
	}

	/** How to earn something not yet yours: seasonal pieces only in their months (once earned, they stay), then the condition. */
	private void lockedHint(List<Component> tooltip, Hat.Unlock unlock, boolean season, Hat.Group group) {
		if (!season) {
			tooltip.add(Component.translatable("cobblecomfort.wardrobe.hat_out_of_season", group.season()).withStyle(ChatFormatting.GRAY));
		}
		if (unlock.kind() != Hat.Unlock.Kind.FREE) {
			tooltip.add(unlockHint(unlock).copy().withStyle(ChatFormatting.GRAY));
		}
	}

	/** One kind: shown in the colour worn, else its first; a dot below for each of its colours. */
	private <S, P> void kindTile(GuiGraphicsExtractor g, Slot<S, P> slot, S style, int cx, int cy, int cx1, int cy1) {
		List<P> variants = slot.variants().apply(style);
		P current = slot.worn().get();
		P worn = current != null && slot.styleOf().apply(current) == style ? current : null;
		boolean earned = slot.earned().test(variants.getFirst());
		Hat.Group group = slot.group().apply(style);
		boolean season = group.inSeason();
		boolean open = openKind == style;
		tileBack(g, cx, cy, cx1, cy1, worn != null);
		if (open) {
			RenderUtil.roundedOutline(g, cx, cy, cx1, cy1, 4, MapUi.TEXT, RenderUtil.withAlpha(MapUi.TEXT, 0.5f));
		}
		if (visible(cy, cy1)) {
			slot.preview().draw(g, worn != null ? worn : variants.getFirst(), cx, cy, cx1, cy1);
		}
		if (!earned) {
			veil(g, cx, cy, cx1, cy1, true);
		}
		int dx = (cx + cx1) / 2 - (variants.size() * 4 - 1) / 2;
		for (P piece : variants) {
			int color = slot.swatch().applyAsInt(piece);
			g.fill(dx - 1, cy1 - 6, dx + 4, cy1 - 1, 0xC0101014);
			g.fill(dx, cy1 - 5, dx + 3, cy1 - 2, earned ? color : RenderUtil.withAlpha(color, 0.35f));
			dx += 4;
		}
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(slot.styleTitle().apply(style).copy().withStyle(earned ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (!earned) {
			lockedHint(tooltip, slot.unlock().apply(variants.getFirst()), season, group);
		}
		tooltip.add(Component.translatable(open ? "cobblecomfort.wardrobe.hat_colors_hide" : "cobblecomfort.wardrobe.hat_colors")
				.withStyle(ChatFormatting.GRAY));
		hits.add(new Hit(cx, cy, cx1, cy1, () -> {
			openKind = openKind == style ? null : style;
			reveal = openKind != null;
		}, tooltip));
	}

	/** The colours of the open kind, in a panel of their own; click one to put it on or take it off. */
	private <S, P> int kindColours(GuiGraphicsExtractor g, Slot<S, P> slot, S style, int x0, int y, int columns) {
		List<P> variants = slot.variants().apply(style);
		int rows = (variants.size() + columns - 1) / columns;
		int bottom = y + 14 + rows * HAT_CELL + 2;
		RenderUtil.roundedFill(g, x0 - 2, y, bodyRight() + 2, bottom, 4, 0x20FFFFFF);
		MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.hat_colors_title", slot.styleTitle().apply(style)),
				x0 + 2, y + 4, MapUi.TEXT_DIM, 0.75f);
		for (int k = 0; k < variants.size(); k++) {
			P piece = variants.get(k);
			int cx = x0 + (k % columns) * HAT_CELL;
			int cy = y + 14 + (k / columns) * HAT_CELL;
			int cx1 = cx + HAT_CELL - 2;
			int cy1 = cy + HAT_CELL - 2;
			boolean earned = slot.earned().test(piece);
			boolean worn = slot.worn().get() == piece;
			Hat.Group group = slot.group().apply(style);
			tileBack(g, cx, cy, cx1, cy1, worn);
			if (visible(cy, cy1)) {
				slot.preview().draw(g, piece, cx, cy, cx1, cy1);
			}
			if (!earned) {
				veil(g, cx, cy, cx1, cy1, true);
			}
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(slot.title().apply(piece).copy().withStyle(earned ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			if (!earned) {
				lockedHint(tooltip, slot.unlock().apply(piece), group.inSeason(), group);
			} else {
				tooltip.add(Component.translatable(worn ? slot.off() : slot.on()).withStyle(ChatFormatting.GRAY));
			}
			hits.add(new Hit(cx, cy, cx1, cy1, () -> {
				if (slot.earned().test(piece)) {
					slot.wear().accept(slot.worn().get() == piece ? null : piece);
				}
			}, tooltip));
		}
		if (reveal && bottom > visibleBottom()) {
			smoothScroll.scroll(bottom - visibleBottom());
		}
		reveal = false;
		return bottom;
	}

	private void drawNecks(GuiGraphicsExtractor g) {
		int x0 = bodyLeft();
		int y = hint(g, Component.translatable("cobblecomfort.wardrobe.neck_hint"), x0, bodyTop() + 2 - scroll);
		y = kinds(g, neckSlot(), x0, y);
		contentBottom = y + scroll - bodyTop();
	}

	/** A hint over the list, wrapped; returns the y below it. */
	private int hint(GuiGraphicsExtractor g, Component text, int x0, int y) {
		for (var line : font.split(text, bodyRight() - x0)) {
			g.text(font, line, x0, y, MapUi.TEXT_DIM, false);
			y += 10;
		}
		return y + 4;
	}

	/** Your neck and chest wearing {@code neck}, three-quarter on; the tile under the mouse turns. */
	private void neckPreview(GuiGraphicsExtractor g, Neck neck, int cx, int cy, int cx1, int cy1) {
		neckPreview(g, neck, cx, cy, cx1, cy1, tileFigure(cx, cy, cx1, cy1, 35f));
	}

	static void neckPreview(GuiGraphicsExtractor g, Neck neck, int cx, int cy, int cx1, int cy1, EntityRenderState state) {
		if (state != null) {
			state.setData(NeckLayer.PREVIEW, neck);
			// The shoulders are half a block below the top of the head: the chin above, the chest below.
			figure(g, state, (cy1 - cy - 2) / 0.95f, state.boundingBoxHeight - 0.55f, 10f, cx + 1, cy + 1, cx1 - 1, cy1 - 1);
		}
	}

	/** Your back carrying {@code piece}, seen from behind and a little to the side. */
	private void backPreview(GuiGraphicsExtractor g, BackPiece piece, int cx, int cy, int cx1, int cy1) {
		backPreview(g, piece, cx, cy, cx1, cy1, tileFigure(cx, cy, cx1, cy1, backTurn(piece)));
	}

	static float backTurn(BackPiece piece) {
		return BackLayer.winged(piece) ? 180f : 215f;
	}

	static void backPreview(GuiGraphicsExtractor g, BackPiece piece, int cx, int cy, int cx1, int cy1, EntityRenderState state) {
		if (state != null) {
			state.setData(BackLayer.PREVIEW, piece);
			// Wings spread far: further away for them.
			float view = BackLayer.winged(piece) ? 2.1f : 1.45f;
			figure(g, state, (cy1 - cy - 2) / view, state.boundingBoxHeight - 0.8f, 10f, cx + 1, cy + 1, cx1 - 1, cy1 - 1);
		}
	}

	/** Your back with {@code trinket} hanging from it, seen from behind and a little to the side. */
	private void trinketPreview(GuiGraphicsExtractor g, Trinket trinket, int cx, int cy, int cx1, int cy1) {
		trinketPreview(g, trinket, cx, cy, cx1, cy1, tileFigure(cx, cy, cx1, cy1, 215f));
	}

	static void trinketPreview(GuiGraphicsExtractor g, Trinket trinket, int cx, int cy, int cx1, int cy1, EntityRenderState state) {
		if (state != null) {
			state.setData(TrinketModel.PREVIEW, trinket);
			figure(g, state, (cy1 - cy - 2) / 1.05f, state.boundingBoxHeight - 0.95f, 10f, cx + 1, cy + 1, cx1 - 1, cy1 - 1);
		}
	}

	/** The figure for a tile, turned {@code turn} degrees; the one under the mouse turns round. */
	private EntityRenderState tileFigure(int cx, int cy, int cx1, int cy1, float turn) {
		boolean hovered = hoverX >= cx && hoverX < cx1 && hoverY >= cy && hoverY < cy1;
		return tile(hovered ? (System.nanoTime() / 1e9f * 90f + turn) % 360f : turn);
	}

	// ---- face

	private Slot<Face.Style, Face> faceSlot(Face.Place place) {
		WardrobeFeature f = feature();
		return new Slot<>(Face.Style.at(place), s -> s.group, Face.Style::title, Face::variants, p -> p.style, Face::title,
				p -> p.unlock, f::earned, () -> f.face(place), p -> f.wearFace(place, p), p -> p.main[0], this::facePreview,
				"cobblecomfort.wardrobe.face_on", "cobblecomfort.wardrobe.face_off");
	}

	/** The two places on the face, each with what is worn there; then the pieces for the place picked. */
	private void drawFace(GuiGraphicsExtractor g) {
		int x0 = bodyLeft();
		int y = bodyTop() + 2 - scroll;
		Face.Place[] places = Face.Place.values();
		int w = 0;
		for (Face.Place place : places) {
			w = Math.max(w, font.width(place.title()) + 16);
		}
		for (int i = 0; i < places.length; i++) {
			Face.Place place = places[i];
			Face worn = feature().face(place);
			chip(g, place.title(), facePlace == place, x0 + i * (w + 4), y, w, () -> {
				facePlace = place;
				openKind = null;
			}, worn == null ? Component.translatable("cobblecomfort.wardrobe.face_none") : worn.title());
		}
		y += 22;
		y = hint(g, Component.translatable("cobblecomfort.wardrobe.face_" + facePlace.name().toLowerCase(java.util.Locale.ROOT) + "_hint"),
				x0, y);
		y = kinds(g, faceSlot(facePlace), x0, y);
		contentBottom = y + scroll - bodyTop();
	}

	/** Your face wearing {@code face}, nearly from the front; the tile under the mouse turns. */
	private void facePreview(GuiGraphicsExtractor g, Face face, int cx, int cy, int cx1, int cy1) {
		facePreview(g, face, cx, cy, cx1, cy1, tileFigure(cx, cy, cx1, cy1, 25f));
	}

	static void facePreview(GuiGraphicsExtractor g, Face face, int cx, int cy, int cx1, int cy1, EntityRenderState state) {
		if (state instanceof AvatarRenderState avatar) {
			// Nothing on the head: a helmet would hide it.
			avatar.headEquipment = ItemStack.EMPTY;
			avatar.setData(FaceLayer.PREVIEW, face);
			// The middle of the head, a little low for a bandana's point under the chin.
			figure(g, avatar, (cy1 - cy - 2) / 0.72f, avatar.boundingBoxHeight - 0.28f, 5f, cx + 1, cy + 1, cx1 - 1, cy1 - 1);
		}
	}

	// ---- back

	// ---- totem

	private void drawTotems(GuiGraphicsExtractor g) {
		int x0 = bodyLeft();
		int y = hint(g, Component.translatable("cobblecomfort.wardrobe.totem_hint"), x0, bodyTop() + 2 - scroll);
		y = kinds(g, totemSlot(), x0, y);
		contentBottom = y + scroll - bodyTop();
	}

	private Slot<TotemSkin.Style, TotemSkin> totemSlot() {
		WardrobeFeature f = feature();
		return new Slot<>(TotemSkin.Style.values(), s -> s.group, TotemSkin.Style::title, TotemSkin::variants, t -> t.style,
				TotemSkin::title, t -> t.unlock, f::earned, f::totem, f::wearTotem, TotemSkin::swatch,
				(g, skin, cx, cy, cx1, cy1) -> totemPreview(g, skin, cx + 2, cy + 2, cx1 - 2, cy1 - 2),
				"cobblecomfort.wardrobe.totem_on", "cobblecomfort.wardrobe.totem_off");
	}

	/** A totem in a skin, as an item filling the box: cheap to draw, and the game keeps its picture while it stands still. */
	static void totemPreview(GuiGraphicsExtractor g, TotemSkin skin, int x0, int y0, int x1, int y1) {
		int size = Math.min(x1 - x0, y1 - y0);
		g.pose().pushMatrix();
		g.pose().translate(x0 + (x1 - x0 - size) / 2f, y0 + (y1 - y0 - size) / 2f);
		g.pose().scale(size / 16f);
		PlushFeature.INSTANCE.preview = skin;
		try {
			g.item(TOTEM, 0, 0);
		} finally {
			PlushFeature.INSTANCE.preview = null;
		}
		g.pose().popMatrix();
	}

	private void drawBack(GuiGraphicsExtractor g) {
		int x0 = bodyLeft();
		int y = bodyTop() + 2 - scroll;
		// The two parts: what is carried, and what hangs from it.
		int w = Math.max(font.width(Component.translatable("cobblecomfort.wardrobe.back_pieces")),
				font.width(Component.translatable("cobblecomfort.wardrobe.back_charms"))) + 16;
		chip(g, Component.translatable("cobblecomfort.wardrobe.back_pieces"), !charms, x0, y, w, () -> {
			charms = false;
			openKind = null;
		}, Component.translatable("cobblecomfort.wardrobe.back_pieces_hint"));
		chip(g, Component.translatable("cobblecomfort.wardrobe.back_charms"), charms, x0 + w + 4, y, w, () -> {
			charms = true;
			openKind = null;
		}, Component.translatable("cobblecomfort.wardrobe.back_charms_hint"));
		y += 22;
		y = hint(g, Component.translatable(charms ? "cobblecomfort.wardrobe.back_charms_hint" : "cobblecomfort.wardrobe.back_pieces_hint"),
				x0, y);
		y = charms ? kinds(g, trinketSlot(), x0, y) : kinds(g, backSlot(), x0, y);
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

	// ---- weapon and tool charms

	private Slot<ToolCharm.Style, ToolCharm> toolCharmSlot(ToolKind kind) {
		WardrobeFeature f = feature();
		return new Slot<>(ToolCharm.Style.values(), s -> s.group, ToolCharm.Style::title, ToolCharm::variants, c -> c.style,
				ToolCharm::title, c -> c.unlock, f::earned, () -> f.toolCharm(kind), c -> f.wearToolCharm(kind, c),
				c -> c.main[0], (g, charm, cx, cy, cx1, cy1) -> toolCharmPreview(g, kind, charm, cx + 2, cy + 2, cx1 - 2, cy1 - 2),
				"cobblecomfort.wardrobe.tool_charm_on", "cobblecomfort.wardrobe.tool_charm_off");
	}

	/** One button for each kind of weapon and tool, showing the item; then the charms for the kind picked. */
	private void drawTools(GuiGraphicsExtractor g) {
		int x0 = bodyLeft();
		int y = bodyTop() + 2 - scroll;
		int columns = Math.max(1, (bodyRight() - x0 + 2) / (KIND_BUTTON + 2));
		ToolKind[] kinds = ToolKind.values();
		for (int i = 0; i < kinds.length; i++) {
			ToolKind kind = kinds[i];
			int bx = x0 + i % columns * (KIND_BUTTON + 2);
			int by = y + i / columns * (KIND_BUTTON + 2);
			boolean on = kind == toolKind;
			MapUi.buttonFace(g, bx, by, bx + KIND_BUTTON, by + KIND_BUTTON, false, on, WORN);
			if (!on) {
				RenderUtil.roundedOutline(g, bx, by, bx + KIND_BUTTON, by + KIND_BUTTON, 3, 0x30FFFFFF, 0x18FFFFFF);
			}
			g.item(kind.icon(), bx + (KIND_BUTTON - 16) / 2, by + (KIND_BUTTON - 16) / 2);
			ToolCharm worn = feature().toolCharm(kind);
			Component current = worn == null ? Component.translatable("cobblecomfort.wardrobe.tool_charm_none") : worn.title();
			hits.add(new Hit(bx, by, bx + KIND_BUTTON, by + KIND_BUTTON, () -> {
				toolKind = kind;
				openKind = null;
			}, List.of(kind.title(), current.copy().withStyle(ChatFormatting.GRAY))));
		}
		y += (kinds.length + columns - 1) / columns * (KIND_BUTTON + 2) + 4;
		y = hint(g, Component.translatable("cobblecomfort.wardrobe.tools_hint", toolKind.title()), x0, y);
		// Where the charm is tied on this kind: placed by hand in the charm editor.
		Component place = Component.translatable("cobblecomfort.wardrobe.tool_charm_place", toolKind.title());
		int px1 = x0 + font.width(place) + 16;
		boolean over = hoverX >= x0 && hoverX < px1 && hoverY >= y && hoverY < y + 18;
		MapUi.textButton(g, font, place, x0, y, px1, y + 18, over, true, MapUi.ACCENT);
		ToolKind editing = toolKind;
		hits.add(new Hit(x0, y, px1, y + 18, () -> minecraft.gui.setScreen(new CharmEditorScreen(this, editing)),
				List.of(Component.translatable("cobblecomfort.wardrobe.tool_charm_place_hint").withStyle(ChatFormatting.GRAY))));
		if (feature().charmSpotMoved(toolKind)) {
			MapUi.smallText(g, font, Component.translatable("cobblecomfort.wardrobe.tool_charm_moved"), px1 + 6, y + 6, WORN, 0.75f);
		}
		y += 24;
		y = kinds(g, toolCharmSlot(toolKind), x0, y);
		contentBottom = y + scroll - bodyTop();
	}

	/**
	 * A weapon or tool of this kind with {@code charm} hanging from it, seen close up: the item larger than the box, the
	 * place the charm is tied a little above the middle, the rest cut off.
	 */
	static void toolCharmPreview(GuiGraphicsExtractor g, ToolKind kind, ToolCharm charm, int x0, int y0, int x1, int y1) {
		float size = Math.min(x1 - x0, y1 - y0) * TOOL_ZOOM;
		Vector3f spot = WardrobeFeature.INSTANCE.charmSpot(kind, false);
		float ax = (x0 + x1) / 2f - spot.x / 16f * size;
		float ay = y0 + (y1 - y0) * 0.3f - (1f - spot.y / 16f) * size;
		g.enableScissor(x0, y0, x1, y1);
		g.pose().pushMatrix();
		g.pose().translate(ax, ay);
		g.pose().scale(size / 16f);
		NameCharmFeature.INSTANCE.preview = charm;
		try {
			g.item(kind.icon(), 0, 0);
		} finally {
			NameCharmFeature.INSTANCE.preview = null;
		}
		g.pose().popMatrix();
		g.disableScissor();
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
		contentBottom = y + scroll - bodyTop();
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
			Neck neck = Neck.byKey(outfit.neck);
			if (neck != null) {
				g.pose().pushMatrix();
				g.pose().translate(hatX + 12, y + 4);
				g.pose().scale(0.75f);
				g.item(neck.icon(), 0, 0);
				g.pose().popMatrix();
			}
			BackPiece back = BackPiece.byKey(outfit.back);
			Trinket trinket = Trinket.byKey(outfit.trinket);
			Face eyes = Face.byKey(outfit.eyes);
			Face mouth = Face.byKey(outfit.mouth);
			Face skin = Face.byKey(outfit.skin);
			ItemStack[] more = {back == null ? ItemStack.EMPTY : back.icon(), trinket == null ? ItemStack.EMPTY : trinket.icon(),
					eyes == null ? ItemStack.EMPTY : eyes.icon(), mouth == null ? ItemStack.EMPTY : mouth.icon(),
					skin == null ? ItemStack.EMPTY : skin.icon()};
			for (int i = 0; i < more.length; i++) {
				g.pose().pushMatrix();
				g.pose().translate(hatX + 24 + 12 * i, y + 4);
				g.pose().scale(0.75f);
				g.item(more[i], 0, 0);
				g.pose().popMatrix();
			}
			int textX = hatX + 24 + 12 * more.length + 4;
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
