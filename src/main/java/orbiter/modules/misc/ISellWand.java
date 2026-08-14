package orbiter.modules.misc;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ISellWand extends Module {

    public enum TargetMode {
        Specified,
        AllNearby
    }

    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgRecording = settings.createGroup("Recording");
    private final SettingGroup sgTypes     = settings.createGroup("Container Types");

    private final Setting<Item> sellWandItem = sgGeneral.add(new ItemSetting.Builder()
        .name("sell-wand-item")
        .description("Item that acts as the sell wand. Equipped into the main hand before each click.")
        .defaultValue(Items.BLAZE_ROD)
        .build());

    private final Setting<Boolean> recordMode = sgRecording.add(new BoolSetting.Builder()
        .name("record-mode")
        .description("While ON, right-clicking a chest records its coordinates instead of running the wand. Double chests are stored as one entry.")
        .defaultValue(false)
        .build());

    private final Setting<List<String>> chestList = sgRecording.add(new StringListSetting.Builder()
        .name("chest-list")
        .description("Recorded chest positions. Format \"x,y,z\" or \"x,y,z,ticks\" for a per-chest delay. Populated by record-mode or edited manually.")
        .defaultValue(new ArrayList<>())
        .build());

    private final Setting<TargetMode> targetMode = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("Specified runs the recorded chest list. AllNearby scans around you each cycle.")
        .defaultValue(TargetMode.Specified)
        .visible(() -> !recordMode.get())
        .build());

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Blocks around you to search for chests in AllNearby mode.")
        .defaultValue(5)
        .min(1).sliderRange(1, 12)
        .visible(() -> !recordMode.get() && targetMode.get() == TargetMode.AllNearby)
        .build());

    private final Setting<Integer> globalDelay = sgGeneral.add(new IntSetting.Builder()
        .name("global-delay")
        .description("Ticks to wait between chest clicks. Overridden per chest when a delay is listed.")
        .defaultValue(10)
        .min(1).sliderRange(1, 100)
        .build());

    private final Setting<Boolean> loop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .description("Repeat the cycle forever. Disable to run through the chests once.")
        .defaultValue(true)
        .visible(() -> !recordMode.get())
        .build());

    private final Setting<Boolean> useChests = sgTypes.add(new BoolSetting.Builder()
        .name("use-chests")
        .description("Click normal chests.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useTrappedChests = sgTypes.add(new BoolSetting.Builder()
        .name("use-trapped-chests")
        .description("Click trapped chests.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useBarrels = sgTypes.add(new BoolSetting.Builder()
        .name("use-barrels")
        .description("Click barrels.")
        .defaultValue(false)
        .build());

    private int tickWaiter = 0;
    private final List<Target> targets = new ArrayList<>();
    private int targetIndex = 0;
    private int cycleCount = 0;

    private static class Target {
        final BlockPos pos;
        final int delay;

        Target(BlockPos pos, int delay) {
            this.pos = pos;
            this.delay = delay;
        }
    }

    public ISellWand() {
        super(Orbiter.CATEGORY_STUPID, "i-sell-wand",
            "Equips a sell wand and right-clicks recorded/nearby chests on a delay to automate selling.");
    }

    @Override
    public void onActivate() {
        tickWaiter = 0;
        targets.clear();
        targetIndex = 0;
        cycleCount = 0;
        info("ISellWand started" + (recordMode.get() ? " (record-mode: right-click chests to add them)." : "."));
    }

    @Override
    public void onDeactivate() {
        info("ISellWand stopped. Cycles: " + cycleCount + ", recorded chests: " + chestList.get().size());
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!recordMode.get() || mc.world == null) return;
        BlockPos pos = event.result.getBlockPos();
        if (!isTargetContainer(mc.world.getBlockState(pos))) return;

        if (listContainsPos(pos) || adjacentChestRecorded(pos)) return;

        List<String> list = new ArrayList<>(chestList.get());
        list.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        chestList.set(list);
        info("Recorded chest at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] (" + list.size() + " total).");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (recordMode.get()) return;

        if (tickWaiter > 0) {
            tickWaiter--;
            return;
        }

        if (targetIndex >= targets.size()) {
            if (!loop.get() && cycleCount > 0) {
                info("ISellWand finished one pass.");
                toggle();
                return;
            }
            targets.clear();
            buildTargets();
            targetIndex = 0;
            cycleCount++;
            if (targets.isEmpty()) {
                warning("No chests to use the sell wand on.");
                tickWaiter = 20;
                if (!loop.get()) toggle();
                return;
            }
        }

        Target t = targets.get(targetIndex);

        if (!isTargetContainer(mc.world.getBlockState(t.pos))) {
            targetIndex++;
            tickWaiter = 0;
            return;
        }

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            tickWaiter = 2;
            return;
        }

        if (!equipWand()) {
            warning("Sell wand (" + sellWandItem.get().getName().getString() + ") not found in inventory.");
            tickWaiter = 20;
            if (!loop.get()) toggle();
            return;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(t.pos), Direction.UP, t.pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        targetIndex++;
        tickWaiter = Math.max(1, t.delay);
    }

    private void buildTargets() {
        Map<BlockPos, Integer> listedDelays = new HashMap<>();
        for (String entry : chestList.get()) {
            int[] parsed = parseEntry(entry);
            if (parsed != null) {
                listedDelays.put(new BlockPos(parsed[0], parsed[1], parsed[2]), parsed[3]);
            }
        }

        if (targetMode.get() == TargetMode.Specified) {
            for (Map.Entry<BlockPos, Integer> e : listedDelays.entrySet()) {
                targets.add(new Target(e.getKey(), e.getValue() > 0 ? e.getValue() : globalDelay.get()));
            }
            return;
        }

        BlockPos p = mc.player.getBlockPos();
        int r = range.get();
        Set<BlockPos> added = new HashSet<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = p.add(x, y, z);
                    if (!isTargetContainer(mc.world.getBlockState(pos))) continue;
                    if (adjacentChestAdded(pos, added)) continue;
                    int delay = listedDelays.getOrDefault(pos, globalDelay.get());
                    targets.add(new Target(pos, delay));
                    added.add(pos);
                }
            }
        }
    }

    private boolean equipWand() {
        ItemStack main = mc.player.getMainHandStack();
        if (!main.isEmpty() && main.getItem() == sellWandItem.get()) return true;

        FindItemResult result = InvUtils.find(sellWandItem.get());
        if (!result.found()) return false;

        if (result.isHotbar()) {
            selectHotbarSlot(result.slot());
            return true;
        }

        FindItemResult empty = InvUtils.findEmpty();
        if (empty.found() && empty.isHotbar()) {
            InvUtils.move().from(result.slot()).toHotbar(empty.slot());
            selectHotbarSlot(empty.slot());
            return true;
        }

        warning("No empty hotbar slot to equip the sell wand.");
        return false;
    }

    private void selectHotbarSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private boolean isTargetContainer(BlockState bs) {
        return (useChests.get() && bs.isOf(Blocks.CHEST))
            || (useTrappedChests.get() && bs.isOf(Blocks.TRAPPED_CHEST))
            || (useBarrels.get() && bs.isOf(Blocks.BARREL));
    }

    private boolean listContainsPos(BlockPos p) {
        for (String entry : chestList.get()) {
            int[] parsed = parseEntry(entry);
            if (parsed != null && parsed[0] == p.getX() && parsed[1] == p.getY() && parsed[2] == p.getZ()) return true;
        }
        return false;
    }

    private boolean adjacentChestRecorded(BlockPos pos) {
        for (Direction d : HORIZONTALS) {
            BlockPos n = pos.offset(d);
            if (mc.world != null && isTargetContainer(mc.world.getBlockState(n)) && listContainsPos(n)) return true;
        }
        return false;
    }

    private boolean adjacentChestAdded(BlockPos pos, Set<BlockPos> added) {
        for (Direction d : HORIZONTALS) {
            if (added.contains(pos.offset(d))) return true;
        }
        return false;
    }

    private int[] parseEntry(String entry) {
        if (entry == null) return null;
        String[] parts = entry.trim().split("\\s*,\\s*");
        try {
            if (parts.length == 3) {
                return new int[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    -1
                };
            }
            if (parts.length >= 4) {
                return new int[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
                };
            }
        } catch (NumberFormatException ignored) {

        }
        return null;
    }

    public void clearChests() {
        chestList.set(new ArrayList<>());
        info("Chest list cleared.");
    }

    public void toggleRecordMode() {
        recordMode.set(!recordMode.get());
        info("Record-mode " + (recordMode.get() ? "§aON" : "§cOFF") + ". "
            + (recordMode.get() ? "Right-click chests to add them." : "Running wand on recorded chests."));
    }

    public List<String> getChestList() {
        return new ArrayList<>(chestList.get());
    }

    public int recordedChestCount() {
        return chestList.get().size();
    }
}
