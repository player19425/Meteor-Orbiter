package orbiter.util;

import io.netty.channel.Channel;
import io.netty.handler.flush.FlushConsolidationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import orbiter.mixin.ConnectionChannelAccessor;

public final class NetworkOptimizer {
    private static final String HANDLER_NAME = "orbiter_flush_consolidation";
    private static Connection lastConnection;

    private NetworkOptimizer() {
    }

    public static void ensureInstalled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        if (connection == null) return;
        if (connection == lastConnection) return;

        Channel channel = ((ConnectionChannelAccessor) connection).orbiter$getChannel();
        if (channel == null || !channel.isActive()) return;
        if (channel.pipeline().get(HANDLER_NAME) == null) {
            channel.pipeline().addFirst(HANDLER_NAME,
                    new FlushConsolidationHandler(256, true));
        }
        lastConnection = connection;
    }
}
