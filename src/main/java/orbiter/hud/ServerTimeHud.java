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

        long clockTime = mc.level.getOverworldClockTime();
        int ticks = (int) ((clockTime % 24000L + 24000L + 6000L) % 24000L);
        int day = (int) (clockTime / 24000L) + 1;
        int hours = ticks / 1000;
        int minutes = (ticks % 1000) * 60 / 1000;
        return String.format("Time Day %d (%d:%02d)", day, hours, minutes);
    }
}
