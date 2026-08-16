package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class WorldEditModule extends CreativeSafetyModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgFeatures = settings.createGroup("Features");

    private final Setting<Boolean> autoGiveWand = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-give-wand")
            .description("Give yourself the configured selection tool when enabled.")
            .defaultValue(true)
            .build());

    private final Setting<String> selectionToolItem = sgGeneral.add(new StringSetting.Builder()
            .name("selection-tool-item")
            .description("Item ID used as the selection tool (supports modded IDs).")
            .defaultValue("minecraft:stone_axe")
            .build());

    private final Setting<Boolean> showProgress = sgGeneral.add(new BoolSetting.Builder()
            .name("show-progress")
            .description("Show progress messages for operations.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> showBlockCount = sgGeneral.add(new BoolSetting.Builder()
            .name("show-block-count")
            .description("Show block count before executing operations.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> commandsPerTick = sgPerformance.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of commands sent per tick.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 100)
            .build());

    private final Setting<Integer> tickDelay = sgPerformance.add(new IntSetting.Builder()
            .name("tick-delay")
            .description("Ticks between command bursts.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 20)
            .build());

    private final Setting<Integer> fillChunkSize = sgPerformance.add(new IntSetting.Builder()
            .name("fill-chunk-size")
            .description("Max axis length per fill command chunk.")
            .defaultValue(32)
            .min(8)
            .sliderRange(8, 32)
            .build());

    private final Setting<Integer> maxGeneratedCommands = sgPerformance.add(new IntSetting.Builder()
            .name("max-generated-commands")
            .description("Reject an operation before execution when its generated command plan exceeds this bound.")
            .defaultValue(10000)
            .min(1)
            .max(100000)
            .sliderRange(1, 20000)
            .build());

    private final Setting<SpeedPreset> speedPreset = sgPerformance.add(new EnumSetting.Builder<SpeedPreset>()
            .name("speed-preset")
            .description("Quick speed presets.")
            .defaultValue(SpeedPreset.Fast)
            .build());

    private final Setting<Boolean> confirmLargeOps = sgFeatures.add(new BoolSetting.Builder()
            .name("confirm-large-ops")
            .description("Ask for confirmation before very large operations.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> largeOpThreshold = sgFeatures.add(new IntSetting.Builder()
            .name("large-op-threshold")
            .description("Block count threshold for confirmation prompt.")
            .defaultValue(100000)
            .min(1000)
            .sliderRange(1000, 1000000)
            .visible(confirmLargeOps::get)
            .build());

    private final Setting<Boolean> noiseMode = sgFeatures.add(new BoolSetting.Builder()
            .name("noise-fill")
            .description("Fill with mixed blocks instead of one block type.")
            .defaultValue(false)
            .build());

    private final Setting<String> noiseBlocks = sgFeatures.add(new StringSetting.Builder()
            .name("noise-block-list")
            .description("Comma-separated block list used by noise fill.")
            .defaultValue("stone,cobblestone,gravel,andesite,diorite,granite")
            .visible(noiseMode::get)
            .build());

    private final Setting<Boolean> preventUnloadedZoneError = sgFeatures.add(new BoolSetting.Builder()
            .name("prevent-unloaded-zone-error")
            .description("Teleport to target zone before operation if its chunk is unloaded.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> autoReturnAfterZoneTp = sgFeatures.add(new BoolSetting.Builder()
            .name("auto-return-after-zone-tp")
            .description("Return to original position after zone pre-teleport operation.")
            .defaultValue(true)
            .visible(preventUnloadedZoneError::get)
            .build());

    private final Setting<Integer> zoneTpDelay = sgFeatures.add(new IntSetting.Builder()
            .name("zone-tp-delay")
            .description("Ticks to wait after zone pre-teleport before running commands.")
            .defaultValue(4)
            .min(0)
            .sliderRange(0, 40)
            .visible(preventUnloadedZoneError::get)
            .build());

    private final Setting<Boolean> abortIfZoneTpFails = sgFeatures.add(new BoolSetting.Builder()
            .name("abort-if-zone-tp-fails")
            .description("Abort operation if pre-teleport verification fails.")
            .defaultValue(true)
            .visible(preventUnloadedZoneError::get)
            .build());

    private BlockPos pos1 = null;
    private BlockPos pos2 = null;

    private List<ExecutionStep> pendingCommands = null;
    private int cmdIndex = 0;
    private int delayCounter = 0;
    private int scriptWaitTicks = 0;
    private long operationStartTime = 0;
    private String currentOperationName = null;

    private boolean awaitingConfirmation = false;
    private OperationRequest pendingConfirmRequest = null;

    private BlockPos clipboardMin = null;
    private BlockPos clipboardMax = null;
    private BlockPos clipboardOrigin = null;

    private static final Path CLIPBOARD_FILE = Path.of(System.getProperty("user.home"), ".orbiter_clipboard.json");

    private static final int MAX_HISTORY = 100;
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();
    private HistoryEntry pendingHistoryEntry = null;

    private boolean warnedInvalidToolItem = false;

    public WorldEditModule() {
        super("world-edit", "Client-side WorldEdit. .we <command>");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        clearExecutionState();
        awaitingConfirmation = false;
        pendingConfirmRequest = null;

        loadClipboard();
        resolveSelectionToolItem(true);

        if (mc.player != null && autoGiveWand.get() && mc.player.getAbilities().instabuild) ensureSelectionToolInHotbar();
        applySpeedPreset();

        info("WorldEdit module active.");
        info("Selection: pos1/pos2, hpos1/hpos2, chunk, size, clear, expand, contract, shift, inset, outset");
        info("Blocks: set/replace/walls/outline/floor/roof/hollow/line/center");
        info("Shapes: sphere/hsphere/cyl/hcyl/pyramid/hpyramid");
        info("Clipboard: copy/cut/paste/flip/stack/move/saveclipboard/loadclipboard");
        info("Navigation: ascend/descend/ceiling/thru");
        info("History: undo [count], redo [count] (100 entries)");
    }

    @Override
    public void onDeactivate() {
        clearExecutionState();
        awaitingConfirmation = false;
        pendingConfirmRequest = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null) return;
        List<ExecutionStep> commands = pendingCommands;
        if (commands == null) return;

        if (scriptWaitTicks > 0) {
            scriptWaitTicks--;
            return;
        }

        if (cmdIndex >= commands.size()) {
            long elapsed = System.currentTimeMillis() - operationStartTime;
            if (showProgress.get()) {
                info(String.format("Operation complete (%s): %d commands in %.1fs", currentOperationName == null ? "unnamed" : currentOperationName, commands.size(), elapsed / 1000.0));
            }

            if (pendingHistoryEntry != null) {
                pushUndoEntry(pendingHistoryEntry);
                redoStack.clear();
                pendingHistoryEntry = null;
            }

            clearExecutionState();
            return;
        }

        if (tickDelay.get() > 0) {
            delayCounter++;
            if (delayCounter < tickDelay.get()) return;
            delayCounter = 0;
        }

        int sent = 0;
        while (cmdIndex < commands.size() && sent < commandsPerTick.get()) {
            ExecutionStep step = commands.get(cmdIndex);

            if (step instanceof WaitStep wait) {
                scriptWaitTicks = wait.ticks();
                cmdIndex++;
                return;
            }

            if (step instanceof VerifyTeleportStep verify) {
                if (!verifyTeleport(verify.target())) {
                    abortCurrentOperation("Pre-teleport verification failed. Operation aborted.");
                    return;
                }
                cmdIndex++;
                continue;
            }

            if (step instanceof CommandStep command && mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand(command.command());
            }
            cmdIndex++;
            sent++;
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc.player == null) return;
        if (!isSelectionTool(mc.player.getMainHandItem())) return;

        event.cancel();
        pos1 = event.blockPos;
        info("Pos1 set to: " + formatPos(pos1) + selectionInfo());
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null) return;
        if (!isSelectionTool(mc.player.getMainHandItem())) return;

        event.cancel();
        pos2 = event.result.getBlockPos();
        info("Pos2 set to: " + formatPos(pos2) + selectionInfo());
    }

    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }

    public void setPos1(BlockPos p) {
        pos1 = p;
        info("Pos1 set to: " + formatPos(pos1) + selectionInfo());
    }

    public void setPos2(BlockPos p) {
        pos2 = p;
        info("Pos2 set to: " + formatPos(pos2) + selectionInfo());
    }

    public void processCommand(String args) {
        if (args == null) return;
        String[] parts = args.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;
        handleCommand(parts);
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String msg = event.message;
        String prefix;

        if (msg.startsWith(".we ")) prefix = ".we ";
        else if (msg.startsWith(".worldedit ")) prefix = ".worldedit ";
        else return;

        event.cancel();

        String[] parts = msg.substring(prefix.length()).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;
        handleCommand(parts);
    }

    private void handleCommand(String[] parts) {
        if (mc.player == null || mc.level == null) return;

        String action = parts[0].toLowerCase(Locale.ROOT);

        if (awaitingConfirmation) {
            if (action.equals("confirm") || action.equals("yes")) {
                awaitingConfirmation = false;
                OperationRequest confirmed = pendingConfirmRequest;
                pendingConfirmRequest = null;
                if (confirmed != null) executeRequest(confirmed);
                return;
            }
            if (action.equals("cancel") || action.equals("no")) {
                awaitingConfirmation = false;
                pendingConfirmRequest = null;
                info("Operation cancelled.");
                return;
            }
        }

        if (action.equals("confirm") || action.equals("cancel") || action.equals("yes") || action.equals("no")) {
            info("No pending confirmation.");
            return;
        }

        try {
            switch (action) {
                case "pos1", "p1" -> {
                    if (parts.length >= 4) pos1 = new BlockPos(parseInt(parts[1], 0), parseInt(parts[2], 0), parseInt(parts[3], 0));
                    else pos1 = mc.player.blockPosition();
                    info("Pos1 set to: " + formatPos(pos1) + selectionInfo());
                }
                case "pos2", "p2" -> {
                    if (parts.length >= 4) pos2 = new BlockPos(parseInt(parts[1], 0), parseInt(parts[2], 0), parseInt(parts[3], 0));
                    else pos2 = mc.player.blockPosition();
                    info("Pos2 set to: " + formatPos(pos2) + selectionInfo());
                }
                case "hpos1" -> {
                    BlockPos target = getCrosshairBlockPos();
                    if (target == null) {
                        error("No block targeted.");
                        return;
                    }
                    pos1 = target;
                    info("Pos1 set to looked-at block: " + formatPos(pos1) + selectionInfo());
                }
                case "hpos2" -> {
                    BlockPos target = getCrosshairBlockPos();
                    if (target == null) {
                        error("No block targeted.");
                        return;
                    }
                    pos2 = target;
                    info("Pos2 set to looked-at block: " + formatPos(pos2) + selectionInfo());
                }
                case "chunk" -> {
                    ChunkPos chunk = ChunkPos.containing(mc.player.blockPosition());
                    pos1 = new BlockPos(chunk.getMinBlockX(), mc.level.getMinY(), chunk.getMinBlockZ());
                    pos2 = new BlockPos(chunk.getMaxBlockX(), worldTopY(), chunk.getMaxBlockZ());
                    info("Selection set to current chunk: " + selectionInfo());
                }
                case "set" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we set <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    List<String> cmds = noiseMode.get() ? buildNoiseFillCommands(b.min, b.max) : buildFillCommands(b.min, b.max, block);
                    maybeExecute(new OperationRequest("set " + block, cmds, b.volume(), b, b.center(), true));
                }
                case "replace", "rep" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we replace <to> or .we replace <from> <to>");
                        return;
                    }
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    if (parts.length == 2) {
                        String to = parts[1];
                        maybeExecute(new OperationRequest("replace all -> " + to, buildFillCommands(b.min, b.max, to), b.volume(), b, b.center(), true));
                    } else {
                        String from = parts[1];
                        String to = parts[2];
                        maybeExecute(new OperationRequest("replace " + from + " -> " + to, buildReplaceCommands(b.min, b.max, from, to), b.volume(), b, b.center(), true));
                    }
                }
                case "walls" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we walls <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    maybeExecute(new OperationRequest("walls " + block, buildWallsCommands(b.min, b.max, block), b.volume(), b, b.center(), true));
                }
                case "outline", "faces" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we outline <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    maybeExecute(new OperationRequest("outline " + block, buildOutlineCommands(b.min, b.max, block), b.volume(), b, b.center(), true));
                }
                case "floor" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we floor <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    BlockPos min = getMin();
                    BlockPos max = getMax();
                    BlockPos floorMin = new BlockPos(min.getX(), min.getY(), min.getZ());
                    BlockPos floorMax = new BlockPos(max.getX(), min.getY(), max.getZ());
                    OperationBounds b = new OperationBounds(floorMin, floorMax);
                    maybeExecute(new OperationRequest("floor " + block, buildFillCommands(floorMin, floorMax, block), b.volume(), b, b.center(), true));
                }
                case "roof" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we roof <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    BlockPos min = getMin();
                    BlockPos max = getMax();
                    BlockPos roofMin = new BlockPos(min.getX(), max.getY(), min.getZ());
                    BlockPos roofMax = new BlockPos(max.getX(), max.getY(), max.getZ());
                    OperationBounds b = new OperationBounds(roofMin, roofMax);
                    maybeExecute(new OperationRequest("roof " + block, buildFillCommands(roofMin, roofMax, block), b.volume(), b, b.center(), true));
                }
                case "hollow" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we hollow <block> [thickness]");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int thickness = parts.length >= 3 ? parseInt(parts[2], 1) : 1;
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    maybeExecute(new OperationRequest("hollow " + block + " t=" + thickness, buildHollowCommands(b.min, b.max, block, thickness), b.volume(), b, b.center(), true));
                }
                case "sphere" -> {
                    if (parts.length < 3) {
                        error("Usage: .we sphere <block> <radius>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int r = parseInt(parts[2], 5);
                    BlockPos c = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(c.offset(-r, -r, -r), c.offset(r, r, r));
                    maybeExecute(new OperationRequest("sphere " + block + " r=" + r, buildSphereCommands(c, r, block), b.volume(), b, c, true));
                }
                case "hsphere" -> {
                    if (parts.length < 3) {
                        error("Usage: .we hsphere <block> <radius>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int r = parseInt(parts[2], 5);
                    BlockPos c = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(c.offset(-r, -r, -r), c.offset(r, r, r));
                    maybeExecute(new OperationRequest("hsphere " + block + " r=" + r, buildHollowSphereCommands(c, r, block), b.volume(), b, c, true));
                }
                case "cyl", "cylinder" -> {
                    if (parts.length < 4) {
                        error("Usage: .we cyl <block> <radius> <height>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int r = parseInt(parts[2], 5);
                    int h = parseInt(parts[3], 10);
                    BlockPos base = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(base.offset(-r, 0, -r), base.offset(r, h - 1, r));
                    maybeExecute(new OperationRequest("cyl " + block + " r=" + r + " h=" + h, buildCylinderCommands(base, r, h, block), b.volume(), b, b.center(), true));
                }
                case "hcyl" -> {
                    if (parts.length < 4) {
                        error("Usage: .we hcyl <block> <radius> <height>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int r = parseInt(parts[2], 5);
                    int h = parseInt(parts[3], 10);
                    BlockPos base = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(base.offset(-r, 0, -r), base.offset(r, h - 1, r));
                    maybeExecute(new OperationRequest("hcyl " + block + " r=" + r + " h=" + h, buildHollowCylinderCommands(base, r, h, block), b.volume(), b, b.center(), true));
                }
                case "pyramid" -> {
                    if (parts.length < 3) {
                        error("Usage: .we pyramid <block> <size>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int size = parseInt(parts[2], 5);
                    BlockPos base = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(base.offset(-size, 0, -size), base.offset(size, Math.max(0, size - 1), size));
                    maybeExecute(new OperationRequest("pyramid " + block + " s=" + size, buildPyramidCommands(base, size, block, false), b.volume(), b, b.center(), true));
                }
                case "hpyramid" -> {
                    if (parts.length < 3) {
                        error("Usage: .we hpyramid <block> <size>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    int size = parseInt(parts[2], 5);
                    BlockPos base = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(base.offset(-size, 0, -size), base.offset(size, Math.max(0, size - 1), size));
                    maybeExecute(new OperationRequest("hpyramid " + block + " s=" + size, buildPyramidCommands(base, size, block, true), b.volume(), b, b.center(), true));
                }
                case "cut" -> {
                    if (!requireSelection()) return;
                    clipboardMin = getMin();
                    clipboardMax = getMax();
                    clipboardOrigin = mc.player.blockPosition();
                    saveClipboard();
                    OperationBounds b = new OperationBounds(clipboardMin, clipboardMax);
                    maybeExecute(new OperationRequest("cut", buildFillCommands(b.min, b.max, "minecraft:air"), b.volume(), b, b.center(), true));
                    info("Cut to clipboard (" + b.volume() + " blocks).");
                }
                case "copy" -> {
                    if (!requireSelection()) return;
                    clipboardMin = getMin();
                    clipboardMax = getMax();
                    clipboardOrigin = mc.player.blockPosition();
                    saveClipboard();
                    info("Copied to clipboard (" + getVolume() + " blocks).");
                }
                case "paste" -> {
                    if (clipboardMin == null || clipboardMax == null || clipboardOrigin == null) {
                        error("Nothing in clipboard. Use .we copy or .we cut first.");
                        return;
                    }
                    BlockPos offset = mc.player.blockPosition();
                    int dx = offset.getX() - clipboardOrigin.getX();
                    int dy = offset.getY() - clipboardOrigin.getY();
                    int dz = offset.getZ() - clipboardOrigin.getZ();
                    BlockPos dstMin = clipboardMin.offset(dx, dy, dz);
                    BlockPos dstMax = clipboardMax.offset(dx, dy, dz);
                    OperationBounds b = new OperationBounds(dstMin, dstMax);
                    List<String> cmds = buildCloneCommandsChunked(clipboardMin, clipboardMax, dstMin, true, false);
                    maybeExecute(new OperationRequest("paste", cmds, b.volume(), b, b.center(), true));
                    info("Pasted clipboard with offset (" + dx + ", " + dy + ", " + dz + ").");
                }
                case "flip" -> {
                    if (!requireSelection()) return;
                    String dir = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : getFacingDirection();
                    int axis = getFlipAxis(dir);
                    if (axis < 0) {
                        error("Usage: .we flip [north|south|east|west|up|down]");
                        return;
                    }
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    List<String> cmds = buildFlipCommands(b.min, b.max, axis);
                    if (cmds.isEmpty()) return;
                    maybeExecute(new OperationRequest("flip " + dir, cmds, b.volume(), b, b.center(), true));
                }
                case "stack" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we stack <count> [direction]");
                        return;
                    }
                    int count = parseInt(parts[1], 1);
                    String dir = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : getFacingDirection();
                    BlockPos min = getMin();
                    BlockPos max = getMax();
                    OperationBounds b = computeStackBounds(min, max, count, dir);
                    maybeExecute(new OperationRequest("stack " + count + " " + dir, buildStackCommands(min, max, count, dir), b == null ? -1 : b.volume(), b, b == null ? mc.player.blockPosition() : b.center(), true));
                }
                case "move" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we move <distance> [direction]");
                        return;
                    }
                    int dist = parseInt(parts[1], 1);
                    String dir = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : getFacingDirection();
                    BlockPos min = getMin();
                    BlockPos max = getMax();
                    OperationBounds b = computeMoveBounds(min, max, dist, dir);
                    maybeExecute(new OperationRequest("move " + dist + " " + dir, buildMoveCommands(min, max, dist, dir), b == null ? -1 : b.volume(), b, b == null ? mc.player.blockPosition() : b.center(), true));
                }
                case "drain" -> {
                    int r = parts.length >= 2 ? parseInt(parts[1], 10) : 10;
                    BlockPos c = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(c.offset(-r, -r, -r), c.offset(r, r, r));
                    List<String> cmds = new ArrayList<>();
                    cmds.addAll(buildReplaceCommands(b.min, b.max, "minecraft:water", "minecraft:air"));
                    cmds.addAll(buildReplaceCommands(b.min, b.max, "minecraft:lava", "minecraft:air"));
                    cmds.addAll(buildReplaceCommands(b.min, b.max, "minecraft:kelp", "minecraft:air"));
                    cmds.addAll(buildReplaceCommands(b.min, b.max, "minecraft:seagrass", "minecraft:air"));
                    cmds.addAll(buildReplaceCommands(b.min, b.max, "minecraft:tall_seagrass", "minecraft:air"));
                    maybeExecute(new OperationRequest("drain r=" + r, cmds, b.volume(), b, c, true));
                }
                case "replacenear", "repnear" -> {
                    if (parts.length < 4) {
                        error("Usage: .we replacenear <radius> <from> <to>");
                        return;
                    }
                    int r = parseInt(parts[1], 10);
                    String from = parts[2];
                    String to = parts[3];
                    BlockPos c = mc.player.blockPosition();
                    OperationBounds b = new OperationBounds(c.offset(-r, -r, -r), c.offset(r, r, r));
                    maybeExecute(new OperationRequest("replacenear " + from + " -> " + to + " r=" + r, buildReplaceCommands(b.min, b.max, from, to), b.volume(), b, c, true));
                }
                case "line" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we line <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    OperationBounds b = new OperationBounds(pos1, pos2);
                    maybeExecute(new OperationRequest("line " + block, buildLineCommands(pos1, pos2, block), b.volume(), b, b.center(), true));
                }
                case "center", "middle" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we center <block>");
                        return;
                    }
                    String block = parts[1];
                    if (parseBlockPattern(block) == null) return;
                    String centerBlock = pickPatternBlock(block);
                    if (centerBlock == null) return;

                    BlockPos min = getMin();
                    BlockPos max = getMax();
                    BlockPos c = new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
                    List<String> cmds = new ArrayList<>();
                    cmds.add(String.format("setblock %d %d %d %s", c.getX(), c.getY(), c.getZ(), centerBlock));
                    OperationBounds b = new OperationBounds(c, c);
                    maybeExecute(new OperationRequest("center " + block, cmds, 1, b, c, true));
                }
                case "size", "sel" -> {
                    if (!requireSelection()) return;
                    BlockPos min = getMin();
                    BlockPos max = getMax();
                    int sx = max.getX() - min.getX() + 1;
                    int sy = max.getY() - min.getY() + 1;
                    int sz = max.getZ() - min.getZ() + 1;
                    info(String.format("Selection: %dx%dx%d = %d blocks", sx, sy, sz, sx * sy * sz));
                    info(String.format("Min: %s  Max: %s", formatPos(min), formatPos(max)));
                }
                case "count" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we count <block>");
                        return;
                    }
                    String normalized = normalizeBlock(parts[1]);
                    Identifier id = parseBlockIdentifier(normalized);
                    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                        error("Invalid block: " + parts[1]);
                        return;
                    }
                    OperationBounds b = new OperationBounds(getMin(), getMax());
                    if (!isBoundsLoaded(b)) {
                        warning("Selection includes unloaded chunks. Counting requires loaded bounds.");
                        return;
                    }
                    info("Counted " + countBlocksInBounds(b, id) + " block(s) of " + id + " in selection.");
                }
                case "clear", "desel" -> {
                    pos1 = null;
                    pos2 = null;
                    info("Selection cleared.");
                }
                case "expand" -> {
                    if (!requireSelection()) return;
                    if (parts.length >= 2 && parts[1].equalsIgnoreCase("vert")) {
                        expandSelectionVertical();
                        return;
                    }
                    if (parts.length < 2) {
                        error("Usage: .we expand <amount> [direction] or .we expand vert");
                        return;
                    }
                    int amt = parseInt(parts[1], 5);
                    String dir = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : getFacingDirection();
                    expandSelection(amt, dir);
                }
                case "contract" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we contract <amount> [direction]");
                        return;
                    }
                    int amt = parseInt(parts[1], 5);
                    String dir = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : getFacingDirection();
                    expandSelection(-amt, dir);
                }
                case "shift" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we shift <amount> [direction]");
                        return;
                    }
                    int amt = parseInt(parts[1], 5);
                    String dir = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : getFacingDirection();
                    shiftSelection(amt, dir);
                }
                case "inset" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we inset <amount>");
                        return;
                    }
                    insetSelection(parseInt(parts[1], 1));
                }
                case "outset" -> {
                    if (!requireSelection()) return;
                    if (parts.length < 2) {
                        error("Usage: .we outset <amount>");
                        return;
                    }
                    outsetSelection(parseInt(parts[1], 1));
                }
                case "ascend" -> ascend(parts.length >= 2 ? Math.max(1, parseInt(parts[1], 1)) : 1);
                case "descend" -> descend(parts.length >= 2 ? Math.max(1, parseInt(parts[1], 1)) : 1);
                case "ceiling" -> teleportToCeiling(parts.length >= 2 ? Math.max(0, parseInt(parts[1], 0)) : 0);
                case "thru" -> attemptThru();
                case "tool" -> {
                    if (parts.length == 1) {
                        info("Current selection tool: " + getSelectionToolItemId());
                        return;
                    }
                    if (setSelectionTool(parts[1]) && autoGiveWand.get() && mc.player != null && mc.player.getAbilities().instabuild) ensureSelectionToolInHotbar();
                }
                case "undo" -> performUndo(parts.length >= 2 ? Math.max(1, parseInt(parts[1], 1)) : 1);
                case "redo" -> performRedo(parts.length >= 2 ? Math.max(1, parseInt(parts[1], 1)) : 1);
                case "help" -> printHelp();
                case "saveclipboard" -> {
                    if (clipboardMin == null || clipboardMax == null || clipboardOrigin == null) {
                        error("Nothing in clipboard to save.");
                        return;
                    }
                    saveClipboard();
                    info("Clipboard saved.");
                }
                case "loadclipboard" -> {
                    loadClipboard();
                    if (clipboardMin != null) info("Clipboard loaded.");
                    else warning("No saved clipboard found.");
                }
                default -> error("Unknown command: " + action + ". Type .we help.");
            }
        } catch (Exception e) {
            error("WorldEdit error: " + e.getMessage());
        }
    }

    private void performUndo(int requestedCount) {
        if (pendingCommands != null) {
            warning("An operation is already running.");
            return;
        }
        if (undoStack.isEmpty()) {
            info("Nothing to undo.");
            return;
        }

        int count = Math.min(requestedCount, undoStack.size());
        List<String> cmds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HistoryEntry entry = undoStack.pop();
            cmds.addAll(entry.undoCommands);
            redoStack.push(entry);
        }
        startExecution(cmds, "undo x" + count, null);
        info("Undo queued: " + count + " operation(s).");
    }

    private void performRedo(int requestedCount) {
        if (pendingCommands != null) {
            warning("An operation is already running.");
            return;
        }
        if (redoStack.isEmpty()) {
            info("Nothing to redo.");
            return;
        }

        int count = Math.min(requestedCount, redoStack.size());
        List<String> cmds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HistoryEntry entry = redoStack.pop();
            cmds.addAll(entry.redoCommands);
            pushUndoEntry(entry);
        }
        startExecution(cmds, "redo x" + count, null);
        info("Redo queued: " + count + " operation(s).");
    }

    private void maybeExecute(OperationRequest request) {
        if (request == null || request.commands == null || request.commands.isEmpty()) {
            warning("No commands to execute.");
            return;
        }

        if (request.commands.size() > maxGeneratedCommands.get()) {
            warning("Operation rejected: %d generated commands exceeds the %d command budget.",
                request.commands.size(), maxGeneratedCommands.get());
            return;
        }

        if (showBlockCount.get() && request.blockCount > 0) {
            info(String.format("%s: %d blocks, %d commands", request.name, request.blockCount, request.commands.size()));
        }

        if (confirmLargeOps.get() && request.blockCount > largeOpThreshold.get()) {
            awaitingConfirmation = true;
            pendingConfirmRequest = request;
            warning(String.format("Large operation: %d blocks. Type .we confirm or .we cancel.", request.blockCount));
            return;
        }

        executeRequest(request);
    }

    private void executeRequest(OperationRequest request) {
        if (pendingCommands != null) {
            warning("Another operation is already running.");
            return;
        }

        HistoryEntry historyEntry = null;
        if (request.recordHistory && request.bounds != null) {
            List<String> undoCommands = createUndoSnapshotCommands(request.bounds);
            if (!undoCommands.isEmpty()) historyEntry = new HistoryEntry(request.name, undoCommands, new ArrayList<>(request.commands));
            else warning("Undo snapshot could not be captured for this operation.");
        }

        List<ExecutionStep> wrapped = wrapCommandsForZoneSafety(request.commands, request.targetCenter, request.bounds);
        startTypedExecution(wrapped, request.name, historyEntry);
    }

    private void startExecution(List<String> commands, String name, HistoryEntry historyEntry) {
        if (commands == null) {
            startTypedExecution(List.of(), name, historyEntry);
            return;
        }
        List<ExecutionStep> steps = new ArrayList<>(commands.size());
        for (String command : commands) steps.add(new CommandStep(command));
        startTypedExecution(steps, name, historyEntry);
    }

    private void startTypedExecution(List<ExecutionStep> commands, String name, HistoryEntry historyEntry) {
        if (commands == null || commands.isEmpty()) {
            warning("No commands to execute.");
            return;
        }

        pendingCommands = commands;
        cmdIndex = 0;
        delayCounter = 0;
        scriptWaitTicks = 0;
        operationStartTime = System.currentTimeMillis();
        currentOperationName = name;
        pendingHistoryEntry = historyEntry;

        if (showProgress.get()) info(String.format("Executing operation: %s (%d commands)", name, commands.size()));
    }

    private void abortCurrentOperation(String reason) {
        warning(reason);
        clearExecutionState();
    }

    private void clearExecutionState() {
        pendingCommands = null;
        cmdIndex = 0;
        delayCounter = 0;
        scriptWaitTicks = 0;
        currentOperationName = null;
        pendingHistoryEntry = null;
    }
    private boolean verifyTeleport(BlockPos target) {
        double distSq = mc.player.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        return !abortIfZoneTpFails.get() || distSq <= 100;
    }

    private List<ExecutionStep> wrapCommandsForZoneSafety(List<String> commands, BlockPos targetCenter, OperationBounds targetBounds) {
        List<ExecutionStep> wrapped = new ArrayList<>();
        if (commands == null || commands.isEmpty()) return wrapped;

        if (!preventUnloadedZoneError.get() || mc.player == null || mc.level == null) {
            for (String command : commands) wrapped.add(new CommandStep(command));
            return wrapped;
        }

        List<BlockPos> preloadTargets = collectZonePreloadTargets(targetBounds, targetCenter);
        if (preloadTargets.isEmpty()) {
            for (String command : commands) wrapped.add(new CommandStep(command));
            return wrapped;
        }

        BlockPos origin = mc.player.blockPosition();
        for (BlockPos target : preloadTargets) {
            wrapped.add(new CommandStep(formatTeleportCommand(target)));
            int waitTicks = Math.max(0, zoneTpDelay.get());
            if (waitTicks > 0) wrapped.add(new WaitStep(waitTicks));
            if (abortIfZoneTpFails.get()) wrapped.add(new VerifyTeleportStep(target.immutable()));
        }

        for (String command : commands) wrapped.add(new CommandStep(command));
        if (autoReturnAfterZoneTp.get()) wrapped.add(new CommandStep(formatTeleportCommand(origin)));

        return wrapped;
    }

    private sealed interface ExecutionStep permits CommandStep, WaitStep, VerifyTeleportStep {}
    private record CommandStep(String command) implements ExecutionStep {}
    private record WaitStep(int ticks) implements ExecutionStep {
        private WaitStep { ticks = Math.max(0, ticks); }
    }
    private record VerifyTeleportStep(BlockPos target) implements ExecutionStep {}

    private List<BlockPos> collectZonePreloadTargets(OperationBounds bounds, BlockPos targetCenter) {
        List<BlockPos> targets = new ArrayList<>();
        if (mc.level == null || mc.player == null) return targets;

        int y = clampTpY(targetCenter != null ? targetCenter.getY() : mc.player.getBlockY());

        if (bounds == null) {
            BlockPos center = targetCenter != null ? targetCenter : mc.player.blockPosition();
            if (!isChunkLoaded(center)) targets.add(new BlockPos(center.getX(), y, center.getZ()));
            return targets;
        }

        if (isBoundsLoaded(bounds)) return targets;

        int minChunkX = bounds.min.getX() >> 4;
        int maxChunkX = bounds.max.getX() >> 4;
        int minChunkZ = bounds.min.getZ() >> 4;
        int maxChunkZ = bounds.max.getZ() >> 4;
        int centerChunkX = (minChunkX + maxChunkX) / 2;
        int centerChunkZ = (minChunkZ + maxChunkZ) / 2;

        addUnloadedChunkTarget(targets, centerChunkX, centerChunkZ, y);
        addUnloadedChunkTarget(targets, minChunkX, minChunkZ, y);
        addUnloadedChunkTarget(targets, maxChunkX, minChunkZ, y);
        addUnloadedChunkTarget(targets, minChunkX, maxChunkZ, y);
        addUnloadedChunkTarget(targets, maxChunkX, maxChunkZ, y);

        if (targets.isEmpty() && targetCenter != null && !isChunkLoaded(targetCenter)) targets.add(new BlockPos(targetCenter.getX(), y, targetCenter.getZ()));
        return targets;
    }

    private int clampTpY(int y) {
        if (mc.level == null) return y;
        int minY = mc.level.getMinY() + 2;
        int maxY = worldTopY() - 2;
        return Math.max(minY, Math.min(maxY, y));
    }

    private void addUnloadedChunkTarget(List<BlockPos> targets, int chunkX, int chunkZ, int y) {
        if (mc.level == null || mc.level.getChunkSource() == null) return;
        if (mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) return;

        BlockPos pos = new BlockPos((chunkX << 4) + 8, y, (chunkZ << 4) + 8);
        for (BlockPos existing : targets) {
            if (existing.getX() == pos.getX() && existing.getZ() == pos.getZ()) return;
        }
        targets.add(pos);
    }

    private List<String> createUndoSnapshotCommands(OperationBounds bounds) {
        List<String> cmds = new ArrayList<>();
        if (mc.level == null || bounds == null) return cmds;
        if (!isBoundsLoaded(bounds)) {
            warning("Cannot capture undo snapshot: bounds include unloaded chunks.");
            return cmds;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = bounds.min.getY(); y <= bounds.max.getY(); y++) {
            for (int z = bounds.min.getZ(); z <= bounds.max.getZ(); z++) {
                int x = bounds.min.getX();
                while (x <= bounds.max.getX()) {
                    mutable.set(x, y, z);
                    String state = toCommandBlockState(mc.level.getBlockState(mutable));
                    int startX = x;
                    x++;

                    while (x <= bounds.max.getX()) {
                        mutable.set(x, y, z);
                        String next = toCommandBlockState(mc.level.getBlockState(mutable));
                        if (!state.equals(next)) break;
                        x++;
                    }

                    int endX = x - 1;
                    if (endX > startX) cmds.add(String.format("fill %d %d %d %d %d %d %s", startX, y, z, endX, y, z, state));
                    else cmds.add(String.format("setblock %d %d %d %s", startX, y, z, state));
                }
            }
        }

        return cmds;
    }

    private String toCommandBlockState(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        StringBuilder sb = new StringBuilder(id.toString());

        Map<Property<?>, Comparable<?>> entries = new HashMap<>();
        state.getValues().forEach(v -> entries.put(v.property(), v.value()));
        if (!entries.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<Property<?>, Comparable<?>> entry : entries.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(entry.getKey().getName()).append('=').append(getPropertyName(entry.getKey(), entry.getValue()));
            }
            sb.append(']');
        }

        return sb.toString();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String getPropertyName(Property<?> property, Comparable<?> value) {
        return ((Property) property).getName((Comparable) value);
    }

    private void expandSelection(int amount, String dir) {
        if (pos1 == null || pos2 == null) return;

        BlockPos min = getMin();
        BlockPos max = getMax();

        switch (dir) {
            case "north", "n" -> pos1 = new BlockPos(min.getX(), min.getY(), min.getZ() - amount);
            case "south", "s" -> pos2 = new BlockPos(max.getX(), max.getY(), max.getZ() + amount);
            case "east", "e" -> pos2 = new BlockPos(max.getX() + amount, max.getY(), max.getZ());
            case "west", "w" -> pos1 = new BlockPos(min.getX() - amount, min.getY(), min.getZ());
            case "up", "u" -> pos2 = new BlockPos(max.getX(), max.getY() + amount, max.getZ());
            case "down", "d" -> pos1 = new BlockPos(min.getX(), min.getY() - amount, min.getZ());
            default -> {
                error("Invalid direction: " + dir);
                return;
            }
        }

        info("Selection expanded " + amount + " " + dir + selectionInfo());
    }

    private void expandSelectionVertical() {
        if (!requireSelection()) return;
        BlockPos min = getMin();
        BlockPos max = getMax();
        pos1 = new BlockPos(min.getX(), mc.level.getMinY(), min.getZ());
        pos2 = new BlockPos(max.getX(), worldTopY(), max.getZ());
        info("Selection expanded vertically" + selectionInfo());
    }

    private void shiftSelection(int amount, String dir) {
        if (pos1 == null || pos2 == null) return;

        int ox = 0;
        int oy = 0;
        int oz = 0;

        switch (dir) {
            case "north", "n" -> oz = -amount;
            case "south", "s" -> oz = amount;
            case "east", "e" -> ox = amount;
            case "west", "w" -> ox = -amount;
            case "up", "u" -> oy = amount;
            case "down", "d" -> oy = -amount;
            default -> {
                error("Invalid direction: " + dir);
                return;
            }
        }

        pos1 = pos1.offset(ox, oy, oz);
        pos2 = pos2.offset(ox, oy, oz);
        info("Selection shifted " + amount + " " + dir + selectionInfo());
    }

    private void insetSelection(int amount) {
        BlockPos min = getMin();
        BlockPos max = getMax();
        BlockPos newMin = min.offset(amount, amount, amount);
        BlockPos newMax = max.offset(-amount, -amount, -amount);

        if (newMin.getX() > newMax.getX() || newMin.getY() > newMax.getY() || newMin.getZ() > newMax.getZ()) {
            error("Inset amount is too large for current selection.");
            return;
        }

        pos1 = newMin;
        pos2 = newMax;
        info("Selection inset by " + amount + selectionInfo());
    }

    private void outsetSelection(int amount) {
        BlockPos min = getMin();
        BlockPos max = getMax();
        pos1 = min.offset(-amount, -amount, -amount);
        pos2 = max.offset(amount, amount, amount);
        info("Selection outset by " + amount + selectionInfo());
    }

    private String getFacingDirection() {
        if (mc.player == null) return "north";

        float yaw = mc.player.getYRot() % 360;
        if (yaw < 0) yaw += 360;

        if (yaw >= 315 || yaw < 45) return "south";
        if (yaw >= 45 && yaw < 135) return "west";
        if (yaw >= 135 && yaw < 225) return "north";
        return "east";
    }

    private void ascend(int levels) {
        BlockPos cursor = mc.player.blockPosition();
        int moved = 0;
        for (int i = 0; i < levels; i++) {
            BlockPos next = findStandableAbove(cursor);
            if (next == null) break;
            cursor = next;
            moved++;
        }
        if (moved == 0) {
            warning("Could not find a standable position above.");
            return;
        }
        queueSingleCommand(formatTeleportCommand(cursor));
        info("Ascend queued by " + moved + " level(s).");
    }

    private void descend(int levels) {
        BlockPos cursor = mc.player.blockPosition();
        int moved = 0;
        for (int i = 0; i < levels; i++) {
            BlockPos next = findStandableBelow(cursor);
            if (next == null) break;
            cursor = next;
            moved++;
        }
        if (moved == 0) {
            warning("Could not find a standable position below.");
            return;
        }
        queueSingleCommand(formatTeleportCommand(cursor));
        info("Descend queued by " + moved + " level(s).");
    }

    private void teleportToCeiling(int clearance) {
        BlockPos playerPos = mc.player.blockPosition();
        int topY = worldTopY();

        for (int y = playerPos.getY() + 1; y <= topY; y++) {
            BlockPos ceilingBlock = new BlockPos(playerPos.getX(), y, playerPos.getZ());
            if (!mc.level.getBlockState(ceilingBlock).isAir()) {
                int targetFeetY = y - clearance - 2;
                BlockPos target = new BlockPos(playerPos.getX(), targetFeetY, playerPos.getZ());
                if (canStandAt(target)) {
                    queueSingleCommand(formatTeleportCommand(target));
                    info("Ceiling teleport queued to " + formatPos(target) + ".");
                } else {
                    warning("No standable spot found below the ceiling with requested clearance.");
                }
                return;
            }
        }

        warning("No ceiling found above player.");
    }

    private void attemptThru() {
        BlockPos start = mc.player.blockPosition();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        boolean hitWall = false;
        for (int i = 1; i <= 12; i++) {
            int x = (int) Math.floor(mc.player.getX() + dirX * i);
            int z = (int) Math.floor(mc.player.getZ() + dirZ * i);
            BlockPos candidate = new BlockPos(x, start.getY(), z);

            if (!canStandAt(candidate)) {
                hitWall = true;
                continue;
            }

            if (hitWall) {
                queueSingleCommand(formatTeleportCommand(candidate));
                info("Thru teleport queued to " + formatPos(candidate) + ".");
                return;
            }
        }

        warning("Could not find a valid thru destination.");
    }

    private BlockPos findStandableAbove(BlockPos origin) {
        int topY = worldTopY() - 1;
        for (int y = origin.getY() + 1; y <= topY; y++) {
            BlockPos candidate = new BlockPos(origin.getX(), y, origin.getZ());
            if (canStandAt(candidate)) return candidate;
        }
        return null;
    }

    private BlockPos findStandableBelow(BlockPos origin) {
        int minY = mc.level.getMinY() + 1;
        for (int y = origin.getY() - 1; y >= minY; y--) {
            BlockPos candidate = new BlockPos(origin.getX(), y, origin.getZ());
            if (canStandAt(candidate)) return candidate;
        }
        return null;
    }

    private boolean canStandAt(BlockPos feet) {
        if (mc.level == null) return false;
        if (!isChunkLoaded(feet)) return false;

        BlockState feetState = mc.level.getBlockState(feet);
        BlockState headState = mc.level.getBlockState(feet.above());
        BlockState floorState = mc.level.getBlockState(feet.below());

        return feetState.isAir() && headState.isAir() && floorState.isSolid();
    }
    private Identifier normalizeItemIdentifier(String raw) {
        if (raw == null) return null;

        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;
        if (!value.contains(":")) value = "minecraft:" + value;
        return Identifier.tryParse(value);
    }

    private Item resolveSelectionToolItem(boolean warn) {
        Identifier id = normalizeItemIdentifier(selectionToolItem.get());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            if (warn && !warnedInvalidToolItem) {
                warning("Invalid selection-tool-item: " + selectionToolItem.get() + ". Falling back to minecraft:stone_axe.");
                warnedInvalidToolItem = true;
            }
            return Items.STONE_AXE;
        }

        warnedInvalidToolItem = false;
        return BuiltInRegistries.ITEM.getValue(id);
    }

    private boolean setSelectionTool(String raw) {
        Identifier id = normalizeItemIdentifier(raw);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            error("Invalid item ID: " + raw);
            return false;
        }

        selectionToolItem.set(id.toString());
        warnedInvalidToolItem = false;
        info("Selection tool set to " + id);
        return true;
    }

    private String getSelectionToolItemId() {
        return BuiltInRegistries.ITEM.getKey(resolveSelectionToolItem(false)).toString();
    }

    private boolean isSelectionTool(ItemStack stack) {
        return stack != null && stack.getItem() == resolveSelectionToolItem(false);
    }

    private void ensureSelectionToolInHotbar() {
        if (mc.player == null || mc.player.connection == null || mc.player.getInventory() == null) return;

        Item tool = resolveSelectionToolItem(true);
        int slot = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getItem(slot).getItem() == tool) return;

        ItemStack toolStack = new ItemStack(tool, 1);
        mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(36 + slot, toolStack));
        mc.player.getInventory().setItem(slot, toolStack);
        info("Gave selection tool: " + BuiltInRegistries.ITEM.getId(tool));
    }

    private List<String> buildFillCommands(BlockPos min, BlockPos max, String blockSpec) {
        List<String> cmds = new ArrayList<>();
        BlockPattern pattern = parseBlockPattern(blockSpec);
        if (pattern == null) return cmds;

        if (pattern.isSingleBlock()) {
            int cs = fillChunkSize.get();
            String block = pattern.singleBlock();

            for (int x = min.getX(); x <= max.getX(); x += cs) {
                for (int y = min.getY(); y <= max.getY(); y += cs) {
                    for (int z = min.getZ(); z <= max.getZ(); z += cs) {
                        int x2 = Math.min(x + cs - 1, max.getX());
                        int y2 = Math.min(y + cs - 1, max.getY());
                        int z2 = Math.min(z + cs - 1, max.getZ());
                        cmds.add(String.format("fill %d %d %d %d %d %d %s", x, y, z, x2, y2, z2, block));
                    }
                }
            }

            return cmds;
        }

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                addPatternSpanCommands(cmds, min.getX(), max.getX(), y, z, pattern);
            }
        }

        return cmds;
    }

    private List<String> buildNoiseFillCommands(BlockPos min, BlockPos max) {
        List<String> cmds = new ArrayList<>();
        String[] blocks = noiseBlocks.get().split(",");
        if (blocks.length == 0) return cmds;

        for (int i = 0; i < blocks.length; i++) blocks[i] = normalizeBlock(blocks[i].trim());

        cmds.addAll(buildFillCommands(min, max, blocks[0]));
        for (int i = 1; i < blocks.length; i++) cmds.addAll(buildReplaceCommands(min, max, blocks[0], blocks[i]));

        return cmds;
    }

    private List<String> buildReplaceCommands(BlockPos min, BlockPos max, String fromSpec, String toSpec) {
        List<String> cmds = new ArrayList<>();

        if (isPatternExpression(fromSpec)) {
            error("Replace source must be a single block, not a pattern.");
            return cmds;
        }

        String from = normalizeSingleBlock(fromSpec);
        Identifier fromId = parseBlockIdentifier(from);
        if (fromId == null || !BuiltInRegistries.BLOCK.containsKey(fromId)) {
            error("Invalid source block for replace: " + fromSpec);
            return cmds;
        }

        BlockPattern toPattern = parseBlockPattern(toSpec);
        if (toPattern == null) return cmds;

        if (toPattern.isSingleBlock()) {
            int cs = fillChunkSize.get();
            String to = toPattern.singleBlock();

            for (int x = min.getX(); x <= max.getX(); x += cs) {
                for (int y = min.getY(); y <= max.getY(); y += cs) {
                    for (int z = min.getZ(); z <= max.getZ(); z += cs) {
                        int x2 = Math.min(x + cs - 1, max.getX());
                        int y2 = Math.min(y + cs - 1, max.getY());
                        int z2 = Math.min(z + cs - 1, max.getZ());
                        cmds.add(String.format("fill %d %d %d %d %d %d %s replace %s", x, y, z, x2, y2, z2, to, from));
                    }
                }
            }

            return cmds;
        }

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    String to = toPattern.randomBlock();
                    cmds.add(String.format("execute if block %d %d %d %s run setblock %d %d %d %s", x, y, z, from, x, y, z, to));
                }
            }
        }

        return cmds;
    }

    private List<String> buildWallsCommands(BlockPos min, BlockPos max, String block) {
        List<String> cmds = new ArrayList<>();
        cmds.addAll(buildFillCommands(new BlockPos(min.getX(), min.getY(), min.getZ()), new BlockPos(min.getX(), max.getY(), max.getZ()), block));
        cmds.addAll(buildFillCommands(new BlockPos(max.getX(), min.getY(), min.getZ()), new BlockPos(max.getX(), max.getY(), max.getZ()), block));
        cmds.addAll(buildFillCommands(new BlockPos(min.getX(), min.getY(), min.getZ()), new BlockPos(max.getX(), max.getY(), min.getZ()), block));
        cmds.addAll(buildFillCommands(new BlockPos(min.getX(), min.getY(), max.getZ()), new BlockPos(max.getX(), max.getY(), max.getZ()), block));
        return cmds;
    }

    private List<String> buildOutlineCommands(BlockPos min, BlockPos max, String block) {
        List<String> cmds = new ArrayList<>();
        cmds.addAll(buildWallsCommands(min, max, block));
        cmds.addAll(buildFillCommands(new BlockPos(min.getX(), min.getY(), min.getZ()), new BlockPos(max.getX(), min.getY(), max.getZ()), block));
        cmds.addAll(buildFillCommands(new BlockPos(min.getX(), max.getY(), min.getZ()), new BlockPos(max.getX(), max.getY(), max.getZ()), block));
        return cmds;
    }

    private List<String> buildHollowCommands(BlockPos min, BlockPos max, String block, int thickness) {
        List<String> cmds = new ArrayList<>();
        cmds.addAll(buildFillCommands(min, max, block));

        BlockPos innerMin = min.offset(thickness, thickness, thickness);
        BlockPos innerMax = max.offset(-thickness, -thickness, -thickness);
        if (innerMin.getX() <= innerMax.getX() && innerMin.getY() <= innerMax.getY() && innerMin.getZ() <= innerMax.getZ()) {
            cmds.addAll(buildFillCommands(innerMin, innerMax, "minecraft:air"));
        }

        return cmds;
    }

    private List<String> buildSphereCommands(BlockPos center, int radius, String blockSpec) {
        List<String> cmds = new ArrayList<>();
        BlockPattern pattern = parseBlockPattern(blockSpec);
        if (pattern == null || radius < 0) return cmds;

        if (radius == 0) {
            cmds.add(String.format("setblock %d %d %d %s", center.getX(), center.getY(), center.getZ(), pattern.randomBlock()));
            return cmds;
        }

        int r2 = radius * radius;
        boolean single = pattern.isSingleBlock();
        String singleBlock = single ? pattern.singleBlock() : null;

        for (int y = -radius; y <= radius; y++) {
            int y2 = y * y;
            for (int z = -radius; z <= radius; z++) {
                int z2 = z * z;
                int remaining = r2 - y2 - z2;
                if (remaining < 0) continue;

                int xSpan = (int) Math.floor(Math.sqrt(remaining));
                int x1 = center.getX() - xSpan;
                int x2 = center.getX() + xSpan;
                int by = center.getY() + y;
                int bz = center.getZ() + z;

                if (single) cmds.add(String.format("fill %d %d %d %d %d %d %s", x1, by, bz, x2, by, bz, singleBlock));
                else addPatternSpanCommands(cmds, x1, x2, by, bz, pattern);
            }
        }

        return cmds;
    }

    private List<String> buildHollowSphereCommands(BlockPos center, int radius, String block) {
        List<String> cmds = new ArrayList<>();
        cmds.addAll(buildSphereCommands(center, radius, block));
        if (radius > 1) cmds.addAll(buildSphereCommands(center, radius - 1, "minecraft:air"));
        return cmds;
    }

    private List<String> buildCylinderCommands(BlockPos base, int radius, int height, String blockSpec) {
        List<String> cmds = new ArrayList<>();
        BlockPattern pattern = parseBlockPattern(blockSpec);
        if (pattern == null || radius < 0 || height <= 0) return cmds;

        if (radius == 0) {
            int x = base.getX();
            int z = base.getZ();
            for (int y = 0; y < height; y++) {
                cmds.add(String.format("setblock %d %d %d %s", x, base.getY() + y, z, pattern.randomBlock()));
            }
            return cmds;
        }

        int r2 = radius * radius;
        boolean single = pattern.isSingleBlock();
        String singleBlock = single ? pattern.singleBlock() : null;

        for (int y = 0; y < height; y++) {
            int by = base.getY() + y;
            for (int z = -radius; z <= radius; z++) {
                int z2 = z * z;
                int remaining = r2 - z2;
                if (remaining < 0) continue;

                int xSpan = (int) Math.floor(Math.sqrt(remaining));
                int x1 = base.getX() - xSpan;
                int x2 = base.getX() + xSpan;
                int bz = base.getZ() + z;

                if (single) cmds.add(String.format("fill %d %d %d %d %d %d %s", x1, by, bz, x2, by, bz, singleBlock));
                else addPatternSpanCommands(cmds, x1, x2, by, bz, pattern);
            }
        }

        return cmds;
    }

    private List<String> buildHollowCylinderCommands(BlockPos base, int radius, int height, String block) {
        List<String> cmds = new ArrayList<>();
        cmds.addAll(buildCylinderCommands(base, radius, height, block));
        if (radius > 1) cmds.addAll(buildCylinderCommands(base, radius - 1, height, "minecraft:air"));
        return cmds;
    }

    private List<String> buildStackCommands(BlockPos min, BlockPos max, int count, String dir) {
        List<String> cmds = new ArrayList<>();
        int dx = max.getX() - min.getX() + 1;
        int dy = max.getY() - min.getY() + 1;
        int dz = max.getZ() - min.getZ() + 1;

        for (int i = 1; i <= count; i++) {
            int ox = 0;
            int oy = 0;
            int oz = 0;

            switch (dir) {
                case "north", "n" -> oz = -dz * i;
                case "south", "s" -> oz = dz * i;
                case "east", "e" -> ox = dx * i;
                case "west", "w" -> ox = -dx * i;
                case "up", "u" -> oy = dy * i;
                case "down", "d" -> oy = -dy * i;
                default -> oz = dz * i;
            }

            cmds.addAll(buildCloneCommandsChunked(min, max, min.offset(ox, oy, oz), true, false));
        }

        return cmds;
    }

    private List<String> buildCloneCommandsChunked(BlockPos srcMin, BlockPos srcMax, BlockPos dstMin, boolean forceMode, boolean moveMode) {
        List<String> cmds = new ArrayList<>();
        int cs = fillChunkSize.get();

        for (int x = srcMin.getX(); x <= srcMax.getX(); x += cs) {
            for (int y = srcMin.getY(); y <= srcMax.getY(); y += cs) {
                for (int z = srcMin.getZ(); z <= srcMax.getZ(); z += cs) {
                    int x2 = Math.min(x + cs - 1, srcMax.getX());
                    int y2 = Math.min(y + cs - 1, srcMax.getY());
                    int z2 = Math.min(z + cs - 1, srcMax.getZ());

                    int dx = dstMin.getX() + (x - srcMin.getX());
                    int dy = dstMin.getY() + (y - srcMin.getY());
                    int dz = dstMin.getZ() + (z - srcMin.getZ());

                    String cmd = String.format("clone %d %d %d %d %d %d %d %d %d", x, y, z, x2, y2, z2, dx, dy, dz);
                    if (moveMode) cmd += " replace move";
                    else if (forceMode) cmd += " replace force";
                    cmds.add(cmd);
                }
            }
        }

        return cmds;
    }

    private int getFlipAxis(String dir) {
        return switch (dir) {
            case "east", "west", "e", "w" -> 0;
            case "up", "down", "u", "d" -> 1;
            case "north", "south", "n", "s" -> 2;
            default -> -1;
        };
    }

    private List<String> buildFlipCommands(BlockPos min, BlockPos max, int axis) {
        List<String> cmds = new ArrayList<>();
        if (mc.level == null) return cmds;

        OperationBounds bounds = new OperationBounds(min, max);
        if (!isBoundsLoaded(bounds)) {
            warning("Flip requires the selected area to be loaded.");
            return cmds;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    int tx = x;
                    int ty = y;
                    int tz = z;

                    if (axis == 0) tx = max.getX() - (x - min.getX());
                    else if (axis == 1) ty = max.getY() - (y - min.getY());
                    else tz = max.getZ() - (z - min.getZ());

                    mutable.set(x, y, z);
                    String state = toCommandBlockState(mc.level.getBlockState(mutable));
                    cmds.add(String.format("setblock %d %d %d %s", tx, ty, tz, state));
                }
            }
        }

        return cmds;
    }

    private List<String> buildMoveCommands(BlockPos min, BlockPos max, int dist, String dir) {
        List<String> cmds = new ArrayList<>();
        int ox = 0;
        int oy = 0;
        int oz = 0;

        switch (dir) {
            case "north", "n" -> oz = -dist;
            case "south", "s" -> oz = dist;
            case "east", "e" -> ox = dist;
            case "west", "w" -> ox = -dist;
            case "up", "u" -> oy = dist;
            case "down", "d" -> oy = -dist;
            default -> {
            }
        }

        cmds.add(String.format("clone %d %d %d %d %d %d %d %d %d replace move", min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ(), min.getX() + ox, min.getY() + oy, min.getZ() + oz));

        pos1 = min.offset(ox, oy, oz);
        pos2 = max.offset(ox, oy, oz);

        return cmds;
    }

    private List<String> buildPyramidCommands(BlockPos base, int size, String block, boolean hollow) {
        List<String> cmds = new ArrayList<>();
        for (int layer = 0; layer < size; layer++) {
            int r = size - layer;
            BlockPos layerMin = base.offset(-r, layer, -r);
            BlockPos layerMax = base.offset(r, layer, r);

            if (hollow && layer > 0 && layer < size - 1) cmds.addAll(buildWallsCommands(layerMin, layerMax, block));
            else cmds.addAll(buildFillCommands(layerMin, layerMax, block));
        }
        return cmds;
    }

    private List<String> buildLineCommands(BlockPos a, BlockPos b, String blockSpec) {
        List<String> cmds = new ArrayList<>();
        BlockPattern pattern = parseBlockPattern(blockSpec);
        if (pattern == null) return cmds;

        int dx = Math.abs(b.getX() - a.getX());
        int dy = Math.abs(b.getY() - a.getY());
        int dz = Math.abs(b.getZ() - a.getZ());
        int steps = Math.max(dx, Math.max(dy, dz));

        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
            int y = (int) Math.round(a.getY() + (b.getY() - a.getY()) * t);
            int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
            cmds.add(String.format("setblock %d %d %d %s", x, y, z, pattern.randomBlock()));
        }

        return cmds;
    }

    private void addPatternSpanCommands(List<String> cmds, int minX, int maxX, int y, int z, BlockPattern pattern) {
        if (minX > maxX) return;

        int runStart = minX;
        String runBlock = pattern.randomBlock();

        for (int x = minX + 1; x <= maxX; x++) {
            String current = pattern.randomBlock();
            if (!current.equals(runBlock)) {
                emitPatternRunCommand(cmds, runStart, x - 1, y, z, runBlock);
                runStart = x;
                runBlock = current;
            }
        }

        emitPatternRunCommand(cmds, runStart, maxX, y, z, runBlock);
    }

    private void emitPatternRunCommand(List<String> cmds, int x1, int x2, int y, int z, String block) {
        if (x1 == x2) cmds.add(String.format("setblock %d %d %d %s", x1, y, z, block));
        else cmds.add(String.format("fill %d %d %d %d %d %d %s", x1, y, z, x2, y, z, block));
    }

    private OperationBounds computeStackBounds(BlockPos min, BlockPos max, int count, String dir) {
        if (count <= 0) return null;

        int dx = max.getX() - min.getX() + 1;
        int dy = max.getY() - min.getY() + 1;
        int dz = max.getZ() - min.getZ() + 1;

        OperationBounds total = null;
        for (int i = 1; i <= count; i++) {
            int ox = 0;
            int oy = 0;
            int oz = 0;

            switch (dir) {
                case "north", "n" -> oz = -dz * i;
                case "south", "s" -> oz = dz * i;
                case "east", "e" -> ox = dx * i;
                case "west", "w" -> ox = -dx * i;
                case "up", "u" -> oy = dy * i;
                case "down", "d" -> oy = -dy * i;
                default -> oz = dz * i;
            }

            OperationBounds current = new OperationBounds(min.offset(ox, oy, oz), max.offset(ox, oy, oz));
            total = total == null ? current : total.union(current);
        }

        return total;
    }

    private OperationBounds computeMoveBounds(BlockPos min, BlockPos max, int dist, String dir) {
        int ox = 0;
        int oy = 0;
        int oz = 0;

        switch (dir) {
            case "north", "n" -> oz = -dist;
            case "south", "s" -> oz = dist;
            case "east", "e" -> ox = dist;
            case "west", "w" -> ox = -dist;
            case "up", "u" -> oy = dist;
            case "down", "d" -> oy = -dist;
            default -> {
            }
        }

        OperationBounds original = new OperationBounds(min, max);
        OperationBounds moved = new OperationBounds(min.offset(ox, oy, oz), max.offset(ox, oy, oz));
        return original.union(moved);
    }

    private void queueSingleCommand(String command) {
        List<String> cmds = new ArrayList<>();
        cmds.add(command);
        startExecution(cmds, "single-command", null);
    }
    private void pushUndoEntry(HistoryEntry entry) {
        if (entry == null) return;
        undoStack.push(entry);
        while (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
    }

    private boolean requireSelection() {
        if (pos1 == null || pos2 == null) {
            error("Set both positions first: .we pos1 / .we pos2");
            return false;
        }
        return true;
    }

    private BlockPos getMin() {
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
    }

    private BlockPos getMax() {
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    private int getVolume() {
        BlockPos min = getMin();
        BlockPos max = getMax();
        return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    private int worldTopY() {
        if (mc.level == null || mc.level.dimension() == null) return 319;
        return mc.level.getMinY() + mc.level.dimensionType().height() - 1;
    }

    private boolean isChunkLoaded(BlockPos pos) {
        if (mc.level == null || pos == null || mc.level.getChunkSource() == null) return false;
        return mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private boolean isBoundsLoaded(OperationBounds bounds) {
        if (mc.level == null || bounds == null || mc.level.getChunkSource() == null) return false;

        int minChunkX = bounds.min.getX() >> 4;
        int maxChunkX = bounds.max.getX() >> 4;
        int minChunkZ = bounds.min.getZ() >> 4;
        int maxChunkZ = bounds.max.getZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!mc.level.getChunkSource().hasChunk(cx, cz)) return false;
            }
        }

        return true;
    }

    private long countBlocksInBounds(OperationBounds bounds, Identifier blockId) {
        if (mc.level == null || bounds == null) return 0;

        long count = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = bounds.min.getX(); x <= bounds.max.getX(); x++) {
            for (int y = bounds.min.getY(); y <= bounds.max.getY(); y++) {
                for (int z = bounds.min.getZ(); z <= bounds.max.getZ(); z++) {
                    mutable.set(x, y, z);
                    BlockState state = mc.level.getBlockState(mutable);
                    if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(blockId)) count++;
                }
            }
        }

        return count;
    }

    private BlockPos getCrosshairBlockPos() {
        if (mc.player == null) return null;

        HitResult hit = mc.player.pick(256.0, 0.0f, false);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hit).getBlockPos();
    }

    private Identifier parseBlockIdentifier(String normalizedBlock) {
        if (normalizedBlock == null || normalizedBlock.isEmpty()) return null;
        int stateIndex = normalizedBlock.indexOf('[');
        String idPart = stateIndex >= 0 ? normalizedBlock.substring(0, stateIndex) : normalizedBlock;
        return Identifier.tryParse(idPart);
    }

    private String pickPatternBlock(String blockSpec) {
        BlockPattern pattern = parseBlockPattern(blockSpec);
        if (pattern == null) return null;
        return pattern.randomBlock();
    }

    private BlockPattern parseBlockPattern(String blockSpec) {
        if (blockSpec == null || blockSpec.isBlank()) {
            error("Invalid block pattern: empty input.");
            return null;
        }

        List<String> tokens = splitPatternEntries(blockSpec);
        List<BlockPatternEntry> entries = new ArrayList<>();

        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) continue;

            int weight = 1;
            String blockPart = token;

            int percentIndex = token.indexOf('%');
            if (percentIndex == 0) {
                error("Invalid pattern entry: " + rawToken);
                return null;
            }

            if (percentIndex > 0) {
                String weightPart = token.substring(0, percentIndex).trim();
                boolean isWeight = !weightPart.isEmpty();
                for (int i = 0; i < weightPart.length() && isWeight; i++) {
                    if (!Character.isDigit(weightPart.charAt(i))) isWeight = false;
                }

                if (isWeight) {
                    weight = Math.max(1, parseInt(weightPart, 1));
                    blockPart = token.substring(percentIndex + 1).trim();
                }
            }

            if (blockPart.isEmpty()) {
                error("Invalid pattern entry: " + rawToken);
                return null;
            }

            String normalized = normalizeSingleBlock(blockPart);
            Identifier id = parseBlockIdentifier(normalized);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                error("Invalid block in pattern: " + blockPart);
                return null;
            }

            entries.add(new BlockPatternEntry(normalized, weight));
        }

        if (entries.isEmpty()) {
            error("Invalid block pattern: " + blockSpec);
            return null;
        }

        return new BlockPattern(entries);
    }

    private List<String> splitPatternEntries(String blockSpec) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;

        for (int i = 0; i < blockSpec.length(); i++) {
            char c = blockSpec.charAt(i);
            if (c == '[') bracketDepth++;
            else if (c == ']' && bracketDepth > 0) bracketDepth--;

            if (c == ',' && bracketDepth == 0) {
                entries.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        entries.add(current.toString());
        return entries;
    }

    private boolean isPatternExpression(String blockSpec) {
        if (blockSpec == null || blockSpec.isBlank()) return false;

        List<String> tokens = splitPatternEntries(blockSpec);
        if (tokens.size() > 1) return true;

        String token = tokens.get(0).trim();
        int percentIndex = token.indexOf('%');
        if (percentIndex <= 0) return false;

        String weightPart = token.substring(0, percentIndex).trim();
        if (weightPart.isEmpty()) return false;

        for (int i = 0; i < weightPart.length(); i++) {
            if (!Character.isDigit(weightPart.charAt(i))) return false;
        }

        return true;
    }

    private static final Map<String, String> NUMERIC_IDS = new HashMap<>();
    static {
        NUMERIC_IDS.put("0", "minecraft:air");
        NUMERIC_IDS.put("1", "minecraft:stone");
        NUMERIC_IDS.put("2", "minecraft:grass_block");
        NUMERIC_IDS.put("3", "minecraft:dirt");
        NUMERIC_IDS.put("4", "minecraft:cobblestone");
        NUMERIC_IDS.put("5", "minecraft:oak_planks");
        NUMERIC_IDS.put("7", "minecraft:bedrock");
        NUMERIC_IDS.put("8", "minecraft:water");
        NUMERIC_IDS.put("9", "minecraft:water");
        NUMERIC_IDS.put("10", "minecraft:lava");
        NUMERIC_IDS.put("11", "minecraft:lava");
        NUMERIC_IDS.put("12", "minecraft:sand");
        NUMERIC_IDS.put("13", "minecraft:gravel");
        NUMERIC_IDS.put("14", "minecraft:gold_ore");
        NUMERIC_IDS.put("15", "minecraft:iron_ore");
        NUMERIC_IDS.put("16", "minecraft:coal_ore");
        NUMERIC_IDS.put("17", "minecraft:oak_log");
        NUMERIC_IDS.put("18", "minecraft:oak_leaves");
        NUMERIC_IDS.put("20", "minecraft:glass");
        NUMERIC_IDS.put("21", "minecraft:lapis_ore");
        NUMERIC_IDS.put("22", "minecraft:lapis_block");
        NUMERIC_IDS.put("24", "minecraft:sandstone");
        NUMERIC_IDS.put("41", "minecraft:gold_block");
        NUMERIC_IDS.put("42", "minecraft:iron_block");
        NUMERIC_IDS.put("43", "minecraft:double_stone_slab");
        NUMERIC_IDS.put("44", "minecraft:stone_slab");
        NUMERIC_IDS.put("45", "minecraft:bricks");
        NUMERIC_IDS.put("46", "minecraft:tnt");
        NUMERIC_IDS.put("47", "minecraft:bookshelf");
        NUMERIC_IDS.put("48", "minecraft:mossy_cobblestone");
        NUMERIC_IDS.put("49", "minecraft:obsidian");
        NUMERIC_IDS.put("52", "minecraft:spawner");
        NUMERIC_IDS.put("56", "minecraft:diamond_ore");
        NUMERIC_IDS.put("57", "minecraft:diamond_block");
        NUMERIC_IDS.put("58", "minecraft:crafting_table");
        NUMERIC_IDS.put("73", "minecraft:redstone_ore");
        NUMERIC_IDS.put("87", "minecraft:netherrack");
        NUMERIC_IDS.put("88", "minecraft:soul_sand");
        NUMERIC_IDS.put("89", "minecraft:glowstone");
        NUMERIC_IDS.put("95", "minecraft:white_stained_glass");
        NUMERIC_IDS.put("97", "minecraft:infested_stone");
        NUMERIC_IDS.put("98", "minecraft:stone_bricks");
        NUMERIC_IDS.put("112", "minecraft:nether_bricks");
        NUMERIC_IDS.put("121", "minecraft:end_stone");
        NUMERIC_IDS.put("129", "minecraft:emerald_ore");
        NUMERIC_IDS.put("133", "minecraft:emerald_block");
        NUMERIC_IDS.put("138", "minecraft:beacon");
        NUMERIC_IDS.put("152", "minecraft:redstone_block");
        NUMERIC_IDS.put("153", "minecraft:quartz_ore");
        NUMERIC_IDS.put("155", "minecraft:quartz_block");
        NUMERIC_IDS.put("159", "minecraft:white_terracotta");
        NUMERIC_IDS.put("161", "minecraft:dark_oak_leaves");
        NUMERIC_IDS.put("162", "minecraft:acacia_log");
        NUMERIC_IDS.put("168", "minecraft:prismarine");
        NUMERIC_IDS.put("169", "minecraft:sea_lantern");
        NUMERIC_IDS.put("172", "minecraft:terracotta");
        NUMERIC_IDS.put("173", "minecraft:coal_block");
    }

    private String normalizeSingleBlock(String block) {
        if (block == null || block.isEmpty()) return "minecraft:air";
        String numeric = NUMERIC_IDS.get(block.trim());
        if (numeric != null) return numeric;
        if (!block.contains(":")) return "minecraft:" + block;
        return block;
    }

    private String normalizeBlock(String block) {
        return normalizeSingleBlock(block);
    }

    private static final class BlockPatternEntry {
        final String block;
        final int weight;

        private BlockPatternEntry(String block, int weight) {
            this.block = block;
            this.weight = Math.max(1, weight);
        }
    }

    private static final class BlockPattern {
        final List<BlockPatternEntry> entries;
        final int totalWeight;

        private BlockPattern(List<BlockPatternEntry> entries) {
            this.entries = entries;
            int total = 0;
            for (BlockPatternEntry entry : entries) total += entry.weight;
            this.totalWeight = Math.max(1, total);
        }

        private boolean isSingleBlock() {
            return entries.size() == 1;
        }

        private String singleBlock() {
            return entries.isEmpty() ? "minecraft:air" : entries.get(0).block;
        }

        private String randomBlock() {
            if (entries.isEmpty()) return "minecraft:air";
            if (entries.size() == 1) return entries.get(0).block;

            int roll = ThreadLocalRandom.current().nextInt(totalWeight);
            int cumulative = 0;
            for (BlockPatternEntry entry : entries) {
                cumulative += entry.weight;
                if (roll < cumulative) return entry.block;
            }

            return entries.get(entries.size() - 1).block;
        }
    }

    private String formatPos(BlockPos pos) {
        return String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    private String selectionInfo() {
        if (pos1 != null && pos2 != null) return String.format(" (%d blocks)", getVolume());
        return "";
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String formatTeleportCommand(BlockPos pos) {
        return String.format("tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    private void applySpeedPreset() {
        switch (speedPreset.get()) {
            case Slow -> {
                commandsPerTick.set(3);
                tickDelay.set(2);
            }
            case Normal -> {
                commandsPerTick.set(10);
                tickDelay.set(1);
            }
            case Fast -> {
                commandsPerTick.set(25);
                tickDelay.set(0);
            }
            case Turbo -> {
                commandsPerTick.set(50);
                tickDelay.set(0);
            }
            case Insane -> {
                commandsPerTick.set(100);
                tickDelay.set(0);
            }
        }
    }

    private void printHelp() {
        info("=== WorldEdit Help ===");
        info("Selection: pos1, pos2, hpos1, hpos2, chunk, size, clear, expand, contract, shift, inset, outset");
        info("Blocks: set, replace, walls, outline, floor, roof, hollow, line, center, count");
        info("Patterns: use weighted blocks like 25%glowstone,75%acacia_log");
        info("Shapes: sphere, hsphere, cyl, hcyl, pyramid, hpyramid");
        info("Clipboard: copy, cut, paste, flip, stack, move, saveclipboard, loadclipboard");
        info("Utility: drain, replacenear, tool");
        info("Navigation: ascend, descend, ceiling, thru");
        info("History: undo [count], redo [count]");
    }

    private void saveClipboard() {
        if (clipboardMin == null || clipboardMax == null || clipboardOrigin == null) return;

        try {
            String data = String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d", clipboardMin.getX(), clipboardMin.getY(), clipboardMin.getZ(), clipboardMax.getX(), clipboardMax.getY(), clipboardMax.getZ(), clipboardOrigin.getX(), clipboardOrigin.getY(), clipboardOrigin.getZ());
            Files.writeString(CLIPBOARD_FILE, data);
        } catch (IOException e) {
            error("Failed to save clipboard: " + e.getMessage());
        }
    }

    private void loadClipboard() {
        try {
            if (!Files.exists(CLIPBOARD_FILE)) return;
            String data = Files.readString(CLIPBOARD_FILE).trim();
            String[] p = data.split(",");
            if (p.length != 9) return;

            clipboardMin = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            clipboardMax = new BlockPos(Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
            clipboardOrigin = new BlockPos(Integer.parseInt(p[6]), Integer.parseInt(p[7]), Integer.parseInt(p[8]));

            int volume = (clipboardMax.getX() - clipboardMin.getX() + 1) * (clipboardMax.getY() - clipboardMin.getY() + 1) * (clipboardMax.getZ() - clipboardMin.getZ() + 1);
            info("Loaded clipboard from file (" + volume + " blocks).");
        } catch (IOException | NumberFormatException ignored) {

        }
    }

    public enum SpeedPreset {
        Slow,
        Normal,
        Fast,
        Turbo,
        Insane
    }

    private static final class OperationRequest {
        final String name;
        final List<String> commands;
        final int blockCount;
        final OperationBounds bounds;
        final BlockPos targetCenter;
        final boolean recordHistory;

        private OperationRequest(String name, List<String> commands, int blockCount, OperationBounds bounds, BlockPos targetCenter, boolean recordHistory) {
            this.name = name;
            this.commands = commands;
            this.blockCount = blockCount;
            this.bounds = bounds;
            this.targetCenter = targetCenter;
            this.recordHistory = recordHistory;
        }
    }

    private static final class HistoryEntry {
        final String name;
        final List<String> undoCommands;
        final List<String> redoCommands;

        private HistoryEntry(String name, List<String> undoCommands, List<String> redoCommands) {
            this.name = name;
            this.undoCommands = undoCommands;
            this.redoCommands = redoCommands;
        }
    }

    private static final class OperationBounds {
        final BlockPos min;
        final BlockPos max;

        private OperationBounds(BlockPos a, BlockPos b) {
            this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }

        int volume() {
            return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        }

        BlockPos center() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
        }

        OperationBounds union(OperationBounds other) {
            return new OperationBounds(new BlockPos(Math.min(this.min.getX(), other.min.getX()), Math.min(this.min.getY(), other.min.getY()), Math.min(this.min.getZ(), other.min.getZ())),
                    new BlockPos(Math.max(this.max.getX(), other.max.getX()), Math.max(this.max.getY(), other.max.getY()), Math.max(this.max.getZ(), other.max.getZ())));
        }
    }
}
