package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import orbiter.Orbiter;

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
        .description("Minimum dot product between projectile direction and vector-to-player for blocking. 1.0 = perfect head-on, 0.0 = 90° angle.")
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
        .description("Ticks to release shield between blocks. Allows counter-attacks. Vanilla shield disable cooldown: 100 ticks (5 seconds).")
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

    private final Setting<Boolean> detectAxeDisabling = sgAdvanced.add(new BoolSetting.Builder()
        .name("detect-axe-disabling")
        .description("Detect when an axe-wielding attacker is nearby and prioritize blocking against them.")
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
        .description("Ticks the shield stays disabled after being hit by an axe (vanilla: 100 = 5 seconds).")
        .defaultValue(100)
        .min(20)
        .sliderRange(20, 200)
        .visible(shieldCooldownTracking::get)
        .build()
    );

    private boolean isBlocking = false;
    private int blockTimer = 0;
    private int releaseTimer = 0;
    private int shieldDisableTimer = 0;
    private int tickCounter = 0;
    private final Set<UUID> trackedAxeUsers = new HashSet<>();

    public ShieldAssist() {
        super(Orbiter.CATEGORY_VANILLA, "shield-assist",
            "Auto-blocks with shield against projectiles, melee, and special attacks. Smart release for counter-attacks.");
    }

    @Override
    public void onActivate() {
        isBlocking = false;
        blockTimer = 0;
        releaseTimer = 0;
        shieldDisableTimer = 0;
        tickCounter = 0;
        trackedAxeUsers.clear();
    }

    @Override
    public void onDeactivate() {
        if (isBlocking && mc.player != null && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
        }
        isBlocking = false;
        trackedAxeUsers.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        tickCounter++;

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
        }

        boolean threat = detectThreat();

        if (threat && !isBlocking && releaseTimer <= 0) {
            startBlocking();
        } else if (isBlocking) {
            blockTimer--;
            if (blockTimer <= 0 || (releaseWhenSafe.get() && !threat)) {
                stopBlocking();
                releaseTimer = releaseGap.get();
            }
        }

        if (releaseTimer > 0) releaseTimer--;
    }

    private boolean checkHasShield() {
        if (mc.player == null) return false;
        return mc.player.getOffhandItem().getItem() instanceof ShieldItem
            || mc.player.getMainHandItem().getItem() instanceof ShieldItem;
    }

    private boolean detectThreat() {
        Vec3 eyes = mc.player.getEyePosition();

        if (threatMode.get() == ThreatMode.All || threatMode.get() == ThreatMode.Projectiles) {
            if (detectProjectileThreat(eyes)) return true;
        }

        if (threatMode.get() == ThreatMode.All || threatMode.get() == ThreatMode.Melee) {
            if (detectMeleeThreat(eyes)) return true;
        }

        return false;
    }

    private boolean detectProjectileThreat(Vec3 eyes) {
        double detectionRange = projectileRange.get();
        double dotThreshold = projectileDotThreshold.get();

        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
            if (!(entity instanceof Projectile proj)) continue;
            if (proj.getOwner() == mc.player) continue;
            if (proj.tickCount < 0) continue;

            Vec3 projPos = new Vec3(proj.getX(), proj.getY(), proj.getZ());
            Vec3 projVel = proj.getDeltaMovement();
            double dist = projPos.distanceTo(eyes);

            if (dist > detectionRange) continue;

            if (projVel.lengthSqr() < 0.001) continue;

            Vec3 toPlayer = eyes.subtract(projPos).normalize();
            Vec3 projDir = projVel.normalize();
            double dot = toPlayer.dot(projDir);

            if (dot > dotThreshold && dist < 20) {
                return true;
            }

            if (dist < 3.0) {
                return true;
            }
        }

        return false;
    }

    private boolean detectMeleeThreat(Vec3 eyes) {
        double meleeDist = meleeRange.get();

        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;

            if (living instanceof Player p) {
                if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;
                if (p.getAbilities().instabuild) continue;
            }

            double dist = living.distanceTo(mc.player);
            if (dist > meleeDist + 2.0) continue;

            if (living.swinging && dist < meleeDist) {
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

        for (Entity entity : ((meteordevelopment.meteorclient.mixin.LevelAccessor) mc.level).meteor$getEntityLookup().getAll()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;

            ItemStack mainHand = living.getMainHandItem();
            ItemStack offHand = living.getOffhandItem();
            boolean hasAxe = mainHand.getItem() instanceof net.minecraft.world.item.AxeItem
                || offHand.getItem() instanceof net.minecraft.world.item.AxeItem;

            if (hasAxe && living.distanceTo(mc.player) < detectionRange) {
                trackedAxeUsers.add(living.getUUID());
            }
        }
    }

    private void startBlocking() {
        if (mc.player == null) return;

        if (shieldDisableTimer > 0) return;

        boolean offHandShield = mc.player.getOffhandItem().getItem() instanceof ShieldItem;
        boolean mainHandShield = mc.player.getMainHandItem().getItem() instanceof ShieldItem;

        if (offHandShield || mainHandShield) {

            mc.options.keyUse.setDown(true);
            isBlocking = true;
            blockTimer = blockTicks.get();
        }
    }

    private void stopBlocking() {
        if (mc.player == null) return;
        mc.options.keyUse.setDown(false);
        isBlocking = false;
        blockTimer = 0;
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
            return "Blocking (" + blockTimer + "t)";
        }
        if (releaseTimer > 0) {
            return "Releasing (" + releaseTimer + "t)";
        }
        return trackedAxeUsers.isEmpty() ? "Ready" : "Alert (" + trackedAxeUsers.size() + " axes)";
    }
}
