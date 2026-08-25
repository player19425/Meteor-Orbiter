package orbiter.util;

import com.google.gson.JsonArray;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class UpdateChecker {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final String LATEST_URL =
        "https://api.github.com/repos/player19425/Meteor-Orbiter/releases/latest";
    private static final String MOD_ID = "meteor-orbiter";
    private static final long MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;

    private static volatile boolean checkedThisSession;
    private static volatile boolean installing;

    private UpdateChecker() {}

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
        if (checkedThisSession && !notifyWhenUpToDate) return;
        checkedThisSession = true;

        CompletableFuture.supplyAsync(() -> fetchLatest())
            .thenAccept(release -> {
                if (release == null) {
                    if (notifyWhenUpToDate) error("Could not reach GitHub to check for updates.");
                    return;
                }
                if (!isNewer(release[0])) {
                    if (notifyWhenUpToDate) ChatUtils.infoPrefix("Orbiter", "You are on the latest version.");
                    return;
                }
                String ignored = ConfigModifier.get().ignoredVersion();
                if (release[0].equalsIgnoreCase(ignored)) return;

                ChatUtils.infoPrefix("Orbiter", "There is a new update available (%s).", release[0]);
                if (ConfigModifier.get().updateAutoEnabled()) {
                    install(release[0], release[2]);
                } else {
                    Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().gui.setScreen(new orbiter.gui.UpdateAvailableScreen(
                            GuiThemes.get(), release[0], release[1], release[2])));
                }
            });
    }

    public static void install(String tag, String downloadUrl) {
        if (installing) return;
        if (downloadUrl == null || downloadUrl.isBlank()) {
            error("Update payload missing for " + tag + ".");
            return;
        }
        installing = true;
        ChatUtils.infoPrefix("Orbiter", "Downloading update %s...", tag);

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
                if (!fileName.endsWith(".jar")) fileName = "meteor-orbiter-" + sanitizeTag(tag) + "-26.2.jar";

                Path target = mods.resolve(fileName);
                Path staging = mods.resolve(fileName + ".new");
                Files.write(staging, bytes);

                removeOldJars(mods, fileName);
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
                return fileName;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((fileName, err) -> {
            installing = false;
            if (err != null) {
                error("Auto-update failed: " + rootMessage(err));
                return;
            }
            Minecraft.getInstance().execute(() ->
                ChatUtils.infoPrefix("Orbiter", "Updated to %s. Restart Minecraft to load it.", tag));
        });
    }

    public static void sendChangelogLink(String pageUrl) {
        Minecraft.getInstance().execute(() -> {
            try {
                net.minecraft.util.Util.getPlatform().openUri(URI.create(pageUrl));
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

    private static String[] fetchLatest() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_URL))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", MOD_ID)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) return null;
            byte[] body = response.body();
            if (body == null || body.length > MAX_RESPONSE_BYTES) return null;

            JsonObject json = JsonParser.parseString(new String(body, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
            String pageUrl = json.has("html_url") ? json.get("html_url").getAsString() : "";
            String downloadUrl = "";
            if (json.has("assets") && json.get("assets").isJsonArray()) {
                JsonArray assets = json.getAsJsonArray("assets");
                for (int i = 0; i < assets.size(); i++) {
                    JsonObject asset = assets.get(i).getAsJsonObject();
                    String name = asset.has("name") ? asset.get("name").getAsString() : "";
                    String url = asset.has("browser_download_url") ? asset.get("browser_download_url").getAsString() : "";
                    if (name.endsWith(".jar") && !name.contains("sources")) {
                        downloadUrl = url;
                        break;
                    }
                }
            }
            if (tag.isBlank() || downloadUrl.isBlank()) return null;
            return new String[]{tag, pageUrl, downloadUrl};
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNewer(String tag) {
        String current = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");

        String left = strip(tag);
        String right = strip(current);
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

    private static String strip(String version) {
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

            String current = currentJarName();
            try (var stream = Files.list(mods)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        if (!n.startsWith("meteor-orbiter")) return false;
                        if (n.equals(current)) return false;
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

    private static String currentJarName() {
        String friendly = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");
        return friendly.isBlank() ? "" : MOD_ID + "-" + friendly + ".jar";
    }

    private static String sanitizeTag(String tag) {
        return tag.replace("v", "").replace("V", "").replace("/", "_");
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static void error(String message) {
        Minecraft.getInstance().execute(() -> ChatUtils.errorPrefix("Orbiter", message));
    }
}
