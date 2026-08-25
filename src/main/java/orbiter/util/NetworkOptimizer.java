package orbiter.util;

import io.netty.channel.Channel;
import io.netty.handler.flush.FlushConsolidationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import orbiter.mixin.ConnectionChannelAccessor;

import java.lang.ref.WeakReference;

public final class NetworkOptimizer {
    private static final String HANDLER_NAME = "orbiter_flush_consolidation";
    private static WeakReference<Connection> lastConnection;

    private NetworkOptimizer() {
    }

    public static void ensureInstalled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;

        Connection connection = mc.getConnection().getConnection();
        if (connection == null) return;
        WeakReference<Connection> ref = lastConnection;
        if (ref != null && ref.get() == connection) return;

        Channel channel = ((ConnectionChannelAccessor) connection).orbiter$getChannel();
        if (channel == null || !channel.isActive()) {
            lastConnection = null;
            return;
        }
        if (channel.pipeline().get(HANDLER_NAME) == null) {
            Runnable install = () -> {
                if (!channel.isActive()) return;
                if (channel.pipeline().get(HANDLER_NAME) == null) {
                    channel.pipeline().addFirst(HANDLER_NAME,
                            new FlushConsolidationHandler(256, true));
                }
            };
            if (channel.eventLoop().inEventLoop()) {
                install.run();
            } else {
                channel.eventLoop().execute(install);
            }
        }
        lastConnection = new WeakReference<>(connection);
    }
}
