package orbiter.util;

import orbiter.modules.ClientSideThings;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ClientSpoofState {

    private static final Map<ItemStack, Integer> fakeCounts = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ItemStack, Component> fakeNames = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ItemStack, List<Component>> fakeLore = Collections.synchronizedMap(new IdentityHashMap<>());

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

    public static void clearAll() {
        fakeCounts.clear();
        fakeNames.clear();
        fakeLore.clear();
    }

    public static Component getFakeName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return fakeNames.get(stack);
    }

    public static List<Component> getFakeLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        List<Component> lore = fakeLore.get(stack);
        if (lore == null) return List.of();

        return lore;
    }
}
