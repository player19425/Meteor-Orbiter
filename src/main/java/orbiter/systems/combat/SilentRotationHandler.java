package orbiter.systems.combat;

public final class SilentRotationHandler {
    private static volatile float yaw;
    private static volatile float pitch;

    private SilentRotationHandler() {
    }

    public static void set(float yaw, float pitch) {
        SilentRotationHandler.yaw = yaw;
        SilentRotationHandler.pitch = pitch;
    }

    public static void clear() {
        yaw = 0.0f;
        pitch = 0.0f;
    }

    public static float pendingYaw() {
        return yaw;
    }

    public static float pendingPitch() {
        return pitch;
    }
}