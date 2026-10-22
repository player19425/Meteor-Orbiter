package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import orbiter.compat.EntityCompat;
import orbiter.Orbiter;
import orbiter.systems.combat.CombatEngine;
import orbiter.util.ConfigModifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ShieldAssist extends Module {

    public enum ThreatMode { All, Projectiles, Melee }
    public enum BlockStage { Engaged, Tired, Disengaged }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlock = settings.createGroup("Blocking");
    private final SettingGroup sgRelease = settings.createGroup("Release");
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    private final Setting<ThreatMode> threatMode = sgGeneral.add(new EnumSetting.Builder<ThreatMode>()
        .name("threat-mode")
        .description("What threats to block against.")
        .defaultValue(ThreatMode.All)
        .build()
    );

    private final Setting<Boolean> onlyWithShield = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-shield")
        .description("Only activate when a shield is equipped in main or off hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> projectileRange = sgBlock.add(new DoubleSetting.Builder()
        .name("projectile-range")
        .description("Detection range for incoming projectiles.")
        .defaultValue(30.0)
        .min(5.0)
        .sliderRange(5.0, 64.0)
        .build()
    );

    private final Setting<Double> meleeRange = sgBlock.add(new DoubleSetting.Builder()
        .name("melee-range")
        .description("Detection range for melee attackers.")
        .defaultValue(4.5)
        .min(2.0)
        .sliderRange(2.0, 8.0)
        .build()
    );

    private final Setting<Integer> blockTicks = sgBlock.add(new IntSetting.Builder()
        .name("block-ticks")
        .description("How long to hold the shield up (ticks). Vanilla shield block delay: 5 ticks.")
        .defaultValue(10)
        .min(5)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<Double> projectileDotThreshold = sgBlock.add(new DoubleSetting.Builder()
        .name("projectile-dot-threshold")
        .description("How straight a projectile has to be aimed at you. 1.0 is head on.")
        .defaultValue(0.85)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Boolean> blockRavagerRoars = sgBlock.add(new BoolSetting.Builder()
        .name("block-ravager-roars")
        .description("Block against ravager roar attacks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockFireballs = sgBlock.add(new BoolSetting.Builder()
        .name("block-fireballs")
        .description("Block against blaze fireballs and ghast fireballs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> releaseGap = sgRelease.add(new IntSetting.Builder()
        .name("release-gap")
        .description("Ticks to release the shield between blocks. Vanilla disable cooldown: 100 ticks.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> releaseWhenSafe = sgRelease.add(new BoolSetting.Builder()
        .name("release-when-safe")
        .description("Release the shield when no threats are detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> safeReleaseDistance = sgRelease.add(new DoubleSetting.Builder()
        .name("safe-release-distance")
        .description("Distance at which melee threats are considered too far to be dangerous.")
        .defaultValue(6.0)
        .min(2.0)
        .sliderRange(2.0, 16.0)
        .visible(releaseWhenSafe::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Don't block against attacks from friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTarget.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entity types count as melee threats. Empty counts all living entities.")
        .build()
    );

    private final Setting<Boolean> detectAxeDisabling = sgAdvanced.add(new BoolSetting.Builder()
        .name("detect-axe-disabling")
        .description("Treat a nearby axe wielder as a threat even when it is not swinging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shieldCooldownTracking = sgAdvanced.add(new BoolSetting.Builder()
        .name("shield-cooldown-tracking")
        .description("Track shield disable cooldown and don't try to block while disabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> shieldDisableDuration = sgAdvanced.add(new IntSetting.Builder()
        .name("shield-disable-duration")
        .description("Ticks the shield stays disabled after an axe hit. Vanilla: 100.")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 200)
        .visible(shieldCooldownTracking::get)
        .build()
    );

    private boolean isBlocking = false;
    private int blockTimer = 0;
    private int blockActiveTicks = 0;
    private int releaseTimer = 0;
    private int shieldDisableTimer = 0;
    private final Set<UUID> trackedAxeUsers = new HashSet<>();

    public ShieldAssist() {
        super(Orbiter.CATEGORY_WIP, "shield-assist",
            "Auto-blocks with the shield.");
    }

    @Override
    public void onActivate() {
        if (!ConfigModifier.get().wipModulesEnabled()) {
            info("WIP Modules is disabled. Enable it in Meteor Config, Orbiter section.");
            toggle();
            return;
        }
        resetState();
    }

    @Override
    public void onDeactivate() {
        if (isBlocking && mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        if (isBlocking) mc.options.keyUse.setDown(false);
        isBlocking = false;
        trackedAxeUsers.clear();
    }

    private void resetState() {
        isBlocking = false;
        blockTimer = 0;
        blockActiveTicks = 0;
        releaseTimer = 0;
        shieldDisableTimer = 0;
        trackedAxeUsers.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (CombatEngine.get().isFrozen()) {
            if (isBlocking) stopBlocking();
            return;
        }

        if (releaseTimer > 0) releaseTimer--;

        if (shieldDisableTimer > 0) shieldDisableTimer--;
        if (shieldDisableTimer > 0 && shieldCooldownTracking.get()) {
            if (isBlocking) stopBlocking();
            return;
        }

        boolean hasShield = checkHasShield();
        if (onlyWithShield.get() && !hasShield) {
            if (isBlocking) stopBlocking();
            return;
        }

        if (detectAxeDisabling.get()) {
            trackAxeUsers();
        } else {
            trackedAxeUsers.clear();
        }

        boolean threat = detectThreat();

        if (threat && !isBlocking && releaseTimer <= 0) {
            startBlocking();
        } else if (isBlocking) {
            blockActiveTicks++;
            blockTimer--;
            if (!isShieldActuallyRaised() || blockTimer <= 0 || (releaseWhenSafe.get() && !threat)) {
                stopBlocking();
                releaseTimer = releaseGap.get();
            }
        }
    }

    private boolean isBlockItem(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.has(DataComponents.BLOCKS_ATTACKS);
    }

    private boolean checkHasShield() {
        if (mc.player == null) return false;
        return isBlockItem(mc.player.getMainHandItem()) || isBlockItem(mc.player.getOffhandItem());
    }

    private ItemStack blockingStack() {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (isBlockItem(mainHand)) return mainHand;
        ItemStack offHand = mc.player.getOffhandItem();
        return isBlockItem(offHand) ? offHand : ItemStack.EMPTY;
    }

    private BlocksAttacks blockComponent() {
        return blockingStack().get(DataComponents.BLOCKS_ATTACKS);
    }

    private int shieldBlockDelayTicks() {
        BlocksAttacks component = blockComponent();
        return component == null ? 0 : component.blockDelayTicks();
    }

    private boolean isBlockEffective() {
        return isBlocking && blockActiveTicks >= shieldBlockDelayTicks();
    }

    private boolean isShieldActuallyRaised() {
        if (mc.player == null) return false;
        if (blockActiveTicks < shieldBlockDelayTicks()) return true;
        return mc.player.isUsingItem() && mc.player.isBlocking();
    }

    private float shieldBlockingAngle() {
        BlocksAttacks component = blockComponent();
        if (component == null) return 0.0F;
        float widest = 0.0F;
        for (BlocksAttacks.DamageReduction reduction : component.damageReductions()) {
            widest = Math.max(widest, reduction.horizontalBlockingAngle());
        }
        return widest;
    }

    private boolean isWithinBlockCone(Vec3 sourcePos, float angleLimit) {
        if (mc.player == null || sourcePos == null || angleLimit <= 0.0F) return false;

        Vec3 toSource = sourcePos.subtract(mc.player.position());
        toSource = new Vec3(toSource.x, 0.0, toSource.z).normalize();
        Vec3 view = mc.player.calculateViewVector(0.0F, mc.player.getYHeadRot());
        double angle = Math.acos(Mth.clamp(toSource.dot(view), -1.0, 1.0));

        return angle <= angleLimit * Mth.DEG_TO_RAD;
    }

    private boolean detectThreat() {
        Vec3 eyes = mc.player.getEyePosition();
        float angleLimit = shieldBlockingAngle();

        if (threatMode.get() == ThreatMode.All || threatMode.get() == ThreatMode.Projectiles) {
            if (detectProjectileThreat(eyes, angleLimit)) return true;
        }

        if (threatMode.get() == ThreatMode.All || threatMode.get() == ThreatMode.Melee) {
            if (detectMeleeThreat(angleLimit, meleeRange.get())) return true;
        }

        return false;
    }

    private boolean detectProjectileThreat(Vec3 eyes, float angleLimit) {
        double detectionRange = projectileRange.get();
        double dotThreshold = projectileDotThreshold.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile proj)) continue;
            if (proj.getOwner() == mc.player) continue;

            Vec3 projPos = new Vec3(proj.getX(), proj.getY(), proj.getZ());
            double dist = projPos.distanceTo(eyes);

            if (dist > detectionRange) continue;

            Vec3 projVel = proj.getDeltaMovement();
            if (projVel.lengthSqr() < 0.001) continue;

            if (!isWithinBlockCone(projPos, angleLimit)) continue;

            Vec3 toPlayer = eyes.subtract(projPos).normalize();
            Vec3 projDir = projVel.normalize();
            double dot = toPlayer.dot(projDir);

            if (dot > dotThreshold) {
                return true;
            }

            if (dist < 3.0) {
                return true;
            }
        }

        return false;
    }

    private boolean detectMeleeThreat(float angleLimit, double meleeDist) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (!entities.get().isEmpty() && !entities.get().contains(living.getType())) continue;

            if (living instanceof Player p) {
                if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;
                if (p.getAbilities().instabuild) continue;
            }

            double dist = living.distanceTo(mc.player);
            if (dist > meleeDist + 2.0) continue;

            Vec3 attackerPos = new Vec3(living.getX(), living.getY(), living.getZ());
            if (!isWithinBlockCone(attackerPos, angleLimit)) continue;

            if (EntityCompat.isSwinging(living) && dist < meleeDist) {
                return true;
            }

            if (detectAxeDisabling.get() && trackedAxeUsers.contains(living.getUUID()) && dist < meleeDist + 1.0) {
                return true;
            }

            if (living.isSprinting() && dist < meleeDist + 1.0) {
                Vec3 toPlayer = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()).subtract(
                    new Vec3(living.getX(), living.getY(), living.getZ())).normalize();
                Vec3 moveDir = living.getDeltaMovement().normalize();
                if (toPlayer.dot(moveDir) > 0.7) {
                    return true;
                }
            }
        }

        return false;
    }

    private void trackAxeUsers() {
        trackedAxeUsers.clear();
        double detectionRange = meleeRange.get() + 4.0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;

            ItemStack mainHand = living.getMainHandItem();
            ItemStack offHand = living.getOffhandItem();
            boolean hasAxe = EntityCompat.isAxe(mainHand)
                || EntityCompat.isAxe(offHand);

            if (hasAxe && living.distanceTo(mc.player) < detectionRange) {
                trackedAxeUsers.add(living.getUUID());
            }
        }
    }

    private void startBlocking() {
        if (mc.player == null) return;

        if (shieldDisableTimer > 0) return;

        if (blockingStack().isEmpty()) return;

        mc.options.keyUse.setDown(true);
        isBlocking = true;
        blockActiveTicks = 0;
        blockTimer = blockTicks.get();
    }

    private void stopBlocking() {
        if (mc.player == null) return;
        mc.options.keyUse.setDown(false);
        isBlocking = false;
        blockTimer = 0;
        blockActiveTicks = 0;
    }

    public void onShieldDisabled() {
        shieldDisableTimer = shieldDisableDuration.get();
        if (isBlocking) stopBlocking();
    }

    public boolean isShieldDisabled() {
        return shieldDisableTimer > 0;
    }

    public BlockStage getBlockStage() {
        if (!isBlocking) return null;
        if (!isBlockEffective()) return BlockStage.Disengaged;
        if (blockTimer > blockTicks.get() * 2 / 3) return BlockStage.Engaged;
        if (blockTimer > blockTicks.get() / 3) return BlockStage.Tired;
        return BlockStage.Disengaged;
    }

    @Override
    public String getInfoString() {
        if (shieldDisableTimer > 0) {
            return "Disabled (" + shieldDisableTimer + "t)";
        }
        if (isBlocking) {
            return (isBlockEffective() ? "Blocking (" : "Raising (") + blockTimer + "t)";
        }
        if (releaseTimer > 0) {
            return "Releasing (" + releaseTimer + "t)";
        }
        return trackedAxeUsers.isEmpty() ? "Ready" : "Alert (" + trackedAxeUsers.size() + " axes)";
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
    }
}
