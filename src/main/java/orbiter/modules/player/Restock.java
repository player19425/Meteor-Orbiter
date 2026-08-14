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
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

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
        if (mc.player == null || mc.interactionManager == null) return;

        if (!(mc.currentScreen instanceof HandledScreen<?> handledScreen)) return;
        ScreenHandler handler = handledScreen.getScreenHandler();
        if (handler == null) return;

        PlayerInventory playerInv = mc.player.getInventory();

        boolean isContainerOpen = handler.slots.stream().anyMatch(slot -> slot.inventory != playerInv);
        if (!isContainerOpen) return;

        if (timer > 0) {
            timer--;
            return;
        }

        for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
            ItemStack hotbarStack = playerInv.getStack(hotbarIndex);
            boolean needsRestock = hotbarStack.isEmpty()
                ? restockEmptySlots.get()
                : hotbarStack.getCount() < minCount.get();

            if (!needsRestock) continue;

            Item wanted = hotbarStack.isEmpty() ? null : hotbarStack.getItem();
            int hotbarSlotId = findHandlerSlotForHotbar(handler, hotbarIndex, playerInv);
            if (hotbarSlotId == -1) continue;

            if (inventoryFirst.get() && wanted != null) {
                int playerSlotId = findMatchingPlayerInventorySlot(handler, playerInv, wanted);
                if (playerSlotId != -1 && moveStackByPickup(handler.syncId, playerSlotId, hotbarSlotId)) {
                    timer = tickDelay.get();
                    return;
                }
            }

            int containerSlotId = findMatchingContainerSlot(handler, playerInv, wanted);
            if (containerSlotId == -1) continue;

            if (useQuickMove.get()) {
                mc.interactionManager.clickSlot(handler.syncId, containerSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer = tickDelay.get();
                return;
            }

            if (moveStackByPickup(handler.syncId, containerSlotId, hotbarSlotId)) {
                timer = tickDelay.get();
                return;
            }
        }
    }

    private int findHandlerSlotForHotbar(ScreenHandler handler, int hotbarIndex, PlayerInventory playerInv) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == playerInv && slot.getIndex() == hotbarIndex) return slot.id;
        }
        return -1;
    }

    private int findMatchingPlayerInventorySlot(ScreenHandler handler, PlayerInventory playerInv, Item wanted) {
        for (Slot slot : handler.slots) {
            if (slot.inventory != playerInv) continue;
            if (slot.getIndex() < 9 || slot.getIndex() >= 36) continue;
            if (!slot.hasStack()) continue;
            if (!slot.getStack().isOf(wanted)) continue;
            return slot.id;
        }
        return -1;
    }

    private int findMatchingContainerSlot(ScreenHandler handler, PlayerInventory playerInv, Item wanted) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == playerInv) continue;
            if (!slot.hasStack()) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            if (!passesFilter(stack.getItem())) continue;
            if (wanted != null && stack.getItem() != wanted) continue;

            return slot.id;
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
        if (mc.player == null || mc.interactionManager == null) return false;

        mc.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, toSlot, 0, SlotActionType.PICKUP, mc.player);

        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(syncId, fromSlot, 0, SlotActionType.PICKUP, mc.player);
        }

        return true;
    }
}
