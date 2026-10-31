package dev.cobblecomfort.core.update;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Offers a newer release: install it on the next start, or open its page to read what is new. */
public class UpdateScreen extends Screen {
	private static final int PANEL_WIDTH = 300;
	private static final int PANEL_HEIGHT = 128;

	private final Screen parent;
	private Button install;
	private int left, top;

	public UpdateScreen(Screen parent) {
		super(Component.translatable("cobblecomfort.update.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = (height - PANEL_HEIGHT) / 2;
		Updater.Release release = Updater.available();
		install = addRenderableWidget(Button.builder(Component.empty(), b -> Updater.install())
				.bounds(left + 8, top + PANEL_HEIGHT - 52, PANEL_WIDTH - 16, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.update.page"),
						b -> ConfirmLinkScreen.confirmLinkNow(this, release.page()))
				.bounds(left + 8, top + PANEL_HEIGHT - 28, (PANEL_WIDTH - 20) / 2, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("cobblecomfort.update.later"), b -> onClose())
				.bounds(left + PANEL_WIDTH / 2 + 2, top + PANEL_HEIGHT - 28, (PANEL_WIDTH - 20) / 2, 20).build());
		updateInstall();
	}

	/** The install button as things stand: ready to go, busy, done, failed, or not possible here. */
	private void updateInstall() {
		Updater.State state = Updater.state();
		install.active = Updater.installable() && (state == Updater.State.IDLE || state == Updater.State.FAILED);
		install.setMessage(Component.translatable(switch (state) {
			case IDLE -> Updater.installable() ? "cobblecomfort.update.install" : "cobblecomfort.update.install_unavailable";
			case DOWNLOADING -> "cobblecomfort.update.downloading";
			case READY -> "cobblecomfort.update.ready";
			case FAILED -> "cobblecomfort.update.failed";
		}));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		updateInstall();
		Updater.Release release = Updater.available();
		g.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101018);
		g.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A3A50);
		g.text(font, Component.translatable("cobblecomfort.update.available", release.version()), left + 8, top + 8, 0xFFFFFFFF, true);
		g.text(font, Component.translatable("cobblecomfort.update.installed", Updater.installedVersion()).withStyle(ChatFormatting.GRAY),
				left + 8, top + 22, 0xFFFFFFFF, false);
		int y = top + 38;
		for (var line : font.split(Component.translatable("cobblecomfort.update.hint").withStyle(ChatFormatting.GRAY), PANEL_WIDTH - 16)) {
			g.text(font, line, left + 8, y, 0xFFFFFFFF, false);
			y += 10;
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
