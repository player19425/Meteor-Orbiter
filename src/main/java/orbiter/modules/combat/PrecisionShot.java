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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
        .name("silent-packet-aim").description("Send look packets without moving camera.").defaultValue(false).build());
    private final Setting<SilentAimMode> silentAimMode = sgGeneral.add(new EnumSetting.Builder<SilentAimMode>()
        .name("silent-aim-mode").defaultValue(SilentAimMode.BallisticCrosshair).visible(silentPacketAim::get).build());
    private final Setting<Boolean> releaseOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("release-only").description("Only silent-aim on bow release.").defaultValue(true).visible(silentPacketAim::get).build());
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

    private final List<Vec3d> points = new ArrayList<>();
    private BlockPos blockHitPos;
    private Box entityHitBox;
    private Vec3d lastHitPos;
    private boolean isSending;
    private PendingSilentShot pendingSilentShot;

    private int aimRecalcTickCounter = 0;

    private static final int AIM_RECALC_INTERVAL_TICKS = 5;

    private AimSolution cachedPreviewAim = null;

    public PrecisionShot() {
        super(Orbiter.CATEGORY, "precision-shot", "Predicts projectile trajectories and supports silent packet aiming.");
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
        if (mc.player == null || mc.world == null) return;
        ItemStack stack = mc.player.getMainHandStack();
        ProjectileProfile profile = getProfile(stack.getItem(), stack);
        if (profile == null && autoOnlySupportedItems.get()) return;
        if (profile == null) profile = new ProjectileProfile(1.5, 0.99, 0.03);

        AimSolution preview = cachedPreviewAim;
        if (preview == null) {
            preview = getPreviewAim(stack, profile);
        }
        simulate(profile, preview != null ? preview.yaw() : mc.player.getYaw(), preview != null ? preview.pitch() : mc.player.getPitch());

        for (int i = 1; i < points.size(); i++) {
            Vec3d a = points.get(i - 1);
            Vec3d b = points.get(i);
            event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, lineColor.get());
        }

        int lw = Math.max(1, (int) Math.round(lineWidth.get()));
        if (blockHitPos != null) {
            event.renderer.box(new Box(blockHitPos).expand(0.001), hitColor.get(), lineColor.get(), ShapeMode.Both, lw);
        }
        if (entityHitBox != null) {
            event.renderer.box(entityHitBox, hitColor.get(), lineColor.get(), ShapeMode.Both, lw);
        }
        if (lastHitPos != null) {
            Box marker = new Box(lastHitPos, lastHitPos).expand(0.07);
            event.renderer.box(marker, hitColor.get(), lineColor.get(), ShapeMode.Both, lw);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        aimRecalcTickCounter++;
        if (aimRecalcTickCounter >= AIM_RECALC_INTERVAL_TICKS) {
            aimRecalcTickCounter = 0;
            ItemStack stack = mc.player.getMainHandStack();
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
        if (!(event.entity instanceof ProjectileEntity projectile) || mc.player == null || mc.world == null) return;
        if (pendingSilentShot == null) return;

        long age = mc.world.getTime() - pendingSilentShot.worldTime();
        if (age < 0 || age > 3) {
            pendingSilentShot = null;
            return;
        }

        if (!matchesPendingShot(projectile, pendingSilentShot)) return;

        projectile.setVelocity(mc.player, pendingSilentShot.pitch(), pendingSilentShot.yaw(), 0.0f, (float) pendingSilentShot.profile().speed(), 0.0f);
        projectile.setYaw(pendingSilentShot.yaw());
        projectile.setPitch(pendingSilentShot.pitch());

        if (projectile instanceof PersistentProjectileEntity persistent) {
            persistent.setVelocityClient(projectile.getVelocity());
        }
        pendingSilentShot = null;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!silentPacketAim.get() || mc.player == null || mc.getNetworkHandler() == null) return;
        if (isSending) return;

        if (event.packet instanceof PlayerActionC2SPacket actionPacket
            && actionPacket.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            ItemStack stack = mc.player.getMainHandStack();
            if (isSupportedItem(stack.getItem())) performSilentAim(stack);
            return;
        }

        if (releaseOnly.get()) return;
        if (!(event.packet instanceof PlayerInteractItemC2SPacket interactPacket)) return;

        ItemStack stack = interactPacket.getHand() == Hand.MAIN_HAND
            ? mc.player.getMainHandStack()
            : mc.player.getOffHandStack();
        if (isSupportedItem(stack.getItem())) performSilentAim(stack);
    }

    private void performSilentAim(ItemStack stack) {
        ProjectileProfile profile = getProfile(stack.getItem(), stack);
        if (profile == null) return;
        AimSolution solution = getSilentAimSolution(stack, profile);
        if (solution == null) return;

        pendingSilentShot = new PendingSilentShot(solution.yaw(), solution.pitch(), stack.getItem(), profile, mc.world.getTime());
        sendLook(solution.yaw(), solution.pitch());
    }

    private AimSolution getPreviewAim(ItemStack stack, ProjectileProfile profile) {
        if (!silentPacketAim.get() || !isSupportedItem(stack.getItem())) return null;
        if (silentAimMode.get() == SilentAimMode.PredictedHit) return null;
        return getSilentAimSolution(stack, profile);
    }

    private AimSolution getSilentAimSolution(ItemStack stack, ProjectileProfile profile) {
        Vec3d crosshairTarget = getCrosshairTargetPos(profile);
        Vec3d point = switch (silentAimMode.get()) {
            case PredictedHit -> lastHitPos;
            case CrosshairTarget, BallisticCrosshair -> crosshairTarget != null ? crosshairTarget : lastHitPos;
        };
        if (point == null) return null;
        return silentAimMode.get() == SilentAimMode.BallisticCrosshair
            ? solveBallisticAim(point, profile)
            : solveDirectAim(point);
    }

    private Vec3d getCrosshairTargetPos(ProjectileProfile profile) {
        if (mc.player == null || mc.world == null) return null;
        Vec3d eyes = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        double range = Math.max(32.0, Math.min(crosshairRange.get(), profile.speed * maxSteps.get()));
        Vec3d end = eyes.add(look.multiply(range));

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
            eyes, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        EntityHitResult entityHit = ProjectileUtil.raycast(
            mc.player, eyes, end,
            mc.player.getBoundingBox().stretch(look.multiply(range)).expand(1.0),
            this::canHitEntity, 0.2f);

        Vec3d best = end;
        double bestSq = eyes.squaredDistanceTo(end);
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            best = blockHit.getPos();
            bestSq = eyes.squaredDistanceTo(best);
        }
        if (entityHit != null && eyes.squaredDistanceTo(entityHit.getPos()) < bestSq) {
            best = entityHit.getPos();
        }
        return best;
    }

    private void simulate(ProjectileProfile profile, float yaw, float pitch) {
        points.clear();
        blockHitPos = null;
        entityHitBox = null;
        lastHitPos = null;
        if (mc.player == null || mc.world == null) return;

        Vec3d pos = mc.player.getEyePos();
        Vec3d vel = Vec3d.fromPolar(pitch, yaw).multiply(profile.speed);
        points.add(pos);

        for (int i = 0; i < maxSteps.get(); i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult blockResult = mc.world.raycast(new RaycastContext(
                pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            EntityHitResult entityResult = ProjectileUtil.raycast(
                mc.player, pos, next,
                mc.player.getBoundingBox().stretch(vel).expand(1.0),
                this::canHitEntity, 0.2f);

            if (entityResult != null) {
                points.add(entityResult.getPos());
                entityHitBox = entityResult.getEntity().getBoundingBox();
                lastHitPos = entityResult.getPos();
                return;
            }
            if (blockResult != null && blockResult.getType() == HitResult.Type.BLOCK) {
                points.add(blockResult.getPos());
                blockHitPos = blockResult.getBlockPos();
                lastHitPos = blockResult.getPos();
                return;
            }
            points.add(next);
            pos = next;
            vel = vel.multiply(profile.drag, profile.drag, profile.drag).add(0.0, -profile.gravity, 0.0);
            if (pos.y < mc.world.getBottomY() - 4) return;
        }
    }

    private boolean canHitEntity(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isSpectator() || entity == mc.player) return false;
        if (ignoreFriends.get() && entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        if (onlyPlayers.get() && !(entity instanceof PlayerEntity)) return false;
        return true;
    }

    private boolean isSupportedItem(Item item) {
        return item instanceof BowItem || item == Items.CROSSBOW
            || item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG
            || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private AimSolution solveBallisticAim(Vec3d target, ProjectileProfile profile) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d delta = target.subtract(eyes);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);

        float coarse = findBestPitch(eyes, yaw, target, profile, -89.0f, 89.0f, 1.0f);
        float fine = findBestPitch(eyes, yaw, target, profile, coarse - 1.0f, coarse + 1.0f, 0.05f);
        return new AimSolution(yaw, fine, target);
    }

    private float findBestPitch(Vec3d origin, float yaw, Vec3d target, ProjectileProfile profile, float min, float max, float step) {
        float best = mc.player.getPitch();
        double bestErr = Double.MAX_VALUE;
        for (float pitch = min; pitch <= max; pitch += step) {
            double err = simulateErrorSq(origin, yaw, pitch, target, profile);
            if (err < bestErr) { bestErr = err; best = pitch; }
        }
        return best;
    }

    private double simulateErrorSq(Vec3d origin, float yaw, float pitch, Vec3d target, ProjectileProfile profile) {
        Vec3d pos = origin;
        Vec3d vel = Vec3d.fromPolar(pitch, yaw).multiply(profile.speed);
        double best = pos.squaredDistanceTo(target);

        int steps = Math.min(maxSteps.get(), 120);
        for (int i = 0; i < steps; i++) {
            Vec3d next = pos.add(vel);
            best = Math.min(best, next.squaredDistanceTo(target));
            pos = next;
            vel = vel.multiply(profile.drag, profile.drag, profile.drag).add(0.0, -profile.gravity, 0.0);
            if (pos.y < mc.world.getBottomY() - 4) break;
        }
        return best;
    }

    private AimSolution solveDirectAim(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d delta = target.subtract(eyes);
        double xz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, xz)));
        return new AimSolution(yaw, pitch, target);
    }

    private void sendLook(float yaw, float pitch) {
        if (mc.getNetworkHandler() == null) return;
        isSending = true;
        try {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        } finally { isSending = false; }
    }

    private ProjectileProfile getProfile(Item item, ItemStack stack) {
        if (mc.player == null || stack == null || stack.isEmpty()) return null;
        if (item instanceof BowItem) {
            float pull = 1.0f;
            if (mc.player.isUsingItem() && mc.player.getActiveItem() == stack) {
                int use = stack.getMaxUseTime(mc.player) - mc.player.getItemUseTimeLeft();
                pull = BowItem.getPullProgress(use);
            }
            return new ProjectileProfile(Math.max(0.1, pull * 3.0), 0.99, 0.05);
        }
        if (item == Items.CROSSBOW) return new ProjectileProfile(3.15, 0.99, 0.05);
        if (item == Items.ENDER_PEARL) return new ProjectileProfile(1.5, 0.99, 0.03);
        if (item == Items.SNOWBALL || item == Items.EGG) return new ProjectileProfile(1.5, 0.99, 0.03);
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) return new ProjectileProfile(0.5, 0.95, 0.05);
        return null;
    }

    private boolean matchesPendingShot(ProjectileEntity projectile, PendingSilentShot shot) {
        if (mc.player == null) return false;
        Entity owner = projectile.getOwner();
        if (owner != null && owner != mc.player) return false;
        if (!matchesProjectileType(projectile, shot.item())) return false;
        double maxDistSq = (shot.item() instanceof BowItem || shot.item() == Items.CROSSBOW) ? 25.0 : 16.0;
        return new Vec3d(projectile.getX(), projectile.getY(), projectile.getZ()).squaredDistanceTo(mc.player.getEyePos()) <= maxDistSq;
    }

    private boolean matchesProjectileType(ProjectileEntity projectile, Item item) {
        if (item instanceof BowItem || item == Items.CROSSBOW) return projectile instanceof PersistentProjectileEntity;
        return projectile instanceof ThrownEntity;
    }

    private record AimSolution(float yaw, float pitch, Vec3d target) {}
    private record PendingSilentShot(float yaw, float pitch, Item item, ProjectileProfile profile, long worldTime) {}
    private record ProjectileProfile(double speed, double drag, double gravity) {}
}
