package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

public class ServerVersionNoteHud extends BaseServerInfoHud {
    public static final HudElementInfo<ServerVersionNoteHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-version-note",
        "Shows a note when the server runs a different version (protocol bridge).",
        ServerVersionNoteHud::new
    );

    public ServerVersionNoteHud() {
        super(INFO);
    }

    @Override
    protected String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getConnection().getServerData() == null) return null;

        var data = mc.getConnection().getServerData();
        if (data.protocol <= 0 || data.protocol == SharedConstants.getProtocolVersion()) return null;

        String version = null;
        if (data.version != null) version = data.version.getString();
        if ((version == null || version.isBlank()) && data.status != null) version = data.status.getString();
        if (version == null || version.isBlank()) version = "protocol " + data.protocol;

        return "Version note: " + version + " (via bridge)";
    }
}
