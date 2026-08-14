package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import orbiter.Orbiter;
import orbiter.util.ComboTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SpearAssist extends Module {

    public enum TargetMode { Closest, Crosshair, LowestHealth }
    public enum AttackMode { JabOnly, ChargeOnly, JabCharge, Auto }
    public enum AimMode { Visible, Silent }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgAim = settings.createGroup("Aiming");
    private final SettingGroup sgJab = settings.createGroup("Jab Attack");
    private final SettingGroup sgCharge = settings.createGroup("Charge Attack");
    private final SettingGroup sgCombo = settings.createGroup("Combo");

    private final Setting<AttackMode> attackMode = sgGeneral.add(new EnumSetting.Builder<AttackMode>()
        .name("attack-mode")
        .description("JabOnly = only jab attacks. ChargeOnly = only charge attacks. JabCharge = alternate. Auto = pick best.")
        .defaultValue(AttackMode.Auto)
        .build()
    );

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack")
        .description("Automatically attack when in range and aimed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhenHoldingSpear = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-holding-spear")
        .description("Only activate when holding a melee weapon (sword, axe, mace, trident, tool).")
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
        .description("Max targeting distance. Spears have 4.5 block reach, other weapons 3.0.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderRange(1.0, 8.0)
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
        .description("Spear attacks can go through non-solid blocks like cobwebs and tall grass.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreCreative = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-creative")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minAttackRange = sgTarget.add(new DoubleSetting.Builder()
        .name("min-attack-range")
        .description("Minimum distance • spears can't hit targets closer than 2 blocks.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 4.0)
        .build()
    );

    private final Setting<AimMode> aimMode = sgAim.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .defaultValue(AimMode.Visible)
        .build()
    );

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed")
        .defaultValue(0.6)
        .min(0.05)
        .sliderRange(0.05, 1.0)
        .build()
    );

    private final Setting<Double> maxAimAngle = sgAim.add(new DoubleSetting.Builder()
        .name("max-aim-angle")
        .description("Max angle (degrees) between look and target before aim kicks in.")
        .defaultValue(60.0)
        .min(5.0)
        .sliderRange(5.0, 180.0)
        .build()
    );

    private final Setting<Boolean> aimAtCenter = sgAim.add(new BoolSetting.Builder()
        .name("aim-at-center")
        .description("Aim at the target's bounding box center (true) or eye level (false).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreJabCooldown = sgJab.add(new BoolSetting.Builder()
        .name("ignore-jab-cooldown")
        .description("Attack without waiting for the jab cooldown to reach 100%.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> jabCooldownTicks = sgJab.add(new IntSetting.Builder()
        .name("jab-cooldown-ticks")
        .description("Ticks to wait between jab attacks (manual cooldown).")
        .defaultValue(13)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> critOnly = sgJab.add(new BoolSetting.Builder()
        .name("crit-only")
        .description("Only jab when a critical hit is possible (falling + not on ground). Note: spears can't crit.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableChargeAttack = sgCharge.add(new BoolSetting.Builder()
        .name("enable-charge-attack")
        .description("Enable charge attacks (hold use button, deal damage based on velocity).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minChargeSpeed = sgCharge.add(new DoubleSetting.Builder()
        .name("min-charge-speed")
        .description("Minimum relative speed (blocks/sec) required for charge attack damage. Vanilla: 5.1")
        .defaultValue(5.1)
        .min(1.0)
        .sliderRange(1.0, 15.0)
        .visible(enableChargeAttack::get)
        .build()
    );

    private final Setting<Boolean> autoChargeWhenMoving = sgCharge.add(new BoolSetting.Builder()
        .name("auto-charge-when-moving")
        .description("Automatically start a charge attack when sprinting toward a target.")
        .defaultValue(true)
        .visible(enableChargeAttack::get)
        .build()
    );

    private final Setting<Boolean> trackCombo = sgCombo.add(new BoolSetting.Builder()
        .name("track-combo")
        .defaultValue(true)
        .build()
    );

    private LivingEntity currentTarget;
    private int jabCooldown = 0;
    private int tickCounter = 0;
    private boolean isCharging = false;
    private boolean lastJabWasJab = false;

    public SpearAssist() {
        super(Orbiter.CATEGORY_VANILLA, "spear-assist",
            "Melee assist tuned for close-range combat. Supports jab attacks, charge attacks, and combo alternation.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        jabCooldown = 0;
        tickCounter = 0;
        isCharging = false;
        lastJabWasJab = false;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        isCharging = false;
        ComboTracker.clearAll();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (jabCooldown > 0) jabCooldown--;

        if (onlyWhenHoldingSpear.get() && !isHoldingMeleeWeapon()) {
            currentTarget = null;
            return;
        }

        currentTarget = findBestTarget();
        if (currentTarget == null) {
            isCharging = false;
            return;
        }

        Vec3d eyes = mc.player.getEyePos();
        Vec3d targetCenter = currentTarget.getBoundingBox().getCenter();
        double dist = eyes.distanceTo(targetCenter);

        if (dist < minAttackRange.get()) {

            return;
        }
        if (dist > range.get()) {
            return;
        }

        Vec3d aimPos = aimAtCenter.get() ? targetCenter : new Vec3d(currentTarget.getX(), currentTarget.getEyeY(), currentTarget.getZ());
        float targetYaw = (float) (Math.toDegrees(Math.atan2(aimPos.z - eyes.z, aimPos.x - eyes.x)) - 90.0f);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(aimPos.y - eyes.y, Math.sqrt(
            (aimPos.x - eyes.x) * (aimPos.x - eyes.x) + (aimPos.z - eyes.z) * (aimPos.z - eyes.z))));

        float angleDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
        if (angleDiff > maxAimAngle.get().floatValue()) return;

        double speed = aimSpeed.get();
        float newYaw = (float) (mc.player.getYaw() + MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()) * speed);
        float newPitch = (float) (mc.player.getPitch() + (targetPitch - mc.player.getPitch()) * speed);

        if (aimMode.get() == AimMode.Visible) {
            mc.player.setYaw(newYaw);
            mc.player.setPitch(newPitch);
        }

        if (!autoAttack.get()) return;

        AttackMode effectiveMode = attackMode.get();
        if (effectiveMode == AttackMode.Auto) {

            double playerSpeed = new Vec3d(mc.player.getVelocity().x, 0, mc.player.getVelocity().z).length() * 20;
            if (enableChargeAttack.get() && playerSpeed >= minChargeSpeed.get()) {
                effectiveMode = AttackMode.ChargeOnly;
            } else {
                effectiveMode = AttackMode.JabOnly;
            }
        }

        switch (effectiveMode) {
            case JabOnly -> tryJabAttack(currentTarget, dist, angleDiff);
            case ChargeOnly -> tryChargeAttack(currentTarget, dist);
            case JabCharge -> {

                if (lastJabWasJab) {
                    tryChargeAttack(currentTarget, dist);
                    lastJabWasJab = false;
                } else {
                    tryJabAttack(currentTarget, dist, angleDiff);
                    lastJabWasJab = true;
                }
            }
            case Auto -> {

                tryJabAttack(currentTarget, dist, angleDiff);
            }
        }
    }

    private void tryJabAttack(LivingEntity target, double dist, float angleDiff) {
        if (jabCooldown > 0) return;

        if (!ignoreJabCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        if (critOnly.get()) {
            boolean canCrit = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.08;
            if (!canCrit) return;
        }

        if (dist > range.get()) return;
        if (angleDiff > 15.0f) return;

        if (mc.interactionManager != null) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (trackCombo.get()) ComboTracker.registerHit(target.getUuid());
            jabCooldown = jabCooldownTicks.get();
        }
    }

    private void tryChargeAttack(LivingEntity target, double dist) {
        if (!enableChargeAttack.get()) return;

        double playerSpeed = new Vec3d(mc.player.getVelocity().x, 0, mc.player.getVelocity().z).length() * 20;
        double targetSpeed = new Vec3d(target.getVelocity().x, 0, target.getVelocity().z).length() * 20;
        double relativeSpeed = Math.abs(playerSpeed - targetSpeed);

        if (relativeSpeed < minChargeSpeed.get()) {

            if (autoChargeWhenMoving.get() && mc.player.isSprinting() && !isCharging) {
                Vec3d toTarget = target.getBoundingBox().getCenter().subtract(mc.player.getEyePos()).normalize();
                Vec3d moveDir = mc.player.getVelocity().normalize();
                if (toTarget.dotProduct(moveDir) > 0.5) {

                    mc.options.useKey.setPressed(true);
                    isCharging = true;
                }
            }
            return;
        }

        if (isCharging) {

            if (target == null || !target.isAlive() || dist > range.get() + 2) {
                mc.options.useKey.setPressed(false);
                isCharging = false;
            }
        }
    }

    private boolean isHoldingMeleeWeapon() {
        if (mc.player == null) return false;
        Item item = mc.player.getMainHandStack().getItem();

        return item instanceof MaceItem
            || item instanceof TridentItem
            || item == Items.MACE
            || item == Items.TRIDENT
            || item == Items.NETHERITE_SWORD
            || item == Items.DIAMOND_SWORD
            || item == Items.IRON_SWORD
            || item == Items.GOLDEN_SWORD
            || item == Items.STONE_SWORD
            || item == Items.WOODEN_SWORD
            || item == Items.NETHERITE_AXE
            || item == Items.DIAMOND_AXE
            || item == Items.IRON_AXE
            || item == Items.GOLDEN_AXE
            || item == Items.STONE_AXE
            || item == Items.WOODEN_AXE;
    }

    private LivingEntity findBestTarget() {
        Vec3d eyes = mc.player.getEyePos();
        List<LivingEntity> candidates = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;

            double dist = eyes.distanceTo(living.getBoundingBox().getCenter());
            if (dist > range.get()) continue;

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

    @Override
    public String getInfoString() {
        if (currentTarget == null) return "No target";

        StringBuilder sb = new StringBuilder();
        String name = currentTarget instanceof PlayerEntity p
            ? p.getName().getString() : currentTarget.getName().getString();
        sb.append("\u2192 ").append(name);

        if (isCharging) sb.append(" | charging");
        if (jabCooldown > 0) sb.append(" | cd: ").append(jabCooldown);

        if (trackCombo.get()) {
            int combo = ComboTracker.getCombo(currentTarget.getUuid());
            if (combo > 0) sb.append(" | combo: ").append(combo);
        }

        return sb.toString();
    }
}
