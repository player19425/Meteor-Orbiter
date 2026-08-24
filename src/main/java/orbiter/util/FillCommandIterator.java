package orbiter.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class FillCommandIterator implements Iterator<String>, Iterable<String> {
    private static final long MAX_VOLUME = 32768L;
    private final Deque<long[]> pending = new ArrayDeque<>();
    private final String block;

    public interface ChunkVisibility {
        boolean isLoaded(int chunkX, int chunkZ);
    }

    private final ChunkVisibility visibility;
    public long skippedUnloaded;

    public FillCommandIterator(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String block) {
        this(minX, minY, minZ, maxX, maxY, maxZ, block, null);
    }

    public FillCommandIterator(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String block,
            ChunkVisibility visibility) {
        if (!SafeRegionMath.validBounds(minX, minY, minZ, maxX, maxY, maxZ)) throw new IllegalArgumentException("Invalid region bounds");
        this.block = block == null || block.isBlank() ? "minecraft:air" : block;
        this.visibility = visibility;
        pending.add(new long[]{minX, minY, minZ, maxX, maxY, maxZ});
    }

    @Override public boolean hasNext() { return !pending.isEmpty(); }

    @Override public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        while (!pending.isEmpty()) {
            long[] region = pending.removeFirst();
            long volume = Math.multiplyExact(
                Math.multiplyExact(region[3] - region[0] + 1, region[4] - region[1] + 1),
                region[5] - region[2] + 1);
            if (volume <= MAX_VOLUME) {
                if (visibility != null && !fullyVisible(region)) {
                    skippedUnloaded++;
                    continue;
                }
                return format(region);
            }
            splitLargestAxis(region);
        }
        throw new NoSuchElementException();
    }

    private boolean fullyVisible(long[] r) {
        int minCx = (int) (r[0] >> 4), maxCx = (int) (r[3] >> 4);
        int minCz = (int) (r[2] >> 4), maxCz = (int) (r[5] >> 4);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!visibility.isLoaded(cx, cz)) return false;
            }
        }
        return true;
    }

    private void splitAtChunkBoundary(long[] r) {
        long bX = ((r[0] >> 4) + 1) << 4;
        if (bX <= r[3]) {
            long[] a = r.clone(), b = r.clone();
            a[3] = bX - 1;
            b[0] = bX;
            pending.addFirst(b); pending.addFirst(a);
            return;
        }

        long bZ = ((r[2] >> 4) + 1) << 4;
        if (bZ <= r[5]) {
            long[] a = r.clone(), b = r.clone();
            a[5] = bZ - 1;
            b[2] = bZ;
            pending.addFirst(b); pending.addFirst(a);
        }
    }

    private void splitLargestAxis(long[] region) {
        if (visibility != null) {
            long bX = ((region[0] >> 4) + 1) << 4;
            long bZ = ((region[2] >> 4) + 1) << 4;
            if (bX <= region[3] || bZ <= region[5]) {
                splitAtChunkBoundary(region);
                return;
            }
            split(region, 1, region[1] + (region[4] - region[1]) / 2);
            return;
        }
        long sx = region[3] - region[0], sy = region[4] - region[1], sz = region[5] - region[2];
        if (sx >= sy && sx >= sz) { long mid = region[0] + sx / 2; split(region, 0, mid); }
        else if (sy >= sz) { long mid = region[1] + sy / 2; split(region, 1, mid); }
        else { long mid = region[2] + sz / 2; split(region, 2, mid); }
    }

    private String format(long[] r) {
        return String.format("fill %d %d %d %d %d %d %s", r[0], r[1], r[2], r[3], r[4], r[5], block);
    }

    private void split(long[] r, int axis, long mid) {
        long[] a = r.clone(), b = r.clone();
        if (axis == 0) { a[3] = mid; b[0] = mid + 1; }
        else if (axis == 1) { a[4] = mid; b[1] = mid + 1; }
        else { a[5] = mid; b[2] = mid + 1; }
        pending.addFirst(b); pending.addFirst(a);
    }

    @Override public Iterator<String> iterator() { return this; }
}
