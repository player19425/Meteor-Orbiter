package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public class CustomTextHud extends HudElement {
    public static final HudElementInfo<CustomTextHud> INFO = new HudElementInfo<>(Orbiter.HUD_GROUP, "custom-text", "Displays custom text on the HUD with placeholders.", CustomTextHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> text = sgGeneral.add(new StringSetting.Builder()
        .name("text")
        .description("Custom text. Placeholders: {player}, {health}, {health%}, {armor}, {x}, {y}, {z}, {tps}, {ping}, {fps}, {dimension}")
        .defaultValue("{player} | {health}/{health%}HP | {tps} TPS | {ping}ms")
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Text color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Boolean> rainbow = sgGeneral.add(new BoolSetting.Builder()
        .name("rainbow")
        .description("Cycle text color through the rainbow.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draw text shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 3.0)
        .build()
    );

    public CustomTextHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        String display = replacePlaceholders(text.get());
        if (display == null || display.isBlank()) display = " ";

        double s = scale.get();
        double w = renderer.textWidth(display, shadow.get(), s);
        double h = renderer.textHeight(shadow.get(), s);
        setSize(w, h);

        SettingColor color = rainbow.get() ? rainbowColor() : textColor.get();
        renderer.text(display, x, y, color, shadow.get(), s);
    }

    private String replacePlaceholders(String input) {
        if (input == null) return "";
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;

        String out = input;
        out = out.replace("{player}", p != null ? p.getName().getString() : "?");
        out = out.replace("{health}", p != null ? String.format("%.1f", p.getHealth()) : "?");
        out = out.replace("{health%}", p != null ? String.format("%.1f", p.getMaxHealth()) : "?");
        out = out.replace("{armor}", p != null ? String.format("%.0f", (float) p.getArmor()) : "?");
        out = out.replace("{x}", p != null ? String.format("%.1f", p.getX()) : "?");
        out = out.replace("{y}", p != null ? String.format("%.1f", p.getY()) : "?");
        out = out.replace("{z}", p != null ? String.format("%.1f", p.getZ()) : "?");

        float tps = TickRate.INSTANCE.getTickRate();
        out = out.replace("{tps}", String.format("%.1f", tps));

        int ping = -1;
        if (p != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) != null) {
            ping = mc.getNetworkHandler().getPlayerListEntry(p.getUuid()).getLatency();
        }
        out = out.replace("{ping}", ping >= 0 ? String.valueOf(ping) : "?");

        out = out.replace("{fps}", String.valueOf(mc.getCurrentFps()));
        out = out.replace("{dimension}", mc.world != null ? mc.world.getRegistryKey().getValue().getPath() : "?");

        return out;
    }

    private SettingColor rainbowColor() {
        long millis = System.currentTimeMillis();
        float hue = (millis % 4000L) / 4000f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }
}
