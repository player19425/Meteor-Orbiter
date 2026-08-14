package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoCraftPlus extends Module {

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgRecipe   = settings.createGroup("Recipe Configuration");
    private final SettingGroup sgSpeed    = settings.createGroup("Speed");

    private final Setting<Boolean> useInventoryCrafting = sgGeneral.add(new BoolSetting.Builder()
        .name("use-inventory-crafting")
        .description("Use the 2x2 inventory crafting grid instead of a crafting table.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> openInventoryAutomatically = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-inventory")
        .description("Automatically open inventory to craft without manual interaction.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> stopWhenFull = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-full")
        .description("Stop crafting when inventory is full.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> stopWhenOutOfMaterials = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-no-materials")
        .description("Stop module when materials run out.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxCrafts = sgGeneral.add(new IntSetting.Builder()
        .name("max-crafts")
        .description("Maximum number of crafts. 0 = unlimited.")
        .defaultValue(0)
        .min(0).sliderRange(0, 1000)
        .build());

    private final Setting<Item> slot0 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-1")
        .description("Top-Left for 3x3 / Top-Left for 2x2.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot1 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-2")
        .description("Top-Center for 3x3 / Top-Right for 2x2.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot2 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-3")
        .description("Top-Right for 3x3.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot3 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-4")
        .description("Middle-Left for 3x3 / Bottom-Left for 2x2.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot4 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-5")
        .description("Center for 3x3 / Bottom-Right for 2x2.")
        .defaultValue(Items.BONE)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot5 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-6")
        .description("Middle-Right for 3x3.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot6 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-7")
        .description("Bottom-Left for 3x3.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot7 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-8")
        .description("Bottom-Center for 3x3.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> slot8 = sgRecipe.add(new ItemSetting.Builder()
        .name("slot-9")
        .description("Bottom-Right for 3x3.")
        .defaultValue(Items.AIR)
        .visible(() -> !useInventoryCrafting.get())
        .build());

    private final Setting<Item> invSlot0 = sgRecipe.add(new ItemSetting.Builder()
        .name("inv-slot-1")
        .description("2x2 Top-Left.")
        .defaultValue(Items.AIR)
        .visible(useInventoryCrafting::get)
        .build());

    private final Setting<Item> invSlot1 = sgRecipe.add(new ItemSetting.Builder()
        .name("inv-slot-2")
        .description("2x2 Top-Right.")
        .defaultValue(Items.AIR)
        .visible(useInventoryCrafting::get)
        .build());

    private final Setting<Item> invSlot2 = sgRecipe.add(new ItemSetting.Builder()
        .name("inv-slot-3")
        .description("2x2 Bottom-Left.")
        .defaultValue(Items.AIR)
        .visible(useInventoryCrafting::get)
        .build());

    private final Setting<Item> invSlot3 = sgRecipe.add(new ItemSetting.Builder()
        .name("inv-slot-4")
        .description("2x2 Bottom-Right.")
        .defaultValue(Items.BONE)
        .visible(useInventoryCrafting::get)
        .build());

    private final Setting<Integer> craftDelay = sgSpeed.add(new IntSetting.Builder()
        .name("craft-delay")
        .description("Ticks between each craft cycle.")
        .defaultValue(1)
        .min(0).sliderRange(0, 20)
        .build());

    private final Setting<Integer> placeDelay = sgSpeed.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between placing each ingredient in the grid.")
        .defaultValue(0)
        .min(0).sliderRange(0, 10)
        .build());

    private final Setting<Integer> takeDelay = sgSpeed.add(new IntSetting.Builder()
        .name("take-delay")
        .description("Ticks to wait before taking the result.")
        .defaultValue(1)
        .min(0).sliderRange(0, 10)
        .build());

    private final Setting<Integer> craftsPerTick = sgSpeed.add(new IntSetting.Builder()
        .name("crafts-per-tick")
        .description("How many complete craft cycles to attempt per tick. Higher = faster but riskier.")
        .defaultValue(1)
        .min(1).sliderRange(1, 10)
        .build());

    private enum CraftState {
        OPEN_SCREEN,
        PLACE_INGREDIENTS,
        TAKE_RESULT,
        WAIT
    }

    private CraftState craftState = CraftState.OPEN_SCREEN;
    private int tickWaiter = 0;
    private int totalCrafted = 0;

    public AutoCraftPlus() {
        super(Orbiter.CATEGORY, "auto-craft-plus",
            "Automatically crafts items at max speed. Configure the recipe, enable, and watch it go.");
    }

    @Override
    public void onActivate() {
        craftState = CraftState.OPEN_SCREEN;
        tickWaiter = 0;
        totalCrafted = 0;
        info("AutoCraft+ started.");
    }

    @Override
    public void onDeactivate() {
        info("AutoCraft+ stopped. Crafted " + totalCrafted + " items.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (tickWaiter > 0) {
            tickWaiter--;
            return;
        }

        if (maxCrafts.get() > 0 && totalCrafted >= maxCrafts.get()) {
            info("Max crafts reached (" + totalCrafted + "). Stopping.");
            toggle();
            return;
        }

        if (stopWhenFull.get() && getEmptySlots() == 0) {
            info("Inventory full. Stopping.");
            toggle();
            return;
        }

        for (int i = 0; i < craftsPerTick.get(); i++) {
            if (!processCraftCycle()) break;
        }
    }

    private boolean processCraftCycle() {
        switch (craftState) {
            case OPEN_SCREEN -> {
                if (useInventoryCrafting.get()) {
                    if (!(mc.currentScreen instanceof InventoryScreen)) {
                        if (openInventoryAutomatically.get()) {
                            mc.setScreen(new InventoryScreen(mc.player));
                        } else {
                            return false;
                        }
                    }
                } else {

                    if (!(mc.currentScreen instanceof CraftingScreen)) {
                        info("Please open a crafting table.");
                        return false;
                    }
                }
                craftState = CraftState.PLACE_INGREDIENTS;
                return true;
            }

            case PLACE_INGREDIENTS -> {
                if (!(mc.currentScreen instanceof HandledScreen<?>)) {
                    craftState = CraftState.OPEN_SCREEN;
                    return false;
                }

                HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                int syncId = screen.getScreenHandler().syncId;

                Item[] recipe = getRecipe();
                int gridStart = getCraftingGridStart(screen);
                int gridSize = useInventoryCrafting.get() ? 4 : 9;

                if (!hasMaterials(recipe, gridSize)) {
                    if (stopWhenOutOfMaterials.get()) {
                        info("Out of materials. Stopping.");
                        toggle();
                        return false;
                    }
                    return false;
                }

                for (int i = 0; i < gridSize; i++) {
                    Item needed = recipe[i];
                    if (needed == Items.AIR || needed == null) continue;

                    int craftSlot = gridStart + i;
                    Slot slot = screen.getScreenHandler().slots.get(craftSlot);

                    if (!slot.getStack().isEmpty() && slot.getStack().getItem() == needed) continue;

                    int srcSlot = findItemInInventory(screen, needed, gridStart, gridSize);
                    if (srcSlot < 0) {
                        if (stopWhenOutOfMaterials.get()) {
                            info("Missing ingredient: " + needed.toString() + ". Stopping.");
                            toggle();
                            return false;
                        }
                        return false;
                    }

                    mc.interactionManager.clickSlot(syncId, srcSlot, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(syncId, craftSlot, 1, SlotActionType.PICKUP, mc.player);

                    if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        mc.interactionManager.clickSlot(syncId, srcSlot, 0, SlotActionType.PICKUP, mc.player);
                    }
                }

                craftState = CraftState.TAKE_RESULT;
                tickWaiter = takeDelay.get();
                return true;
            }

            case TAKE_RESULT -> {
                if (!(mc.currentScreen instanceof HandledScreen<?>)) {
                    craftState = CraftState.OPEN_SCREEN;
                    return false;
                }

                HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                int syncId = screen.getScreenHandler().syncId;
                int resultSlot = getResultSlot(screen);

                Slot result = screen.getScreenHandler().slots.get(resultSlot);
                if (!result.getStack().isEmpty()) {

                    mc.interactionManager.clickSlot(syncId, resultSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    totalCrafted++;
                }

                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {

                    for (int i = getCraftingGridStart(screen) + (useInventoryCrafting.get() ? 4 : 9);
                         i < screen.getScreenHandler().slots.size(); i++) {
                        Slot s = screen.getScreenHandler().slots.get(i);
                        if (s.getStack().isEmpty()) {
                            mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, mc.player);
                            break;
                        }
                    }
                }

                craftState = CraftState.PLACE_INGREDIENTS;
                tickWaiter = craftDelay.get();
                return true;
            }

            default -> {
                return false;
            }
        }
    }

    private Item[] getRecipe() {
        if (useInventoryCrafting.get()) {
            return new Item[]{
                invSlot0.get(), invSlot1.get(),
                invSlot2.get(), invSlot3.get()
            };
        }
        return new Item[]{
            slot0.get(), slot1.get(), slot2.get(),
            slot3.get(), slot4.get(), slot5.get(),
            slot6.get(), slot7.get(), slot8.get()
        };
    }

    private int getCraftingGridStart(HandledScreen<?> screen) {
        if (screen instanceof CraftingScreen) return 1;
        if (screen instanceof InventoryScreen) return 1;
        return 1;
    }

    private int getResultSlot(HandledScreen<?> screen) {
        return 0;
    }

    private boolean hasMaterials(Item[] recipe, int gridSize) {

        java.util.Map<Item, Integer> needed = new java.util.HashMap<>();
        for (int i = 0; i < gridSize; i++) {
            Item item = recipe[i];
            if (item != Items.AIR && item != null) {
                needed.put(item, needed.getOrDefault(item, 0) + 1);
            }
        }

        if (mc.player == null) return false;
        for (var entry : needed.entrySet()) {
            int count = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == entry.getKey()) {
                    count += stack.getCount();
                }
            }
            if (count < entry.getValue()) return false;
        }
        return true;
    }

    private int findItemInInventory(HandledScreen<?> screen, Item item, int gridStart, int gridSize) {

        int invStart = gridStart + gridSize;
        for (int i = invStart; i < screen.getScreenHandler().slots.size(); i++) {
            Slot slot = screen.getScreenHandler().slots.get(i);
            if (!slot.getStack().isEmpty() && slot.getStack().getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private int getEmptySlots() {
        if (mc.player == null) return 0;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) empty++;
        }
        return empty;
    }
}
