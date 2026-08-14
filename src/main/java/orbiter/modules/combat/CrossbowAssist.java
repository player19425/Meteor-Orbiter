package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import orbiter.Orbiter;
import orbiter.util.ComboTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CrossbowAssist extends Module {

    public enum TargetMode { Closest, Crosshair, LowestHealth }
    public enum FireMode { Manual, Auto, Burst }
    public enum RenderMode { Off, Line, Both }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgAim = settings.createGroup("Aiming");
    private final SettingGroup sgPhysics = settings.createGroup("Projectile Physics");
    private final SettingGroup sgRender = settings.createGroup("Rendering");

    private final Setting<FireMode> fireMode = sgGeneral.add(new EnumSetting.Builder<FireMode>()
        .name("fire-mode")
        .description("Manual = only fire on key press. Auto = fire when aimed. Burst = fire repeatedly.")
        .defaultValue(FireMode.Auto)
        .build()
    );

    private final Setting<Boolean> onlyWhenLoaded = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-loaded")
        .description("Only assist when the crossbow is loaded and ready to fire.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoReload = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reload")
        .description("Automatically start charging after firing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> burstDelay = sgGeneral.add(new IntSetting.Builder()
        .name("burst-delay")
        .description("Ticks between shots in burst mode.")
        .defaultValue(10)
        .min(2)
        .sliderRange(2, 40)
        .visible(() -> fireMode.get() == FireMode.Burst)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .defaultValue(TargetMode.Closest)
        .build()
    );

    private final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
        .name("range")
        .defaultValue(80.0)
        .min(8.0)
        .sliderRange(8.0, 256.0)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
        .name("players-only")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisibles = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-invisibles")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreCreative = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> targetSwitchDelay = sgTarget.add(new DoubleSetting.Builder()
        .name("target-switch-delay")
        .defaultValue(0.3)
        .min(0.0)
        .sliderRange(0.0, 3.0)
        .build()
    );

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed")
        .defaultValue(0.6)
        .min(0.05)
        .sliderRange(0.05, 1.0)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgAim.add(new BoolSetting.Builder()
        .name("predict-movement")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictionSteps = sgAim.add(new IntSetting.Builder()
        .name("prediction-steps")
        .defaultValue(50)
        .min(10)
        .sliderRange(10, 150)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> aimAtHead = sgAim.add(new BoolSetting.Builder()
        .name("aim-at-head")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> boltGravity = sgPhysics.add(new DoubleSetting.Builder()
        .name("bolt-gravity")
        .description("Gravity per tick. Vanilla arrows: 0.05. Crossbow bolts use same values.")
        .defaultValue(0.05)
        .min(0.0)
        .sliderRange(0.0, 0.2)
        .build()
    );

    private final Setting<Double> boltDrag = sgPhysics.add(new DoubleSetting.Builder()
        .name("bolt-drag")
        .description("Air drag per tick. Vanilla: 0.99")
        .defaultValue(0.99)
        .min(0.9)
        .sliderRange(0.9, 1.0)
        .build()
    );

    private final Setting<Double> boltSpeed = sgPhysics.add(new DoubleSetting.Builder()
        .name("bolt-speed")
        .description("Initial bolt speed. Vanilla crossbow: 3.15 (slightly faster than bow's 3.0)")
        .defaultValue(3.15)
        .min(1.0)
        .sliderRange(1.0, 5.0)
        .build()
    );

    private final Setting<Integer> simSteps = sgPhysics.add(new IntSetting.Builder()
        .name("simulation-steps")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 300)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .defaultValue(RenderMode.Off)
        .build()
    );

    private LivingEntity currentTarget;
    private long lastTargetSwitchTime;
    private final List<Vec3d> trajectoryPoints = new ArrayList<>();
    private int tickCounter = 0;
    private int burstCounter = 0;

    private final Map<UUID, Vec3d> prevVelocities = new HashMap<>();
    private final Map<UUID, Vec3d> smoothedVelocities = new HashMap<>();
    private static final double VEL_SMOOTH_ALPHA = 0.3;

    private boolean hasFireworkLoaded = false;

    public CrossbowAssist() {
        super(Orbiter.CATEGORY_VANILLA, "crossbow-assist",
            "Aims a loaded crossbow with projectile physics, movement prediction, and auto-fire.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        lastTargetSwitchTime = 0;
        trajectoryPoints.clear();
        prevVelocities.clear();
        smoothedVelocities.clear();
        tickCounter = 0;
        burstCounter = 0;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        trajectoryPoints.clear();
        prevVelocities.clear();
        smoothedVelocities.clear();
        ComboTracker.clearAll();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;

        if (mc.player.getMainHandStack().getItem() != Items.CROSSBOW) {
            currentTarget = null;
            trajectoryPoints.clear();
            return;
        }

        trackVelocities();

        boolean isLoaded = isCrossbowLoaded();
        hasFireworkLoaded = isFireworkLoaded();

        if (onlyWhenLoaded.get() && !isLoaded) {

            currentTarget = null;
            trajectoryPoints.clear();
            return;
        }

        currentTarget = findOrMaintainTarget();
        if (currentTarget == null) {
            trajectoryPoints.clear();
            return;
        }

        Vec3d targetPos = getTargetPosition(currentTarget);
        Vec3d origin = mc.player.getEyePos();

        if (predictMovement.get()) {
            targetPos = predictTargetPosition(currentTarget, targetPos, origin);
        }

        AimSolution solution = solveAim(origin, targetPos);

        applyAim(solution.yaw, solution.pitch);

        if (renderMode.get() != RenderMode.Off) {
            simulateTrajectory(origin, solution.yaw, solution.pitch, trajectoryPoints);
        } else {
            trajectoryPoints.clear();
        }

        if (isLoaded) {
            handleFireLogic(solution);
        }
    }

    private void handleFireLogic(AimSolution solution) {

        float yawDiff = Math.abs(MathHelper.wrapDegrees(solution.yaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(solution.pitch - mc.player.getPitch());
        boolean aimedClose = yawDiff < 5.0f && pitchDiff < 5.0f;

        switch (fireMode.get()) {
            case Manual -> {

            }
            case Auto -> {
                if (aimedClose) {
                    fireCrossbow();
                }
            }
            case Burst -> {
                if (burstCounter > 0) {
                    burstCounter--;
                    return;
                }
                if (aimedClose) {
                    fireCrossbow();
                    burstCounter = burstDelay.get();
                }
            }
        }
    }

    private void fireCrossbow() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        mc.player.stopUsingItem();

        if (currentTarget != null) {
            ComboTracker.registerHit(currentTarget.getUuid());
        }

        if (autoReload.get()) {

        }
    }

    private boolean isCrossbowLoaded() {
        if (mc.player == null) return false;
        ChargedProjectilesComponent charged = mc.player.getMainHandStack()
            .get(DataComponentTypes.CHARGED_PROJECTILES);
        return charged != null && !charged.getProjectiles().isEmpty();
    }

    private boolean isFireworkLoaded() {
        if (mc.player == null) return false;
        ChargedProjectilesComponent charged = mc.player.getMainHandStack()
            .get(DataComponentTypes.CHARGED_PROJECTILES);
        if (charged == null || charged.getProjectiles().isEmpty()) return false;
        return charged.getProjectiles().get(0).getItem() == Items.FIREWORK_ROCKET;
    }

    private void trackVelocities() {
        Set<UUID> alive = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            UUID id = living.getUuid();
            alive.add(id);

            Vec3d currentVel = living.getVelocity();
            Vec3d prevVel = prevVelocities.get(id);

            if (prevVel != null) {

                Vec3d prevSmoothed = smoothedVelocities.getOrDefault(id, prevVel);
                Vec3d smoothed = new Vec3d(
                    prevSmoothed.x * (1 - VEL_SMOOTH_ALPHA) + currentVel.x * VEL_SMOOTH_ALPHA,
                    prevSmoothed.y * (1 - VEL_SMOOTH_ALPHA) + currentVel.y * VEL_SMOOTH_ALPHA,
                    prevSmoothed.z * (1 - VEL_SMOOTH_ALPHA) + currentVel.z * VEL_SMOOTH_ALPHA
                );
                smoothedVelocities.put(id, smoothed);
            }

            prevVelocities.put(id, currentVel);
        }

        prevVelocities.keySet().retainAll(alive);
        smoothedVelocities.keySet().retainAll(alive);
    }

    private LivingEntity findOrMaintainTarget() {
        if (currentTarget != null && isValidTarget(currentTarget) && isInRange(currentTarget)) {
            return currentTarget;
        }

        if (currentTarget != null) {
            currentTarget = null;
            lastTargetSwitchTime = tickCounter;
        }

        long delayTicks = (long) (targetSwitchDelay.get() * 20);
        if (tickCounter - lastTargetSwitchTime < delayTicks) return null;

        return findBestTarget();
    }

    private LivingEntity findBestTarget() {
        Vec3d eyes = mc.player.getEyePos();
        List<LivingEntity> candidates = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            if (!isInRange(living)) continue;
            if (!ignoreWalls.get() && !hasLineOfSight(eyes, living)) continue;
            candidates.add(living);
        }

        if (candidates.isEmpty()) return null;

        switch (targetMode.get()) {
            case Closest -> candidates.sort(Comparator.comparingDouble(e -> eyes.distanceTo(e.getBoundingBox().getCenter())));
            case Crosshair -> candidates.sort(Comparator.comparingDouble(e -> getAngleToEntity(e)));
            case LowestHealth -> candidates.sort(Comparator.comparingDouble(e -> e.getHealth()));
        }

        return candidates.get(0);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity == mc.player) return false;
        if (!entity.isAttackable()) return false;
        if (entity instanceof PlayerEntity p) {
            if (ignoreFriends.get() && Friends.get().isFriend(p)) return false;
            if (ignoreCreative.get() && p.getAbilities().creativeMode) return false;
        }
        if (playersOnly.get() && !(entity instanceof PlayerEntity)) return false;
        if (ignoreInvisibles.get() && entity.isInvisible()) return false;
        return true;
    }

    private boolean isInRange(LivingEntity entity) {
        return mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) <= range.get();
    }

    private double getAngleToEntity(LivingEntity entity) {
        Vec3d playerEyes = mc.player.getEyePos();
        Vec3d targetCenter = entity.getBoundingBox().getCenter();
        Vec3d diff = targetCenter.subtract(playerEyes).normalize();
        float yaw = mc.player.getYaw() * ((float) Math.PI / 180f);
        float pitch = mc.player.getPitch() * ((float) Math.PI / 180f);
        Vec3d look = new Vec3d(
            -MathHelper.sin(yaw) * MathHelper.cos(pitch),
            -MathHelper.sin(pitch),
            MathHelper.cos(yaw) * MathHelper.cos(pitch)
        ).normalize();
        double dot = look.dotProduct(diff);
        return Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
    }

    private boolean hasLineOfSight(Vec3d from, LivingEntity target) {
        Vec3d to = target.getBoundingBox().getCenter();
        if (mc.world == null) return true;
        var result = mc.world.raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }

    private Vec3d getTargetPosition(LivingEntity target) {
        if (aimAtHead.get()) {
            return new Vec3d(target.getX(), target.getEyeY(), target.getZ());
        }
        return target.getBoundingBox().getCenter();
    }

    private Vec3d predictTargetPosition(LivingEntity target, Vec3d currentPos, Vec3d origin) {
        Vec3d targetVel = smoothedVelocities.getOrDefault(target.getUuid(), target.getVelocity());
        if (targetVel.lengthSquared() < 0.001) return currentPos;

        double dist = origin.distanceTo(currentPos);
        double speed = hasFireworkLoaded ? 3.15 : boltSpeed.get();

        if (hasFireworkLoaded) speed = 1.6;

        double flightTime = dist / speed;
        flightTime = Math.min(flightTime, predictionSteps.get());

        Vec3d predicted = currentPos.add(targetVel.multiply(flightTime));
        if (mc.world != null && predicted.y < mc.world.getBottomY()) {
            predicted = new Vec3d(predicted.x, mc.world.getBottomY(), predicted.z);
        }
        return predicted;
    }

    private AimSolution solveAim(Vec3d origin, Vec3d target) {
        Vec3d delta = target.subtract(origin);
        double dx = delta.x;
        double dy = delta.y;
        double dz = delta.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);

        double speed = hasFireworkLoaded ? 1.6 : boltSpeed.get();
        double g = hasFireworkLoaded ? 0.0 : boltGravity.get();
        double v2 = speed * speed;

        float pitch;
        double disc = v2 * v2 - g * (g * horizontalDist * horizontalDist + 2 * dy * v2);

        if (disc < 0 || speed < 0.1) {

            pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        } else {
            double tanTheta = (v2 - Math.sqrt(disc)) / (g * horizontalDist);
            pitch = (float) -Math.toDegrees(Math.atan(tanTheta));

            pitch = refinePitchWithSimulation(origin, target, pitch, speed, g);
        }

        return new AimSolution(yaw, pitch);
    }

    private float refinePitchWithSimulation(Vec3d origin, Vec3d target, float initialPitch, double speed, double g) {
        float bestPitch = initialPitch;
        double bestError = simulateError(origin, target, bestPitch, speed, g);

        for (float p = initialPitch - 10.0f; p <= initialPitch + 10.0f; p += 2.0f) {
            double err = simulateError(origin, target, p, speed, g);
            if (err < bestError) { bestError = err; bestPitch = p; }
        }

        for (float p = bestPitch - 2.0f; p <= bestPitch + 2.0f; p += 0.25f) {
            double err = simulateError(origin, target, p, speed, g);
            if (err < bestError) { bestError = err; bestPitch = p; }
        }

        return bestPitch;
    }

    private double simulateError(Vec3d origin, Vec3d target, float pitch, double speed, double g) {
        Vec3d pos = origin;
        Vec3d vel = Vec3d.fromPolar(pitch, lastCalculatedYawForSim).multiply(speed);
        double bestDist = pos.squaredDistanceTo(target);
        int maxSteps = Math.min(simSteps.get(), 120);

        for (int i = 0; i < maxSteps; i++) {
            Vec3d next = pos.add(vel);
            double d = next.squaredDistanceTo(target);
            if (d < bestDist) bestDist = d;
            pos = next;
            vel = vel.multiply(boltDrag.get());
            vel = vel.add(0, -g, 0);
            if (pos.y < mc.world.getBottomY() - 4) break;
        }
        return bestDist;
    }

    private float lastCalculatedYawForSim;

    private void simulateTrajectory(Vec3d origin, float yaw, float pitch, List<Vec3d> out) {
        out.clear();
        lastCalculatedYawForSim = yaw;

        double speed = hasFireworkLoaded ? 1.6 : boltSpeed.get();
        double g = hasFireworkLoaded ? 0.0 : boltGravity.get();
        Vec3d pos = origin;
        Vec3d vel = Vec3d.fromPolar(pitch, yaw).multiply(speed);

        out.add(pos);

        int maxSteps = Math.min(simSteps.get(), 150);
        for (int i = 0; i < maxSteps; i++) {
            Vec3d next = pos.add(vel);

            if (mc.world != null) {
                var result = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (result.getType() == HitResult.Type.BLOCK) {
                    out.add(result.getPos());
                    break;
                }
            }

            out.add(next);
            pos = next;
            vel = vel.multiply(boltDrag.get());
            vel = vel.add(0, -g, 0);
            if (pos.y < mc.world.getBottomY() - 4) break;
        }
    }

    private void applyAim(float targetYaw, float targetPitch) {
        double speed = aimSpeed.get();
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        float newYaw = currentYaw + (float) (yawDelta * speed);
        float newPitch = currentPitch + (float) (pitchDelta * speed);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderMode.get() == RenderMode.Off || trajectoryPoints.isEmpty()) return;

        for (int i = 0; i < trajectoryPoints.size() - 1; i++) {
            Vec3d from = trajectoryPoints.get(i);
            Vec3d to = trajectoryPoints.get(i + 1);

            float t = (float) i / Math.max(1, trajectoryPoints.size() - 1);
            meteordevelopment.meteorclient.utils.render.color.Color c =
                new meteordevelopment.meteorclient.utils.render.color.Color(
                    (int)(t * 255), (int)((1.0f - t * 0.5f) * 255), 0, 200);

            if (renderMode.get() == RenderMode.Line || renderMode.get() == RenderMode.Both) {
                event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, c);
            }

            if (renderMode.get() == RenderMode.Both && i % 5 == 0) {
                event.renderer.line(from.x - 0.1, from.y, from.z, from.x + 0.1, from.y, from.z, c);
                event.renderer.line(from.x, from.y - 0.1, from.z, from.x, from.y + 0.1, from.z, c);
                event.renderer.line(from.x, from.y, from.z - 0.1, from.x, from.y, from.z + 0.1, c);
            }
        }

        if (!trajectoryPoints.isEmpty()) {
            Vec3d end = trajectoryPoints.get(trajectoryPoints.size() - 1);
            meteordevelopment.meteorclient.utils.render.color.Color red =
                new meteordevelopment.meteorclient.utils.render.color.Color(255, 0, 0, 255);
            event.renderer.line(end.x - 0.3, end.y, end.z, end.x + 0.3, end.y, end.z, red);
            event.renderer.line(end.x, end.y - 0.3, end.z, end.x, end.y + 0.3, end.z, red);
            event.renderer.line(end.x, end.y, end.z - 0.3, end.x, end.y, end.z + 0.3, red);
        }
    }

    private record AimSolution(float yaw, float pitch) {}

    @Override
    public String getInfoString() {
        if (mc.player == null) return "Off";
        boolean loaded = isCrossbowLoaded();
        if (!loaded) return "Not loaded";
        if (currentTarget == null) return "Loaded • no target";

        String name = currentTarget instanceof PlayerEntity p
            ? p.getName().getString()
            : currentTarget.getName().getString();

        int combo = ComboTracker.getCombo(currentTarget.getUuid());
        String comboStr = combo > 0 ? " | combo: " + combo : "";
        return "\u2192 " + name + comboStr;
    }
}
