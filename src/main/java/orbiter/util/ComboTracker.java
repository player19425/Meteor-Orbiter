package orbiter.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ComboTracker {
    private static final int MAX_TARGETS = 64;
    private static final Map<UUID, Integer> hits = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return size() > MAX_TARGETS;
        }
    };

    public static void registerHit(UUID target) {
        hits.merge(target, 1, Integer::sum);
    }

    public static int getCombo(UUID target) {
        return hits.getOrDefault(target, 0);
    }

    public static void clear(UUID target) {
        hits.remove(target);
    }

    public static void clearAll() {
        hits.clear();
    }
}
