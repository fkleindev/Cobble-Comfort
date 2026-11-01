package dev.cobblecomfort.sheets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.cobblecomfort.features.wardrobe.CosmeticSheetScreen;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Draws every cosmetic of the wardrobe on the figure, one image per category, into the folder given by the
 * {@code cobblecomfort.sheets.out} property. Run with {@code ./gradlew cosmeticSheets}.
 */
public class CosmeticSheetTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Cobble Comfort Cosmetic Sheets");
	/** Whose skin the figure wears. */
	private static final String SKIN_OF = "FLRN_tv";
	private static final int WIDTH = 2560;
	private static final int HEIGHT = 1440;
	private static final int GUI_SCALE = 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		Path out = Path.of(System.getProperty("cobblecomfort.sheets.out", "cosmetics")).toAbsolutePath();
		byte[] skin = downloadSkin();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty peaceful");
			if (skin != null) {
				context.runOnClient(mc -> {
					NativeImage image = NativeImage.read(skin);
					Identifier id = Identifier.fromNamespaceAndPath("cobblecomfort", "sheets/skin");
					mc.getTextureManager().register(id, new DynamicTexture(() -> "cosmetic sheet skin", image));
					ClientAsset.Texture texture = new ClientAsset.ResourceTexture(id, id);
					SheetSkin.skin = PlayerSkin.insecure(texture, null, null, SheetSkin.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
				});
			}
			// A large frame at a large GUI scale, so the figures come out sharp.
			// No "new hat" toasts over the pages.
			WardrobeFeature.INSTANCE.config().announce = false;
			// Something to wear, for the Mini-Me totem (free pieces, so they count in this world).
			WardrobeFeature.INSTANCE.config().hat = "crown_paper";
			WardrobeFeature.INSTANCE.config().neck = "bowtie_red";
			context.waitTicks(2);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.runOnClient(mc -> {
				mc.getWindow().setWidth(WIDTH);
				mc.getWindow().setHeight(HEIGHT);
				mc.options.guiScale().set(GUI_SCALE);
				mc.resizeGui();
			});
			Path pages = out.resolve(".pages");
			Files.createDirectories(pages);
			for (CosmeticSheetScreen.Category category : CosmeticSheetScreen.Category.values()) {
				List<Path> shots = new ArrayList<>();
				List<Integer> heights = new ArrayList<>();
				int count = 1;
				for (int page = 0; page < count; page++) {
					int p = page;
					context.setScreen(() -> new CosmeticSheetScreen(category, p));
					context.waitTicks(3);
					count = context.computeOnClient(mc -> ((CosmeticSheetScreen) mc.gui.screen()).pageCount());
					heights.add(context.computeOnClient(mc -> ((CosmeticSheetScreen) mc.gui.screen()).usedHeight()
							* mc.getWindow().getGuiScale()));
					shots.add(context.takeScreenshot(TestScreenshotOptions.of(category.file + "_" + page)
							.withSize(WIDTH, HEIGHT).withDestinationDir(pages).disableCounterPrefix()));
				}
				stack(shots, heights, out.resolve(category.file + ".png"));
			}
			context.setScreen(() -> null);
			try (var shots = Files.newDirectoryStream(pages)) {
				for (Path page : shots) {
					Files.delete(page);
				}
			}
			Files.delete(pages);
		} catch (IOException e) {
			throw new RuntimeException("Could not write the cosmetic sheets to " + out, e);
		}
		SheetSkin.skin = null;
	}

	/** Puts the pages one under the other, each cut off below what is drawn on it ({@code heights}, in pixels). */
	private static void stack(List<Path> shots, List<Integer> heights, Path file) throws IOException {
		List<BufferedImage> images = new ArrayList<>();
		for (int i = 0; i < shots.size(); i++) {
			BufferedImage image = ImageIO.read(shots.get(i).toFile());
			images.add(image.getSubimage(0, 0, image.getWidth(), Math.min(image.getHeight(), heights.get(i))));
		}
		int width = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
		int height = images.stream().mapToInt(BufferedImage::getHeight).sum();
		BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int y = 0;
		for (BufferedImage image : images) {
			sheet.getGraphics().drawImage(image, 0, y, null);
			y += image.getHeight();
		}
		ImageIO.write(sheet, "png", file.toFile());
		LOGGER.info("Cosmetic sheet {} ({} pages)", file, images.size());
	}

	/** The skin's PNG from Mojang, or null (the default skin is used then). */
	private static byte[] downloadSkin() {
		try (HttpClient http = HttpClient.newHttpClient()) {
			JsonObject profile = json(http, "https://api.mojang.com/users/profiles/minecraft/" + SKIN_OF);
			JsonObject session = json(http, "https://sessionserver.mojang.com/session/minecraft/profile/" + profile.get("id").getAsString());
			String value = session.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
			JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
			SheetSkin.slim = textures.has("metadata") && "slim".equals(textures.getAsJsonObject("metadata").get("model").getAsString());
			HttpResponse<InputStream> png = http.send(HttpRequest.newBuilder(URI.create(textures.get("url").getAsString())).build(),
					HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = png.body()) {
				return in.readAllBytes();
			}
		} catch (Exception e) {
			LOGGER.warn("Could not load the skin of {}, using the default one", SKIN_OF, e);
			return null;
		}
	}

	private static JsonObject json(HttpClient http, String url) throws IOException, InterruptedException {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException(url + " answered " + response.statusCode());
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}
}
