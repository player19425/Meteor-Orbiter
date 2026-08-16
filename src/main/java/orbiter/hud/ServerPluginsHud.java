package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;

public class ServerPluginsHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerPluginsHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-plugins",
        "Shows the detected plugin count.",
        ServerPluginsHud::new
    );

    public ServerPluginsHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        PeakPluginScanner scanner = scanner();
        if (scanner == null) return null;

        int count = scanner.getDetectedCount();
        return count > 0 ? "Plugins: " + count : null;
    }
}
