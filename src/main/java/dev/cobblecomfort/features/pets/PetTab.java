package dev.cobblecomfort.features.pets;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.MapFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The "Pets" tab of the storage memory screen: remembered pets whose name or kind match the search, living ones in this
 * dimension first and nearest first, graves last. Each row has a button to forget the pet.
 */
public final class PetTab {
	private static final int ROW_HOVER = 0x22FFFFFF;
	private static final int FORGET = 12;

	/** What the mouse is over: a row, and whether it is the forget button. */
	public record Hovered(PetEntry pet, boolean forget) {
	}

	private int builtVersion = -1;
	private String builtQuery;
	private List<PetEntry> results = List.of();

	public List<PetEntry> results() {
		return results;
	}

	public void rebuild(String query) {
		PetStore store = PetFeature.INSTANCE.store();
		if (store.version() == builtVersion && query.equals(builtQuery)) {
			return;
		}
		builtVersion = store.version();
		builtQuery = query;
		String lower = query.trim().toLowerCase(Locale.ROOT);
		List<PetEntry> list = new ArrayList<>();
		for (PetEntry pet : store.all()) {
			if (lower.isEmpty() || pet.name.toLowerCase(Locale.ROOT).contains(lower)
					|| PetFeature.typeName(pet).getString().toLowerCase(Locale.ROOT).contains(lower)) {
				list.add(pet);
			}
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
		String dim = mc.level == null ? "" : MapFeature.dimensionId(mc.level);
		list.sort(Comparator.comparingInt((PetEntry p) -> p.dead() ? 2 : p.dimension.equals(dim) ? 0 : 1)
				.thenComparingDouble(p -> p.dimension.equals(dim) ? p.distanceTo(eye) : 0));
		results = list;
	}

	public Component emptyText() {
		return PetFeature.INSTANCE.store().all().isEmpty()
				? Component.translatable("cobblecomfort.pets.nothing_yet")
				: Component.translatable("cobblecomfort.storage.no_results");
	}

	/** Whether a click at {@code mouseX} in a row hits its forget button. */
	public static boolean overForget(int x1, double mouseX) {
		return mouseX >= x1 - FORGET && mouseX < x1;
	}

	/** Draws the rows from {@code y} on; returns what the mouse is over. */
	public Hovered draw(GuiGraphicsExtractor g, Font font, int x0, int x1, int y, int bodyTop, int bodyBottom, int row,
			int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
		String dim = mc.level == null ? "" : MapFeature.dimensionId(mc.level);
		PetFeature feature = PetFeature.INSTANCE;
		Hovered hovered = null;
		for (PetEntry pet : results) {
			if (y + row >= bodyTop && y <= bodyBottom) {
				boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + row - 2;
				if (hover) {
					RenderUtil.roundedFill(g, x0 - 2, y - 1, x1 + 2, y + row - 1, 3, ROW_HOVER);
				}
				g.item(feature.icon(pet), x0, y + 2);
				int textX = x0 + 20;
				Component title = PetFeature.title(pet);
				g.text(font, title, textX, y + 1, 0xFFFFFFFF, true);
				g.text(font, PetFeature.status(pet), textX + font.width(title) + 6, y + 1, 0xFFFFFFFF, true);
				MutableComponent location = Component.literal((int) Math.floor(pet.x) + ", " + (int) Math.floor(pet.y) + ", " + (int) Math.floor(pet.z) + " · ")
						.append(PetFeature.where(pet, eye, dim));
				if (!pet.dead()) {
					location.append(" · ").append(feature.seen(pet));
				}
				g.text(font, location, textX, y + 12, 0xFF9AA0AE, false);
				boolean forget = hover && overForget(x1, mouseX);
				if (hover) {
					g.centeredText(font, "✕", x1 - FORGET / 2, y + 6, forget ? 0xFFFF6060 : 0xFF9AA0AE);
					hovered = new Hovered(pet, forget);
				}
			}
			y += row;
		}
		return hovered;
	}

	/** The map tooltip for a row, or a hint for the forget button. */
	public static List<Component> tooltip(Hovered hovered) {
		if (hovered.forget()) {
			return List.of(Component.translatable(hovered.pet().dead() ? "cobblecomfort.pets.remove_grave" : "cobblecomfort.pets.forget")
					.withStyle(ChatFormatting.RED));
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
		String dim = mc.level == null ? "" : MapFeature.dimensionId(mc.level);
		return PetFeature.INSTANCE.tooltip(hovered.pet(), eye, dim);
	}
}
