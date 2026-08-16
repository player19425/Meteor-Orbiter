package orbiter.hud;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class NearestPlayerHud extends HudElement {
    public static final HudElementInfo<NearestPlayerHud> INFO = new HudElementInfo<>(Orbiter.HUD_GROUP, "nearest-player", "Shows the nearest player and their distance.", NearestPlayerHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Component color.")
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
        .description("Component scale.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 3.0)
        .build()
    );

    public NearestPlayerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            String none = "No players nearby";
            double s = scale.get();
            setSize(renderer.textWidth(none, shadow.get(), s), renderer.textHeight(shadow.get(), s));
            renderer.text(none, x, y, textColor.get(), shadow.get(), s);
            return;
        }

        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player entity : mc.level.players()) {
            if (entity == mc.player) continue;
            double dist = mc.player.distanceToSqr(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }

        String text;
        if (nearest == null) {
            text = "No players nearby";
        } else {
            double dist = Math.sqrt(nearestDist);
            text = String.format("%s is %.1f blocks near", nearest.getName().getString(), dist);
        }

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
