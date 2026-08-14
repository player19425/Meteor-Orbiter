package orbiter.util;

public final class SafeRegionMath {
    public static final int MIN_WORLD_COORDINATE = -30_000_000;
    public static final int MAX_WORLD_COORDINATE = 30_000_000;

    private SafeRegionMath() {}

    public static long checkedAdd(long left, long right) { return Math.addExact(left, right); }
    public static long checkedMultiply(long left, long right) { return Math.multiplyExact(left, right); }
    public static int clampCoordinate(long value) {
        return (int) Math.max(MIN_WORLD_COORDINATE, Math.min(MAX_WORLD_COORDINATE, value));
    }
    public static long inclusiveLength(int min, int max) { return (long) max - min + 1L; }
    public static long volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return Math.multiplyExact(Math.multiplyExact(inclusiveLength(minX, maxX), inclusiveLength(minY, maxY)), inclusiveLength(minZ, maxZ));
    }
    public static boolean validBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return minX <= maxX && minY <= maxY && minZ <= maxZ
            && minX >= MIN_WORLD_COORDINATE && maxX <= MAX_WORLD_COORDINATE
            && minZ >= MIN_WORLD_COORDINATE && maxZ <= MAX_WORLD_COORDINATE;
    }
}
