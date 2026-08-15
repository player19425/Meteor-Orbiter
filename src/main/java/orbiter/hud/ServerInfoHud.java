package orbiter.hud;

import orbiter.Orbiter;
import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.List;

public class ServerInfoHud extends HudElement {
    public static final HudElementInfo<ServerInfoHud> INFO = new HudElementInfo<>(
        Orbiter.HUD_GROUP, "server-info",
        "Shows server info: brand, version, IP, difficulty, time, anticheats, plugins.",
        ServerInfoHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showBrand = sgGeneral.add(new BoolSetting.Builder()
        .name("show-brand").description("Show server brand.")
        .defaultValue(true).build());

    private final Setting<Boolean> showVersion = sgGeneral.add(new BoolSetting.Builder()
        .name("show-version").description("Show server version.")
        .defaultValue(true).build());

    private final Setting<Boolean> showIp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-ip").description("Show server IP.")
        .defaultValue(true).build());

    private final Setting<Boolean> showProtocol = sgGeneral.add(new BoolSetting.Builder()
        .name("show-protocol").description("Show protocol version.")
        .defaultValue(false).build());

    private final Setting<Boolean> showDifficulty = sgGeneral.add(new BoolSetting.Builder()
        .name("show-difficulty").description("Show world difficulty.")
        .defaultValue(true).build());

    private final Setting<Boolean> showTime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-time").description("Show in-game time (day + time).")
        .defaultValue(true).build());

    private final Setting<Boolean> showAnticheats = sgGeneral.add(new BoolSetting.Builder()
        .name("show-anticheats").description("Show detected anticheats (requires scan).")
        .defaultValue(true).build());

    private final Setting<Boolean> showPlugins = sgGeneral.add(new BoolSetting.Builder()
        .name("show-plugins").description("Show detected plugin count.")
        .defaultValue(true).build());

    private final Setting<Boolean> showSeparator = sgGeneral.add(new BoolSetting.Builder()
        .name("separator").description("Show separator line between sections.")
        .defaultValue(true).build());

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color").description("Component color.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow").description("Draw text shadow.")
        .defaultValue(true).build());

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").description("Component scale.")
        .defaultValue(1.0).min(0.5).sliderRange(0.5, 3.0).build());

    public ServerInfoHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) {
            setSize(0, 0);
            return;
        }

        PeakPluginScanner scanner = null;
        try {
            scanner = Modules.get().get(PeakPluginScanner.class);
        } catch (Exception ignored) {}

        List<String> lines = new ArrayList<>();
        double s = scale.get();

        if (showBrand.get()) {
            String brand = scanner != null ? scanner.getServerBrand() : null;
            lines.add("§bBrand: §f" + (brand != null ? brand : "Unknown"));
        }

        if (showVersion.get()) {
            String ver = scanner != null ? scanner.getServerVersion() : null;
            lines.add("§bVersion: §f" + (ver != null ? ver : "Unknown"));
        }

        if (showIp.get()) {
            String ip = scanner != null ? scanner.getServerIp() : null;
            if (ip == null && mc.getConnection() != null && mc.getConnection().getServerData() != null) {
                ip = mc.getConnection().getServerData().ip;
            }
            lines.add("§bIP: §f" + (ip != null ? ip : "Unknown"));
        }

        if (showProtocol.get()) {
            int proto = scanner != null ? scanner.getProtocolVersion() : 0;
            lines.add("§bProtocol: §f" + (proto > 0 ? proto : "Unknown"));
        }

        if (showDifficulty.get() && mc.level != null) {
            lines.add("§bDifficulty: §f" + mc.level.getDifficulty().getDisplayName());
        }

        if (showTime.get() && mc.level != null) {
            long timeOfDay = mc.level.getLevelData().getGameTime();
            int ticks = (int) (timeOfDay % 24000);
            int day = (int) (timeOfDay / 24000) + 1;
            int hours = ticks / 1000;
            int minutes = (ticks % 1000) * 60 / 1000;
            lines.add(String.format("§bTime: §fDay %d §7- %d:%02d", day, hours, minutes));
        }

        if (showAnticheats.get() && scanner != null) {
            List<String> ac = scanner.getDetectedAnticheats();
            if (!ac.isEmpty()) {
                lines.add("§cAnticheats: §f" + String.join(", ", ac));
            }
        }

        if (showPlugins.get() && scanner != null) {
            int count = scanner.getDetectedCount();
            if (count > 0) {
                lines.add("§aPlugins: §f" + count);
            }
        }

        if (lines.isEmpty()) {
            setSize(0, 0);
            return;
        }

        double maxW = 0;
        double totalH = 0;
        for (String line : lines) {
            double w = renderer.textWidth(line, shadow.get(), s);
            if (w > maxW) maxW = w;
            totalH += renderer.textHeight(shadow.get(), s) + 2;
        }

        setSize(maxW, totalH);

        double cy = y;
        SettingColor color = textColor.get();

        for (int i = 0; i < lines.size(); i++) {
            renderer.text(lines.get(i), x, cy, color, shadow.get(), s);
            cy += renderer.textHeight(shadow.get(), s) + 2;
        }
    }
}
