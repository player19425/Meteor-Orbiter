package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

public class AutoFarming extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 8)
        .build());

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Ticks between farm actions.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build());

    private final Setting<Integer> actionsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("actions-per-cycle")
        .description("How many farming actions are performed in one cycle.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 16)
        .build());

    private final Setting<Integer> replantDelay = sgGeneral.add(new IntSetting.Builder()
        .name("replant-delay")
        .description("Tick delay before replant interaction packets.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build());

    private final Setting<Boolean> crops = sgGeneral.add(new BoolSetting.Builder()
        .name("crops")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> cactus = sgGeneral.add(new BoolSetting.Builder()
        .name("cactus")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> sugarcane = sgGeneral.add(new BoolSetting.Builder()
        .name("sugarcane")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> bamboo = sgGeneral.add(new BoolSetting.Builder()
        .name("bamboo")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> breedAnimals = sgGeneral.add(new BoolSetting.Builder()
        .name("breed-animals")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> useBonemeal = sgGeneral.add(new BoolSetting.Builder()
        .name("use-bonemeal")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> moveBonemealToHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("move-bonemeal-to-hotbar")
        .description("If bonemeal is only in inventory, move it to hotbar automatically.")
        .defaultValue(true)
        .visible(useBonemeal::get)
        .build());

    private final Setting<Boolean> useHoe = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-use-hoe")
        .description("Automatically till dirt/grass near crops.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> placeWater = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-place-water")
        .description("Places water on dry farmland zones using water bucket.")
        .defaultValue(false)
        .build());

    private int timer = 0;
    private final Deque<PendingReplant> replantQueue = new ArrayDeque<>();

    public AutoFarming() {
        super(Orbiter.CATEGORY, "auto-farming", "Harvests crops/cactus/sugarcane/bamboo, breeds animals, applies bonemeal and replants with delay.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        replantQueue.clear();
    }

    @Override
    public void onDeactivate() {
        replantQueue.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) return;

        processReplants();

        if (timer > 0) {
            timer--;
            return;
        }

        BlockPos base = mc.player.getBlockPos();
        int r = range.get();
        int done = 0;

        while (done < actionsPerTick.get()) {
            boolean acted = false;

            if (!acted && crops.get() && tryHarvestCrops(base, r)) acted = true;
            if (!acted && cactus.get() && tryHarvestColumn(base, r, CactusBlock.class)) acted = true;
            if (!acted && sugarcane.get() && tryHarvestColumn(base, r, SugarCaneBlock.class)) acted = true;
            if (!acted && bamboo.get() && tryHarvestColumn(base, r, BambooBlock.class)) acted = true;
            if (!acted && useHoe.get() && tryUseHoe(base, r)) acted = true;
            if (!acted && placeWater.get() && tryPlaceWater(base, r)) acted = true;
            if (!acted && useBonemeal.get() && tryBonemeal(base, r)) acted = true;
            if (!acted && breedAnimals.get() && tryBreed(base, r)) acted = true;

            if (!acted) break;
            done++;
        }

        if (done > 0) {
            timer = actionDelay.get();
        }
    }

    private boolean tryHarvestCrops(BlockPos base, int r) {
        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = base.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!(state.getBlock() instanceof CropBlock crop)) continue;
                    if (!crop.isMature(state)) continue;

                    sendBreakPackets(pos);
                    queueReplant(pos, crop);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryHarvestColumn(BlockPos base, int r, Class<? extends Block> type) {
        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = base.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!type.isInstance(state.getBlock())) continue;

                    BlockPos below = pos.down();
                    BlockPos above = pos.up();
                    if (type.isInstance(mc.world.getBlockState(below).getBlock()) && type.isInstance(mc.world.getBlockState(above).getBlock())) {
                        sendBreakPackets(pos);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean tryBonemeal(BlockPos base, int r) {
        int slot = findOrMoveToHotbar(Items.BONE_MEAL, moveBonemealToHotbar.get());
        if (slot == -1) return false;

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = base.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!(state.getBlock() instanceof CropBlock crop)) continue;
                    if (crop.isMature(state)) continue;

                    int prev = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(slot, false);

                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

                    InvUtils.swap(prev, false);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryBreed(BlockPos base, int r) {
        double rangeSq = r * r;

        for (AnimalEntity animal : mc.world.getEntitiesByClass(AnimalEntity.class, mc.player.getBoundingBox().expand(r), e -> true)) {
            if (animal.squaredDistanceTo(mc.player) > rangeSq) continue;
            if (animal.isBaby() || animal.isInLove()) continue;

            int slot = findBreedingSlot(animal);
            if (slot == -1) continue;

            int prev = mc.player.getInventory().getSelectedSlot();
            InvUtils.swap(slot, false);

            mc.interactionManager.interactEntity(mc.player, animal, Hand.MAIN_HAND);

            InvUtils.swap(prev, false);
            return true;
        }

        return false;
    }

    private int findBreedingSlot(AnimalEntity animal) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (animal.isBreedingItem(stack)) return i;
        }

        return -1;
    }

    private int findHotbarItem(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private void sendBreakPackets(BlockPos pos) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
            pos,
            Direction.UP
        ));

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
            pos,
            Direction.UP
        ));
    }

    private void queueReplant(BlockPos pos, CropBlock crop) {
        Item replantItem = crop.asItem();
        if (replantItem == Items.AIR) return;

        while (replantQueue.size() >= 128) replantQueue.pollFirst();
        replantQueue.addLast(new PendingReplant(pos, replantItem, replantDelay.get()));
    }

    private void processReplants() {
        if (replantQueue.isEmpty() || mc.player == null || mc.world == null) return;

        PendingReplant pending = replantQueue.peekFirst();
        if (pending == null) return;

        if (pending.delayTicks > 0) {
            pending.delayTicks--;
            return;
        }

        replantQueue.pollFirst();
        if (!mc.world.getBlockState(pending.pos).isReplaceable()) return;

        int slot = findReplantSlot(pending.seedItem);
        if (slot == -1) return;

        int prev = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(slot, false);

        BlockPos below = pending.pos.down();
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below), Direction.UP, below, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

        InvUtils.swap(prev, false);
    }

    private int findReplantSlot(Item seedItem) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(seedItem)) return i;

            if (stack.getItem() instanceof BlockItem && stack.getItem() == seedItem) return i;
        }

        return -1;
    }

    private int findOrMoveToHotbar(Item item, boolean allowMove) {
        int hotbar = findHotbarItem(item);
        if (hotbar != -1) return hotbar;
        if (!allowMove || mc.player == null) return -1;

        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                invSlot = i;
                break;
            }
        }
        if (invSlot == -1) return -1;

        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                target = i;
                break;
            }
        }
        if (target == -1) target = mc.player.getInventory().getSelectedSlot();

        InvUtils.move().from(invSlot).toHotbar(target);
        return target;
    }

    private int findHoeHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof HoeItem) return i;
        }
        return -1;
    }

    private boolean tryUseHoe(BlockPos base, int r) {
        int hoeSlot = findHoeHotbarSlot();
        if (hoeSlot == -1 || mc.player == null || mc.world == null) return false;

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = base.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    BlockState above = mc.world.getBlockState(pos.up());

                    boolean tillable = state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT_PATH);
                    if (!tillable) continue;
                    if (!above.isAir()) continue;

                    int prev = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(hoeSlot, false);

                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

                    InvUtils.swap(prev, false);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryPlaceWater(BlockPos base, int r) {
        int waterBucketSlot = findOrMoveToHotbar(Items.WATER_BUCKET, true);
        if (waterBucketSlot == -1 || mc.player == null || mc.world == null) return false;

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos farmlandPos = base.add(x, y, z);
                    BlockState farmland = mc.world.getBlockState(farmlandPos);
                    if (!(farmland.getBlock() instanceof FarmlandBlock)) continue;
                    if (!farmland.contains(Properties.MOISTURE)) continue;
                    if (farmland.get(Properties.MOISTURE) >= 7) continue;
                    if (hasNearbyWater(farmlandPos, 4)) continue;

                    BlockPos placePos = farmlandPos.up();
                    if (!mc.world.getBlockState(placePos).isReplaceable()) continue;
                    BlockPos support = placePos.down();
                    if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) continue;

                    int prev = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(waterBucketSlot, false);

                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(support), Direction.UP, support, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

                    InvUtils.swap(prev, false);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasNearbyWater(BlockPos pos, int radius) {
        if (mc.world == null) return false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos check = pos.add(x, 0, z);
                if (!mc.world.getFluidState(check).isEmpty() && mc.world.getFluidState(check).isStill()) {
                    if (mc.world.getFluidState(check).isIn(net.minecraft.registry.tag.FluidTags.WATER)) return true;
                }
            }
        }
        return false;
    }

    private static class PendingReplant {
        private final BlockPos pos;
        private final Item seedItem;
        private int delayTicks;

        private PendingReplant(BlockPos pos, Item seedItem, int delayTicks) {
            this.pos = pos;
            this.seedItem = seedItem;
            this.delayTicks = delayTicks;
        }
    }
}
