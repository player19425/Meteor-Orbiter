package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;

import java.util.List;

public class ServerAnticheatHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerAnticheatHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-anticheats",
        "Shows detected anticheats.",
        ServerAnticheatHud::new
    );

    public ServerAnticheatHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        PeakPluginScanner scanner = scanner();
        if (scanner == null) return null;

        List<String> anticheats = scanner.getDetectedAnticheats();
        if (anticheats == null || anticheats.isEmpty()) return null;
        return "Anticheats: " + String.join(", ", anticheats);
    }
}
