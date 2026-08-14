package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class AutoClutch extends Module {
    public enum PlaceMode {
        LookVector,
        BelowFeet
    }

    public enum ClutchMode {
        Blocks,
        Boats,
        Water,
        AnyCanceler,
        All
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Block Filter");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<ClutchMode> mode = sgGeneral.add(new EnumSetting.Builder<ClutchMode>()
        .name("clutch-mode")
        .description("What to use to break your fall.")
        .defaultValue(ClutchMode.All)
        .build());

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switch to the required item.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to look downward before placing.")
        .defaultValue(true)
        .build());

    private final Setting<PlaceMode> placeMode = sgGeneral.add(new EnumSetting.Builder<PlaceMode>()
        .name("place-mode")
        .description("LookVector places where camera is looking, BelowFeet places directly down at player block.")
        .defaultValue(PlaceMode.BelowFeet)
        .build());

    private final Setting<Boolean> survivalOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("survival-only")
        .description("Disables clutch logic while in creative/spectator.")
        .defaultValue(true)
        .build());

    private final Setting<ListMode> listMode = sgBlocks.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode")
        .description("Whitelist = only use these blocks. Blacklist = use any block EXCEPT these.")
        .defaultValue(ListMode.Blacklist)
        .visible(() -> mode.get() == ClutchMode.Blocks || mode.get() == ClutchMode.All || mode.get() == ClutchMode.AnyCanceler)
        .build());

    private final Setting<List<Block>> blockList = sgBlocks.add(new BlockListSetting.Builder()
        .name("block-list")
        .description("Blocks for the whitelist/blacklist filter.")
        .visible(() -> mode.get() == ClutchMode.Blocks || mode.get() == ClutchMode.All || mode.get() == ClutchMode.AnyCanceler)
        .build());

    private final Setting<Double> activationHeight = sgTiming.add(new DoubleSetting.Builder()
        .name("activation-height")
        .description("Distance from ground to start the clutch attempt.")
        .defaultValue(3.0)
        .min(1.0).sliderRange(1.0, 20.0)
        .build());

    private final Setting<Double> minFallDistance = sgTiming.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance before clutch activates.")
        .defaultValue(3.0)
        .min(1.0).sliderRange(1.0, 50.0)
        .build());

    private final Setting<Boolean> onlyWhenFalling = sgSafety.add(new BoolSetting.Builder()
        .name("only-when-falling")
        .description("Only clutch when velocity is downward.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> disableOnSuccess = sgSafety.add(new BoolSetting.Builder()
        .name("disable-on-success")
        .description("Disable module after a successful clutch.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> notifyOnClutch = sgSafety.add(new BoolSetting.Builder()
        .name("notify-on-clutch")
        .description("Send a chat notification when a clutch is performed.")
        .defaultValue(true)
        .build());

    private boolean clutched = false;
    private int prevSlot = -1;

    public AutoClutch() {
        super(Orbiter.CATEGORY_VANILLA, "auto-clutch",
            "Automatically clutches to prevent fall damage using blocks, boats, water, or any fall-canceling item.");
    }

    @Override
    public void onActivate() {
        clutched = false;
        prevSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (survivalOnly.get() && (mc.player.isCreative() || mc.player.isSpectator())) {
            restoreSlot();
            clutched = false;
            return;
        }

        if (onlyWhenFalling.get() && mc.player.getVelocity().y >= 0) {
            restoreSlot();
            clutched = false;
            return;
        }

        if (mc.player.fallDistance < minFallDistance.get()) return;

        if (mc.player.isOnGround()) {
            restoreSlot();
            clutched = false;
            return;
        }

        double groundDist = distanceToGround();
        if (groundDist > activationHeight.get()) return;

        if (clutched) return;

        if (attemptClutch()) {
            clutched = true;
            if (notifyOnClutch.get()) info("Clutch performed!");
            if (disableOnSuccess.get()) toggle();
        }
    }

    private void restoreSlot() {
        if (prevSlot >= 0) {
            InvUtils.swap(prevSlot, false);
            prevSlot = -1;
        }
    }

    private boolean attemptClutch() {
        ClutchMode m = mode.get();

        if (m == ClutchMode.Water || m == ClutchMode.All || m == ClutchMode.AnyCanceler) {
            if (tryWaterBucket(Items.WATER_BUCKET)) return true;
            if (tryWaterBucket(Items.POWDER_SNOW_BUCKET)) return true;
        }

        if (m == ClutchMode.Blocks || m == ClutchMode.All || m == ClutchMode.AnyCanceler) {
            if (tryPlaceBlock()) return true;
        }

        if (m == ClutchMode.Boats || m == ClutchMode.All || m == ClutchMode.AnyCanceler) {
            if (tryPlaceBoat()) return true;
        }

        if (m == ClutchMode.AnyCanceler || m == ClutchMode.All) {

            if (tryPlaceSpecificBlock(Items.SLIME_BLOCK)) return true;
            if (tryPlaceSpecificBlock(Items.HAY_BLOCK)) return true;
            if (tryPlaceSpecificBlock(Items.HONEY_BLOCK)) return true;
        }

        return false;
    }

    private boolean findAndSwitch(Item item) {
        if (mc.player == null) return false;

        if (mc.player.getMainHandStack().getItem() == item) return true;

        FindItemResult hotbarResult = InvUtils.findInHotbar(item);
        if (hotbarResult.found()) {
            if (autoSwitch.get()) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(hotbarResult.slot(), false);
                return true;
            }
            return false;
        }

        FindItemResult invResult = InvUtils.find(item);
        if (invResult.found() && autoSwitch.get()) {

            FindItemResult emptySlot = InvUtils.find(ItemStack::isEmpty, 0, 8);
            int targetSlot;
            if (emptySlot.found()) {
                targetSlot = emptySlot.slot();
            } else {

                targetSlot = mc.player.getInventory().getSelectedSlot();
            }

            prevSlot = mc.player.getInventory().getSelectedSlot();
            InvUtils.move().from(invResult.slot()).toHotbar(targetSlot);
            InvUtils.swap(targetSlot, false);
            return true;
        }

        return false;
    }

    private boolean tryWaterBucket(Item bucketItem) {
        if (!findAndSwitch(bucketItem)) return false;

        if (rotate.get()) mc.player.setPitch(90.0f);

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        return true;
    }

    private boolean tryPlaceBoat() {
        Item[] boats = {
            Items.OAK_BOAT, Items.SPRUCE_BOAT, Items.BIRCH_BOAT,
            Items.JUNGLE_BOAT, Items.ACACIA_BOAT, Items.DARK_OAK_BOAT,
            Items.CHERRY_BOAT, Items.MANGROVE_BOAT, Items.BAMBOO_RAFT
        };

        for (Item boat : boats) {
            if (!findAndSwitch(boat)) continue;

            if (rotate.get()) mc.player.setPitch(90.0f);

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private boolean tryPlaceSpecificBlock(Item item) {
        if (!findAndSwitch(item)) return false;
        return placeBlockBelow();
    }

    private boolean tryPlaceBlock() {

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;

            Block block = blockItem.getBlock();
            if (!isBlockAllowed(block)) continue;
            if (isDangerousBlock(block)) continue;

            if (autoSwitch.get()) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(i, false);
            }

            if (placeBlockBelow()) return true;
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;

            Block block = blockItem.getBlock();
            if (!isBlockAllowed(block)) continue;
            if (isDangerousBlock(block)) continue;

            if (autoSwitch.get()) {

                FindItemResult emptySlot = InvUtils.find(ItemStack::isEmpty, 0, 8);
                int targetSlot = emptySlot.found() ? emptySlot.slot() : mc.player.getInventory().getSelectedSlot();

                prevSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.move().from(i).toHotbar(targetSlot);
                InvUtils.swap(targetSlot, false);
            } else {
                continue;
            }

            if (placeBlockBelow()) return true;
        }
        return false;
    }

    private boolean placeBlockBelow() {
        BlockPos below;
        if (placeMode.get() == PlaceMode.LookVector && mc.crosshairTarget instanceof BlockHitResult bhr) {
            below = bhr.getBlockPos();
            if (!mc.world.getBlockState(below).isReplaceable()) below = below.offset(bhr.getSide());
        } else {
            below = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.2, mc.player.getZ()).down();
        }

        if (!mc.world.getBlockState(below).isReplaceable()) return false;

        if (rotate.get()) {
            mc.player.setPitch(90.0f);
        }

        Direction[] priorities = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction dir : priorities) {
            BlockPos neighbor = below.offset(dir);
            BlockState neighborState = mc.world.getBlockState(neighbor);

            if (neighborState.isAir() || !neighborState.isSolidBlock(mc.world, neighbor)) continue;

            Direction clickFace = dir.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(neighbor).add(
                clickFace.getOffsetX() * 0.5,
                clickFace.getOffsetY() * 0.5,
                clickFace.getOffsetZ() * 0.5
            );

            BlockHitResult hit = new BlockHitResult(hitPos, clickFace, neighbor, false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (result.isAccepted()) return true;
        }

        return false;
    }

    private boolean isBlockAllowed(Block block) {
        List<Block> list = blockList.get();
        if (list == null || list.isEmpty()) return true;

        if (listMode.get() == ListMode.Whitelist) {
            return list.contains(block);
        } else {
            return !list.contains(block);
        }
    }

    private boolean isDangerousBlock(Block block) {
        return block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK || block == Blocks.FIRE ||
            block == Blocks.SOUL_FIRE || block == Blocks.LAVA || block == Blocks.CAMPFIRE ||
            block == Blocks.SOUL_CAMPFIRE || block == Blocks.WITHER_ROSE ||
            block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POINTED_DRIPSTONE ||
            block instanceof BedBlock;
    }

    private double distanceToGround() {
        if (mc.player == null || mc.world == null) return Double.MAX_VALUE;

        BlockPos.Mutable mpos = mc.player.getBlockPos().mutableCopy();
        int startY = mpos.getY();

        for (int y = startY - 1; y >= mc.world.getBottomY(); y--) {
            mpos.setY(y);
            BlockState state = mc.world.getBlockState(mpos);
            if (!state.isAir()) {
                return mc.player.getY() - y - 1;
            }
        }
        return Double.MAX_VALUE;
    }
}
