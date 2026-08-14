package orbiter.modules.misc;

import meteordevelopment.meteorclient.systems.modules.Modules;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RawPacketCapture {

    private static final Pattern CHANNEL_TOKEN = Pattern.compile(
        "(?i)\\b[a-z0-9_.-]{2,64}:[a-z0-9_./-]{1,128}\\b"
    );

    private static final Pattern REGISTRATION_SPLIT = Pattern.compile(
        "[\\u0000\\r\\n\\t ,;]+"
    );

    private static final int MAX_PENDING_BYTES = 4 * 1024 * 1024;

    private static final ConcurrentLinkedQueue<byte[]> PENDING_BYTES = new ConcurrentLinkedQueue<>();

    private static volatile int pendingByteCount = 0;

    private static int totalBytesQueued;
    private static int totalChannelsFound;
    private static int totalRegistrationChannels;
    private static int totalEmbeddedChannels;

    private static boolean shouldCapture() {
        try {
            PeakPluginScanner scanner = Modules.get() == null ? null : Modules.get().get(PeakPluginScanner.class);
            return scanner != null && scanner.isActive() && scanner.shouldCaptureChannels();
        } catch (Throwable ignored) {

            return false;
        }
    }

    public static void enqueue(byte[] raw) {
        if (raw == null || raw.length == 0) return;

        if (!shouldCapture()) return;

        int incoming = raw.length;

        if (pendingByteCount + incoming > MAX_PENDING_BYTES) {
            byte[] eldest;
            while (pendingByteCount + incoming > MAX_PENDING_BYTES
                   && (eldest = PENDING_BYTES.poll()) != null) {
                pendingByteCount -= eldest.length;
            }

            if (incoming > MAX_PENDING_BYTES) return;
        }

        PENDING_BYTES.offer(raw);
        pendingByteCount += incoming;
        totalBytesQueued += incoming;
    }

    public static List<String> processPending() {
        List<String> allDetected = new ArrayList<>();
        byte[] raw;
        int processed = 0;

        while ((raw = PENDING_BYTES.poll()) != null && processed < 50) {
            pendingByteCount -= raw.length;
            if (pendingByteCount < 0) pendingByteCount = 0;
            processed++;

            List<String> registered = extractRegisteredChannels(raw);
            if (!registered.isEmpty()) {
                totalRegistrationChannels += registered.size();
                for (String ch : registered) {
                    if (!isFilteredChannel(ch)) allDetected.add(ch);
                }
            }

            List<String> embedded = extractChannelTokens(raw);
            if (!embedded.isEmpty()) {
                totalEmbeddedChannels += embedded.size();
                for (String ch : embedded) {
                    if (!isFilteredChannel(ch)) allDetected.add(ch);
                }
            }
        }

        totalChannelsFound += allDetected.size();
        return allDetected;
    }

    public static void clearPending() {
        PENDING_BYTES.clear();
        pendingByteCount = 0;
    }

    private static boolean isFilteredChannel(String channel) {
        if (channel == null || channel.isEmpty()) return true;
        int colon = channel.indexOf(':');
        if (colon <= 0) return false;
        String ns = channel.substring(0, colon);
        return PluginDatabase.isNonPluginNamespace(ns);
    }

    public static List<String> extractRegisteredChannels(byte[] raw) {
        List<String> channels = new ArrayList<>();
        if (raw == null || raw.length == 0) return channels;

        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            String[] parts = REGISTRATION_SPLIT.split(text);

            for (String part : parts) {
                String normalized = normalizeChannel(part);
                if (normalized.isEmpty()) continue;

                if (normalized.contains(":") || isLegacyInterestingChannel(normalized)) {
                    channels.add(normalized);
                }
            }
        } catch (Exception ignored) {}

        return channels;
    }

    public static List<String> extractChannelTokens(byte[] raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null || raw.length == 0) return tokens;

        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            Matcher m = CHANNEL_TOKEN.matcher(text);

            while (m.find()) {
                String token = normalizeChannel(m.group());
                if (!token.isEmpty()) {
                    tokens.add(token);
                }

                if (tokens.size() >= 512) break;
            }
        } catch (Exception ignored) {}

        return tokens;
    }

    private static String normalizeChannel(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isLegacyInterestingChannel(String channel) {
        if (channel == null || channel.isEmpty()) return false;

        return channel.equals("bungeecord") || channel.equals("bungeecord:main")
            || channel.equals("velocity") || channel.equals("velocity:main");
    }

    public static boolean isRegisterableChannel(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isEmpty() || normalized.contains("*")) return false;
        if (!normalized.contains(":")) return false;

        if (normalized.equals("minecraft:register") || normalized.equals("minecraft:unregister")
            || normalized.equals("minecraft:brand")) return false;

        int colon = normalized.indexOf(':');
        if (colon > 0) {
            String ns = normalized.substring(0, colon);
            if (PluginDatabase.isNonPluginNamespace(ns)) return false;
        }
        return true;
    }

    public static int getTotalBytesQueued() { return totalBytesQueued; }
    public static int getTotalChannelsFound() { return totalChannelsFound; }
    public static int getTotalRegistrationChannels() { return totalRegistrationChannels; }
    public static int getTotalEmbeddedChannels() { return totalEmbeddedChannels; }

    public static void resetStats() {
        totalBytesQueued = 0;
        totalChannelsFound = 0;
        totalRegistrationChannels = 0;
        totalEmbeddedChannels = 0;
        PENDING_BYTES.clear();
        pendingByteCount = 0;
    }
}
