package orbiter.util;

public final class SafeRegionMath {
    public static final int MIN_WORLD_COORDINATE = -30_000_000;
    public static final int MAX_WORLD_COORDINATE = 30_000_000;

    private SafeRegionMath() {}

    public static boolean validBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return minX <= maxX && minY <= maxY && minZ <= maxZ
            && minX >= MIN_WORLD_COORDINATE && maxX <= MAX_WORLD_COORDINATE
            && minY >= -2048 && maxY <= 2048
            && minZ >= MIN_WORLD_COORDINATE && maxZ <= MAX_WORLD_COORDINATE;
    }
}
