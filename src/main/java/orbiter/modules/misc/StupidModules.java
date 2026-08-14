package orbiter.modules.misc;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import orbiter.Orbiter;
import orbiter.util.ConfigModifier;

public class StupidModules extends Module {

    public StupidModules() {
        super(Orbiter.CATEGORY, "stupid-modules", "Master toggle for joke / experimental / stupid modules.");
    }

    public static StupidModules get() {
        return Modules.get().get(StupidModules.class);
    }

    @Override
    public void onActivate() {
        ConfigModifier.get().stupidModules.set(true);
    }

    @Override
    public void onDeactivate() {
        ConfigModifier.get().stupidModules.set(false);
    }

    public boolean stupidEnabled() {
        return ConfigModifier.get().stupidModules.get();
    }
}
