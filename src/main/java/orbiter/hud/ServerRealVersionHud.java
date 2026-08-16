package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerRealVersionHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerRealVersionHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-real-version",
        "Shows the real server version from the server list.",
        ServerRealVersionHud::new
    );

    public ServerRealVersionHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getConnection().getServerData() == null) return null;

        var data = mc.getConnection().getServerData();
        String version = null;
        if (data.version != null) version = data.version.getString();
        if ((version == null || version.isBlank()) && data.status != null) version = data.status.getString();
        if (version == null || version.isBlank()) {
            PeakPluginScanner scanner = scanner();
            if (scanner != null) version = scanner.getServerVersion();
        }
        return "Real Version: " + (version != null && !version.isBlank() ? version : "Unknown");
    }
}
