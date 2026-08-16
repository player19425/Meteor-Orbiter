package orbiter.modules.render;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public final class BeaconOptimizer extends Module {
    public enum Mode { Conservative, Balanced, Aggressive }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Controls animation-state update throttling. Beams remain rendered in every mode.")
        .defaultValue(Mode.Conservative)
        .build());
    private final Setting<Integer> farAnimationUpdateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("far-animation-update-interval")
        .description("Animation sampling interval. This does not hide or shorten beams.")
        .defaultValue(2)
        .min(1).max(20).sliderRange(1, 10)
        .build());
    private final Setting<Boolean> geometryLod = sgGeneral.add(new BoolSetting.Builder()
        .name("geometry-lod")
        .description("Reserved for a renderer-compatible geometry path; disabled when unsupported.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> frustumCullOffscreen = sgGeneral.add(new BoolSetting.Builder()
        .name("frustum-cull-offscreen")
        .description("Allows only vanilla renderer culling; Orbiter never cancels an on-screen beam.")
        .defaultValue(true)
        .build());
    private final Setting<Boolean> showStats = sgGeneral.add(new BoolSetting.Builder()
        .name("show-stats")
        .description("Shows the active conservative optimization mode in module info.")
        .defaultValue(false)
        .build());

    public BeaconOptimizer() {
        super(Orbiter.CATEGORY, "beacon-optimizer",
            "Reduces beacon lag.");
    }

    public float quantizeTickProgress(float tickProgress) {
        int interval = switch (mode.get()) {
            case Conservative -> 1;
            case Balanced -> Math.max(1, farAnimationUpdateInterval.get());
            case Aggressive -> Math.max(2, farAnimationUpdateInterval.get());
        };
        if (interval <= 1 || !Float.isFinite(tickProgress)) return tickProgress;
        return Math.round(tickProgress * interval) / (float) interval;
    }

    @Override
    public String getInfoString() {
        return showStats.get() ? mode.get().name() : null;
    }
}
