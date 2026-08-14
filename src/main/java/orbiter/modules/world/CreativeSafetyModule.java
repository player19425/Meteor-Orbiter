package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public abstract class CreativeSafetyModule extends Module {
    protected final SettingGroup sgSafety = settings.createGroup("Safety");

    protected final Setting<Boolean> disableOnLeave = sgSafety.add(new BoolSetting.Builder()
            .name("disable-on-leave")
            .description("Disable this module automatically when you leave the server/world.")
            .defaultValue(true)
            .build());

    protected CreativeSafetyModule(String name, String description) {
        super(Orbiter.CATEGORY_OP, name, description);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!disableOnLeave.get() || !isActive()) return;

        info("Disconnected from world/server. " + title + " disabled by safety setting.");
        toggle();
    }
}
