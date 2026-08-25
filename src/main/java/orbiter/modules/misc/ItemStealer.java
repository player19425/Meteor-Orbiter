package orbiter.modules.misc;

import orbiter.Orbiter;
import orbiter.commands.GivePresetItemsCommand;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.item.trading.MerchantOffers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ItemStealer extends Module {

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgAutoSteal  = settings.createGroup("Auto-Steal");
    private final SettingGroup sgShiftSteal = settings.createGroup("Shift-Click Steal");
    private final SettingGroup sgVillager   = settings.createGroup("Villager");
    private final SettingGroup sgContainer  = settings.createGroup("Container");
    private final SettingGroup sgFilters    = settings.createGroup("Filters");
    private final SettingGroup sgPersistence = settings.createGroup("Persistence");

    private final Setting<Boolean> pickBlockClone = sgGeneral.add(new BoolSetting.Builder()
        .name("pick-block-clone")
        .description("Clone any item you middle-click (pick-block) in a GUI or the world. Does NOT send any packet to the server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rightClickClone = sgGeneral.add(new BoolSetting.Builder()
        .name("right-click-clone")
        .description("Clone any item you right-click in a GUI. Works on Shopkeepers, chest shops, and any container GUI.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> creativeGive = sgGeneral.add(new BoolSetting.Builder()
        .name("creative-give")
        .description("When in creative mode, also send the cloned item via ServerboundSetCreativeModeSlotPacket so it actually appears in your inventory server-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Print chat messages when items are cloned or trades bypassed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cloneOnAnyGui = sgGeneral.add(new BoolSetting.Builder()
        .name("clone-on-any-gui")
        .description("Pick-block clone works on ANY AbstractContainerScreen, not just specific GUI types.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSteal = sgAutoSteal.add(new BoolSetting.Builder()
        .name("auto-steal")
        .description("Automatically clone every visible slot in the open GUI on a configurable tick interval.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> autoStealDelay = sgAutoSteal.add(new IntSetting.Builder()
        .name("auto-steal-delay")
        .description("Ticks between each auto-steal cycle.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 200)
        .visible(autoSteal::get)
        .build()
    );

    private final Setting<Boolean> autoStealSkipPlayerInv = sgAutoSteal.add(new BoolSetting.Builder()
        .name("skip-player-inventory")
        .description("When auto-stealing, skip the player's own inventory slots in the GUI.")
        .defaultValue(true)
        .visible(autoSteal::get)
        .build()
    );

    private final Setting<Boolean> autoStealSkipEmpty = sgAutoSteal.add(new BoolSetting.Builder()
        .name("skip-empty-slots")
        .description("When auto-stealing, skip empty slots to reduce spam.")
        .defaultValue(true)
        .visible(autoSteal::get)
        .build()
    );

    private final Setting<Boolean> shiftClickSteal = sgShiftSteal.add(new BoolSetting.Builder()
        .name("shift-click-clone")
        .description("When shift-clicking items in any GUI, clone them client-side instead of moving them server-side.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> shiftClickCancelPacket = sgShiftSteal.add(new BoolSetting.Builder()
        .name("cancel-shift-packet")
        .description("Cancel the shift-click packet to the server so the item stays in the container. The clone is still created client-side.")
        .defaultValue(true)
        .visible(shiftClickSteal::get)
        .build()
    );

    private final Setting<Boolean> tradeBypass = sgVillager.add(new BoolSetting.Builder()
        .name("trade-bypass")
        .description("Inject merchant trade result items client-side without server validation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tradeBypassOnPick = sgVillager.add(new BoolSetting.Builder()
        .name("trade-bypass-on-pick")
        .description("Also trigger trade bypass when pressing the pick-block key on the result slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> villagerTradeDump = sgVillager.add(new BoolSetting.Builder()
        .name("trade-dump")
        .description("When opening a villager GUI, automatically clone ALL trade result items at once.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> villagerTradeDumpNotify = sgVillager.add(new BoolSetting.Builder()
        .name("trade-dump-notify")
        .description("Print a summary when trade dump completes.")
        .defaultValue(true)
        .visible(villagerTradeDump::get)
        .build()
    );

    private final Setting<Boolean> containerSnapshot = sgContainer.add(new BoolSetting.Builder()
        .name("container-snapshot")
        .description("On opening any container GUI, snapshot all items to disk in the orbiter-stolen-items directory.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> containerSnapshotNotify = sgContainer.add(new BoolSetting.Builder()
        .name("snapshot-notify")
        .description("Print a message when a container snapshot is saved.")
        .defaultValue(true)
        .visible(containerSnapshot::get)
        .build()
    );

    private final Setting<Boolean> filterEnabled = sgFilters.add(new BoolSetting.Builder()
        .name("filter-enabled")
        .description("Only auto-clone items matching the configured name regex or item type filter.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> filterNameRegex = sgFilters.add(new StringSetting.Builder()
        .name("name-regex")
        .description("Regex pattern that item display names must match to be auto-cloned. Leave empty to disable name filter.")
        .defaultValue("")
        .visible(filterEnabled::get)
        .build()
    );

    private final Setting<Boolean> filterItemType = sgFilters.add(new BoolSetting.Builder()
        .name("filter-item-type")
        .description("Enable filtering by specific item type. Items must match the type list to be auto-cloned.")
        .defaultValue(false)
        .visible(filterEnabled::get)
        .build()
    );

    private final Setting<List<Item>> filterItems = sgFilters.add(new ItemListSetting.Builder()
        .name("filter-items")
        .description("Items that will be auto-cloned when the item type filter is enabled.")
        .visible(() -> filterEnabled.get() && filterItemType.get())
        .build()
    );

    private final Setting<Boolean> creativePresets = sgPersistence.add(new BoolSetting.Builder()
        .name("creative-presets")
        .description("Add a button/shortcut to give preset items via GivePresetItemsCommand when in creative mode.")
        .defaultValue(false)
        .build()
    );

    private ItemStack lastClonedItem = null;

    private int pickBlockCooldown = 0;
    private boolean bypassedThisClick = false;
    private int autoStealTimer = 0;

    private boolean pendingShiftCancel = false;

    private final Map<String, HotbarPreset> hotbarPresets = new LinkedHashMap<>();

    private String lastContainerSnapshotId = "";

    public ItemStealer() {
        super(Orbiter.CATEGORY, "item-stealer",
            "Steals items from GUIs.");
    }

    public static ItemStealer get() {
        return Modules.get().get(ItemStealer.class);
    }

    public static boolean isGuiCloneEnabled() {
        ItemStealer m = get();
        return m != null && m.isActive() && m.pickBlockClone.get();
    }

    public static boolean isRightClickCloneEnabled() {
        ItemStealer m = get();
        return m != null && m.isActive() && m.rightClickClone.get() && m.cloneOnAnyGui.get();
    }

    @Override
    public void onActivate() {
        pickBlockCooldown = 0;
        bypassedThisClick = false;
        pendingShiftCancel = false;
        lastClonedItem = null;
        autoStealTimer = 0;
        lastContainerSnapshotId = "";
    }

    @Override
    public void onDeactivate() {
        pendingShiftCancel = false;
        bypassedThisClick = false;
    }

    public ItemStack getLastClonedItem() {
        return lastClonedItem;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (pickBlockCooldown > 0) pickBlockCooldown--;

        if (pickBlockClone.get() && pickBlockCooldown <= 0 && mc.options.keyPickItem.isDown()) {
            pickBlockCooldown = 4;

            if (mc.gui.screen() instanceof AbstractContainerScreen<?>) return;

            cloneWorldItem();
        }

        if (autoSteal.get()) {
            autoStealTimer++;
            if (autoStealTimer >= autoStealDelay.get()) {
                autoStealTimer = 0;
                runAutoSteal();
            }
        }

        if (villagerTradeDump.get() && mc.player != null) {
            if (mc.player.containerMenu instanceof MerchantMenu merchant) {

                String dumpId = "merchant@" + System.identityHashCode(merchant);
                if (!dumpId.equals(lastContainerSnapshotId)) {
                    lastContainerSnapshotId = dumpId;
                    runTradeDump(merchant);
                }
            }
        }

        if (containerSnapshot.get() && mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?> handled) {
            String snapId = "container@" + System.identityHashCode(mc.player.containerMenu);
            if (!snapId.equals(lastContainerSnapshotId) && !(mc.player.containerMenu instanceof MerchantMenu)) {
                lastContainerSnapshotId = snapId;
                runContainerSnapshot(handled);
            }
        }
    }

    private void cloneWorldItem() {
        ItemStack target = null;

        if (mc.hitResult instanceof EntityHitResult eh) {
            if (eh.getEntity() instanceof ItemEntity itemEntity) {
                target = perfectClone(itemEntity.getItem());
            }
        }

        if (target == null && mc.hitResult instanceof BlockHitResult bh) {
            var state = mc.level.getBlockState(bh.getBlockPos());
            if (!state.isAir()) {
                target = state.getBlock().asItem().getDefaultInstance();
            }
        }

        if (target == null || target.isEmpty()) return;
        injectClonedIntoInventory(target);
        if (notify.get()) info("Cloned: " + target.getItemName().getString() + " x" + target.getCount());
    }

    public static boolean cloneGuiSlot(Slot slot) {
        if (slot == null || !slot.hasItem()) return false;
        ItemStealer m = get();
        if (m == null || !m.isActive() || !m.pickBlockClone.get()) return false;
        if (!m.cloneOnAnyGui.get()) return false;

        ItemStack original = slot.getItem();
        if (original.isEmpty()) return false;

        if (!m.passesFilter(original)) return false;

        ItemStack clone = m.perfectClone(original);
        m.lastClonedItem = clone;

        m.injectClonedIntoInventory(clone);
        if (m.notify.get()) m.info("Cloned: " + clone.getItemName().getString() + " x" + clone.getCount());
        return true;
    }

    private void runAutoSteal() {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> handled)) return;
        if (mc.player == null || mc.player.containerMenu == null) return;

        AbstractContainerMenu handler = mc.player.containerMenu;
        int containerSlots = handler.slots.size() - 36;
        if (containerSlots < 0) containerSlots = handler.slots.size();

        int cloned = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);

            if (autoStealSkipPlayerInv.get() && i >= containerSlots) continue;

            if (autoStealSkipEmpty.get() && !slot.hasItem()) continue;

            ItemStack original = slot.getItem();
            if (original == null || original.isEmpty()) continue;

            if (!passesFilter(original)) continue;

            ItemStack clone = perfectClone(original);
            injectClonedIntoInventory(clone);
            lastClonedItem = clone;
            cloned++;
        }

        if (cloned > 0 && notify.get()) {
            info("Auto-stole " + cloned + " item(s) from GUI.");
        }
    }

    public int stealAllSlots() {
        if (mc.player == null || mc.player.containerMenu == null) return 0;

        AbstractContainerMenu handler = mc.player.containerMenu;
        int cloned = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasItem()) continue;

            ItemStack original = slot.getItem();
            if (original == null || original.isEmpty()) continue;

            if (!passesFilter(original)) continue;

            ItemStack clone = perfectClone(original);
            injectClonedIntoInventory(clone);
            lastClonedItem = clone;
            cloned++;
        }
        return cloned;
    }

    public int stealSlotRange(int start, int end) {
        if (mc.player == null || mc.player.containerMenu == null) return 0;

        AbstractContainerMenu handler = mc.player.containerMenu;
        int lo = Math.max(0, start);
        int hi = Math.min(handler.slots.size() - 1, end);
        int cloned = 0;

        for (int i = lo; i <= hi; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasItem()) continue;

            ItemStack original = slot.getItem();
            if (original == null || original.isEmpty()) continue;

            if (!passesFilter(original)) continue;

            ItemStack clone = perfectClone(original);
            injectClonedIntoInventory(clone);
            lastClonedItem = clone;
            cloned++;
        }
        return cloned;
    }

    public static boolean onShiftClickSlot(int slotIndex, int button, ContainerInput actionType) {
        ItemStealer m = get();
        if (m == null || !m.isActive() || !m.shiftClickSteal.get()) return false;
        if (actionType != ContainerInput.QUICK_MOVE) return false;

        if (m.mc.player == null || m.mc.player.containerMenu == null) return false;
        if (slotIndex < 0 || slotIndex >= m.mc.player.containerMenu.slots.size()) return false;

        Slot slot = m.mc.player.containerMenu.getSlot(slotIndex);
        if (!slot.hasItem()) return false;

        ItemStack original = slot.getItem();
        if (original.isEmpty()) return false;

        if (!m.passesFilter(original)) return false;

        ItemStack clone = m.perfectClone(original);
        m.injectClonedIntoInventory(clone);
        m.lastClonedItem = clone;

        if (m.notify.get()) m.info("Shift-clone: " + clone.getItemName().getString() + " x" + clone.getCount());

        if (m.shiftClickCancelPacket.get()) {
            m.pendingShiftCancel = true;
            return true;
        }
        return false;
    }

    public boolean consumePendingShiftCancel() {
        boolean was = pendingShiftCancel;
        pendingShiftCancel = false;
        return was;
    }

    private void runTradeDump(MerchantMenu merchant) {
        try {
            MerchantOffers offers = merchant.getOffers();
            if (offers == null || offers.isEmpty()) return;

            int cloned = 0;
            for (var offer : offers) {
                ItemStack result = offer.getResult();
                if (result == null || result.isEmpty()) continue;

                if (!passesFilter(result)) continue;

                ItemStack copy = perfectClone(result);
                injectClonedIntoInventory(copy);
                cloned++;
            }

            if (cloned > 0 && villagerTradeDumpNotify.get()) {
                info("Trade dump: cloned " + cloned + "/" + offers.size() + " trade results.");
            }
        } catch (Exception e) {
            if (notify.get()) warning("Trade dump failed: " + e.getMessage());
        }
    }

    public int dumpTrades() {
        if (mc.player == null || !(mc.player.containerMenu instanceof MerchantMenu merchant)) return -1;

        MerchantOffers offers = merchant.getOffers();
        if (offers == null || offers.isEmpty()) return 0;

        int cloned = 0;
        for (var offer : offers) {
            ItemStack result = offer.getResult();
            if (result == null || result.isEmpty()) continue;
            if (!passesFilter(result)) continue;

            ItemStack copy = perfectClone(result);
            injectClonedIntoInventory(copy);
            lastClonedItem = copy;
            cloned++;
        }
        return cloned;
    }

    private void runContainerSnapshot(AbstractContainerScreen<?> handled) {
        try {
            if (mc.player == null || mc.player.containerMenu == null) return;

            AbstractContainerMenu handler = mc.player.containerMenu;
            String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(Instant.now());
            String screenName = handled.getClass().getSimpleName();
            String snapshotId = "snapshot_" + screenName + "_" + timestamp;

            Path dir = storageDir().resolve("snapshots");
            Files.createDirectories(dir);

            CompoundTag root = new CompoundTag();
            root.putString("source", screenName);
            root.putString("timestamp", Instant.now().toString());
            root.putInt("slotCount", handler.slots.size());

            RegistryOps<Tag> ops = registryOps();
            if (ops == null) return;

            int savedCount = 0;
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                if (!slot.hasItem()) continue;

                ItemStack stack = slot.getItem();
                if (stack == null || stack.isEmpty()) continue;

                var encoded = ItemStack.CODEC.encodeStart(ops, stack);
                if (encoded.isSuccess()) {
                    CompoundTag itemTag = (CompoundTag) encoded.getOrThrow();
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("slotIndex", i);
                    entry.put("item", itemTag);
                    root.put("slot_" + i, entry);
                    savedCount++;
                }
            }

            root.putInt("savedCount", savedCount);
            String filename = sanitizeId(snapshotId) + ".dat";
            NbtIo.writeCompressed(root, dir.resolve(filename));

            if (containerSnapshotNotify.get()) {
                info("Container snapshot saved: " + savedCount + " items -> " + filename);
            }
        } catch (Exception e) {
            if (notify.get()) warning("Container snapshot failed: " + e.getMessage());
        }
    }

    public boolean passesFilter(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!filterEnabled.get()) return true;

        boolean nameOk = true;
        boolean typeOk = true;

        String nameRegex = filterNameRegex.get();
        if (nameRegex != null && !nameRegex.isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(nameRegex, Pattern.CASE_INSENSITIVE);
                String displayName = stack.getItemName().getString();
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                nameOk = pattern.matcher(displayName).find() || pattern.matcher(itemId).find();
            } catch (PatternSyntaxException e) {

                nameOk = false;
            }
        } else {

            nameOk = true;
        }

        if (filterItemType.get()) {
            typeOk = filterItems.get().contains(stack.getItem());
        }

        return nameOk && typeOk;
    }

    public ItemStack perfectClone(ItemStack original) {
        if (original == null || original.isEmpty()) return ItemStack.EMPTY;

        try {
            RegistryOps<Tag> ops = registryOps();
            if (ops != null) {
                var result = ItemStack.CODEC.encodeStart(ops, original);
                if (result.isSuccess()) {
                    CompoundTag tag = (CompoundTag) result.getOrThrow();
                    var parsed = ItemStack.CODEC.parse(ops, tag);
                    if (parsed.isSuccess()) {
                        ItemStack clone = parsed.resultOrPartial(s -> {}).orElse(null);
                        if (clone != null && !clone.isEmpty()) return clone;
                    }
                }
            }
        } catch (Exception e) {

        }

        return original.copy();
    }

    private RegistryOps<Tag> registryOps() {
        if (mc.level == null) return null;
        return RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
    }

    public boolean injectClonedIntoInventory(ItemStack stack) {
        LocalPlayer player = mc.player;
        if (player == null || stack == null || stack.isEmpty()) return false;

        ItemStack clone = stack.copy();

        if (!player.getInventory().add(clone)) {
            ItemEntity entity = new ItemEntity(mc.level, player.getX(), player.getY(), player.getZ(), clone);
            mc.level.addEntity(entity);
        }

        if (creativeGive.get() && player.getAbilities().instabuild && mc.getConnection() != null) {
            giveInCreative(clone);
        }

        return true;
    }

    public boolean injectIntoInventory(ItemStack stack) {
        LocalPlayer player = mc.player;
        if (player == null || stack == null || stack.isEmpty()) return false;
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) {
            ItemEntity entity = new ItemEntity(mc.level, player.getX(), player.getY(), player.getZ(), copy);
            mc.level.addEntity(entity);
        }
        return true;
    }

    private void giveInCreative(ItemStack stack) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (!mc.player.getAbilities().instabuild) return;

        int slot = findEmptySlot();
        if (slot < 0) slot = 36 + mc.player.getInventory().getSelectedSlot();

        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
        if (mc.player.containerMenu == mc.player.inventoryMenu && slot < mc.player.inventoryMenu.slots.size()) {
            mc.player.inventoryMenu.getSlot(slot).set(stack);
        }
    }

    private int findEmptySlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return 36 + i;
        }

        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    public static boolean bypassTrade(Slot slot) {
        if (slot == null || slot.index != 2) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof MerchantMenu)) return false;

        ItemStealer m = get();
        if (m == null || !m.isActive()) return false;

        boolean pickPressed = mc.options.keyPickItem.isDown();
        if (!m.tradeBypass.get() && !(m.tradeBypassOnPick.get() && pickPressed)) return false;

        MerchantMenu merchant = (MerchantMenu) mc.player.containerMenu;
        ItemStack result = merchant.getSlot(2).getItem();
        if (result == null || result.isEmpty()) return false;

        ItemStack copy = m.perfectClone(result);
        m.injectClonedIntoInventory(copy);
        if (m.notify.get()) m.info("Trade bypassed: " + copy.getItemName().getString() + " x" + copy.getCount());
        m.bypassedThisClick = true;
        return true;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {

        if (event.packet instanceof ServerboundContainerClickPacket click) {
            if (pendingShiftCancel) {
                pendingShiftCancel = false;
                bypassedThisClick = false;
                event.cancel();
                return;
            }

            if (bypassedThisClick) {
                bypassedThisClick = false;
                event.cancel();
                return;
            }

            if (!tradeBypass.get() && !tradeBypassOnPick.get()) return;
            if (mc.player == null) return;
            if (!(mc.player.containerMenu instanceof MerchantMenu merchant)) return;
            if (click.slotNum() != 2) return;

            if (shiftClickSteal.get() && click.containerInput() == ContainerInput.QUICK_MOVE) {
                ItemStack result = merchant.getSlot(2).getItem();
                if (result != null && !result.isEmpty()) {
                    ItemStack copy = perfectClone(result);
                    injectClonedIntoInventory(copy);
                    if (notify.get()) info("Shift-clone trade: " + copy.getItemName().getString() + " x" + copy.getCount());
                    if (shiftClickCancelPacket.get()) {
                        event.cancel();
                    }
                    return;
                }
            }

            ItemStack result = merchant.getSlot(2).getItem();
            if (result == null || result.isEmpty()) return;

            ItemStack copy = perfectClone(result);
            injectClonedIntoInventory(copy);
            if (notify.get()) info("Trade bypassed: " + copy.getItemName().getString() + " x" + copy.getCount());

            event.cancel();
        }
    }

    public boolean givePresetItem(String presetName) {
        if (mc.player == null || !mc.player.getAbilities().instabuild) return false;
        if (!creativePresets.get()) return false;

        try {

            GivePresetItemsCommand cmd = new GivePresetItemsCommand();

            return givePresetItemInternal(presetName);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean givePresetItemInternal(String presetName) {

        ItemStack presetItem = createQuickPreset(presetName);
        if (presetItem == null || presetItem.isEmpty()) return false;

        giveInCreative(presetItem);
        if (notify.get()) info("Gave preset: " + presetName);
        return true;
    }

    private ItemStack createQuickPreset(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "god-apple-64" -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64);
            case "totem-64" -> new ItemStack(Items.TOTEM_OF_UNDYING, 64);
            case "ender-pearl-64" -> new ItemStack(Items.ENDER_PEARL, 64);
            case "nether-star-64" -> new ItemStack(Items.NETHER_STAR, 64);
            case "obsidian-64" -> new ItemStack(Items.OBSIDIAN, 64);
            case "command-block" -> new ItemStack(Items.COMMAND_BLOCK);
            case "barrier" -> new ItemStack(Items.BARRIER);
            case "bedrock" -> new ItemStack(Items.BEDROCK);
            case "crystal-64" -> new ItemStack(Items.END_CRYSTAL, 64);
            case "experience-64" -> new ItemStack(Items.EXPERIENCE_BOTTLE, 64);
            case "diamond-block-64" -> new ItemStack(Items.DIAMOND_BLOCK, 64);
            case "netherite-block-64" -> new ItemStack(Items.NETHERITE_BLOCK, 64);
            case "emerald-block-64" -> new ItemStack(Items.EMERALD_BLOCK, 64);
            default -> null;
        };
    }

    public List<String> getPresetNames() {
        return List.of(
            "god-apple-64", "totem-64", "ender-pearl-64", "nether-star-64",
            "obsidian-64", "command-block", "barrier", "bedrock",
            "crystal-64", "experience-64", "diamond-block-64",
            "netherite-block-64", "emerald-block-64"
        );
    }

    public boolean saveHotbarPreset(String name) {
        if (mc.player == null) return false;

        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            hotbar[i] = stack.isEmpty() ? ItemStack.EMPTY : perfectClone(stack);
        }

        hotbarPresets.put(name, new HotbarPreset(name, hotbar, Instant.now()));

        try {
            Path dir = storageDir().resolve("hotbar-presets");
            Files.createDirectories(dir);

            CompoundTag root = new CompoundTag();
            root.putString("name", name);
            root.putString("savedAt", Instant.now().toString());

            RegistryOps<Tag> ops = registryOps();
            if (ops != null) {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = hotbar[i];
                    if (stack.isEmpty()) continue;
                    var encoded = ItemStack.CODEC.encodeStart(ops, stack);
                    if (encoded.isSuccess()) {
                        CompoundTag itemTag = (CompoundTag) encoded.getOrThrow();
                        root.put("slot_" + i, itemTag);
                    }
                }
            }
            NbtIo.writeCompressed(root, dir.resolve(sanitizeId(name) + ".dat"));
        } catch (Exception e) {
            warning("Failed to persist hotbar preset: " + e.getMessage());
        }

        if (notify.get()) info("Hotbar preset saved: " + name);
        return true;
    }

    public boolean loadHotbarPreset(String name) {

        HotbarPreset preset = hotbarPresets.get(name);
        if (preset != null) {
            return applyHotbarPreset(preset);
        }

        try {
            Path file = storageDir().resolve("hotbar-presets").resolve(sanitizeId(name) + ".dat");
            if (!Files.exists(file)) {
                if (notify.get()) warning("Hotbar preset not found: " + name);
                return false;
            }

            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            RegistryOps<Tag> ops = registryOps();
            if (ops == null) return false;
            ItemStack[] hotbar = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                if (root.contains("slot_" + i)) {
                    hotbar[i] = ItemStack.CODEC.parse(ops, root.get("slot_" + i))
                        .result().orElse(ItemStack.EMPTY);
                } else {
                    hotbar[i] = ItemStack.EMPTY;
                }
            }

            preset = new HotbarPreset(name, hotbar, Instant.now());
            hotbarPresets.put(name, preset);
            return applyHotbarPreset(preset);
        } catch (Exception e) {
            warning("Failed to load hotbar preset: " + e.getMessage());
            return false;
        }
    }

    private boolean applyHotbarPreset(HotbarPreset preset) {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = preset.slots()[i];
            if (stack == null || stack.isEmpty()) continue;

            ItemStack clone = perfectClone(stack);
            if (creativeGive.get() && mc.player.getAbilities().instabuild && mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + i, clone));
            }
            mc.player.getInventory().setItem(i, clone);
        }

        if (notify.get()) info("Hotbar preset loaded: " + preset.name());
        return true;
    }

    public Set<String> listHotbarPresets() {

        Set<String> names = new TreeSet<>(hotbarPresets.keySet());
        try {
            Path dir = storageDir().resolve("hotbar-presets");
            if (Files.exists(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> p.toString().endsWith(".dat"))
                        .forEach(p -> names.add(p.getFileName().toString().replaceAll("\\.dat$", "")));
                }
            }
        } catch (IOException ignored) {}
        return names;
    }

    public boolean deleteHotbarPreset(String name) {
        hotbarPresets.remove(name);
        try {
            Path file = storageDir().resolve("hotbar-presets").resolve(sanitizeId(name) + ".dat");
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    public record HotbarPreset(String name, ItemStack[] slots, Instant savedAt) {}

    public boolean saveItem(String id, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            Path dir = storageDir();
            Files.createDirectories(dir);

            RegistryOps<Tag> ops = registryOps();
            if (ops == null) return false;

            var result = ItemStack.CODEC.encodeStart(ops, stack);
            if (result.isError()) return false;
            CompoundTag tag = (CompoundTag) result.getOrThrow();
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("item", tag);
            wrapper.putString("savedAt", java.time.Instant.now().toString());
            wrapper.putString("id", id);
            NbtIo.writeCompressed(wrapper, dir.resolve(sanitizeId(id) + ".dat"));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public ItemStack peekItem(String id) {
        try {
            Path file = storageDir().resolve(sanitizeId(id) + ".dat");
            if (!Files.exists(file)) return null;
            CompoundTag wrapper = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (wrapper == null || !wrapper.contains("item")) return null;

            RegistryOps<Tag> ops = registryOps();
            if (ops == null) return null;

            return ItemStack.CODEC.parse(ops, wrapper.get("item")).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack loadItem(String id) {
        ItemStack loaded = peekItem(id);
        if (loaded != null && !loaded.isEmpty()) {
            if (creativeGive.get() && mc.player != null && mc.player.getAbilities().instabuild) {
                giveInCreative(loaded.copy());
            }
        }
        return loaded;
    }

    public boolean deleteItem(String id) {
        try {
            Path file = storageDir().resolve(sanitizeId(id) + ".dat");
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    public Set<String> listItems() {
        Set<String> ids = new TreeSet<>();
        try {
            Path dir = storageDir();
            if (!Files.exists(dir)) return ids;
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".dat"))
                    .forEach(p -> ids.add(p.getFileName().toString().replaceAll("\\.dat$", "")));
            }
        } catch (IOException ignored) {}
        return ids;
    }

    public static Path storageDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("orbiter-stolen-items");
    }

    private static String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public Set<String> listSnapshots() {
        Set<String> ids = new TreeSet<>();
        try {
            Path dir = storageDir().resolve("snapshots");
            if (!Files.exists(dir)) return ids;
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".dat"))
                    .forEach(p -> ids.add(p.getFileName().toString().replaceAll("\\.dat$", "")));
            }
        } catch (IOException ignored) {}
        return ids;
    }

    public String describeSnapshot(String id) {
        try {
            Path file = storageDir().resolve("snapshots").resolve(sanitizeId(id) + ".dat");
            if (!Files.exists(file)) return "Snapshot not found: " + id;

            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            String source = root.getStringOr("source", "unknown");
            String timestamp = root.getStringOr("timestamp", "unknown");
            int savedCount = root.getIntOr("savedCount", 0);
            int slotCount = root.getIntOr("slotCount", 0);

            return String.format("Snapshot '%s': source=%s, time=%s, items=%d/%d slots", id, source, timestamp, savedCount, slotCount);
        } catch (Exception e) {
            return "Failed to read snapshot: " + e.getMessage();
        }
    }

    public boolean isInGui() {
        return mc.gui.screen() instanceof AbstractContainerScreen<?>;
    }

    public AbstractContainerMenu getCurrentHandler() {
        if (mc.player == null) return null;
        return mc.player.containerMenu;
    }
}
