package orbiter.util;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;

public class ConfigModifier {
    private static ConfigModifier INSTANCE;

    public SettingGroup sgOrbiter;
    public Setting<Boolean> stupidModules;

    public static ConfigModifier get() {
        if (INSTANCE == null) INSTANCE = new ConfigModifier();
        if (INSTANCE.stupidModules == null) INSTANCE.tryInit();
        return INSTANCE;
    }

    private ConfigModifier() {}

    private void tryInit() {
        Config config = Config.get();
        if (config == null || config.settings == null) return;

        sgOrbiter = config.settings.createGroup("Orbiter");
        stupidModules = sgOrbiter.add(new BoolSetting.Builder()
            .name("stupid-modules")
            .description("Enable 'stupid' modules that are normally hidden/disabled by default.")
            .defaultValue(false)
            .build()
        );
    }

    public boolean stupidModulesEnabled() {
        Setting<Boolean> setting = stupidModules;
        return setting != null && setting.get();
    }
}
