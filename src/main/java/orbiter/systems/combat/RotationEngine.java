package orbiter.systems.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class RotationEngine {
    private enum State {
        Idle,
        Turning,
        Hold
    }

    private static final int TIMEOUT_TICKS = 60;

    private final Minecraft mc;

    private State state = State.Idle;
    private float currentYaw;
    private float currentPitch;
    private float targetYaw;
    private float targetPitch;

    private double velYaw;
    private double velPitch;
    private double stiffness;
    private double damping;
    private double overshoot;
    private double jitterYaw;
    private double jitterPitch;
    private double maxStep;

    private CombatRequest request;
    private CombatRequest.Mode mode;
    private int holdTicks;
    private int ticks;
    private long seed;
    private boolean fired;

    public RotationEngine(Minecraft mc) {
        this.mc = mc;
        this.seed = (long) (Math.random() * 100000);
        this.stiffness = 0.55;
        this.damping = 0.75;
    }

    public void begin(CombatRequest req, float fromYaw, float fromPitch, double yaw, double pitch) {
        boolean sameGoal = request != null
            && request.owner() == req.owner()
            && request.targetPoint() != null
            && request.targetPoint().equals(req.targetPoint());

        request = req;
        mode = req.mode();
        CombatRequest.Profile p = req.profile();
        stiffness = Mth.clamp(p.speed() * 2.2, 0.15, 2.0);
        damping = Mth.clamp(p.damping(), 0.3, 1.0);
        overshoot = Mth.clamp(p.overshoot(), 0.0, 1.0);
        jitterYaw = p.jitterYaw();
        jitterPitch = p.jitterPitch();
        maxStep = Math.max(1.0, p.maxDegreesPerTick());

        targetYaw = Mth.wrapDegrees((float) yaw);
        targetPitch = (float) Mth.clamp(pitch, -90.0, 90.0);

        if (state == State.Idle) {
            currentYaw = Mth.wrapDegrees(fromYaw);
            currentPitch = fromPitch;
            velYaw = 0;
            velPitch = 0;
            fired = false;
            ticks = 0;
        } else if (!sameGoal) {
            fired = false;
            ticks = 0;
        }

        state = State.Turning;
    }

    public void stop() {
        state = State.Idle;
        request = null;
        fired = false;
    }

    public boolean busy() {
        return state != State.Idle;
    }

    public CombatRequest activeRequest() {
        return request;
    }

    public float serverYaw() {
        return currentYaw;
    }

    public float serverPitch() {
        return currentPitch;
    }

    public void step(double gameTime) {
        if (state == State.Idle || request == null) return;

        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            currentYaw = targetYaw;
            currentPitch = targetPitch;
            velYaw = 0;
            velPitch = 0;
            settle();
            return;
        }

        double jYaw = jitterYaw > 0 ? Math.sin(gameTime * 0.37 + seed) * jitterYaw : 0;
        double jPitch = jitterPitch > 0 ? Math.cos(gameTime * 0.29 + seed * 1.7) * jitterPitch : 0;

        double goalYaw = targetYaw + jYaw;
        double goalPitch = Mth.clamp(targetPitch + jPitch, -90.0, 90.0);

        double dYaw = Mth.wrapDegrees(goalYaw - currentYaw);
        double dPitch = goalPitch - currentPitch;

        double accelYaw = dYaw * stiffness - velYaw * damping;
        double accelPitch = dPitch * stiffness - velPitch * damping;

        velYaw += accelYaw;
        velPitch += accelPitch;

        double stepYaw = velYaw;
        double stepPitch = velPitch;

        double mag = Math.sqrt(stepYaw * stepYaw + stepPitch * stepPitch);
        boolean clamped = false;
        if (mag > maxStep) {
            stepYaw = stepYaw / mag * maxStep;
            stepPitch = stepPitch / mag * maxStep;
            velYaw = stepYaw;
            velPitch = stepPitch;
            clamped = true;
        }

        if (state == State.Turning && !clamped && overshoot > 0) {
            double remaining = Math.abs(Mth.wrapDegrees(goalYaw - currentYaw));
            if (remaining < maxStep * 1.5 && Math.abs(dPitch) < maxStep * 0.5) {
                stepYaw *= 1.0 + overshoot;
                stepPitch *= 1.0 + overshoot;
            }
        }

        currentYaw = Mth.wrapDegrees(currentYaw + (float) stepYaw);
        currentPitch = Mth.clamp(currentPitch + (float) stepPitch, -90.0f, 90.0f);

        double errorYaw = Math.abs(Mth.wrapDegrees(targetYaw - currentYaw));
        double errorPitch = Math.abs(targetPitch - currentPitch);
        double tolerance = Math.max(0.1, request.toleranceDeg());

        if (state == State.Turning && errorYaw <= tolerance && errorPitch <= tolerance) {
            currentYaw = targetYaw;
            currentPitch = targetPitch;
            velYaw = 0;
            velPitch = 0;
            settle();
            return;
        }

        if (state == State.Hold) {
            holdTicks--;
            if (holdTicks <= 0) stop();
        }
    }

    private void settle() {
        state = State.Hold;
        holdTicks = 2;
        if (!fired) {
            fired = true;
            if (request.onAimed() != null) request.onAimed().run();
        }
    }

    public void apply() {
        if (state == State.Idle || request == null || mc.player == null) return;

        if (mode == CombatRequest.Mode.Visible) {
            SilentRotationHandler.clear();
            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
        } else {
            SilentRotationHandler.set(currentYaw, currentPitch);
        }
    }

    public static double yawTo(Minecraft mc, Vec3 point) {
        double dx = point.x - mc.player.getX();
        double dz = point.z - mc.player.getZ();
        return Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    public static double pitchTo(Minecraft mc, Vec3 point) {
        double dx = point.x - mc.player.getX();
        double dy = point.y - mc.player.getEyeY();
        double dz = point.z - mc.player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return -Math.toDegrees(Math.atan2(dy, horizontal));
    }
}
