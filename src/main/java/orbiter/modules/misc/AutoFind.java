package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class AutoFind extends Module {

    public enum EspMode { Full, Corners, Wireframe, Sides }

    public enum NotifyMode { Chat, ActionBar, Both, Toast, All, None }

    public enum ScanPattern { Square, Circular }

    public enum FlightPattern {
        Snake,
        Spiral,
        Radial
    }

    public enum ScanMode {
        LocalRadius,
        WorldSweep
    }

    public static class FindResult {
        public final BlockPos pos;
        public final String category;
        public final String criteria;
        public final long timestamp;
        public final int blockCount;

        public FindResult(BlockPos pos, String category, String criteria, int blockCount) {
            this.pos = pos.immutable();
            this.category = category;
            this.criteria = criteria;
            this.timestamp = System.currentTimeMillis();
            this.blockCount = blockCount;
        }

        public String toDisplayString() {
            return String.format("[%s] %s at %d, %d, %d (%d blocks) • %s",
                category, criteria, pos.getX(), pos.getY(), pos.getZ(), blockCount, criteria);
        }
    }

    private static final int WORLD_BOUNDARY = 30_000_000;
    private static final int MIN_CHUNK_X = -WORLD_BOUNDARY >> 4;
    private static final int MAX_CHUNK_X = (WORLD_BOUNDARY - 1) >> 4;
    private static final int MIN_CHUNK_Z = -WORLD_BOUNDARY >> 4;
    private static final int MAX_CHUNK_Z = (WORLD_BOUNDARY - 1) >> 4;

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgFlight     = settings.createGroup("Level Sweep Flight");
    private final SettingGroup sgStash      = settings.createGroup("Stash Finder");
    private final SettingGroup sgBase       = settings.createGroup("Base Finder");
    private final SettingGroup sgStorage    = settings.createGroup("Storage Finder");
    private final SettingGroup sgExclusion   = settings.createGroup("Exclusion Zone");
    private final SettingGroup sgRender     = settings.createGroup("Render");
    private final SettingGroup sgNotify     = settings.createGroup("Notifications");

    private final Setting<ScanMode> scanMode = sgGeneral.add(new EnumSetting.Builder<ScanMode>()
        .name("scan-mode")
        .description("LocalRadius = scan around player; WorldSweep = fly the entire world.")
        .defaultValue(ScanMode.LocalRadius)
        .build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius-chunks")
        .description("Chunk radius to scan around the player (LocalRadius mode).")
        .defaultValue(5)
        .min(1).sliderRange(1, 16)
        .visible(() -> scanMode.get() == ScanMode.LocalRadius)
        .build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between scan ticks (20 = 1 second).")
        .defaultValue(40)
        .min(10).sliderRange(10, 200)
        .build());

    private final Setting<Boolean> pauseOnKey = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-key")
        .description("Pause/resume scanning with the module toggle key.")
        .defaultValue(false)
        .build());

    private final Setting<ScanPattern> scanPattern = sgGeneral.add(new EnumSetting.Builder<ScanPattern>()
        .name("scan-pattern")
        .description("Pattern used for local-area scanning (LocalRadius mode).")
        .defaultValue(ScanPattern.Square)
        .visible(() -> scanMode.get() == ScanMode.LocalRadius)
        .build());

    private final Setting<FlightPattern> flightPattern = sgFlight.add(new EnumSetting.Builder<FlightPattern>()
        .name("flight-pattern")
        .description("Pattern the scanner flies to cover the entire world.")
        .defaultValue(FlightPattern.Snake)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Boolean> useCurrentPos = sgFlight.add(new BoolSetting.Builder()
        .name("start-at-current-pos")
        .description("Use the player's current position as the starting point for the sweep.")
        .defaultValue(true)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Integer> startX = sgFlight.add(new IntSetting.Builder()
        .name("start-x")
        .description("Starting X coordinate for the world sweep (when not using current pos).")
        .defaultValue(0)
        .sliderRange(-30000000, 30000000)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep && !useCurrentPos.get())
        .build());

    private final Setting<Integer> startZ = sgFlight.add(new IntSetting.Builder()
        .name("start-z")
        .description("Starting Z coordinate for the world sweep (when not using current pos).")
        .defaultValue(0)
        .sliderRange(-30000000, 30000000)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep && !useCurrentPos.get())
        .build());

    private final Setting<Integer> flightY = sgFlight.add(new IntSetting.Builder()
        .name("flight-y")
        .description("Y level the scanner flies at during world sweep.")
        .defaultValue(320)
        .min(-64).sliderRange(-64, 320)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Double> flightSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("flight-speed")
        .description("Blocks per tick the scanner moves during world sweep.")
        .defaultValue(50.0)
        .min(1.0).sliderRange(1.0, 500.0)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Integer> chunksPerTick = sgFlight.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("How many chunks to scan each scan tick during world sweep.")
        .defaultValue(8)
        .min(1).sliderRange(1, 64)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Boolean> worldWrap = sgFlight.add(new BoolSetting.Builder()
        .name("world-wrap")
        .description("Wrap around world boundaries (±30M) instead of stopping.")
        .defaultValue(true)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Boolean> autoTp = sgFlight.add(new BoolSetting.Builder()
        .name("auto-tp")
        .description("Automatically send /tp to move the player during world sweep (requires OP/creative).")
        .defaultValue(true)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep)
        .build());

    private final Setting<Integer> tpInterval = sgFlight.add(new IntSetting.Builder()
        .name("tp-interval")
        .description("Minimum ticks between /tp commands to avoid spamming the server.")
        .defaultValue(10)
        .min(1).sliderRange(1, 100)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep && autoTp.get())
        .build());

    private final Setting<Integer> spiralStep = sgFlight.add(new IntSetting.Builder()
        .name("spiral-step-chunks")
        .description("How many chunks to step per spiral arm segment (Spiral pattern).")
        .defaultValue(16)
        .min(1).sliderRange(1, 256)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep && flightPattern.get() == FlightPattern.Spiral)
        .build());

    private final Setting<Integer> radialSectors = sgFlight.add(new IntSetting.Builder()
        .name("radial-sectors")
        .description("Number of sectors for the radial sweep pattern.")
        .defaultValue(8)
        .min(2).sliderRange(2, 32)
        .visible(() -> scanMode.get() == ScanMode.WorldSweep && flightPattern.get() == FlightPattern.Radial)
        .build());

    private final Setting<Boolean> findStashes = sgStash.add(new BoolSetting.Builder()
        .name("find-stashes")
        .description("Look for potential stash locations (clusters of storage blocks).")
        .defaultValue(true)
        .build());

    private final Setting<Integer> stashMinChests = sgStash.add(new IntSetting.Builder()
        .name("min-storage-blocks")
        .description("Minimum number of storage blocks in a chunk to flag as a potential stash.")
        .defaultValue(4)
        .min(1).sliderRange(1, 30)
        .visible(() -> findStashes.get())
        .build());

    private final Setting<Boolean> stashChests = sgStash.add(new BoolSetting.Builder()
        .name("stash-chests").description("Count chests for stash detection.").defaultValue(true)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashTrappedChests = sgStash.add(new BoolSetting.Builder()
        .name("stash-trapped-chests").description("Count trapped chests.").defaultValue(true)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashBarrels = sgStash.add(new BoolSetting.Builder()
        .name("stash-barrels").description("Count barrels.").defaultValue(true)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashShulkers = sgStash.add(new BoolSetting.Builder()
        .name("stash-shulkers").description("Count shulker boxes.").defaultValue(true)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashEnderChests = sgStash.add(new BoolSetting.Builder()
        .name("stash-ender-chests").description("Count ender chests.").defaultValue(true)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashDroppers = sgStash.add(new BoolSetting.Builder()
        .name("stash-droppers").description("Count droppers.").defaultValue(false)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashDispensers = sgStash.add(new BoolSetting.Builder()
        .name("stash-dispensers").description("Count dispensers.").defaultValue(false)
        .visible(() -> findStashes.get()).build());
    private final Setting<Boolean> stashHoppers = sgStash.add(new BoolSetting.Builder()
        .name("stash-hoppers").description("Count hoppers.").defaultValue(false)
        .visible(() -> findStashes.get()).build());

    private final Setting<Boolean> findBases = sgBase.add(new BoolSetting.Builder()
        .name("find-bases")
        .description("Detect player-made bases by looking for redstone, crafting, and other non-natural blocks.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> baseMinIndicators = sgBase.add(new IntSetting.Builder()
        .name("min-base-indicators")
        .description("Minimum number of base indicator blocks in a chunk to flag as a base.")
        .defaultValue(3)
        .min(1).sliderRange(1, 20)
        .visible(() -> findBases.get())
        .build());

    private final Setting<Boolean> basePistons = sgBase.add(new BoolSetting.Builder()
        .name("pistons").description("Count pistons and sticky pistons.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseRedstone = sgBase.add(new BoolSetting.Builder()
        .name("redstone-wire").description("Count redstone wire/dust.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseRepeaters = sgBase.add(new BoolSetting.Builder()
        .name("repeaters").description("Count redstone repeaters.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseComparators = sgBase.add(new BoolSetting.Builder()
        .name("comparators").description("Count redstone comparators.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseObservers = sgBase.add(new BoolSetting.Builder()
        .name("observers").description("Count observers.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseFurnaces = sgBase.add(new BoolSetting.Builder()
        .name("furnaces").description("Count furnaces, blast furnaces, smokers.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseCraftingTables = sgBase.add(new BoolSetting.Builder()
        .name("crafting-tables").description("Count crafting tables.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseAnvils = sgBase.add(new BoolSetting.Builder()
        .name("anvils").description("Count anvils.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseEnchantTables = sgBase.add(new BoolSetting.Builder()
        .name("enchanting-tables").description("Count enchanting tables.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseBeds = sgBase.add(new BoolSetting.Builder()
        .name("beds").description("Count beds.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseBeacons = sgBase.add(new BoolSetting.Builder()
        .name("beacons").description("Count beacons.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseBrewingStands = sgBase.add(new BoolSetting.Builder()
        .name("brewing-stands").description("Count brewing stands.").defaultValue(true)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseLecterns = sgBase.add(new BoolSetting.Builder()
        .name("lecterns").description("Count lecterns.").defaultValue(false)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> baseLogStripping = sgBase.add(new BoolSetting.Builder()
        .name("stripped-logs").description("Detect stripped logs (sign of player activity).").defaultValue(false)
        .visible(() -> findBases.get()).build());
    private final Setting<Boolean> excludeStructures = sgBase.add(new BoolSetting.Builder()
        .name("exclude-structures")
        .description("Attempt to filter out naturally generated structures by checking for structure blocks nearby.")
        .defaultValue(true)
        .visible(() -> findBases.get())
        .build());

    private final Setting<Boolean> findStorage = sgStorage.add(new BoolSetting.Builder()
        .name("find-storage").description("Highlight individual storage blocks.").defaultValue(false).build());
    private final Setting<Boolean> storageChests = sgStorage.add(new BoolSetting.Builder()
        .name("chests").defaultValue(true).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageTrappedChests = sgStorage.add(new BoolSetting.Builder()
        .name("trapped-chests").defaultValue(true).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageEnderChests = sgStorage.add(new BoolSetting.Builder()
        .name("ender-chests").defaultValue(true).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageBarrels = sgStorage.add(new BoolSetting.Builder()
        .name("barrels").defaultValue(true).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageShulkers = sgStorage.add(new BoolSetting.Builder()
        .name("shulker-boxes").defaultValue(true).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageHoppers = sgStorage.add(new BoolSetting.Builder()
        .name("hoppers").defaultValue(false).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageDroppers = sgStorage.add(new BoolSetting.Builder()
        .name("droppers").defaultValue(false).visible(() -> findStorage.get()).build());
    private final Setting<Boolean> storageDispensers = sgStorage.add(new BoolSetting.Builder()
        .name("dispensers").defaultValue(false).visible(() -> findStorage.get()).build());

    private final Setting<Boolean> enableExclusion = sgExclusion.add(new BoolSetting.Builder()
        .name("enable-exclusion-zone").description("Ignore findings within a certain radius.").defaultValue(true).build());
    private final Setting<Integer> exclusionX = sgExclusion.add(new IntSetting.Builder()
        .name("exclusion-center-x").defaultValue(0).sliderRange(-30000, 30000)
        .visible(() -> enableExclusion.get()).build());
    private final Setting<Integer> exclusionY = sgExclusion.add(new IntSetting.Builder()
        .name("exclusion-center-y").defaultValue(-1).sliderRange(-1, 320)
        .visible(() -> enableExclusion.get()).build());
    private final Setting<Integer> exclusionZ = sgExclusion.add(new IntSetting.Builder()
        .name("exclusion-center-z").defaultValue(0).sliderRange(-30000, 30000)
        .visible(() -> enableExclusion.get()).build());
    private final Setting<Integer> exclusionRadius = sgExclusion.add(new IntSetting.Builder()
        .name("exclusion-radius").defaultValue(200).min(1).sliderRange(1, 5000)
        .visible(() -> enableExclusion.get()).build());

    private final Setting<EspMode> espMode = sgRender.add(new EnumSetting.Builder<EspMode>()
        .name("esp-mode").description("How to render found blocks.").defaultValue(EspMode.Corners).build());
    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity").defaultValue(20).min(0).sliderRange(0, 255).build());
    private final Setting<Integer> lineOpacity = sgRender.add(new IntSetting.Builder()
        .name("line-opacity").defaultValue(200).min(0).sliderRange(0, 255).build());
    private final Setting<Double> cornerLen = sgRender.add(new DoubleSetting.Builder()
        .name("corner-length").defaultValue(0.25).min(0.05).sliderRange(0.05, 0.5)
        .visible(() -> espMode.get() == EspMode.Corners).build());
    private final Setting<Boolean> renderStashBlocks = sgRender.add(new BoolSetting.Builder()
        .name("render-stash-blocks").description("Render individual storage blocks within stashes.").defaultValue(true).build());
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").description("Draw a tracer line to found stashes and bases.").defaultValue(true).build());

    private final Setting<Boolean> createWaypoints = sgNotify.add(new BoolSetting.Builder()
        .name("create-waypoints").description("Auto-add Meteor waypoint on findings.").defaultValue(false).build());
    private final Setting<NotifyMode> notifyMode = sgNotify.add(new EnumSetting.Builder<NotifyMode>()
        .name("notify-mode").description("How to notify on findings.").defaultValue(NotifyMode.Chat).build());
    private final Setting<Boolean> showCoordinates = sgNotify.add(new BoolSetting.Builder()
        .name("show-coordinates").description("Include coordinates in the notification.").defaultValue(true).build());
    private final Setting<Boolean> logResults = sgNotify.add(new BoolSetting.Builder()
        .name("log-results").description("Log findings to file.").defaultValue(true).build());

    private static final int MAX_RESULTS = 2000;
    private static final int MAX_RENDER_STORAGE = 5000;
    private static final int MAX_SCANNED_CHUNKS = 100000;

    private final List<FindResult> results = new ArrayList<>();
    private final Set<ChunkPos> scannedChunks = new LinkedHashSet<>();
    private final List<BlockPos> renderStoragePositions = new ArrayList<>();
    private final Map<BlockPos, Color> renderStorageColors = new HashMap<>();
    private int tickCounter = 0;
    private boolean paused = false;

    private double flightX, flightZ;
    private boolean sweepInitialized = false;
    private boolean sweepComplete = false;
    private long totalSweepChunksScanned = 0;
    private int lastTpTick = 0;

    private int snakeRow = 0;
    private boolean snakeForward = true;

    private int spiralArmLength = 1;
    private int spiralStepsInArm = 0;
    private int spiralDirection = 0;
    private int spiralTurnCount = 0;

    private int radialCurrentSector = 0;
    private double radialDistance = 0;
    private double radialAngle = 0;

    private static final Color STASH_COLOR_LINE = new Color(255, 50, 50, 200);
    private static final Color BASE_COLOR_LINE = new Color(50, 50, 255, 200);
    private static final Color STORAGE_CHEST_LINE = new Color(255, 200, 0, 200);
    private static final Color STORAGE_ENDER_LINE = new Color(80, 0, 160, 200);
    private static final Color STORAGE_SHULKER_LINE = new Color(200, 50, 200, 200);
    private static final Color STORAGE_BARREL_LINE = new Color(160, 120, 50, 200);
    private static final Color STORAGE_HOPPER_LINE = new Color(100, 100, 100, 200);
    private static final Color STORAGE_OTHER_LINE = new Color(150, 150, 150, 200);

    public AutoFind() {
        super(Orbiter.CATEGORY, "auto-find", "Scan for stashes, bases, and storage. Level-wrapping flight scanner.");
    }

    @Override
    public void onActivate() {
        results.clear();
        scannedChunks.clear();
        renderStoragePositions.clear();
        renderStorageColors.clear();
        tickCounter = 0;
        paused = false;
        sweepInitialized = false;
        sweepComplete = false;
        totalSweepChunksScanned = 0;
        scanEnvironment();
    }

    public void pause() { paused = true; }
    public void resume() { paused = false; }
    public boolean isPaused() { return paused; }

    private void scanEnvironment() {
        if (mc.player == null || mc.level == null) return;
        if (scanMode.get() == ScanMode.LocalRadius) {
            scanLoadedChunks();
        }
    }

    @Override
    public void onDeactivate() {
        if (logResults.get() && !results.isEmpty()) {
            ChatUtils.sendMsg(Component.literal("§6[AutoFind] §fSession summary: §a" + results.size() + " §ffindings logged."));
            for (FindResult r : results) {
                ChatUtils.sendMsg(Component.literal("§7 - " + r.toDisplayString()));
            }
        }
        if (scanMode.get() == ScanMode.WorldSweep) {
            info("Level sweep stopped. §a" + totalSweepChunksScanned + "§r chunks scanned, §e" + results.size() + "§r findings.");
        }
    }

    @Override
    public String getInfoString() {
        if (scanMode.get() == ScanMode.WorldSweep && sweepInitialized && !sweepComplete) {
            return results.size() + " found | " + totalSweepChunksScanned + " chunks";
        }
        return results.size() + " found";
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (paused) return;

        tickCounter++;
        if (tickCounter % scanInterval.get() != 0) return;

        if (scanMode.get() == ScanMode.LocalRadius) {
            scanLoadedChunks();
        } else {
            tickWorldSweep();
        }
    }

    private void scanLoadedChunks() {
        if (mc.level == null || mc.player == null || mc.level.getChunkSource() == null) return;

        int playerChunkX = mc.player.blockPosition().getX() >> 4;
        int playerChunkZ = mc.player.blockPosition().getZ() >> 4;
        int r = scanRadius.get();

        int budget = 2;
        for (int cx = playerChunkX - r; cx <= playerChunkX + r && budget > 0; cx++) {
            for (int cz = playerChunkZ - r; cz <= playerChunkZ + r && budget > 0; cz++) {
                if (scanPattern.get() == ScanPattern.Circular) {
                    int dcx = cx - playerChunkX;
                    int dcz = cz - playerChunkZ;
                    if (dcx * dcx + dcz * dcz > r * r) continue;
                }

                ChunkPos cp = new ChunkPos(cx, cz);
                if (scannedChunks.contains(cp)) continue;

                LevelChunk chunk = mc.level.getChunk(cx, cz);
                if (chunk == null) continue;

                scannedChunks.add(cp);
                scanChunk(chunk, cp);
                budget--;

                if (scannedChunks.size() > MAX_SCANNED_CHUNKS) {
                    scannedChunks.clear();
                    info("Scanned-chunks cache reset (>100K entries). Continuing scan.");
                }
            }
        }
    }

    private void initWorldSweep() {
        if (useCurrentPos.get() && mc.player != null) {
            flightX = mc.player.getX();
            flightZ = mc.player.getZ();
        } else {
            flightX = startX.get();
            flightZ = startZ.get();
        }

        snakeRow = (int) flightX >> 4;
        snakeForward = true;
        spiralArmLength = 1;
        spiralStepsInArm = 0;
        spiralDirection = 0;
        spiralTurnCount = 0;
        radialCurrentSector = 0;
        radialDistance = 0;
        radialAngle = 0;

        sweepInitialized = true;
        sweepComplete = false;
        info("Level sweep started at §a" + (int) flightX + ", " + (int) flightZ + "§r with pattern §e" + flightPattern.get());
    }

    private void tickWorldSweep() {
        if (!sweepInitialized) {
            initWorldSweep();
            return;
        }
        if (sweepComplete) return;

        int cpt = chunksPerTick.get();
        int scannedThisTick = 0;

        while (scannedThisTick < cpt) {
            int cx = wrapChunkX((int) flightX >> 4);
            int cz = wrapChunkZ((int) flightZ >> 4);

            ChunkPos cp = new ChunkPos(cx, cz);
            if (!scannedChunks.contains(cp)) {

                LevelChunk chunk = mc.level.getChunk(cx, cz);
                if (chunk != null) {
                    scannedChunks.add(cp);
                    scanChunk(chunk, cp);
                    totalSweepChunksScanned++;
                    scannedThisTick++;
                } else {

                    break;
                }
            }

            advanceFlightPosition();

            if (totalSweepChunksScanned > 500_000) {
                sweepComplete = true;
                info("Level sweep completed. §a" + totalSweepChunksScanned + "§r chunks scanned, §e" + results.size() + "§r findings.");
                break;
            }
        }

        if (autoTp.get() && mc.player != null && mc.getConnection() != null && !sweepComplete
            && mc.player.getAbilities().instabuild
            && (tickCounter - lastTpTick) >= tpInterval.get()) {
            int bx = wrapBlockX((int) flightX);
            int bz = wrapBlockZ((int) flightZ);
            mc.getConnection().sendCommand(CommandUtils.vanilla("tp " + bx + " " + flightY.get() + " " + bz));
            lastTpTick = tickCounter;
        }
    }

    private void advanceFlightPosition() {
        double speed = flightSpeed.get();
        int stepChunks = Math.max(1, (int) Math.ceil(speed / 16.0));
        double stepBlocks = stepChunks * 16.0;

        switch (flightPattern.get()) {
            case Snake -> advanceSnake(stepBlocks);
            case Spiral -> advanceSpiral(stepBlocks);
            case Radial -> advanceRadial(stepBlocks);
        }
    }

    private void advanceSnake(double stepBlocks) {
        if (snakeForward) {
            flightZ += stepBlocks;

            if (flightZ > WORLD_BOUNDARY) {
                if (worldWrap.get()) {
                    flightZ = -WORLD_BOUNDARY;

                    flightX += 16;
                    snakeForward = false;
                } else {

                    snakeForward = false;
                    flightX += 16;
                    flightZ = WORLD_BOUNDARY;
                }
            }
        } else {
            flightZ -= stepBlocks;
            if (flightZ < -WORLD_BOUNDARY) {
                if (worldWrap.get()) {
                    flightZ = WORLD_BOUNDARY;

                    flightX += 16;
                    snakeForward = true;
                } else {

                    snakeForward = true;
                    flightX += 16;
                    flightZ = -WORLD_BOUNDARY;
                }
            }
        }

        if (flightX > WORLD_BOUNDARY) {
            flightX = worldWrap.get() ? -WORLD_BOUNDARY : WORLD_BOUNDARY;
        }
    }

    private void advanceSpiral(double stepBlocks) {
        spiralStepsInArm++;

        switch (spiralDirection) {
            case 0 -> flightX += stepBlocks;
            case 1 -> flightZ += stepBlocks;
            case 2 -> flightX -= stepBlocks;
            case 3 -> flightZ -= stepBlocks;
        }

        flightX = wrapBlockX(flightX);
        flightZ = wrapBlockZ(flightZ);

        int armChunkLen = spiralStep.get();
        if (spiralStepsInArm >= armChunkLen) {
            spiralStepsInArm = 0;
            spiralDirection = (spiralDirection + 1) % 4;
            spiralTurnCount++;

            if (spiralTurnCount % 2 == 0) {
                spiralArmLength++;
            }
        }
    }

    private void advanceRadial(double stepBlocks) {
        double sectors = radialSectors.get();
        double sectorAngle = (2.0 * Math.PI) / sectors;
        radialAngle = sectorAngle * radialCurrentSector;

        radialDistance += stepBlocks;

        flightX = radialDistance * Math.cos(radialAngle);
        flightZ = radialDistance * Math.sin(radialAngle);

        flightX = wrapBlockX(flightX);
        flightZ = wrapBlockZ(flightZ);

        double maxDist = Math.sqrt(2.0) * WORLD_BOUNDARY;
        if (radialDistance > maxDist) {
            radialDistance = 0;
            radialCurrentSector = (radialCurrentSector + 1) % radialSectors.get();

            if (radialCurrentSector == 0) {
                radialDistance = 0;
            }
        }
    }

    private int wrapBlockX(double x) {
        if (!worldWrap.get()) return (int) Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, x));

        double range = 2.0 * WORLD_BOUNDARY;
        double shifted = x + WORLD_BOUNDARY;
        double wrapped = ((shifted % range) + range) % range - WORLD_BOUNDARY;
        return (int) wrapped;
    }

    private int wrapBlockZ(double z) {
        if (!worldWrap.get()) return (int) Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, z));
        double range = 2.0 * WORLD_BOUNDARY;
        double shifted = z + WORLD_BOUNDARY;
        double wrapped = ((shifted % range) + range) % range - WORLD_BOUNDARY;
        return (int) wrapped;
    }

    private int wrapChunkX(int cx) {
        if (cx < MIN_CHUNK_X) return worldWrap.get() ? (MAX_CHUNK_X - (MIN_CHUNK_X - cx)) : MIN_CHUNK_X;
        if (cx > MAX_CHUNK_X) return worldWrap.get() ? (MIN_CHUNK_X + (cx - MAX_CHUNK_X)) : MAX_CHUNK_X;
        return cx;
    }

    private int wrapChunkZ(int cz) {
        if (cz < MIN_CHUNK_Z) return worldWrap.get() ? (MAX_CHUNK_Z - (MIN_CHUNK_Z - cz)) : MIN_CHUNK_Z;
        if (cz > MAX_CHUNK_Z) return worldWrap.get() ? (MIN_CHUNK_Z + (cz - MAX_CHUNK_Z)) : MAX_CHUNK_Z;
        return cz;
    }

    private void scanChunk(LevelChunk chunk, ChunkPos cp) {
        int startX = cp.getMinBlockX();
        int startZ = cp.getMinBlockZ();
        BlockPos centerOfChunk = new BlockPos(startX + 8, 64, startZ + 8);

        if (isExcluded(centerOfChunk)) return;

        int storageCount = 0;
        int baseIndicatorCount = 0;
        List<BlockPos> storagePositions = new ArrayList<>();
        List<BlockPos> basePositions = new ArrayList<>();
        List<String> storageTypes = new ArrayList<>();
        List<String> baseTypes = new ArrayList<>();
        boolean hasNaturalStructureSigns = false;

        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntity be = entry.getValue();

            if (isExcluded(pos)) continue;

            if (be instanceof ChestBlockEntity) {
                if (stashChests.get() || (findStorage.get() && storageChests.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Chest"); addRenderStorage(pos, STORAGE_CHEST_LINE);
                }
            } else if (be instanceof TrappedChestBlockEntity) {
                if (stashTrappedChests.get() || (findStorage.get() && storageTrappedChests.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Trapped Chest"); addRenderStorage(pos, STORAGE_CHEST_LINE);
                }
            } else if (be instanceof BarrelBlockEntity) {
                if (stashBarrels.get() || (findStorage.get() && storageBarrels.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Barrel"); addRenderStorage(pos, STORAGE_BARREL_LINE);
                }
            } else if (be instanceof ShulkerBoxBlockEntity) {
                if (stashShulkers.get() || (findStorage.get() && storageShulkers.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Shulker AABB"); addRenderStorage(pos, STORAGE_SHULKER_LINE);
                }
            } else if (be instanceof HopperBlockEntity) {
                if (stashHoppers.get() || (findStorage.get() && storageHoppers.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Hopper"); addRenderStorage(pos, STORAGE_HOPPER_LINE);
                }
            } else if (be instanceof DropperBlockEntity) {
                if (stashDroppers.get() || (findStorage.get() && storageDroppers.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Dropper"); addRenderStorage(pos, STORAGE_OTHER_LINE);
                }
            } else if (be instanceof DispenserBlockEntity && !(be instanceof DropperBlockEntity)) {
                if (stashDispensers.get() || (findStorage.get() && storageDispensers.get())) {
                    storageCount++; storagePositions.add(pos.immutable()); storageTypes.add("Dispenser"); addRenderStorage(pos, STORAGE_OTHER_LINE);
                }
            }

            if (findBases.get()) {
                if (be instanceof FurnaceBlockEntity && baseFurnaces.get()) { baseIndicatorCount++; basePositions.add(pos.immutable()); baseTypes.add("Furnace"); }
                else if (be instanceof BrewingStandBlockEntity && baseBrewingStands.get()) { baseIndicatorCount++; basePositions.add(pos.immutable()); baseTypes.add("Brewing Stand"); }
                else if (be instanceof BeaconBlockEntity && baseBeacons.get()) { baseIndicatorCount++; basePositions.add(pos.immutable()); baseTypes.add("Beacon"); }
                else if (be instanceof LecternBlockEntity && baseLecterns.get()) { baseIndicatorCount++; basePositions.add(pos.immutable()); baseTypes.add("Lectern"); }
                else if (be instanceof EnchantingTableBlockEntity && baseEnchantTables.get()) { baseIndicatorCount++; basePositions.add(pos.immutable()); baseTypes.add("Enchanting Table"); }
            }
        }

        if (findBases.get()) {
            int minY = mc.level.getMinY();
            int maxY = mc.level.getMaxY();

            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos(x, y, z);
                        BlockState state = chunk.getBlockState(mpos);
                        Block block = state.getBlock();

                        if (basePistons.get() && (block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON)) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Piston");
                        } else if (baseRedstone.get() && block == Blocks.REDSTONE_WIRE) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Redstone Wire");
                        } else if (baseRepeaters.get() && block == Blocks.REPEATER) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Repeater");
                        } else if (baseComparators.get() && block == Blocks.COMPARATOR) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Comparator");
                        } else if (baseObservers.get() && block == Blocks.OBSERVER) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Observer");
                        } else if (baseCraftingTables.get() && block == Blocks.CRAFTING_TABLE) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Crafting Table");
                        } else if (baseAnvils.get() && (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL)) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Anvil");
                        } else if (baseBeds.get() && translationKeyContains(state.getBlock(), "bed")) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Bed");
                        } else if (baseLogStripping.get() && translationKeyContains(block, "stripped")) {
                            baseIndicatorCount++; basePositions.add(mpos.immutable()); baseTypes.add("Stripped Log");
                        }

                        if (block == Blocks.ENDER_CHEST) {
                            if (stashEnderChests.get() || (findStorage.get() && storageEnderChests.get())) {
                                storageCount++; storagePositions.add(mpos.immutable()); storageTypes.add("Ender Chest"); addRenderStorage(mpos.immutable(), STORAGE_ENDER_LINE);
                            }
                        }

                        if (excludeStructures.get()) {
                            if (block == Blocks.COBBLESTONE_WALL || block == Blocks.MOSSY_COBBLESTONE
                                || block == Blocks.COBBLESTONE_STAIRS || block == Blocks.STONE_BRICK_STAIRS
                                || block == Blocks.NETHER_BRICKS || block == Blocks.NETHER_BRICK_STAIRS
                                || block == Blocks.SPAWNER || block == Blocks.END_PORTAL_FRAME
                                || block == Blocks.POLISHED_BLACKSTONE_BRICKS || block == Blocks.CHISELED_STONE_BRICKS
                                || block == Blocks.INFESTED_STONE_BRICKS || block == Blocks.SUSPICIOUS_SAND
                                || block == Blocks.SUSPICIOUS_GRAVEL || block == Blocks.DECORATED_POT) {
                                hasNaturalStructureSigns = true;
                            }
                        }
                    }
                }
            }
        }

        if (findStashes.get() && storageCount >= stashMinChests.get()) {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (String t : storageTypes) typeCounts.merge(t, 1, (a, b) -> a + b);
            StringBuilder criteria = new StringBuilder("Storage: ");
            typeCounts.forEach((type, count) -> criteria.append(count).append("x ").append(type).append(", "));
            if (criteria.length() > 2) criteria.setLength(criteria.length() - 2);
            BlockPos avg = averagePos(storagePositions);
            addResult(new FindResult(avg, "STASH", criteria.toString(), storageCount));
        }

        if (findBases.get() && baseIndicatorCount >= baseMinIndicators.get()) {
            if (excludeStructures.get() && hasNaturalStructureSigns) return;
            Map<String, Integer> typeCounts = new HashMap<>();
            for (String t : baseTypes) typeCounts.merge(t, 1, (a, b) -> a + b);
            StringBuilder criteria = new StringBuilder("Indicators: ");
            typeCounts.forEach((type, count) -> criteria.append(count).append("x ").append(type).append(", "));
            if (criteria.length() > 2) criteria.setLength(criteria.length() - 2);
            BlockPos avg = averagePos(basePositions);
            addResult(new FindResult(avg, "BASE", criteria.toString(), baseIndicatorCount));
        }
    }

    private boolean isExcluded(BlockPos pos) {
        if (!enableExclusion.get()) return false;
        int dx = pos.getX() - exclusionX.get();
        int dz = pos.getZ() - exclusionZ.get();
        if (exclusionY.get() >= 0) {
            int dy = pos.getY() - exclusionY.get();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return dist <= exclusionRadius.get();
        } else {
            double dist = Math.sqrt(dx * dx + dz * dz);
            return dist <= exclusionRadius.get();
        }
    }

    private boolean translationKeyContains(Block block, String substring) {
        if (block == null) return false;
        String tk = block.getDescriptionId();
        return tk != null && tk.contains(substring);
    }

    private BlockPos averagePos(List<BlockPos> positions) {
        if (positions.isEmpty()) return BlockPos.ZERO;
        long x = 0, y = 0, z = 0;
        for (BlockPos p : positions) { x += p.getX(); y += p.getY(); z += p.getZ(); }
        int size = positions.size();
        return new BlockPos((int)(x / size), (int)(y / size), (int)(z / size));
    }

    private void addResult(FindResult result) {
        results.add(result);
        while (results.size() > MAX_RESULTS) results.remove(0);

        String msg;
        if (showCoordinates.get()) {
            msg = String.format("§6[AutoFind] §e%s §fdetected at §a%d, %d, %d §7(%d blocks) §f• %s",
                result.category, result.pos.getX(), result.pos.getY(), result.pos.getZ(),
                result.blockCount, result.criteria);
        } else {
            msg = String.format("§6[AutoFind] §e%s §fdetected nearby §7(%d blocks) §f• %s",
                result.category, result.blockCount, result.criteria);
        }

        switch (notifyMode.get()) {
            case Chat -> ChatUtils.sendMsg(Component.literal(msg));
            case ActionBar -> { if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(msg)); }
            case Both -> { ChatUtils.sendMsg(Component.literal(msg)); if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(msg)); }
            case Toast -> { if (mc.gui.toastManager() != null) mc.gui.toastManager().addToast(new net.minecraft.client.gui.components.toasts.SystemToast(net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("§6AutoFind " + result.category), Component.literal(result.criteria))); }
            case All -> { ChatUtils.sendMsg(Component.literal(msg)); if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(msg)); if (mc.gui.toastManager() != null) mc.gui.toastManager().addToast(new net.minecraft.client.gui.components.toasts.SystemToast(net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("§6AutoFind " + result.category), Component.literal(result.criteria))); }
            case None -> {}
        }

        if (createWaypoints.get()) {
            try {
                Object waypointsObj = Class.forName("meteordevelopment.meteorclient.systems.waypoints.Waypoints").getMethod("get").invoke(null);
                Object wp = Class.forName("meteordevelopment.meteorclient.systems.waypoints.Waypoint").getConstructor().newInstance();
                wp.getClass().getField("name").set(wp, result.category + " " + result.blockCount);
                Object mutablePos = wp.getClass().getField("pos").get(wp);
                mutablePos.getClass().getMethod("set", double.class, double.class, double.class).invoke(mutablePos, result.pos.getX(), result.pos.getY(), result.pos.getZ());
                wp.getClass().getField("dimension").set(wp, mc.level.dimension());
                waypointsObj.getClass().getMethod("add", Class.forName("meteordevelopment.meteorclient.systems.waypoints.Waypoint")).invoke(waypointsObj, wp);
            } catch (Exception ignored) {}
        }

        if (logResults.get()) saveResultToFile(result);
    }

    private void saveResultToFile(FindResult result) {
        try {
            File logFile = new File("orbiter_autofind.txt");
            boolean created = logFile.createNewFile();
            try (FileWriter writer = new FileWriter(logFile, true)) {
                if (created) writer.write("AutoFind Log\n========================\n");
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(result.timestamp));
                writer.write(String.format("[%s] %s\n", time, result.toDisplayString()));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void addRenderStorage(BlockPos pos, Color color) {
        BlockPos immutable = pos.immutable();
        if (!renderStoragePositions.contains(immutable)) {
            renderStoragePositions.add(immutable);
            renderStorageColors.put(immutable, new Color(color.r, color.g, color.b, lineOpacity.get()));
            if (renderStoragePositions.size() > MAX_RENDER_STORAGE) {
                BlockPos oldest = renderStoragePositions.remove(0);
                renderStorageColors.remove(oldest);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        for (FindResult result : results) {
            Color fill, line;
            if (result.category.equals("STASH")) {
                fill = new Color(STASH_COLOR_LINE.r, STASH_COLOR_LINE.g, STASH_COLOR_LINE.b, fillOpacity.get());
                line = new Color(STASH_COLOR_LINE.r, STASH_COLOR_LINE.g, STASH_COLOR_LINE.b, lineOpacity.get());
            } else {
                fill = new Color(BASE_COLOR_LINE.r, BASE_COLOR_LINE.g, BASE_COLOR_LINE.b, fillOpacity.get());
                line = new Color(BASE_COLOR_LINE.r, BASE_COLOR_LINE.g, BASE_COLOR_LINE.b, lineOpacity.get());
            }

            BlockPos p = result.pos;
            renderEsp(event, p, fill, line);
            renderEsp(event, p.above(), fill, line);
            renderEsp(event, p.below(), fill, line);

            if (tracers.get() && mc.player != null) {
                Vec3 cam = mc.player.getEyePosition();
                event.renderer.line(cam.x, cam.y, cam.z, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, line);
            }
        }

        if (renderStashBlocks.get() || findStorage.get()) {
            for (BlockPos pos : renderStoragePositions) {
                Color lineColor = renderStorageColors.getOrDefault(pos, STORAGE_OTHER_LINE);
                Color fillColor = new Color(lineColor.r, lineColor.g, lineColor.b, fillOpacity.get());
                Color outlineColor = new Color(lineColor.r, lineColor.g, lineColor.b, lineOpacity.get());
                renderEsp(event, pos, fillColor, outlineColor);
            }
        }
    }

    private void renderEsp(Render3DEvent event, BlockPos pos, Color fill, Color line) {
        switch (espMode.get()) {
            case Full -> event.renderer.box(pos, fill, line, ShapeMode.Both, 0);
            case Wireframe -> event.renderer.box(pos, fill, line, ShapeMode.Lines, 0);
            case Sides -> event.renderer.box(pos, fill, line, ShapeMode.Sides, 0);
            case Corners -> renderCorners(event, pos, fill, line);
        }
    }

    private void renderCorners(Render3DEvent event, BlockPos pos, Color fill, Color line) {
        double x1 = pos.getX(), y1 = pos.getY(), z1 = pos.getZ();
        double x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1;
        double cl = cornerLen.get();
        drawCornerEdges(event, x1, y1, z1, cl, cl, cl, line);
        drawCornerEdges(event, x2, y1, z1, -cl, cl, cl, line);
        drawCornerEdges(event, x1, y1, z2, cl, cl, -cl, line);
        drawCornerEdges(event, x2, y1, z2, -cl, cl, -cl, line);
        drawCornerEdges(event, x1, y2, z1, cl, -cl, cl, line);
        drawCornerEdges(event, x2, y2, z1, -cl, -cl, cl, line);
        drawCornerEdges(event, x1, y2, z2, cl, -cl, -cl, line);
        drawCornerEdges(event, x2, y2, z2, -cl, -cl, -cl, line);
    }

    private void drawCornerEdges(Render3DEvent event, double cx, double cy, double cz,
                                  double dx, double dy, double dz, Color color) {
        event.renderer.line(cx, cy, cz, cx + dx, cy, cz, color);
        event.renderer.line(cx, cy, cz, cx, cy + dy, cz, color);
        event.renderer.line(cx, cy, cz, cx, cy, cz + dz, color);
    }

    public List<FindResult> getResults() { return Collections.unmodifiableList(results); }

    public void clearResults() {
        results.clear();
        scannedChunks.clear();
        renderStoragePositions.clear();
        renderStorageColors.clear();
    }
}
