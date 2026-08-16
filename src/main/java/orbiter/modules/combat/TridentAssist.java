package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.ItemStack;
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

public class TridentAssist extends Module {

    public enum TargetMode { Closest, Crosshair, LowestHealth }
    public enum AimMode { Visible, Silent }
    public enum ThrowMode { Manual, Auto, AutoMelee }
    public enum RenderMode { Off, Line, Both }
    public enum EnchantMode { Off, Detect, Respect }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgAim = settings.createGroup("Aiming");
    private final SettingGroup sgPhysics = settings.createGroup("Projectile Physics");
    private final SettingGroup sgEnchant = settings.createGroup("Enchantments");
    private final SettingGroup sgRender = settings.createGroup("Rendering");
    private final SettingGroup sgMelee = settings.createGroup("Melee Mode");

    private final Setting<ThrowMode> throwMode = sgGeneral.add(new EnumSetting.Builder<ThrowMode>()
        .name("throw-mode")
        .description("Manual = throw on key release. Auto = throw when charged and aimed. AutoMelee = melee when close, throw when far.")
        .defaultValue(ThrowMode.Auto)
        .build()
    );

    private final Setting<Double> minChargePercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-charge-percent")
        .description("Minimum charge (0.0-1.0) before throwing.")
        .defaultValue(0.8)
        .min(0.1)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private final Setting<Boolean> onlyWhenDrawing = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-drawing")
        .description("Only assist while charging the trident (right-click held).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> preventThrowAtLowDurability = sgGeneral.add(new BoolSetting.Builder()
        .name("prevent-low-durability-throw")
        .description("Don't throw if trident has 1 durability or less (it would be lost).")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .defaultValue(TargetMode.Closest)
        .build()
    );

    private final Setting<Double> range = sgTarget.add(new DoubleSetting.Builder()
        .name("range")
        .description("Max targeting distance for thrown trident.")
        .defaultValue(60.0)
        .min(8.0)
        .sliderRange(8.0, 256.0)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgTarget.add(new BoolSetting.Builder()
        .name("players-only")
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

    private final Setting<AimMode> aimMode = sgAim.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .defaultValue(AimMode.Visible)
        .build()
    );

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed")
        .defaultValue(0.5)
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
        .defaultValue(40)
        .min(10)
        .sliderRange(10, 100)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> aimAtHead = sgAim.add(new BoolSetting.Builder()
        .name("aim-at-head")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> tridentGravity = sgPhysics.add(new DoubleSetting.Builder()
        .name("trident-gravity")
        .description("Gravity per tick. Vanilla: 0.05 (same as arrows)")
        .defaultValue(0.05)
        .min(0.0)
        .sliderRange(0.0, 0.2)
        .build()
    );

    private final Setting<Double> tridentDrag = sgPhysics.add(new DoubleSetting.Builder()
        .name("trident-drag")
        .description("Air drag per tick. Vanilla: 0.99 (same as arrows)")
        .defaultValue(0.99)
        .min(0.9)
        .sliderRange(0.9, 1.0)
        .build()
    );

    private final Setting<Double> tridentSpeed = sgPhysics.add(new DoubleSetting.Builder()
        .name("trident-speed")
        .description("Initial trident speed. Vanilla: 2.0 * charge * 1.0 = 2.0 at full charge")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 5.0)
        .build()
    );

    private final Setting<Integer> simSteps = sgPhysics.add(new IntSetting.Builder()
        .name("simulation-steps")
        .defaultValue(80)
        .min(20)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Boolean> waterNoDrag = sgPhysics.add(new BoolSetting.Builder()
        .name("water-no-drag")
        .description("Tridents don't slow in water (vanilla behavior).")
        .defaultValue(true)
        .build()
    );

    private final Setting<EnchantMode> enchantMode = sgEnchant.add(new EnumSetting.Builder<EnchantMode>()
        .name("enchant-mode")
        .description("Off = ignore enchantments. Detect = detect and log. Respect = don't throw if Riptide active.")
        .defaultValue(EnchantMode.Respect)
        .build()
    );

    private final Setting<Boolean> respectRiptide = sgEnchant.add(new BoolSetting.Builder()
        .name("respect-riptide")
        .description("Don't throw if trident has Riptide (can't be thrown, launches player instead).")
        .defaultValue(true)
        .visible(() -> enchantMode.get() != EnchantMode.Off)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .defaultValue(RenderMode.Off)
        .build()
    );

    private final Setting<Boolean> meleeWhenClose = sgMelee.add(new BoolSetting.Builder()
        .name("melee-when-close")
        .description("Switch to melee attack when target is within 3 blocks (trident melee range).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> meleeRange = sgMelee.add(new DoubleSetting.Builder()
        .name("melee-range")
        .description("Range at which to switch from ranged to melee.")
        .defaultValue(3.0)
        .min(1.0)
        .sliderRange(1.0, 6.0)
        .visible(meleeWhenClose::get)
        .build()
    );

    private final Setting<Boolean> ignoreCooldown = sgMelee.add(new BoolSetting.Builder()
        .name("ignore-cooldown")
        .description("Attack without waiting for cooldown bar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> critOnly = sgMelee.add(new BoolSetting.Builder()
        .name("crit-only")
        .description("Only melee attack when a critical hit is possible (falling + not on ground).")
        .defaultValue(false)
        .build()
    );

    private LivingEntity currentTarget;
    private long lastTargetSwitchTime;
    private final List<Vec3> trajectoryPoints = new ArrayList<>();
    private int tickCounter = 0;
    private int attackCooldown = 0;
    private float currentCharge;
    private float lastCalculatedYaw;
    private float lastCalculatedPitch;

    private boolean hasRiptide = false;
    private boolean hasLoyalty = false;
    private boolean hasChanneling = false;
    private int impalingLevel = 0;

    public TridentAssist() {
        super(Orbiter.CATEGORY_VANILLA, "trident-assist",
            "Aims and throws the trident with projectile physics, enchantment detection, and melee fallback.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        lastTargetSwitchTime = 0;
        trajectoryPoints.clear();
        tickCounter = 0;
        attackCooldown = 0;
        currentCharge = 0;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        trajectoryPoints.clear();
        ComboTracker.clearAll();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        tickCounter++;
        if (attackCooldown > 0) attackCooldown--;

        if (!(mc.player.getMainHandItem().getItem() instanceof TridentItem)) {
            currentTarget = null;
            trajectoryPoints.clear();
            return;
        }

        detectEnchantments();

        boolean canThrow = true;
        if (respectRiptide.get() && hasRiptide) {

            canThrow = false;
        }

        if (preventThrowAtLowDurability.get()) {
            ItemStack trident = mc.player.getMainHandItem();
            int maxDamage = trident.getMaxDamage();
            int damage = trident.getDamageValue();
            int remaining = maxDamage - damage;
            if (remaining <= 1) {
                canThrow = false;
            }
        }

        boolean isDrawing = mc.player.isUsingItem();
        if (onlyWhenDrawing.get() && !isDrawing && canThrow) {
            currentTarget = null;
            trajectoryPoints.clear();
            return;
        }

        currentTarget = findOrMaintainTarget();
        if (currentTarget == null) {
            trajectoryPoints.clear();
            return;
        }

        double distToTarget = mc.player.getEyePosition().distanceTo(currentTarget.getBoundingBox().getCenter());

        if (meleeWhenClose.get() && distToTarget <= meleeRange.get()) {
            handleMeleeAttack(currentTarget);
            trajectoryPoints.clear();
            return;
        }

        if (!canThrow) {

            handleMeleeAttack(currentTarget);
            return;
        }

        int maxUseTime = mc.player.getMainHandItem().getUseDuration(mc.player);
        int elapsedTicks = isDrawing ? (maxUseTime - mc.player.getUseItemRemainingTicks()) : 0;
        currentCharge = (float) elapsedTicks / 10.0f;
        currentCharge = Math.min(currentCharge, 1.0f);

        if (currentCharge < minChargePercent.get()) {

            return;
        }

        Vec3 targetPos = getTargetPosition(currentTarget);
        Vec3 origin = mc.player.getEyePosition();

        if (predictMovement.get()) {
            targetPos = predictTargetPosition(currentTarget, targetPos, origin);
        }

        AimSolution solution = solveAim(origin, targetPos);
        lastCalculatedYaw = solution.yaw;
        lastCalculatedPitch = solution.pitch;

        applyAim(solution.yaw, solution.pitch);

        if (renderMode.get() != RenderMode.Off) {
            simulateTrajectory(origin, solution.yaw, solution.pitch, trajectoryPoints);
        } else {
            trajectoryPoints.clear();
        }

        if (isDrawing && currentCharge >= minChargePercent.get().floatValue()) {
            switch (throwMode.get()) {
                case Auto -> {
                    if (currentCharge >= 0.95f) {
                        float yawDiff = Math.abs(Mth.wrapDegrees(solution.yaw - mc.player.getYRot()));
                        float pitchDiff = Math.abs(solution.pitch - mc.player.getXRot());
                        if (yawDiff < 8.0f && pitchDiff < 8.0f) {
                            mc.player.stopUsingItem();
                            ComboTracker.registerHit(currentTarget.getUUID());
                        }
                    }
                }
                case AutoMelee -> {

                }
                case Manual -> {

                }
            }
        }
    }

    private void handleMeleeAttack(LivingEntity target) {
        if (attackCooldown > 0) return;
        if (!ignoreCooldown.get() && mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;

        boolean canCrit = !mc.player.onGround() && mc.player.getDeltaMovement().y < -0.08;
        if (critOnly.get() && !canCrit) return;

        if (mc.gameMode != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            ComboTracker.registerHit(target.getUUID());
            attackCooldown = 12;
        }
    }

    private void detectEnchantments() {
        hasRiptide = false;
        hasLoyalty = false;
        hasChanneling = false;
        impalingLevel = 0;

        if (enchantMode.get() == EnchantMode.Off) return;

        ItemStack trident = mc.player.getMainHandItem();
        var enchantments = trident.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return;

        for (var entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey().orElseThrow().identifier().getPath();
            int level = enchantments.getLevel(entry.getKey());
            switch (id) {
                case "riptide" -> hasRiptide = true;
                case "loyalty" -> hasLoyalty = true;
                case "channeling" -> hasChanneling = true;
                case "impaling" -> impalingLevel = level;
            }
        }
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
        Vec3 eyes = mc.player.getEyePosition();
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
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
        if (entity instanceof Player p) {
            if (ignoreFriends.get() && Friends.get().isFriend(p)) return false;
            if (ignoreCreative.get() && p.getAbilities().instabuild) return false;
        }
        if (playersOnly.get() && !(entity instanceof Player)) return false;
        if (!entities.get().isEmpty() && !entities.get().contains(entity.getType())) return false;
        if (ignoreInvisibles.get() && entity.isInvisible()) return false;
        return true;
    }

    private boolean isInRange(LivingEntity entity) {
        return mc.player.getEyePosition().distanceTo(entity.getBoundingBox().getCenter()) <= range.get();
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
            from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }

    private Vec3 getTargetPosition(LivingEntity target) {
        if (aimAtHead.get()) {
            return new Vec3(target.getX(), target.getEyeY(), target.getZ());
        }
        return target.getBoundingBox().getCenter();
    }

    private Vec3 predictTargetPosition(LivingEntity target, Vec3 currentPos, Vec3 origin) {
        Vec3 targetVel = target.getDeltaMovement();
        if (targetVel.lengthSqr() < 0.001) return currentPos;

        double dist = origin.distanceTo(currentPos);
        double speed = tridentSpeed.get() * currentCharge;
        if (speed < 0.1) return currentPos;

        double flightTime = dist / speed;
        flightTime = Math.min(flightTime, predictionSteps.get());

        Vec3 predicted = currentPos.add(targetVel.scale(flightTime));
        if (mc.level != null && predicted.y < mc.level.getMinY()) {
            predicted = new Vec3(predicted.x, mc.level.getMinY(), predicted.z);
        }
        return predicted;
    }

    private AimSolution solveAim(Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        double dx = delta.x;
        double dy = delta.y;
        double dz = delta.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);

        double speed = tridentSpeed.get() * currentCharge;
        double g = tridentGravity.get();
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

    private float refinePitchWithSimulation(Vec3 origin, Vec3 target, float initialPitch, double speed, double g) {
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

    private double simulateError(Vec3 origin, Vec3 target, float pitch, double speed, double g) {
        Vec3 pos = origin;
        Vec3 vel = Vec3.directionFromRotation(pitch, lastCalculatedYaw).scale(speed);
        double bestDist = pos.distanceToSqr(target);
        int maxSteps = Math.min(simSteps.get(), 100);

        for (int i = 0; i < maxSteps; i++) {
            Vec3 next = pos.add(vel);
            double d = next.distanceToSqr(target);
            if (d < bestDist) bestDist = d;
            pos = next;
            vel = vel.scale(tridentDrag.get());
            vel = vel.add(0, -g, 0);
            if (pos.y < mc.level.getMinY() - 4) break;
        }
        return bestDist;
    }

    private void simulateTrajectory(Vec3 origin, float yaw, float pitch, List<Vec3> out) {
        out.clear();
        double speed = tridentSpeed.get() * currentCharge;
        double g = tridentGravity.get();
        Vec3 pos = origin;
        Vec3 vel = Vec3.directionFromRotation(pitch, yaw).scale(speed);

        out.add(pos);

        int maxSteps = Math.min(simSteps.get(), 120);
        for (int i = 0; i < maxSteps; i++) {
            Vec3 next = pos.add(vel);

            if (mc.level != null) {
                var result = mc.level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (result.getType() == HitResult.Type.BLOCK) {
                    out.add(result.getLocation());
                    break;
                }
            }

            out.add(next);
            pos = next;
            vel = vel.scale(tridentDrag.get());
            vel = vel.add(0, -g, 0);
            if (pos.y < mc.level.getMinY() - 4) break;
        }
    }

    private void applyAim(float targetYaw, float targetPitch) {
        double speed = aimSpeed.get();
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

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderMode.get() == RenderMode.Off || trajectoryPoints.isEmpty()) return;

        for (int i = 0; i < trajectoryPoints.size() - 1; i++) {
            Vec3 from = trajectoryPoints.get(i);
            Vec3 to = trajectoryPoints.get(i + 1);

            float t = (float) i / Math.max(1, trajectoryPoints.size() - 1);
            meteordevelopment.meteorclient.utils.render.color.Color c =
                new meteordevelopment.meteorclient.utils.render.color.Color(
                    (int)(t * 255), (int)((1.0f - t * 0.5f) * 255), (int)(t * 128), 200);

            if (renderMode.get() == RenderMode.Line || renderMode.get() == RenderMode.Both) {
                event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, c);
            }
            if (renderMode.get() == RenderMode.Both && i % 5 == 0) {
                event.renderer.line(from.x - 0.1, from.y, from.z, from.x + 0.1, from.y, from.z, c);
                event.renderer.line(from.x, from.y - 0.1, from.z, from.x, from.y + 0.1, from.z, c);
                event.renderer.line(from.x, from.y, from.z - 0.1, from.x, from.y, from.z + 0.1, c);
            }
        }
    }

    private record AimSolution(float yaw, float pitch) {}

    @Override
    public String getInfoString() {
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof TridentItem)) return "Off";
        if (currentTarget == null) return "No target";

        StringBuilder sb = new StringBuilder();
        String name = currentTarget instanceof Player p
            ? p.getName().getString() : currentTarget.getName().getString();
        sb.append("\u2192 ").append(name);

        if (hasRiptide) sb.append(" | Riptide");
        if (hasLoyalty) sb.append(" | Loyalty");
        if (hasChanneling) sb.append(" | Channeling");
        if (impalingLevel > 0) sb.append(" | Impaling ").append(impalingLevel);

        int combo = ComboTracker.getCombo(currentTarget.getUUID());
        if (combo > 0) sb.append(" | combo: ").append(combo);

        return sb.toString();
    }
}
