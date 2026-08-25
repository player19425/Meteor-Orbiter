package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

import java.util.UUID;

public class OutOfReach extends Module {
    public enum TargetMode {
        Auto,
        Selected
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgBoost = settings.createGroup("Boost");

    private final Setting<Double> survivalReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("survival-reach")
        .description("Reach used when the target player is in survival/adventure mode.")
        .defaultValue(3.0)
        .min(1.0)
        .sliderRange(1.0, 8.0)
        .build());

    private final Setting<Double> creativeReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("creative-reach")
        .description("Reach used when the target player is in creative mode.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderRange(1.0, 10.0)
        .build());

    private final Setting<Double> reachMargin = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach-margin")
        .description("Base safety margin around detected reach.")
        .defaultValue(0.1)
        .min(0.0)
        .sliderRange(0.0, 2.0)
        .build());

    private final Setting<Double> extraBuffer = sgGeneral.add(new DoubleSetting.Builder()
        .name("extra-buffer")
        .description("Extra distance added on top of reach + margin.")
        .defaultValue(1.75)
        .min(0.0)
        .sliderRange(0.0, 8.0)
        .build());

    private final Setting<Double> latencyBuffer = sgGeneral.add(new DoubleSetting.Builder()
        .name("latency-buffer")
        .description("Extra compensation for ping and server delay.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderRange(0.0, 6.0)
        .build());

    private final Setting<Integer> predictTicks = sgGeneral.add(new IntSetting.Builder()
        .name("predict-ticks")
        .description("How many ticks of movement are considered for aggressive spacing.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build());

    private final Setting<Double> maxPredictBuffer = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-predict-buffer")
        .description("Maximum extra buffer gained from predicted closing speed.")
        .defaultValue(4.0)
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .build());

    private final Setting<Double> sprintClosingBoost = sgGeneral.add(new DoubleSetting.Builder()
        .name("sprint-closing-boost")
        .description("Extra anti-rush buffer added when the threat is sprinting toward you.")
        .defaultValue(1.25)
        .min(0.0)
        .sliderRange(0.0, 6.0)
        .build());

    private final Setting<Double> emergencyDistanceBoost = sgGeneral.add(new DoubleSetting.Builder()
        .name("emergency-distance-boost")
        .description("Extra teleport distance when threat is already very close.")
        .defaultValue(1.5)
        .min(0.0)
        .sliderRange(0.0, 8.0)
        .build());

    private final Setting<Double> minPushDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-push-distance")
        .description("Minimum distance gained by each correction teleport.")
        .defaultValue(2.25)
        .min(0.1)
        .sliderRange(0.1, 12.0)
        .build());

    private final Setting<Double> maxTeleportDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-teleport-distance")
        .description("Hard cap on each correction teleport distance.")
        .defaultValue(16.0)
        .min(2.0)
        .sliderRange(2.0, 64.0)
        .build());

    private final Setting<Integer> burstPackets = sgGeneral.add(new IntSetting.Builder()
        .name("burst-packets")
        .description("How many position packets are sent during each correction burst.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 8)
        .build());

    private final Setting<Double> scanRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("scan-range")
        .description("Maximum range to search for threatening players.")
        .defaultValue(24.0)
        .min(1.0)
        .sliderRange(1.0, 64.0)
        .build());

    private final Setting<Integer> cooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .description("Ticks to wait between correction bursts.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not react to players in your Meteor friends list.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoEnableReach = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-enable-reach")
        .description("Automatically enables the built-in Reach module while this module is active.")
        .defaultValue(false)
        .build());

    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("Auto picks the most dangerous threat. Selected only protects against your selected player.")
        .defaultValue(TargetMode.Auto)
        .build());

    private final Setting<Boolean> autoSelectFromCrosshair = sgTargeting.add(new BoolSetting.Builder()
        .name("auto-select-crosshair")
        .description("When in Selected mode, looking at a player sets them as selected target.")
        .defaultValue(true)
        .visible(() -> targetMode.get() == TargetMode.Selected)
        .build());

    private final Setting<Boolean> keepSelectedTarget = sgTargeting.add(new BoolSetting.Builder()
        .name("keep-selected-target")
        .description("Keep last selected player even after you stop aiming at them.")
        .defaultValue(true)
        .visible(() -> targetMode.get() == TargetMode.Selected)
        .build());

    private final Setting<Boolean> useTimerBoost = sgBoost.add(new BoolSetting.Builder()
        .name("use-timer-boost")
        .description("Optionally uses Meteor Timer while OutOfReach is active.")
        .defaultValue(false)
        .build());

    private final Setting<Double> timerMultiplier = sgBoost.add(new DoubleSetting.Builder()
        .name("timer-multiplier")
        .description("Timer override multiplier used while boost is enabled.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderRange(0.1, 10.0)
        .visible(useTimerBoost::get)
        .build());

    private final Setting<Boolean> disableTimerOnDeactivate = sgBoost.add(new BoolSetting.Builder()
        .name("disable-timer-on-deactivate")
        .description("Turns Timer back off when this module is disabled, if OutOfReach enabled it.")
        .defaultValue(true)
        .visible(useTimerBoost::get)
        .build());

    private final Setting<Boolean> collisionAwareTeleport = sgGeneral.add(new BoolSetting.Builder()
        .name("collision-aware-teleport")
        .description("Only teleports to positions where your hitbox does not collide with blocks.")
        .defaultValue(true)
        .build());

    private final Setting<Double> safeDistanceStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("safe-distance-step")
        .description("Distance step used while searching for a collision-safe destination.")
        .defaultValue(0.75)
        .min(0.1)
        .sliderRange(0.1, 4.0)
        .visible(collisionAwareTeleport::get)
        .build());

    private final Setting<Integer> safeVerticalSearch = sgGeneral.add(new IntSetting.Builder()
        .name("safe-vertical-search")
        .description("How many blocks up/down to search for a safe Y destination.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 12)
        .visible(collisionAwareTeleport::get)
        .build());

    private final Setting<Boolean> preferVerticalEscape = sgGeneral.add(new BoolSetting.Builder()
        .name("prefer-vertical-escape")
        .description("When dodge path is blocked, try escaping upward over blocks or walls first.")
        .defaultValue(true)
        .visible(collisionAwareTeleport::get)
        .build());

    private final Setting<Integer> obstacleClimbHeight = sgGeneral.add(new IntSetting.Builder()
        .name("obstacle-climb-height")
        .description("Maximum extra Y blocks used to climb over blocked dodge paths.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 16)
        .visible(collisionAwareTeleport::get)
        .build());

    private final Setting<Double> climbDistanceProbe = sgGeneral.add(new DoubleSetting.Builder()
        .name("climb-distance-probe")
        .description("Distance step used while probing blocked paths for climb-up escapes.")
        .defaultValue(0.75)
        .min(0.25)
        .sliderRange(0.25, 4.0)
        .visible(collisionAwareTeleport::get)
        .build());

    private int cooldownLeft = 0;
    private Player lastThreat;

    private UUID selectedTargetUuid;
    private String selectedTargetName;

    private boolean timerEnabledByOutOfReach;

    private static final double[] SAFE_HEADING_OFFSETS = {0.0, 20.0, -20.0, 40.0, -40.0, 65.0, -65.0, 90.0, -90.0};

    private static final class ThreatData {
        private final Player player;
        private final double distance;
        private final double triggerDistance;
        private final double safeDistance;

        private ThreatData(Player player, double distance, double triggerDistance, double safeDistance) {
            this.player = player;
            this.distance = distance;
            this.triggerDistance = triggerDistance;
            this.safeDistance = safeDistance;
        }
    }

    public OutOfReach() {
        super(Orbiter.CATEGORY, "out-of-reach", "Fights from outside player reach.");
    }

    @Override
    public void onActivate() {
        cooldownLeft = 0;
        lastThreat = null;

        if (targetMode.get() == TargetMode.Selected && autoSelectFromCrosshair.get()) {
            updateSelectionFromCrosshair();
        }

        if (autoEnableReach.get()) ensureReachEnabled();
        if (useTimerBoost.get()) ensureTimerBoost();
    }

    @Override
    public void onDeactivate() {
        lastThreat = null;

        if (useTimerBoost.get() || timerEnabledByOutOfReach) {
            releaseTimerBoost(disableTimerOnDeactivate.get());
        }

        if (targetMode.get() == TargetMode.Selected && !keepSelectedTarget.get()) {
            clearSelectedTarget();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (targetMode.get() == TargetMode.Selected && autoSelectFromCrosshair.get()) {
            updateSelectionFromCrosshair();
        }

        if (autoEnableReach.get()) ensureReachEnabled();

        if (useTimerBoost.get()) ensureTimerBoost();
        else if (timerEnabledByOutOfReach) releaseTimerBoost(true);

        if (cooldownLeft > 0) {
            cooldownLeft--;
            return;
        }

        ThreatData threat = targetMode.get() == TargetMode.Auto ? findMostDangerousThreat() : findSelectedThreat();
        lastThreat = threat != null ? threat.player : null;
        if (threat == null) return;

        if (threat.distance > threat.triggerDistance) return;

        if (teleportOutsideReach(threat.player, threat.safeDistance, threat.distance)) {
            cooldownLeft = Math.max(0, cooldownTicks.get());
        }
    }

    private ThreatData findMostDangerousThreat() {
        if (mc.player == null || mc.level == null) return null;

        double maxSq = scanRange.get() * scanRange.get();
        ThreatData best = null;
        double bestDanger = Double.POSITIVE_INFINITY;

        for (Player player : mc.level.players()) {
            if (!isValidCandidate(player)) continue;

            double distSq = mc.player.distanceToSqr(player);
            if (distSq > maxSq) continue;

            ThreatData data = buildThreatData(player);
            if (data == null) continue;

            double danger = data.distance - data.triggerDistance;
            if (danger < bestDanger) {
                bestDanger = danger;
                best = data;
            }
        }

        return best;
    }

    private ThreatData findSelectedThreat() {
        Player selected = resolveSelectedTarget();
        if (selected == null) return null;

        ThreatData data = buildThreatData(selected);
        if (data == null && !keepSelectedTarget.get()) clearSelectedTarget();
        return data;
    }

    private ThreatData buildThreatData(Player player) {
        if (mc.player == null || mc.level == null || !isValidCandidate(player)) return null;

        double distance = mc.player.getEyePosition().distanceTo(player.getBoundingBox().getCenter());
        double baseReach = getReachFor(player);
        if (baseReach <= 0.0) return null;

        double dynamicBuffer = getDynamicClosingBuffer(player);
        double triggerDistance = Math.max(0.0, (baseReach - reachMargin.get()) + dynamicBuffer);
        double safeDistance = (baseReach + reachMargin.get()) + extraBuffer.get() + dynamicBuffer;

        return new ThreatData(player, distance, triggerDistance, safeDistance);
    }

    private boolean isValidCandidate(Player player) {
        if (mc.player == null || player == null) return false;
        if (player == mc.player) return false;
        if (!player.isAlive() || player.isSpectator()) return false;
        return !ignoreFriends.get() || Friends.get().shouldAttack(player);
    }

    private void updateSelectionFromCrosshair() {
        if (mc.player == null) return;
        Entity targeted = mc.crosshairPickEntity;
        if (!(targeted instanceof Player player)) return;
        if (!isValidCandidate(player)) return;

        selectedTargetUuid = player.getUUID();
        selectedTargetName = player.getName().getString();
    }

    private Player resolveSelectedTarget() {
        if (mc.level == null || selectedTargetUuid == null) return null;

        for (Player player : mc.level.players()) {
            if (player.getUUID().equals(selectedTargetUuid)) {
                return isValidCandidate(player) ? player : null;
            }
        }

        return null;
    }

    private void clearSelectedTarget() {
        selectedTargetUuid = null;
        selectedTargetName = null;
    }

    private double getDynamicClosingBuffer(Player threat) {
        if (mc.player == null || threat == null) return 0.0;

        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3 threatPos = new Vec3(threat.getX(), threat.getY(), threat.getZ());
        Vec3 delta = playerPos.subtract(threatPos);
        double horizontal = Math.sqrt((delta.x * delta.x) + (delta.z * delta.z));
        if (horizontal < 1.0E-6) return Math.min(maxPredictBuffer.get(), latencyBuffer.get());

        double dirX = delta.x / horizontal;
        double dirZ = delta.z / horizontal;

        Vec3 relVel = mc.player.getDeltaMovement().subtract(threat.getDeltaMovement());
        double radialSpeed = (relVel.x * dirX) + (relVel.z * dirZ);
        double closingSpeed = Math.max(0.0, -radialSpeed);

        double predicted = closingSpeed * Math.max(0, predictTicks.get());
        Vec3 threatVel = threat.getDeltaMovement();
        double threatToward = (threatVel.x * dirX) + (threatVel.z * dirZ);
        double rushBonus = Math.max(0.0, threatToward) * Math.max(1.0, predictTicks.get() * 0.35);
        double sprintBonus = threat.isSprinting() ? sprintClosingBoost.get() : 0.0;

        double total = latencyBuffer.get() + predicted + rushBonus + sprintBonus;
        return Math.min(maxPredictBuffer.get(), Math.max(0.0, total));
    }

    private double getReachFor(Player player) {
        GameType gameMode = resolveGameMode(player);
        if (gameMode == GameType.SPECTATOR) return 0.0;
        if (gameMode == GameType.CREATIVE) return creativeReach.get();
        return survivalReach.get();
    }

    private GameType resolveGameMode(Player player) {
        if (mc.getConnection() == null || player == null) return GameType.SURVIVAL;

        PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
        if (entry != null && entry.getGameMode() != null) return entry.getGameMode();

        if (player.isCreative()) return GameType.CREATIVE;
        if (player.isSpectator()) return GameType.SPECTATOR;
        return GameType.SURVIVAL;
    }

    private boolean teleportOutsideReach(Player threat, double wantedDistance, double currentDistance) {
        if (mc.player == null || threat == null) return false;

        Vec3 threatNow = new Vec3(threat.getX(), threat.getY(), threat.getZ());
        Vec3 ourNow = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3 threatPredicted = threatNow.add(threat.getDeltaMovement().scale(Math.max(0, predictTicks.get())));
        Vec3 ourPredicted = ourNow.add(mc.player.getDeltaMovement().scale(Math.max(0, predictTicks.get())));

        double dx = ourPredicted.x - threatPredicted.x;
        double dz = ourPredicted.z - threatPredicted.z;
        double horizontalSq = (dx * dx) + (dz * dz);

        if (horizontalSq < 1.0E-6) {
            double yawRad = Math.toRadians(mc.player.getYRot());
            dx = -Math.sin(yawRad);
            dz = Math.cos(yawRad);
            horizontalSq = (dx * dx) + (dz * dz);
            if (horizontalSq < 1.0E-6) return false;
        }

        double invLen = 1.0 / Math.sqrt(horizontalSq);
        double dirX = dx * invLen;
        double dirZ = dz * invLen;

        double desiredDistance = Math.max(wantedDistance, currentDistance + minPushDistance.get());
        double emergencyGap = Math.max(0.0, (getReachFor(threat) + 1.0) - currentDistance);
        if (emergencyGap > 0.0) desiredDistance += emergencyDistanceBoost.get() + emergencyGap;
        desiredDistance = Math.min(desiredDistance, Math.max(2.0, maxTeleportDistance.get()));

        Vec3 destination;
        if (collisionAwareTeleport.get()) destination = findSafeTeleportDestination(threat, dirX, dirZ, desiredDistance);
        else destination = new Vec3(threat.getX() + (dirX * desiredDistance), mc.player.getY(), threat.getZ() + (dirZ * desiredDistance));

        if (destination == null) return false;

        double dstX = destination.x;
        double dstY = destination.y;
        double dstZ = destination.z;

        if (mc.getConnection() != null) {
            int packets = Math.max(1, burstPackets.get());
            for (int i = 1; i <= packets; i++) {
                double t = (double) i / packets;
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    mc.player.getX() + (dstX - mc.player.getX()) * t,
                    mc.player.getY() + (dstY - mc.player.getY()) * t,
                    mc.player.getZ() + (dstZ - mc.player.getZ()) * t,
                    mc.player.onGround(),
                    mc.player.horizontalCollision
                ));
            }
        }

        mc.player.setPos(dstX, dstY, dstZ);
        return true;
    }

    private Vec3 findSafeTeleportDestination(Player threat, double dirX, double dirZ, double preferredDistance) {
        if (mc.player == null || mc.level == null || threat == null) return null;

        double baseY = mc.player.getY();
        double maxDistance = Math.max(preferredDistance, Math.max(2.0, maxTeleportDistance.get()));
        double step = Math.max(0.1, safeDistanceStep.get());
        boolean primaryBlocked = isPrimaryPathBlocked(threat, dirX, dirZ, preferredDistance, baseY);

        if (preferVerticalEscape.get() && primaryBlocked) {
            Vec3 climbFirst = findVerticalEscapeDestination(threat, dirX, dirZ, preferredDistance, maxDistance, baseY);
            if (climbFirst != null) return climbFirst;
        }

        for (double distance = preferredDistance; distance <= maxDistance + 1.0E-6; distance += step) {
            Vec3 candidate = findSafeForDistance(threat, dirX, dirZ, distance, baseY);
            if (candidate != null) return candidate;
        }

        for (double distance = preferredDistance - step; distance >= 2.0; distance -= step) {
            Vec3 candidate = findSafeForDistance(threat, dirX, dirZ, distance, baseY);
            if (candidate != null) return candidate;
        }

        if (preferVerticalEscape.get()) {
            Vec3 climbFallback = findVerticalEscapeDestination(threat, dirX, dirZ, preferredDistance, maxDistance, baseY);
            if (climbFallback != null) return climbFallback;
        }

        return null;
    }

    private boolean isPrimaryPathBlocked(Player threat, double dirX, double dirZ, double distance, double baseY) {
        if (mc.player == null || threat == null) return false;

        double endX = threat.getX() + (dirX * distance);
        double endZ = threat.getZ() + (dirZ * distance);
        return isPathBlocked(mc.player.getX(), mc.player.getZ(), endX, endZ, baseY);
    }

    private boolean isPathBlocked(double startX, double startZ, double endX, double endZ, double y) {
        double dx = endX - startX;
        double dz = endZ - startZ;
        double total = Math.sqrt((dx * dx) + (dz * dz));
        if (total < 1.0E-6) return false;

        double probeStep = Math.max(0.25, climbDistanceProbe.get());
        int samples = Math.max(1, (int) Math.ceil(total / probeStep));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            double x = startX + (dx * t);
            double z = startZ + (dz * t);

            boolean safeAtY = isTeleportPositionSafe(x, y, z);
            boolean safeAbove = isTeleportPositionSafe(x, y + 1.0, z);
            if (!safeAtY && !safeAbove) return true;
        }

        return false;
    }

    private Vec3 findVerticalEscapeDestination(Player threat, double dirX, double dirZ, double preferredDistance, double maxDistance, double baseY) {
        if (mc.player == null || threat == null) return null;

        int maxClimb = Math.max(1, obstacleClimbHeight.get());
        double probeStep = Math.max(0.25, climbDistanceProbe.get());
        double cappedDistance = Math.max(2.0, Math.min(maxDistance, preferredDistance));

        for (double offsetDegrees : SAFE_HEADING_OFFSETS) {
            double radians = Math.toRadians(offsetDegrees);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);

            double rotatedX = (dirX * cos) - (dirZ * sin);
            double rotatedZ = (dirX * sin) + (dirZ * cos);

            for (int up = 1; up <= maxClimb; up++) {
                double y = baseY + up;

                double targetX = threat.getX() + (rotatedX * cappedDistance);
                double targetZ = threat.getZ() + (rotatedZ * cappedDistance);
                Vec3 direct = buildSafePosition(targetX, y, targetZ);
                if (direct != null) return direct;

                for (double back = probeStep; back <= Math.min(cappedDistance - 1.0, maxClimb * probeStep * 2.0); back += probeStep) {
                    double dist = cappedDistance - back;
                    if (dist < 2.0) break;

                    double x = threat.getX() + (rotatedX * dist);
                    double z = threat.getZ() + (rotatedZ * dist);
                    Vec3 stepped = buildSafePosition(x, y, z);
                    if (stepped != null) return stepped;
                }
            }
        }

        for (int up = 1; up <= maxClimb; up++) {
            Vec3 straightUp = buildSafePosition(mc.player.getX(), baseY + up, mc.player.getZ());
            if (straightUp != null) return straightUp;
        }

        return null;
    }

    private Vec3 findSafeForDistance(Player threat, double dirX, double dirZ, double distance, double baseY) {
        for (double offsetDegrees : SAFE_HEADING_OFFSETS) {
            double radians = Math.toRadians(offsetDegrees);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);

            double rotatedX = (dirX * cos) - (dirZ * sin);
            double rotatedZ = (dirX * sin) + (dirZ * cos);

            double targetX = threat.getX() + (rotatedX * distance);
            double targetZ = threat.getZ() + (rotatedZ * distance);

            Vec3 safe = findSafeYForXZ(targetX, targetZ, baseY);
            if (safe != null) return safe;
        }

        return null;
    }

    private Vec3 findSafeYForXZ(double x, double z, double baseY) {
        int verticalSearch = Math.max(0, safeVerticalSearch.get());

        Vec3 atBase = buildSafePosition(x, baseY, z);
        if (atBase != null) return atBase;

        for (int offset = 1; offset <= verticalSearch; offset++) {
            Vec3 above = buildSafePosition(x, baseY + offset, z);
            if (above != null) return above;

            Vec3 below = buildSafePosition(x, baseY - offset, z);
            if (below != null) return below;
        }

        return null;
    }

    private Vec3 buildSafePosition(double x, double y, double z) {
        if (isTeleportPositionSafe(x, y, z)) return new Vec3(x, y, z);
        return null;
    }

    private boolean isTeleportPositionSafe(double x, double y, double z) {
        if (mc.player == null || mc.level == null) return false;

        double minY = mc.level.getMinY() + 1.0;
        double maxY = mc.level.getMaxY() - 1.0;
        if (y < minY || y > maxY) return false;

        AABB movedBox = mc.player.getBoundingBox().move(
            x - mc.player.getX(),
            y - mc.player.getY(),
            z - mc.player.getZ()
        );

        return !mc.level.getBlockCollisions(mc.player, movedBox).iterator().hasNext();
    }

    private void ensureReachEnabled() {
        Modules modules = Modules.get();
        if (modules == null) return;
        Module reach = modules.get("reach");
        if (reach == null) reach = modules.get("Reach");

        if (reach != null && !reach.isActive()) reach.toggle();
    }

    private void ensureTimerBoost() {
        Modules modules = Modules.get();
        if (modules == null) return;
        Timer timer = modules.get(Timer.class);
        if (timer == null) return;

        if (!timer.isActive()) {
            timer.toggle();
            if (timer.isActive()) timerEnabledByOutOfReach = true;
        }

        timer.setOverride(Math.max(0.1, timerMultiplier.get()));
    }

    private void releaseTimerBoost(boolean disableTimerModule) {
        Modules modules = Modules.get();
        if (modules == null) return;
        Timer timer = modules.get(Timer.class);
        if (timer != null) {
            timer.setOverride(Timer.OFF);

            if (disableTimerModule && timerEnabledByOutOfReach && timer.isActive()) {
                timer.toggle();
            }
        }

        timerEnabledByOutOfReach = false;
    }

    @Override
    public String getInfoString() {
        if (lastThreat != null) return lastThreat.getName().getString();
        if (targetMode.get() == TargetMode.Selected && selectedTargetName != null) return "sel:" + selectedTargetName;
        return null;
    }
}
