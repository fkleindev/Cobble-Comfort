package dev.cobblecomfort.core.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.FeatureManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Looks for a newer release on GitHub once at start and offers it with a button on the title screen. On request it
 * downloads the new jar next to this one (named so the loader ignores it) and has a small process swap the two once
 * the game has closed: the running jar can't be replaced while it is in use, and two copies of the mod must never be
 * in the mods folder at once.
 */
public final class Updater {
	private static final URI LATEST = URI.create("https://api.github.com/repos/fkleindev/Cobble-Comfort/releases/latest");
	/** Downloaded, waiting for the game to close: the loader only reads files ending in .jar. */
	private static final String PENDING = ".update";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10)).build();

	/** A newer release: its version, its page, and its jar (with the jar's SHA-256 if GitHub gives one). */
	public record Release(String version, URI page, URI jar, String jarName, String sha256) {
	}

	public enum State {
		IDLE, DOWNLOADING, READY, FAILED
	}

	private static volatile Release available;
	private static volatile State state = State.IDLE;
	/** The title screen's button, if it has one. */
	private static Button button;

	private Updater() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				addButton(screen);
			}
		});
		resumePending();
		if (FeatureManager.general().updateCheck) {
			check();
		}
	}

	public static Release available() {
		return available;
	}

	public static State state() {
		return state;
	}

	public static String installedVersion() {
		return own().map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	/** Whether the new jar can be put in place of this one: not when running from a folder, as in development. */
	public static boolean installable() {
		return available != null && available.jar() != null && ownJar() != null;
	}

	// ---------------------------------------------------------------- checking

	private static void check() {
		HttpRequest request = HttpRequest.newBuilder(LATEST).timeout(Duration.ofSeconds(15))
				.header("Accept", "application/vnd.github+json").header("User-Agent", "CobbleComfort-Updater").build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
			if (response.statusCode() != 200) {
				return;
			}
			Release release = parse(response.body());
			if (release != null) {
				Minecraft mc = Minecraft.getInstance();
				mc.execute(() -> {
					available = release;
					if (mc.gui.screen() instanceof TitleScreen title) {
						addButton(title);
					}
				});
			}
		}).exceptionally(e -> {
			CobbleComfortClient.LOGGER.info("Could not check for updates: {}", e.toString());
			return null;
		});
	}

	/** The latest release, if it is newer than this version. */
	private static Release parse(String body) {
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		if (json.has("draft") && json.get("draft").getAsBoolean() || json.has("prerelease") && json.get("prerelease").getAsBoolean()) {
			return null;
		}
		String tag = json.get("tag_name").getAsString();
		String version = tag.startsWith("v") ? tag.substring(1) : tag;
		if (!newer(version)) {
			return null;
		}
		// The mod's own jar: the release also carries the server plugin and sources.
		String jarName = "cobble-comfort-" + version + ".jar";
		URI jar = null;
		String sha256 = null;
		for (JsonElement element : json.getAsJsonArray("assets")) {
			JsonObject asset = element.getAsJsonObject();
			if (asset.get("name").getAsString().equals(jarName) && safeName(jarName)) {
				jar = URI.create(asset.get("browser_download_url").getAsString());
				JsonElement digest = asset.get("digest");
				if (digest != null && !digest.isJsonNull() && digest.getAsString().startsWith("sha256:")) {
					sha256 = digest.getAsString().substring("sha256:".length()).toLowerCase(Locale.ROOT);
				}
			}
		}
		return new Release(version, URI.create(json.get("html_url").getAsString()), jar, jar == null ? null : jarName, sha256);
	}

	private static boolean newer(String version) {
		try {
			Version latest = SemanticVersion.parse(version);
			return own().map(mod -> latest.compareTo(mod.getMetadata().getVersion()) > 0).orElse(false);
		} catch (VersionParsingException e) {
			return false;
		}
	}

	/** A plain file name, so a release can't place a file anywhere but next to this jar. */
	private static boolean safeName(String name) {
		return name.matches("[A-Za-z0-9._+-]+") && !name.startsWith(".");
	}

	// ---------------------------------------------------------------- the title screen

	private static void addButton(Screen screen) {
		Release release = available;
		if (release == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Component label = Component.translatable(state == State.READY ? "cobblecomfort.update.button_ready" : "cobblecomfort.update.button",
				release.version());
		int width = mc.font.width(label) + 16;
		// Only ever once: the check may finish while the title screen is already up.
		Screens.getWidgets(screen).remove(button);
		button = Button.builder(label, b -> mc.gui.setScreen(new UpdateScreen(screen))).bounds(4, 4, width, 20)
				.tooltip(Tooltip.create(Component.translatable("cobblecomfort.update.button_tooltip"))).build();
		Screens.getWidgets(screen).add(button);
	}

	// ---------------------------------------------------------------- installing

	/** Downloads the new jar next to this one and has it swapped in once the game has closed. */
	public static void install() {
		Release release = available;
		Path own = ownJar();
		if (release == null || release.jar() == null || own == null || state == State.DOWNLOADING || state == State.READY) {
			return;
		}
		state = State.DOWNLOADING;
		Path pending = own.resolveSibling(release.jarName() + PENDING);
		HttpRequest request = HttpRequest.newBuilder(release.jar()).timeout(Duration.ofMinutes(2))
				.header("User-Agent", "CobbleComfort-Updater").build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofFile(pending)).thenAccept(response -> {
			try {
				if (response.statusCode() != 200) {
					throw new IOException("HTTP " + response.statusCode());
				}
				if (release.sha256() != null && !release.sha256().equals(sha256(pending))) {
					throw new IOException("checksum mismatch");
				}
				swapOnExit(own, pending, own.resolveSibling(release.jarName()));
				state = State.READY;
			} catch (IOException e) {
				fail(pending, e);
			}
		}).exceptionally(e -> {
			fail(pending, e);
			return null;
		});
	}

	private static void fail(Path pending, Throwable e) {
		CobbleComfortClient.LOGGER.warn("Update failed", e);
		try {
			Files.deleteIfExists(pending);
		} catch (IOException ignored) {
		}
		state = State.FAILED;
	}

	/** A download left from a session whose swap never ran (the game killed with it): swap it in this time. */
	private static void resumePending() {
		Path own = ownJar();
		if (own == null) {
			return;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(own.getParent(), "*.jar" + PENDING)) {
			for (Path pending : files) {
				String name = pending.getFileName().toString();
				Path target = own.resolveSibling(name.substring(0, name.length() - PENDING.length()));
				String version = target.getFileName().toString().replaceFirst("^cobble-comfort-(.*)\\.jar$", "$1");
				if (!newer(version)) {
					// Older than what runs now (installed by hand meanwhile): never go back.
					Files.deleteIfExists(pending);
				} else if (!target.equals(own)) {
					swapOnExit(own, pending, target);
					state = State.READY;
					return;
				}
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.warn("Could not look for a downloaded update", e);
		}
	}

	/**
	 * Starts a small process that waits for the game to close, removes the old jar and only then puts the new one in its
	 * place, so the two are never both loaded.
	 */
	private static void swapOnExit(Path old, Path pending, Path target) throws IOException {
		long pid = ProcessHandle.current().pid();
		ProcessBuilder builder;
		if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
			String script = "$p=" + pid + "; while (Get-Process -Id $p -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 500 }; "
					+ "for ($i = 0; $i -lt 40; $i++) { try { Remove-Item -LiteralPath " + quote(old) + " -ErrorAction Stop; break } "
					+ "catch { Start-Sleep -Milliseconds 500 } }; "
					+ "if (-not (Test-Path -LiteralPath " + quote(old) + ")) { Move-Item -LiteralPath " + quote(pending)
					+ " -Destination " + quote(target) + " -Force }";
			builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script);
		} else {
			builder = new ProcessBuilder("sh", "-c", "while kill -0 " + pid + " 2>/dev/null; do sleep 0.5; done; "
					+ "rm -f -- \"$1\" && mv -f -- \"$2\" \"$3\"", "sh", old.toString(), pending.toString(), target.toString());
		}
		builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
	}

	/** A path in single quotes for PowerShell, where a quote inside is written twice. */
	private static String quote(Path path) {
		return "'" + path.toString().replace("'", "''") + "'";
	}

	private static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[64 * 1024];
			for (int n; (n = in.read(buffer)) > 0; ) {
				digest.update(buffer, 0, n);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static Optional<ModContainer> own() {
		return FabricLoader.getInstance().getModContainer(CobbleComfortClient.MOD_ID);
	}

	/** The jar this mod was loaded from, or null when it wasn't loaded from a jar. */
	private static Path ownJar() {
		return own().flatMap(mod -> mod.getOrigin().getKind() == ModOrigin.Kind.PATH
						? mod.getOrigin().getPaths().stream().filter(path -> Files.isRegularFile(path)
						&& path.getFileName().toString().endsWith(".jar")).findFirst() : Optional.empty())
				.orElse(null);
	}
}
