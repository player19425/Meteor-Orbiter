package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerIpHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerIpHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-ip",
        "Shows the real server IP address.",
        ServerIpHud::new
    );

    public ServerIpHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        PeakPluginScanner scanner = scanner();
        String ip = scanner != null ? scanner.getServerIp() : null;
        if (ip == null && mc.getConnection() != null && mc.getConnection().getServerData() != null) {
            ip = mc.getConnection().getServerData().ip;
        }
        return "IP: " + (ip != null ? ip : "Unknown");
    }
}
