package orbiter.util;

import java.util.Locale;
import java.util.UUID;

public final class EntityNbtBuilder {
    private EntityNbtBuilder() {}

    public static String resilient(UUID uuid, String username, boolean glowing) {
        return String.format(Locale.ROOT,
            "{UUID:[I;%d,%d,%d,%d],Tags:[\"orbiter_uuidban\"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b,Glowing:%db,CustomName:'{\"text\":\"%s\"}'}",
            (int) (uuid.getMostSignificantBits() >>> 32), (int) uuid.getMostSignificantBits(),
            (int) (uuid.getLeastSignificantBits() >>> 32), (int) uuid.getLeastSignificantBits(),
            glowing ? 1 : 0, CommandUtils.escapeJson(username));
    }

    public static String armorStand(UUID uuid, String username, boolean glowing) {
        return String.format(Locale.ROOT,
            "{UUID:[I;%d,%d,%d,%d],Tags:[\"orbiter_uuidban\"],Invulnerable:1b,NoGravity:1b,PersistenceRequired:1b,Glowing:%db,CustomNameVisible:1b,CustomName:'{\"text\":\"%s\"}'}",
            (int) (uuid.getMostSignificantBits() >>> 32), (int) uuid.getMostSignificantBits(),
            (int) (uuid.getLeastSignificantBits() >>> 32), (int) uuid.getLeastSignificantBits(),
            glowing ? 1 : 0, CommandUtils.escapeJson(username));
    }
}
