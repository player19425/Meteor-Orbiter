package orbiter.util;

import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class CommandUtils {
    private CommandUtils() {
    }

    public static String formatCommand(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    private static volatile Object capabilitiesOwner;
    private static volatile ServerCapabilities capabilitiesCache;

    public static ServerCapabilities capabilities() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            capabilitiesOwner = null;
            capabilitiesCache = null;
            return ServerCapabilities.capture(null);
        }

        Object owner = client.getConnection();
        ServerCapabilities cached = capabilitiesCache;
        if (cached != null && capabilitiesOwner == owner) return cached;

        ServerCapabilities fresh = ServerCapabilities.capture(client.getConnection());
        if (!fresh.roots().isEmpty()) {
            capabilitiesOwner = owner;
            capabilitiesCache = fresh;
        }
        return fresh;
    }

    public static String vanilla(String command) {
        if (command == null || command.isEmpty()) return command;

        boolean slash = command.charAt(0) == '/';
        String body = slash ? command.substring(1) : command;
        int space = body.indexOf(' ');
        String root = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);
        if (root.isEmpty() || root.indexOf(':') >= 0) return command;

        ServerCapabilities caps = capabilities();
        if (caps != null && caps.has(root) && !caps.has("minecraft:" + root)) {
            return command;
        }
        return (slash ? "/" : "") + "minecraft:" + body;
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
}
