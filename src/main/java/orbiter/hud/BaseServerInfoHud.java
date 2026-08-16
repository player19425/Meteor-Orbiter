package orbiter.hud;

import orbiter.modules.misc.PeakPluginScanner;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public abstract class BaseServerInfoHud extends HudElement {
    protected final SettingGroup sgGeneral = settings.getDefaultGroup();

    protected final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Text color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    protected final Setting<Boolean> rainbow = sgGeneral.add(new BoolSetting.Builder()
        .name("rainbow")
        .description("Cycle text color through the rainbow.")
        .defaultValue(false)
        .build()
    );

    protected final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draw text shadow.")
        .defaultValue(true)
        .build()
    );

    protected final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Component scale.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 3.0)
        .build()
    );

    protected BaseServerInfoHud(HudElementInfo<?> info) {
        super(info);
    }

    /** Returns the text to display, or null to hide the element. */
    protected abstract String getText();

    @Override
    public void render(HudRenderer renderer) {
        String text = getText();
        if (text == null) {
            setSize(0, 0);
            return;
        }

        double s = scale.get();
        double w = renderer.textWidth(text, shadow.get(), s);
        double h = renderer.textHeight(shadow.get(), s);
        setSize(w, h);

        SettingColor color = rainbow.get() ? rainbowColor() : textColor.get();
        renderer.text(text, x, y, color, shadow.get(), s);
    }

    protected PeakPluginScanner scanner() {
        try {
            return Modules.get().get(PeakPluginScanner.class);
        } catch (Exception e) {
            return null;
        }
    }

    protected SettingColor rainbowColor() {
        long millis = System.currentTimeMillis();
        float hue = (millis % 4000L) / 4000f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }
}
