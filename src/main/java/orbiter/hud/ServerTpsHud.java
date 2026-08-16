package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.utils.world.TickRate;

public class ServerTpsHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerTpsHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-tps",
        "Shows the server ticks per second.",
        ServerTpsHud::new
    );

    public ServerTpsHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        float tps = TickRate.INSTANCE.getTickRate();
        return "TPS: " + (tps > 0 ? String.format("%.1f", tps) : "N/A");
    }
}
