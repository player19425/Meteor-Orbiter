package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerPlayersHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerPlayersHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-players",
        "Shows the number of online players.",
        ServerPlayersHud::new
    );

    public ServerPlayersHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        return "Players: " + mc.getConnection().getOnlinePlayers().size();
    }
}
