package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PingSpoof extends Module {
    public enum Mode {
        PingBypass("Ping bypass"), PingSpoof("Ping spoof"), MovementSpoof("Movement spoof"),
        AdaptiveSpoof("Adaptive spoof"), CompetitiveAdvantage("Competitive"), DynamicAdaptive("Dynamic Adaptive");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum DelayPattern {
        Static("Static"), Jitter("Jitter"), Pulse("Pulse"), Ramp("Ramp");
        private final String label;
        DelayPattern(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum MovementPacketMode {
        All("All"), EveryNPacket("Every N packets");
        private final String label;
        MovementPacketMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum QueueOverflowMode {
        SendImmediately("Send immediately"), DropNewest("Drop newest"), DropOldest("Drop oldest");
        private final String label;
        QueueOverflowMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum DisableAction {
        FlushQueued("Flush queued"), DropQueued("Drop queued");
        private final String label;
        DisableAction(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record CombatProfile(
        String name,
        int baseDelayMs,
        DelayPattern pattern,
        Mode mode,
        int maxDelayCapMs,
        int combatDelayMs,
        int jitterMs,
        int movementDelayBonusMs,
        int movementJitterMs,
        int keepAliveScalePercent,
        int pongScalePercent
    ) {
        public String toJson() {
            return "{\n" +
                "  \"name\": \"" + escapeJson(name) + "\",\n" +
                "  \"baseDelayMs\": " + baseDelayMs + ",\n" +
                "  \"pattern\": \"" + pattern.name() + "\",\n" +
                "  \"mode\": \"" + mode.name() + "\",\n" +
                "  \"maxDelayCapMs\": " + maxDelayCapMs + ",\n" +
                "  \"combatDelayMs\": " + combatDelayMs + ",\n" +
                "  \"jitterMs\": " + jitterMs + ",\n" +
                "  \"movementDelayBonusMs\": " + movementDelayBonusMs + ",\n" +
                "  \"movementJitterMs\": " + movementJitterMs + ",\n" +
                "  \"keepAliveScalePercent\": " + keepAliveScalePercent + ",\n" +
                "  \"pongScalePercent\": " + pongScalePercent + "\n}";
        }

        public static CombatProfile fromJson(String json) {
            try {
                String name = extractString(json, "name");
                int baseDelay = extractInt(json, "baseDelayMs", 150);
                DelayPattern pattern = DelayPattern.valueOf(extractString(json, "pattern"));
                Mode m = Mode.valueOf(extractString(json, "mode"));
                int maxCap = extractInt(json, "maxDelayCapMs", 15000);
                int combatDelay = extractInt(json, "combatDelayMs", 150);
                int jitter = extractInt(json, "jitterMs", 90);
                int moveBonus = extractInt(json, "movementDelayBonusMs", 110);
                int moveJitter = extractInt(json, "movementJitterMs", 80);
                int kaScale = extractInt(json, "keepAliveScalePercent", 100);
                int pongScale = extractInt(json, "pongScalePercent", 100);
                return new CombatProfile(name, baseDelay, pattern, m, maxCap, combatDelay,
                    jitter, moveBonus, moveJitter, kaScale, pongScale);
            } catch (Exception e) {
                return null;
            }
        }

        private static String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static String extractString(String json, String key) {
            String search = "\"" + key + "\":\"";
            int start = json.indexOf(search);
            if (start < 0) return "";
            start += search.length();
            int end = json.indexOf("\"", start);
            if (end < 0) return "";
            return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
        }

        private static int extractInt(String json, String key, int def) {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start < 0) return def;
            start += search.length();
            StringBuilder num = new StringBuilder();
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c) || (num.length() == 0 && c == '-')) {
                    num.append(c);
                } else if (num.length() > 0) {
                    break;
                }
            }
            if (num.length() == 0) return def;
            try { return Integer.parseInt(num.toString()); }
            catch (NumberFormatException e) { return def; }
        }
    }

    private static final int ABSOLUTE_MAX_DELAY_MS = 90_000;
    private static final double MOVE_SPEED_THRESHOLD_SQ = 0.0009;
    private static final int KEEPALIVE_TIMEOUT_MS = 25_000;
    private static final int KEEPALIVE_SAFETY_CAP_MS = 20_000;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgCompetitive = settings.createGroup("Competitive");
    private final SettingGroup sgCombat = settings.createGroup("Combat");
    private final SettingGroup sgQueue = settings.createGroup("Queue");
    private final SettingGroup sgProfile = settings.createGroup("Combat Profile");
    private final SettingGroup sgDynamic = settings.createGroup("Dynamic Adaptive");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgAntiTimeout = settings.createGroup("Anti-Timeout");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("How packets are spoofed.").defaultValue(Mode.PingBypass).build());

    private final Setting<DelayPattern> delayPattern = sgTiming.add(new EnumSetting.Builder<DelayPattern>()
        .name("delay-pattern").description("Pattern used to generate delay.").defaultValue(DelayPattern.Static).build());

    private final Setting<Integer> baseDelayMs = sgTiming.add(new IntSetting.Builder()
        .name("base-delay-ms").description("Base spoof delay.").defaultValue(150).min(0).sliderRange(0, 5000).build());

    private final Setting<Integer> jitterMs = sgTiming.add(new IntSetting.Builder()
        .name("jitter-ms").description("Random +/- jitter for jitter pattern.").defaultValue(90).min(0).sliderRange(0, 1500)
        .visible(() -> delayPattern.get() == DelayPattern.Jitter).build());

    private final Setting<Integer> pulseMinDelayMs = sgTiming.add(new IntSetting.Builder()
        .name("pulse-min-delay-ms").description("Minimum pulse delay.").defaultValue(120).min(0).sliderRange(0, 3000)
        .visible(() -> delayPattern.get() == DelayPattern.Pulse).build());

    private final Setting<Integer> pulseMaxDelayMs = sgTiming.add(new IntSetting.Builder()
        .name("pulse-max-delay-ms").description("Maximum pulse delay.").defaultValue(850).min(0).sliderRange(0, 5000)
        .visible(() -> delayPattern.get() == DelayPattern.Pulse).build());

    private final Setting<Integer> pulsePeriodTicks = sgTiming.add(new IntSetting.Builder()
        .name("pulse-period-ticks").description("Ticks for one full pulse cycle.").defaultValue(28).min(2).sliderRange(2, 200)
        .visible(() -> delayPattern.get() == DelayPattern.Pulse).build());

    private final Setting<Integer> rampMinDelayMs = sgTiming.add(new IntSetting.Builder()
        .name("ramp-min-delay-ms").description("Minimum ramp delay.").defaultValue(80).min(0).sliderRange(0, 3000)
        .visible(() -> delayPattern.get() == DelayPattern.Ramp).build());

    private final Setting<Integer> rampMaxDelayMs = sgTiming.add(new IntSetting.Builder()
        .name("ramp-max-delay-ms").description("Maximum ramp delay.").defaultValue(1100).min(0).sliderRange(0, 5000)
        .visible(() -> delayPattern.get() == DelayPattern.Ramp).build());

    private final Setting<Integer> rampStepMs = sgTiming.add(new IntSetting.Builder()
        .name("ramp-step-ms").description("Ramp step size.").defaultValue(70).min(1).sliderRange(1, 500)
        .visible(() -> delayPattern.get() == DelayPattern.Ramp).build());

    private final Setting<Integer> rampStepIntervalTicks = sgTiming.add(new IntSetting.Builder()
        .name("ramp-step-interval").description("Ticks between ramp updates.").defaultValue(3).min(1).sliderRange(1, 40)
        .visible(() -> delayPattern.get() == DelayPattern.Ramp).build());

    private final Setting<Integer> maxDelayCapMs = sgTiming.add(new IntSetting.Builder()
        .name("max-delay-cap-ms").description("Hard delay cap for safety.").defaultValue(15_000).min(100).sliderRange(500, ABSOLUTE_MAX_DELAY_MS).build());

    private final Setting<Boolean> delayKeepAlive = sgGeneral.add(new BoolSetting.Builder()
        .name("delay-keepalive").description("Delay KeepAlive response packets.").defaultValue(true).visible(this::supportsPingDelay).build());

    private final Setting<Integer> keepAliveScalePercent = sgGeneral.add(new IntSetting.Builder()
        .name("keepalive-scale-%").description("Multiplier for KeepAlive delay.").defaultValue(100).min(0).sliderRange(0, 300)
        .visible(() -> supportsPingDelay() && delayKeepAlive.get()).build());

    private final Setting<Boolean> delayPong = sgGeneral.add(new BoolSetting.Builder()
        .name("delay-pong").description("Delay CommonPong response packets.").defaultValue(true).visible(this::supportsPingDelay).build());

    private final Setting<Integer> pongScalePercent = sgGeneral.add(new IntSetting.Builder()
        .name("pong-scale-%").description("Multiplier for Pong delay.").defaultValue(100).min(0).sliderRange(0, 300)
        .visible(() -> supportsPingDelay() && delayPong.get()).build());

    private final Setting<Boolean> delayMovement = sgMovement.add(new BoolSetting.Builder()
        .name("delay-movement").description("Delay movement packets.").defaultValue(true).visible(this::supportsMovementDelay).build());

    private final Setting<MovementPacketMode> movementPacketMode = sgMovement.add(new EnumSetting.Builder<MovementPacketMode>()
        .name("movement-packet-mode").description("How many movement packets get delayed.").defaultValue(MovementPacketMode.All)
        .visible(() -> supportsMovementDelay() && delayMovement.get()).build());

    private final Setting<Integer> movementEveryNPackets = sgMovement.add(new IntSetting.Builder()
        .name("movement-every-n").description("Delay one movement packet every N packets.").defaultValue(2).min(2).sliderRange(2, 12)
        .visible(() -> supportsMovementDelay() && delayMovement.get() && movementPacketMode.get() == MovementPacketMode.EveryNPacket).build());

    private final Setting<Integer> movementScalePercent = sgMovement.add(new IntSetting.Builder()
        .name("movement-scale-%").description("Multiplier for movement delay.").defaultValue(100).min(0).sliderRange(0, 300)
        .visible(() -> supportsMovementDelay() && delayMovement.get()).build());

    private final Setting<Integer> movementDelayBonusMs = sgMovement.add(new IntSetting.Builder()
        .name("movement-delay-bonus-ms").description("Extra delay added only to movement packets.").defaultValue(110).min(0).sliderRange(0, 3000)
        .visible(() -> supportsMovementDelay() && delayMovement.get()).build());

    private final Setting<Integer> movementJitterMs = sgMovement.add(new IntSetting.Builder()
        .name("movement-jitter-ms").description("Random +/- jitter for movement delay.").defaultValue(80).min(0).sliderRange(0, 1000)
        .visible(() -> supportsMovementDelay() && delayMovement.get()).build());

    private final Setting<Boolean> movementOnlyWhileMoving = sgMovement.add(new BoolSetting.Builder()
        .name("movement-only-while-moving").description("Delay movement packets only when player is actually moving.").defaultValue(true)
        .visible(() -> supportsMovementDelay() && delayMovement.get()).build());

    private final Setting<Integer> adaptiveMoveBonusMs = sgMovement.add(new IntSetting.Builder()
        .name("adaptive-moving-bonus-ms").description("Extra delay while moving (Adaptive spoof mode).")
        .defaultValue(180).min(0).sliderRange(0, 2000).visible(() -> mode.get() == Mode.AdaptiveSpoof && delayMovement.get()).build());

    private final Setting<Integer> adaptiveIdleReductionMs = sgMovement.add(new IntSetting.Builder()
        .name("adaptive-idle-reduction-ms").description("Delay reduction while idle (Adaptive spoof mode).")
        .defaultValue(120).min(0).sliderRange(0, 2000).visible(() -> mode.get() == Mode.AdaptiveSpoof).build());

    private final Setting<Integer> competitiveLatencyMs = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-latency-ms").description("Base ping delay used in Competitive mode.").defaultValue(420).min(0).sliderRange(0, 5000)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveJitterMs = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-jitter-ms").description("Small jitter used to avoid perfectly static delay.").defaultValue(35).min(0).sliderRange(0, 800)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveMoveDelayMs = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-move-delay-ms").description("Base movement delay in Competitive mode.").defaultValue(150).min(0).sliderRange(0, 2000)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveBurstExtraDelayMs = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-burst-extra-ms").description("Extra delay added during burst window.").defaultValue(220).min(0).sliderRange(0, 3000)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveMoveEveryNPackets = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-move-every-n").description("Delays 1 movement packet every N packets.").defaultValue(2).min(1).sliderRange(1, 10)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveBurstPeriodTicks = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-burst-period").description("Ticks per burst cycle.").defaultValue(16).min(4).sliderRange(4, 80)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveBurstWindowTicks = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-burst-window").description("Burst hold window inside each cycle.").defaultValue(5).min(1).sliderRange(1, 30)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Integer> competitiveFlushBoost = sgCompetitive.add(new IntSetting.Builder()
        .name("competitive-flush-boost").description("Extra flush budget outside burst windows.").defaultValue(35).min(0).sliderRange(0, 200)
        .visible(this::isCompetitiveMode).build());

    private final Setting<Boolean> combatSpoof = sgCombat.add(new BoolSetting.Builder()
        .name("combat-spoof").description("Apply extra delay only to combat packets (damage, knockback).").defaultValue(true).build());

    private final Setting<Integer> combatDelayMs = sgCombat.add(new IntSetting.Builder()
        .name("combat-delay-ms").description("Delay applied to combat packets when combat spoof is enabled.").defaultValue(150).min(0).sliderRange(0, 1000)
        .visible(() -> combatSpoof.get()).build());

    private final Setting<Integer> maxQueuedPackets = sgQueue.add(new IntSetting.Builder()
        .name("max-queued-packets").description("Safety cap for delayed packets.").defaultValue(200).min(32).sliderRange(64, 1200).build());

    private final Setting<Integer> flushPerTick = sgQueue.add(new IntSetting.Builder()
        .name("flush-per-tick").description("Maximum delayed packets released each tick.").defaultValue(12).min(1).sliderRange(1, 200).build());

    private final Setting<QueueOverflowMode> queueOverflowMode = sgQueue.add(new EnumSetting.Builder<QueueOverflowMode>()
        .name("queue-overflow-mode").description("Behavior when queue reaches max size.").defaultValue(QueueOverflowMode.SendImmediately).build());

    private final Setting<DisableAction> disableAction = sgQueue.add(new EnumSetting.Builder<DisableAction>()
        .name("on-disable").description("What to do with queued packets when module turns off.").defaultValue(DisableAction.FlushQueued).build());

    private final Setting<String> profileName = sgProfile.add(new StringSetting.Builder()
        .name("profile-name").description("Name for the combat profile to save or load.").defaultValue("default").build());

    private final Setting<Boolean> profileSave = sgProfile.add(new BoolSetting.Builder()
        .name("profile-save").description("Toggle to save current settings as a named profile.").defaultValue(false).build());

    private final Setting<Boolean> profileLoad = sgProfile.add(new BoolSetting.Builder()
        .name("profile-load").description("Toggle to load the named profile's settings.").defaultValue(false).build());

    private final Setting<Integer> dynamicTpsFloor = sgDynamic.add(new IntSetting.Builder()
        .name("tps-floor-ms").description("Minimum delay reduction when TPS is very low (<=10).").defaultValue(80).min(0).sliderRange(0, 500)
        .visible(() -> mode.get() == Mode.DynamicAdaptive).build());

    private final Setting<Integer> dynamicHealthFloor = sgDynamic.add(new IntSetting.Builder()
        .name("health-floor-ms").description("Minimum delay when health is critically low (<20%).").defaultValue(50).min(0).sliderRange(0, 500)
        .visible(() -> mode.get() == Mode.DynamicAdaptive).build());

    private final Setting<Double> dynamicTpsThreshold = sgDynamic.add(new DoubleSetting.Builder()
        .name("tps-threshold").description("TPS below this triggers delay reduction.").defaultValue(15.0).min(1.0).sliderRange(1.0, 20.0)
        .visible(() -> mode.get() == Mode.DynamicAdaptive).build());

    private final Setting<Double> dynamicHealthThreshold = sgDynamic.add(new DoubleSetting.Builder()
        .name("health-threshold-%").description("Health % below this triggers delay reduction for ping packets.").defaultValue(40.0).min(1.0).sliderRange(1.0, 100.0)
        .visible(() -> mode.get() == Mode.DynamicAdaptive).build());

    private final Setting<Integer> dynamicCombatMovementReduction = sgDynamic.add(new IntSetting.Builder()
        .name("combat-movement-reduction-ms").description("Reduce movement delay by this amount while in combat.").defaultValue(60).min(0)
        .sliderRange(0, 500).visible(() -> mode.get() == Mode.DynamicAdaptive).build());

    private final Setting<Boolean> disableInInventory = sgInventory.add(new BoolSetting.Builder()
        .name("disable-in-inventory").description("Temporarily flush and bypass spoofing when any screen/GUI is open.").defaultValue(false).build());

    private final Setting<Boolean> antiTimeout = sgAntiTimeout.add(new BoolSetting.Builder()
        .name("anti-timeout").description("Ensure KeepAlive packets are never delayed beyond 20s (25s server timeout).").defaultValue(true).build());

    private final PriorityQueue<DelayedPacket> delayedPackets = new PriorityQueue<>((a, b) -> {
        int timeCmp = Long.compare(a.sendAt, b.sendAt);
        if (timeCmp != 0) return timeCmp;
        return Long.compare(a.sequence, b.sequence);
    });

    private boolean sendingInternally = false;
    private boolean queueOverflowWarned = false;
    private int tickCounter = 0;
    private int rampCurrentDelay = 0;
    private boolean rampIncreasing = true;
    private int movementPacketCounter = 0;
    private long enqueueSequence = 0;
    private boolean wasInventoryOpen = false;

    private double lastKnownTps = 20.0;
    private boolean inCombatState = false;
    private long lastCombatPacketTime = 0;
    private static final long COMBAT_STATE_EXPIRY_MS = 3000;

    public PingSpoof() {
        super(Orbiter.CATEGORY, "ping-spoof", "Advanced ping/movement spoof with bypass, spoof, adaptive, competitive, and dynamic adaptive modes.");
    }

    @Override
    public void onActivate() {
        delayedPackets.clear();
        sendingInternally = false;
        queueOverflowWarned = false;
        tickCounter = 0;
        movementPacketCounter = 0;
        enqueueSequence = 0L;
        wasInventoryOpen = false;
        inCombatState = false;
        lastCombatPacketTime = 0;
        lastKnownTps = 20.0;
        initRampState();

        profileSave.set(false);
        profileLoad.set(false);
    }

    @Override
    public void onDeactivate() {
        if (disableAction.get() == DisableAction.FlushQueued) flushAllNow();
        else delayedPackets.clear();
        queueOverflowWarned = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (sendingInternally || mc.player == null || mc.getConnection() == null) return;

        if (disableInInventory.get() && mc.gui.screen() != null) {
            return;
        }

        Packet<?> packet = event.packet;
        long delay = getPacketDelayMs(packet);
        if (delay < 0) return;

        event.cancel();
        queuePacket(packet, delay);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        updateRampState();
        handleProfileToggles();

        if (mode.get() == Mode.DynamicAdaptive && mc.level != null) {
            lastKnownTps = meteordevelopment.meteorclient.utils.world.TickRate.INSTANCE.getTickRate();
        }

        if (inCombatState && System.currentTimeMillis() - lastCombatPacketTime > COMBAT_STATE_EXPIRY_MS) {
            inCombatState = false;
        }

        if (disableInInventory.get()) {
            boolean inventoryOpen = mc.gui.screen() != null;
            if (inventoryOpen && !wasInventoryOpen) {
                flushAllNow();
            }
            wasInventoryOpen = inventoryOpen;
        }

        if (mc.getConnection() == null || delayedPackets.isEmpty()) return;

        long now = System.currentTimeMillis();
        int sent = 0;
        int flushBudget = flushPerTick.get();

        if (isCompetitiveMode()) {
            if (isCompetitiveBurstWindow()) flushBudget = Math.max(1, flushBudget / 3);
            else flushBudget = Math.min(500, flushBudget + Math.max(0, competitiveFlushBoost.get()));
        }

        while (sent < flushBudget) {
            DelayedPacket next = delayedPackets.peek();
            if (next == null || next.sendAt > now) break;

            delayedPackets.poll();
            sendNow(next.packet);
            sent++;
        }

        if (queueOverflowWarned && delayedPackets.size() < Math.max(8, maxQueuedPackets.get() / 2)) {
            queueOverflowWarned = false;
        }
    }

    private void queuePacket(Packet<?> packet, long delayMs) {
        if (delayedPackets.size() >= maxQueuedPackets.get()) {
            switch (queueOverflowMode.get()) {
                case SendImmediately -> {
                    warnQueueOverflowOnce("Queue full, sending packet immediately.");
                    sendNow(packet);
                    return;
                }
                case DropNewest -> {
                    warnQueueOverflowOnce("Queue full, dropping newest delayed packet.");
                    return;
                }
                case DropOldest -> {
                    warnQueueOverflowOnce("Queue full, dropping oldest delayed packet.");
                    delayedPackets.poll();
                }
            }
        }

        long safeDelay = clampDelay(delayMs);
        if (antiTimeout.get() && packet instanceof ServerboundKeepAlivePacket) {
            safeDelay = Math.min(safeDelay, KEEPALIVE_SAFETY_CAP_MS);
        }

        delayedPackets.add(new DelayedPacket(packet, System.currentTimeMillis() + safeDelay, enqueueSequence++));
    }

    private boolean isCombatPacket(Packet<?> packet) {
        return packet instanceof ClientboundEntityEventPacket || packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
    }

    private long getPacketDelayMs(Packet<?> packet) {
        if (packet == null) return -1L;

        if (combatSpoof.get() && isCombatPacket(packet)) {
            inCombatState = true;
            lastCombatPacketTime = System.currentTimeMillis();
            return clampDelay(combatDelayMs.get());
        }

        if (packet instanceof ServerboundKeepAlivePacket) {
            if (!supportsPingDelay() || !delayKeepAlive.get()) return -1L;
            if (isCompetitiveMode()) {
                int base = jitterDelay(Math.max(0, competitiveLatencyMs.get()), Math.max(0, competitiveJitterMs.get()));
                return scaleDelay(base, keepAliveScalePercent.get());
            }
            int computed = getComputedDelay(false);
            long result = scaleDelay(computed, keepAliveScalePercent.get());

            if (mode.get() == Mode.DynamicAdaptive) {
                result = applyDynamicKeepAliveAdjustments(result);
            }

            if (antiTimeout.get()) {
                result = Math.min(result, KEEPALIVE_SAFETY_CAP_MS);
            }

            return result;
        }

        if (packet instanceof net.minecraft.network.protocol.common.ServerboundPongPacket) {
            if (!supportsPingDelay() || !delayPong.get()) return -1L;
            if (isCompetitiveMode()) {
                int base = jitterDelay(Math.max(0, competitiveLatencyMs.get()), Math.max(0, competitiveJitterMs.get()));
                return scaleDelay(base, pongScalePercent.get());
            }
            int computed = getComputedDelay(false);
            long result = scaleDelay(computed, pongScalePercent.get());

            if (mode.get() == Mode.DynamicAdaptive) {
                result = applyDynamicKeepAliveAdjustments(result);
            }

            return result;
        }

        if (!isMovementPacket(packet)) return -1L;
        if (!supportsMovementDelay() || !delayMovement.get()) return -1L;
        if (movementOnlyWhileMoving.get() && !isPlayerMoving()) return -1L;

        if (disableInInventory.get() && mc.gui.screen() != null) return -1L;

        if (isCompetitiveMode()) {
            movementPacketCounter++;
            int everyN = Math.max(1, competitiveMoveEveryNPackets.get());
            if (everyN > 1 && movementPacketCounter % everyN != 0) return -1L;

            int moveDelay = Math.max(0, competitiveMoveDelayMs.get());
            if (isCompetitiveBurstWindow()) moveDelay += Math.max(0, competitiveBurstExtraDelayMs.get());
            moveDelay = jitterDelay(moveDelay, Math.max(0, competitiveJitterMs.get()));
            return scaleDelay(moveDelay, movementScalePercent.get());
        }

        if (movementPacketMode.get() == MovementPacketMode.EveryNPacket) {
            movementPacketCounter++;
            int everyN = Math.max(2, movementEveryNPackets.get());
            if (movementPacketCounter % everyN != 0) return -1L;
        }

        int computed = getComputedDelay(true);
        long result = scaleDelay(computed, movementScalePercent.get());

        if (mode.get() == Mode.DynamicAdaptive && inCombatState) {
            result = Math.max(0, result - dynamicCombatMovementReduction.get());
        }

        if (antiTimeout.get()) {
            result = Math.min(result, KEEPALIVE_SAFETY_CAP_MS);
        }

        return result;
    }

    private long applyDynamicKeepAliveAdjustments(long delay) {
        long adjusted = delay;

        if (lastKnownTps <= dynamicTpsThreshold.get()) {
            double tpsFactor = Math.max(0.0, lastKnownTps / 20.0);
            int reduction = (int) (dynamicTpsFloor.get() * (1.0 - tpsFactor));
            adjusted = Math.max(0, adjusted - reduction);
        }

        if (mc.player != null) {
            float health = mc.player.getHealth();
            float maxHealth = Math.max(1.0f, mc.player.getMaxHealth());
            double healthPercent = (health / maxHealth) * 100.0;
            if (healthPercent <= dynamicHealthThreshold.get()) {
                double healthFactor = healthPercent / dynamicHealthThreshold.get();
                int reduction = (int) (dynamicHealthFloor.get() * (1.0 - healthFactor));
                adjusted = Math.max(0, adjusted - reduction);
            }
        }

        return adjusted;
    }

    private int getComputedDelay(boolean movementPacket) {
        int delay = switch (delayPattern.get()) {
            case Static -> Math.max(0, baseDelayMs.get());
            case Jitter -> jitterDelay(Math.max(0, baseDelayMs.get()), Math.max(0, jitterMs.get()));
            case Pulse -> pulseDelay();
            case Ramp -> Math.max(0, rampCurrentDelay);
        };

        if (movementPacket) {
            delay += Math.max(0, movementDelayBonusMs.get());
            delay = jitterDelay(delay, Math.max(0, movementJitterMs.get()));
        }

        if (mode.get() == Mode.AdaptiveSpoof) {
            if (isPlayerMoving()) delay += Math.max(0, adaptiveMoveBonusMs.get());
            else delay -= Math.max(0, adaptiveIdleReductionMs.get());
        }

        if (mode.get() == Mode.DynamicAdaptive) {
            delay = applyDynamicBaseAdjustments(delay);
        }

        return (int) clampDelay(delay);
    }

    private int applyDynamicBaseAdjustments(int delay) {
        int adjusted = delay;

        if (lastKnownTps <= dynamicTpsThreshold.get()) {
            double tpsFactor = Math.max(0.0, lastKnownTps / 20.0);
            int reduction = (int) (dynamicTpsFloor.get() * (1.0 - tpsFactor));
            adjusted = Math.max(0, adjusted - reduction);
        }

        if (mc.player != null) {
            float health = mc.player.getHealth();
            float maxHealth = Math.max(1.0f, mc.player.getMaxHealth());
            double healthPercent = (health / maxHealth) * 100.0;
            if (healthPercent <= dynamicHealthThreshold.get()) {
                double healthFactor = healthPercent / dynamicHealthThreshold.get();
                int reduction = (int) (dynamicHealthFloor.get() * (1.0 - healthFactor));
                adjusted = Math.max(0, adjusted - reduction);
            }
        }

        return adjusted;
    }

    private boolean supportsPingDelay() {
        return mode.get() != Mode.MovementSpoof;
    }

    private boolean supportsMovementDelay() {
        return mode.get() != Mode.PingBypass;
    }

    private boolean isCompetitiveMode() {
        return mode.get() == Mode.CompetitiveAdvantage;
    }

    private boolean isCompetitiveBurstWindow() {
        int period = Math.max(4, competitiveBurstPeriodTicks.get());
        int window = Math.max(1, Math.min(period - 1, competitiveBurstWindowTicks.get()));
        return (tickCounter % period) < window;
    }

    private boolean isMovementPacket(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket || packet instanceof net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
    }

    private boolean isPlayerMoving() {
        if (mc.player == null) return false;

        double vx = mc.player.getDeltaMovement().x;
        double vy = mc.player.getDeltaMovement().y;
        double vz = mc.player.getDeltaMovement().z;
        double horizontalSq = vx * vx + vz * vz;

        return horizontalSq > MOVE_SPEED_THRESHOLD_SQ || Math.abs(vy) > 0.03;
    }

    private int jitterDelay(int base, int jitter) {
        if (jitter <= 0) return Math.max(0, base);
        int delta = ThreadLocalRandom.current().nextInt(-jitter, jitter + 1);
        return Math.max(0, base + delta);
    }

    private int pulseDelay() {
        int min = Math.min(pulseMinDelayMs.get(), pulseMaxDelayMs.get());
        int max = Math.max(pulseMinDelayMs.get(), pulseMaxDelayMs.get());
        if (max <= min) return Math.max(0, min);

        int period = Math.max(2, pulsePeriodTicks.get());
        double phase = (tickCounter % period) / (double) period;
        double wave = (Math.sin(phase * (Math.PI * 2.0) - (Math.PI / 2.0)) + 1.0) * 0.5;
        return min + (int) Math.round((max - min) * wave);
    }

    private void initRampState() {
        int min = Math.min(rampMinDelayMs.get(), rampMaxDelayMs.get());
        rampCurrentDelay = Math.max(0, min);
        rampIncreasing = true;
    }

    private void updateRampState() {
        if (delayPattern.get() != DelayPattern.Ramp) {
            initRampState();
            return;
        }

        int interval = Math.max(1, rampStepIntervalTicks.get());
        if (tickCounter % interval != 0) return;

        int min = Math.max(0, Math.min(rampMinDelayMs.get(), rampMaxDelayMs.get()));
        int max = Math.max(min, Math.max(rampMinDelayMs.get(), rampMaxDelayMs.get()));
        int step = Math.max(1, rampStepMs.get());

        if (rampIncreasing) {
            rampCurrentDelay += step;
            if (rampCurrentDelay >= max) {
                rampCurrentDelay = max;
                rampIncreasing = false;
            }
        } else {
            rampCurrentDelay -= step;
            if (rampCurrentDelay <= min) {
                rampCurrentDelay = min;
                rampIncreasing = true;
            }
        }
    }

    private long scaleDelay(int baseDelay, int percent) {
        long scaled = (long) Math.max(0, baseDelay) * Math.max(0, percent) / 100L;
        return clampDelay(scaled);
    }

    private long clampDelay(long value) {
        long cap = Math.max(100L, Math.min((long) ABSOLUTE_MAX_DELAY_MS, (long) maxDelayCapMs.get()));
        if (antiTimeout.get()) {
            cap = Math.min(cap, KEEPALIVE_SAFETY_CAP_MS);
        }
        return Math.max(0L, Math.min(cap, value));
    }

    private void warnQueueOverflowOnce(String message) {
        if (queueOverflowWarned) return;
        queueOverflowWarned = true;
        warning("PingSpoof: " + message);
    }

    private void flushAllNow() {
        if (mc.getConnection() == null || delayedPackets.isEmpty()) return;

        while (!delayedPackets.isEmpty()) {
            DelayedPacket delayed = delayedPackets.poll();
            if (delayed != null) sendNow(delayed.packet);
        }
    }

    private void sendNow(Packet<?> packet) {
        if (mc.getConnection() == null) return;

        sendingInternally = true;
        try {
            mc.getConnection().send(packet);
        } finally {
            sendingInternally = false;
        }
    }

    private void handleProfileToggles() {
        if (profileSave.get()) {
            saveProfile(profileName.get());
            profileSave.set(false);
        }
        if (profileLoad.get()) {
            loadProfile(profileName.get());
            profileLoad.set(false);
        }
    }

    public void saveProfile(String name) {
        if (name == null || name.isBlank()) {
            warning("Profile name cannot be empty.");
            return;
        }

        try {
            File dir = new File("orbiter-ping-profiles");
            if (!dir.exists()) dir.mkdirs();

            CombatProfile profile = new CombatProfile(
                name.trim(),
                baseDelayMs.get(),
                delayPattern.get(),
                mode.get(),
                maxDelayCapMs.get(),
                combatDelayMs.get(),
                jitterMs.get(),
                movementDelayBonusMs.get(),
                movementJitterMs.get(),
                keepAliveScalePercent.get(),
                pongScalePercent.get()
            );

            File file = new File(dir, name.trim() + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(profile.toJson());
            }
            info("Saved ping profile: " + name.trim());
        } catch (IOException e) {
            warning("Failed to save profile: " + e.getMessage());
        }
    }

    public void loadProfile(String name) {
        if (name == null || name.isBlank()) {
            warning("Profile name cannot be empty.");
            return;
        }

        try {
            File dir = new File("orbiter-ping-profiles");
            File file = new File(dir, name.trim() + ".json");
            if (!file.exists()) {
                warning("Profile not found: " + name.trim());
                return;
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            CombatProfile profile = CombatProfile.fromJson(content);
            if (profile == null) {
                warning("Failed to parse profile: " + name.trim());
                return;
            }

            baseDelayMs.set(profile.baseDelayMs());
            delayPattern.set(profile.pattern());
            mode.set(profile.mode());
            maxDelayCapMs.set(profile.maxDelayCapMs());
            combatDelayMs.set(profile.combatDelayMs());
            jitterMs.set(profile.jitterMs());
            movementDelayBonusMs.set(profile.movementDelayBonusMs());
            movementJitterMs.set(profile.movementJitterMs());
            keepAliveScalePercent.set(profile.keepAliveScalePercent());
            pongScalePercent.set(profile.pongScalePercent());

            info("Loaded ping profile: " + name.trim());
        } catch (IOException e) {
            warning("Failed to load profile: " + e.getMessage());
        }
    }

    public List<String> listProfiles() {
        List<String> names = new ArrayList<>();
        File dir = new File("orbiter-ping-profiles");
        if (!dir.exists() || !dir.isDirectory()) return names;

        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return names;

        for (File file : files) {
            String fileName = file.getName();
            names.add(fileName.substring(0, fileName.length() - 5));
        }
        return names;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null || mc.getConnection() == null) return null;

        int queued = delayedPackets.size();
        int effectivePing = estimateEffectivePing();
        String burstState = "";

        if (isCompetitiveMode()) {
            burstState = isCompetitiveBurstWindow() ? " [BURST]" : " [FLUSH]";
        }

        String combatOverlay = inCombatState ? " combat" : "";
        String modeLabel = mode.get().toString();

        return String.format(java.util.Locale.ROOT, "%dms q:%d%s%s%s",
            effectivePing, queued, burstState, combatOverlay,
            mode.get() == Mode.DynamicAdaptive ? " dyn" : "");
    }

    private int estimateEffectivePing() {
        if (mc.player == null || mc.getConnection() == null) return 0;

        DelayedPacket front = delayedPackets.peek();
        if (front == null) return 0;

        long remaining = Math.max(0, front.sendAt - System.currentTimeMillis());
        return (int) remaining;
    }

    private record DelayedPacket(Packet<?> packet, long sendAt, long sequence) {}
}
