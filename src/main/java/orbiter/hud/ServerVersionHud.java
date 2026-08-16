package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerVersionHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerVersionHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-version",
        "Shows the server version.",
        ServerVersionHud::new
    );

    public ServerVersionHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        PeakPluginScanner scanner = scanner();
        String version = scanner != null ? scanner.getServerVersion() : null;
        return "Version: " + (version != null ? version : "Unknown");
    }
}
