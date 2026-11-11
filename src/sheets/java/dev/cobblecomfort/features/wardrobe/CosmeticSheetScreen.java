package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.features.map.render.MapUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One page of a cosmetics sheet: every piece of a category, each kind under its name, every colour on the figure in a
 * tile with its name below. Drawn by the cosmetic sheets game test, which screenshots the pages and stacks them.
 */
public final class CosmeticSheetScreen extends Screen {
	private static final int TILE = 48;
	/** Wide enough for the longer names under the tiles. */
	private static final int CELL_W = TILE + 20;
	private static final int CELL_H = TILE + 16;
	private static final float LABEL_SCALE = 0.5f;
	private static final int HEADER_H = 14;
	/** Between sections side by side and between shelves. */
	private static final int GAP = 10;
	private static final int MARGIN = 8;
	private static final int TITLE_H = 18;
	private static final int BACKGROUND = 0xFF1C1D22;
	private static final int TILE_BACK = 0xFF2A2C33;

	/** What a sheet is of. */
	public enum Category {
		BADGES("badges"), HATS("hats"), NECK("neck"), BACK("back"), CHARMS("charms"), TOTEMS("totems"), TOOL_CHARMS("tool_charms"), FACE("face"), COMPANIONS("companions");

		public final String file;

		Category(String file) {
			this.file = file;
		}
	}

	private interface Drawer {
		void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1);
	}

	private record Item(Component label, Drawer drawer) {
	}

	private record Section(Component title, List<Item> items) {
	}

	/** A section placed on a page: its tiles in rows of {@code columns}. */
	private record Block(Section section, int x, int y, int columns) {
		int height() {
			return HEADER_H + (section.items().size() + columns - 1) / columns * CELL_H;
		}
	}

	private record Page(List<Block> blocks, int bottom) {
	}

	private final Category category;
	private final int page;
	private List<Page> pages = List.of();

	public CosmeticSheetScreen(Category category, int page) {
		super(Component.literal(category.file));
		this.category = category;
		this.page = page;
	}

	public int pageCount() {
		return pages.size();
	}

	/** How far down this page is drawn on, in GUI pixels; the rest is empty. */
	public int usedHeight() {
		return page < pages.size() ? pages.get(page).bottom() + MARGIN : height;
	}

	/**
	 * Places the sections side by side in shelves, a new shelf where one doesn't fit beside the others, a new page where a
	 * shelf doesn't fit below. Only the first page has the title.
	 */
	@Override
	protected void init() {
		int maxColumns = Math.max(1, (width - 2 * MARGIN) / CELL_W);
		pages = new ArrayList<>();
		List<Block> blocks = new ArrayList<>();
		int top = TITLE_H;
		int x = MARGIN;
		int y = top;
		int shelf = 0;
		for (Section section : sections(category)) {
			int columns = Math.min(maxColumns, section.items().size());
			int w = columns * CELL_W;
			if (x > MARGIN && x + w > width - MARGIN) {
				x = MARGIN;
				y += shelf + GAP;
				shelf = 0;
			}
			Block block = new Block(section, x, y, columns);
			if (y > top && y + block.height() > height - MARGIN) {
				pages.add(new Page(blocks, shelf == 0 ? y - GAP : y + shelf));
				blocks = new ArrayList<>();
				top = MARGIN;
				x = MARGIN;
				y = top;
				shelf = 0;
				block = new Block(section, x, y, columns);
			}
			blocks.add(block);
			shelf = Math.max(shelf, block.height());
			x += w + GAP;
		}
		if (!blocks.isEmpty()) {
			pages.add(new Page(blocks, y + shelf));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, BACKGROUND);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (page == 0) {
			g.text(font, Component.literal(category.file), MARGIN, 6, MapUi.TEXT_DIM, false);
		}
		if (page >= pages.size()) {
			return;
		}
		for (Block block : pages.get(page).blocks()) {
			g.text(font, block.section().title(), block.x(), block.y() + 3, MapUi.TEXT, false);
			List<Item> items = block.section().items();
			for (int i = 0; i < items.size(); i++) {
				Item item = items.get(i);
				int cx = block.x() + i % block.columns() * CELL_W;
				int x = cx + (CELL_W - TILE) / 2;
				int y = block.y() + HEADER_H + i / block.columns() * CELL_H;
				g.fill(x, y, x + TILE, y + TILE, TILE_BACK);
				item.drawer().draw(g, x, y, x + TILE, y + TILE);
				// Long names wrap onto a second line.
				var lines = font.split(item.label(), (int) ((CELL_W - 2) / LABEL_SCALE));
				for (int l = 0; l < Math.min(2, lines.size()); l++) {
					float lineX = cx + CELL_W / 2f - (font.width(lines.get(l)) - 1) * LABEL_SCALE / 2f;
					g.pose().pushMatrix();
					g.pose().translate(lineX, y + TILE + 3 + l * 5);
					g.pose().scale(LABEL_SCALE);
					g.text(font, lines.get(l), 0, 0, MapUi.TEXT_DIM, false);
					g.pose().popMatrix();
				}
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- content

	private static List<Section> sections(Category category) {
		return switch (category) {
			case BADGES -> badges();
			case HATS -> grouped(Hat.Style.values(), Hat.Style::title, Hat::variants, Hat::title,
					hat -> (g, x0, y0, x1, y1) -> hat(g, hat, x0, y0, x1, y1));
			case NECK -> grouped(Neck.Style.values(), Neck.Style::title, Neck::variants, Neck::title,
					neck -> (g, x0, y0, x1, y1) -> WardrobeScreen.neckPreview(g, neck, x0, y0, x1, y1, WardrobeScreen.tile(35f)));
			case BACK -> grouped(BackPiece.Style.values(), BackPiece.Style::title, BackPiece::variants, BackPiece::title,
					piece -> (g, x0, y0, x1, y1) -> WardrobeScreen.backPreview(g, piece, x0, y0, x1, y1,
							WardrobeScreen.tile(WardrobeScreen.backTurn(piece))));
			case CHARMS -> grouped(Trinket.Style.values(), Trinket.Style::title, Trinket::variants, Trinket::title,
					trinket -> (g, x0, y0, x1, y1) -> WardrobeScreen.trinketPreview(g, trinket, x0, y0, x1, y1, WardrobeScreen.tile(215f)));
			case TOTEMS -> grouped(TotemSkin.Style.values(), TotemSkin.Style::title, TotemSkin::variants, TotemSkin::title,
					skin -> (g, x0, y0, x1, y1) -> WardrobeScreen.totemPreview(g, skin, x0 + 3, y0 + 3, x1 - 3, y1 - 3));
			case FACE -> grouped(Face.Style.values(), Face.Style::title, Face::variants, Face::title,
					face -> (g, x0, y0, x1, y1) -> WardrobeScreen.facePreview(g, face, x0, y0, x1, y1, WardrobeScreen.tile(25f)));
			case TOOL_CHARMS -> grouped(ToolCharm.Style.values(), ToolCharm.Style::title, ToolCharm::variants, ToolCharm::title,
					charm -> (g, x0, y0, x1, y1) -> WardrobeScreen.toolCharmPreview(g, charm.style.showcase, charm, x0 + 3, y0 + 3, x1 - 3, y1 - 3));
			case COMPANIONS -> companions();
		};
	}

	/** Every companion, then each kind once happy (just petted) and once asleep. */
	private static List<Section> companions() {
		List<Section> sections = new ArrayList<>(grouped(Companion.Style.values(), Companion.Style::title, Companion::variants,
				Companion::title, companion -> companion(companion, null)));
		CompanionModel.Pose happy = new CompanionModel.Pose(0, 0, 0, 0, 0, 0, 0.2f, 0, 1f, 0, 22f, CompanionModel.NONE, 0, 0, 0);
		CompanionModel.Pose dozing = new CompanionModel.Pose(0, 0, 0, 1f, 0, 0, 0, 0, 0, 0, 30f, CompanionModel.NONE, 0, 0, 0);
		for (CompanionModel.Pose pose : new CompanionModel.Pose[]{happy, dozing}) {
			List<Item> items = new ArrayList<>();
			for (Companion.Style style : Companion.Style.values()) {
				items.add(new Item(style.title(), companion(Companion.variants(style).getFirst(), pose)));
			}
			sections.add(new Section(Component.literal(pose == happy ? "Happy" : "Asleep"), items));
		}
		// What the red panda does by itself: rearing up, nibbling bamboo (fresh and half eaten), washing its face.
		CompanionModel.Pose idle = CompanionModel.Pose.idle(18f);
		CompanionModel.Pose[] acts = {idle.doing(CompanionModel.STAND, 0.5f), idle.doing(CompanionModel.SNACK, 0.1f),
				idle.doing(CompanionModel.SNACK, 0.7f), idle.doing(CompanionModel.GROOM, 0.5f)};
		String[] names = {"Rearing up", "Snack", "Snack, half eaten", "Grooming"};
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < acts.length; i++) {
			items.add(new Item(Component.literal(names[i]), companion(Companion.RED_PANDA_CLASSIC, acts[i])));
		}
		items.add(new Item(Component.literal("Side"), companion(Companion.RED_PANDA_CLASSIC, idle, 100f)));
		items.add(new Item(Component.literal("Asleep, side"), companion(Companion.RED_PANDA_CLASSIC, dozing, 120f)));
		items.add(new Item(Component.literal("Asleep, from above"), companion(Companion.RED_PANDA_CLASSIC, dozing, 200f)));
		sections.add(new Section(Component.literal("Red panda"), items));
		// The turtle: walking, swimming, hiding (peeking out), munching a berry, yawning, asleep.
		List<Item> turtle = new ArrayList<>();
		CompanionModel.Pose walking = new CompanionModel.Pose(0.8f, 1f, 0, 0, 0, 0, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0);
		CompanionModel.Pose swimming = new CompanionModel.Pose(0, 0, 0, 0, 1f, 0, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0);
		Object[][] turtlePoses = {{"Walking", walking}, {"Swimming", swimming}, {"Hiding", idle.doing(CompanionModel.HIDE, 0.5f)},
				{"Snack", idle.doing(CompanionModel.SNACK, 0.2f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Asleep", dozing}, {"Happy", happy}};
		for (Object[] entry : turtlePoses) {
			turtle.add(new Item(Component.literal((String) entry[0]), companion(Companion.TURTLE_GREEN, (CompanionModel.Pose) entry[1])));
		}
		sections.add(new Section(Component.literal("Turtle"), turtle));
		// The kitten: hopping, ducking and peeking out, out of the box (jumping, stretching, washing), licking a paw in the
		// box, yawning, asleep, happy, afloat.
		List<Item> kitten = new ArrayList<>();
		CompanionModel.Pose hopping = new CompanionModel.Pose(0.8f, 1f, 0, 0, 0, 0, 0, 0, 0, -0.5f, 18f, CompanionModel.NONE, 0, 0, 0);
		Object[][] kittenPoses = {{"Hopping", hopping}, {"Ducked", idle.doing(CompanionModel.HIDE, 0.4f)},
				{"Peeking", idle.doing(CompanionModel.HIDE, 0.85f)}, {"Jumping out", idle.doing(CompanionModel.OUT, 0.06f)},
				{"Stretching", idle.doing(CompanionModel.OUT, 0.3f)}, {"Washing outside", idle.doing(CompanionModel.OUT, 0.66f)},
				{"Licking a paw", idle.doing(CompanionModel.GROOM, 0.5f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Asleep", dozing}, {"Happy", happy}, {"Afloat", swimming}};
		for (Object[] entry : kittenPoses) {
			kitten.add(new Item(Component.literal((String) entry[0]), companion(Companion.KITTEN_GINGER, (CompanionModel.Pose) entry[1])));
		}
		sections.add(new Section(Component.literal("Kitten"), kitten));
		// The shadow wolf: trotting, sitting, howling, bowing to play, vanishing into smoke, yawning, asleep, happy; and
		// every variant in the dark (fading into the shadows).
		List<Item> wolf = new ArrayList<>();
		CompanionModel.Pose sitting = new CompanionModel.Pose(0, 0, 1f, 0, 0, 0, 0.3f, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0);
		Object[][] wolfPoses = {{"Trotting", walking}, {"Sitting", sitting}, {"Howling", idle.doing(CompanionModel.HOWL, 0.5f)},
				{"Play bow", idle.doing(CompanionModel.PLAYBOW, 0.5f)}, {"Vanishing", idle.doing(CompanionModel.VANISH, 0.3f)},
				{"Smoke", idle.doing(CompanionModel.VANISH, 0.5f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Asleep", dozing}, {"Happy", happy}, {"Side", idle}};
		for (Object[] entry : wolfPoses) {
			String name = (String) entry[0];
			wolf.add(new Item(Component.literal(name), companion(Companion.SHADOW_WOLF_NIGHT, (CompanionModel.Pose) entry[1],
					name.equals("Side") ? 100f : 30f)));
		}
		for (Companion variant : Companion.variants(Companion.Style.SHADOW_WOLF)) {
			wolf.add(new Item(Component.literal("In the dark"), companion(variant, idle.inShade(1f))));
		}
		wolf.add(new Item(Component.literal("Howling in the dark"), companion(Companion.SHADOW_WOLF_SOUL,
				idle.doing(CompanionModel.HOWL, 0.5f).inShade(1f))));
		sections.add(new Section(Component.literal("Shadow wolf"), wolf));
		return sections;
	}

	private static Drawer companion(Companion companion, CompanionModel.Pose pose) {
		return companion(companion, pose, 30f);
	}

	private static Drawer companion(Companion companion, CompanionModel.Pose pose, float turn) {
		return (g, x0, y0, x1, y1) -> {
			var state = WardrobeScreen.tile(turn);
			if (state != null && pose != null) {
				state.setData(CompanionLayer.STANCE, pose);
			}
			WardrobeScreen.companionPreview(g, new Companion.Look(companion, "", Companion.Accessory.NONE, 14), x0, y0, x1, y1, state);
		};
	}

	/** A hat on the head, from further away than in the wardrobe so tall ones fit whole. */
	private static void hat(GuiGraphicsExtractor g, Hat hat, int x0, int y0, int x1, int y1) {
		var state = WardrobeScreen.tile(35f);
		if (state != null) {
			state.setData(HatLayer.PREVIEW, hat);
			WardrobeScreen.figure(g, state, (y1 - y0) / 1.3f, state.boundingBoxHeight + 0.1f, 15f, x0, y0, x1, y1);
		}
	}

	private static List<Section> badges() {
		List<Item> free = new ArrayList<>();
		List<Item> earned = new ArrayList<>();
		for (Badge badge : Badge.values()) {
			Drawer drawer = (g, x0, y0, x1, y1) -> {
				int scale = 3;
				int size = Badge.CELL * scale;
				WardrobeScreen.drawBadge(g, badge, (x0 + x1 - size) / 2, (y0 + y1 - size) / 2, scale, true);
			};
			(badge.free() ? free : earned).add(new Item(badge.title(), drawer));
		}
		return List.of(new Section(Component.translatable("cobblecomfort.wardrobe.group.free"), free),
				new Section(Component.translatable("cobblecomfort.wardrobe.group.earned"), earned));
	}

	private static <S, P> List<Section> grouped(S[] styles, Function<S, Component> styleTitle, Function<S, List<P>> variants,
			Function<P, Component> title, Function<P, Drawer> drawer) {
		List<Section> sections = new ArrayList<>();
		for (S style : styles) {
			List<Item> items = new ArrayList<>();
			for (P piece : variants.apply(style)) {
				items.add(new Item(title.apply(piece), drawer.apply(piece)));
			}
			if (!items.isEmpty()) {
				sections.add(new Section(styleTitle.apply(style), items));
			}
		}
		return sections;
	}
}
