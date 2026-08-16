package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ScanPlayerListCommand extends Command {
    private static final String PROBE_PREFIXES = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static final int RESPONSE_TIMEOUT_TICKS = 80;
    private static final int MAX_INLINE_NAMES = 12;

    private final Deque<String> pendingProbes = new ArrayDeque<>();
    private final Set<String> foundNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final List<String> orderedNames = new ArrayList<>();

    private String baseCommand;
    private boolean scanning;
    private int awaitingId = -1;
    private String awaitingPrefix;
    private int timeoutTicks;
    private int probeCount;
    private int nextProbeId = 900000;

    public ScanPlayerListCommand() {
        super("scanplayerlist", "Enumerate online players through command autocomplete and copy them to the clipboard.");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            error("Usage: .scanplayerlist <command> (e.g. .scanplayerlist pay)");
            return SINGLE_SUCCESS;
        });

        builder.then(argument("command", StringArgumentType.greedyString()).executes(context -> {
            String value = StringArgumentType.getString(context, "command").trim();
            if (value.equalsIgnoreCase("stop")) {
                stop(true);
            } else if (value.equalsIgnoreCase("copy")) {
                copyToClipboard();
            } else {
                start(value);
            }
            return SINGLE_SUCCESS;
        }));
    }

    private void start(String command) {
        if (mc.getConnection() == null) {
            error("Not connected to a server.");
            return;
        }
        if (command == null || command.isBlank()) {
            error("Usage: .scanplayerlist <command> (e.g. .scanplayerlist pay)");
            return;
        }

        String cmd = command.trim();
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
        }
        if (cmd.isEmpty() || cmd.contains(" ")) {
            error("Use a single base command without arguments (e.g. .scanplayerlist pay).");
            return;
        }

        baseCommand = cmd;
        scanning = true;
        pendingProbes.clear();
        foundNames.clear();
        orderedNames.clear();
        awaitingId = -1;
        awaitingPrefix = null;
        timeoutTicks = 0;
        probeCount = 0;

        pendingProbes.addLast("");
        for (int i = 0; i < PROBE_PREFIXES.length(); i++) {
            pendingProbes.addLast(String.valueOf(PROBE_PREFIXES.charAt(i)));
        }

        info("§e[Scan] §7Enumerating players via §f/" + cmd + " §7autocomplete (§f" + pendingProbes.size() + " §7probes)...");
    }

    private void stop(boolean announce) {
        if (scanning && announce) {
            info("§e[Scan] §7Stopped. §f" + orderedNames.size() + " §7players collected so far.");
        }
        scanning = false;
        pendingProbes.clear();
        awaitingId = -1;
        awaitingPrefix = null;
    }

    private void copyToClipboard() {
        if (orderedNames.isEmpty()) {
            error("No players collected yet. Run .scanplayerlist <command> first.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderedNames.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(orderedNames.get(i));
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sb.toString()), null);
            info("§a[Scan] §7Copied §f" + orderedNames.size() + " §7player names to the clipboard.");
        } catch (Exception e) {
            error("Clipboard failed: " + e.getMessage());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!scanning) return;
        if (mc.getConnection() == null) {
            stop(false);
            return;
        }

        if (awaitingId >= 0) {
            timeoutTicks++;
            if (timeoutTicks > RESPONSE_TIMEOUT_TICKS) {
                awaitingId = -1;
                awaitingPrefix = null;
                sendNextProbe();
            }
            return;
        }

        sendNextProbe();
    }

    private void sendNextProbe() {
        if (!scanning) return;
        if (pendingProbes.isEmpty()) {
            finish();
            return;
        }

        String prefix = pendingProbes.pollFirst();
        awaitingId = nextProbeId++;
        awaitingPrefix = prefix;
        timeoutTicks = 0;

        try {
            String query = "/" + baseCommand + (prefix.isEmpty() ? "" : " " + prefix);
            mc.getConnection().send(new ServerboundCommandSuggestionPacket(awaitingId, query));
            probeCount++;
        } catch (Exception e) {
            awaitingId = -1;
            awaitingPrefix = null;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!scanning || awaitingId < 0) return;
        if (!(event.packet instanceof ClientboundCommandSuggestionsPacket pkt)) return;
        if (pkt.id() != awaitingId) return;

        int before = orderedNames.size();
        if (pkt.suggestions() != null) {
            for (ClientboundCommandSuggestionsPacket.Entry s : pkt.suggestions()) {
                String text = s.text();
                if (text == null || text.isBlank()) continue;
                addCandidate(text);
            }
        }

        int added = orderedNames.size() - before;
        if (added > 0) {
            String label = awaitingPrefix == null || awaitingPrefix.isEmpty() ? "(all)" : awaitingPrefix;
            StringBuilder line = new StringBuilder("§e[Scan] §7'" + label + "' §7→ §f" + added + "§7 new (total §f"
                + orderedNames.size() + "§7)");
            if (added <= MAX_INLINE_NAMES) {
                line.append("§8: ");
                List<String> addedNames = new ArrayList<>(orderedNames.subList(before, orderedNames.size()));
                for (int i = 0; i < addedNames.size(); i++) {
                    if (i > 0) line.append("§7, ");
                    line.append("§f").append(addedNames.get(i));
                }
            }
            info(line.toString());
        }

        awaitingId = -1;
        awaitingPrefix = null;
    }

    private void addCandidate(String text) {
        String clean = text.trim();
        if (clean.isEmpty()) return;
        while (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        int space = clean.indexOf(' ');
        if (space >= 0) {
            clean = clean.substring(0, space);
        }
        if (clean.length() < 2 || clean.length() > 16) return;
        if (!isUsernameChars(clean)) return;

        String key = clean.toLowerCase(Locale.ROOT);
        if (foundNames.add(key)) {
            orderedNames.add(clean);
        }
    }

    private static boolean isUsernameChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_';
            if (!valid) return false;
        }
        return true;
    }

    private void finish() {
        scanning = false;
        awaitingId = -1;
        awaitingPrefix = null;

        info("§e[Scan] §7Complete. §f" + orderedNames.size() + " §7players found across §f" + probeCount + " §7probes.");
        if (orderedNames.isEmpty()) return;

        copyToClipboard();

        StringBuilder line = new StringBuilder("§7  ");
        for (String name : orderedNames) {
            if (line.length() > 180) {
                info(line.toString());
                line = new StringBuilder("§7  ");
            }
            line.append("§f").append(name).append("§7, ");
        }
        if (line.length() > 4) info(line.toString());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        stop(false);
    }
}
