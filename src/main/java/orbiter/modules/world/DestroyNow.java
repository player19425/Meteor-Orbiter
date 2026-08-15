package orbiter.modules.world;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.PlayerInfo;
import orbiter.util.CommandBatcher;
import orbiter.util.ServerCapabilities;
import orbiter.util.UserListLoader;
import orbiter.modules.CreativeSafetyModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class DestroyNow extends CreativeSafetyModule {
    public enum ImportMode { Merge, Replace }

    public record ExecutionPlan(
        String serverIdentity,
        String executorName,
        String configurationFingerprint,
        long createdAt,
        List<String> onlineUsers,
        List<String> protectedUsers,
        List<String> usersToDeop,
        List<String> usersToOp,
        List<String> commands,
        List<String> limitations,
        int visibleEntityCount,
        String keepTag
    ) {
        public ExecutionPlan {
            onlineUsers = List.copyOf(onlineUsers);
            protectedUsers = List.copyOf(protectedUsers);
            usersToDeop = List.copyOf(usersToDeop);
            usersToOp = List.copyOf(usersToOp);
            commands = List.copyOf(commands);
            limitations = List.copyOf(limitations);
        }
    }

    private static final String OWNER = "destroy-now";
    private static final long ARM_LIFETIME_MS = 120_000L;
    private static final long PREVIEW_LIFETIME_MS = 120_000L;
    private static final int MAX_IMPORT_ENTRIES = 512;
    private static final long MAX_IMPORT_BYTES = 64L * 1024L;
    private static final int MAX_MANUAL_CHARS = 16 * 1024;
    private static final int HARD_MAX_COMMANDS = 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter MANIFEST_TIME = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgLuckPerms = settings.createGroup("LuckPerms");
    private final SettingGroup sgExecution = settings.createGroup("Execution");

    private final Setting<String> protectedUsers = sgTargets.add(new StringSetting.Builder()
        .name("protected-op-users")
        .description("Comma or newline separated usernames protected from deop and optionally kill.")
        .defaultValue("")
        .build());
    private final Setting<String> importFile = sgTargets.add(new StringSetting.Builder()
        .name("import-file")
        .description("UTF-8 username file relative to the Orbiter data directory.")
        .defaultValue("destroynow-users.txt")
        .build());
    private final Setting<ImportMode> importMode = sgTargets.add(new EnumSetting.Builder<ImportMode>()
        .name("import-mode").description("Merge imported users or replace the manual list.")
        .defaultValue(ImportMode.Merge).build());
    private final Setting<Boolean> protectOpListFromDeop = sgTargets.add(new BoolSetting.Builder()
        .name("protect-op-list-from-deop").description("Protect the configured op list from deop.")
        .defaultValue(true).build());
    private final Setting<Boolean> protectOpListFromKill = sgTargets.add(new BoolSetting.Builder()
        .name("protect-op-list-from-kill").description("Temporarily tag configured online users before kill.")
        .defaultValue(false).build());
    private final Setting<Boolean> opListAfterCleanup = sgTargets.add(new BoolSetting.Builder()
        .name("op-list-after-cleanup").description("Re-op the configured allowlist after cleanup.")
        .defaultValue(true).build());
    private final Setting<Boolean> killPlayers = sgTargets.add(new BoolSetting.Builder()
        .name("kill-players").description("Include players in the previewed kill selector.")
        .defaultValue(true).build());
    private final Setting<Boolean> killNonPlayers = sgTargets.add(new BoolSetting.Builder()
        .name("kill-non-player-entities").description("Include non-player entities in the previewed kill selector.")
        .defaultValue(true).build());

    private final Setting<String> wildcardGrantTemplate = sgLuckPerms.add(new StringSetting.Builder()
        .name("wildcard-grant-template")
        .description("Explicit verified LuckPerms command template. Use {executor}; blank skips it.")
        .defaultValue("").build());
    private final Setting<String> userCleanupTemplate = sgLuckPerms.add(new StringSetting.Builder()
        .name("user-cleanup-template")
        .description("Explicit verified per-user LuckPerms cleanup template. Use {user}; blank skips cleanup.")
        .defaultValue("").build());
    private final Setting<String> protectedGroups = sgLuckPerms.add(new StringSetting.Builder()
        .name("protected-groups").description("Groups retained by externally configured cleanup procedures.")
        .defaultValue("default,recovery").build());

    private final Setting<Integer> commandsPerTick = sgExecution.add(new IntSetting.Builder()
        .name("commands-per-tick").description("Maximum commands sent per client tick.")
        .defaultValue(1).min(1).max(8).sliderRange(1, 4).build());
    private final Setting<Integer> maxCommands = sgExecution.add(new IntSetting.Builder()
        .name("max-commands").description("Reject previews above this command budget.")
        .defaultValue(256).min(1).max(HARD_MAX_COMMANDS).sliderRange(1, 512).build());

    private final CommandBatcher batcher = new CommandBatcher(1);
    private List<String> importedUsers = List.of();
    private String importError;
    private String nonce;
    private String confirmationPhrase;
    private long nonceExpiresAt;
    private String armedServer;
    private String armedExecutor;
    private String armedFingerprint;
    private boolean phraseConfirmed;
    private ExecutionPlan preview;
    private boolean executing;
    private int executionTotal;

    private int toggleCount = 0;
    private long firstToggleTime = 0;
    private static final long TOGGLE_WINDOW_MS = 20_000L;

    public DestroyNow() {
        super("destroy-now", "Toggle 4 times within 20s to execute: inspect → arm → preview → execute.");
    }

    @Override
    public void onActivate() {
        if (!requireActiveContext()) return;

        long now = System.currentTimeMillis();

        if (toggleCount > 0 && now - firstToggleTime > TOGGLE_WINDOW_MS) {
            toggleCount = 0;
            clearArmingState(false);
        }

        if (toggleCount == 0) {

            firstToggleTime = now;
            toggleCount = 1;
            clearArmingState(false);
            inspectAndLoad();
            warning("Toggle 1/4: INSPECT. Enable 3 more times within 20s to execute.");
        } else if (toggleCount == 1) {

            toggleCount = 2;
            beginArm();
            warning("Toggle 2/4: ARMED. Phrase: " + confirmationPhrase);
        } else if (toggleCount == 2) {

            toggleCount = 3;
            if (nonce != null) preview(nonce);
        } else if (toggleCount == 3) {

            toggleCount = 0;
            if (nonce != null && preview != null) {
                execute(nonce, "CONFIRM_DESTROY_NOW");
            }
            toggle();
            return;
        }

    }

    @Override
    public void onDeactivate() {
        toggleCount = 0;
        cancel("Module deactivated; armed state and unsent commands were cleared.");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        cancel("Disconnected; DestroyNow execution and armed state were cancelled.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (nonce != null && (System.currentTimeMillis() > nonceExpiresAt
            || !armedServer.equals(serverIdentity())
            || !armedExecutor.equals(executorName())
            || !armedFingerprint.equals(configurationFingerprint()))) {
            clearArmingState(false);
            warning("Armed state expired or was invalidated by a server, executor, or configuration change.");
        }
        if (!executing || mc.player == null || mc.player.connection == null) return;
        if (!serverIdentity().equals(preview.serverIdentity()) || !executorName().equals(preview.executorName())) {
            cancel("Server or executor identity changed during execution.");
            return;
        }
        batcher.setBudgetPerTick(commandsPerTick.get());
        batcher.drain(mc.player.connection::sendCommand);
        if (batcher.size() == 0) {
            executing = false;
            info("DestroyNow sent all %d planned commands. Server acceptance is not observable reliably client-side.", executionTotal);
        }
    }

    public void inspectAndLoad() {
        if (!requireActiveContext()) return;
        ServerCapabilities capabilities = ServerCapabilities.capture(mc.getConnection());
        warning("INSPECT ONLY: activation sent no commands. Server=%s executor=%s", serverIdentity(), executorName());
        warning("Advertised roots: op=%s deop=%s kill=%s tag=%s LuckPerms=%s. Presence does not prove permission.",
            capabilities.hasAny("op", "minecraft:op"), capabilities.hasAny("deop", "minecraft:deop"),
            capabilities.hasAny("kill", "minecraft:kill"), capabilities.hasAny("tag", "minecraft:tag"),
            capabilities.hasAny("lp", "luckperms", "luckperms:luckperms"));
        String manualError = validateManualUsers();
        if (manualError != null) warning("Manual allowlist rejected: " + manualError);
        Path root = dataDirectory();
        String path = importFile.get();
        CompletableFuture.supplyAsync(() -> UserListLoader.load(root, path, MAX_IMPORT_ENTRIES, MAX_IMPORT_BYTES))
            .thenAccept(result -> mc.execute(() -> {
                if (!isActive() || !path.equals(importFile.get())) return;
                importedUsers = result.valid() ? result.users() : List.of();
                importError = result.valid() ? null : String.join("; ", result.errors());
                if (importError == null) info("Validated %d imported usernames (%d bytes).", importedUsers.size(), result.bytesRead());
                else warning("Allowlist import rejected: " + importError);
            }));
    }

    public void beginArm() {
        if (!requireActiveContext()) return;
        clearArmingState(false);
        nonce = randomHex(6);
        nonceExpiresAt = System.currentTimeMillis() + ARM_LIFETIME_MS;
        armedServer = serverIdentity();
        armedExecutor = executorName();
        armedFingerprint = configurationFingerprint();
        confirmationPhrase = "ARM_DESTROY_NOW_" + sanitizePhrasePart(armedServer) + "_AS_" + armedExecutor;
        warning("ARM challenge created. Retype exactly: .destroynow arm %s", confirmationPhrase);
        warning("Nonce %s expires in 120 seconds. No destructive command has run.", nonce);
    }

    public void confirmArm(String phrase) {
        String problem = validateArmed(false);
        if (problem != null) { warning(problem); return; }
        if (!confirmationPhrase.equals(phrase)) { warning("Confirmation phrase did not match exactly."); return; }
        phraseConfirmed = true;
        info("Arm phrase accepted. Run .destroynow preview %s", nonce);
    }

    public void preview(String suppliedNonce) {
        String problem = validateArmed(true);
        if (problem != null) { warning(problem); return; }
        if (!nonce.equals(suppliedNonce)) { warning("Nonce did not match."); return; }
        if (importError != null) { warning("Preview blocked because the import file is invalid: " + importError); return; }
        String manualError = validateManualUsers();
        if (manualError != null) { warning("Preview blocked: " + manualError); return; }
        ExecutionPlan candidate = buildPlan(randomHex(5));
        if (candidate.commands().size() > maxCommands.get() || candidate.commands().size() > HARD_MAX_COMMANDS) {
            warning("Preview rejected: %d commands exceeds the configured/hard budget.", candidate.commands().size());
            return;
        }
        preview = candidate;
        try {
            Path manifest = writeManifest(candidate);
            warning("DRY RUN: deop=%d, visible entities=%d, LuckPerms user targets=%d, op=%d, commands=%d",
                candidate.usersToDeop().size(), candidate.visibleEntityCount(),
                userCleanupTemplate.get().isBlank() ? 0 : candidate.usersToDeop().size(),
                candidate.usersToOp().size(), candidate.commands().size());
            info("Immutable preview manifest: " + manifest);
            for (String limitation : candidate.limitations()) warning("Limitation: " + limitation);
        } catch (IOException exception) {
            preview = null;
            warning("Preview blocked because the manifest could not be written: " + exception.getMessage());
        }
    }

    public void execute(String suppliedNonce, String fixedConfirmation) {
        String problem = validateArmed(true);
        if (problem != null) { warning(problem); return; }
        if (!nonce.equals(suppliedNonce) || !"CONFIRM_DESTROY_NOW".equals(fixedConfirmation)) {
            warning("Nonce or final confirmation token did not match.");
            return;
        }
        if (preview == null || System.currentTimeMillis() - preview.createdAt() > PREVIEW_LIFETIME_MS) {
            warning("A recent preview is required.");
            return;
        }
        if (!preview.serverIdentity().equals(serverIdentity()) || !preview.executorName().equals(executorName())
            || !preview.configurationFingerprint().equals(configurationFingerprint())) {
            clearArmingState(false);
            warning("Server, executor, or configuration changed after preview. Re-arm.");
            return;
        }
        ExecutionPlan current = buildPlan(preview.keepTag());
        if (!current.commands().equals(preview.commands()) || !current.onlineUsers().equals(preview.onlineUsers())) {
            clearArmingState(false);
            warning("Online users or exact command plan changed after preview. Re-arm and preview again.");
            return;
        }
        ServerCapabilities capabilities = ServerCapabilities.capture(mc.getConnection());
        if (!capabilities.hasAny("tag", "minecraft:tag") || !capabilities.hasAny("kill", "minecraft:kill")) {
            warning("Execution blocked: advertised tag and kill roots are required for self-protection.");
            return;
        }
        batcher.clear();
        for (int index = 0; index < preview.commands().size(); index++) {
            if (!batcher.offer(new CommandBatcher.Step(OWNER, OWNER + ":" + index, 0, preview.commands().get(index)))) {
                batcher.clear();
                warning("Execution blocked because the bounded command queue rejected the plan.");
                return;
            }
        }
        executionTotal = preview.commands().size();
        executing = true;
        nonce = null;
        confirmationPhrase = null;
        phraseConfirmed = false;
        warning("Execution started with %d immutable commands. Use .destroynow cancel for unsent commands.", executionTotal);
    }

    public void cancel(String reason) {
        int removed = batcher.cancelOwner(OWNER);
        executing = false;
        executionTotal = 0;
        clearArmingState(false);
        if (reason != null) warning(reason + " Removed " + removed + " unsent commands.");
    }

    public void status() {
        info("DestroyNow active=%s armed=%s phrase-confirmed=%s preview=%s executing=%s queued=%d",
            isActive(), nonce != null, phraseConfirmed, preview != null, executing, batcher.size());
    }

    private ExecutionPlan buildPlan(String keepTagSuffix) {
        String executor = executorName();
        String server = serverIdentity();
        List<String> online = onlineUsers();
        List<String> allowlist = resolvedAllowlist(executor);
        Set<String> protectedLower = protectOpListFromDeop.get()
            ? lowerSet(allowlist)
            : Set.of(executor.toLowerCase(Locale.ROOT));
        List<String> toDeop = new ArrayList<>();
        for (String user : online) if (!protectedLower.contains(user.toLowerCase(Locale.ROOT))) toDeop.add(user);
        List<String> toOp = opListAfterCleanup.get() ? allowlist : List.of(executor);
        String keepTag = "orbiter_keep_" + keepTagSuffix;
        List<String> commands = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        ServerCapabilities capabilities = ServerCapabilities.capture(mc.getConnection());
        boolean luckPerms = capabilities.hasAny("lp", "luckperms", "luckperms:luckperms");

        if (luckPerms) addTemplate(commands, wildcardGrantTemplate.get(), "{executor}", executor, limitations, "wildcard grant");
        for (String user : toOp) commands.add(commandRoot(capabilities, "op") + " " + user);
        commands.add(commandRoot(capabilities, "tag") + " " + executor + " add " + keepTag);
        if (protectOpListFromKill.get()) {
            for (String user : allowlist) if (!user.equalsIgnoreCase(executor) && containsIgnoreCase(online, user)) {
                commands.add(commandRoot(capabilities, "tag") + " " + user + " add " + keepTag);
            }
        }
        if (luckPerms && !userCleanupTemplate.get().isBlank()) {
            for (String user : toDeop) addTemplate(commands, userCleanupTemplate.get(), "{user}", user, limitations, "user cleanup");
        } else {
            limitations.add(luckPerms
                ? "LuckPerms was detected, but no explicitly verified user cleanup template is configured."
                : "LuckPerms was not advertised in the command tree; permission cleanup and wildcard grant may be skipped.");
        }
        limitations.add("Offline LuckPerms users and groups cannot be exhaustively enumerated by a normal client.");
        limitations.add("The manifest records observable client state only and is not a complete LuckPerms backup.");
        limitations.add("Command-tree visibility does not prove authorization, and command success cannot be acknowledged reliably.");
        for (String user : toDeop) commands.add(commandRoot(capabilities, "deop") + " " + user);
        String killSelector = killSelector(keepTag);
        if (killSelector != null) commands.add(commandRoot(capabilities, "kill") + " " + killSelector);
        if (opListAfterCleanup.get()) for (String user : toOp) commands.add(commandRoot(capabilities, "op") + " " + user);
        if (luckPerms) addTemplate(commands, wildcardGrantTemplate.get(), "{executor}", executor, limitations, "wildcard reassertion");
        commands.add(commandRoot(capabilities, "tag") + " @e[tag=" + keepTag + "] remove " + keepTag);

        int entities = 0;
        if (mc.level != null) for (var ignored : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) entities++;
        return new ExecutionPlan(server, executor, configurationFingerprint(), System.currentTimeMillis(), online,
            allowlist, toDeop, toOp, commands, deduplicate(limitations), entities, keepTag);
    }

    private void addTemplate(List<String> commands, String template, String placeholder, String value,
                             List<String> limitations, String operation) {
        if (template == null || template.isBlank()) {
            limitations.add("No explicitly verified LuckPerms " + operation + " template is configured.");
            return;
        }
        String trimmed = template.trim();
        if (!trimmed.contains(placeholder) || trimmed.contains("\n") || trimmed.contains("\r")) {
            limitations.add("The configured " + operation + " template is invalid and was skipped.");
            return;
        }
        commands.add(trimmed.replace(placeholder, value).replaceFirst("^/", ""));
    }

    private String validateArmed(boolean requirePhrase) {
        if (!requireActiveContext()) return "DestroyNow must be active and connected.";
        if (nonce == null) return "DestroyNow is not armed. Run .destroynow arm.";
        if (System.currentTimeMillis() > nonceExpiresAt) { clearArmingState(false); return "Arm nonce expired."; }
        if (!armedServer.equals(serverIdentity()) || !armedExecutor.equals(executorName())) {
            clearArmingState(false); return "Server or executor changed. Armed state was invalidated.";
        }
        if (!armedFingerprint.equals(configurationFingerprint())) {
            clearArmingState(false); return "Configuration changed. Armed state was invalidated.";
        }
        if (requirePhrase && !phraseConfirmed) return "Retype the generated arm phrase first.";
        return null;
    }

    private boolean requireActiveContext() {
        return isActive() && mc.player != null && mc.getConnection() != null && mc.level != null;
    }

    private void clearArmingState(boolean clearQueue) {
        nonce = null;
        confirmationPhrase = null;
        nonceExpiresAt = 0L;
        armedServer = null;
        armedExecutor = null;
        armedFingerprint = null;
        phraseConfirmed = false;
        preview = null;
        if (clearQueue) batcher.clear();
    }

    private List<String> resolvedAllowlist(String executor) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (importMode.get() == ImportMode.Merge) addManualUsers(values, protectedUsers.get());
        for (String user : importedUsers) values.putIfAbsent(user.toLowerCase(Locale.ROOT), user);
        if (importMode.get() == ImportMode.Replace && importedUsers.isEmpty()) addManualUsers(values, protectedUsers.get());
        values.put(executor.toLowerCase(Locale.ROOT), executor);
        return List.copyOf(values.values());
    }

    private String validateManualUsers() {
        String input = protectedUsers.get();
        if (input == null || input.isBlank()) return null;
        if (input.length() > MAX_MANUAL_CHARS) return "manual username text exceeds " + MAX_MANUAL_CHARS + " characters";
        int count = 0;
        for (String token : input.split("[,\\r\\n]+")) {
            String user = token.trim();
            if (user.isEmpty()) continue;
            if (!UserListLoader.isValidUsername(user)) return "invalid Minecraft username: " + user;
            if (++count > MAX_IMPORT_ENTRIES) return "manual list exceeds " + MAX_IMPORT_ENTRIES + " entries";
        }
        return null;
    }

    private void addManualUsers(LinkedHashMap<String, String> values, String input) {
        if (input == null) return;
        for (String token : input.split("[,\\r\\n]+")) {
            String user = token.trim();
            if (UserListLoader.isValidUsername(user)) values.putIfAbsent(user.toLowerCase(Locale.ROOT), user);
        }
    }

    private List<String> onlineUsers() {
        List<String> users = new ArrayList<>();
        if (mc.getConnection() == null) return users;
        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            GameProfile profile = entry.getProfile();
            if (profile != null && UserListLoader.isValidUsername(profile.name())) users.add(profile.name());
        }
        users.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(users);
    }

    private String commandRoot(ServerCapabilities capabilities, String root) {
        return capabilities.has("minecraft:" + root) ? "minecraft:" + root : root;
    }

    private String killSelector(String tag) {
        if (killPlayers.get() && killNonPlayers.get()) return "@e[tag=!" + tag + "]";
        if (killPlayers.get()) return "@a[tag=!" + tag + "]";
        if (killNonPlayers.get()) return "@e[type=!minecraft:player,tag=!" + tag + "]";
        return null;
    }

    private String configurationFingerprint() {
        return Integer.toHexString(String.join("\u0000", protectedUsers.get(), importFile.get(), importMode.get().name(),
            protectOpListFromDeop.get().toString(), protectOpListFromKill.get().toString(),
            opListAfterCleanup.get().toString(), killPlayers.get().toString(), killNonPlayers.get().toString(),
            wildcardGrantTemplate.get(), userCleanupTemplate.get(), protectedGroups.get(),
            commandsPerTick.get().toString(), maxCommands.get().toString(), String.join(",", importedUsers)).hashCode());
    }

    private Path writeManifest(ExecutionPlan plan) throws IOException {
        Path directory = dataDirectory().resolve("destroynow-manifests");
        Files.createDirectories(directory);
        Path file = directory.resolve("preview-" + MANIFEST_TIME.format(Instant.ofEpochMilli(plan.createdAt())) + "-" + nonce + ".txt");
        List<String> lines = new ArrayList<>();
        lines.add("DestroyNow dry-run manifest");
        lines.add("Created UTC: " + Instant.ofEpochMilli(plan.createdAt()));
        lines.add("Server: " + plan.serverIdentity());
        lines.add("Executor: " + plan.executorName());
        lines.add("Configuration fingerprint: " + plan.configurationFingerprint());
        lines.add("Online users: " + plan.onlineUsers());
        lines.add("Protected users: " + plan.protectedUsers());
        lines.add("Users to deop: " + plan.usersToDeop());
        lines.add("Users to op: " + plan.usersToOp());
        lines.add("Visible entity count (not selector result): " + plan.visibleEntityCount());
        lines.add("Protected LuckPerms groups configured: " + protectedGroups.get());
        lines.add("");
        lines.add("Limitations:");
        for (String limitation : plan.limitations()) lines.add("- " + limitation);
        lines.add("");
        lines.add("Exact commands:");
        for (int index = 0; index < plan.commands().size(); index++) lines.add((index + 1) + ". /" + plan.commands().get(index));
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private Path dataDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("orbiter").toAbsolutePath().normalize();
    }

    private String serverIdentity() {
        if (mc.hasSingleplayerServer()) return "integrated:" + (mc.getSingleplayerServer() == null ? "unknown" : mc.getSingleplayerServer().getWorldData().getLevelName());
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) return mc.getCurrentServer().ip;
        if (mc.getConnection() != null && mc.getConnection().getServerData() != null) return mc.getConnection().getServerData().ip;
        return "unknown-server";
    }

    private String executorName() {
        return mc.player == null ? "unknown" : mc.player.getGameProfile().name();
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte current : value) builder.append(String.format(Locale.ROOT, "%02x", current & 0xff));
        return builder.toString();
    }

    private static String sanitizePhrasePart(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(value.toLowerCase(Locale.ROOT));
        return result;
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    private static List<String> deduplicate(List<String> values) {
        return List.copyOf(new LinkedHashMap<String, String>() {{
            for (String value : values) putIfAbsent(value, value);
        }}.values());
    }
}
