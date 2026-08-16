package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;

public class Restock extends Module {
    public enum FilterMode {
        Smart,
        Whitelist,
        Blacklist
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minCount = sgGeneral.add(new IntSetting.Builder()
        .name("min-hotbar-count")
        .description("When hotbar stack is below this amount, restock from inventory or open container.")
        .defaultValue(16)
        .min(1)
        .sliderRange(1, 64)
        .build());

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 20)
        .build());

    private final Setting<Boolean> inventoryFirst = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-first")
        .description("Try refilling from your own inventory before pulling from container.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useQuickMove = sgGeneral.add(new BoolSetting.Builder()
        .name("use-quick-move")
        .description("Uses QUICK_MOVE for container pulls to prevent pickup ping-pong.")
        .defaultValue(true)
        .build());

    private final Setting<FilterMode> filterMode = sgGeneral.add(new EnumSetting.Builder<FilterMode>()
        .name("filter-mode")
        .defaultValue(FilterMode.Smart)
        .build());

    private final Setting<List<Item>> itemFilter = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Whitelist/blacklist entries. Smart mode ignores this list.")
        .build());

    private final Setting<Boolean> restockEmptySlots = sgGeneral.add(new BoolSetting.Builder()
        .name("restock-empty-hotbar")
        .defaultValue(true)
        .build());

    private int timer = 0;

    public Restock() {
        super(Orbiter.CATEGORY, "restock", "Fast hotbar restocking from inventory first, then open storage GUIs.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.gameMode == null) return;

        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> handledScreen)) return;
        AbstractContainerMenu handler = handledScreen.getMenu();
        if (handler == null) return;

        Inventory playerInv = mc.player.getInventory();

        boolean isContainerOpen = handler.slots.stream().anyMatch(slot -> slot.container != playerInv);
        if (!isContainerOpen) return;

        if (timer > 0) {
            timer--;
            return;
        }

        for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
            ItemStack hotbarStack = playerInv.getItem(hotbarIndex);
            boolean needsRestock = hotbarStack.isEmpty()
                ? restockEmptySlots.get()
                : hotbarStack.getCount() < minCount.get();

            if (!needsRestock) continue;

            Item wanted = hotbarStack.isEmpty() ? null : hotbarStack.getItem();
            int hotbarSlotId = findHandlerSlotForHotbar(handler, hotbarIndex, playerInv);
            if (hotbarSlotId == -1) continue;

            if (inventoryFirst.get() && wanted != null) {
                int playerSlotId = findMatchingPlayerInventorySlot(handler, playerInv, wanted);
                if (playerSlotId != -1 && moveStackByPickup(handler.containerId, playerSlotId, hotbarSlotId)) {
                    timer = tickDelay.get();
                    return;
                }
            }

            int containerSlotId = findMatchingContainerSlot(handler, playerInv, wanted);
            if (containerSlotId == -1) continue;

            if (useQuickMove.get()) {
                mc.gameMode.handleContainerInput(handler.containerId, containerSlotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                timer = tickDelay.get();
                return;
            }

            if (moveStackByPickup(handler.containerId, containerSlotId, hotbarSlotId)) {
                timer = tickDelay.get();
                return;
            }
        }
    }

    private int findHandlerSlotForHotbar(AbstractContainerMenu handler, int hotbarIndex, Inventory playerInv) {
        for (Slot slot : handler.slots) {
            if (slot.container == playerInv && slot.index == hotbarIndex) return slot.index;
        }
        return -1;
    }

    private int findMatchingPlayerInventorySlot(AbstractContainerMenu handler, Inventory playerInv, Item wanted) {
        for (Slot slot : handler.slots) {
            if (slot.container != playerInv) continue;
            if (slot.index < 9 || slot.index >= 36) continue;
            if (!slot.hasItem()) continue;
            if (!slot.getItem().is(wanted)) continue;
            return slot.index;
        }
        return -1;
    }

    private int findMatchingContainerSlot(AbstractContainerMenu handler, Inventory playerInv, Item wanted) {
        for (Slot slot : handler.slots) {
            if (slot.container == playerInv) continue;
            if (!slot.hasItem()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (!passesFilter(stack.getItem())) continue;
            if (wanted != null && stack.getItem() != wanted) continue;

            return slot.index;
        }
        return -1;
    }

    private boolean passesFilter(Item item) {
        if (filterMode.get() == FilterMode.Smart) return true;

        List<Item> filter = itemFilter.get();
        if (filter == null || filter.isEmpty()) return filterMode.get() != FilterMode.Whitelist;

        boolean contains = filter.contains(item);
        if (filterMode.get() == FilterMode.Whitelist) return contains;
        return !contains;
    }

    private boolean moveStackByPickup(int syncId, int fromSlot, int toSlot) {
        if (mc.player == null || mc.gameMode == null) return false;

        mc.gameMode.handleContainerInput(syncId, fromSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, toSlot, 0, ContainerInput.PICKUP, mc.player);

        if (!mc.player.containerMenu.getCarried().isEmpty()) {
            mc.gameMode.handleContainerInput(syncId, fromSlot, 0, ContainerInput.PICKUP, mc.player);
        }

        return true;
    }
}
