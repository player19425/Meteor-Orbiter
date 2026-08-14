package orbiter.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class FillCommandIterator implements Iterator<String>, Iterable<String> {
    private static final long MAX_VOLUME = 32768L;
    private final Deque<long[]> pending = new ArrayDeque<>();
    private final String block;

    public FillCommandIterator(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String block) {
        if (!SafeRegionMath.validBounds(minX, minY, minZ, maxX, maxY, maxZ)) throw new IllegalArgumentException("Invalid region bounds");
        this.block = block == null || block.isBlank() ? "minecraft:air" : block;
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
                return String.format("fill %d %d %d %d %d %d %s", region[0], region[1], region[2], region[3], region[4], region[5], block);
            }
            long sx = region[3] - region[0], sy = region[4] - region[1], sz = region[5] - region[2];
            if (sx >= sy && sx >= sz) { long mid = region[0] + sx / 2; split(region, 0, mid); }
            else if (sy >= sz) { long mid = region[1] + sy / 2; split(region, 1, mid); }
            else { long mid = region[2] + sz / 2; split(region, 2, mid); }
        }
        throw new NoSuchElementException();
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
