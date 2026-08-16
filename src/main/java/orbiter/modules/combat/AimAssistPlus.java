package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.Set;

public class AimAssistPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgSpeed = settings.createGroup("Aim Speed");
    private final SettingGroup sgStickyAim = settings.createGroup("Sticky Aim");
    private final SettingGroup sgHumanize = settings.createGroup("Humanization");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgProjectile = settings.createGroup("Projectile Aim");
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Which entities to aim at.")
            .defaultValue(EntityTypes.PLAYER)
            .build());

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Maximum targeting range.")
            .defaultValue(5.0)
            .min(1.0)
            .sliderRange(1.0, 20.0)
            .build());

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
            .name("fov")
            .description("Field of view cone for target acquisition.")
            .defaultValue(360.0)
            .min(1.0)
            .max(360.0)
            .sliderRange(1.0, 360.0)
            .build());

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-walls")
            .description("Target entities through blocks.")
            .defaultValue(false)
            .build());

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
            .name("priority")
            .description("How to select the best target.")
            .defaultValue(SortPriority.LowestHealth)
            .build());

    private final Setting<Target> bodyTarget = sgTargeting.add(new EnumSetting.Builder<Target>()
            .name("aim-target")
            .description("Which part of the body to aim at.")
            .defaultValue(Target.Head)
            .build());

    private final Setting<Boolean> prediction = sgTargeting.add(new BoolSetting.Builder()
            .name("prediction")
            .description("Predict target movement and lead your aim.")
            .defaultValue(false)
            .build());

    private final Setting<Double> predictionStrength = sgTargeting.add(new DoubleSetting.Builder()
            .name("prediction-strength")
            .description("How many ticks ahead to predict.")
            .defaultValue(2.0)
            .min(0.5)
            .sliderRange(0.5, 10.0)
            .visible(prediction::get)
            .build());

    private final Setting<Boolean> targetLock = sgTargeting.add(new BoolSetting.Builder()
            .name("target-lock")
            .description("Keep aiming at the same entity until it dies or leaves range.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> projectileAim = sgProjectile.add(new BoolSetting.Builder()
            .name("projectile-aim")
            .description("Enable ballistic prediction for bows and crossbows.")
            .defaultValue(false)
            .build());

    private final Setting<Double> projectileGravity = sgProjectile.add(new DoubleSetting.Builder()
            .name("projectile-gravity")
            .description("Gravity constant for arrow trajectory.")
            .defaultValue(0.05)
            .min(0.01)
            .sliderRange(0.01, 0.2)
            .visible(projectileAim::get)
            .build());

    private final Setting<Double> projectileVelocity = sgProjectile.add(new DoubleSetting.Builder()
            .name("projectile-velocity")
            .description("Initial arrow velocity (blocks/tick).")
            .defaultValue(3.0)
            .min(0.5)
            .sliderRange(0.5, 6.0)
            .visible(projectileAim::get)
            .build());

    private final Setting<Boolean> chargeCompensation = sgProjectile.add(new BoolSetting.Builder()
            .name("charge-compensation")
            .description("Account for bow charge level when calculating velocity.")
            .defaultValue(true)
            .visible(projectileAim::get)
            .build());

    private final Setting<Boolean> instant = sgSpeed.add(new BoolSetting.Builder()
            .name("instant-look")
            .description("Snap to target instantly.")
            .defaultValue(false)
            .build());

    private final Setting<Double> yawSpeed = sgSpeed.add(new DoubleSetting.Builder()
            .name("yaw-speed")
            .description("Horizontal aim speed.")
            .defaultValue(8.0)
            .min(0.1)
            .sliderRange(0.1, 50.0)
            .visible(() -> !instant.get())
            .build());

    private final Setting<Double> pitchSpeed = sgSpeed.add(new DoubleSetting.Builder()
            .name("pitch-speed")
            .description("Vertical aim speed.")
            .defaultValue(6.0)
            .min(0.1)
            .sliderRange(0.1, 50.0)
            .visible(() -> !instant.get())
            .build());

    private final Setting<Double> maxAnglePerTick = sgSpeed.add(new DoubleSetting.Builder()
            .name("max-angle-per-tick")
            .description("Maximum degrees the aim can rotate per tick. Prevents snapping.")
            .defaultValue(180.0)
            .min(1.0)
            .sliderRange(1.0, 180.0)
            .visible(() -> !instant.get())
            .build());

    private final Setting<Double> smoothing = sgSpeed.add(new DoubleSetting.Builder()
            .name("smoothing")
            .description("Easing factor for aim movement. Higher = smoother.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 5.0)
            .visible(() -> !instant.get())
            .build());

    private final Setting<Boolean> stickyAim = sgStickyAim.add(new BoolSetting.Builder()
            .name("sticky-aim")
            .description("Once locked on, stay for a minimum duration even if target leaves FOV.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> stickyDuration = sgStickyAim.add(new IntSetting.Builder()
            .name("sticky-duration")
            .description("Ticks to hold aim on target after losing FOV.")
            .defaultValue(20)
            .min(5)
            .sliderRange(5, 100)
            .visible(stickyAim::get)
            .build());

    private final Setting<Boolean> breakAimOnHit = sgStickyAim.add(new BoolSetting.Builder()
            .name("break-on-kill")
            .description("Release target lock when target dies.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> humanize = sgHumanize.add(new BoolSetting.Builder()
            .name("humanize")
            .description("Add subtle random jitter to aim movement to look natural.")
            .defaultValue(false)
            .build());

    private final Setting<Double> jitterYaw = sgHumanize.add(new DoubleSetting.Builder()
            .name("jitter-yaw")
            .description("Maximum random horizontal jitter in degrees.")
            .defaultValue(1.5)
            .min(0.1)
            .sliderRange(0.1, 10.0)
            .visible(humanize::get)
            .build());

    private final Setting<Double> jitterPitch = sgHumanize.add(new DoubleSetting.Builder()
            .name("jitter-pitch")
            .description("Maximum random vertical jitter in degrees.")
            .defaultValue(0.8)
            .min(0.1)
            .sliderRange(0.1, 10.0)
            .visible(humanize::get)
            .build());

    private final Setting<Integer> aimPauseTicks = sgHumanize.add(new IntSetting.Builder()
            .name("aim-pause-ticks")
            .description("Randomly pause aiming for this many ticks to simulate human imperfection.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 10)
            .visible(humanize::get)
            .build());

    private final Setting<Boolean> onlyWeapon = sgFilters.add(new BoolSetting.Builder()
            .name("only-with-weapon")
            .description("Only activate when holding a weapon (sword, axe, trident, mace).")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> ignoreFriends = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Don't target friends.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> ignoreInvisible = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-invisible")
            .description("Skip invisible entities.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> ignoreBabies = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-babies")
            .description("Skip baby entities.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> ignoreNametagged = sgFilters.add(new BoolSetting.Builder()
            .name("ignore-nametagged")
            .description("Skip entities with custom name tags.")
            .defaultValue(false)
            .build());

    private final Setting<Double> minHealth = sgFilters.add(new DoubleSetting.Builder()
            .name("min-health")
            .description("Ignore entities below this health (already nearly dead).")
            .defaultValue(0.0)
            .min(0.0)
            .sliderRange(0.0, 20.0)
            .build());

    private final Setting<Boolean> renderTarget = sgVisuals.add(new BoolSetting.Builder()
            .name("render-target")
            .description("Highlight the current target entity.")
            .defaultValue(true)
            .build());

    private final Setting<SettingColor> targetColor = sgVisuals.add(new ColorSetting.Builder()
            .name("target-color")
            .description("Highlight color for the current target.")
            .defaultValue(new SettingColor(255, 50, 50, 100))
            .visible(renderTarget::get)
            .build());

    private Entity target;
    private Entity lockedTarget;
    private int stickyTicks = 0;
    private int pauseTicks = 0;
    private final Random random = new Random();

    public AimAssistPlus() {
        super(Orbiter.CATEGORY_VANILLA, "aim-assist-plus",
                "Aim assist with prediction.");
    }

    @Override
    public void onActivate() {
        target = null;
        lockedTarget = null;
        stickyTicks = 0;
        pauseTicks = 0;
    }

    @Override
    public void onDeactivate() {
        target = null;
        lockedTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null)
            return;

        if (onlyWeapon.get() && !isHoldingWeapon()) {
            target = null;
            return;
        }

        if (humanize.get() && aimPauseTicks.get() > 0 && pauseTicks > 0) {
            pauseTicks--;
            return;
        }
        if (humanize.get() && aimPauseTicks.get() > 0 && random.nextInt(100) < 3) {
            pauseTicks = 1 + random.nextInt(aimPauseTicks.get());
        }

        if (targetLock.get() && lockedTarget != null) {
            if (isValidTarget(lockedTarget)) {
                target = lockedTarget;
                stickyTicks = 0;
            } else if (stickyAim.get() && stickyTicks < stickyDuration.get() && lockedTarget.isAlive()) {

                target = lockedTarget;
                stickyTicks++;
            } else {

                lockedTarget = null;
                target = findTarget();
                if (target != null)
                    lockedTarget = target;
            }
        } else {
            target = findTarget();
            if (targetLock.get() && target != null) {
                lockedTarget = target;
            }
        }

        if (breakAimOnHit.get() && target != null && !target.isAlive()) {
            target = null;
            lockedTarget = null;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (target == null)
            return;

        aim(target, event.tickDelta);

        if (renderTarget.get() && target instanceof LivingEntity) {
            AABB box = target.getBoundingBox();
            event.renderer.box(box, targetColor.get(), targetColor.get(), ShapeMode.Both, 0);
        }
    }

    private Entity findTarget() {
        return TargetUtils.get(this::isValidTarget, priority.get());
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive())
            return false;
        if (entity == mc.player)
            return false;
        if (!entities.get().contains(entity.getType()))
            return false;
        if (!PlayerUtils.isWithin(entity, range.get()))
            return false;
        if (!ignoreWalls.get() && !PlayerUtils.canSeeEntity(entity))
            return false;

        if (fov.get() < 360.0) {
            if (!isInFov(entity))
                return false;
        }

        if (ignoreFriends.get() && entity instanceof Player player) {
            if (!Friends.get().shouldAttack(player))
                return false;
        }

        if (ignoreInvisible.get() && entity.isInvisible())
            return false;

        if (ignoreBabies.get() && entity instanceof LivingEntity living && living.isBaby())
            return false;

        if (ignoreNametagged.get() && entity.hasCustomName())
            return false;

        if (entity instanceof LivingEntity living) {
            if (living.getHealth() < minHealth.get())
                return false;
        }

        return true;
    }

    private boolean isInFov(Entity entity) {
        float yaw = mc.player.getYRot();
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = Mth.wrapDegrees(angle - yaw);
        return Math.abs(diff) <= fov.get() / 2.0;
    }

    private void aim(Entity target, double tickDelta) {
        Vec3 targetPos = getTargetPosition(target, tickDelta);

        double dx = targetPos.x - mc.player.getX();
        double dz = targetPos.z - mc.player.getZ();
        double dy = targetPos.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));

        double desiredYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double desiredPitch;

        if (projectileAim.get() && isHoldingProjectileWeapon()) {
            desiredPitch = calculateBallisticPitch(horizontalDist, dy);
        } else {
            desiredPitch = -Math.toDegrees(Math.atan2(dy, horizontalDist));
        }

        if (humanize.get()) {
            desiredYaw += (random.nextDouble() - 0.5) * 2.0 * jitterYaw.get();
            desiredPitch += (random.nextDouble() - 0.5) * 2.0 * jitterPitch.get();
        }

        desiredPitch = Mth.clamp(desiredPitch, -90.0, 90.0);

        if (instant.get()) {
            mc.player.setYRot((float) desiredYaw);
            mc.player.setXRot((float) desiredPitch);
        } else {

            double deltaYaw = Mth.wrapDegrees(desiredYaw - mc.player.getYRot());
            double deltaPitch = Mth.wrapDegrees(desiredPitch - mc.player.getXRot());

            double easeFactor = 1.0 / smoothing.get();

            double yawRotation = yawSpeed.get() * Math.signum(deltaYaw) * tickDelta * easeFactor;
            yawRotation = clampRotation(yawRotation, deltaYaw, maxAnglePerTick.get());

            double pitchRotation = pitchSpeed.get() * Math.signum(deltaPitch) * tickDelta * easeFactor;
            pitchRotation = clampRotation(pitchRotation, deltaPitch, maxAnglePerTick.get());

            mc.player.setYRot(mc.player.getYRot() + (float) yawRotation);
            mc.player.setXRot(Mth.clamp(mc.player.getXRot() + (float) pitchRotation, -90f, 90f));
        }
    }

    private double calculateBallisticPitch(double horizontalDist, double verticalDist) {
        if (!Double.isFinite(horizontalDist) || !Double.isFinite(verticalDist)) return 0.0;

        double g = projectileGravity.get();
        double v = projectileVelocity.get();
        if (!Double.isFinite(g) || g <= 0 || !Double.isFinite(v) || v <= 0) return 0.0;

        if (chargeCompensation.get() && mc.player != null) {
            ItemStack active = mc.player.getActiveItem();
            if (active != null && active.getItem() instanceof BowItem) {
                int useTime = mc.player.getUseItemRemainingTicks();
                float charge = BowItem.getPowerForTime(useTime);
                v *= Math.max(0.1, charge);
            }
        }

        double bestPitch = -Math.toDegrees(Math.atan2(verticalDist, horizontalDist));

        for (int iteration = 0; iteration < 20; iteration++) {
            double pitchRad = Math.toRadians(-bestPitch);
            double cosP = Math.cos(pitchRad);
            double sinP = Math.sin(pitchRad);

            if (Math.abs(cosP) < 0.001)
                break;

            double vx = v * cosP;
            double vy = v * sinP;

            double t = horizontalDist / vx;
            if (t <= 0 || !Double.isFinite(t))
                break;

            double predictedY = vy * t - 0.5 * g * t * t;
            if (!Double.isFinite(predictedY))
                break;

            double error = verticalDist - predictedY;

            double correction = Math.toDegrees(Math.atan2(error, horizontalDist)) * 0.5;
            bestPitch -= correction;

            if (Math.abs(error) < 0.01)
                break;
        }

        return Double.isFinite(bestPitch) ? bestPitch : 0.0;
    }

    private boolean isHoldingProjectileWeapon() {
        if (mc.player == null)
            return false;
        ItemStack stack = mc.player.getMainHandItem();
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.is(Items.TRIDENT);
    }

    private double clampRotation(double rotation, double delta, double maxAngle) {

        if ((rotation >= 0 && rotation > delta) || (rotation < 0 && rotation < delta)) {
            rotation = delta;
        }

        if (Math.abs(rotation) > maxAngle) {
            rotation = maxAngle * Math.signum(rotation);
        }
        return rotation;
    }

    private Vec3 getTargetPosition(Entity entity, double tickDelta) {
        if (entity == null) return Vec3.ZERO;

        Vec3 lerpedPos = entity.position();
        double x = lerpedPos.x;
        double y = lerpedPos.y;
        double z = lerpedPos.z;

        switch (bodyTarget.get()) {
            case Head -> {
                if (entity instanceof LivingEntity living) {
                    y += living.getEyeHeight(living.getPose());
                }
            }
            case Body -> {
                if (entity instanceof LivingEntity living) {
                    y += living.getEyeHeight(living.getPose()) * 0.5;
                }
            }
            case Feet -> {

            }
        }

        if (prediction.get()) {
            Vec3 vel = entity.getDeltaMovement();
            x += vel.x * predictionStrength.get();
            y += vel.y * predictionStrength.get();
            z += vel.z * predictionStrength.get();
        }

        return new Vec3(x, y, z);
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null)
            return false;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack == null)
            return false;

        return stack.has(DataComponents.WEAPON);
    }

    @Override
    public String getInfoString() {
        if (target == null)
            return null;
        return EntityUtils.getName(target);
    }
}
