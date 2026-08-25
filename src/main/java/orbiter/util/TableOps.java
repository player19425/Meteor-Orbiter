package orbiter.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

public final class TableOps {
    private TableOps() {}

    public static boolean carrying(EnchantmentMenu menu) {
        return !menu.getCarried().isEmpty();
    }

    public static ItemStack carried(EnchantmentMenu menu) {
        return menu.getCarried();
    }

    public static ItemStack slot0(EnchantmentMenu menu) {
        return menu.getSlot(0).getItem();
    }

    public static int gold(EnchantmentMenu menu) {
        return menu.getGoldCount();
    }

    public static void pickupAll(EnchantmentMenu menu, int slot) {
        click(menu, slot, ContainerInput.PICKUP, 0);
    }

    public static void depositOne(EnchantmentMenu menu, int slot) {
        click(menu, slot, ContainerInput.PICKUP, 1);
    }

    public static void shiftMove(EnchantmentMenu menu, int slot) {
        click(menu, slot, ContainerInput.QUICK_MOVE, 0);
    }

    public static void dropCarried(EnchantmentMenu menu) {
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, -999, 0, ContainerInput.PICKUP, Minecraft.getInstance().player);
    }

    private static void click(EnchantmentMenu menu, int slot, ContainerInput input, int button) {
        if (slot < 0 || slot >= menu.slots.size()) return;
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, slot, button, input, Minecraft.getInstance().player);
    }

    public static boolean isPlayerSlot(EnchantmentMenu menu, int index) {
        return index >= 0 && index < menu.slots.size()
                && menu.slots.get(index).container instanceof Inventory;
    }

    public static int parkTarget(EnchantmentMenu menu) {
        int best = -1;
        int bestRank = Integer.MIN_VALUE;
        for (int i = 3; i < menu.slots.size(); i++) {
            if (!isPlayerSlot(menu, i)) continue;
            if (!menu.slots.get(i).getItem().isEmpty()) continue;
            int cs = menu.slots.get(i).getContainerSlot();
            int rank = cs >= 9 ? cs : cs - 100;
            if (rank > bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    public static int findByPath(EnchantmentMenu menu, String path) {
        for (int i = 3; i < menu.slots.size(); i++) {
            if (!isPlayerSlot(menu, i)) continue;
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String id = BuiltInIds.of(s);
            if (id.equals(path) || id.equals("minecraft:" + path)) return i;
        }
        return -1;
    }

    private static final class BuiltInIds {
        static String of(ItemStack stack) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id == null ? "" : id.toString();
        }
    }
}
