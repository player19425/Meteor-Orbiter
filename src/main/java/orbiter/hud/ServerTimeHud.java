package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerTimeHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerTimeHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-time",
        "Shows the in-game day and time.",
        ServerTimeHud::new
    );

    public ServerTimeHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        long timeOfDay = mc.level.getLevelData().getGameTime();
        int ticks = (int) (timeOfDay % 24000);
        int day = (int) (timeOfDay / 24000) + 1;
        int hours = ticks / 1000;
        int minutes = (ticks % 1000) * 60 / 1000;
        return String.format("Time Day %d (%d:%02d)", day, hours, minutes);
    }
}
