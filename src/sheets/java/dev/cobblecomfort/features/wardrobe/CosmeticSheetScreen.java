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
		CompanionModel.Pose happy = new CompanionModel.Pose(0, 0, 0, 0, 0, 0, 0.2f, 0, 1f, 0, 22f, CompanionModel.NONE, 0, 0, 0, 0, 0);
		CompanionModel.Pose dozing = new CompanionModel.Pose(0, 0, 0, 1f, 0, 0, 0, 0, 0, 0, 30f, CompanionModel.NONE, 0, 0, 0, 0, 0);
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
		CompanionModel.Pose walking = new CompanionModel.Pose(0.8f, 1f, 0, 0, 0, 0, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 0);
		CompanionModel.Pose swimming = new CompanionModel.Pose(0, 0, 0, 0, 1f, 0, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 0);
		Object[][] turtlePoses = {{"Walking", walking}, {"Swimming", swimming}, {"Hiding", idle.doing(CompanionModel.HIDE, 0.5f)},
				{"Snack", idle.doing(CompanionModel.SNACK, 0.2f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Asleep", dozing}, {"Happy", happy}};
		for (Object[] entry : turtlePoses) {
			turtle.add(new Item(Component.literal((String) entry[0]), companion(Companion.TURTLE_GREEN, (CompanionModel.Pose) entry[1])));
		}
		turtle.add(new Item(Component.literal("From behind"), companion(Companion.TURTLE_GREEN, walking, 150f)));
		turtle.add(new Item(Component.literal("Hiding, behind"), companion(Companion.TURTLE_GREEN, idle.doing(CompanionModel.HIDE, 0.5f), 150f)));
		sections.add(new Section(Component.literal("Turtle"), turtle));
		// The kitten: hopping, ducking and peeking out, out of the box (jumping, stretching, washing), licking a paw in the
		// box, yawning, asleep, happy, afloat.
		List<Item> kitten = new ArrayList<>();
		CompanionModel.Pose hopping = new CompanionModel.Pose(0.8f, 1f, 0, 0, 0, 0, 0, 0, 0, -0.5f, 18f, CompanionModel.NONE, 0, 0, 0, 0, 0);
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
		CompanionModel.Pose sitting = new CompanionModel.Pose(0, 0, 1f, 0, 0, 0, 0.3f, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 0);
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
		// The hedgehog: scurrying, rolled up, curled (and peeking), sniffing, a berry, yawning, asleep, happy, side.
		List<Item> hedgehog = new ArrayList<>();
		CompanionModel.Pose rolling = new CompanionModel.Pose(1.3f, 1f, 0, 0, 1f, 0, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 0);
		Object[][] hedgehogPoses = {{"Scurrying", walking}, {"Rolling", rolling}, {"Curled up", idle.doing(CompanionModel.HIDE, 0.3f)},
				{"Peeking", idle.doing(CompanionModel.HIDE, 0.65f)}, {"Sniffing", idle.doing(CompanionModel.SNIFF, 0.5f)},
				{"Snack", idle.doing(CompanionModel.SNACK, 0.3f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Asleep", dozing}, {"Happy", happy}};
		for (Object[] entry : hedgehogPoses) {
			hedgehog.add(new Item(Component.literal((String) entry[0]), companion(Companion.HEDGEHOG_AUTUMN, (CompanionModel.Pose) entry[1])));
		}
		hedgehog.add(new Item(Component.literal("Side"), companion(Companion.HEDGEHOG_BROWN, idle, 100f)));
		sections.add(new Section(Component.literal("Hedgehog"), hedgehog));
		// The penguin chick: waddling, sliding, flapping, shaking, yawning, asleep, happy, side.
		List<Item> penguin = new ArrayList<>();
		Object[][] penguinPoses = {{"Waddling", walking}, {"Sliding", swimming}, {"Flapping", idle.doing(CompanionModel.STAND, 0.5f)},
				{"Shaking", idle.doing(CompanionModel.SHAKE, 0.4f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Asleep", dozing}, {"Happy", happy}};
		for (Object[] entry : penguinPoses) {
			penguin.add(new Item(Component.literal((String) entry[0]), companion(Companion.PENGUIN_EMPEROR, (CompanionModel.Pose) entry[1])));
		}
		penguin.add(new Item(Component.literal("Side"), companion(Companion.PENGUIN_EMPEROR, idle, 100f)));
		sections.add(new Section(Component.literal("Penguin chick"), penguin));
		// The mech: stomping, scanning, rocket boots, venting, happy, powered down, at night with its headlamp, and its
		// copper weathering step by step.
		List<Item> mech = new ArrayList<>();
		Object[][] mechPoses = {{"Stomping", walking}, {"Scanning", idle.doing(CompanionModel.SCAN, 0.3f)},
				{"Rocket boots", idle.doing(CompanionModel.ROCKET, 0.5f)}, {"Letting off steam", idle.doing(CompanionModel.SHAKE, 0.5f)},
				{"Happy", happy}, {"Powered down", dozing}, {"Headlamp at night", idle.inShade(1f)}};
		for (Object[] entry : mechPoses) {
			mech.add(new Item(Component.literal((String) entry[0]), companion(Companion.MECH_STEEL, (CompanionModel.Pose) entry[1])));
		}
		mech.add(new Item(Component.literal("From behind"), companion(Companion.MECH_STEEL, idle, 200f)));
		for (float wear : new float[]{0.3f, 0.6f, 1f}) {
			mech.add(new Item(Component.literal("Weathered " + Math.round(wear * 100) + "%"), companion(Companion.MECH_COPPER, idle.worn(wear))));
		}
		sections.add(new Section(Component.literal("Mini mech"), mech));
		// The salamander: running, push-ups, basking, yawning embers, tongue out, asleep, happy, side, swimming.
		List<Item> salamander = new ArrayList<>();
		Object[][] salamanderPoses = {{"Running", walking}, {"Push-ups", idle.doing(CompanionModel.STAND, 0.08f)},
				{"Basking", idle.doing(CompanionModel.BASK, 0.5f)}, {"Yawning embers", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Tongue out", CompanionModel.Pose.idle(4f)}, {"Asleep", dozing}, {"Happy", happy}, {"Swimming", swimming}};
		for (Object[] entry : salamanderPoses) {
			salamander.add(new Item(Component.literal((String) entry[0]), companion(Companion.SALAMANDER_LAVA, (CompanionModel.Pose) entry[1])));
		}
		salamander.add(new Item(Component.literal("Side"), companion(Companion.SALAMANDER_LAVA, idle, 100f)));
		sections.add(new Section(Component.literal("Lava salamander"), salamander));
		// The lantern spirit: gliding, flaring up, yawning, twirling, happy, asleep, in the dark, frightened, dimmed, each
		// variant at night, the copper one weathered.
		List<Item> lantern = new ArrayList<>();
		Object[][] lanternPoses = {{"Gliding", walking}, {"Flaring up", idle.doing(CompanionModel.STAND, 0.5f)},
				{"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)}, {"Twirling", idle.doing(CompanionModel.SHAKE, 0.4f)},
				{"Happy", happy}, {"Asleep", dozing}, {"Afraid", idle.afraid(1f, 0)}, {"Dimmed (hurt)", idle.afraid(0, 0.5f)},
				{"Dread (warden)", idle.afraid(0.45f, 0.85f)}};
		for (Object[] entry : lanternPoses) {
			lantern.add(new Item(Component.literal((String) entry[0]), companion(Companion.LANTERN_CLASSIC, (CompanionModel.Pose) entry[1])));
		}
		lantern.add(new Item(Component.literal("Side"), companion(Companion.LANTERN_CLASSIC, idle, 100f)));
		for (Companion variant : Companion.variants(Companion.Style.LANTERN)) {
			lantern.add(new Item(Component.literal("At night"), companion(variant, idle.inShade(1f))));
		}
		lantern.add(new Item(Component.literal("Weathered 100%"), companion(Companion.LANTERN_COPPER, idle.worn(1f))));
		sections.add(new Section(Component.literal("Lantern spirit"), lantern));
		// The wisp: gliding, looping, yawning, ducked away, boo, happy, asleep, flickering in the Nether, side, each
		// variant in the light (faint) and in the dark (nearly solid).
		List<Item> wisp = new ArrayList<>();
		Object[][] wispPoses = {{"Gliding", walking}, {"Looping", idle.doing(CompanionModel.SHAKE, 0.35f)},
				{"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)}, {"Ducked away", idle.doing(CompanionModel.HIDE, 0.3f)},
				{"Boo", idle.doing(CompanionModel.HIDE, 0.8f)}, {"Happy", happy}, {"Asleep", dozing},
				{"Nether flicker", idle.inShade(0.6f).afraid(1f, 0)}};
		for (Object[] entry : wispPoses) {
			wisp.add(new Item(Component.literal((String) entry[0]), companion(Companion.WISP_SHADOW, ((CompanionModel.Pose) entry[1]).inShade(0.6f))));
		}
		wisp.add(new Item(Component.literal("Side"), companion(Companion.WISP_SHADOW, idle.inShade(0.6f), 100f)));
		for (Companion variant : Companion.variants(Companion.Style.WISP)) {
			wisp.add(new Item(Component.literal("In the light"), companion(variant, idle)));
			wisp.add(new Item(Component.literal("In the dark"), companion(variant, idle.inShade(1f))));
		}
		sections.add(new Section(Component.literal("Shadow wisp"), wisp));
		// The whirlwind: spinning along, wild (its player sprinting), spun up, dying down, happy, asleep, each variant.
		List<Item> wind = new ArrayList<>();
		CompanionModel.Pose wild = new CompanionModel.Pose(0.8f, 1f, 0, 0, 0, 12f, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 1f);
		Object[][] windPoses = {{"Spinning along", walking}, {"Wild (sprint)", wild}, {"Spinning up", idle.doing(CompanionModel.STAND, 0.5f)},
				{"Dying down", idle.doing(CompanionModel.YAWN, 0.5f)}, {"Happy", happy}, {"Asleep", dozing}};
		for (Object[] entry : windPoses) {
			wind.add(new Item(Component.literal((String) entry[0]), companion(Companion.WIND_BREEZE, (CompanionModel.Pose) entry[1])));
		}
		for (Companion variant : Companion.variants(Companion.Style.WIND)) {
			wind.add(new Item(variant.title(), companion(variant, idle)));
		}
		sections.add(new Section(Component.literal("Little whirlwind"), wind));
		// The ball of yarn: rolling, half and all unwound, bouncing, wobbling, happy, asleep, side, each colour.
		List<Item> yarn = new ArrayList<>();
		Object[][] yarnPoses = {{"Rolling", walking}, {"Half unwound", idle.worn(0.5f)}, {"All unwound", idle.worn(1f)},
				{"Bouncing", idle.doing(CompanionModel.STAND, 0.15f)}, {"Wobbling", idle.doing(CompanionModel.SHAKE, 0.1f)},
				{"Happy", happy}, {"Asleep", dozing}};
		for (Object[] entry : yarnPoses) {
			yarn.add(new Item(Component.literal((String) entry[0]), companion(Companion.YARN_RED, (CompanionModel.Pose) entry[1])));
		}
		yarn.add(new Item(Component.literal("Side"), companion(Companion.YARN_RED, idle, 100f)));
		for (Companion variant : Companion.variants(Companion.Style.YARN)) {
			yarn.add(new Item(variant.title(), companion(variant, idle)));
		}
		sections.add(new Section(Component.literal("Ball of yarn"), yarn));
		// The baby panda: waddling, sitting, munching bamboo, a somersault, on its back, sneezing, yawning, happy, asleep,
		// side, each variant.
		List<Item> panda = new ArrayList<>();
		Object[][] pandaPoses = {{"Waddling", walking}, {"Sitting", sitting}, {"Bamboo", idle.doing(CompanionModel.SNACK, 0.3f)},
				{"Somersault", idle.doing(CompanionModel.SHAKE, 0.4f)}, {"On its back", idle.doing(CompanionModel.BASK, 0.5f)},
				{"Sneezing", idle.doing(CompanionModel.GROOM, 0.65f)}, {"Yawning", idle.doing(CompanionModel.YAWN, 0.5f)},
				{"Happy", happy}, {"Asleep", dozing}};
		for (Object[] entry : pandaPoses) {
			panda.add(new Item(Component.literal((String) entry[0]), companion(Companion.PANDA_CLASSIC, (CompanionModel.Pose) entry[1])));
		}
		panda.add(new Item(Component.literal("Side"), companion(Companion.PANDA_CLASSIC, idle, 100f)));
		for (Companion variant : Companion.variants(Companion.Style.PANDA)) {
			panda.add(new Item(variant.title(), companion(variant, idle)));
		}
		sections.add(new Section(Component.literal("Baby panda"), panda));
		// The fliers chasing a player on elytra: wings swept back, from the side and from behind.
		List<Item> chase = new ArrayList<>();
		for (Companion flier : new Companion[]{Companion.DRAGON_GREEN, Companion.OWL_BROWN}) {
			for (float flap : new float[]{0f, 1.6f}) {
				CompanionModel.Pose dash = new CompanionModel.Pose(0, 0, 0, 0, 1f, flap, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 1f);
				chase.add(new Item(Component.literal("Chasing " + flap), companion(flier, dash)));
			}
			chase.add(new Item(Component.literal("From behind"), companion(flier,
					new CompanionModel.Pose(0, 0, 0, 0, 1f, 0.8f, 0, 0, 0, 0, 18f, CompanionModel.NONE, 0, 0, 0, 0, 1f), 200f)));
		}
		sections.add(new Section(Component.literal("Elytra chase"), chase));
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
