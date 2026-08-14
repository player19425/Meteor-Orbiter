package orbiter.util;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;

public class ConfigModifier {
    private static ConfigModifier INSTANCE;

    public static final SettingGroup sgOrbiter = Config.get().settings.createGroup("Orbiter");

    public final Setting<Boolean> stupidModules = sgOrbiter.add(new BoolSetting.Builder()
        .name("stupid-modules")
        .description("Enable 'stupid' modules that are normally hidden/disabled by default.")
        .defaultValue(false)
        .build()
    );

    public static ConfigModifier get() {
        if (INSTANCE == null) INSTANCE = new ConfigModifier();
        return INSTANCE;
    }

    private ConfigModifier() {}
}
