package orbiter.modules;

import orbiter.Orbiter;
import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiStaff extends Module {

    private final SettingGroup sgGeneral       = settings.getDefaultGroup();
    private final SettingGroup sgDetection     = settings.createGroup("Detection");
    private final SettingGroup sgProximity     = settings.createGroup("Proximity");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");
    private final SettingGroup sgChatTriggers  = settings.createGroup("Chat Triggers");
    private final SettingGroup sgActions       = settings.createGroup("Actions");

    private final Setting<String> watchedUsernames = sgGeneral.add(new StringSetting.Builder()
        .name("watched-usernames")
        .description("Comma-separated usernames that trigger detection.")
        .defaultValue("")
        .build());

    private final Setting<String> watchedPrefixes = sgGeneral.add(new StringSetting.Builder()
        .name("watched-prefixes")
        .description("Comma-separated prefixes to detect (color codes are auto-stripped).")
        .defaultValue("[ADMIN],[MOD],[STAFF],[HELPER],[OWNER],[BUILDER],[DEV],[DEVELOPER],[SR.MOD],[JR.MOD],[SRMOD],[JRMOD],[OP],[MANAGER],[HEAD-MOD],[HEAD-ADMIN],[MODERATOR],[ADMINISTRATOR],[SUPPORT],[GAMEMASTER],[GM],[SRADMIN],[HEADSTAFF],[OPERATOR],[CO-OWNER],[COOWNER],[SUPERVISOR],[TRAINEE],[T-MOD],[TMOD],[TRIAL],[TRIALMOD],[TRIAL-MOD],[TRIAL-STAFF],[SENIORMOD],[SENIOR-MOD],[SENIORADMIN],[SENIOR-ADMIN],[HEADHELPER],[HEAD-HELPER],[LEAD],[LEADMOD],[LEAD-MOD],[LEADADMIN],[LEAD-ADMIN],[LEADSTAFF],[LEAD-STAFF],[ASSISTANT],[ASSISTANTMOD],[ASSTMOD],[COMMUNITY],[COMMUNITYMANAGER],[COMMUNITY-MANAGER],[CM],[EVENT],[EVENTS],[EVENTMANAGER],[EVENT-MANAGER],[ADMIN+],[MOD+],[STAFF+],[OWNER+],[MGR],[SRMGR],[JRHELPER],[SRHELPER],[MENTOR],[SENTINEL],[WATCHDOG],[GUARD],[SECURITY],[S-MOD],[S-ADMIN],[SUPERMOD],[SUPER-MOD],[SUPERADMIN],[SUPER-ADMIN],[TRUSTED],[TRUSTEDSTAFF],[TRUSTED-STAFF],[TRUSTEDMOD],[TRUSTED-MOD],[TRUSTEDADMIN],[TRUSTED-ADMIN],[INSPECTOR],[INVESTIGATOR],[ENFORCER],[ANTI-CHEAT],[ANTICHEAT],[AC],[QC],[QUALITY],[QUALITYCONTROL],[QUALITY-CONTROL],[TESTER],[QA],[ARCHITECT],[DESIGNER],[ENGINEER],[LEADDEV],[LEAD-DEV],[CORE],[CORETEAM],[CORE-TEAM],[FOUNDER],[COFOUNDER],[CO-FOUNDER],[DIRECTOR],[HEAD],[HEADDEV],[HEAD-DEV],[HEADADMIN],[HEAD-ADMINISTRATOR],[SYSADMIN],[SYS-ADMIN],[TECH],[TECHADMIN],[TECH-ADMIN],[OPERATOR+],[MODERATOR+],[ADMINISTRATOR+],[STAFFTEAM],[STAFF-TEAM],[STAFFER],[MANAGEMENT],[MGMT],[PROJECTMANAGER],[PROJECT-MANAGER],[PM],[TRIALHELPER],[TRIAL-HELPER],[JRMODERATOR],[SRMODERATOR],[GLOBALMOD],[GLOBAL-MOD],[GLOBALADMIN],[GLOBAL-ADMIN],[NETWORKADMIN],[NETWORK-ADMIN],[NETWORKMOD],[NETWORK-MOD],[PLUS],[HIDDEN],[VANISHED],[GHOST],[COUNCIL],[BOARD],[WARDEN],[CAPTAIN],[DEITY],[GOD],[SAGE],[ELDER],[CONSOLE],[WEB],[ROOT],[SHERIFF],[~],[&],[@],[#],[$],[+],[-],[*]")
        .build());

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Do not trigger on your own account.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Ignore players on your Meteor friends list.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> caseSensitive = sgGeneral.add(new BoolSetting.Builder()
        .name("case-sensitive")
        .description("Case-sensitive matching for names and prefixes.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> disableAfterLeave = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-after-leave")
        .description("Disable this module after it disconnects you.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> instantPacketCheck = sgDetection.add(new BoolSetting.Builder()
        .name("instant-packet-check")
        .description("React instantly to player list update packets.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> periodicTabScan = sgDetection.add(new BoolSetting.Builder()
        .name("periodic-tab-scan")
        .description("Periodically scan the entire tab list.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> scanInterval = sgDetection.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between periodic tab scans.")
        .defaultValue(10)
        .min(1).sliderRange(1, 200)
        .visible(periodicTabScan::get)
        .build());

    private final Setting<Boolean> checkOnJoin = sgDetection.add(new BoolSetting.Builder()
        .name("check-on-join")
        .description("Scan tab list immediately on joining a server.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectSpectators = sgDetection.add(new BoolSetting.Builder()
        .name("detect-spectators")
        .description("Trigger when any player is in Spectator mode (even if not on watch list).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> vanishDetection = sgDetection.add(new BoolSetting.Builder()
        .name("vanish-detection")
        .description("Cross-check tab list vs loaded entities to detect vanished players.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> vanishScanInterval = sgDetection.add(new IntSetting.Builder()
        .name("vanish-scan-interval")
        .description("Ticks between vanish detection scans.")
        .defaultValue(60)
        .min(20).sliderRange(20, 200)
        .visible(vanishDetection::get)
        .build());

    private final Setting<Boolean> detectRankSymbols = sgDetection.add(new BoolSetting.Builder()
        .name("detect-rank-symbols")
        .description("Detect players with rank symbols like [+], [-], [*] in their display name.")
        .defaultValue(true)
        .build());

    private final Setting<String> operatorPatterns = sgDetection.add(new StringSetting.Builder()
        .name("operator-patterns")
        .description("Comma-separated patterns to detect in display names for operators.")
        .defaultValue("owner,admin,op,staff,mod")
        .build());

    private final Setting<Boolean> detectLowPing = sgDetection.add(new BoolSetting.Builder()
        .name("detect-low-ping")
        .description("Trigger on players with latency below 5ms (often indicates staff/OP).")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> proximityEnabled = sgProximity.add(new BoolSetting.Builder()
        .name("proximity-detection")
        .description("Trigger when a watched player is nearby in the world.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> proximityRadius = sgProximity.add(new IntSetting.Builder()
        .name("proximity-radius")
        .description("Blocks away to trigger proximity alert.")
        .defaultValue(100)
        .min(10).sliderRange(10, 500)
        .visible(proximityEnabled::get)
        .build());

    private final Setting<Integer> proximityScanInterval = sgProximity.add(new IntSetting.Builder()
        .name("proximity-scan-interval")
        .description("Ticks between proximity scans.")
        .defaultValue(20)
        .min(1).sliderRange(1, 100)
        .visible(proximityEnabled::get)
        .build());

    private final Setting<Boolean> chatAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Show detection alerts in chat.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> toastNotifications = sgNotifications.add(new BoolSetting.Builder()
        .name("toast-notifications")
        .description("Show advancement-style toast popups on detection.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> notifyOnLeave = sgNotifications.add(new BoolSetting.Builder()
        .name("notify-on-leave")
        .description("Also notify when a watched player leaves.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatTriggerEnabled = sgChatTriggers.add(new BoolSetting.Builder()
        .name("chat-trigger-enabled")
        .description("Trigger actions based on chat messages (join, advancement, etc).")
        .defaultValue(true)
        .build());

    private final Setting<String> chatJoinKeywords = sgChatTriggers.add(new StringSetting.Builder()
        .name("chat-join-keywords")
        .description("Comma-separated join keywords.")
        .defaultValue("joined the game,se ha unido,se unio,entro al juego,connected,logged in,has logged in,se conecto,ha entrado,entered the game")
        .visible(chatTriggerEnabled::get)
        .build());

    private final Setting<String> chatLeaveKeywords = sgChatTriggers.add(new StringSetting.Builder()
        .name("chat-leave-keywords")
        .description("Comma-separated leave keywords.")
        .defaultValue("left the game,se ha desconectado,salio del juego,disconnected,logged out,has logged out,se desconecto,ha salido,quit the game")
        .visible(chatTriggerEnabled::get)
        .build());

    private final Setting<String> advancementKeywords = sgChatTriggers.add(new StringSetting.Builder()
        .name("advancement-keywords")
        .description("Comma-separated advancement keywords.")
        .defaultValue("has made the advancement,has completed the challenge,ha conseguido el logro,has reached the goal,ha completado el desafio,earned the achievement")
        .visible(chatTriggerEnabled::get)
        .build());

    private final Setting<TriggerAction> onDetect = sgActions.add(new EnumSetting.Builder<TriggerAction>()
        .name("on-detect")
        .description("Action to take when a staff/watched player is detected.")
        .defaultValue(TriggerAction.Leave)
        .build());

    private final Setting<Boolean> sendChatBeforeAction = sgActions.add(new BoolSetting.Builder()
        .name("send-chat-before-action")
        .description("Send a message/command in chat before executing the action.")
        .defaultValue(false)
        .build());

    private final Setting<String> chatMessage = sgActions.add(new StringSetting.Builder()
        .name("chat-message")
        .description("Message/command to send (use / for commands).")
        .defaultValue("/hub")
        .visible(sendChatBeforeAction::get)
        .build());

    private final Setting<String> modulesToDisable = sgActions.add(new StringSetting.Builder()
        .name("modules-to-disable")
        .description("Comma-separated module names to disable on trigger.")
        .defaultValue("")
        .build());

    private final Setting<String> modulesToEnable = sgActions.add(new StringSetting.Builder()
        .name("modules-to-enable")
        .description("Comma-separated module names to enable on trigger.")
        .defaultValue("")
        .build());

    private final Setting<Integer> actionDelayTicks = sgActions.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Ticks to wait before executing actions after detection.")
        .defaultValue(5)
        .min(0).sliderRange(0, 100)
        .build());

    private static final Pattern STRIP_FORMAT = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern USERNAME_REGEX = Pattern.compile("([A-Za-z0-9_]{3,16})\\s*$");

    private final Set<UUID> knownPlayers = new HashSet<>();
    private final Set<String> alertedPlayers = new HashSet<>();
    private int scanTicker;
    private int proxTicker;
    private int vanishTicker;
    private long lastLeaveMs;
    private final Set<String> tabListNames = new HashSet<>();

    private TriggerAction pendingAction;
    private String pendingTarget;
    private int pendingDelay;

    public AntiStaff() {
        super(Orbiter.CATEGORY, "anti-staff",
            "Detects staff and auto-leaves.");
    }

    @Override
    public void onActivate() {
        resetAll();
        if (checkOnJoin.get()) scanTabSnapshot("activation");
    }

    @Override
    public void onDeactivate() {
        resetAll();
    }

    private void resetAll() {
        knownPlayers.clear();
        alertedPlayers.clear();
        tabListNames.clear();
        scanTicker = 0;
        proxTicker = 0;
        vanishTicker = 0;
        lastLeaveMs = 0;
        pendingAction = null;
        pendingTarget = null;
        pendingDelay = 0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        knownPlayers.clear();
        alertedPlayers.clear();
        scanTicker = 0;
        proxTicker = 0;
        if (checkOnJoin.get()) scanTabSnapshot("join");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (pendingAction != null) {
            if (pendingDelay > 0) { pendingDelay--; return; }
            executeAction(pendingAction, pendingTarget);
            pendingAction = null;
            pendingTarget = null;
        }

        scanTicker++;
        proxTicker++;

        if (periodicTabScan.get() && scanTicker >= scanInterval.get()) {
            scanTicker = 0;
            scanTabSnapshot("periodic");
        }

        if (proximityEnabled.get() && proxTicker >= proximityScanInterval.get()) {
            proxTicker = 0;
            scanProximity();
        }

        vanishTicker++;
        if (vanishDetection.get() && vanishTicker >= vanishScanInterval.get()) {
            vanishTicker = 0;
            scanVanished();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!instantPacketCheck.get()) return;
        if (event.packet instanceof ClientboundPlayerInfoUpdatePacket packet) {
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
                UUID uuid = entry.profileId();
                if (uuid == null) continue;
                if (knownPlayers.contains(uuid)) continue;
                knownPlayers.add(uuid);

                GameProfile profile = entry.profile();
                String name = profile != null ? profile.name() : "";
                String display = entry.displayName() != null ? entry.displayName().getString() : name;
                processPlayerDetected(name, display, uuid, "packet");
            }
        }
        if (notifyOnLeave.get() && event.packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket packet) {
            for (UUID uuid : packet.profileIds()) {
                knownPlayers.remove(uuid);
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!chatTriggerEnabled.get() || event.getMessage() == null) return;
        String msg = stripFormatting(event.getMessage().getString());
        if (msg.isBlank()) return;

        for (String kw : split(chatJoinKeywords.get())) {
            if (containsIC(msg, kw)) {
                String name = extractNameBefore(msg, kw);
                if (name != null) processChatDetection(name, "chat-join");
                return;
            }
        }

        for (String kw : split(chatLeaveKeywords.get())) {
            if (containsIC(msg, kw)) {
                String name = extractNameBefore(msg, kw);
                if (name != null && notifyOnLeave.get() && isMatchName(name)) {
                    emitAlert("§a[AntiStaff] §7" + name + " left.", name, false);
                }
                return;
            }
        }

        for (String kw : split(advancementKeywords.get())) {
            if (containsIC(msg, kw)) {
                String name = extractNameBefore(msg, kw);
                if (name != null) processChatDetection(name, "advancement");
                return;
            }
        }
    }

    private void scanTabSnapshot(String source) {
        ClientPacketListener handler = mc.getConnection();
        if (handler == null) return;

        for (PlayerInfo entry : handler.getOnlinePlayers()) {
            GameProfile profile = entry.getProfile();
            if (profile == null || profile.id() == null) continue;
            UUID uuid = profile.id();

            if (!knownPlayers.contains(uuid)) {
                knownPlayers.add(uuid);
                String name = profile.name();
                String display = entry.getTabListDisplayName() != null ? entry.getTabListDisplayName().getString() : name;
                processPlayerDetected(name, display, uuid, source);
            }

            if (detectSpectators.get() && entry.getGameMode() == GameType.SPECTATOR) {
                String name = profile.name();
                if (!isIgnoredName(name) && !alertedPlayers.contains("spec:" + name)) {
                    alertedPlayers.add("spec:" + name);
                    emitAlert("§c§l[AntiStaff] §e⚠ SPECTATOR: §f" + name, name, true);
                    scheduleAction(onDetect.get(), name);
                }
            }

            String opName = profile.name();
            if (opName != null && !isIgnoredName(opName)) {
                if (entry.getTabListDisplayName() != null) {
                    String display = entry.getTabListDisplayName().getString();
                    String cleanDisplay = stripFormatting(display);
                    if (isMatchOperator(cleanDisplay) && !alertedPlayers.contains("op:" + opName)) {
                        alertedPlayers.add("op:" + opName);
                        emitAlert("§c§l[AntiStaff] §e⚠ OPERATOR: §f" + bestLabel(opName, cleanDisplay), opName, true);
                        scheduleAction(onDetect.get(), opName);
                    }
                }
            }

            if (detectLowPing.get() && entry.getLatency() < 5) {
                String pingName = profile.name();
                if (pingName != null && !isIgnoredName(pingName) && !alertedPlayers.contains("lowping:" + pingName)) {
                    alertedPlayers.add("lowping:" + pingName);
                    emitAlert("§c§l[AntiStaff] §e⚠ LOW PING: §f" + pingName, pingName, true);
                    scheduleAction(onDetect.get(), pingName);
                }
            }
        }
    }

    private void scanProximity() {
        if (mc.level == null || mc.player == null) return;
        double radius = proximityRadius.get();
        double radiusSq = radius * radius;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.getGameProfile() == null) continue;
            String name = player.getGameProfile().name();
            if (name == null || isIgnoredName(name)) continue;
            if (!isMatchName(name)) continue;

            double distSq = mc.player.distanceToSqr(player);
            if (distSq <= radiusSq) {
                String key = "prox:" + name;
                if (!alertedPlayers.contains(key)) {
                    alertedPlayers.add(key);
                    int dist = (int) Math.sqrt(distSq);
                    emitAlert("§c§l[AntiStaff] §e⚠ NEAR: §f" + name + " §7(" + dist + " blocks)", name, true);
                    scheduleAction(onDetect.get(), name);
                }
            }
        }
    }

    private Set<String> vanishCandidatesPrev = new HashSet<>();

    private void scanVanished() {
        if (mc.level == null || mc.player == null) return;
        ClientPacketListener handler = mc.getConnection();
        if (handler == null) return;

        tabListNames.clear();
        for (PlayerInfo entry : handler.getOnlinePlayers()) {
            if (entry.getProfile() != null && entry.getProfile().name() != null) {
                tabListNames.add(entry.getProfile().name().toLowerCase(Locale.ROOT));
            }
        }

        Set<String> current = new HashSet<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            String name = player.getGameProfile().name();
            if (name == null || isIgnoredName(name)) continue;

            if (player.hasCustomName()) continue;
            if (player.getUUID().version() != 4) continue;

            String lower = name.toLowerCase(Locale.ROOT);
            if (tabListNames.contains(lower)) continue;

            current.add(lower);

            if (!vanishCandidatesPrev.contains(lower)) continue;
            String key = "vanish:" + lower;
            if (!alertedPlayers.contains(key)) {
                alertedPlayers.add(key);
                double dist = Math.sqrt(mc.player.distanceToSqr(player));
                emitAlert("§c§l[AntiStaff] §d⚠ VANISHED PLAYER: §f" + name + " §7(" + (int) dist + " blocks)", name, true);
                scheduleAction(onDetect.get(), name);
            }
        }

        vanishCandidatesPrev = current;
    }

    private void processPlayerDetected(String name, String display, UUID uuid, String source) {
        if (isIgnored(name, uuid)) return;
        String cleanDisplay = stripFormatting(display);
        if (!isMatch(name, cleanDisplay)) return;

        String key = "det:" + (name != null ? name : uuid.toString());
        if (alertedPlayers.contains(key)) return;
        alertedPlayers.add(key);

        String label = bestLabel(name, cleanDisplay);
        emitAlert("§c§l[AntiStaff] §eDetected: §f" + label + " §7(" + source + ")", name, true);
        scheduleAction(onDetect.get(), name);
    }

    private void processChatDetection(String name, String source) {
        if (isIgnoredName(name)) return;
        if (!isMatchName(name)) return;

        String key = "chat:" + name;
        if (alertedPlayers.contains(key)) return;
        alertedPlayers.add(key);

        emitAlert("§c§l[AntiStaff] §e" + source + ": §f" + name, name, true);
        scheduleAction(onDetect.get(), name);
    }

    private void scheduleAction(TriggerAction action, String target) {
        if (action == TriggerAction.Nothing) return;
        pendingAction = action;
        pendingTarget = target;
        pendingDelay = actionDelayTicks.get();
    }

    private void executeAction(TriggerAction action, String target) {

        if (sendChatBeforeAction.get() && mc.player != null && mc.player.connection != null) {
            String msg = chatMessage.get();
            if (msg != null && !msg.isBlank()) {
                if (msg.startsWith("/")) mc.player.connection.sendCommand(msg.substring(1));
                else mc.player.connection.sendChat(msg);
            }
        }

        switch (action) {
            case Nothing, Notify -> {}
            case Leave -> forceLeave("Detected: " + (target != null ? target : "unknown"));
            case SendChat -> {}
            case DisableModules -> toggleModules(modulesToDisable.get(), false);
            case EnableModules -> toggleModules(modulesToEnable.get(), true);
            case LeaveAndDisable -> {
                toggleModules(modulesToDisable.get(), false);
                forceLeave("Detected: " + (target != null ? target : "unknown"));
            }
        }
    }

    private void toggleModules(String list, boolean enable) {
        for (String name : split(list)) {
            Module m = Modules.get().get(name.trim());
            if (m != null) {
                if (enable && !m.isActive()) { m.toggle(); info("Enabled: " + m.name); }
                if (!enable && m.isActive()) { m.toggle(); info("Disabled: " + m.name); }
            }
        }
    }

    private void forceLeave(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastLeaveMs < 1000) return;
        lastLeaveMs = now;

        warning("[AntiStaff] " + reason);
        Component text = Component.literal("[AntiStaff] " + reason);

        if (mc.getConnection() != null) mc.getConnection().getConnection().disconnect(text);
        else mc.disconnectFromWorld(text);

        if (disableAfterLeave.get()) toggle();
    }

    private void emitAlert(String message, String playerName, boolean high) {
        if (chatAlerts.get()) {
            if (high) warning(message);
            else info(message);
        }
        if (toastNotifications.get() && mc.gui.toastManager() != null) {
            mc.gui.toastManager().addToast(new SystemToast(
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal("§c⚠ AntiStaff"),
                Component.literal(playerName + " detected!")
            ));
        }
    }

    private boolean isMatch(String name, String display) {
        return isMatchName(name) || isMatchDisplay(display);
    }

    private boolean isMatchName(String name) {
        if (name == null || name.isBlank()) return false;
        String n = normalize(name);
        for (String w : split(watchedUsernames.get())) {
            if (!w.isBlank() && normalize(w).equals(n)) return true;
        }
        return false;
    }

    private boolean isMatchDisplay(String display) {
        if (display == null || display.isBlank()) return false;

        String clean = normalize(stripFormatting(display));

        if (detectRankSymbols.get() && (clean.contains("[+]") || clean.contains("[-]") || clean.contains("[*]"))) return true;

        for (String p : split(watchedPrefixes.get())) {
            String pat = normalize(stripFormatting(p));
            if (!p.isBlank() && matchesToken(clean, pat)) return true;
        }
        for (String w : split(watchedUsernames.get())) {
            if (!w.isBlank() && clean.contains(normalize(w))) return true;
        }
        return false;
    }

    private boolean isIgnored(String name, UUID uuid) {
        if (ignoreSelf.get() && mc.player != null) {
            if (uuid != null && mc.player.getUUID().equals(uuid)) return true;
            String self = mc.player.getGameProfile().name();
            if (self != null && namesEqual(self, name)) return true;
        }
        if (ignoreFriends.get() && name != null && !name.isBlank() && Friends.get().get(name) != null) return true;
        return false;
    }

    private boolean isIgnoredName(String name) {
        if (ignoreSelf.get() && mc.player != null) {
            String self = mc.player.getGameProfile().name();
            if (self != null && namesEqual(self, name)) return true;
        }
        return false;
    }

    private String stripFormatting(String text) {
        if (text == null) return "";
        return STRIP_FORMAT.matcher(text).replaceAll("").replace('\u00A0', ' ').trim();
    }

    private String normalize(String v) {
        if (v == null) return "";
        String s = v.trim();
        return caseSensitive.get() ? s : s.toLowerCase(Locale.ROOT);
    }

    private boolean namesEqual(String a, String b) {
        if (a == null || b == null) return false;
        return caseSensitive.get() ? a.equals(b) : a.equalsIgnoreCase(b);
    }

    private List<String> split(String raw) {
        List<String> r = new ArrayList<>();
        if (raw == null || raw.isBlank()) return r;
        for (String s : raw.split("[,;]")) { String t = s.trim(); if (!t.isEmpty()) r.add(t); }
        return r;
    }

    private boolean containsIC(String text, String kw) {
        if (text == null || kw == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(kw.toLowerCase(Locale.ROOT));
    }

    private String extractNameBefore(String message, String keyword) {
        String lower = message.toLowerCase(Locale.ROOT);
        String kLower = keyword.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(kLower);
        if (idx <= 0) return null;
        String before = stripFormatting(message.substring(0, idx)).trim();
        Matcher m = USERNAME_REGEX.matcher(before);
        return m.find() ? m.group(1) : null;
    }

    private boolean isMatchOperator(String cleanDisplay) {
        if (cleanDisplay == null || cleanDisplay.isBlank()) return false;
        String clean = normalize(cleanDisplay);
        for (String p : split(operatorPatterns.get())) {
            String pat = normalize(stripFormatting(p));
            if (!pat.isBlank() && matchesToken(clean, pat)) return true;
        }
        return false;
    }

    private boolean matchesToken(String text, String token) {
        int flags = caseSensitive.get() ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile("(?<![\\w])" + Pattern.quote(token) + "(?![\\w])", flags).matcher(text).find();
    }

    private String bestLabel(String name, String display) {
        if (display == null || display.isBlank() || display.equals(name)) return name;
        return name + " (" + display + ")";
    }

    public enum TriggerAction {
        Nothing,
        Notify,
        Leave,
        SendChat,
        DisableModules,
        EnableModules,
        LeaveAndDisable
    }
}
