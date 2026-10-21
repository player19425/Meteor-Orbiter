package orbiter.util;

public final class GlobalSendLimiter {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final int MAX_RESERVATION = 6;

    private static volatile double permitsPerSecond = 20.0;
    private static double tokens;
    private static long windowStart;

    private GlobalSendLimiter() {
    }

    public static void setPermitsPerSecond(double value) {
        synchronized (GlobalSendLimiter.class) {
            permitsPerSecond = Double.isFinite(value) && value > 0 ? value : 20.0;
            tokens = Math.min(tokens, budget());
        }
    }

    public static double permitsPerSecond() {
        return permitsPerSecond;
    }

    public static synchronized boolean tryAcquireOne() {
        return tryAcquire(1);
    }

    public static synchronized boolean tryAcquire(int permits) {
        if (permits <= 0) return true;
        refill();
        if (tokens < permits) return false;
        tokens -= permits;
        return true;
    }

    private static double budget() {
        return Math.max(permitsPerSecond, MAX_RESERVATION);
    }

    private static void refill() {
        long now = System.nanoTime();
        if (windowStart == 0) {
            windowStart = now;
            tokens = budget();
            return;
        }

        double elapsed = (now - windowStart) / NANOS_PER_SECOND;
        if (elapsed <= 0) return;
        windowStart = now;
        tokens = Math.min(budget(), tokens + elapsed * permitsPerSecond);
    }
}
