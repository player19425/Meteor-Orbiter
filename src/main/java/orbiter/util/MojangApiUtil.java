package orbiter.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

public final class MojangApiUtil {

    private MojangApiUtil() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final String MOJANG_API_URL =
        "https://api.mojang.com/users/profiles/minecraft/%s";

    private static final ConcurrentHashMap<String, String> NAME_TO_UUID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> UUID_TO_NAME = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> NEGATIVE_CACHE = new ConcurrentHashMap<>();
    private static final long NEGATIVE_CACHE_TTL_MS = Duration.ofMinutes(5).toMillis();
    private static final int MAX_NEGATIVE_CACHE_ENTRIES = 256;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    public static String resolveLocal(String username) {
        if (username == null || username.isBlank()) return null;
        String key = username.toLowerCase(Locale.ROOT);

        String cached = NAME_TO_UUID.get(key);
        if (cached != null) return cached;

        ClientPacketListener handler = Minecraft.getInstance().getConnection();
        if (handler == null) return null;

        try {
            for (PlayerInfo entry : new ArrayList<>(handler.getOnlinePlayers())) {
                GameProfile profile = entry.getProfile();
                if (profile == null || profile.name() == null) continue;
                if (profile.name().equalsIgnoreCase(username) && profile.id() != null) {
                    String uuid = profile.id().toString();
                    cache(username, uuid);
                    return uuid;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static CompletableFuture<String> resolveAsync(String username) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            String local = resolveLocal(username);
            if (local != null) {
                future.complete(local);
                return;
            }
            CompletableFuture.supplyAsync(() -> resolveRemote(username))
                .whenComplete((result, err) -> future.complete(err == null ? result : null));
        });
        return future;
    }

    private static String resolveRemote(String username) {
        if (!isValidUsername(username)) return null;
        String key = username.toLowerCase(Locale.ROOT);
        purgeNegativeCache();
        Long failedAt = NEGATIVE_CACHE.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < NEGATIVE_CACHE_TTL_MS) return null;

        String apiResult = queryMojangApi(username);
        if (apiResult != null) {
            cache(username, apiResult);
            return apiResult;
        }

        NEGATIVE_CACHE.put(key, System.currentTimeMillis());
        return null;
    }

    private static void purgeNegativeCache() {
        if (NEGATIVE_CACHE.size() <= MAX_NEGATIVE_CACHE_ENTRIES) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = NEGATIVE_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= NEGATIVE_CACHE_TTL_MS) it.remove();
        }
        if (NEGATIVE_CACHE.size() > MAX_NEGATIVE_CACHE_ENTRIES) NEGATIVE_CACHE.clear();
    }

    private static String queryMojangApi(String username) {
        try {
            String url = String.format(Locale.ROOT, MOJANG_API_URL,
                java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            String body = response.body();
            if (body == null || body.isBlank()) return null;
            if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) return null;

            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (!obj.has("id")) return null;

            String id = obj.get("id").getAsString();
            return toDashedUuid(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isValidUsername(String username) {
        return username != null && username.length() >= 3 && username.length() <= 16
            && username.matches("[A-Za-z0-9_]+");
    }

    public static String toDashedUuid(String undashed) {
        if (undashed == null) return null;
        String clean = undashed.replace("-", "").trim();
        if (clean.length() != 32) return null;
        return clean.substring(0, 8) + "-" +
               clean.substring(8, 12) + "-" +
               clean.substring(12, 16) + "-" +
               clean.substring(16, 20) + "-" +
               clean.substring(20, 32);
    }

    public static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String trimmed = value.trim();
            if (trimmed.replace("-", "").length() == 32 && !trimmed.contains("-")) {
                return UUID.fromString(toDashedUuid(trimmed));
            }
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void cache(String username, String uuid) {
        if (username == null || uuid == null) return;
        String key = username.toLowerCase(Locale.ROOT);
        NAME_TO_UUID.put(key, uuid);
        UUID_TO_NAME.put(uuid.replace("-", "").toLowerCase(Locale.ROOT), username);
        NEGATIVE_CACHE.remove(key);
    }
}
