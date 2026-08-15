package orbiter.util;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ServerCapabilities {
    public enum State { AVAILABLE, UNAVAILABLE, UNKNOWN }

    private final Set<String> roots;
    private final long capturedAt;

    private ServerCapabilities(Set<String> roots) {
        this.roots = Collections.unmodifiableSet(roots);
        this.capturedAt = System.currentTimeMillis();
    }

    public static ServerCapabilities capture(ClientPacketListener handler) {
        if (handler == null || handler.getCommands() == null) return new ServerCapabilities(Set.of());
        RootCommandNode<?> root = handler.getCommands().getRoot();
        Set<String> names = new HashSet<>();
        for (CommandNode<?> child : root.getChildren()) names.add(child.getName().toLowerCase(Locale.ROOT));
        return new ServerCapabilities(names);
    }

    public boolean has(String command) {
        if (command == null || command.isBlank()) return false;
        return roots.contains(command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
    }

    public State state(String command) {
        if (command == null || command.isBlank()) return State.UNKNOWN;
        return has(command) ? State.AVAILABLE : State.UNAVAILABLE;
    }

    public Set<String> roots() { return roots; }
    public long capturedAt() { return capturedAt; }

    public boolean hasAny(String... commands) {
        for (String command : commands) if (has(command)) return true;
        return false;
    }

    public String preferredVanilla(String command) {
        String namespaced = "minecraft:" + command;
        if (has(namespaced)) return namespaced;
        if (has(command)) return command;
        return namespaced;
    }
}
