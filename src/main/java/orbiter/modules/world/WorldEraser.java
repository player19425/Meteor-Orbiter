package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import orbiter.util.FillCommandIterator;
import orbiter.util.SafeRegionMath;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class WorldEraser extends CreativeSafetyModule {
    private static final long MAX_FILL_VOLUME = 32768L;

    private static final int MAX_TOTAL_COMMANDS = 100000;
    private static final int ABSOLUTE_MAX_RADIUS = 10000;
    private static final long HARD_MAX_ESTIMATED_BLOCKS = 100_000_000L;

    private static final class XRange {
        private final int x1;
        private final int x2;

        private XRange(int x1, int x2) {
            this.x1 = x1;
            this.x2 = x2;
        }

        private boolean matches(XStrip strip) {
            return strip.x1 == x1 && strip.x2 == x2;
        }
    }

    private static final class XStrip {
        private final int x1;
        private final int x2;
        private final int startZ;

        private XStrip(int x1, int x2, int startZ) {
            this.x1 = x1;
            this.x2 = x2;
            this.startZ = startZ;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShape = settings.createGroup("Shape");
    private final SettingGroup sgPattern = settings.createGroup("Pattern");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgWorldEdit = settings.createGroup("WorldEdit");

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
            .name("radius")
            .description("Block radius to erase.")
            .defaultValue(10)
            .min(1)
            .max(ABSOLUTE_MAX_RADIUS)
            .sliderRange(1, 1000)
            .build());

    private final Setting<Integer> maxGeneratedCommands = sgGeneral.add(new IntSetting.Builder()
            .name("max-generated-commands")
            .description("Reject operations estimated to exceed this command count.")
            .defaultValue(10000)
            .min(1)
            .max(MAX_TOTAL_COMMANDS)
            .sliderRange(1, 20000)
            .build());

    private final Setting<Double> maxEstimatedBlocks = sgGeneral.add(new DoubleSetting.Builder()
            .name("max-estimated-blocks")
            .description("Reject operations above this estimated block budget before generating commands.")
            .defaultValue(5_000_000)
            .min(1)
            .max(HARD_MAX_ESTIMATED_BLOCKS)
            .sliderRange(1_000, 20_000_000)
            .build());

    private final Setting<String> fillBlock = sgGeneral.add(new StringSetting.Builder()
            .name("fill-block")
            .description("Block to fill with (e.g. air, water, stone).")
            .defaultValue("air")
            .build());

    private final Setting<Boolean> confirmationMsg = sgGeneral.add(new BoolSetting.Builder()
            .name("show-confirmation")
            .description("Show confirmation messages in chat.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> hollow = sgGeneral.add(new BoolSetting.Builder()
            .name("hollow")
            .description("Only erase the outer shell of the shape.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> hollowThickness = sgGeneral.add(new IntSetting.Builder()
            .name("hollow-thickness")
            .description("Thickness of the hollow shell in blocks.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 10)
            .visible(hollow::get)
            .build());

    private final Setting<EraseShape> shape = sgShape.add(new EnumSetting.Builder<EraseShape>()
            .name("shape")
            .description("Shape of the area to erase.")
            .defaultValue(EraseShape.Cube)
            .build());

    private final Setting<Integer> cylinderHeight = sgShape.add(new IntSetting.Builder()
            .name("cylinder-height")
            .description("Vertical height used when shape is Cylinder.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 320)
            .visible(() -> shape.get() == EraseShape.Cylinder)
            .build());

    private final Setting<ErasePattern> erasePattern = sgPattern.add(new EnumSetting.Builder<ErasePattern>()
            .name("erase-pattern")
            .description("Pattern mode for erasing blocks.")
            .defaultValue(ErasePattern.All)
            .build());

    private final Setting<String> targetBlock = sgPattern.add(new StringSetting.Builder()
            .name("target-block")
            .description("Block ID for OnlyBlock/InvertBlock patterns (e.g. stone, diamond_ore).")
            .defaultValue("stone")
            .visible(() -> erasePattern.get() == ErasePattern.OnlyBlock
                    || erasePattern.get() == ErasePattern.InvertBlock)
            .build());

    private static final String[] ORE_BLOCKS = {
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris"
    };

    private final Setting<Integer> commandsPerTick = sgTiming.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of /fill commands sent per tick. Lower = safer.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<Integer> delayBetweenBursts = sgTiming.add(new IntSetting.Builder()
            .name("delay-between-bursts")
            .description("Ticks to wait between command bursts.")
            .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build());

    private final Setting<Boolean> progressMessages = sgTiming.add(new BoolSetting.Builder()
            .name("progress-messages")
            .description("Show progress percentage in chat.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> useWorldEdit = sgWorldEdit.add(new BoolSetting.Builder()
            .name("use-worldedit")
            .description("Use WorldEdit commands instead of /fill.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> weConfirm = sgWorldEdit.add(new BoolSetting.Builder()
            .name("we-auto-confirm")
            .description("Automatically send //confirm after WorldEdit commands.")
            .defaultValue(true)
            .visible(useWorldEdit::get)
            .build());

    private long firstEnableTime = 0;
    private boolean armed = false;
    private List<String> pendingCommands;
    private int commandIndex;
    private int tickDelay;

    private boolean lazyMode = false;
    private int lazyMinX, lazyMaxX, lazyMinY, lazyMaxY, lazyMinZ, lazyMaxZ;
    private int lazyY, lazyZ;
    private String lazyBlock;
    private int lazyGeneratedCount;
    private Iterator<String> lazyIterator;

    public WorldEraser() {
        super("world-eraser",
                "Erases blocks in a radius. Toggle twice to trigger. OP.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }
        if (!hasCommandPermission()) {
            warning("OP or command permissions are required.");
            toggle();
            return;
        }
        String preflightError = validatePreflight();
        if (preflightError != null) {
            warning("WorldEraser rejected: " + preflightError);
            toggle();
            return;
        }
        long now = System.currentTimeMillis();

        if (!armed) {
            armed = true;
            firstEnableTime = now;

            if (confirmationMsg.get()) {
                warning("WorldEraser ARMED! Enable again within 10 seconds to execute.");
                warning("Shape: " + shape.get() + " | Radius: " + radius.get() + " | Block: " + fillBlock.get());
            }

            toggle();
            return;
        }

        if (now - firstEnableTime > 10000) {
            armed = false;
            warning("Safety window expired (>10s). Re-arm by enabling again.");
            toggle();
            return;
        }

        armed = false;
        info("Executing WorldEraser...");

        try {
            if (useWorldEdit.get()) {
                executeWorldEdit();
                toggle();
            } else {

                startLazyGeneration();
                if (!lazyMode && (pendingCommands == null || pendingCommands.isEmpty())) {
                    warning("No commands generated!");
                    toggle();
                } else if (lazyMode) {
                    info("WorldEraser started (lazy mode). Generating and sending commands...");
                } else {
                    info("Queued " + pendingCommands.size() + " fill commands. Processing...");
                }
            }
        } catch (Exception e) {
            error("WorldEraser error: " + e.getMessage());
            toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null) return;

        if (lazyMode) {
            if (tickDelay > 0) { tickDelay--; return; }

            int sent = 0;
            while (sent < commandsPerTick.get() && lazyGeneratedCount < maxGeneratedCommands.get()) {
                if (lazyIterator == null || !lazyIterator.hasNext()) {
                    lazyMode = false;
                    info("WorldEraser complete! Generated and executed " + lazyGeneratedCount + " commands.");
                    toggle();
                    return;
                }
                mc.player.connection.sendCommand(CommandUtils.vanilla(lazyIterator.next()));
                lazyGeneratedCount++;
                sent++;
            }

            if (lazyGeneratedCount >= maxGeneratedCommands.get()) {
                lazyMode = false;
                warning("Hit command cap (" + maxGeneratedCommands.get() + "). Stopping safely.");
                toggle();
            }

            tickDelay = delayBetweenBursts.get();
            return;
        }

        if (pendingCommands == null) return;

        if (commandIndex >= pendingCommands.size()) {
            info("WorldEraser complete! Executed " + pendingCommands.size() + " commands.");
            pendingCommands = null;
            toggle();
            return;
        }

        if (delayBetweenBursts.get() > 0) {
            tickDelay++;
            if (tickDelay < delayBetweenBursts.get())
                return;
            tickDelay = 0;
        }

        int sent = 0;
        while (commandIndex < pendingCommands.size() && sent < commandsPerTick.get()) {
            mc.player.connection.sendCommand(CommandUtils.vanilla(pendingCommands.get(commandIndex)));
            commandIndex++;
            sent++;
        }

        if (progressMessages.get() && pendingCommands.size() > 10) {
            int percent = (int) ((commandIndex / (double) pendingCommands.size()) * 100);
            if (percent % 25 == 0 && percent > 0) {
                info("Progress: %d%% (%d/%d)", percent, commandIndex, pendingCommands.size());
            }
        }
    }

    @Override
    public void onDeactivate() {
        pendingCommands = null;
        commandIndex = 0;
        lazyMode = false;
        lazyIterator = null;
    }

    private void startLazyGeneration() {
        if (mc.level == null || mc.player == null) return;
        BlockPos center = mc.player.blockPosition();
        int r = radius.get();
        lazyBlock = "minecraft:" + fillBlock.get().replace("minecraft:", "").trim();

        long volume = (2L * r + 1) * (2L * r + 1) * Math.max(1, 2L * r + 1);
        if (volume > 500000 && shape.get() == EraseShape.Cube
            && erasePattern.get() == ErasePattern.All && !hollow.get()) {

            lazyMinX = center.getX() - r;
            lazyMaxX = center.getX() + r;
            lazyMinY = Math.max(mc.level.getMinY(), center.getY() - r);
            lazyMaxY = Math.min(worldTopY(), center.getY() + r);
            lazyMinZ = center.getZ() - r;
            lazyMaxZ = center.getZ() + r;
            lazyGeneratedCount = 0;
            lazyIterator = new FillCommandIterator(lazyMinX, lazyMinY, lazyMinZ,
                lazyMaxX, lazyMaxY, lazyMaxZ, lazyBlock);
            lazyMode = true;
            if (tickDelay < 0) tickDelay = 0;
        } else {

            lazyMode = false;
            pendingCommands = buildVanillaCommands();
            commandIndex = 0;
            tickDelay = 0;
        }
    }

    private String validatePreflight() {
        if (mc.level == null || mc.player == null) return "world is unavailable";
        int r = radius.get();
        if (r < 1 || r > ABSOLUTE_MAX_RADIUS) return "radius must be between 1 and " + ABSOLUTE_MAX_RADIUS;

        BlockPos center = mc.player.blockPosition();
        long minX = (long) center.getX() - r;
        long maxX = (long) center.getX() + r;
        long minZ = (long) center.getZ() - r;
        long maxZ = (long) center.getZ() + r;
        if (minX < SafeRegionMath.MIN_WORLD_COORDINATE || maxX > SafeRegionMath.MAX_WORLD_COORDINATE
            || minZ < SafeRegionMath.MIN_WORLD_COORDINATE || maxZ > SafeRegionMath.MAX_WORLD_COORDINATE) {
            return "region exceeds legal world coordinates";
        }

        long height = shape.get() == EraseShape.Cylinder
            ? cylinderHeight.get()
            : Math.min((long) worldTopY(), (long) center.getY() + r)
                - Math.max((long) mc.level.getMinY(), (long) center.getY() - r) + 1L;
        long diameter = Math.addExact(Math.multiplyExact(2L, r), 1L);
        long boundingVolume;
        try {
            boundingVolume = Math.multiplyExact(Math.multiplyExact(diameter, diameter), Math.max(1L, height));
        } catch (ArithmeticException overflow) {
            return "region volume overflow";
        }
        long estimatedBlocks = switch (shape.get()) {
            case Cube -> boundingVolume;
            case Sphere -> Math.min(boundingVolume, (long) Math.ceil((4.0 / 3.0) * Math.PI * r * r * r));
            case Cylinder -> Math.min(boundingVolume, (long) Math.ceil(Math.PI * r * r * height));
        };
        if (estimatedBlocks > Math.min(HARD_MAX_ESTIMATED_BLOCKS, maxEstimatedBlocks.get().longValue())) {
            return "estimated " + estimatedBlocks + " blocks exceeds configured budget";
        }

        long estimatedCommands = erasePattern.get() == ErasePattern.Checkerboard
            ? (estimatedBlocks + 1L) / 2L
            : Math.max(1L, (estimatedBlocks + MAX_FILL_VOLUME - 1L) / MAX_FILL_VOLUME);
        if (erasePattern.get() == ErasePattern.NonOres) estimatedCommands = Math.multiplyExact(estimatedCommands, 25L);
        if (estimatedCommands > maxGeneratedCommands.get()) {
            return "estimated " + estimatedCommands + " commands exceeds configured budget";
        }
        info("Preflight: radius=%d, estimated-blocks=%d, estimated-commands=%d", r, estimatedBlocks, estimatedCommands);
        return null;
    }

    private List<String> buildVanillaCommands() {
        List<String> commands = new ArrayList<>();
        if (mc.level == null || mc.player == null) return commands;
        BlockPos center = mc.player.blockPosition();
        int r = radius.get();
        String block = "minecraft:" + fillBlock.get().replace("minecraft:", "").trim();

        switch (shape.get()) {
            case Cube -> buildCubeCommands(commands, center, r, block);
            case Sphere, Cylinder -> buildRadialCommands(commands, center, r, block, shape.get());
        }

        return commands;
    }

    private void buildCubeCommands(List<String> commands, BlockPos center, int r, String block) {
        int minX = center.getX() - r;
        int maxX = center.getX() + r;
        int minY = Math.max(mc.level.getMinY(), center.getY() - r);
        int maxY = Math.min(worldTopY(), center.getY() + r);
        int minZ = center.getZ() - r;
        int maxZ = center.getZ() + r;

        if (minY > maxY) return;

        if (!hollow.get()) {
            addPatternCommands(commands, minX, minY, minZ, maxX, maxY, maxZ, block);
            return;
        }

        int shell = Math.max(1, hollowThickness.get());
        int innerMinX = minX + shell;
        int innerMaxX = maxX - shell;
        int innerMinY = minY + shell;
        int innerMaxY = maxY - shell;
        int innerMinZ = minZ + shell;
        int innerMaxZ = maxZ - shell;

        addCuboidIfValid(commands, minX, minY, minZ, maxX, Math.min(maxY, minY + shell - 1), maxZ, block);
        addCuboidIfValid(commands, minX, Math.max(minY, maxY - shell + 1), minZ, maxX, maxY, maxZ, block);
        addCuboidIfValid(commands, minX, innerMinY, minZ, Math.min(maxX, minX + shell - 1), innerMaxY, maxZ, block);
        addCuboidIfValid(commands, Math.max(minX, maxX - shell + 1), innerMinY, minZ, maxX, innerMaxY, maxZ, block);
        addCuboidIfValid(commands, innerMinX, innerMinY, minZ, innerMaxX, innerMaxY, Math.min(maxZ, minZ + shell - 1), block);
        addCuboidIfValid(commands, innerMinX, innerMinY, Math.max(minZ, maxZ - shell + 1), innerMaxX, innerMaxY, maxZ, block);
    }

    private void buildRadialCommands(List<String> commands, BlockPos center, int r, String block, EraseShape radialShape) {
        int minY = radialShape == EraseShape.Cylinder
                ? center.getY()
                : center.getY() - r;
        int maxY = radialShape == EraseShape.Cylinder
                ? center.getY() + cylinderHeight.get() - 1
                : center.getY() + r;

        minY = Math.max(mc.level.getMinY(), minY);
        maxY = Math.min(worldTopY(), maxY);
        if (minY > maxY) return;

        int shell = hollow.get() ? Math.max(1, hollowThickness.get()) : 0;
        int innerRadius = Math.max(0, r - shell);
        int innerMinY = minY + shell;
        int innerMaxY = maxY - shell;

        for (int y = minY; y <= maxY; y++) {
            List<XStrip> active = new ArrayList<>();
            for (int worldZ = center.getZ() - r; worldZ <= center.getZ() + r + 1; worldZ++) {
                List<XRange> rowRanges = worldZ <= center.getZ() + r
                        ? buildRowRanges(center, y, worldZ, r, innerRadius, minY, maxY, innerMinY, innerMaxY, radialShape)
                        : List.of();

                List<XStrip> nextActive = new ArrayList<>();
                for (XRange range : rowRanges) {
                    XStrip existing = removeMatchingStrip(active, range);
                    if (existing != null) nextActive.add(existing);
                    else nextActive.add(new XStrip(range.x1, range.x2, worldZ));
                }

                for (XStrip strip : active) {
                    addPatternCommands(commands, strip.x1, y, strip.startZ, strip.x2, y, worldZ - 1, block);
                }

                active = nextActive;
            }
        }
    }

    private List<XRange> buildRowRanges(BlockPos center, int y, int worldZ, int radius, int innerRadius, int minY,
            int maxY, int innerMinY, int innerMaxY, EraseShape radialShape) {
        List<XRange> ranges = new ArrayList<>(2);
        int dz = worldZ - center.getZ();
        int dy = y - center.getY();

        int outerReach = getHorizontalReach(radius, dy, dz, radialShape);
        if (outerReach < 0) return ranges;

        int outerMinX = center.getX() - outerReach;
        int outerMaxX = center.getX() + outerReach;

        if (!hollow.get()) {
            ranges.add(new XRange(outerMinX, outerMaxX));
            return ranges;
        }

        boolean innerActive = y >= innerMinY && y <= innerMaxY && innerRadius > 0;
        int innerReach = innerActive ? getHorizontalReach(innerRadius, dy, dz, radialShape) : -1;

        if (innerReach < 0) {
            ranges.add(new XRange(outerMinX, outerMaxX));
            return ranges;
        }

        int innerMinX = center.getX() - innerReach;
        int innerMaxX = center.getX() + innerReach;

        if (outerMinX <= innerMinX - 1) ranges.add(new XRange(outerMinX, innerMinX - 1));
        if (innerMaxX + 1 <= outerMaxX) ranges.add(new XRange(innerMaxX + 1, outerMaxX));
        return ranges;
    }

    private int getHorizontalReach(int radius, int dy, int dz, EraseShape radialShape) {
        if (radius <= 0) return -1;

        int distanceSq = radialShape == EraseShape.Sphere
                ? radius * radius - dy * dy - dz * dz
                : radius * radius - dz * dz;
        if (distanceSq < 0) return -1;

        return (int) Math.floor(Math.sqrt(distanceSq));
    }

    private XStrip removeMatchingStrip(List<XStrip> active, XRange range) {
        for (int i = 0; i < active.size(); i++) {
            XStrip strip = active.get(i);
            if (range.matches(strip)) {
                active.remove(i);
                return strip;
            }
        }

        return null;
    }

    private void addCuboidIfValid(List<String> commands, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        if (x1 > x2 || y1 > y2 || z1 > z2) return;
        addPatternCommands(commands, x1, y1, z1, x2, y2, z2, block);
    }

    private void addPatternCommands(List<String> commands, int x1, int y1, int z1, int x2, int y2, int z2,
            String block) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        if (erasePattern.get() != ErasePattern.Checkerboard) {
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume > MAX_FILL_VOLUME && (minX != maxX || minY != maxY || minZ != maxZ)) {
                int sizeX = maxX - minX;
                int sizeY = maxY - minY;
                int sizeZ = maxZ - minZ;

                if (sizeX >= sizeY && sizeX >= sizeZ) {
                    int midX = minX + sizeX / 2;
                    addPatternCommands(commands, minX, minY, minZ, midX, maxY, maxZ, block);
                    addPatternCommands(commands, midX + 1, minY, minZ, maxX, maxY, maxZ, block);
                } else if (sizeZ >= sizeY) {
                    int midZ = minZ + sizeZ / 2;
                    addPatternCommands(commands, minX, minY, minZ, maxX, maxY, midZ, block);
                    addPatternCommands(commands, minX, minY, midZ + 1, maxX, maxY, maxZ, block);
                } else {
                    int midY = minY + sizeY / 2;
                    addPatternCommands(commands, minX, minY, minZ, maxX, midY, maxZ, block);
                    addPatternCommands(commands, minX, midY + 1, minZ, maxX, maxY, maxZ, block);
                }
                return;
            }
        }

        switch (erasePattern.get()) {
            case All -> {
                commands.add(String.format("fill %d %d %d %d %d %d %s", minX, minY, minZ, maxX, maxY, maxZ, block));
            }
            case NonOres -> {

                String[] commonBlocks = { "minecraft:stone", "minecraft:dirt", "minecraft:grass_block",
                        "minecraft:gravel", "minecraft:sand", "minecraft:sandstone", "minecraft:cobblestone",
                        "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                        "minecraft:deepslate", "minecraft:tuff", "minecraft:calcite",
                        "minecraft:water", "minecraft:lava", "minecraft:netherrack",
                        "minecraft:basalt", "minecraft:blackstone", "minecraft:end_stone",
                        "minecraft:obsidian", "minecraft:clay", "minecraft:terracotta",
                        "minecraft:smooth_basalt" };
                for (String b : commonBlocks) {
                    commands.add(String.format("fill %d %d %d %d %d %d %s replace %s",
                            minX, minY, minZ, maxX, maxY, maxZ, block, b));
                }
            }
            case Checkerboard -> {

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if ((x + y + z) % 2 == 0) {
                                commands.add(String.format("setblock %d %d %d %s", x, y, z, block));
                            }
                        }
                    }
                }
            }
            case OnlyBlock -> {
                String target = "minecraft:" + targetBlock.get().replace("minecraft:", "");
                commands.add(String.format("fill %d %d %d %d %d %d %s replace %s",
                        minX, minY, minZ, maxX, maxY, maxZ, block, target));
            }
            case InvertBlock -> {

                String target = "minecraft:" + targetBlock.get().replace("minecraft:", "");

                commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier replace %s",
                        minX, minY, minZ, maxX, maxY, maxZ, target));
                commands.add(String.format("fill %d %d %d %d %d %d %s",
                        minX, minY, minZ, maxX, maxY, maxZ, block));
                commands.add(String.format("fill %d %d %d %d %d %d %s replace minecraft:barrier",
                        minX, minY, minZ, maxX, maxY, maxZ, target));
            }
        }
    }

    private void executeWorldEdit() {
        if (mc.player == null || mc.player.connection == null) return;

        int r = radius.get();
        String block = fillBlock.get().replace("minecraft:", "");

        sendWorldEditCommand("pos1");

        switch (shape.get()) {
            case Cube -> {
                sendWorldEditCommand(String.format("expand %d", r));
                sendWorldEditCommand(String.format("expand %d up", r));
                sendWorldEditCommand(String.format("expand %d down", r));
                sendWorldEditCommand(String.format("set %s", block));
            }
            case Sphere -> sendWorldEditCommand(String.format("sphere %s %d", block, r));
            case Cylinder -> sendWorldEditCommand(String.format("cyl %s %d %d", block, r, cylinderHeight.get()));
        }

        if (weConfirm.get()) {
            sendWorldEditCommand("confirm");
        }

        info("WorldEdit erase executed! Shape: " + shape.get() + ", Radius: " + r);
    }

    private void sendWorldEditCommand(String command) {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand(command);
    }

    private boolean hasCommandPermission() {
        if (mc.player == null || mc.player.connection == null) return false;

        var dispatcher = mc.player.connection.getCommands();
        if (dispatcher == null || dispatcher.getRoot() == null) return false;

        return dispatcher.getRoot().getChild("fill") != null
                || dispatcher.getRoot().getChild("setblock") != null
                || dispatcher.getRoot().getChild("clone") != null
                || dispatcher.getRoot().getChild("sphere") != null
                || dispatcher.getRoot().getChild("cyl") != null
                || dispatcher.getRoot().getChild("expand") != null
                || dispatcher.getRoot().getChild("set") != null;
    }

    private int worldTopY() {
        return mc.level.getMinY() + mc.level.dimensionType().height() - 1;
    }
    public enum EraseShape {
        Cube,
        Sphere,
        Cylinder
    }

    public enum ErasePattern {
        All,
        NonOres,
        Checkerboard,
        OnlyBlock,
        InvertBlock
    }
}
