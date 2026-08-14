package orbiter.util;

import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.UUID;

public record PlayerSnapshot(String username, UUID uuid, double x, double y, double z,
                             long capturedAt, Quality quality) {
    public enum Quality { LIVE_EXACT, CACHED_EXACT, IDENTITY_ONLY }

    public static PlayerSnapshot live(AbstractClientPlayerEntity player) {
        return new PlayerSnapshot(player.getGameProfile().name(), player.getUuid(), player.getX(), player.getY(),
            player.getZ(), System.currentTimeMillis(), Quality.LIVE_EXACT);
    }

    public static PlayerSnapshot identity(String username, UUID uuid) {
        return new PlayerSnapshot(username, uuid, 0, 0, 0, System.currentTimeMillis(), Quality.IDENTITY_ONLY);
    }
}
