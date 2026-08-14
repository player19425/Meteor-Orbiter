package orbiter.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import orbiter.Orbiter;
import orbiter.util.ConfigModifier;

public class JumpA extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> requireGrounded = sg.add(new BoolSetting.Builder()
        .name("require-grounded").description("Only jump from the ground.")
        .defaultValue(false).build());

    private final Setting<Boolean> requireForwardInput = sg.add(new BoolSetting.Builder()
        .name("require-forward-input").description("Only jump while holding forward.")
        .defaultValue(false).build());

    private final Setting<Double> velocityCap = sg.add(new DoubleSetting.Builder()
        .name("velocity-cap").description("Maximum upward velocity to apply.")
        .defaultValue(2.0).min(0.42).max(4.0).sliderRange(0.42, 4.0).build());

    private final Setting<Double> lookRange = sg.add(new DoubleSetting.Builder()
        .name("look-range").description("How far to raytrace for parkour target.")
        .defaultValue(32.0).min(4.0).max(64.0).sliderRange(4.0, 64.0).build());

    private final Setting<Boolean> debugMode = sg.add(new BoolSetting.Builder()
        .name("debug").description("Report detection details.")
        .defaultValue(true).build());

    public JumpA() {
        super(Orbiter.CATEGORY_STUPID, "jump-a",
            "Jump over walls or reach blocks you're looking at with calculated velocity.");
    }

    @Override public void onActivate() {
        if (!ConfigModifier.get().stupidModules.get()) { info("Stupid Modules disabled."); toggle(); }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!ConfigModifier.get().stupidModules.get()) { toggle(); return; }

        if (!mc.options.jumpKey.isPressed()) return;
        if (requireGrounded.get() && !mc.player.isOnGround()) return;
        if (requireForwardInput.get() && !mc.player.input.hasForwardMovement()) return;

        BlockPos feet = mc.player.getBlockPos();
        if (isSolid(feet.up(1)) || isSolid(feet.up(2))) {
            if (debugMode.get()) info("Ceiling too low.");
            return;
        }

        double wallHeight = detectWall();
        if (wallHeight > 0) {
            double targetHeight = wallHeight + 0.36;
            double velocity = solveVelocity(targetHeight);
            if (velocity <= velocityCap.get()) {
                applyVelocity(velocity);
                if (debugMode.get()) info("Wall jump: %.1f blocks, velocity %.3f", wallHeight, velocity);
                return;
            }
        }

        double parkourVelocity = calcParkourJump();
        if (parkourVelocity > 0 && parkourVelocity <= velocityCap.get()) {
            applyVelocity(parkourVelocity);
            if (debugMode.get()) info("Parkour jump: velocity %.3f", parkourVelocity);
            return;
        }

        if (debugMode.get() && wallHeight <= 0 && parkourVelocity <= 0) {
            info("No target detected.");
        }
    }

    private double detectWall() {
        double yaw = Math.toRadians(mc.player.getYaw());
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);
        int baseY = mc.player.getBlockPos().getY();
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dist = 1; dist <= 3; dist++) {
            for (double[] off : new double[][]{{0, 0}, {-0.3, 0}, {0.3, 0}}) {
                double sx = mc.player.getX() + fwdX * dist + rightX * off[0];
                double sz = mc.player.getZ() + fwdZ * dist + rightZ * off[0];

                for (int y = baseY; y <= baseY + 5; y++) {
                    m.set((int) Math.floor(sx), y, (int) Math.floor(sz));
                    BlockState state = mc.world.getBlockState(m);
                    if (state.isAir() || state.getCollisionShape(mc.world, m).isEmpty()) continue;

                    int top = y;
                    while (top < baseY + 5) {
                        m.set((int) Math.floor(sx), top + 1, (int) Math.floor(sz));
                        BlockState above = mc.world.getBlockState(m);
                        if (above.isAir() || above.getCollisionShape(mc.world, m).isEmpty()) break;
                        top++;
                    }
                    return top - baseY + 1.0;
                }
            }
        }
        return 0;
    }

    private double calcParkourJump() {
        if (mc.player == null || mc.world == null) return 0;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        BlockHitResult result = mc.world.raycast(new net.minecraft.world.RaycastContext(
            eyePos,
            eyePos.add(look.multiply(lookRange.get())),
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        if (result.getType() != HitResult.Type.BLOCK) return 0;
        if (result.isInsideBlock()) return 0;

        BlockPos target = result.getBlockPos();
        int targetY = target.getY();

        if (targetY <= mc.player.getBlockPos().getY()) return 0;

        double dx = target.getX() + 0.5 - mc.player.getX();
        double dz = target.getZ() + 0.5 - mc.player.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.5 || horizDist > 10) return 0;

        BlockPos landing = target.up();
        if (isSolid(landing) || isSolid(landing.up())) {

            boolean foundLanding = false;
            for (BlockPos adj : new BlockPos[]{
                landing.west(), landing.east(), landing.north(), landing.south()
            }) {
                if (!isSolid(adj) && !isSolid(adj.up())) {
                    foundLanding = true;
                    break;
                }
            }
            if (!foundLanding) return 0;
        }

        double heightDiff = targetY - mc.player.getY();
        if (heightDiff <= 0) return 0;

        return solveVelocity(heightDiff + 0.36);
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private double solveVelocity(double targetHeight) {
        double lo = 0.42, hi = 6.0;
        if (simApex(hi) < targetHeight) return hi + 1;
        for (int i = 0; i < 20; i++) {
            double mid = (lo + hi) * 0.5;
            if (simApex(mid) >= targetHeight) hi = mid;
            else lo = mid;
        }
        return hi;
    }

    private double simApex(double v0) {
        double v = v0, rise = 0, max = 0;
        for (int t = 0; t < 100 && v > -3.92; t++) {
            rise += v;
            max = Math.max(max, rise);
            v = (v - 0.08) * 0.98;
        }
        return max;
    }

    private void applyVelocity(double velocity) {
        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x, velocity, vel.z);
    }
}
