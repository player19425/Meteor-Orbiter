package orbiter.util;

import orbiter.modules.ClientSideThings;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ClientSpoofState {

    private static final Map<ItemStack, Integer> fakeCounts = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ItemStack, Text> fakeNames = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ItemStack, List<Text>> fakeLore = Collections.synchronizedMap(new IdentityHashMap<>());

    private ClientSpoofState() {
    }

    public static ClientSideThings module() {
        Modules modules = Modules.get();
        if (modules == null) return null;

        ClientSideThings mod = modules.get(ClientSideThings.class);
        if (mod == null || !mod.isActive()) return null;

        return mod;
    }

    public static void clearAll() {
        fakeCounts.clear();
        fakeNames.clear();
        fakeLore.clear();
    }

    public static void setFakeCount(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty()) return;

        if (count <= 0) fakeCounts.remove(stack);
        else fakeCounts.put(stack, count);
    }

    public static int getFakeCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        return fakeCounts.getOrDefault(stack, -1);
    }

    public static void setFakeName(ItemStack stack, Text name) {
        if (stack == null || stack.isEmpty()) return;

        if (name == null) fakeNames.remove(stack);
        else fakeNames.put(stack, name);
    }

    public static Text getFakeName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return fakeNames.get(stack);
    }

    public static void setFakeLore(ItemStack stack, List<Text> lore) {
        if (stack == null || stack.isEmpty()) return;

        if (lore == null || lore.isEmpty()) {
            fakeLore.remove(stack);
            return;
        }

        fakeLore.put(stack, new ArrayList<>(lore));
    }

    public static List<Text> getFakeLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        List<Text> lore = fakeLore.get(stack);
        if (lore == null) return List.of();

        return lore;
    }
}
