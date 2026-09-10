package orbiter.util;

import orbiter.modules.misc.ClientSideThings;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class ClientSpoofState {

    private static ClientSideThings cachedModule;
    private static final ThreadLocal<Integer> hudRenderDepth = ThreadLocal.withInitial(() -> 0);

    private ClientSpoofState() {
    }

    public static ClientSideThings module() {
        ClientSideThings mod = cachedModule;
        if (mod == null) {
            Modules modules = Modules.get();
            if (modules == null) return null;

            mod = modules.get(ClientSideThings.class);
            if (mod == null) return null;

            cachedModule = mod;
        }

        return mod.isActive() ? mod : null;
    }

    public static void pushHudRenderScope() {
        hudRenderDepth.set(hudRenderDepth.get() + 1);
    }

    public static void popHudRenderScope() {
        hudRenderDepth.set(Math.max(0, hudRenderDepth.get() - 1));
    }

    public static boolean isHudRenderScope() {
        return hudRenderDepth.get() > 0;
    }
}
