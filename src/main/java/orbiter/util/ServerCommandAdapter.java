package orbiter.util;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public final class ServerCommandAdapter {
    private ServerCommandAdapter() {}

    public static String giveRoot(ServerCapabilities capabilities) {
        if (capabilities == null) return "minecraft:give";
        if (capabilities.has("minecraft:give")) return "minecraft:give";
        if (capabilities.has("essentials:give")) return "essentials:give";
        if (capabilities.has("egive") || capabilities.has("essentials:egive")) return "egive";
        return "minecraft:give";
    }

    public static String worldEditRoot(ServerCapabilities capabilities, String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (capabilities != null && capabilities.has("worldedit:" + normalized)) return "worldedit:" + normalized;
        return normalized;
    }

    public static boolean canIssue(ServerCapabilities capabilities, String command) {
        return capabilities != null && capabilities.state(command) == ServerCapabilities.State.AVAILABLE;
    }

    public static void send(MinecraftClient client, ServerCapabilities capabilities, String command) {
        if (client == null || client.player == null || client.player.networkHandler == null || command == null) return;
        client.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
    }
}
