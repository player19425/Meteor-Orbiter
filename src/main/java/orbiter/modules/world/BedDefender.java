package orbiter.modules.world;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import orbiter.Orbiter;
import orbiter.compat.EntityCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BedDefender extends Module {
    public enum PlaceMode {
        FirstBedPerJoin,
        NearestBed,
        AllBeds
    }

    public enum BedFilter {
        AnyBed,
        ColorList,
        ClosestToLeather
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filtering");
    private final SettingGroup sgBlocks = settings.createGroup("Block Source");
    private final SettingGroup sgLayout = settings.createGroup("Ring");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Bed scan radius around the player.")
        .defaultValue(8.0)
        .min(3.0).max(16.0).sliderRange(3.0, 16.0)
        .build());

    private final Setting<PlaceMode> placeMode = sgGeneral.add(new EnumSetting.Builder<PlaceMode>()
        .name("place-mode")
        .description("Which beds get a defensive ring.")
        .defaultValue(PlaceMode.NearestBed)
        .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between block placements.")
        .defaultValue(4)
        .min(0).sliderRange(0, 20)
        .build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between bed scans.")
        .defaultValue(20)
        .min(1).sliderRange(1, 60)
        .build());

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switch to the placeable block.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders found beds and pending ring positions.")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> renderColor = sgGeneral.add(new ColorSetting.Builder()
        .name("render-color")
        .description("Color for bed and ring outlines.")
        .defaultValue(new SettingColor(120, 200, 255, 60))
        .build());

    private final Setting<BedFilter> bedFilter = sgFilter.add(new EnumSetting.Builder<BedFilter>()
        .name("bed-filter")
        .description("ClosestToLeather matches your worn leather armor instead.")
        .defaultValue(BedFilter.AnyBed)
        .build());

    private final Setting<String> bedColors = sgFilter.add(new StringSetting.Builder()
        .name("bed-colors")
        .description("Dye colors the ColorList filter accepts, for example red, white, blue.")
        .defaultValue("red, white")
        .visible(() -> bedFilter.get() == BedFilter.ColorList)
        .build());

    private final Setting<Boolean> skipDangerous = sgFilter.add(new BoolSetting.Builder()
        .name("skip-dangerous")
        .description("Never use blocks that damage the player.")
        .defaultValue(true)
        .build());

    private final Setting<List<Block>> preferredBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("preferred-blocks")
        .description("Blocks to place first, in order. Empty = any hotbar block.")
        .build());

    private final Setting<Boolean> allowHotbar = sgBlocks.add(new BoolSetting.Builder()
        .name("allow-hotbar")
        .description("Allow using any placeable block from the hotbar.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> allowHeld = sgBlocks.add(new BoolSetting.Builder()
        .name("allow-held")
        .description("Allow using whatever block is held in the main hand.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> blocksPerBed = sgLayout.add(new IntSetting.Builder()
        .name("blocks-per-bed")
        .description("Maximum blocks placed per bed ring.")
        .defaultValue(14)
        .min(1).max(14).sliderRange(1, 14)
        .build());

    private final Setting<Integer> ringRadius = sgLayout.add(new IntSetting.Builder()
        .name("ring-radius")
        .description("Horizontal ring radius around each bed half.")
        .defaultValue(1)
        .min(1).max(2).sliderRange(1, 2)
        .build());

    private final Setting<Boolean> coverTop = sgLayout.add(new BoolSetting.Builder()
        .name("cover-top")
        .description("Also place blocks directly on top of the bed halves.")
        .defaultValue(true)
        .build());

    private static final double REACH = 4.5;
    private static final double REACH_SQ = REACH * REACH;
    private static final int COLOR_DIST_SQ = 2500;
    private static final int STALL_LIMIT = 10;
    private static final int TOTAL_FAIL_LIMIT = 20;
    private static final int GRACE_TICKS = 2;
    private static final int SNEAK_SETTLE_TICKS = 3;

    private final Map<BlockPos, BlockPos> footByHead = new HashMap<>();
    private final List<BlockPos> bedHeads = new ArrayList<>();
    private final Set<BlockPos> renderBeds = new HashSet<>();
    private final Set<BlockPos> defendedThisSession = new LinkedHashSet<>();
    private final Set<BlockPos> cooledBeds = new HashSet<>();
    private final Set<DyeColor> colorCache = new HashSet<>();
    private final List<BlockPos> pendingCandidates = new ArrayList<>();
    private final Map<BlockPos, Integer> passFails = new HashMap<>();
    private final Map<BlockPos, Integer> totalFails = new HashMap<>();

    private String colorKeyCache = null;
    private BlockPos currentBed = null;
    private boolean currentRingFailed = false;
    private boolean lastConsumed = false;
    private boolean sneakHeld = false;
    private boolean sneakRequested = false;
    private int sneakArmTimer = 0;
    private int scanTimer = 0;
    private int placeTimer = 0;
    private long lastNoSource = 0;

    public BedDefender() {
        super(Orbiter.CATEGORY, "bed-defender", "Auto-places blocks in a protective ring around beds.");
    }

    @Override
    public void onActivate() {
        resetSessionState();
    }

    @Override
    public void onDeactivate() {
        releaseSneak();
        restoreSlot();
        resetSessionState();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetSessionState();
    }

    private void resetSessionState() {
        releaseSneak();
        defendedThisSession.clear();
        cooledBeds.clear();
        passFails.clear();
        totalFails.clear();
        colorKeyCache = null;
        colorCache.clear();
        resetWorkState();
        footByHead.clear();
        bedHeads.clear();
        renderBeds.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (sneakArmTimer > 0) sneakArmTimer--;

        scanTimer++;
        placeTimer++;
        if (scanTimer >= scanInterval.get()) {
            scanTimer = 0;
            scanBeds();
        }

        if (currentBed != null && !bedExists(currentBed)) {
            currentBed = null;
            pendingCandidates.clear();
            currentRingFailed = false;
            releaseSneak();
        }

        if (currentBed == null) pickNextBed();
        if (currentBed == null) {
            releaseSneak();
            restoreSlot();
            return;
        }

        if (placeTimer < delay.get()) return;
        placeTimer = 0;

        if (pendingCandidates.isEmpty()) {
            if (!currentRingFailed) defendedThisSession.add(currentBed);
            else cooledBeds.add(currentBed);
            currentBed = null;
            releaseSneak();
            return;
        }

        BlockPos target = findPlaceable();
        if (target == null) return;

        int slot = resolveSourceSlot();
        if (slot < 0) {
            long now = System.currentTimeMillis();
            if (now - lastNoSource > 5000) {
                lastNoSource = now;
                info("No placeable blocks in inventory");
            }
            return;
        }

        if (sneakArmTimer > 0) return;

        switchTo(slot);

        if (placeAt(target)) {
            pendingCandidates.remove(target);
            passFails.remove(target);
        } else {
            totalFails.merge(target, 1, Integer::sum);
            if (sneakRequested) {
                sneakRequested = false;
                holdSneak();
                sneakArmTimer = SNEAK_SETTLE_TICKS;
            } else if (lastConsumed) {
                placeTimer = -GRACE_TICKS;
            } else {
                passFails.merge(target, 1, Integer::sum);
                if (passFails.getOrDefault(target, 0) >= STALL_LIMIT
                    || totalFails.getOrDefault(target, 0) >= TOTAL_FAIL_LIMIT) {
                    pendingCandidates.remove(target);
                    currentRingFailed = true;
                }
            }
        }
        restoreSlot();

        if (sneakHeld && !hasTopPending()) releaseSneak();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || mc.player == null || mc.level == null) return;

        SettingColor color = renderColor.get();

        for (BlockPos pos : renderBeds) {
            event.renderer.box(pos, color, color, ShapeMode.Both, 0);
        }
        for (BlockPos pos : pendingCandidates) {
            event.renderer.box(pos, color, color, ShapeMode.Both, 0);
        }
    }

    private void resetWorkState() {
        currentBed = null;
        pendingCandidates.clear();
        scanTimer = 0;
        placeTimer = 0;
        lastNoSource = 0;
        currentRingFailed = false;
    }

    private BlockPos findPlaceable() {
        Iterator<BlockPos> iterator = pendingCandidates.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!mc.level.getBlockState(pos).canBeReplaced()) {
                iterator.remove();
                continue;
            }
            if (eyeDistSq(pos) <= REACH_SQ) return pos;
        }
        return null;
    }

    private void scanBeds() {
        footByHead.clear();
        renderBeds.clear();
        cooledBeds.clear();
        passFails.clear();

        int r = (int) Math.ceil(range.get());
        BlockPos center = mc.player.blockPosition();
        BlockPos min = center.offset(-r, -r, -r);
        BlockPos max = center.offset(r, r, r);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock bed)) continue;
            if (!bedAllowed(bed)) continue;

            BlockPos fixed = pos.immutable();
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos head;
            BlockPos foot;

            if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                head = fixed.relative(facing);
                foot = fixed;
            } else {
                head = fixed;
                foot = fixed.relative(facing.getOpposite());
            }

            if (footByHead.containsKey(head)) continue;
            footByHead.put(head, foot);
            renderBeds.add(head);
            renderBeds.add(foot);
        }

        bedHeads.clear();
        bedHeads.addAll(footByHead.keySet());
        bedHeads.sort(Comparator.comparingDouble(this::distToPlayer));
    }

    private boolean bedExists(BlockPos head) {
        BlockPos foot = footByHead.get(head);
        if (foot == null) return false;
        return mc.level.getBlockState(head).getBlock() instanceof BedBlock
            && mc.level.getBlockState(foot).getBlock() instanceof BedBlock;
    }

    private boolean bedAllowed(BedBlock bed) {
        BedFilter filter = bedFilter.get();
        if (filter == BedFilter.AnyBed) return true;

        DyeColor bedColor = bed.getColor();

        if (filter == BedFilter.ClosestToLeather) {
            int armorRgb = leatherArmorColor();
            if (armorRgb < 0) return true;
            return adjacentColor(bedColor, armorRgb);
        }

        return parsedBedColors().contains(bedColor);
    }

    private Set<DyeColor> parsedBedColors() {
        String raw = bedColors.get();
        if (raw.equals(colorKeyCache)) return colorCache;
        colorKeyCache = raw;
        colorCache.clear();
        for (String name : raw.toLowerCase(Locale.ROOT).split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            DyeColor parsed = parseColor(trimmed);
            if (parsed == null) warning("Unknown bed color: " + trimmed);
            else colorCache.add(parsed);
        }
        return colorCache;
    }

    private DyeColor parseColor(String name) {
        for (DyeColor color : DyeColor.values()) {
            if (color.getName().equals(name)) return color;
        }
        return null;
    }

    private int leatherArmorColor() {
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty() || !isLeatherArmor(stack)) continue;
            if (!stack.has(DataComponents.DYED_COLOR)) return DyedItemColor.LEATHER_COLOR;
            return DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR);
        }
        return -1;
    }

    private boolean isLeatherArmor(ItemStack stack) {
        return stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
            || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS);
    }

    private DyeColor closestDye(int rgb) {
        DyeColor best = null;
        double bestDist = Double.MAX_VALUE;
        for (DyeColor color : DyeColor.values()) {
            int texture = color.getTextureDiffuseColor();
            int dr = ((texture >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            int dg = ((texture >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            int db = (texture & 0xFF) - (rgb & 0xFF);
            double dist = dr * (double) dr + dg * (double) dg + db * (double) db;
            if (dist < bestDist) {
                bestDist = dist;
                best = color;
            }
        }
        return best;
    }

    private boolean adjacentColor(DyeColor bedColor, int armorRgb) {
        if (bedColor == closestDye(armorRgb)) return true;
        int bedRgb = bedColor.getTextureDiffuseColor();
        int dr = ((bedRgb >> 16) & 0xFF) - ((armorRgb >> 16) & 0xFF);
        int dg = ((bedRgb >> 8) & 0xFF) - ((armorRgb >> 8) & 0xFF);
        int db = (bedRgb & 0xFF) - (armorRgb & 0xFF);
        return dr * dr + dg * dg + db * db <= COLOR_DIST_SQ;
    }

    private void pickNextBed() {
        if (bedHeads.isEmpty()) return;

        if (requeueGaps()) return;

        if (placeMode.get() != PlaceMode.AllBeds && !defendedThisSession.isEmpty()) return;

        for (BlockPos head : bedHeads) {
            if (defendedThisSession.contains(head) || cooledBeds.contains(head)) continue;
            List<BlockPos> ring = buildRing(head);
            if (ring.isEmpty()) continue;
            currentBed = head;
            currentRingFailed = false;
            pendingCandidates.addAll(ring);
            return;
        }
    }

    private boolean requeueGaps() {
        if (defendedThisSession.isEmpty()) return false;
        for (BlockPos head : defendedThisSession) {
            if (!bedExists(head)) continue;
            List<BlockPos> gaps = buildRing(head);
            if (gaps.isEmpty()) continue;
            currentBed = head;
            currentRingFailed = false;
            pendingCandidates.addAll(gaps);
            return true;
        }
        return false;
    }

    private List<BlockPos> buildRing(BlockPos head) {
        BlockPos foot = footByHead.get(head);
        if (foot == null) return new ArrayList<>();

        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        collectRing(candidates, head);
        collectRing(candidates, foot);

        BlockPos feet = mc.player.blockPosition();

        List<BlockPos> sides = new ArrayList<>();
        List<BlockPos> tops = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (pos.equals(feet) || pos.equals(feet.above())) continue;
            if (totalFails.getOrDefault(pos, 0) >= TOTAL_FAIL_LIMIT) continue;
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            if (pos.getY() > head.getY()) tops.add(pos);
            else sides.add(pos);
        }

        Comparator<BlockPos> order = Comparator.comparingDouble(pos -> ringOrderDist(pos, head, foot));
        sides.sort(order);
        tops.sort(order);

        List<BlockPos> ring = new ArrayList<>(sides);
        ring.addAll(tops);

        int cap = blocksPerBed.get();
        if (ring.size() > cap) return new ArrayList<>(ring.subList(0, cap));
        return ring;
    }

    private double ringOrderDist(BlockPos pos, BlockPos head, BlockPos foot) {
        double cx = (head.getX() + foot.getX()) / 2.0 + 0.5;
        double cz = (head.getZ() + foot.getZ()) / 2.0 + 0.5;
        double dx = pos.getX() + 0.5 - cx;
        double dz = pos.getZ() + 0.5 - cz;
        return dx * dx + dz * dz;
    }

    private void collectRing(Set<BlockPos> out, BlockPos base) {
        int r = ringRadius.get();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) continue;
                out.add(base.offset(dx, 0, dz));
            }
        }
        if (coverTop.get()) out.add(base.above());
    }

    private boolean placeAt(BlockPos target) {
        lastConsumed = false;

        BlockPos below = target.below();
        BlockState belowState = mc.level.getBlockState(below);
        if (belowState.getBlock() instanceof BedBlock) {
            if (placeViaNeighbors(target)) return true;
            if (!mc.player.input.keyPresses.shift()) {
                sneakRequested = true;
                return false;
            }
            return interactBlock(target, new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
        }

        if (!belowState.canBeReplaced() && belowState.isFaceSturdy(mc.level, below, Direction.UP)) {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
            if (interactBlock(target, hit)) return true;
        }

        return placeViaNeighbors(target);
    }

    private boolean placeViaNeighbors(BlockPos target) {
        Direction[] priorities = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

        for (Direction dir : priorities) {
            BlockPos neighbor = target.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            BlockState neighborState = mc.level.getBlockState(neighbor);

            if (neighborState.isAir() || !neighborState.isFaceSturdy(mc.level, neighbor, dir.getOpposite())) continue;
            if (neighborState.getBlock() instanceof BedBlock || mc.level.getBlockEntity(neighbor) != null) continue;

            Direction clickFace = dir.getOpposite();
            Vec3 hitPos = Vec3.atCenterOf(neighbor).add(
                clickFace.getStepX() * 0.5,
                clickFace.getStepY() * 0.5,
                clickFace.getStepZ() * 0.5
            );

            BlockHitResult hit = new BlockHitResult(hitPos, clickFace, neighbor, false);
            if (interactBlock(target, hit)) return true;
        }

        return false;
    }

    private void holdSneak() {
        if (sneakHeld) return;
        mc.options.keyShift.setDown(true);
        sneakHeld = true;
    }

    private void releaseSneak() {
        if (!sneakHeld) return;
        mc.options.keyShift.setDown(false);
        sneakHeld = false;
        sneakRequested = false;
    }

    private boolean hasTopPending() {
        for (BlockPos pos : pendingCandidates) {
            if (mc.level.getBlockState(pos.below()).getBlock() instanceof BedBlock) return true;
        }
        return false;
    }

    private boolean interactBlock(BlockPos target, BlockHitResult hit) {
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        lastConsumed = result.consumesAction();
        if (!lastConsumed) return false;
        EntityCompat.swing(mc.player, InteractionHand.MAIN_HAND);
        BlockState after = mc.level.getBlockState(target);
        return !after.isAir() && !after.canBeReplaced();
    }

    private int resolveSourceSlot() {
        int selected = mc.player.getInventory().getSelectedSlot();

        if (!autoSwitch.get()) {
            ItemStack held = mc.player.getMainHandItem();
            if (held.getItem() instanceof BlockItem blockItem
                && (!skipDangerous.get() || !isDangerous(blockItem.getBlock()))) {
                return selected;
            }
            return -1;
        }

        List<Block> preferred = preferredBlocks.get();
        if (preferred != null && !preferred.isEmpty()) {
            for (Block block : preferred) {
                if (skipDangerous.get() && isDangerous(block)) continue;
                FindItemResult result = InvUtils.find(stack -> !stack.isEmpty() && stack.getItem() == block.asItem());
                if (result.found()) return toHotbarSlot(result);
            }
        }

        if (allowHotbar.get()) {
            FindItemResult result = InvUtils.find(stack -> {
                if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
                return !skipDangerous.get() || !isDangerous(blockItem.getBlock());
            }, 0, 8);
            if (result.found()) return result.slot();
        }

        if (allowHeld.get()) {
            ItemStack held = mc.player.getMainHandItem();
            if (held.getItem() instanceof BlockItem blockItem
                && (!skipDangerous.get() || !isDangerous(blockItem.getBlock()))) {
                return selected;
            }
        }

        return -1;
    }

    private int toHotbarSlot(FindItemResult result) {
        if (result.slot() <= 8) return result.slot();

        FindItemResult empty = InvUtils.find(ItemStack::isEmpty, 0, 8);
        int target = empty.found() ? empty.slot() : mc.player.getInventory().getSelectedSlot();
        InvUtils.move().from(result.slot()).toHotbar(target);
        return target;
    }

    private void switchTo(int slot) {
        if (!autoSwitch.get()) return;
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        InvUtils.swap(slot, true);
    }

    private void restoreSlot() {
        InvUtils.swapBack();
    }

    private boolean isDangerous(Block block) {
        return block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK || block == Blocks.FIRE
            || block == Blocks.SOUL_FIRE || block == Blocks.LAVA || block == Blocks.CAMPFIRE
            || block == Blocks.SOUL_CAMPFIRE || block == Blocks.WITHER_ROSE
            || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POINTED_DRIPSTONE;
    }

    private double eyeDistSq(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getEyeY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private double distToPlayer(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String getInfoString() {
        return bedHeads.size() + " beds";
    }
}
