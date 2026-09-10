package orbiter.systems.combat;

public final class SilentRotationHandler {
    private static volatile float pendingYaw;
    private static volatile float pendingPitch;
    private static volatile boolean active;

    private SilentRotationHandler() {
    }

    public static void set(float yaw, float pitch) {
        pendingYaw = yaw;
        pendingPitch = pitch;
        active = true;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static float pendingYaw() {
        return pendingYaw;
    }

    public static float pendingPitch() {
        return pendingPitch;
    }
}
