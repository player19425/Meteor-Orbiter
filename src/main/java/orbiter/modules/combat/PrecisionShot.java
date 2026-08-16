package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.List;

public class PrecisionShot extends Module {

    public enum SilentAimMode {
        PredictedHit,
        CrosshairTarget,
        BallisticCrosshair
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxSteps = sgGeneral.add(new IntSetting.Builder()
        .name("steps").description("Simulation steps.").defaultValue(350).min(20).sliderRange(20, 500).build());
    private final Setting<Double> crosshairRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("crosshair-range").description("Max distance for crosshair raycast.").defaultValue(256.0).min(16.0).sliderRange(16.0, 512.0).build());
    private final Setting<Boolean> autoOnlySupportedItems = sgGeneral.add(new BoolSetting.Builder()
        .name("supported-items-only").description("Only render/predict for supported items.").defaultValue(true).build());
    private final Setting<Boolean> silentPacketAim = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-packet-aim").description("Send look packets without moving the camera so projectiles hit where you point.").defaultValue(true).build());
    private final Setting<SilentAimMode> silentAimMode = sgGeneral.add(new EnumSetting.Builder<SilentAimMode>()
        .name("silent-aim-mode").defaultValue(SilentAimMode.BallisticCrosshair).visible(silentPacketAim::get).build());
    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Don't target friends.").defaultValue(true).build());
    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players").description("Only target players.").defaultValue(false).build());
    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(120, 220, 255, 255)).build());
    private final Setting<SettingColor> hitColor = sgGeneral.add(new ColorSetting.Builder()
        .name("hit-color").defaultValue(new SettingColor(255, 70, 70, 80)).build());
    private final Setting<Double> lineWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-width").defaultValue(1.5).min(0.1).sliderRange(0.1, 4.0).build());

    private final List<Vec3> points = new ArrayList<>();
    private BlockPos blockHitPos;
    private AABB entityHitBox;
    private Vec3 lastHitPos;
    private boolean isSending;
    private PendingSilentShot pendingSilentShot;

    private int aimRecalcTickCounter = 0;

    private static final int AIM_RECALC_INTERVAL_TICKS = 5;

    private AimSolution cachedPreviewAim = null;

    public PrecisionShot() {
        super(Orbiter.CATEGORY_WIP, "precision-shot", "Silent aim at your crosshair.");
    }

    @Override
    public void onActivate() {
        points.clear();
        blockHitPos = null;
        entityHitBox = null;
        lastHitPos = null;
        isSending = false;
        pendingSilentShot = null;
        aimRecalcTickCounter = 0;
        cachedPreviewAim = null;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        ProjectileProfile profile = getProfile(stack.getItem(), stack);
        if (profile == null && autoOnlySupportedItems.get()) return;
        if (profile == null) profile = new ProjectileProfile(1.5, 0.99, 0.03);

        AimSolution preview = cachedPreviewAim;
        if (preview == null) {
            preview = getPreviewAim(stack, profile);
        }
        simulate(profile, preview != null ? preview.yaw() : mc.player.getYRot(), preview != null ? preview.pitch() : mc.player.getXRot());

        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, lineColor.get());
        }

        int lw = Math.max(1, (int) Math.round(lineWidth.get()));
        if (blockHitPos != null) {
            event.renderer.box(new AABB(blockHitPos).inflate(0.001), hitColor.get(), lineColor.get(), ShapeMode.Both, lw);
        }
        if (entityHitBox != null) {
            event.renderer.box(entityHitBox, hitColor.get(), lineColor.get(), ShapeMode.Both, lw);
        }
        if (lastHitPos != null) {
            AABB marker = new AABB(lastHitPos, lastHitPos).inflate(0.07);
            event.renderer.box(marker, hitColor.get(), lineColor.get(), ShapeMode.Both, lw);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        aimRecalcTickCounter++;
        if (aimRecalcTickCounter >= AIM_RECALC_INTERVAL_TICKS) {
            aimRecalcTickCounter = 0;
            ItemStack stack = mc.player.getMainHandItem();
            ProjectileProfile profile = getProfile(stack.getItem(), stack);
            if (profile == null) {
                cachedPreviewAim = null;
            } else {
                cachedPreviewAim = getPreviewAim(stack, profile);
            }
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof Projectile projectile) || mc.player == null || mc.level == null) return;
        if (pendingSilentShot == null) return;

        long age = mc.level.getGameTime() - pendingSilentShot.worldTime();
        if (age < 0 || age > 3) {
            pendingSilentShot = null;
            return;
        }

        if (!matchesPendingShot(projectile, pendingSilentShot)) return;

        projectile.shootFromRotation(mc.player, pendingSilentShot.pitch(), pendingSilentShot.yaw(), 0.0f, (float) pendingSilentShot.profile().speed(), 0.0f);
        projectile.setYRot(pendingSilentShot.yaw());
        projectile.setXRot(pendingSilentShot.pitch());

        if (projectile instanceof AbstractArrow persistent) {
            persistent.setDeltaMovement(projectile.getDeltaMovement());
        }
        pendingSilentShot = null;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!silentPacketAim.get() || mc.player == null || mc.getConnection() == null) return;
        if (isSending) return;

        if (event.packet instanceof ServerboundPlayerActionPacket actionPacket
            && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            ItemStack stack = mc.player.getMainHandItem();
            if (isSupportedItem(stack.getItem())) performSilentAim(stack);
            return;
        }

        if (!(event.packet instanceof ServerboundUseItemPacket interactPacket)) return;

        ItemStack stack = interactPacket.getHand() == InteractionHand.MAIN_HAND
            ? mc.player.getMainHandItem()
            : mc.player.getOffhandItem();
        if (isThrowable(stack.getItem())) performSilentAim(stack);
    }

    private void performSilentAim(ItemStack stack) {
        ProjectileProfile profile = getProfile(stack.getItem(), stack);
        if (profile == null) return;
        AimSolution solution = getSilentAimSolution(stack, profile);
        if (solution == null) return;

        pendingSilentShot = new PendingSilentShot(solution.yaw(), solution.pitch(), stack.getItem(), profile, mc.level.getGameTime());
        sendLook(solution.yaw(), solution.pitch());
    }

    private AimSolution getPreviewAim(ItemStack stack, ProjectileProfile profile) {
        if (!silentPacketAim.get() || !isSupportedItem(stack.getItem())) return null;
        if (silentAimMode.get() == SilentAimMode.PredictedHit) return null;
        return getSilentAimSolution(stack, profile);
    }

    private AimSolution getSilentAimSolution(ItemStack stack, ProjectileProfile profile) {
        Vec3 crosshairTarget = getCrosshairTargetPos(profile);
        Vec3 point = switch (silentAimMode.get()) {
            case PredictedHit -> lastHitPos;
            case CrosshairTarget, BallisticCrosshair -> crosshairTarget != null ? crosshairTarget : lastHitPos;
        };
        if (point == null) return null;
        return silentAimMode.get() == SilentAimMode.BallisticCrosshair
            ? solveBallisticAim(point, profile)
            : solveDirectAim(point);
    }

    private Vec3 getCrosshairTargetPos(ProjectileProfile profile) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        double range = Math.max(32.0, Math.min(crosshairRange.get(), profile.speed * maxSteps.get()));
        Vec3 end = eyes.add(look.scale(range));

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
            eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            mc.player, eyes, end,
            mc.player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
            this::canHitEntity, 0.2f);

        Vec3 best = end;
        double bestSq = eyes.distanceToSqr(end);
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            best = blockHit.getLocation();
            bestSq = eyes.distanceToSqr(best);
        }
        if (entityHit != null && eyes.distanceToSqr(entityHit.getLocation()) < bestSq) {
            best = entityHit.getLocation();
        }
        return best;
    }

    private void simulate(ProjectileProfile profile, float yaw, float pitch) {
        points.clear();
        blockHitPos = null;
        entityHitBox = null;
        lastHitPos = null;
        if (mc.player == null || mc.level == null) return;

        Vec3 pos = mc.player.getEyePosition();
        Vec3 vel = Vec3.directionFromRotation(pitch, yaw).scale(profile.speed);
        points.add(pos);

        for (int i = 0; i < maxSteps.get(); i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult blockResult = mc.level.clip(new ClipContext(
                pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            EntityHitResult entityResult = ProjectileUtil.getEntityHitResult(
                mc.player, pos, next,
                mc.player.getBoundingBox().expandTowards(vel).inflate(1.0),
                this::canHitEntity, 0.2f);

            if (entityResult != null) {
                points.add(entityResult.getLocation());
                entityHitBox = entityResult.getEntity().getBoundingBox();
                lastHitPos = entityResult.getLocation();
                return;
            }
            if (blockResult != null && blockResult.getType() == HitResult.Type.BLOCK) {
                points.add(blockResult.getLocation());
                blockHitPos = blockResult.getBlockPos();
                lastHitPos = blockResult.getLocation();
                return;
            }
            points.add(next);
            pos = next;
            vel = vel.multiply(profile.drag, profile.drag, profile.drag).add(0.0, -profile.gravity, 0.0);
            if (pos.y < mc.level.getMinY() - 4) return;
        }
    }

    private boolean canHitEntity(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator() || entity == mc.player) return false;
        if (ignoreFriends.get() && entity instanceof Player player && Friends.get().isFriend(player)) return false;
        if (onlyPlayers.get() && !(entity instanceof Player)) return false;
        return true;
    }

    private boolean isSupportedItem(Item item) {
        return item instanceof BowItem || item == Items.CROSSBOW || isThrowable(item);
    }

    private boolean isThrowable(Item item) {
        return item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG
            || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private AimSolution solveBallisticAim(Vec3 target, ProjectileProfile profile) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 delta = target.subtract(eyes);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);

        float coarse = findBestPitch(eyes, yaw, target, profile, -89.0f, 89.0f, 1.0f);
        float fine = findBestPitch(eyes, yaw, target, profile, coarse - 1.0f, coarse + 1.0f, 0.05f);
        return new AimSolution(yaw, fine, target);
    }

    private float findBestPitch(Vec3 origin, float yaw, Vec3 target, ProjectileProfile profile, float min, float max, float step) {
        float best = mc.player.getXRot();
        double bestErr = Double.MAX_VALUE;
        for (float pitch = min; pitch <= max; pitch += step) {
            double err = simulateErrorSq(origin, yaw, pitch, target, profile);
            if (err < bestErr) { bestErr = err; best = pitch; }
        }
        return best;
    }

    private double simulateErrorSq(Vec3 origin, float yaw, float pitch, Vec3 target, ProjectileProfile profile) {
        Vec3 pos = origin;
        Vec3 vel = Vec3.directionFromRotation(pitch, yaw).scale(profile.speed);
        double best = pos.distanceToSqr(target);

        int steps = Math.min(maxSteps.get(), 120);
        for (int i = 0; i < steps; i++) {
            Vec3 next = pos.add(vel);
            best = Math.min(best, next.distanceToSqr(target));
            pos = next;
            vel = vel.multiply(profile.drag, profile.drag, profile.drag).add(0.0, -profile.gravity, 0.0);
            if (pos.y < mc.level.getMinY() - 4) break;
        }
        return best;
    }

    private AimSolution solveDirectAim(Vec3 target) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 delta = target.subtract(eyes);
        double xz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, xz)));
        return new AimSolution(yaw, pitch, target);
    }

    private void sendLook(float yaw, float pitch) {
        if (mc.getConnection() == null) return;
        isSending = true;
        try {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        } finally { isSending = false; }
    }

    private ProjectileProfile getProfile(Item item, ItemStack stack) {
        if (mc.player == null || stack == null || stack.isEmpty()) return null;
        if (item instanceof BowItem) {
            float pull = 1.0f;
            if (mc.player.isUsingItem() && mc.player.getActiveItem() == stack) {
                int use = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
                pull = BowItem.getPowerForTime(use);
            }
            return new ProjectileProfile(Math.max(0.1, pull * 3.0), 0.99, 0.05);
        }
        if (item == Items.CROSSBOW) return new ProjectileProfile(3.15, 0.99, 0.05);
        if (item == Items.ENDER_PEARL) return new ProjectileProfile(1.5, 0.99, 0.03);
        if (item == Items.SNOWBALL || item == Items.EGG) return new ProjectileProfile(1.5, 0.99, 0.03);
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) return new ProjectileProfile(0.5, 0.95, 0.05);
        return null;
    }

    private boolean matchesPendingShot(Projectile projectile, PendingSilentShot shot) {
        if (mc.player == null) return false;
        Entity owner = projectile.getOwner();
        if (owner != null && owner != mc.player) return false;
        if (!matchesProjectileType(projectile, shot.item())) return false;
        double maxDistSq = (shot.item() instanceof BowItem || shot.item() == Items.CROSSBOW) ? 25.0 : 16.0;
        return new Vec3(projectile.getX(), projectile.getY(), projectile.getZ()).distanceToSqr(mc.player.getEyePosition()) <= maxDistSq;
    }

    private boolean matchesProjectileType(Projectile projectile, Item item) {
        if (item instanceof BowItem || item == Items.CROSSBOW) return projectile instanceof AbstractArrow;
        return projectile instanceof ThrowableProjectile;
    }

    private record AimSolution(float yaw, float pitch, Vec3 target) {}
    private record PendingSilentShot(float yaw, float pitch, Item item, ProjectileProfile profile, long worldTime) {}
    private record ProjectileProfile(double speed, double drag, double gravity) {}
}
