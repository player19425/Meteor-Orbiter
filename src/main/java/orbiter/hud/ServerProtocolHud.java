package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerProtocolHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerProtocolHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-protocol",
        "Shows the server protocol version.",
        ServerProtocolHud::new
    );

    public ServerProtocolHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        PeakPluginScanner scanner = scanner();
        int protocol = scanner != null ? scanner.getProtocolVersion() : 0;
        if (protocol <= 0 && mc.getConnection() != null && mc.getConnection().getServerData() != null) {
            protocol = mc.getConnection().getServerData().protocol;
        }
        if (protocol <= 0) protocol = net.minecraft.SharedConstants.getProtocolVersion();
        return "Protocol: " + (protocol > 0 ? protocol : "Unknown");
    }
}
