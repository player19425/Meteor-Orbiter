package orbiter.util;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.config.Config;

public class ConfigModifier {
    private static ConfigModifier INSTANCE;

    public SettingGroup sgOrbiter;
    public Setting<Boolean> stupidModules;
    public Setting<Boolean> wipModules;
    public Setting<Boolean> updateChecker;
    public Setting<Boolean> updateAuto;
    public Setting<String> updateIgnoredVersion;

    public static synchronized ConfigModifier get() {
        if (INSTANCE == null) INSTANCE = new ConfigModifier();
        if (INSTANCE.stupidModules == null || INSTANCE.wipModules == null || INSTANCE.updateChecker == null) INSTANCE.tryInit();
        return INSTANCE;
    }

    private ConfigModifier() {}

    @SuppressWarnings("unchecked")
    private synchronized void tryInit() {        Config config = Config.get();
        if (config == null || config.settings == null) return;

        for (SettingGroup group : config.settings.groups) {
            if (group != null && "Orbiter".equals(group.name)) {
                sgOrbiter = group;
                break;
            }
        }

        if (sgOrbiter == null) sgOrbiter = config.settings.createGroup("Orbiter");

        if (stupidModules == null) stupidModules = findBool(sgOrbiter, "stupid-modules",
            "Enable 'stupid' modules that are normally hidden/disabled by default.", false);
        if (wipModules == null) wipModules = findBool(sgOrbiter, "wip-modules",
            "Enable work-in-progress modules that are normally hidden/disabled by default.", false);
        if (updateChecker == null) updateChecker = findBool(sgOrbiter, "update-checker",
            "Check for new Orbiter releases on startup and notify you.", true);
        if (updateAuto == null) updateAuto = findBool(sgOrbiter, "update-auto",
            "Automatically download and install new Orbiter releases when detected. Requires restart after install.", false);

        if (updateIgnoredVersion == null) {
            for (Setting<?> setting : sgOrbiter) {
                if (setting != null && "update-ignored-version".equals(setting.name)) {
                    updateIgnoredVersion = (Setting<String>) setting;
                    break;
                }
            }
            if (updateIgnoredVersion == null) {
                updateIgnoredVersion = sgOrbiter.add(new StringSetting.Builder()
                    .name("update-ignored-version")
                    .description("Update version you chose to skip.")
                    .defaultValue("")
                    .build()
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Setting<Boolean> findBool(SettingGroup group, String name, String description, boolean defaultValue) {
        for (Setting<?> setting : group) {
            if (setting != null && name.equals(setting.name)) return (Setting<Boolean>) setting;
        }
        return group.add(new BoolSetting.Builder()
            .name(name)
            .description(description)
            .defaultValue(defaultValue)
            .build()
        );
    }

    public boolean stupidModulesEnabled() {
        Setting<Boolean> setting = stupidModules;
        return setting != null && setting.get();
    }

    public boolean wipModulesEnabled() {
        Setting<Boolean> setting = wipModules;
        return setting != null && setting.get();
    }

    public boolean updateCheckerEnabled() {
        Setting<Boolean> setting = updateChecker;
        return setting != null && setting.get();
    }

    public boolean updateAutoEnabled() {
        Setting<Boolean> setting = updateAuto;
        return setting != null && setting.get();
    }

    public String ignoredVersion() {
        Setting<String> setting = updateIgnoredVersion;
        return setting == null ? "" : setting.get();
    }

    public void setIgnoredVersion(String version) {
        Setting<String> setting = updateIgnoredVersion;
        if (setting != null) setting.set(version);
    }
}
