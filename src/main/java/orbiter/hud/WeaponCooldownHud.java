package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public class WeaponCooldownHud extends HudElement {
    public static final HudElementInfo<WeaponCooldownHud> INFO = new HudElementInfo<>(Orbiter.HUD_GROUP, "weapon-cooldown", "Shows current weapon attack cooldown in seconds.", WeaponCooldownHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> readyText = sgGeneral.add(new StringSetting.Builder()
        .name("ready-text")
        .description("Text shown when weapon is ready to attack.")
        .defaultValue("Ready")
        .build()
    );

    private final Setting<String> cooldownFormat = sgGeneral.add(new StringSetting.Builder()
        .name("cooldown-format")
        .description("Format string for cooldown. Use %s for seconds.")
        .defaultValue("%.2fs")
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Text color while cooling down.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> readyColor = sgGeneral.add(new ColorSetting.Builder()
        .name("ready-color")
        .description("Color when cooldown is at 100%.")
        .defaultValue(new SettingColor(0, 255, 100, 255))
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

    public WeaponCooldownHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        float progress = player.getAttackCooldownProgress(0.5f);
        float ticksRemaining = (1.0f - progress) * player.getAttackCooldownProgressPerTick();
        double seconds = ticksRemaining / 20.0;

        String text;
        if (progress >= 0.999f) {
            text = readyText.get();
        } else {
            String fmt = cooldownFormat.get();
            if (fmt == null || !fmt.contains("%")) fmt = "%.2fs";
            try {
                text = String.format(fmt, seconds);
            } catch (Exception e) {
                text = String.format("%.2fs", seconds);
            }
        }

        double s = scale.get();
        double w = renderer.textWidth(text, shadow.get(), s);
        double h = renderer.textHeight(shadow.get(), s);
        setSize(w, h);

        SettingColor color;
        if (rainbow.get()) {
            color = rainbowColor();
        } else {
            color = (progress >= 0.999f) ? readyColor.get() : textColor.get();
        }
        renderer.text(text, x, y, color, shadow.get(), s);
    }

    private SettingColor rainbowColor() {
        long millis = System.currentTimeMillis();
        float hue = (millis % 4000L) / 4000f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }
}
