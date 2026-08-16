package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.client.Minecraft;

public class ServerBrandHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerBrandHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-brand",
        "Shows the server brand.",
        ServerBrandHud::new
    );

    public ServerBrandHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        PeakPluginScanner scanner = scanner();
        String brand = scanner != null ? scanner.getServerBrand() : null;
        return "Brand: " + (brand != null ? brand : "Unknown");
    }
}
