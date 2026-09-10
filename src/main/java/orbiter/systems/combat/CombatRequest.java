package orbiter.systems.combat;

import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.systems.modules.Module;

public record CombatRequest(
    Module owner,
    int priority,
    Vec3 targetPoint,
    Double pitchOverride,
    Mode mode,
    Profile profile,
    Runnable onAimed,
    double toleranceDeg
) {
    public enum Mode {
        Visible,
        Silent
    }

    public record Profile(
        double speed,
        double damping,
        double jitterYaw,
        double jitterPitch,
        double overshoot,
        double maxDegreesPerTick
    ) {
    }

    public static CombatRequest rotation(Module owner, int priority, Vec3 targetPoint, Mode mode, Profile profile, Runnable onAimed) {
        return new CombatRequest(owner, priority, targetPoint, null, mode, profile, onAimed, 3.0);
    }

    public static CombatRequest rotation(Module owner, int priority, Vec3 targetPoint, Double pitchOverride, Mode mode, Profile profile, Runnable onAimed) {
        return new CombatRequest(owner, priority, targetPoint, pitchOverride, mode, profile, onAimed, 3.0);
    }
}
