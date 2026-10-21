package orbiter.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import meteordevelopment.orbit.EventHandler;
import orbiter.compat.ClientCompat;
import orbiter.compat.EntityCompat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final String RELEASES_URL =
        "https://api.github.com/repos/player19425/Meteor-Orbiter/releases?per_page=30";
    private static final String MOD_ID = "meteor-orbiter";
    private static final long MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;

    private static final AtomicBoolean checkedThisSession = new AtomicBoolean();
    private static final AtomicBoolean installing = new AtomicBoolean();

    private UpdateChecker() {}

    private static final class FetchResult {
        final String[] release;
        final String error;

        private FetchResult(String[] release, String error) {
            this.release = release;
            this.error = error;
        }

        static FetchResult found(String[] release) {
            return new FetchResult(release, null);
        }

        static FetchResult failed(String error) {
            return new FetchResult(null, error);
        }
    }

    public static void init() {
        sweepLeftovers();
        MeteorClient.EVENT_BUS.subscribe(new Object() {
            @EventHandler
            private void onGameJoined(GameJoinedEvent event) {
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> checkNow(false));
            }
        });
    }

    public static void checkNow(boolean notifyWhenUpToDate) {
        if (!ConfigModifier.get().updateCheckerEnabled()) return;
        if (!notifyWhenUpToDate && !checkedThisSession.compareAndSet(false, true)) return;
        if (notifyWhenUpToDate) checkedThisSession.set(true);

        CompletableFuture.supplyAsync(() -> fetchLatest())
            .thenAccept(result -> {
                String[] release = result == null ? null : result.release;
                if (release == null) {
                    if (notifyWhenUpToDate) {
                        error(result == null ? "Could not reach GitHub to check for updates." : result.error);
                    }
                    return;
                }
                if (!isNewer(release[0])) {
                    if (notifyWhenUpToDate) chat("You are on the latest version.");
                    return;
                }
                String ignored = ConfigModifier.get().ignoredVersion();
                if (release[0].equalsIgnoreCase(ignored)) return;

                chat("There is a new update available (%s).", release[0]);
                if (ConfigModifier.get().updateAutoEnabled()) {
                    install(release[0], release[2]);
                } else {
                    Minecraft.getInstance().execute(() ->
                        ClientCompat.setScreen(new orbiter.gui.UpdateAvailableScreen(
                            GuiThemes.get(), release[0], release[1], release[2])));
                }
            });
    }

    public static void install(String tag, String downloadUrl) {
        if (!installing.compareAndSet(false, true)) return;
        if (downloadUrl == null || downloadUrl.isBlank()) {
            installing.set(false);
            error("Update payload missing for " + tag + ".");
            return;
        }
        chat("Downloading update %s...", tag);

        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("User-Agent", MOD_ID)
                    .GET()
                    .build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
                byte[] bytes = response.body();
                if (bytes == null || bytes.length < 1024 || bytes.length > MAX_JAR_BYTES) {
                    throw new IllegalStateException("Downloaded file has an unexpected size.");
                }

                Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                String fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
                if (!fileName.endsWith(".jar")) {
                    String base = sanitizeTag(tag);
                    String mc = currentMinecraftVersion();
                    fileName = !mc.isEmpty() && base.endsWith("-" + mc)
                        ? "meteor-orbiter-" + base + ".jar"
                        : "meteor-orbiter-" + base + (mc.isEmpty() ? "" : "-" + mc) + ".jar";
                }

                Path target = mods.resolve(fileName);
                Path staging = mods.resolve(fileName + ".new");
                Files.write(staging, bytes);
                try {
                    Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception moveFailure) {
                    throw new IllegalStateException("Could not place the new jar (is the old one locked?): " + moveFailure.getMessage(), moveFailure);
                }

                removeOldJars(mods, fileName);
                return fileName;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((fileName, err) -> {
            installing.set(false);
            if (err != null) {
                error("Auto-update failed: " + rootMessage(err));
                return;
            }
            Minecraft.getInstance().execute(() ->
                chat("Updated to %s. Restart Minecraft to load it.", tag));
        });
    }

    public static void sendChangelogLink(String pageUrl) {
        Minecraft.getInstance().execute(() -> {
            try {
                EntityCompat.openUri(URI.create(pageUrl));
            } catch (Throwable browserFailure) {
                sendClickableLink(pageUrl);
            }
        });
    }

    private static void sendClickableLink(String pageUrl) {
        try {
            Component link = Component.literal("[Open changelog]")
                .setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(pageUrl)))
                    .withUnderlined(true));
            ChatUtils.sendMsg(Component.literal("[Orbiter] ").append(link));
        } catch (Exception e) {
            error("Changelog: " + pageUrl);
        }
    }

    private static FetchResult fetchLatest() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", MOD_ID)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) return FetchResult.failed("Could not reach GitHub to check for updates.");
            byte[] body = response.body();
            if (body == null || body.length > MAX_RESPONSE_BYTES) return FetchResult.failed("GitHub sent an unreadable answer.");

            JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!parsed.isJsonArray()) return FetchResult.failed("GitHub sent an unreadable answer.");
            JsonArray releases = parsed.getAsJsonArray();

            String mc = currentMinecraftVersion();
            if (mc.isEmpty()) return FetchResult.failed("Could not work out which Minecraft version this build is for.");
            Pattern asset = assetPattern(mc);

            for (int i = 0; i < releases.size(); i++) {
                if (!releases.get(i).isJsonObject()) continue;
                JsonObject release = releases.get(i).getAsJsonObject();
                if (release.has("draft") && release.get("draft").getAsBoolean()) continue;

                String tag = release.has("tag_name") ? release.get("tag_name").getAsString() : "";
                if (tag.isBlank()) continue;

                String downloadUrl = assetUrlFor(release, asset);
                if (downloadUrl.isBlank()) continue;

                String pageUrl = release.has("html_url") ? release.get("html_url").getAsString() : "";
                return FetchResult.found(new String[]{tag, pageUrl, downloadUrl});
            }
            return FetchResult.failed("No Orbiter release for Minecraft " + mc + " yet.");
        } catch (Exception e) {
            return FetchResult.failed("Could not reach GitHub to check for updates.");
        }
    }

    private static String assetUrlFor(JsonObject release, Pattern asset) {
        if (!release.has("assets") || !release.get("assets").isJsonArray()) return "";
        JsonArray assets = release.getAsJsonArray("assets");

        for (int i = 0; i < assets.size(); i++) {
            if (!assets.get(i).isJsonObject()) continue;
            JsonObject entry = assets.get(i).getAsJsonObject();

            String name = entry.has("name") ? entry.get("name").getAsString() : "";
            if (!name.endsWith(".jar") || name.contains("sources")) continue;
            if (!asset.matcher(name).matches()) continue;

            return entry.has("browser_download_url") ? entry.get("browser_download_url").getAsString() : "";
        }
        return "";
    }

    private static Pattern assetPattern(String mc) {
        return Pattern.compile("meteor-orbiter-.*-" + Pattern.quote(mc) + "(-[A-Za-z0-9]+)?\\.jar");
    }

    private static String currentMinecraftVersion() {
        String version = currentVersionString();
        int dash = version.lastIndexOf('-');
        if (dash <= 0 || dash == version.length() - 1) return "";
        return version.substring(dash + 1);
    }

    private static String currentVersionString() {
        return FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");
    }

    private static boolean isNewer(String tag) {
        String current = currentVersionString();

        String left = stripNumeric(tag);
        String right = stripNumeric(current);
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = partAt(a, i);
            int y = partAt(b, i);
            if (x != y) return x > y;
        }
        return false;
    }

    private static String stripNumeric(String version) {
        String s = version == null ? "" : version.trim();
        while (!s.isEmpty() && !Character.isDigit(s.charAt(0))) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash);
        return s;
    }

    private static int partAt(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void removeOldJars(Path mods, String keepName) {
        try (var stream = Files.list(mods)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith("meteor-orbiter") && n.endsWith(".jar") && !n.equals(keepName);
                })
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception deleteFailure) {
                        try {
                            Files.move(p, p.resolveSibling(p.getFileName().toString() + ".old"));
                        } catch (Exception ignored) {
                        }
                    }
                });
        } catch (Exception ignored) {
        }
    }

    private static void sweepLeftovers() {
        try {
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
            if (!Files.isDirectory(mods)) return;

            Path activeJar = activeModJar();
            try (var stream = Files.list(mods)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        if (!n.startsWith("meteor-orbiter")) return false;
                        if (activeJar != null && n.equals(activeJar.getFileName().toString())) return false;
                        return n.endsWith(".jar") || n.endsWith(".old") || n.endsWith(".new");
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception ignored) {
                        }
                    });
            }
        } catch (Exception ignored) {
        }
    }

    private static Path activeModJar() {
        try {
            return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .flatMap(container -> container.getRootPaths().stream().findFirst())
                .filter(path -> path.toString().endsWith(".jar"))
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitizeTag(String tag) {
        String s = tag == null ? "" : tag.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s.replace("/", "_");
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static void chat(String message, Object... args) {
        Minecraft.getInstance().execute(() -> ChatUtils.infoPrefix("Orbiter", message, args));
    }

    private static void error(String message) {
        Minecraft.getInstance().execute(() -> ChatUtils.errorPrefix("Orbiter", message));
    }
}
