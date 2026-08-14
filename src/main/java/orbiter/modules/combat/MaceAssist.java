package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.NoFall;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MaceAssist extends Module {
    public enum TargetMode {
        Closest,
        Crosshair
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgElytra = settings.createGroup("Elytra Swap");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum targeting range for mace attacks.")
        .defaultValue(4.0)
        .min(1.0)
        .max(10.0)
        .sliderRange(1.0, 10.0)
        .build()
    );

    private final Setting<Boolean> onlyCrits = sgGeneral.add(new BoolSetting.Builder()
        .name("only-crits")
        .description("Only strike when a critical hit is possible.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-cooldown")
        .description("Attack without waiting for the attack cooldown bar to reach 100%.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smashOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("smash-only")
        .description("Only attack when a smash attack is possible (fallen >= 1.5 blocks, not grounded, not elytra flying).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Attack targets through blocks without requiring line of sight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minFallBlocks = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall-blocks")
        .description("Minimum fall distance required before a smash attack is attempted.")
        .defaultValue(1.5)
        .min(1.5)
        .max(50.0)
        .sliderRange(1.5, 20.0)
        .visible(smashOnly::get)
        .build()
    );

    private final Setting<Boolean> autoEquip = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-equip")
        .description("Automatically swap to the mace when a target is in range and conditions are met.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableNoFall = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-no-fall")
        .description("Automatically enable Meteor's NoFall while this module is active to prevent smash-attack fall damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Don't target friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How to select the target.")
        .defaultValue(TargetMode.Closest)
        .build()
    );

    private final Setting<Boolean> elytraSwap = sgElytra.add(new BoolSetting.Builder()
        .name("elytra-swap")
        .description("Automatically swap Elytra and chestplate for critical hits on landing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapTicks = sgElytra.add(new IntSetting.Builder()
        .name("swap-ticks")
        .description("Ticks before landing to swap Elytra to chestplate.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(elytraSwap::get)
        .build()
    );

    private LivingEntity target;
    private boolean wasAutoSwapped = false;
    private int elytraSlot = -1;
    private int chestplateSlot = -1;
    private boolean weToggledNoFall = false;

    public MaceAssist() {
        super(Orbiter.CATEGORY_VANILLA, "mace-assist", "Auto-aim and strike with the Mace. Elytra swapping for critical hits on landing.");
    }

    @Override
    public void onActivate() {
        target = null;
        wasAutoSwapped = false;
        elytraSlot = -1;
        chestplateSlot = -1;
        if (enableNoFall.get()) toggleNoFall(true);
    }

    @Override
    public void onDeactivate() {
        restoreElytra();
        target = null;
        if (enableNoFall.get()) toggleNoFall(false);
    }

    private void toggleNoFall(boolean on) {
        Modules modules = Modules.get();
        if (modules == null) return;
        NoFall noFall = modules.get(NoFall.class);
        if (noFall == null) return;
        if (on && !noFall.isActive()) {
            noFall.toggle();
            weToggledNoFall = true;
        } else if (!on && weToggledNoFall && noFall.isActive()) {
            noFall.toggle();
            weToggledNoFall = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (!isHoldingMace()) {
            target = null;
            restoreElytra();
            return;
        }

        target = findTarget();
        if (target == null) {
            restoreElytra();
            return;
        }

        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);
        Rotations.rotate(yaw, pitch, 50, false, () -> {});

        boolean canCrit = canCriticalHit(target);

        handleElytraSwap(target, canCrit);

        boolean canSmash = canSmashAttack();
        if (smashOnly.get() && !canSmash) return;

        if (autoEquip.get() && !isHoldingMace()) {
            int maceSlot = findMaceSlot();
            if (maceSlot != -1) {
                InvUtils.swap(maceSlot, false);
                return;
            }
        }

        if (onlyCrits.get() && !canCrit) return;
        if (!ignoreCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < 1) return;
        if (mc.player.squaredDistanceTo(target) > range.get() * range.get()) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isHoldingMace() {
        return mc.player.getMainHandStack().isOf(Items.MACE);
    }

    private LivingEntity findTarget() {
        LivingEntity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living)) continue;

            double distSq = mc.player.squaredDistanceTo(living);
            if (distSq > range.get() * range.get()) continue;

            if (targetMode.get() == TargetMode.Closest) {
                if (distSq < bestScore) {
                    bestScore = distSq;
                    bestTarget = living;
                }
            } else if (targetMode.get() == TargetMode.Crosshair) {
                double angle = getAngleToEntity(living);
                if (angle < bestScore) {
                    bestScore = angle;
                    bestTarget = living;
                }
            }
        }

        return bestTarget;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == mc.player) return false;
        if (!entity.isAttackable()) return false;

        if (ignoreFriends.get() && entity instanceof PlayerEntity player) {
            if (!Friends.get().shouldAttack(player)) return false;
        }

        if (!ignoreWalls.get()) {
            if (mc.world != null && mc.player != null) {
                var result = mc.world.raycast(new net.minecraft.world.RaycastContext(
                    mc.player.getEyePos(),
                    entity.getBoundingBox().getCenter(),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    mc.player
                ));
                if (result.getType() != net.minecraft.util.hit.HitResult.Type.MISS) return false;
            }
        }

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
        return Math.acos(MathHelper.clamp(dot, -1.0, 1.0));
    }

    private boolean canCriticalHit(LivingEntity target) {
        if (!mc.player.isOnGround() && !mc.player.isGliding() && mc.player.getVelocity().y < -0.1) {
            return true;
        }
        if (!mc.player.isOnGround() && !mc.player.isSprinting() && !mc.player.isClimbing() && mc.player.getVelocity().y < -0.08) {
            return true;
        }
        return false;
    }

    private boolean canSmashAttack() {
        if (mc.player == null) return false;
        if (mc.player.isOnGround()) return false;
        if (mc.player.isGliding()) return false;
        double fallDist = mc.player.fallDistance;
        return fallDist >= minFallBlocks.get();
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.MACE)) {
                return i;
            }
        }
        return -1;
    }

    private void handleElytraSwap(LivingEntity target, boolean canCrit) {
        if (!elytraSwap.get()) return;

        ItemStack chestItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean hasElytraEquipped = chestItem.contains(DataComponentTypes.GLIDER);

        if (hasElytraEquipped && canCrit && mc.player.getVelocity().y < -0.5) {
            if (willHitTargetSoon(target)) {
                int cpSlot = findChestplateSlot();
                if (cpSlot != -1) {
                    InvUtils.move().from(cpSlot).toArmor(2);
                    wasAutoSwapped = true;
                    chestplateSlot = cpSlot;
                }
            }
        } else if (wasAutoSwapped) {
            if (!canCrit || mc.player.isOnGround() || mc.player.getVelocity().y >= 0) {
                restoreElytra();
            } else if (!willHitTargetSoon(target)) {
                restoreElytra();
            }
        }
    }

    private boolean willHitTargetSoon(LivingEntity target) {
        Vec3d vel = mc.player.getVelocity();
        Vec3d futurePos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).add(vel.multiply(swapTicks.get()));
        double futureDistSq = futurePos.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
        double rangeSq = range.get() * range.get();
        return futureDistSq <= rangeSq * 1.5;
    }

    private void restoreElytra() {
        if (!wasAutoSwapped) return;

        int elytra = findElytraSlot();
        if (elytra != -1) {
            InvUtils.move().from(elytra).toArmor(2);
        }

        wasAutoSwapped = false;
        chestplateSlot = -1;
    }

    private int findChestplateSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (isChestplate(stack)) return i;
        }
        return -1;
    }

    private int findElytraSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.contains(DataComponentTypes.GLIDER)) return i;
        }
        return -1;
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.contains(DataComponentTypes.GLIDER)) return false;
        if (!stack.contains(DataComponentTypes.EQUIPPABLE)) return false;
        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }
}
