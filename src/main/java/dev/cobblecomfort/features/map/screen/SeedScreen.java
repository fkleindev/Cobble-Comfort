package dev.cobblecomfort.features.map.screen;

import dev.cobblecomfort.features.map.MapFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Enter the seed of a server by hand (it is picked up automatically from /seed if you may run it). */
public class SeedScreen extends Screen {
	private static final int PANEL_WIDTH = 260;
	private static final int PANEL_HEIGHT = 110;

	private final Screen parent;
	private EditBox seed;
	private int left, top;

	public SeedScreen(Screen parent) {
		super(Component.translatable("cobblecomfort.map.seed.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = (height - PANEL_HEIGHT) / 2;
		seed = new EditBox(font, left + 8, top + 44, PANEL_WIDTH - 16, 16, Component.empty());
		seed.setMaxLength(32);
		Long known = MapFeature.INSTANCE.knownSeed();
		seed.setValue(known == null ? "" : String.valueOf(known));
		addRenderableWidget(seed);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(left + 8, top + PANEL_HEIGHT - 26, 120, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(left + PANEL_WIDTH - 128, top + PANEL_HEIGHT - 26, 120, 20).build());
		setInitialFocus(seed);
	}

	private void save() {
		String text = seed.getValue().trim();
		if (!text.isEmpty()) {
			long value;
			try {
				value = Long.parseLong(text);
			} catch (NumberFormatException e) {
				// Like vanilla: a text seed becomes its hash code.
				value = text.hashCode();
			}
			MapFeature.INSTANCE.setServerSeed(value);
		}
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101018);
		g.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A3A50);
		g.text(font, title, left + 8, top + 7, 0xFFFFFFFF, true);
		g.text(font, Component.translatable("cobblecomfort.map.seed.hint").withStyle(ChatFormatting.GRAY), left + 8, top + 24, 0xFFFFFFFF, false);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
