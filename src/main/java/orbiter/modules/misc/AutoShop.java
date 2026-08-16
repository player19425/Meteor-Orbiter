package orbiter.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import orbiter.Orbiter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AutoShop extends Module {
    public enum Mode { Buy, Sell, BuyAndDeposit }

    private static final int START = 0;
    private static final int WAIT_SHOP = 1;
    private static final int CLICK_CATEGORY = 2;
    private static final int CLICK_ITEM = 3;
    private static final int CLICK_STACK_BATCH = 4;
    private static final int CLICK_ADD = 5;
    private static final int CLICK_CONFIRM = 6;
    private static final int WAIT_TRANSACTION = 7;
    private static final int FIND_CHEST = 100;
    private static final int WAIT_CHEST = 101;
    private static final int DEPOSIT = 102;
    private static final int WAIT_GROUND_PICKUP = 103;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShop = settings.createGroup("Shop Navigation");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgChest = settings.createGroup("Chest Deposit");
    private final SettingGroup sgSpeed = settings.createGroup("Speed");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Buy, sell, or buy and deposit into nearby containers.")
        .defaultValue(Mode.BuyAndDeposit)
        .build());

    private final Setting<String> shopCommand = sgGeneral.add(new StringSetting.Builder()
        .name("shop-command")
        .description("Command used to open the shop, without the slash.")
        .defaultValue("shop")
        .build());

    private final Setting<Item> targetItem = sgGeneral.add(new ItemSetting.Builder()
        .name("target-item")
        .description("Item selected in the shop and deposited into chests.")
        .defaultValue(Items.BONE)
        .build());

    private final Setting<Boolean> autoDetectSlots = sgShop.add(new BoolSetting.Builder()
        .name("auto-detect-slots")
        .description("Detect the category and item buttons. The item is matched directly, independent of language.")
        .defaultValue(true)
        .build());

    private final Setting<String> categoryKeyword = sgShop.add(new StringSetting.Builder()
        .name("category-keyword")
        .description("English keyword used to find the category button.")
        .defaultValue("mobs")
        .visible(autoDetectSlots::get)
        .build());

    private final Setting<String> itemKeyword = sgShop.add(new StringSetting.Builder()
        .name("item-keyword")
        .description("English fallback keyword for the item button.")
        .defaultValue("bone")
        .visible(autoDetectSlots::get)
        .build());

    private final Setting<Integer> categorySlot = sgShop.add(new IntSetting.Builder()
        .name("category-slot")
        .description("Category button slot.")
        .defaultValue(12).min(0).sliderRange(0, 53)
        .build());

    private final Setting<Integer> itemSlot = sgShop.add(new IntSetting.Builder()
        .name("item-slot")
        .description("Item button slot; used when detection is disabled or cannot find the item.")
        .defaultValue(3).min(0).sliderRange(0, 53)
        .build());

    private final Setting<Integer> stackBatchSlot = sgShop.add(new IntSetting.Builder()
        .name("stack-batch-slot")
        .description("Stack batch selector. This slot is clicked exactly once.")
        .defaultValue(31).min(0).sliderRange(0, 53)
        .build());

    private final Setting<Integer> addSlot = sgShop.add(new IntSetting.Builder()
        .name("add-slot")
        .description("Add-more-stacks button.")
        .defaultValue(25).min(0).sliderRange(0, 53)
        .build());

    private final Setting<Integer> addClicks = sgShop.add(new IntSetting.Builder()
        .name("add-clicks")
        .description("Number of times to click the add button. Two produces the intended 64-stack batch on this shop.")
        .defaultValue(2).min(0).max(8).sliderRange(0, 8)
        .build());

    private final Setting<Integer> confirmSlot = sgShop.add(new IntSetting.Builder()
        .name("confirm-slot")
        .description("Final purchase confirmation slot.")
        .defaultValue(13).min(0).sliderRange(0, 53)
        .build());

    private final Setting<Boolean> antiWrongItem = sgSafety.add(new BoolSetting.Builder()
        .name("anti-wrong-item")
        .description("Refuse to click an item button that does not match the target item or English fallback keyword.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxCycles = sgSafety.add(new IntSetting.Builder()
        .name("max-cycles")
        .description("Maximum completed transactions; zero is unlimited.")
        .defaultValue(0).min(0).sliderRange(0, 1000)
        .build());

    private final Setting<Integer> groundItemRange = sgSafety.add(new IntSetting.Builder()
        .name("ground-item-range")
        .description("Nearby dropped target items are collected and deposited before another purchase.")
        .defaultValue(6).min(1).sliderRange(1, 16)
        .visible(() -> mode.get() != Mode.Sell)
        .build());

    private final Setting<Integer> chestRange = sgChest.add(new IntSetting.Builder()
        .name("chest-search-range")
        .description("Range used to find deposit containers.")
        .defaultValue(4).min(1).sliderRange(1, 8)
        .visible(() -> mode.get() == Mode.BuyAndDeposit)
        .build());

    private final Setting<Boolean> useTrappedChests = sgChest.add(new BoolSetting.Builder()
        .name("use-trapped-chests")
        .description("Deposit into trapped chests.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.BuyAndDeposit)
        .build());

    private final Setting<Boolean> useBarrels = sgChest.add(new BoolSetting.Builder()
        .name("use-barrels")
        .description("Deposit into barrels.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.BuyAndDeposit)
        .build());

    private final Setting<Integer> normalDelay = sgSpeed.add(new IntSetting.Builder()
        .name("normal-delay")
        .description("Ticks between opening menus or containers.")
        .defaultValue(10).min(1).sliderRange(1, 40)
        .build());

    private final Setting<Integer> clickDelay = sgSpeed.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks between shop button clicks.")
        .defaultValue(4).min(1).sliderRange(1, 20)
        .build());

    private final Setting<Integer> depositDelay = sgSpeed.add(new IntSetting.Builder()
        .name("deposit-delay")
        .description("Ticks between chest-dump passes (each pass shift-clicks every target stack at once).")
        .defaultValue(1).min(1).sliderRange(1, 20)
        .visible(() -> mode.get() == Mode.BuyAndDeposit)
        .build());

    private int state;
    private int tickWaiter;
    private int timeout;
    private int transactionCycles;
    private int addClicksDone;
    private int preTransactionCount;
    private int groundWaitTicks;
    private int chestReopenFails;
    private int noChestRetries;
    private int depositStallTicks;
    private int depositLastCount;
    private BlockPos currentChest;
    private final Set<BlockPos> fullChests = new HashSet<>();

    public AutoShop() {
        super(Orbiter.CATEGORY, "auto-shop", "Buys from server shops automatically.");
    }

    @Override
    public void onActivate() {
        resetRuntime();
        fullChests.clear();
        info("AutoShop [" + mode.get().name() + "] started.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
        info("AutoShop stopped. Completed transactions: " + transactionCycles);
    }

    private void resetRuntime() {
        state = START;
        tickWaiter = 0;
        timeout = 0;
        transactionCycles = 0;
        addClicksDone = 0;
        preTransactionCount = 0;
        groundWaitTicks = 0;
        chestReopenFails = 0;
        noChestRetries = 0;
        depositStallTicks = 0;
        depositLastCount = -1;
        currentChest = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.player.connection == null) return;
        if (tickWaiter > 0) {
            tickWaiter--;
            return;
        }
        if (maxCycles.get() > 0 && transactionCycles >= maxCycles.get()) {
            info("Max cycles reached.");
            toggle();
            return;
        }

        switch (state) {
            case START -> startCycle();
            case WAIT_SHOP -> waitForShop();
            case CLICK_CATEGORY -> clickCategory();
            case CLICK_ITEM -> clickItem();
            case CLICK_STACK_BATCH -> clickFixedShopSlot(stackBatchSlot.get(), CLICK_ADD);
            case CLICK_ADD -> clickAddButton();
            case CLICK_CONFIRM -> clickConfirm();
            case WAIT_TRANSACTION -> waitForTransaction();
            case FIND_CHEST -> findAndOpenChest();
            case WAIT_CHEST -> waitForChest();
            case DEPOSIT -> depositMoveAll();
            case WAIT_GROUND_PICKUP -> waitForGroundPickup();
            default -> state = START;
        }
    }

    private void startCycle() {
        int inventoryCount = getTargetItemCount();
        long groundCount = getNearbyGroundTargetItemCount();

        if (mode.get() == Mode.Sell && inventoryCount == 0) {
            info("No target items left to sell.");
            toggle();
            return;
        }
        if (mode.get() == Mode.BuyAndDeposit && inventoryCount > 0) {
            state = FIND_CHEST;
            return;
        }
        if (mode.get() == Mode.BuyAndDeposit && groundCount > 0) {
            groundWaitTicks = 0;
            state = WAIT_GROUND_PICKUP;
            return;
        }
        if (mode.get() == Mode.Buy && groundCount > 0) {
            warning("Target items are still on the ground. Stopping before another purchase.");
            toggle();
            return;
        }

        addClicksDone = 0;
        preTransactionCount = inventoryCount;
        mc.player.connection.sendCommand(shopCommand.get());
        state = WAIT_SHOP;
        timeout = 60;
        tickWaiter = normalDelay.get();
    }

    private void waitForShop() {
        if (getHandledScreen() != null) {
            state = CLICK_CATEGORY;
            tickWaiter = clickDelay.get();
            return;
        }
        if (--timeout <= 0) {
            warning("Shop GUI did not open; retrying.");
            state = START;
            tickWaiter = normalDelay.get();
        }
    }

    private void clickCategory() {
        AbstractContainerScreen<?> screen = requireShopScreen();
        if (screen == null) return;
        int detected = autoDetectSlots.get() ? findSlotByKeywords(screen, categoryKeyword.get()) : -1;
        int slot = detected >= 0 ? detected : categorySlot.get();
        if (handleContainerInput(screen, slot)) advanceAfterClick(CLICK_ITEM);
    }

    private void clickItem() {
        AbstractContainerScreen<?> screen = requireShopScreen();
        if (screen == null) return;
        int slot = -1;
        if (autoDetectSlots.get()) {
            slot = findSlotByItem(screen, targetItem.get());
            if (slot < 0) slot = findSlotByKeywords(screen, itemKeyword.get());
        }
        if (slot < 0) slot = itemSlot.get();
        if (antiWrongItem.get() && !validItemButton(screen, slot)) {
            error("Item button did not match " + targetItem.get().getName(net.minecraft.world.item.ItemStack.EMPTY).getString() + ". Stopping.");
            toggle();
            return;
        }
        if (handleContainerInput(screen, slot)) advanceAfterClick(CLICK_STACK_BATCH);
    }

    private void clickFixedShopSlot(int slot, int nextState) {
        AbstractContainerScreen<?> screen = requireShopScreen();
        if (screen == null) return;
        if (handleContainerInput(screen, slot)) advanceAfterClick(nextState);
    }

    private void clickAddButton() {
        if (addClicksDone >= addClicks.get()) {
            state = CLICK_CONFIRM;
            tickWaiter = clickDelay.get();
            return;
        }
        AbstractContainerScreen<?> screen = requireShopScreen();
        if (screen == null) return;
        if (handleContainerInput(screen, addSlot.get())) {
            addClicksDone++;
            tickWaiter = clickDelay.get();
        }
    }

    private void clickConfirm() {
        AbstractContainerScreen<?> screen = requireShopScreen();
        if (screen == null) return;
        preTransactionCount = getTargetItemCount();
        if (handleContainerInput(screen, confirmSlot.get())) {
            state = WAIT_TRANSACTION;
            timeout = 80;
            tickWaiter = clickDelay.get();
        }
    }

    private void waitForTransaction() {
        int currentCount = getTargetItemCount();
        boolean completed = mode.get() == Mode.Sell
            ? currentCount < preTransactionCount
            : currentCount > preTransactionCount || getNearbyGroundTargetItemCount() > 0;

        if (completed) {
            transactionCycles++;
            if (mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
            tickWaiter = normalDelay.get();
            if (mode.get() == Mode.BuyAndDeposit) {
                state = currentCount > 0 ? FIND_CHEST : WAIT_GROUND_PICKUP;
                groundWaitTicks = 0;
            } else {
                state = START;
            }
            return;
        }

        if (--timeout <= 0) {

            if (getTargetItemCount() > 0) {
                state = FIND_CHEST;
            } else if (getNearbyGroundTargetItemCount() > 0) {
                state = WAIT_GROUND_PICKUP;
            } else {
                warning("Purchase was not confirmed by the server; retrying.");
                if (mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
                state = START;
            }
            tickWaiter = normalDelay.get();
        }
    }

    private AbstractContainerScreen<?> requireShopScreen() {
        AbstractContainerScreen<?> screen = getHandledScreen();
        if (screen != null) {
            timeout = 40;
            return screen;
        }

        if (timeout <= 0) timeout = 40;
        if (--timeout <= 0) {
            warning("Shop GUI disappeared during navigation; restarting the transaction.");
            state = START;
            tickWaiter = normalDelay.get();
        } else {
            tickWaiter = 1;
        }
        return null;
    }

    private void advanceAfterClick(int nextState) {
        state = nextState;
        timeout = 40;
        tickWaiter = clickDelay.get();
    }

    private boolean handleContainerInput(AbstractContainerScreen<?> screen, int slot) {
        int containerSlots = getContainerSize(screen);
        if (slot < 0 || slot >= containerSlots) {
            error("Configured shop slot " + slot + " is outside the shop container. Stopping.");
            toggle();
            return false;
        }
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    private void findAndOpenChest() {
        if (getTargetItemCount() == 0) {
            if (getNearbyGroundTargetItemCount() > 0) {
                groundWaitTicks = 0;
                state = WAIT_GROUND_PICKUP;
            } else {
                state = START;
            }
            noChestRetries = 0;
            return;
        }

        currentChest = findNearbyChest();
        if (currentChest == null) {

            if (noChestRetries == 0 || noChestRetries % 20 == 0) {
                warning("No usable chest in range; waiting for one to become available (items stay safe in inventory).");
            }
            noChestRetries++;
            tickWaiter = normalDelay.get();
            return;
        }
        noChestRetries = 0;
        chestReopenFails = 0;
        faceChest(currentChest);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(currentChest), Direction.UP, currentChest, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        state = WAIT_CHEST;
        timeout = 40;
        tickWaiter = normalDelay.get();
    }

    private void waitForChest() {
        AbstractContainerScreen<?> screen = getHandledScreen();
        if (screen != null && getContainerSize(screen) > 0) {
            int remaining = computeChestRemainingCapacity(screen);
            if (remaining <= 0) {

                markChestFull(currentChest);
                mc.player.closeContainer();
                state = FIND_CHEST;
                tickWaiter = normalDelay.get();
                return;
            }
            chestReopenFails = 0;
            depositStallTicks = 0;
            depositLastCount = getTargetItemCount();
            state = DEPOSIT;
            tickWaiter = depositDelay.get();
            return;
        }

        if (currentChest != null) {
            faceChest(currentChest);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(currentChest), Direction.UP, currentChest, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (--timeout <= 0) {

            markChestFull(currentChest);
            state = FIND_CHEST;
            tickWaiter = normalDelay.get();
        }
    }

    private void depositMoveAll() {
        AbstractContainerScreen<?> screen = getHandledScreen();
        if (screen == null) {

            if (++chestReopenFails > 6) {
                markChestFull(currentChest);
                warning("Chest at " + currentChest + " could not be kept open; skipping it.");
                state = FIND_CHEST;
                tickWaiter = normalDelay.get();
                return;
            }
            if (currentChest != null) {
                faceChest(currentChest);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(currentChest), Direction.UP, currentChest, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            }
            state = WAIT_CHEST;
            timeout = 40;
            tickWaiter = normalDelay.get();
            return;
        }
        chestReopenFails = 0;

        if (!mc.player.containerMenu.getCarried().isEmpty()) {
            mc.player.closeContainer();
            state = WAIT_CHEST;
            timeout = 40;
            tickWaiter = normalDelay.get();
            return;
        }

        int count = getTargetItemCount();
        if (count == 0) {

            mc.player.closeContainer();
            state = getNearbyGroundTargetItemCount() > 0 ? WAIT_GROUND_PICKUP : START;
            groundWaitTicks = 0;
            tickWaiter = normalDelay.get();
            return;
        }

        if (count < depositLastCount) depositStallTicks = 0;
        else if (++depositStallTicks > 30) {
            markChestFull(currentChest);
            mc.player.closeContainer();
            state = FIND_CHEST;
            tickWaiter = normalDelay.get();
            depositStallTicks = 0;
            return;
        }
        depositLastCount = count;

        if (computeChestRemainingCapacity(screen) <= 0) {

            markChestFull(currentChest);
            mc.player.closeContainer();
            state = FIND_CHEST;
            tickWaiter = normalDelay.get();
            return;
        }

        int containerSize = getContainerSize(screen);
        int total = screen.getMenu().slots.size();
        for (int i = containerSize; i < total; i++) {
            ItemStack stack = screen.getMenu().slots.get(i).getItem();
            if (!stack.isEmpty() && stack.getItem() == targetItem.get()) {
                clickSlotRaw(screen, i, ContainerInput.QUICK_MOVE);
            }
        }

        if (computeChestRemainingCapacity(screen) <= 0) {
            markChestFull(currentChest);
        }
        tickWaiter = depositDelay.get();
    }

    private boolean clickSlotRaw(AbstractContainerScreen<?> screen, int slot, ContainerInput action) {
        int total = screen.getMenu().slots.size();
        if (slot < 0 || slot >= total) {
            error("Slot " + slot + " is outside the screen. Stopping.");
            toggle();
            return false;
        }
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, action, mc.player);
        return true;
    }

    private void waitForGroundPickup() {
        if (getTargetItemCount() > 0) {
            state = FIND_CHEST;
            tickWaiter = normalDelay.get();
            return;
        }
        if (getNearbyGroundTargetItemCount() == 0) {
            state = START;
            tickWaiter = normalDelay.get();
            return;
        }

        if (++groundWaitTicks % 240 == 0) {
            warning("Waiting to pick up dropped items; move closer if they are out of reach.");
        }
        tickWaiter = 2;
    }

    private AbstractContainerScreen<?> getHandledScreen() {
        return mc.gui.screen() instanceof AbstractContainerScreen<?> handled ? handled : null;
    }

    private int getContainerSize(AbstractContainerScreen<?> screen) {
        return Math.max(0, screen.getMenu().slots.size() - 36);
    }

    private int findSlotByItem(AbstractContainerScreen<?> screen, Item item) {
        int end = getContainerSize(screen);
        for (int i = 0; i < end; i++) {
            ItemStack stack = screen.getMenu().slots.get(i).getItem();
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findSlotByKeywords(AbstractContainerScreen<?> screen, String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isBlank()) return -1;
        String[] keywords = rawKeywords.split(",");
        int end = getContainerSize(screen);
        for (int i = 0; i < end; i++) {
            ItemStack stack = screen.getMenu().slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String key = stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                String candidate = keyword.trim().toLowerCase(Locale.ROOT);
                if (!candidate.isEmpty() && (name.contains(candidate) || key.contains(candidate))) return i;
            }
        }
        return -1;
    }

    private boolean validItemButton(AbstractContainerScreen<?> screen, int slot) {
        int end = getContainerSize(screen);
        if (slot < 0 || slot >= end) return false;
        ItemStack stack = screen.getMenu().slots.get(slot).getItem();
        if (stack.isEmpty()) return false;
        if (stack.getItem() == targetItem.get()) return true;
        String keyword = itemKeyword.get() == null ? "" : itemKeyword.get().trim().toLowerCase(Locale.ROOT);
        return !keyword.isEmpty() && (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(keyword)
            || stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT).contains(keyword));
    }

    private int computeChestRemainingCapacity(AbstractContainerScreen<?> screen) {
        int containerSize = getContainerSize(screen);
        Item target = targetItem.get();
        int maxPer = target.getDefaultMaxStackSize();
        int capacity = 0;
        for (int i = 0; i < containerSize; i++) {
            ItemStack stack = screen.getMenu().slots.get(i).getItem();
            if (stack.isEmpty()) capacity += maxPer;
            else if (stack.getItem() == target) capacity += Math.max(0, maxPer - stack.getCount());
        }
        return capacity;
    }

    private int getTargetItemCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem.get()) count += stack.getCount();
        }
        return count;
    }

    private long getNearbyGroundTargetItemCount() {
        if (mc.player == null || mc.level == null) return 0;
        AABB searchBox = mc.player.getBoundingBox().inflate(groundItemRange.get());
        long count = 0;
        for (ItemEntity entity : mc.level.getEntities(EntityTypeTest.forClass(ItemEntity.class), searchBox,
            entity -> entity.isAlive() && !entity.getItem().isEmpty() && entity.getItem().getItem() == targetItem.get())) {
            count += entity.getItem().getCount();
        }
        return count;
    }

    private BlockPos findNearbyChest() {
        BlockPos origin = mc.player.blockPosition();
        int range = chestRange.get();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -range; x <= range; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (fullChests.contains(pos) || !isDepositContainer(mc.level.getBlockState(pos))) continue;
                    double distance = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean isDepositContainer(BlockState state) {
        return state.is(Blocks.CHEST)
            || (useTrappedChests.get() && state.is(Blocks.TRAPPED_CHEST))
            || (useBarrels.get() && state.is(Blocks.BARREL));
    }

    private void markChestFull(BlockPos pos) {
        if (pos == null) return;
        fullChests.add(pos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);
            if (mc.level != null && isDepositContainer(mc.level.getBlockState(adjacent))) fullChests.add(adjacent);
        }
    }

    private void faceChest(BlockPos chest) {
        if (mc.player == null || chest == null) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(chest);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    public void resetFullChests() {
        fullChests.clear();
        info("Full chest memory cleared.");
    }

    public int getBuyAttempts() {
        return transactionCycles;
    }
}
