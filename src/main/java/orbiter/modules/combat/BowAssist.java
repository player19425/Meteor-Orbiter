package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import orbiter.Orbiter;
import orbiter.util.ComboTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class BowAssist extends Module {

    public enum TargetMode { Closest, Crosshair, LowestHealth, HighestHealth }
    public enum AimMode { Visible, Silent }
    public enum AimStrategy { Direct, Ballistic, Auto }
    public enum RenderMode { Off, Line, Points, Both }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgAim = settings.createGroup("Aiming");
    private final SettingGroup sgPhysics = settings.createGroup("Projectile Physics");
    private final SettingGroup sgRender = settings.createGroup("Rendering");
    private final SettingGroup sgCombo = settings.createGroup("Combo");

    private final Setting<Boolean> autoFire = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-fire")
        .description("Automatically release the bow when fully charged and aimed at the target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minChargePercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-charge-percent")
        .description("Minimum charge percentage (0.0 - 1.0) required before firing or aiming.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Boolean> onlyWhenDrawing = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-drawing")
        .description("Only assist while actively drawing the bow (right-click held).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireArrows = sgGeneral.add(new BoolSetting.Builder()
        .name("require-arrows")
        .description("Only activate when arrows are present in the inventory.")
        .defaultValue(true)
        .visible(onlyWhenDrawing::get)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How to select the target entity.")
        .defaultValue(TargetMode.Closest)
        .build()
    );

    private final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum targeting distance in blocks.")
        .defaultValue(80.0)
        .min(8.0)
        .sliderRange(8.0, 256.0)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
        .name("players-only")
        .description("Only target player entities.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTarget.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entity types to target. Empty targets all living entities.")
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Skip friends when selecting targets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisibles = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-invisibles")
        .description("Skip invisible entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Target through walls without requiring line of sight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreCreative = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .description("Skip players in creative mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> targetSwitchDelay = sgTarget.add(new DoubleSetting.Builder()
        .name("target-switch-delay")
        .description("Seconds before switching to a new target after the current one dies or leaves range.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderRange(0.0, 3.0)
        .build()
    );

    private final Setting<AimMode> aimMode = sgAim.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .description("Visible = rotate client view. Silent = server-side only (anticheat risk).")
        .defaultValue(AimMode.Visible)
        .build()
    );

    private final Setting<AimStrategy> aimStrategy = sgAim.add(new EnumSetting.Builder<AimStrategy>()
        .name("aim-strategy")
        .description("Direct = straight line aim. Ballistic = accounts for gravity and drag. Auto = picks based on distance.")
        .defaultValue(AimStrategy.Auto)
        .build()
    );

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed")
        .description("How fast the aim moves toward the target. 1.0 = instant, 0.1 = very smooth.")
        .defaultValue(0.5)
        .min(0.05)
        .sliderRange(0.05, 1.0)
        .build()
    );

    private final Setting<Double> aimSpeedCharged = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed-charged")
        .description("Aim speed when bow is at critical charge (higher = snap to target faster).")
        .defaultValue(0.8)
        .min(0.05)
        .sliderRange(0.05, 1.0)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgAim.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Lead the target based on its velocity vector.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictionSteps = sgAim.add(new IntSetting.Builder()
        .name("prediction-steps")
        .description("Number of simulation steps for movement prediction. Higher = more accurate but slower.")
        .defaultValue(60)
        .min(10)
        .sliderRange(10, 200)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> aimAtHead = sgAim.add(new BoolSetting.Builder()
        .name("aim-at-head")
        .description("Aim at the target's head (eye height) instead of body center.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> arrowGravity = sgPhysics.add(new DoubleSetting.Builder()
        .name("arrow-gravity")
        .description("Gravity applied to arrows per tick. Vanilla: 0.05")
        .defaultValue(0.05)
        .min(0.0)
        .sliderRange(0.0, 0.2)
        .build()
    );

    private final Setting<Double> arrowDrag = sgPhysics.add(new DoubleSetting.Builder()
        .name("arrow-drag")
        .description("Air drag factor applied to arrows per tick. Vanilla: 0.99")
        .defaultValue(0.99)
        .min(0.9)
        .sliderRange(0.9, 1.0)
        .build()
    );

    private final Setting<Double> arrowSpeedMult = sgPhysics.add(new DoubleSetting.Builder()
        .name("arrow-speed-mult")
        .description("Arrow initial speed multiplier. Vanilla: 3.0 * charge")
        .defaultValue(3.0)
        .min(1.0)
        .sliderRange(1.0, 5.0)
        .build()
    );

    private final Setting<Integer> simSteps = sgPhysics.add(new IntSetting.Builder()
        .name("simulation-steps")
        .description("Max ticks to simulate the arrow trajectory for ballistic solving.")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 300)
        .build()
    );

    private final Setting<Integer> simRaycastInterval = sgPhysics.add(new IntSetting.Builder()
        .name("raycast-interval")
        .description("Check for block collisions every N steps during simulation. 0 = no collision check.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("How to render the predicted trajectory.")
        .defaultValue(RenderMode.Off)
        .build()
    );

    private final Setting<Double> renderLineThickness = sgRender.add(new DoubleSetting.Builder()
        .name("line-thickness")
        .description("Thickness of the trajectory line.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 5.0)
        .visible(() -> renderMode.get() == RenderMode.Line || renderMode.get() == RenderMode.Both)
        .build()
    );

    private final Setting<Boolean> trackCombo = sgCombo.add(new BoolSetting.Builder()
        .name("track-combo")
        .description("Track consecutive hits on the same target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> comboTimeout = sgCombo.add(new IntSetting.Builder()
        .name("combo-timeout")
        .description("Ticks before combo resets if no new hit is registered.")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 400)
        .visible(trackCombo::get)
        .build()
    );

    private LivingEntity currentTarget;
    private LivingEntity lastTarget;
    private long lastTargetSwitchTime;
    private final List<Vec3> trajectoryPoints = new ArrayList<>();
    private final ComboTracker comboTracker = new ComboTracker();
    private int tickCounter = 0;

    private float lastCalculatedYaw;
    private float lastCalculatedPitch;
    private float currentCharge;

    public BowAssist() {
        super(Orbiter.CATEGORY_VANILLA, "bow-assist",
            "Auto-aims the bow.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        lastTarget = null;
        lastTargetSwitchTime = 0;
        trajectoryPoints.clear();
        ComboTracker.clearAll();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        lastTarget = null;
        trajectoryPoints.clear();
        ComboTracker.clearAll();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        tickCounter++;

        if (!(mc.player.getMainHandItem().getItem() instanceof BowItem)) {
            currentTarget = null;
            trajectoryPoints.clear();
            return;
        }

        boolean isDrawing = mc.player.isUsingItem();
        if (onlyWhenDrawing.get() && !isDrawing) {
            currentTarget = null;
            trajectoryPoints.clear();
            return;
        }

        if (requireArrows.get() && !hasArrows()) {
            currentTarget = null;
            return;
        }

        int maxUseTime = mc.player.getMainHandItem().getUseDuration(mc.player);
        int elapsedTicks = isDrawing ? (maxUseTime - mc.player.getUseItemRemainingTicks()) : 0;
        currentCharge = BowItem.getPowerForTime(elapsedTicks);

        if (currentCharge < minChargePercent.get().floatValue()) {

            if (isDrawing) {
                currentTarget = findBestTarget();
            }
            return;
        }

        currentTarget = findOrMaintainTarget();
        if (currentTarget == null) {
            trajectoryPoints.clear();
            return;
        }

        Vec3 targetPos = getTargetPosition(currentTarget);
        Vec3 origin = mc.player.getEyePosition();

        if (predictMovement.get()) {
            targetPos = predictTargetPosition(currentTarget, targetPos, origin);
        }

        AimSolution solution = solveAim(origin, targetPos, currentCharge);

        lastCalculatedYaw = solution.yaw;
        lastCalculatedPitch = solution.pitch;

        applyAim(solution.yaw, solution.pitch, currentCharge);

        if (renderMode.get() != RenderMode.Off) {
            simulateTrajectory(origin, solution.yaw, solution.pitch, currentCharge, trajectoryPoints);
        } else {
            trajectoryPoints.clear();
        }

        if (autoFire.get() && isDrawing && currentCharge >= 0.99f) {

            float yawDiff = Math.abs(Mth.wrapDegrees(solution.yaw - mc.player.getYRot()));
            float pitchDiff = Math.abs(solution.pitch - mc.player.getXRot());
            if (yawDiff < 5.0f && pitchDiff < 5.0f) {
                mc.player.stopUsingItem();
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderMode.get() == RenderMode.Off || trajectoryPoints.isEmpty()) return;
        if (mc.player == null || mc.level == null) return;

        renderTrajectory(event);
    }

    private LivingEntity findOrMaintainTarget() {

        if (currentTarget != null && isValidTarget(currentTarget) && isInRange(currentTarget)) {
            return currentTarget;
        }

        if (currentTarget != null) {
            lastTarget = currentTarget;
            currentTarget = null;
            lastTargetSwitchTime = tickCounter;
        }

        long delayTicks = (long) (targetSwitchDelay.get() * 20);
        if (tickCounter - lastTargetSwitchTime < delayTicks) {
            return null;
        }

        return findBestTarget();
    }

    private LivingEntity findBestTarget() {
        Vec3 eyes = mc.player.getEyePosition();
        List<LivingEntity> candidates = new ArrayList<>();

        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;
            if (!isInRange(living)) continue;

            if (!ignoreWalls.get() && !hasLineOfSight(eyes, living)) {
                continue;
            }

            candidates.add(living);
        }

        if (candidates.isEmpty()) return null;

        switch (targetMode.get()) {
            case Closest -> candidates.sort(Comparator.comparingDouble(e -> eyes.distanceTo(e.getBoundingBox().getCenter())));
            case Crosshair -> candidates.sort(Comparator.comparingDouble(e -> getAngleToEntity(e)));
            case LowestHealth -> candidates.sort(Comparator.comparingDouble(e -> e.getHealth()));
            case HighestHealth -> candidates.sort(Comparator.comparingDouble(e -> -e.getHealth()));
        }

        return candidates.get(0);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity == mc.player) return false;
        if (!entity.isAttackable()) return false;

        if (entity instanceof Player player) {
            if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
            if (ignoreCreative.get() && player.getAbilities().instabuild) return false;
            if (player.isSpectator()) return false;
        }

        if (playersOnly.get() && !(entity instanceof Player)) return false;
        if (!entities.get().isEmpty() && !entities.get().contains(entity.getType())) return false;
        if (ignoreInvisibles.get() && entity.isInvisible()) return false;

        return true;
    }

    private boolean isInRange(LivingEntity entity) {
        double dist = mc.player.getEyePosition().distanceTo(entity.getBoundingBox().getCenter());
        return dist <= range.get();
    }

    private double getAngleToEntity(LivingEntity entity) {
        Vec3 playerEyes = mc.player.getEyePosition();
        Vec3 targetCenter = entity.getBoundingBox().getCenter();
        Vec3 diff = targetCenter.subtract(playerEyes).normalize();

        float yaw = mc.player.getYRot() * ((float) Math.PI / 180f);
        float pitch = mc.player.getXRot() * ((float) Math.PI / 180f);

        Vec3 look = new Vec3(
            -Mth.sin(yaw) * Mth.cos(pitch),
            -Mth.sin(pitch),
            Mth.cos(yaw) * Mth.cos(pitch)
        ).normalize();

        double dot = look.dot(diff);
        return Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
    }

    private boolean hasLineOfSight(Vec3 from, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        if (mc.level == null) return true;
        var result = mc.level.clip(new ClipContext(
            from, to,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    private Vec3 getTargetPosition(LivingEntity target) {
        if (aimAtHead.get()) {

            return new Vec3(target.getX(), target.getEyeY(), target.getZ());
        } else {

            return target.getBoundingBox().getCenter();
        }
    }

    private Vec3 predictTargetPosition(LivingEntity target, Vec3 currentPos, Vec3 origin) {
        Vec3 targetVel = target.getDeltaMovement();
        if (targetVel.lengthSqr() < 0.001) return currentPos;

        double dist = origin.distanceTo(currentPos);
        double arrowSpeed = arrowSpeedMult.get() * currentCharge;
        if (arrowSpeed < 0.1) return currentPos;

        double flightTime = dist / arrowSpeed;

        flightTime = Math.min(flightTime, predictionSteps.get());

        Vec3 predicted = currentPos.add(targetVel.scale(flightTime));

        if (mc.level != null && predicted.y < mc.level.getMinY()) {
            predicted = new Vec3(predicted.x, mc.level.getMinY(), predicted.z);
        }

        return predicted;
    }

    private AimSolution solveAim(Vec3 origin, Vec3 target, float charge) {
        Vec3 delta = target.subtract(origin);
        double dx = delta.x;
        double dy = delta.y;
        double dz = delta.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);

        AimStrategy strategy = aimStrategy.get();
        if (strategy == AimStrategy.Auto) {

            if (horizontalDist < 15.0) {
                strategy = AimStrategy.Direct;
            } else {
                strategy = AimStrategy.Ballistic;
            }
        }

        float pitch;
        if (strategy == AimStrategy.Direct) {

            pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        } else {

            pitch = solveBallisticPitch(origin, target, charge, horizontalDist, dy);
        }

        return new AimSolution(yaw, pitch);
    }

    private float solveBallisticPitch(Vec3 origin, Vec3 target, float charge, double horizontalDist, double dy) {
        double speed = arrowSpeedMult.get() * charge;
        double g = arrowGravity.get();
        double drag = arrowDrag.get();

        double v2 = speed * speed;
        double disc = v2 * v2 - g * (g * horizontalDist * horizontalDist + 2 * dy * v2);

        if (disc >= 0 && speed > 0.1) {

            double tanTheta = (v2 - Math.sqrt(disc)) / (g * horizontalDist);
            float analyticPitch = (float) -Math.toDegrees(Math.atan(tanTheta));

            return refinePitchWithSimulation(origin, target, charge, analyticPitch);
        }

        return bruteForcePitch(origin, target, charge);
    }

    private float refinePitchWithSimulation(Vec3 origin, Vec3 target, float charge, float initialPitch) {

        float bestPitch = initialPitch;
        double bestError = simulateError(origin, target, charge, bestPitch);

        float searchRange = 10.0f;
        float step = 2.0f;

        for (float p = initialPitch - searchRange; p <= initialPitch + searchRange; p += step) {
            double err = simulateError(origin, target, charge, p);
            if (err < bestError) {
                bestError = err;
                bestPitch = p;
            }
        }

        step = 0.25f;
        for (float p = bestPitch - 2.0f; p <= bestPitch + 2.0f; p += step) {
            double err = simulateError(origin, target, charge, p);
            if (err < bestError) {
                bestError = err;
                bestPitch = p;
            }
        }

        return bestPitch;
    }

    private float bruteForcePitch(Vec3 origin, Vec3 target, float charge) {
        float bestPitch = -45.0f;
        double bestError = Double.MAX_VALUE;

        for (float p = -89.0f; p <= 89.0f; p += 2.0f) {
            double err = simulateError(origin, target, charge, p);
            if (err < bestError) {
                bestError = err;
                bestPitch = p;
            }
        }

        for (float p = bestPitch - 2.0f; p <= bestPitch + 2.0f; p += 0.25f) {
            double err = simulateError(origin, target, charge, p);
            if (err < bestError) {
                bestError = err;
                bestPitch = p;
            }
        }

        return bestPitch;
    }

    private double simulateError(Vec3 origin, Vec3 target, float charge, float pitch) {

        double speed = arrowSpeedMult.get() * charge;
        Vec3 pos = origin;
        Vec3 vel = Vec3.directionFromRotation(pitch, lastCalculatedYaw).scale(speed);

        double bestDist = pos.distanceToSqr(target);
        int maxSteps = Math.min(simSteps.get(), 120);
        int raycastInterval = simRaycastInterval.get();

        for (int i = 0; i < maxSteps; i++) {
            Vec3 next = pos.add(vel);

            if (raycastInterval > 0 && i % raycastInterval == 0 && mc.level != null) {
                var result = mc.level.clip(new ClipContext(
                    pos, next,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
                ));
                if (result.getType() == HitResult.Type.BLOCK) {
                    double d = result.getLocation().distanceToSqr(target);
                    if (d < bestDist) bestDist = d;
                    break;
                }
            }

            double d = next.distanceToSqr(target);
            if (d < bestDist) bestDist = d;

            pos = next;
            vel = vel.scale(arrowDrag.get());
            vel = vel.add(0, -arrowGravity.get(), 0);

            if (pos.y < mc.level.getMinY() - 4) break;
        }

        return bestDist;
    }

    private void simulateTrajectory(Vec3 origin, float yaw, float pitch, float charge, List<Vec3> out) {
        out.clear();
        double speed = arrowSpeedMult.get() * charge;
        Vec3 pos = origin;
        Vec3 vel = Vec3.directionFromRotation(pitch, yaw).scale(speed);

        out.add(pos);

        int maxSteps = Math.min(simSteps.get(), 150);
        for (int i = 0; i < maxSteps; i++) {
            Vec3 next = pos.add(vel);

            if (mc.level != null) {
                var result = mc.level.clip(new ClipContext(
                    pos, next,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
                ));
                if (result.getType() == HitResult.Type.BLOCK) {
                    out.add(result.getLocation());
                    break;
                }
            }

            out.add(next);
            pos = next;
            vel = vel.scale(arrowDrag.get());
            vel = vel.add(0, -arrowGravity.get(), 0);

            if (pos.y < mc.level.getMinY() - 4) break;
        }
    }

    private void applyAim(float targetYaw, float targetPitch, float charge) {

        double speed = charge >= 0.99f ? aimSpeedCharged.get() : aimSpeed.get();

        if (aimMode.get() == AimMode.Visible) {
            float currentYaw = mc.player.getYRot();
            float currentPitch = mc.player.getXRot();

            float yawDelta = Mth.wrapDegrees(targetYaw - currentYaw);
            float pitchDelta = targetPitch - currentPitch;

            float newYaw = currentYaw + (float) (yawDelta * speed);
            float newPitch = currentPitch + (float) (pitchDelta * speed);

            mc.player.setYRot(newYaw);
            mc.player.setXRot(newPitch);
        } else {

            Rotations.rotate(targetYaw, targetPitch, (int) (20 / speed), false, () -> {});
        }
    }

    private void renderTrajectory(Render3DEvent event) {
        if (trajectoryPoints.size() < 2) return;

        for (int i = 0; i < trajectoryPoints.size() - 1; i++) {
            Vec3 from = trajectoryPoints.get(i);
            Vec3 to = trajectoryPoints.get(i + 1);

            float[] color = getTrajectoryColor(i, trajectoryPoints.size());
            meteordevelopment.meteorclient.utils.render.color.Color c =
                new meteordevelopment.meteorclient.utils.render.color.Color(
                    (int)(color[0] * 255), (int)(color[1] * 255), (int)(color[2] * 255), (int)(color[3] * 255));

            if (renderMode.get() == RenderMode.Line || renderMode.get() == RenderMode.Both) {
                event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, c);
            }

            if (renderMode.get() == RenderMode.Points || renderMode.get() == RenderMode.Both) {
                if (i % 5 == 0) {
                    event.renderer.line(from.x - 0.1, from.y, from.z, from.x + 0.1, from.y, from.z, c);
                    event.renderer.line(from.x, from.y - 0.1, from.z, from.x, from.y + 0.1, from.z, c);
                    event.renderer.line(from.x, from.y, from.z - 0.1, from.x, from.y, from.z + 0.1, c);
                }
            }
        }

        if (!trajectoryPoints.isEmpty()) {
            Vec3 end = trajectoryPoints.get(trajectoryPoints.size() - 1);
            meteordevelopment.meteorclient.utils.render.color.Color red =
                new meteordevelopment.meteorclient.utils.render.color.Color(255, 0, 0, 255);
            event.renderer.line(end.x - 0.3, end.y, end.z, end.x + 0.3, end.y, end.z, red);
            event.renderer.line(end.x, end.y - 0.3, end.z, end.x, end.y + 0.3, end.z, red);
            event.renderer.line(end.x, end.y, end.z - 0.3, end.x, end.y, end.z + 0.3, red);
        }
    }

    private float[] getTrajectoryColor(int index, int total) {

        float t = (float) index / Math.max(1, total - 1);
        float r = t;
        float g = 1.0f - t * 0.5f;
        float b = 0.0f;
        return new float[]{r, g, b, 0.8f};
    }

    private boolean hasArrows() {
        if (mc.player == null) return false;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.ARROW) return true;
            if (mc.player.getInventory().getItem(i).getItem() == Items.TIPPED_ARROW) return true;
            if (mc.player.getInventory().getItem(i).getItem() == Items.SPECTRAL_ARROW) return true;
        }

        if (mc.player.getOffhandItem().getItem() == Items.ARROW) return true;
        if (mc.player.getOffhandItem().getItem() == Items.TIPPED_ARROW) return true;
        if (mc.player.getOffhandItem().getItem() == Items.SPECTRAL_ARROW) return true;

        var enchantments = mc.player.getMainHandItem().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                if (entry.getKey().unwrapKey().orElseThrow().identifier().getPath().equals("infinity")) {
                    return true;
                }
            }
        }

        return false;
    }

    public void onHitEntity(LivingEntity target) {
        if (!trackCombo.get()) return;
        ComboTracker.registerHit(target.getUUID());
    }

    public int getCurrentCombo() {
        if (!trackCombo.get() || currentTarget == null) return 0;
        return ComboTracker.getCombo(currentTarget.getUUID());
    }

    private record AimSolution(float yaw, float pitch) {}

    @Override
    public String getInfoString() {
        if (currentTarget == null) return "No target";

        StringBuilder sb = new StringBuilder();
        String name = currentTarget instanceof Player p
            ? p.getName().getString()
            : currentTarget.getName().getString();
        sb.append("\u2192 ").append(name);

        if (currentCharge > 0) {
            sb.append(String.format(" | %d%%", (int)(currentCharge * 100)));
        }

        if (trackCombo.get() && currentTarget != null && ComboTracker.getCombo(currentTarget.getUUID()) > 0) {
            sb.append(" | combo: ").append(getCurrentCombo());
        }

        return sb.toString();
    }
}
