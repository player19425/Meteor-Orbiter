package orbiter.util;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CommandUtils {
    private static volatile String[] entityIdsCache;

    private CommandUtils() {
    }

    public static String formatCommand(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    public static String escapeJson(String value) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    public static String stripLegacyFormatting(String value) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(value.length());
        boolean skipNext = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (skipNext) {
                skipNext = false;
                continue;
            }

            if (c == '\u00A7' || c == '&') {
                skipNext = true;
                continue;
            }

            if (c >= 0x20 && c != 0x7F) sb.append(c);
        }

        return sb.toString();
    }

    public static String normalizeEntityId(String value) {
        return normalizeEntityId(value, "minecraft:pig");
    }

    public static String normalizeEntityId(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) normalized = fallback;
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        return normalized;
    }

    public static String[] entityIds() {
        String[] cached = entityIdsCache;
        if (cached != null) return cached.clone();

        if (Registries.ENTITY_TYPE == null) return new String[0];

        synchronized (CommandUtils.class) {
            cached = entityIdsCache;
            if (cached != null) return cached.clone();

            List<String> ids = new ArrayList<>();
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) ids.add(id.toString());
            ids.sort(Comparator.naturalOrder());

            String[] result = ids.toArray(String[]::new);
            entityIdsCache = result;
            return result.clone();
        }
    }
}
