package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;

public class RenderDistanceHud extends HudElement {
    public static final HudElementInfo<RenderDistanceHud> INFO = new HudElementInfo<>(Orbiter.HUD_GROUP, "render-distance", "Shows current render distance.", RenderDistanceHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    public RenderDistanceHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int dist = mc.options != null && mc.options.getViewDistance() != null ? mc.options.getViewDistance().getValue() : 0;
        String text = dist + " chunks";

        double s = scale.get();
        double w = renderer.textWidth(text, shadow.get(), s);
        double h = renderer.textHeight(shadow.get(), s);
        setSize(w, h);

        SettingColor color = rainbow.get() ? rainbowColor() : textColor.get();
        renderer.text(text, x, y, color, shadow.get(), s);
    }

    private SettingColor rainbowColor() {
        long millis = System.currentTimeMillis();
        float hue = (millis % 4000L) / 4000f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }
}
