package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;

import java.util.*;
import java.util.function.Supplier;

public class AutoBuild extends Module {

    public enum SortAlgorithm {
        None(false, (a, b) -> 0),
        TopDown(true, Comparator.comparingInt(value -> value.getY() * -1)),
        BottomUp(true, Comparator.comparingInt(BlockPos::getY)),
        Nearest(false, (a, b) -> {
            var player = MeteorClient.mc.player;
            if (player == null) return 0;
            double distA = Utils.squaredDistance(player.getX(), player.getY(), player.getZ(), a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
            double distB = Utils.squaredDistance(player.getX(), player.getY(), player.getZ(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
            return Double.compare(distA, distB);
        }),
        Furthest(false, (a, b) -> {
            var player = MeteorClient.mc.player;
            if (player == null) return 0;
            double distA = Utils.squaredDistance(player.getX(), player.getY(), player.getZ(), a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
            double distB = Utils.squaredDistance(player.getX(), player.getY(), player.getZ(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
            return Double.compare(distB, distA);
        });

        final boolean applySecondSorting;
        final Comparator<BlockPos> algorithm;

        SortAlgorithm(boolean applySecondSorting, Comparator<BlockPos> algorithm) {
            this.applySecondSorting = applySecondSorting;
            this.algorithm = algorithm;
        }
    }

    public enum SortingSecond {
        None(SortAlgorithm.None.algorithm),
        Nearest(SortAlgorithm.Nearest.algorithm),
        Furthest(SortAlgorithm.Furthest.algorithm);

        final Comparator<BlockPos> algorithm;

        SortingSecond(Comparator<BlockPos> algorithm) {
            this.algorithm = algorithm;
        }
    }

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgFilter   = settings.createGroup("Block Filter");
    private final SettingGroup sgRender   = settings.createGroup("Rendering");

    private final Setting<Integer> printingRange = sgGeneral.add(new IntSetting.Builder()
        .name("printing-range")
        .description("Block placement reach distance.")
        .defaultValue(2)
        .min(1).sliderRange(1, 6)
        .build());

    private final Setting<Integer> printingDelay = sgGeneral.add(new IntSetting.Builder()
        .name("printing-delay")
        .description("Delay between placement cycles (ticks).")
        .defaultValue(2)
        .min(0).sliderRange(0, 40)
        .build());

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per cycle.")
        .defaultValue(1)
        .min(1).sliderRange(1, 100)
        .build());

    private final Setting<Boolean> advanced = sgGeneral.add(new BoolSetting.Builder()
        .name("advanced")
        .description("Respect block rotation (slabs, stairs, directional blocks, redstone, chests, etc).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placing blocks in mid-air without a supporting neighbor.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> placeThroughWall = sgGeneral.add(new BoolSetting.Builder()
        .name("place-through-wall")
        .description("Allow placement through solid blocks (no line-of-sight check).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing arm when placing.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> returnSlot = sgGeneral.add(new BoolSetting.Builder()
        .name("return-slot")
        .description("Return to previous hotbar slot after placement.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate player to face the correct direction for placement.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> clientSideRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("client-side-rotation")
        .description("Rotate on client side only (less detectable).")
        .defaultValue(false)
        .visible(rotate::get)
        .build());

    private final Setting<Boolean> strictRotationSync = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-rotation-sync")
        .description("Sends an explicit LookAndOnGround packet before placement.")
        .defaultValue(true)
        .visible(rotate::get)
        .build());

    private final Setting<Boolean> inventoryResync = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-resync")
        .description("Sends a swap offhand action packet after successful placements to mitigate ghost blocks.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> dirtAsGrass = sgGeneral.add(new BoolSetting.Builder()
        .name("dirt-as-grass")
        .description("Use dirt blocks in place of grass blocks.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("use-offhand")
        .description("Move items to offhand for placement.")
        .defaultValue(false)
        .build());

    private final Setting<SortAlgorithm> firstSort = sgGeneral.add(new EnumSetting.Builder<SortAlgorithm>()
        .name("first-sort")
        .description("Primary sorting for block placement order.")
        .defaultValue(SortAlgorithm.None)
        .build());

    private final Setting<SortingSecond> secondSort = sgGeneral.add(new EnumSetting.Builder<SortingSecond>()
        .name("second-sort")
        .description("Secondary sorting pass.")
        .defaultValue(SortingSecond.None)
        .visible(() -> firstSort.get().applySecondSorting)
        .build());

    private final Setting<Boolean> whitelistEnabled = sgFilter.add(new BoolSetting.Builder()
        .name("whitelist-enabled")
        .description("Only place blocks in the whitelist.")
        .defaultValue(false)
        .build());

    private final Setting<List<Block>> whitelist = sgFilter.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks allowed to be placed.")
        .visible(whitelistEnabled::get)
        .build());

    private final Setting<Boolean> blacklistEnabled = sgFilter.add(new BoolSetting.Builder()
        .name("blacklist-enabled")
        .description("Skip blocks in the blacklist.")
        .defaultValue(false)
        .build());

    private final Setting<List<Block>> blacklist = sgFilter.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("Blocks to skip.")
        .visible(blacklistEnabled::get)
        .build());

    private final Setting<Boolean> renderBlocks = sgRender.add(new BoolSetting.Builder()
        .name("render-placed-blocks")
        .description("Show a fade effect on placed blocks.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> fadeTime = sgRender.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Ticks for the placement fade effect.")
        .defaultValue(3)
        .min(1).sliderRange(1, 20)
        .visible(renderBlocks::get)
        .build());

    private final Setting<SettingColor> renderColor = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("Color for the placement effect.")
        .defaultValue(new SettingColor(95, 190, 255))
        .visible(renderBlocks::get)
        .build());

    private int timer;
    private int debugTimer;
    private int usedSlot = -1;
    private final List<BlockPos> toSort = new ArrayList<>();
    private final List<int[]> placedFade = new ArrayList<>();

    public AutoBuild() {
        super(Orbiter.CATEGORY, "auto-build",
            "Litematica Printer: automatically places blocks from loaded schematics with full rotation support. Requires Litematica.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        debugTimer = 0;
        usedSlot = -1;
        placedFade.clear();

        try {
            Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
        } catch (ClassNotFoundException e) {
            error("Litematica is NOT installed! This module requires Litematica to function.");
            toggle();
            return;
        }

        info("AutoBuild started. Make sure a Litematica schematic is loaded and placed.");
    }

    @Override
    public void onDeactivate() {
        placedFade.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            placedFade.clear();
            return;
        }

        placedFade.forEach(s -> s[0]--);
        placedFade.removeIf(s -> s[0] <= 0);

        Object schematicWorld = getSchematicWorld();
        if (schematicWorld == null) {
            placedFade.clear();
            toggle();
            return;
        }

        toSort.clear();

        if (timer >= printingDelay.get()) {
            int range = printingRange.get();

            BlockIterator.register(range + 1, range + 1, (pos, blockState) -> {
                if (mc.player == null || mc.level == null) return;
                BlockState required = getSchematicBlockState(schematicWorld, pos);
                if (required == null) return;

                if (!mc.player.blockPosition().closerThan(pos, range)) return;
                if (!blockState.canBeReplaced()) return;
                if (!required.getFluidState().isEmpty()) return;
                if (required.isAir()) return;
                if (blockState.getBlock() == required.getBlock()) return;
                if (!isInRenderRange(pos)) return;
                if (mc.player.getBoundingBox().intersects(
                    new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1))) return;
                if (!canSurviveAt(required, pos)) return;

                if (!isBlockAllowed(required.getBlock())) return;

                Item requiredItem = required.getBlock().asItem();
                if (requiredItem == Items.AIR) return;

                boolean isBlockInLineOfSight = isBlockInLineOfSight(pos, required);
                SlabType wantedSlabType = advanced.get() && required.hasProperty(BlockStateProperties.SLAB_TYPE)
                    ? required.getValue(BlockStateProperties.SLAB_TYPE) : null;
                Half wantedHalf = advanced.get() && required.hasProperty(BlockStateProperties.HALF)
                    ? required.getValue(BlockStateProperties.HALF) : null;
                Direction wantedHorizontalOrientation = advanced.get() && required.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? required.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
                Direction.Axis wantedAxis = advanced.get() && required.hasProperty(BlockStateProperties.AXIS)
                    ? required.getValue(BlockStateProperties.AXIS) : null;
                Direction wantedHopperOrientation = advanced.get() && required.hasProperty(BlockStateProperties.FACING_HOPPER)
                    ? required.getValue(BlockStateProperties.FACING_HOPPER) : null;

                Direction effectiveHorizontal = wantedHorizontalOrientation != null
                    ? wantedHorizontalOrientation : wantedHopperOrientation;

                Direction strictSide = placeThroughWall.get()
                    ? getPlaceSide(pos, required, wantedSlabType, wantedHalf, effectiveHorizontal, wantedAxis, advanced.get() ? dir(required) : null)
                    : getVisiblePlaceSide(pos, required, wantedSlabType, wantedHalf, effectiveHorizontal, wantedAxis, range, advanced.get() ? dir(required) : null);

                boolean canPlace = strictSide != null;
                if (!canPlace && airPlace.get()) {

                    canPlace = isBlockInLineOfSight;
                }

                if (canPlace) {
                    toSort.add(new BlockPos(pos));
                }
            });

            BlockIterator.after(() -> {
                if (!toSort.isEmpty() && debugTimer++ % 40 == 0) {
                    info("Found " + toSort.size() + " blocks to place.");
                }

                if (firstSort.get() != SortAlgorithm.None) {
                    if (firstSort.get().applySecondSorting && secondSort.get() != SortingSecond.None) {
                        toSort.sort(secondSort.get().algorithm);
                    }
                    toSort.sort(firstSort.get().algorithm);
                }

                int placed = 0;
                for (BlockPos pos : toSort) {
                    BlockState state = getSchematicBlockState(schematicWorld, pos);
                    if (state == null) continue;

                    Item item = state.getBlock().asItem();
                    if (dirtAsGrass.get() && item == Items.GRASS_BLOCK) item = Items.DIRT;
                    if (item == Items.AIR) continue;

                    boolean success;
                    if (useOffhand.get()) {
                        success = switchItemOffhand(item, () -> place(state, pos));
                    } else {
                        success = switchItemMainhand(item, () -> place(state, pos));
                    }

                    if (success) {
                        timer = 0;
                        placed++;
                        if (renderBlocks.get()) {
                            placedFade.add(new int[]{fadeTime.get(), pos.getX(), pos.getY(), pos.getZ()});
                        }
                        if (placed >= blocksPerTick.get()) return;
                    }
                }
            });
        } else {
            timer++;
        }
    }

    private boolean place(BlockState required, BlockPos pos) {
        if (mc.player == null || mc.level == null) return false;
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;

        Direction wantedSide = advanced.get() ? dir(required) : null;
        SlabType wantedSlabType = advanced.get() && required.hasProperty(BlockStateProperties.SLAB_TYPE)
            ? required.getValue(BlockStateProperties.SLAB_TYPE) : null;
        Half wantedHalf = advanced.get() && required.hasProperty(BlockStateProperties.HALF)
            ? required.getValue(BlockStateProperties.HALF) : null;
        Direction wantedHorizontalOrientation = advanced.get() && required.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? required.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
        Direction.Axis wantedAxis = advanced.get() && required.hasProperty(BlockStateProperties.AXIS)
            ? required.getValue(BlockStateProperties.AXIS) : null;
        Direction wantedHopperOrientation = advanced.get() && required.hasProperty(BlockStateProperties.FACING_HOPPER)
            ? required.getValue(BlockStateProperties.FACING_HOPPER) : null;

        Direction effectiveHorizontal = wantedHorizontalOrientation != null
            ? wantedHorizontalOrientation : wantedHopperOrientation;

        Direction placeSide;
        if (airPlace.get() && placeThroughWall.get()) {

            placeSide = getPlaceSide(pos, required, wantedSlabType, wantedHalf, effectiveHorizontal, wantedAxis, wantedSide);
        } else if (placeThroughWall.get()) {
            placeSide = getPlaceSide(pos, required, wantedSlabType, wantedHalf, effectiveHorizontal, wantedAxis, wantedSide);
        } else {
            placeSide = getVisiblePlaceSide(pos, required, wantedSlabType, wantedHalf, effectiveHorizontal, wantedAxis,
                printingRange.get(), wantedSide);
        }

        return doPlace(pos, placeSide, wantedSlabType, wantedHalf, effectiveHorizontal, wantedAxis);
    }

    private boolean doPlace(BlockPos blockPos, Direction direction, SlabType slabType,
                            Half blockHalf, Direction horizontalFacing, Direction.Axis wantedAxis) {
        if (mc.player == null || mc.level == null) return false;

        if (!mc.level.getBlockState(blockPos).canBeReplaced()) return false;

        Vec3 hitPos = Vec3.atCenterOf(blockPos);
        BlockPos neighbor;

        if (direction == null) {

            if (!airPlace.get()) return false;
            direction = Direction.UP;
            neighbor = blockPos;
        } else {
            neighbor = blockPos.relative(direction.getOpposite());
            hitPos = hitPos.add(
                direction.getStepX() * 0.5,
                direction.getStepY() * 0.5,
                direction.getStepZ() * 0.5);
        }

        Direction finalDir = direction;
        InteractionHand hand = useOffhand.get() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (rotate.get()) {
            if (mc.level != null) {
                BlockState neighborState = mc.level.getBlockState(neighbor);
                var collisionShape = neighborState.getCollisionShape(mc.level, neighbor);

                if (collisionShape.isEmpty()) {

                    final Vec3 fHitPos = hitPos;
                    Rotations.rotate(Rotations.getYaw(fHitPos), Rotations.getPitch(fHitPos),
                        50, clientSideRotation.get(),
                        () -> executePlaceWithSneak(new BlockHitResult(fHitPos, finalDir, neighbor, false), hand));
                    return true;
                }

                AABB aabb = collisionShape.bounds();

                for (double z = 0.1; z < 0.9; z += 0.2) {
                    for (double x = 0.1; x < 0.9; x += 0.2) {
                        Vec3[] multipliers = aabbSideMultipliers(finalDir.getOpposite());
                        for (Vec3 placementMultiplier : multipliers) {

                            double placeX = neighbor.getX() + aabb.minX * x + aabb.maxX * (1 - x);

                            if ((slabType != null && slabType != SlabType.DOUBLE
                                || blockHalf != null && finalDir != Direction.UP && finalDir != Direction.DOWN)
                                && !mc.player.isCreative()) {
                                if (slabType == SlabType.BOTTOM || blockHalf == Half.BOTTOM) {
                                    if (placementMultiplier.y <= 0.5) continue;
                                } else {
                                    if (placementMultiplier.y > 0.5) continue;
                                }
                            }

                            double placeY = neighbor.getY() + aabb.minY * placementMultiplier.y + aabb.maxY * (1 - placementMultiplier.y);
                            double placeZ = neighbor.getZ() + aabb.minZ * z + aabb.maxZ * (1 - z);

                            Vec3 testHitPos = new Vec3(placeX, placeY, placeZ);
                            Vec3 playerHead = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());

                            float[] rot = calcRotation(playerHead, testHitPos);
                            Direction testHorizontalDirection = getHorizontalDirectionFromYaw(rot[0]);

                            if (horizontalFacing != null
                                && testHorizontalDirection.getAxis() != horizontalFacing.getAxis()) continue;

                            HitResult res = rayTraceTowards(rot, printingRange.get());
                            if (res == null || res.getType() != HitResult.Type.BLOCK) continue;
                            BlockHitResult blockHitRes = (BlockHitResult) res;
                            if (!blockHitRes.getBlockPos().equals(neighbor)) continue;
                            if (blockHitRes.getDirection() != finalDir) continue;

                            Rotations.rotate(Rotations.getYaw(testHitPos), Rotations.getPitch(testHitPos),
                                50, clientSideRotation.get(),
                                () -> executePlaceWithSneak(new BlockHitResult(testHitPos, finalDir, neighbor, false), hand));
                            return true;
                        }
                    }
                }
            }

            final Vec3 fallbackHitPos = hitPos;
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos),
                50, clientSideRotation.get(),
                () -> executePlaceWithSneak(new BlockHitResult(fallbackHitPos, finalDir, neighbor, false), hand));
        } else {
            return executePlaceWithSneak(new BlockHitResult(hitPos, finalDir, neighbor, false), hand);
        }

        return true;
    }

    private boolean executePlaceWithSneak(BlockHitResult hitResult, InteractionHand hand) {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return false;

        if (strictRotationSync.get()) {
            Vec3 eye = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
            float[] rot = calcRotation(eye, hitResult.getLocation());
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                rot[0],
                rot[1],
                mc.player.onGround(),
                mc.player.horizontalCollision
            ));
        }

        boolean wasSneaking = mc.player.isShiftKeyDown();
        mc.player.setShiftKeyDown(true);

        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hitResult);

        if (result.consumesAction()) {
            if (swing.get()) mc.player.swing(hand);
            else mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSwingPacket(hand));

            if (inventoryResync.get()) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO,
                    Direction.DOWN
                ));
            }
        }

        mc.player.setShiftKeyDown(wasSneaking);
        return result.consumesAction();
    }

    private Direction dir(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) return state.getValue(BlockStateProperties.FACING);
        if (state.hasProperty(BlockStateProperties.AXIS))
            return Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
            return Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
        return Direction.UP;
    }

    private boolean isBlockSameAsPlaceDir(Block block) {
        return block instanceof HopperBlock;
    }

    private boolean isBlockPlacementOpposite(Block block) {
        return block instanceof AmethystClusterBlock
            || block instanceof EndRodBlock
            || block instanceof LightningRodBlock
            || block instanceof TrapDoorBlock
            || block instanceof ChainBlock
            || block instanceof RotatedPillarBlock;
    }

    private boolean isBlockLikeButton(Block block) {
        return block instanceof ButtonBlock
            || block instanceof BellBlock
            || block instanceof GrindstoneBlock
            || block instanceof TrapDoorBlock;
    }

    private boolean isFaceDesired(Block block, Direction blockHorizontalOrientation, Direction against) {
        return blockHorizontalOrientation == null
            || (!(isBlockSameAsPlaceDir(block) || isBlockPlacementOpposite(block)))
            || (isBlockSameAsPlaceDir(block) && blockHorizontalOrientation == against)
            || (block instanceof TrapDoorBlock && against.getOpposite() == blockHorizontalOrientation)
            || (!(block instanceof TrapDoorBlock) && (
                (isBlockPlacementOpposite(block) && blockHorizontalOrientation == against.getOpposite())
                || (isBlockLikeButton(block) && against != Direction.UP && against != Direction.DOWN
                    && blockHorizontalOrientation == against)
            ));
    }

    private boolean isPlayerOrientationDesired(Block block, Direction blockHorizontalOrientation, Direction playerOrientation) {
        return blockHorizontalOrientation == null
            || (block instanceof StairBlock && playerOrientation == blockHorizontalOrientation)
            || (!(block instanceof StairBlock)
                && !isBlockPlacementOpposite(block) && !isBlockSameAsPlaceDir(block)
                && playerOrientation == blockHorizontalOrientation.getOpposite());
    }

    private Direction getPlaceSide(BlockPos blockPos, BlockState placeState, SlabType slabType,
                                   Half blockHalf, Direction blockHorizontalOrientation,
                                   Direction.Axis wantedAxis, Direction requiredDir) {
        if (mc.level == null || mc.player == null) return null;
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.relative(side);
            Direction side2 = side.getOpposite();

            if (wantedAxis != null && side.getAxis() != wantedAxis) continue;
            if (blockHalf != null) {
                if (side == Direction.UP && blockHalf == Half.BOTTOM) continue;
                if (side == Direction.DOWN && blockHalf == Half.TOP) continue;
            }

            if ((slabType != null && slabType != SlabType.DOUBLE || blockHalf != null)
                && !mc.player.isCreative()) {
                if (slabType == SlabType.BOTTOM || blockHalf == Half.BOTTOM) {
                    if (side2 == Direction.DOWN) continue;
                } else {
                    if (side2 == Direction.UP) continue;
                }
            }

            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (wantedAxis == null && !isFaceDesired(placeState.getBlock(), blockHorizontalOrientation, side)) continue;
            if (wantedAxis != null && wantedAxis != side.getAxis()) continue;

            if (neighborState.isAir() || BlockUtils.isClickable(neighborState.getBlock())) continue;
            if (!neighborState.getFluidState().isEmpty()) continue;

            if (neighborState.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                SlabType nst = neighborState.getValue(BlockStateProperties.SLAB_TYPE);
                if (nst == SlabType.DOUBLE) continue;
                if (side == Direction.UP && nst == SlabType.TOP) continue;
                if (side == Direction.DOWN && nst == SlabType.BOTTOM) continue;
            }

            Vec3 hitPos = Vec3.atCenterOf(neighbor);
            Vec3 playerHead = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
            float[] rot = calcRotation(playerHead, hitPos);
            Direction testHorizontalDirection = getHorizontalDirectionFromYaw(rot[0]);

            if (placeState.getBlock() instanceof TrapDoorBlock
                && !(side != Direction.DOWN && side != Direction.UP)
                && !isPlayerOrientationDesired(placeState.getBlock(), blockHorizontalOrientation, testHorizontalDirection))
                continue;
            if (!(placeState.getBlock() instanceof TrapDoorBlock)
                && !isPlayerOrientationDesired(placeState.getBlock(), blockHorizontalOrientation, testHorizontalDirection))
                continue;

            return side2;
        }
        return null;
    }

    private Direction getVisiblePlaceSide(BlockPos placeAt, BlockState placeAtState, SlabType slabType,
                                          Half blockHalf, Direction blockHorizontalOrientation,
                                          Direction.Axis wantedAxis, int range, Direction requiredDir) {
        if (mc.level == null || mc.player == null) return null;

        for (Direction against : Direction.values()) {
            if (wantedAxis != null && against.getAxis() != wantedAxis) continue;
            if (blockHalf != null) {
                if (against == Direction.UP && blockHalf == Half.BOTTOM) continue;
                if (against == Direction.DOWN && blockHalf == Half.TOP) continue;
            }

            if ((slabType != null && slabType != SlabType.DOUBLE) && !mc.player.isCreative()) {
                if (slabType == SlabType.BOTTOM && against == Direction.DOWN) continue;
                if (slabType == SlabType.TOP && against == Direction.UP) continue;
            }

            if (wantedAxis == null && !isFaceDesired(placeAtState.getBlock(), blockHorizontalOrientation, against))
                continue;
            if (wantedAxis != null && wantedAxis != against.getAxis()) continue;

            BlockPos neighborPos = placeAt.relative(against);
            BlockState neighborState = mc.level.getBlockState(neighborPos);

            if (!canPlaceAgainst(placeAtState, neighborState, against)) continue;
            if (BlockUtils.isClickable(neighborState.getBlock())) continue;

            var collisionShape = neighborState.getCollisionShape(mc.level, neighborPos);
            if (collisionShape.isEmpty()) continue;
            AABB aabb = collisionShape.bounds();

            for (double z = 0.1; z < 0.9; z += 0.2) {
                for (double x = 0.1; x < 0.9; x += 0.2) {
                    Vec3[] multipliers = aabbSideMultipliers(against);
                    for (Vec3 placementMultiplier : multipliers) {

                        double placeX = placeAt.getX() + aabb.minX * x + aabb.maxX * (1 - x);

                        if ((slabType != null && slabType != SlabType.DOUBLE
                            || blockHalf != null && against != Direction.DOWN && against != Direction.UP)
                            && !mc.player.isCreative()) {
                            if (slabType == SlabType.BOTTOM || blockHalf == Half.BOTTOM) {
                                if (placementMultiplier.y <= 0.5) continue;
                            } else {
                                if (placementMultiplier.y > 0.5) continue;
                            }
                        }

                        double placeY = placeAt.getY() + aabb.minY * placementMultiplier.y + aabb.maxY * (1 - placementMultiplier.y);
                        double placeZ = placeAt.getZ() + aabb.minZ * z + aabb.maxZ * (1 - z);

                        Vec3 testHitPos = new Vec3(placeX, placeY, placeZ);
                        Vec3 playerHead = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
                        float[] rot = calcRotation(playerHead, testHitPos);

                        Direction testHorizontalDirection = getHorizontalDirectionFromYaw(rot[0]);

                        if (placeAtState.getBlock() instanceof TrapDoorBlock
                            && !(against != Direction.DOWN && against != Direction.UP)
                            && !isPlayerOrientationDesired(placeAtState.getBlock(), blockHorizontalOrientation, testHorizontalDirection))
                            continue;
                        if (!(placeAtState.getBlock() instanceof TrapDoorBlock)
                            && !isPlayerOrientationDesired(placeAtState.getBlock(), blockHorizontalOrientation, testHorizontalDirection))
                            continue;

                        HitResult res = rayTraceTowards(rot, range);
                        if (res == null || res.getType() != HitResult.Type.BLOCK) continue;
                        BlockHitResult blockHitRes = (BlockHitResult) res;
                        if (!blockHitRes.getBlockPos().equals(placeAt)) continue;
                        if (blockHitRes.getDirection() != against.getOpposite()) continue;

                        return against.getOpposite();
                    }
                }
            }
        }
        return null;
    }

    private boolean isBlockNormalCube(BlockState state) {
        if (mc.level == null) return false;
        Block block = state.getBlock();
        if (block instanceof ScaffoldingBlock || block instanceof ShulkerBoxBlock
            || block instanceof PointedDripstoneBlock || block instanceof AmethystClusterBlock) {
            return false;
        }
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(mc.level, BlockPos.ZERO))
                || state.getBlock() instanceof StairBlock;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean canPlaceAgainst(BlockState placeAtState, BlockState placeAgainstState, Direction against) {
        return isBlockNormalCube(placeAgainstState)
            || placeAgainstState.getBlock() == Blocks.GLASS
            || placeAgainstState.getBlock() instanceof StainedGlassBlock
            || placeAgainstState.getBlock() instanceof StairBlock
            || (placeAgainstState.getBlock() instanceof SlabBlock
                && (placeAgainstState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                    && placeAtState.getBlock() == placeAgainstState.getBlock()
                    && against != Direction.DOWN
                    || placeAtState.getBlock() != placeAgainstState.getBlock()));
    }

    private boolean isBlockInLineOfSight(BlockPos placeAt, BlockState placeAtState) {
        if (mc.player == null || mc.level == null) return false;
        Vec3 playerHead = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Vec3 placeAtVec = Vec3.atCenterOf(placeAt);

        ClipContext context = new ClipContext(
            playerHead, placeAtVec,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player);
        BlockHitResult bhr = mc.level.clip(context);
        return bhr.getType() == HitResult.Type.MISS;
    }

    private boolean canSurviveAt(BlockState state, BlockPos pos) {
        if (mc.level == null) return true;
        try {
            return state.canSurvive(mc.level, pos);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isInRenderRange(BlockPos pos) {
        try {
            Class<?> dataManagerClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object layerRange = dataManagerClass.getMethod("getRenderLayerRange").invoke(null);
            return (boolean) layerRange.getClass().getMethod("isPositionWithinRange", BlockPos.class)
                .invoke(layerRange, pos);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isBlockAllowed(Block block) {
        if (whitelistEnabled.get() && whitelist.get() != null && !whitelist.get().isEmpty()) {
            if (!whitelist.get().contains(block)) return false;
        }
        if (blacklistEnabled.get() && blacklist.get() != null && !blacklist.get().isEmpty()) {
            if (blacklist.get().contains(block)) return false;
        }
        return true;
    }

    private Vec3[] aabbSideMultipliers(Direction side) {
        return switch (side) {
            case UP -> new Vec3[]{
                new Vec3(0.5, 1, 0.5), new Vec3(0.1, 1, 0.5),
                new Vec3(0.9, 1, 0.5), new Vec3(0.5, 1, 0.1), new Vec3(0.5, 1, 0.9)};
            case DOWN -> new Vec3[]{
                new Vec3(0.5, 0, 0.5), new Vec3(0.1, 0, 0.5),
                new Vec3(0.9, 0, 0.5), new Vec3(0.5, 0, 0.1), new Vec3(0.5, 0, 0.9)};
            default -> {
                double xm = side.getStepX() == 0 ? 0.5 : (1 + side.getStepX()) / 2.0;
                double zm = side.getStepZ() == 0 ? 0.5 : (1 + side.getStepZ()) / 2.0;
                yield new Vec3[]{new Vec3(xm, 0.25, zm), new Vec3(xm, 0.75, zm)};
            }
        };
    }

    private float[] calcRotation(Vec3 from, Vec3 to) {
        double dx = from.x - to.x;
        double dy = from.y - to.y;
        double dz = from.z - to.z;
        double yaw = Math.atan2(dx, -dz);
        double dist = Math.sqrt(dx * dx + dz * dz);
        double pitch = Math.atan2(dy, dist);
        return new float[]{(float) Math.toDegrees(yaw), (float) Math.toDegrees(pitch)};
    }

    private HitResult rayTraceTowards(float[] rotation, double blockReachDistance) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 start = mc.player.getEyePosition();

        float yawRad = (float) ((-rotation[0]) * Math.PI / 180.0 - Math.PI);
        float pitchRad = (float) (-rotation[1] * Math.PI / 180.0);

        float flatZ = (float) Math.cos(yawRad);
        float flatX = (float) Math.sin(yawRad);
        float pitchBase = (float) -Math.cos(pitchRad);
        float pitchHeight = (float) Math.sin(pitchRad);

        Vec3 direction = new Vec3(flatX * pitchBase, pitchHeight, flatZ * pitchBase);
        Vec3 end = start.add(
            direction.x * blockReachDistance, direction.y * blockReachDistance, direction.z * blockReachDistance);

        return mc.level.clip(new ClipContext(
            start, end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            mc.player));
    }

    private static Direction getHorizontalDirectionFromYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw < 0) yaw += 360.0F;

        if ((yaw >= 45 && yaw < 135) || (yaw >= -315 && yaw < -225)) return Direction.WEST;
        if ((yaw >= 135 && yaw < 225) || (yaw >= -225 && yaw < -135)) return Direction.NORTH;
        if ((yaw >= 225 && yaw < 315) || (yaw >= -135 && yaw < -45)) return Direction.EAST;
        return Direction.SOUTH;
    }

    private Object getSchematicWorld() {
        try {
            Class<?> handlerClass = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            return handlerClass.getMethod("getSchematicWorld").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    private BlockState getSchematicBlockState(Object schematicWorld, BlockPos pos) {
        try {
            return (BlockState) schematicWorld.getClass()
                .getMethod("getBlockState", BlockPos.class).invoke(schematicWorld, pos);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean switchItemMainhand(Item item, Supplier<Boolean> action) {
        if (mc.player == null) return false;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();

        if (mc.player.getMainHandItem().getItem() == item) {
            if (action.get()) { usedSlot = mc.player.getInventory().getSelectedSlot(); return true; }
            return false;
        }

        if (usedSlot != -1 && mc.player.getInventory().getItem(usedSlot).getItem() == item) {
            InvUtils.swap(usedSlot, returnSlot.get());
            if (action.get()) return true;
            InvUtils.swap(selectedSlot, returnSlot.get());
            return false;
        }

        FindItemResult result = InvUtils.find(item);
        if (result.found()) {
            if (result.isHotbar()) {
                InvUtils.swap(result.slot(), returnSlot.get());
                if (action.get()) { usedSlot = mc.player.getInventory().getSelectedSlot(); return true; }
                InvUtils.swap(selectedSlot, returnSlot.get());
                return false;
            } else if (result.isMain()) {
                FindItemResult empty = InvUtils.findEmpty();
                if (empty.found() && empty.isHotbar()) {
                    InvUtils.move().from(result.slot()).toHotbar(empty.slot());
                    InvUtils.swap(empty.slot(), returnSlot.get());
                    if (action.get()) { usedSlot = mc.player.getInventory().getSelectedSlot(); return true; }
                    InvUtils.swap(selectedSlot, returnSlot.get());
                    return false;
                } else if (usedSlot != -1) {
                    InvUtils.move().from(result.slot()).toHotbar(usedSlot);
                    InvUtils.swap(usedSlot, returnSlot.get());
                    if (action.get()) return true;
                    InvUtils.swap(selectedSlot, returnSlot.get());
                    return false;
                }
            }
        }

        if (mc.player.isCreative()) {
            if (mc.getConnection() == null) return false;
            int slot = 0;
            FindItemResult fir = InvUtils.find(ItemStack::isEmpty, 0, 8);
            if (fir.found()) slot = fir.slot();
            mc.getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(
                    36 + slot, item.getDefaultInstance()));
            InvUtils.swap(slot, returnSlot.get());
            if (action.get()) {
                usedSlot = mc.player.getInventory().getSelectedSlot();
                return true;
            }
            return false;
        }

        return false;
    }

    private boolean switchItemOffhand(Item item, Supplier<Boolean> action) {
        if (mc.player == null) return false;

        if (mc.player.getOffhandItem().getItem() == item) {
            return action.get();
        }

        FindItemResult result = InvUtils.find(item);
        if (!result.found()) return false;

        InvUtils.move().from(result.slot()).toOffhand();
        return action.get();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        Color col = renderColor.get();
        for (int[] s : placedFade) {
            float alpha = (float) s[0] / (float) fadeTime.get();
            Color c = new Color(col.r, col.g, col.b, (int) (alpha * col.a));
            event.renderer.box(
                s[1], s[2], s[3], s[1] + 1, s[2] + 1, s[3] + 1,
                c, null, ShapeMode.Sides, 0);
        }
    }
}
