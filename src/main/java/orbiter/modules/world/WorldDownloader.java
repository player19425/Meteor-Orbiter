package orbiter.modules.world;

import orbiter.Orbiter;
import orbiter.compat.ClientCompat;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import orbiter.compat.PacketCompat;

public class WorldDownloader extends Module {

    public enum DetectMode {
        Off,
        Manual,
        DetectAll
    }

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgChunks     = settings.createGroup("Chunks");
    private final SettingGroup sgContainers = settings.createGroup("Containers");
    private final SettingGroup sgEntities   = settings.createGroup("Entities");
    private final SettingGroup sgDetectAll  = settings.createGroup("Detect All");

    private final Setting<String> worldName = sgGeneral.add(new StringSetting.Builder()
        .name("world-name")
        .description("Folder name to save the world under. Empty = automatic based on server IP.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> autoStart = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-start")
        .description("Start saving automatically when joining a remote server.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> statusMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("status-messages")
        .description("Print status messages in the action bar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgChunks.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Radius (in chunks) around the player to save.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 32)
        .build()
    );

    private final Setting<Integer> saveIntervalTicks = sgChunks.add(new IntSetting.Builder()
        .name("save-interval")
        .description("Ticks between background chunk saves.")
        .defaultValue(40)
        .min(5)
        .sliderRange(5, 600)
        .build()
    );

    private final Setting<Boolean> saveOnlyChanged = sgChunks.add(new BoolSetting.Builder()
        .name("only-changed-chunks")
        .description("Re-saves a chunk only when a block update touched it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chunksPerTickBudget = sgChunks.add(new IntSetting.Builder()
        .name("chunks-per-tick-budget")
        .description("Chunks encoded per save cycle, so a big radius never hitches a frame.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> tickBudgetMs = sgChunks.add(new IntSetting.Builder()
        .name("tick-budget-ms")
        .description("Milliseconds per tick spent encoding chunks. More fills the world faster, less keeps the game smooth.")
        .defaultValue(4)
        .min(1)
        .max(50)
        .sliderRange(1, 25)
        .build()
    );

    private final Setting<Boolean> saveContainers = sgContainers.add(new BoolSetting.Builder()
        .name("save-containers")
        .description("Capture chest, barrel, shulker, hopper, etc. inventories when opened.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveEntities = sgEntities.add(new BoolSetting.Builder()
        .name("save-entities")
        .description("Capture all entities (mobs, villagers, item frames, armor stands, etc).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveAllEntityData = sgEntities.add(new BoolSetting.Builder()
        .name("save-all-entity-data")
        .description("Save full entity NBT including equipment, custom names, hologram text, trades.")
        .defaultValue(true)
        .visible(saveEntities::get)
        .build()
    );

    private final Setting<Boolean> saveShopkeeperTrades = sgEntities.add(new BoolSetting.Builder()
        .name("save-shopkeeper-trades")
        .description("Save merchant trade data for any entity that opens a trade GUI.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveHorseInventories = sgEntities.add(new BoolSetting.Builder()
        .name("save-horse-inventories")
        .description("Capture horse, donkey, llama inventories when opened.")
        .defaultValue(true)
        .build()
    );

    private final Setting<DetectMode> detectMode = sgDetectAll.add(new EnumSetting.Builder<DetectMode>()
        .name("detect-mode")
        .description("DetectAll opens nearby containers by itself, Manual waits for you.")
        .defaultValue(DetectMode.Manual)
        .build()
    );

    private final Setting<Integer> detectRadius = sgDetectAll.add(new IntSetting.Builder()
        .name("detect-radius")
        .description("Blocks around the player to scan in DetectAll mode.")
        .defaultValue(6)
        .min(2)
        .sliderRange(2, 16)
        .visible(() -> detectMode.get() == DetectMode.DetectAll)
        .build()
    );

    private final Setting<Integer> detectIntervalTicks = sgDetectAll.add(new IntSetting.Builder()
        .name("detect-interval")
        .description("Ticks between detect-all scans.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 200)
        .visible(() -> detectMode.get() == DetectMode.DetectAll)
        .build()
    );

    private SwdSaveManager saveManager;
    private Screen lastScreen;
    private Object lastClicked;
    private final Set<UUID> interactedEntities = new HashSet<>();
    private final Set<BlockPos> openedContainers = new HashSet<>();
    private MerchantMenu capturedMerchant = null;
    private int saveTickCounter = 0;
    private int detectTickCounter = 0;
    private int entityScanCounter = 0;
    private int lastContainerOpenTick = 0;
    private final ConcurrentLinkedQueue<Long> queuedChunkSaves = new ConcurrentLinkedQueue<>();
    private long detectScanCursor = 0;
    private BlockPos pendingDetectPos = null;
    private int detectPendingTicks = 0;
    private boolean suppressAutoMark = false;

    private final Set<Long> savedChunks = ConcurrentHashMap.newKeySet();
    private int chunksSaved = 0;
    private int entitiesSaved = 0;
    private int statusCounter = 0;
    private long startTimeMs = 0L;
    private Path activeSavePath = null;
    private long lastEntityScanMs = 0L;

    private static final int MAX_QUEUED_CHUNK_SAVES = 512;
    private static final int DETECT_SCAN_BUDGET = 512;
    private static final int DETECT_OPEN_TIMEOUT_TICKS = 10;

    private final Set<Long> dirtyChunks = ConcurrentHashMap.newKeySet();

    public WorldDownloader() {
        super(Orbiter.CATEGORY_OP, "world-downloader",
            "Saves the server world locally.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }

        Path savePath = buildSavePath();
        saveManager = new SwdSaveManager(mc, savePath);
        saveManager.start(worldName.get().isBlank() ? savePath.getFileName().toString() : worldName.get());

        lastClicked = null;
        lastScreen = null;
        capturedMerchant = null;
        lastContainerOpenTick = 0;
        interactedEntities.clear();
        openedContainers.clear();
        dirtyChunks.clear();
        queuedChunkSaves.clear();
        pendingDetectPos = null;
        detectPendingTicks = 0;
        detectScanCursor = 0;
        suppressAutoMark = false;
        saveTickCounter = 0;
        detectTickCounter = 0;
        entityScanCounter = 0;
        savedChunks.clear();
        chunksSaved = 0;
        entitiesSaved = 0;
        startTimeMs = System.currentTimeMillis();
        activeSavePath = savePath;

        if (statusMessages.get()) {
            info("WDL: %s", "saving to " + savePath);
            info("WDL: %s", "radius " + chunkRadius.get() + " chunks, entities " + (saveEntities.get() ? "on" : "off")
                + ", full entity data " + (saveAllEntityData.get() ? "on" : "off")
                + ", containers " + (saveContainers.get() ? "on" : "off"));
        }
    }

    @Override
    public void onDeactivate() {
        final SwdSaveManager manager = saveManager;
        final long startedAt = startTimeMs;
        final Path target = activeSavePath;
        final long scanMs = lastEntityScanMs;

        if (manager != null) {
            if (mc.player != null) manager.savePlayerInventory(mc.player.getUUID(), mc.player.getInventory());
            reportWriteErrors();
            manager.setFinishHandler((discarded, errors) -> mc.execute(() -> {
                for (String problem : errors) error("WDL: %s", problem);
                if (discarded > 0) error("WDL: %s", discarded + " writes were dropped before they reached the disk");
                if (!statusMessages.get()) return;
                long elapsed = startedAt > 0 ? (System.currentTimeMillis() - startedAt) / 1000L : 0L;
                info("WDL: %s", "stopped after " + elapsed + "s");
                info("WDL: %s", "wrote " + manager.chunksWritten() + " chunks and " + manager.entitiesWritten() + " entities, "
                    + formatBytes(manager.bytesWritten()) + ", last entity scan " + scanMs + "ms");
                if (target != null) info("WDL: %s", "world saved at " + target);
            }));
            saveManager = null;
            manager.close();
        }
        interactedEntities.clear();
        openedContainers.clear();
        dirtyChunks.clear();
        queuedChunkSaves.clear();
        pendingDetectPos = null;
        detectPendingTicks = 0;
        suppressAutoMark = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (autoStart.get() && !isActive() && mc.player != null && mc.level != null && !mc.hasSingleplayerServer()) {
            toggle();
        }
    }

    private Path buildSavePath() {
        String name = worldName.get();
        if (name == null || name.isBlank()) {
            if (mc.getCurrentServer() != null) name = sanitize(mc.getCurrentServer().ip);
            else name = "OrbiterWorld";
        } else name = sanitize(name);

        Path saves = mc.getLevelSource().getBaseDir();
        Path base = saves.resolve("OrbiterDL_" + name);
        if (!Files.exists(base)) return base;
        int i = 1;
        while (Files.exists(saves.resolve("OrbiterDL_" + name + "_" + i))) i++;
        return saves.resolve("OrbiterDL_" + name + "_" + i);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (saveManager == null || !saveManager.isSaving || mc.player == null || mc.level == null) return;

        long deadline = System.nanoTime() + tickBudgetMs.get() * 1_000_000L;

        drainQueuedChunkSaves(deadline);

        saveTickCounter++;
        if (saveTickCounter >= saveIntervalTicks.get()) {
            saveTickCounter = 0;
            reportWriteErrors();
            markUnsavedChunksDirty();
        }

        saveChunksAround(deadline);

        if (detectMode.get() == DetectMode.DetectAll) {
            if (pendingDetectPos != null) {
                if (ClientCompat.screen() instanceof AbstractContainerScreen<?>) {
                    openedContainers.add(pendingDetectPos);
                    pendingDetectPos = null;
                    detectPendingTicks = 0;
                    if (mc.player != null) mc.player.closeContainer();
                } else if (++detectPendingTicks >= DETECT_OPEN_TIMEOUT_TICKS) {
                    if (lastClicked == pendingDetectPos) {
                        lastClicked = null;
                        saveManager.lastClicked = null;
                    }
                    pendingDetectPos = null;
                    detectPendingTicks = 0;
                }
            } else {
                detectTickCounter++;
                if (detectTickCounter >= detectIntervalTicks.get()) {
                    detectTickCounter = 0;
                    scanAndOpenNearbyContainers();
                }
            }
        }

        if (saveEntities.get()) {
            entityScanCounter++;
            if (entityScanCounter >= 20) {
                entityScanCounter = 0;
                saveEntitiesAround();
            }
        }

        updateStatus();
    }

    private void scanAndOpenNearbyContainers() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (ClientCompat.screen() != null) return;
        if (lastContainerOpenTick > 0 && mc.player.tickCount - lastContainerOpenTick < 20) return;

        BlockPos center = mc.player.blockPosition();
        int r = detectRadius.get();
        int size = 2 * r + 1;
        long total = (long) size * size * size;
        int examined = 0;

        for (; examined < DETECT_SCAN_BUDGET && examined < total; examined++) {
            long idx = (detectScanCursor + examined) % total;
            int dx = (int) (idx % size) - r;
            int dy = (int) ((idx / size) % size) - r;
            int dz = (int) (idx / ((long) size * size)) - r;

            BlockPos pos = center.offset(dx, dy, dz);
            if (openedContainers.contains(pos)) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (!isContainerBlock(state)) continue;

            BlockEntity be = mc.level.getBlockEntity(pos);
            if (!(be instanceof Container)) continue;

            pendingDetectPos = pos;
            detectPendingTicks = 0;
            lastClicked = pos;
            saveManager.lastClicked = pos;
            lastContainerOpenTick = mc.player.tickCount;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            suppressAutoMark = true;
            try {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            } finally {
                suppressAutoMark = false;
            }
            examined++;
            break;
        }

        detectScanCursor = (detectScanCursor + Math.max(examined, 1)) % total;
    }

    private boolean isContainerBlock(BlockState state) {
        if (state.getBlock() instanceof ChestBlock) return true;
        Block block = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return id.contains("barrel")
            || id.contains("shulker")
            || id.contains("hopper")
            || id.contains("dispenser")
            || id.contains("dropper")
            || id.contains("furnace")
            || id.contains("smoker")
            || id.contains("blast_furnace")
            || id.contains("brewing_stand")
            || id.contains("lectern")
            || id.contains("crafter");
    }

    private void markUnsavedChunksDirty() {
        if (mc.player == null || mc.level == null) return;
        int r = chunkRadius.get();
        int pcx = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).x();
        int pcz = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).z();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.pack(pcx + dx, pcz + dz);
                if (!savedChunks.contains(key)) dirtyChunks.add(key);
            }
        }
    }

    private void saveChunksAround(long deadline) {
        if (mc.player == null || mc.level == null || saveManager == null) return;
        int r = chunkRadius.get();
        int pcx = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).x();
        int pcz = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).z();

        int budget = Math.max(1, chunksPerTickBudget.get());

        int processed = 0;
        for (int dx = -r; dx <= r && processed < budget; dx++) {
            for (int dz = -r; dz <= r && processed < budget; dz++) {
                long key = ChunkPos.pack(pcx + dx, pcz + dz);
                if (!dirtyChunks.remove(key)) continue;
                LevelChunk chunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, false);
                if (chunk == null) continue;
                saveManager.saveChunk(chunk);
                savedChunks.add(key);
                chunksSaved++;
                processed++;
                if (System.nanoTime() >= deadline) return;
            }
        }

        if (dirtyChunks.size() > 1024) {
            Iterator<Long> it = dirtyChunks.iterator();
            while (it.hasNext()) {
                long k = it.next();
                int cx = ChunkPos.getX(k);
                int cz = ChunkPos.getZ(k);
                if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) it.remove();
            }
        }

        if (savedChunks.size() > 8192) {
            Iterator<Long> it = savedChunks.iterator();
            while (it.hasNext()) {
                long k = it.next();
                int cx = ChunkPos.getX(k);
                int cz = ChunkPos.getZ(k);
                if (Math.abs(cx - pcx) > r * 2 || Math.abs(cz - pcz) > r * 2) it.remove();
            }
        }
    }

    private void saveEntitiesAround() {
        if (mc.player == null || mc.level == null || saveManager == null) return;
        if (!saveEntities.get()) return;
        saveManager.saveFullEntityData = saveAllEntityData.get();
        int r = chunkRadius.get();
        int pcx = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).x();
        int pcz = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).z();

        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.getHeight();
        int fromX = pcx - r;
        int toX = pcx + r;
        int fromZ = pcz - r;
        int toZ = pcz + r;

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(fromX * 16, minY, fromZ * 16,
            (toX + 1) * 16, maxY, (toZ + 1) * 16);

        long started = System.nanoTime();
        java.util.List<Entity> found = mc.level.getEntities(EntityTypeTest.forClass(Entity.class), box, e -> !(e instanceof Player));
        lastEntityScanMs = (System.nanoTime() - started) / 1_000_000L;

        if (found.isEmpty()) return;

        java.util.Map<Long, java.util.List<Entity>> byChunk = new java.util.HashMap<>();
        for (Entity entity : found) {
            if (entity == null) continue;
            int ex = entity.blockPosition().getX() >> 4;
            int ez = entity.blockPosition().getZ() >> 4;
            if (ex < fromX || ex > toX || ez < fromZ || ez > toZ) continue;
            long key = net.minecraft.world.level.ChunkPos.pack(ex, ez);
            byChunk.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(entity);
        }

        int written = 0;
        for (java.util.Map.Entry<Long, java.util.List<Entity>> entry : byChunk.entrySet()) {
            int ex = net.minecraft.world.level.ChunkPos.getX(entry.getKey());
            int ez = net.minecraft.world.level.ChunkPos.getZ(entry.getKey());
            written += saveManager.saveEntities(new net.minecraft.world.level.ChunkPos(ex, ez), entry.getValue());
        }
        entitiesSaved += written;
    }

    private void updateStatus() {
        statusCounter++;
        if (statusCounter < 20) return;
        statusCounter = 0;
        if (!statusMessages.get() || mc.player == null || saveManager == null) return;

        long elapsed = startTimeMs > 0 ? (System.currentTimeMillis() - startTimeMs) / 1000L : 0L;
        String text = "§6[WDL] §fchunks §a" + chunksSaved
            + "§f  entities §a" + entitiesSaved
            + "§f  " + formatBytes(saveManager.bytesWritten())
            + "§f  queued §a" + queuedChunkSaves.size()
            + "§f  " + elapsed + "s";
        mc.player.sendOverlayMessage(Component.literal(text));
    }

    private void reportWriteErrors() {
        if (saveManager == null) return;
        List<String> errors = saveManager.drainWriteErrors();
        for (String problem : errors) {
            error("WDL: %s", problem);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return (bytes / 1024L) + " KB";
        long tenths = (bytes * 10L) / (1024L * 1024L);
        return (tenths / 10L) + "." + (tenths % 10L) + " MB";
    }

    private void enqueueChunkSave(int x, int z) {
        long key = ChunkPos.pack(x, z);
        while (queuedChunkSaves.size() >= MAX_QUEUED_CHUNK_SAVES) queuedChunkSaves.poll();
        queuedChunkSaves.offer(key);
    }

    private void drainQueuedChunkSaves(long deadline) {
        if (mc.level == null || saveManager == null) {
            queuedChunkSaves.clear();
            return;
        }
        Long key;
        while ((key = queuedChunkSaves.poll()) != null) {
            LevelChunk chunk = mc.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
            if (chunk != null) {
                saveManager.saveChunk(chunk);
                savedChunks.add(key);
                chunksSaved++;
            }
            if (System.nanoTime() >= deadline) break;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (saveManager == null || !saveManager.isSaving) return;
        if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {

            enqueueChunkSave(PacketCompat.chunkX(packet), PacketCompat.chunkZ(packet));
        } else if (event.packet instanceof ClientboundBlockUpdatePacket packet) {

            if (mc.level != null && saveOnlyChanged.get()) {
                dirtyChunks.add(ChunkPos.pack(packet.getPos()));
            }
        } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {

            if (mc.level != null && saveOnlyChanged.get()) {
                packet.runUpdates((pos, state) -> dirtyChunks.add(ChunkPos.pack(pos)));
            }
        } else if (event.packet instanceof ClientboundAwardStatsPacket packet) {
            saveManager.cacheStatsPacket(packet);
        } else if (event.packet instanceof ClientboundUpdateAdvancementsPacket packet) {
            saveManager.cacheAdvancementPacket(packet);
        }
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (saveManager == null || !saveManager.isSaving) return;
        Entity entity = event.entity;
        if (entity == null) return;
        interactedEntities.add(entity.getUUID());
        lastClicked = entity;
        saveManager.lastClicked = entity;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (saveManager == null || mc.player == null) return;
        if (event.packet instanceof ServerboundUseItemOnPacket blockPacket) {
            BlockPos pos = PacketCompat.useItemOnHitResult(blockPacket).getBlockPos();
            lastClicked = pos;
            saveManager.lastClicked = pos;
            if (!suppressAutoMark) openedContainers.add(pos);
        }
    }

    @EventHandler
    private void onTickCheckScreen(TickEvent.Pre event) {
        if (saveManager == null || !saveManager.isSaving) return;
        Screen current = ClientCompat.screen();

        if (current instanceof MerchantScreen && saveShopkeeperTrades.get()) {
            if (mc.player.containerMenu instanceof MerchantMenu merchant) {
                capturedMerchant = merchant;
            }
        }

        if (lastScreen != null && current == null) {
            captureScreenData(lastScreen);
            capturedMerchant = null;
        }
        lastScreen = current;
    }

    private void captureScreenData(Screen closed) {
        if (closed == null || mc.player == null || saveManager == null) return;

        if (closed instanceof MerchantScreen && saveShopkeeperTrades.get()) {
            if (lastClicked instanceof Entity entity && capturedMerchant != null) {
                saveManager.saveFullEntityData = saveAllEntityData.get();
                CompoundTag overlay = new CompoundTag();
                MerchantOffers offers = capturedMerchant.getOffers();
                if (!offers.isEmpty()) {
                    RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
                    MerchantOffers.CODEC.encodeStart(ops, offers)
                        .result().ifPresent(t -> overlay.put("Offers", t));
                }
                overlay.putInt("Xp", capturedMerchant.getTraderXp());
                saveManager.cacheEntityOverride(entity.getUUID(), overlay);
                saveManager.saveChunkAt(entity.blockPosition());
                if (statusMessages.get()) info("Saved trade data for " + entity.getName().getString());
            }
            return;
        }

        if (saveContainers.get() && closed instanceof AbstractContainerScreen<?> handled) {
            List<ItemStack> items = new ArrayList<>(handled.getMenu().getItems());

            if (closed.getTitle().getString().equals(Component.translatable("container.enderchest").getString())) {
                List<ItemStack> chestItems = new ArrayList<>(items.subList(0, Math.min(27, items.size())));
                saveManager.cacheEnderChest(mc.player.getUUID(), chestItems);
                if (statusMessages.get()) info("Saved ender chest");
                return;
            }

            int playerInvCount = 36;
            if (items.size() > playerInvCount) {
                items = new ArrayList<>(items.subList(0, items.size() - playerInvCount));
            }

            if (lastClicked instanceof BlockPos pos) {
                saveManager.cacheBlockInventory(pos, items);
                saveManager.saveChunkAt(pos);
                if (statusMessages.get()) info("Saved container at " + pos);
            } else if (lastClicked instanceof Entity entity && interactedEntities.contains(entity.getUUID())) {
                if (entity instanceof AbstractHorse && !saveHorseInventories.get()) return;
                saveManager.cacheEntityInventory(entity.getUUID(), new ArrayList<>(items));
                saveManager.saveChunkAt(entity.blockPosition());
                if (statusMessages.get()) info("Saved entity inventory for " + entity.getName().getString());
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (saveManager != null) {
            saveManager.drainWriteErrors();
            saveManager.close();
            saveManager = null;
        }
        interactedEntities.clear();
        openedContainers.clear();
        dirtyChunks.clear();
        queuedChunkSaves.clear();
        pendingDetectPos = null;
        capturedMerchant = null;
        lastScreen = null;
        lastClicked = null;
        detectPendingTicks = 0;
        suppressAutoMark = false;
    }
}
