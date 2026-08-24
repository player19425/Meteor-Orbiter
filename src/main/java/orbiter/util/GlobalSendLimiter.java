package orbiter.util;

public final class GlobalSendLimiter {
    private static final long TICK_NANOS = 50_000_000L;
    private static final int MAX_BURST_TICKS = 8;

    private static volatile int perTick = 32768;
    private static long windowStart;
    private static int tokens;

    private GlobalSendLimiter() {
    }

    public static void setPerTick(int value) {
        perTick = Math.max(1, value);
    }

    public static int perTick() {
        return perTick;
    }

    public static synchronized int acquire(int wanted) {
        if (wanted <= 0) return 0;
        refill();
        int granted = Math.min(wanted, tokens);
        tokens -= granted;
        return granted;
    }

    public static boolean tryAcquireOne() {
        return acquire(1) >= 1;
    }

    private static void refill() {
        long now = System.nanoTime();
        if (windowStart == 0) {
            windowStart = now;
            tokens = perTick;
            return;
        }
        long elapsed = now - windowStart;
        if (elapsed < TICK_NANOS) return;
        int ticks = (int) Math.min(MAX_BURST_TICKS, elapsed / TICK_NANOS);
        tokens = Math.min(perTick * MAX_BURST_TICKS, tokens + perTick * ticks);
        windowStart = now;
    }
}
