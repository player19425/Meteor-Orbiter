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
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.EntityHitResult;


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
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.gameMode == null) return;

        processReplants();

        if (timer > 0) {
            timer--;
            return;
        }

        BlockPos base = mc.player.blockPosition();
        int r = range.get();
        int done = 0;

        while (done < actionsPerTick.get()) {
            boolean acted = false;

            if (!acted && crops.get() && tryHarvestCrops(base, r)) acted = true;
            if (!acted && cactus.get() && tryHarvestColumn(base, r, CactusBlock.class)) acted = true;
            if (!acted && sugarcane.get() && tryHarvestColumn(base, r, SugarCaneBlock.class)) acted = true;
            if (!acted && bamboo.get() && tryHarvestColumn(base, r, BambooStalkBlock.class)) acted = true;
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
                    BlockPos pos = base.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);

                    if (!(state.getBlock() instanceof CropBlock crop)) continue;
                    if (!crop.isMaxAge(state)) continue;

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
                    BlockPos pos = base.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);

                    if (!type.isInstance(state.getBlock())) continue;

                    BlockPos below = pos.below();
                    BlockPos above = pos.above();
                    if (type.isInstance(mc.level.getBlockState(below).getBlock()) && type.isInstance(mc.level.getBlockState(above).getBlock())) {
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
                    BlockPos pos = base.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);

                    if (!(state.getBlock() instanceof CropBlock crop)) continue;
                    if (crop.isMaxAge(state)) continue;

                    int prev = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(slot, false);

                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);

                    InvUtils.swap(prev, false);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryBreed(BlockPos base, int r) {
        double rangeSq = r * r;

        for (Animal animal : mc.level.getEntities(EntityTypeTest.forClass(Animal.class), mc.player.getBoundingBox().inflate(r), e -> true)) {
            if (animal.distanceToSqr(mc.player) > rangeSq) continue;
            if (animal.isBaby() || animal.isInLove()) continue;

            int slot = findBreedingSlot(animal);
            if (slot == -1) continue;

            int prev = mc.player.getInventory().getSelectedSlot();
            InvUtils.swap(slot, false);

            mc.gameMode.interact(mc.player, animal, new EntityHitResult(animal), InteractionHand.MAIN_HAND);

            InvUtils.swap(prev, false);
            return true;
        }

        return false;
    }

    private int findBreedingSlot(Animal animal) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (animal.isFood(stack)) return i;
        }

        return -1;
    }

    private int findHotbarItem(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private void sendBreakPackets(BlockPos pos) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            pos,
            Direction.UP
        ));

        mc.getConnection().send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
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
        if (replantQueue.isEmpty() || mc.player == null || mc.level == null) return;

        PendingReplant pending = replantQueue.peekFirst();
        if (pending == null) return;

        if (pending.delayTicks > 0) {
            pending.delayTicks--;
            return;
        }

        replantQueue.pollFirst();
        if (!mc.level.getBlockState(pending.pos).canBeReplaced()) return;

        int slot = findReplantSlot(pending.seedItem);
        if (slot == -1) return;

        int prev = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(slot, false);

        BlockPos below = pending.pos.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);

        InvUtils.swap(prev, false);
    }

    private int findReplantSlot(Item seedItem) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(seedItem)) return i;

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
            if (mc.player.getInventory().getItem(i).is(item)) {
                invSlot = i;
                break;
            }
        }
        if (invSlot == -1) return -1;

        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
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
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof HoeItem) return i;
        }
        return -1;
    }

    private boolean tryUseHoe(BlockPos base, int r) {
        int hoeSlot = findHoeHotbarSlot();
        if (hoeSlot == -1 || mc.player == null || mc.level == null) return false;

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = base.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    BlockState above = mc.level.getBlockState(pos.above());

                    boolean tillable = state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH);
                    if (!tillable) continue;
                    if (!above.isAir()) continue;

                    int prev = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(hoeSlot, false);

                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);

                    InvUtils.swap(prev, false);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean tryPlaceWater(BlockPos base, int r) {
        int waterBucketSlot = findOrMoveToHotbar(Items.WATER_BUCKET, true);
        if (waterBucketSlot == -1 || mc.player == null || mc.level == null) return false;

        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos farmlandPos = base.offset(x, y, z);
                    BlockState farmland = mc.level.getBlockState(farmlandPos);
                    if (!(farmland.getBlock() instanceof FarmlandBlock)) continue;
                    if (!farmland.hasProperty(BlockStateProperties.MOISTURE)) continue;
                    if (farmland.getValue(BlockStateProperties.MOISTURE) >= 7) continue;
                    if (hasNearbyWater(farmlandPos, 4)) continue;

                    BlockPos placePos = farmlandPos.above();
                    if (!mc.level.getBlockState(placePos).canBeReplaced()) continue;
                    BlockPos support = placePos.below();
                    if (!mc.level.getBlockState(support).isSolid()) continue;

                    int prev = mc.player.getInventory().getSelectedSlot();
                    InvUtils.swap(waterBucketSlot, false);

                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);

                    InvUtils.swap(prev, false);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasNearbyWater(BlockPos pos, int radius) {
        if (mc.level == null) return false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos check = pos.offset(x, 0, z);
                if (!mc.level.getFluidState(check).isEmpty() && mc.level.getFluidState(check).isSource()) {
                    if (mc.level.getFluidState(check).is(net.minecraft.tags.FluidTags.WATER)) return true;
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
