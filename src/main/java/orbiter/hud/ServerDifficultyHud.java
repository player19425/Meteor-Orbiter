package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerDifficultyHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerDifficultyHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-difficulty",
        "Shows the world difficulty.",
        ServerDifficultyHud::new
    );

    public ServerDifficultyHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return "Difficulty: " + mc.level.getDifficulty().getDisplayName();
    }
}
