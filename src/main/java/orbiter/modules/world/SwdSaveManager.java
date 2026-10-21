package orbiter.modules.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;

import net.minecraft.stats.Stat;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ProblemReporter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.LevelChunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import orbiter.compat.PacketCompat;

public class SwdSaveManager implements AutoCloseable {

    private static final int FALLBACK_DATA_VERSION = 4671;

    /**
     * The data version of the Minecraft this build runs on. The world the
     * downloader writes has to carry the version of the game that opens it,
     * or the game treats it as an old world and tries to convert it.
     */
    private static int dataVersion() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
        } catch (Throwable t) {
            return FALLBACK_DATA_VERSION;
        }
    }

    /** The Minecraft version this build runs on, for the level.dat header. */
    private static String versionName() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().name();
        } catch (Throwable t) {
            return "1.21.11";
        }
    }

    /** The world series, "main" for every release. */
    private static String versionSeries() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().dataVersion().series();
        } catch (Throwable t) {
            return "main";
        }
    }

    public final Path path;
    public volatile boolean isSaving = false;
    public Object lastClicked = null;

    private final net.minecraft.client.Minecraft mc;
    private final DynamicOps<Tag> ops;
    private final SwdRegionStorage regionStorage;
    private final Path regionDir;
    private final SwdRegionStorage entityStorage;
    private final Path entitiesDir;
    public boolean saveFullEntityData = true;
    private final Map<BlockPos, List<ItemStack>> blockInventoryCache = new HashMap<>();
    private final Map<UUID, List<ItemStack>> entityInventoryCache = new HashMap<>();
    private final Map<UUID, CompoundTag> entityOverrideCache = new HashMap<>();

    private ExecutorService diskWorker;
    private final AtomicInteger inFlightWrites = new AtomicInteger(0);
    private volatile FinishHandler finishHandler;

    private final ConcurrentLinkedQueue<String> writeErrors = new ConcurrentLinkedQueue<>();

    private final AtomicInteger chunksWritten = new AtomicInteger(0);
    private final AtomicInteger entitiesWritten = new AtomicInteger(0);
    private final AtomicInteger entityChunksWritten = new AtomicInteger(0);
    private final AtomicLong bytesWritten = new AtomicLong(0L);

    private final Object metaLock = new Object();

    private static final long META_FLUSH_INTERVAL_MS = 5000L;
    private static final DateTimeFormatter ADVANCEMENT_TIME_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)
        .withZone(ZoneId.systemDefault());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonObject cachedStatsByType;
    private JsonObject cachedAdvancements;
    private Set<String> removedAdvancements;
    private boolean advancementsResetThisSession;
    private boolean statsDirty;
    private boolean advancementsDirty;
    private long lastMetaFlushTimeMs;
    private volatile UUID cachePlayerUuid;

    public SwdSaveManager(net.minecraft.client.Minecraft mc, Path path) {
        this.mc = mc;
        this.path = path;
        this.ops = mc.level != null
            ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess())
            : NbtOps.INSTANCE;
        this.regionDir = path.resolve("region");
        this.entitiesDir = path.resolve("entities");
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim =
            mc.level != null ? mc.level.dimension() : net.minecraft.world.level.Level.OVERWORLD;
        this.regionStorage = new SwdRegionStorage(regionDir, dim);
        this.entityStorage = new SwdRegionStorage(entitiesDir, dim);
    }

    public void start(String name) {
        isSaving = true;

        diskWorker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Orbiter-WDL-DiskWorker");
            t.setDaemon(true);
            return t;
        });
        cachedStatsByType = new JsonObject();
        cachedAdvancements = new JsonObject();
        removedAdvancements = new HashSet<>();
        advancementsResetThisSession = false;
        statsDirty = false;
        advancementsDirty = false;
        lastMetaFlushTimeMs = 0L;
        cachePlayerUuid = mc.player != null ? mc.player.getUUID() : null;
        try {
            Files.createDirectories(path);
            Files.createDirectories(regionDir);
            Files.createDirectories(entitiesDir);
            createLevelDat(name);
            bootstrapAdvancementsFromClientCache();
        } catch (IOException e) {
            isSaving = false;
            if (diskWorker != null) {
                diskWorker.shutdownNow();
                diskWorker = null;
            }
            releaseSessionLock();
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            flushPlayerMetaFiles(true);
        } catch (Exception e) {
            writeErrors.offer("Failed to flush the session metadata: " + e.getMessage());
        }

        isSaving = false;

        ExecutorService worker = diskWorker;
        diskWorker = null;
        if (worker != null) worker.shutdown();

        Thread closer = new Thread(() -> {
            int discarded = 0;
            if (worker != null) {
                try {
                    if (!worker.awaitTermination(60, TimeUnit.SECONDS)) {
                        worker.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    worker.shutdownNow();
                }
            }

            discarded = inFlightWrites.getAndSet(0);
            closeStorages();

            List<String> errors = drainWriteErrors();
            FinishHandler handler = finishHandler;
            if (handler != null) handler.onFinished(discarded, errors);
        }, "Orbiter-WDL-Closer");
        closer.setDaemon(true);
        closer.start();
    }

    private void closeStorages() {
        try {
            regionStorage.close();
        } catch (IOException ignored) {}
        try {
            entityStorage.close();
        } catch (IOException ignored) {}
        releaseSessionLock();
        blockInventoryCache.clear();
        entityInventoryCache.clear();
        entityOverrideCache.clear();
    }

    public void setFinishHandler(FinishHandler handler) {
        this.finishHandler = handler;
    }

    public interface FinishHandler {
        void onFinished(int discardedWrites, List<String> errors);
    }

    private void releaseSessionLock() {
        try {
            Files.deleteIfExists(path.resolve("session.lock"));
        } catch (IOException ignored) {}
    }

    public List<String> drainWriteErrors() {
        if (writeErrors.isEmpty()) return List.of();
        List<String> errors = new ArrayList<>();
        String error;
        while ((error = writeErrors.poll()) != null) {
            System.err.println("[SwdSaveManager] " + error);
            errors.add(error);
        }
        return errors;
    }

    public int chunksWritten() {
        return chunksWritten.get();
    }

    public int entitiesWritten() {
        return entitiesWritten.get();
    }

    public int entityChunksWritten() {
        return entityChunksWritten.get();
    }

    public long bytesWritten() {
        return bytesWritten.get();
    }

    private void createLevelDat(String name) throws IOException {
        Files.createDirectories(path);
        CompoundTag data = new CompoundTag();

        data.putInt("DataVersion", dataVersion());
        data.putString("LevelName", name);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putInt("version", 19133);
        data.putInt("GameType", 1);
        data.putByte("initialized", (byte) 1);
        data.putByte("allowCommands", (byte) 1);

        CompoundTag difficulty = new CompoundTag();
        difficulty.putString("difficulty", "normal");
        difficulty.putByte("hardcore", (byte) 0);
        difficulty.putByte("locked", (byte) 0);
        data.put("difficulty_settings", difficulty);

        data.putLong("Time", 0L);
        data.putLong("DayTime", 0L);

        CompoundTag spawn = new CompoundTag();
        spawn.putFloat("pitch", 0);
        spawn.putFloat("yaw", 0);
        spawn.putString("dimension", "minecraft:overworld");
        spawn.putIntArray("pos", new int[]{
            mc.player != null ? mc.player.blockPosition().getX() : 0,
            mc.player != null ? mc.player.blockPosition().getY() : 64,
            mc.player != null ? mc.player.blockPosition().getZ() : 0
        });
        data.put("spawn", spawn);

        CompoundTag version = new CompoundTag();
        version.putString("Name", versionName());
        version.putInt("Id", dataVersion());
        version.putString("Series", versionSeries());
        version.putByte("Snapshot", (byte) 0);
        data.put("Version", version);

        CompoundTag dragonFight = new CompoundTag();
        dragonFight.putByte("DragonKilled", (byte) 1);
        dragonFight.putByte("DragonPreviouslyKilled", (byte) 1);
        dragonFight.put("EndGatewayList", new ListTag());
        dragonFight.putIntArray("ExitPortalLocation", new int[]{0, 64, 0});
        dragonFight.put("Gateways", new ListTag());
        data.put("DragonFight", dragonFight);

        CompoundTag gameRules = new CompoundTag();
        gameRules.putByte("minecraft:do_daylight_cycle", (byte) 1);
        gameRules.putByte("minecraft:do_weather_cycle", (byte) 1);
        gameRules.putInt("minecraft:random_tick_speed", 0);
        data.put("game_rules", gameRules);

        CompoundTag dataPacks = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(net.minecraft.nbt.StringTag.valueOf("vanilla"));
        dataPacks.put("Enabled", enabled);
        ListTag disabled = new ListTag();
        dataPacks.put("Disabled", disabled);
        data.put("DataPacks", dataPacks);

        CompoundTag worldGenSettings = new CompoundTag();
        worldGenSettings.putByte("bonus_chest", (byte) 0);
        worldGenSettings.putByte("generate_structures", (byte) 0);
        worldGenSettings.putLong("seed", 0L);
        CompoundTag dimensions = new CompoundTag();
        dimensions.put("minecraft:overworld", createDimensionEntry("minecraft:overworld", "minecraft:plains"));
        dimensions.put("minecraft:the_nether", createDimensionEntry("minecraft:the_nether", "minecraft:the_nether"));
        dimensions.put("minecraft:the_end", createDimensionEntry("minecraft:the_end", "minecraft:the_end"));
        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        writeCompressedTag(root, path.resolve("level.dat"));

        long now = System.currentTimeMillis();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8).putLong(now);
        Files.write(path.resolve("session.lock"), buf.array());
    }

    private void writeCompressedTag(CompoundTag tag, Path target) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, temp);
        moveIntoPlace(temp, target);
    }

    private CompoundTag readOrCreateTag(Path file) {
        if (!Files.exists(file)) return new CompoundTag();
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (tag == null) return new CompoundTag();
            return tag;
        } catch (IOException | RuntimeException e) {
            System.err.println("Unreadable save at " + file + ", rebuilding it: " + e.getMessage());
            return new CompoundTag();
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CompoundTag createDimensionEntry(String type, String biome) {
        CompoundTag dim = new CompoundTag();
        dim.putString("type", type);

        CompoundTag generator = new CompoundTag();
        generator.putString("type", "minecraft:flat");

        CompoundTag settings = new CompoundTag();
        settings.putByte("features", (byte) 0);
        settings.putString("biome", biome);
        settings.put("layers", new ListTag());
        settings.putByte("lakes", (byte) 0);
        settings.put("structure_overrides", new ListTag());
        generator.put("settings", settings);

        dim.put("generator", generator);
        return dim;
    }

    public void saveChunk(LevelChunk chunk) {
        if (!isSaving || chunk == null) return;

        if (mc.level == null) return;
        try {

            final CompoundTag nbt = buildChunkNbt(chunk);
            if (nbt == null) return;
            final ChunkPos pos = chunk.getPos();
            submitDiskWrite(() -> {
                try {
                    long bytes = regionStorage.write(pos, nbt);
                    chunksWritten.incrementAndGet();
                    bytesWritten.addAndGet(bytes);
                } catch (Exception e) {
                    writeErrors.offer("Failed to save chunk " + pos + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to build chunk NBT " + chunk.getPos() + ": " + e.getMessage());
        }
    }

    public void saveChunkAt(BlockPos pos) {
        if (mc.level == null) return;
        LevelChunk chunk = mc.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk != null) {
            saveChunk(chunk);
            saveEntitiesForChunk(chunk);
        }
    }

    public int saveEntitiesForChunk(LevelChunk chunk) {
        if (!isSaving || chunk == null) return 0;
        if (mc.level == null) return 0;
        try {
            return saveEntities(chunk.getPos(), collectChunkEntities(chunk));
        } catch (Exception e) {
            writeErrors.offer("Failed to read entities for chunk " + chunk.getPos() + ": " + e.getMessage());
            return 0;
        }
    }

    public int saveEntities(ChunkPos pos, List<Entity> entities) {
        if (!isSaving || entities == null || entities.isEmpty()) return 0;
        if (mc.level == null) return 0;
        try {
            ListTag list = new ListTag();
            for (Entity entity : entities) {
                CompoundTag tag = encodeEntity(entity);
                if (tag != null) list.add(tag);
            }
            if (list.isEmpty()) return 0;
            submitDiskWrite(() -> {
                try {
                    CompoundTag chunkTag = new CompoundTag();
                    chunkTag.put("Entities", list);
                    chunkTag.store("Position", ChunkPos.CODEC, pos);
                    long bytes = entityStorage.write(pos, chunkTag);
                    entitiesWritten.addAndGet(list.size());
                    entityChunksWritten.incrementAndGet();
                    bytesWritten.addAndGet(bytes);
                } catch (Exception e) {
                    writeErrors.offer("Failed to save entities for chunk " + pos + ": " + e.getMessage());
                }
            });
            return list.size();
        } catch (Exception e) {
            writeErrors.offer("Failed to encode entities for chunk " + pos + ": " + e.getMessage());
            return 0;
        }
    }

    private void submitDiskWrite(Runnable task) {
        if (diskWorker == null || diskWorker.isShutdown()) {

            runWriteSynchronized(task);
            return;
        }
        inFlightWrites.incrementAndGet();
        try {
            diskWorker.execute(() -> {
                try {
                    task.run();
                } finally {
                    inFlightWrites.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException rejected) {
            inFlightWrites.decrementAndGet();
            runWriteSynchronized(task);
        }
    }

    private void runWriteSynchronized(Runnable task) {
        synchronized (regionStorage) {
            task.run();
        }
    }

    private List<Entity> collectChunkEntities(LevelChunk chunk) {
        if (mc.level == null) return List.of();
        ChunkPos pos = chunk.getPos();
        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.getHeight();
        AABB box = new AABB(pos.getMinBlockX(), minY, pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1, maxY, pos.getMaxBlockZ() + 1);
        return mc.level.getEntities(EntityTypeTest.forClass(Entity.class), box, e -> !(e instanceof Player));
    }

    private CompoundTag buildChunkNbt(LevelChunk chunk) {

        if (mc.level == null) return null;
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("DataVersion", dataVersion());
        nbt.putInt("xPos", chunk.getPos().x());
        nbt.putInt("yPos", chunk.getMinSectionY());
        nbt.putInt("zPos", chunk.getPos().z());
        nbt.putLong("LastUpdate", 0L);
        nbt.putLong("InhabitedTime", 0L);
        nbt.putString("Status", "full");

        writeSections(chunk, nbt);
        writeBlockEntities(chunk, nbt);
        return nbt;
    }

    private void writeSections(LevelChunk chunk, CompoundTag nbt) {
        Registry<Biome> biomeRegistry = mc.level != null ? mc.level.registryAccess().lookupOrThrow(Registries.BIOME) : null;
        if (biomeRegistry == null) return;

        Holder<Biome> defaultBiome = biomeRegistry.getOptional(Biomes.PLAINS).map(biomeRegistry::wrapAsHolder).orElse(null);
        if (defaultBiome == null) return;

        net.minecraft.world.level.chunk.PalettedContainerFactory factory = net.minecraft.world.level.chunk.PalettedContainerFactory.create(mc.level.registryAccess());
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = factory.biomeContainerCodec();
        Codec<PalettedContainer<BlockState>> blockCodec = factory.blockStatesContainerCodec();

        ListTag sections = new ListTag();
        LevelChunkSection[] sectionsArray = chunk.getSections();
        int bottomSection = chunk.getMinSectionY();
        for (int i = 0; i < sectionsArray.length; i++) {
            LevelChunkSection section = sectionsArray[i];
            if (section == null) continue;

            CompoundTag sec = new CompoundTag();
            sec.putByte("Y", (byte) (bottomSection + i));

            blockCodec.encodeStart(ops, section.getStates())
                .result()
                .ifPresent(tag -> sec.put("block_states", tag));

            biomeCodec.encodeStart(ops, section.getBiomes())
                .result()
                .ifPresent(tag -> sec.put("biomes", tag));

            sections.add(sec);
        }
        nbt.put("sections", sections);
    }

    private void writeBlockEntities(LevelChunk chunk, CompoundTag nbt) {
        ListTag list = new ListTag();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            CompoundTag tag = be.saveWithFullMetadata(mc.level.registryAccess());
            List<ItemStack> cached = blockInventoryCache.get(be.getBlockPos());
            if (cached != null && !cached.isEmpty()) {
                tag.put("Items", encodeItems(cached));
            }
            list.add(tag);
        }
        nbt.put("block_entities", list);
    }

    private CompoundTag encodeEntity(Entity entity) {
        if (mc.level == null) return null;
        CompoundTag tag = null;
        boolean fullSave = false;
        if (saveFullEntityData) {
            try {
                TagValueOutput view = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mc.level.registryAccess());
                if (entity.save(view)) {
                    tag = view.buildResult();
                    fullSave = true;
                }
            } catch (Exception e) {
                System.err.println("Full entity save failed for " + entity.getType() + ", falling back: " + e.getMessage());
            }
        }

        if (tag == null) {
            tag = encodeEntityMinimal(entity);
            if (tag == null) return null;
        }

        if (!fullSave) {
            encodeEntityExtra(tag, entity);
        }

        encodeDisplayEntityData(tag, entity);

        List<ItemStack> inv = entityInventoryCache.get(entity.getUUID());
        if (inv != null && !inv.isEmpty()) tag.put("Items", encodeItems(inv));

        CompoundTag override = entityOverrideCache.get(entity.getUUID());
        if (override != null) {
            for (String key : override.keySet()) tag.put(key, override.get(key));
        }

        return tag;
    }

    private void encodeEntityExtra(CompoundTag tag, Entity entity) {
        if (!tag.contains("id")) {
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (typeId != null) tag.putString("id", typeId.toString());
        }
        if (!tag.contains("Pos")) {
            tag.store("Pos", Vec3.CODEC, entity.position());
            tag.store("Motion", Vec3.CODEC, Vec3.ZERO);
        }
        if (!tag.contains("Rotation")) {
            tag.store("Rotation", Vec2.CODEC, new Vec2(entity.getYRot(), entity.getXRot()));
        }
        if (!tag.contains("UUID")) {
            tag.store("UUID", UUIDUtil.CODEC, entity.getUUID());
        }

        if (entity.hasCustomName() && entity.getCustomName() != null) {
            ComponentSerialization.CODEC.encodeStart(ops, entity.getCustomName())
                .result()
                .ifPresent(t -> tag.put("CustomName", t));
            tag.putBoolean("CustomNameVisible", true);
        }
        if (entity.isInvisible()) tag.putBoolean("Invisible", true);
        if (entity.isSilent()) tag.putBoolean("Silent", true);
        tag.putInt("Fire", entity.getRemainingFireTicks());
        tag.putInt("Air", entity.getAirSupply());
        tag.putBoolean("OnGround", entity.onGround());

        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            Tag gear = encodeEquipment(living);
            if (gear != null) tag.put("equipment", gear);
        }
    }

    private void encodeDisplayEntityData(CompoundTag tag, Entity entity) {
        if (entity instanceof net.minecraft.world.entity.Display.TextDisplay tde) {
            net.minecraft.world.entity.Display.TextDisplay.TextRenderState data = tde.textRenderState();
            if (data != null && data.text() != null) {

                if (!tag.contains("text")) {
                    ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data.text())
                        .result()
                        .ifPresent(json -> tag.putString("text", json.toString()));
                }
            }
        }
    }

    private CompoundTag encodeEntityMinimal(Entity entity) {
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return null;

        CompoundTag tag = new CompoundTag();
        tag.putString("id", typeId.toString());
        tag.store("Pos", Vec3.CODEC, entity.position());
        tag.store("Motion", Vec3.CODEC, Vec3.ZERO);
        tag.store("Rotation", Vec2.CODEC, new Vec2(entity.getYRot(), entity.getXRot()));
        tag.store("UUID", UUIDUtil.CODEC, entity.getUUID());
        return tag;
    }

    private ListTag encodeItems(List<ItemStack> items) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            int slot = i;
            ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .ifPresent(tag -> {
                    CompoundTag c = (CompoundTag) tag;
                    c.putByte("Slot", (byte) slot);
                    list.add(c);
                });
        }
        return list;
    }

    public void savePlayerInventory(UUID uuid, Inventory inv) {
        if (!isSaving || inv == null) return;
        try {
            Path playersDir = path.resolve("playerdata");
            Files.createDirectories(playersDir);
            Path file = playersDir.resolve(uuid + ".dat");

            CompoundTag player = readOrCreateTag(file);
            player.put("Inventory", encodePlayerInventory(inv));
            Tag gear = encodeEquipment(inv.player);
            if (gear != null) player.put("equipment", gear);
            player.putInt("DataVersion", dataVersion());
            writeCompressedTag(player, file);
        } catch (IOException e) {
            System.err.println("Failed to write player inventory: " + e.getMessage());
        }
    }

    private ListTag encodePlayerInventory(Inventory inv) {
        ListTag list = new ListTag();
        NonNullList<ItemStack> main = inv.getNonEquipmentItems();
        for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            if (stack.isEmpty()) continue;
            int slot = i;
            ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .ifPresent(tag -> {
                    CompoundTag c = (CompoundTag) tag;
                    c.putByte("Slot", (byte) slot);
                    list.add(c);
                });
        }

        return list;
    }

    private Tag encodeEquipment(net.minecraft.world.entity.LivingEntity living) {
        EntityEquipment gear = new EntityEquipment();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = living.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            gear.set(slot, stack);
        }
        if (gear.isEmpty()) return null;

        return EntityEquipment.CODEC.encodeStart(ops, gear).result().orElse(null);
    }

    public void cacheBlockInventory(BlockPos pos, List<ItemStack> items) {
        blockInventoryCache.put(pos, new ArrayList<>(items));
    }

    public void cacheEntityInventory(UUID uuid, List<ItemStack> items) {
        entityInventoryCache.put(uuid, new ArrayList<>(items));
    }

    public void cacheEntityOverride(UUID uuid, CompoundTag overlay) {
        entityOverrideCache.put(uuid, overlay);
    }

    public void cacheEnderChest(UUID uuid, List<ItemStack> items) {
        writePlayerEnderChest(uuid, items);
    }

    private void writePlayerEnderChest(UUID uuid, List<ItemStack> items) {
        try {
            Path playersDir = path.resolve("playerdata");
            Files.createDirectories(playersDir);
            Path file = playersDir.resolve(uuid + ".dat");

            CompoundTag player = readOrCreateTag(file);
            player.put("EnderItems", encodeItems(items));
            player.putInt("DataVersion", dataVersion());
            writeCompressedTag(player, file);
        } catch (IOException e) {
            System.err.println("Failed to write player ender chest: " + e.getMessage());
        }
    }

    public void cacheStatsPacket(ClientboundAwardStatsPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.hasSingleplayerServer() || mc.getCurrentServer() == null) return;
        if (cachePlayerUuid == null) cachePlayerUuid = mc.player.getUUID();

        boolean changed = false;
        synchronized (metaLock) {
            if (cachedStatsByType == null) cachedStatsByType = new JsonObject();
            for (Object2IntMap.Entry<Stat<?>> entry : packet.stats().object2IntEntrySet()) {
                Stat<?> stat = entry.getKey();
                String typeId = getStatTypeId(stat);
                String valueId = getStatValueId(stat);
                if (typeId == null || valueId == null) continue;

                JsonObject typeObject = getOrCreateJsonObject(cachedStatsByType, typeId);
                int incomingValue = entry.getIntValue();
                int existingValue = getInt(typeObject, valueId, Integer.MIN_VALUE);
                if (incomingValue > existingValue) {
                    typeObject.addProperty(valueId, incomingValue);
                    changed = true;
                }
            }
            if (changed) statsDirty = true;
        }

        if (changed) maybeFlushPlayerMetaFiles();
    }

    public void cacheAdvancementPacket(ClientboundUpdateAdvancementsPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.hasSingleplayerServer() || mc.getCurrentServer() == null) return;
        if (cachePlayerUuid == null) cachePlayerUuid = mc.player.getUUID();

        boolean changed = false;
        boolean hadProgressUpdates = !PacketCompat.advancementsProgress(packet).isEmpty();
        boolean hadRemovals = !PacketCompat.advancementsRemoved(packet).isEmpty();

        synchronized (metaLock) {
            if (cachedAdvancements == null) cachedAdvancements = new JsonObject();
            if (removedAdvancements == null) removedAdvancements = new HashSet<>();

            if (packet.shouldReset()) {
                cachedAdvancements = new JsonObject();
                removedAdvancements.clear();
                advancementsResetThisSession = true;
                if (hadProgressUpdates || hadRemovals) changed = true;
            }

            for (Identifier removedId : PacketCompat.advancementsRemoved(packet)) {
                String key = removedId.toString();
                cachedAdvancements.remove(key);
                removedAdvancements.add(key);
                changed = true;
            }

            for (Map.Entry<Identifier, AdvancementProgress> e : PacketCompat.advancementsProgress(packet).entrySet()) {
                String key = e.getKey().toString();
                JsonObject incoming = buildAdvancementJson(e.getValue());
                JsonObject existing = getJsonObject(cachedAdvancements, key);
                cachedAdvancements.add(key, mergeAdvancementObjects(existing, incoming));
                removedAdvancements.remove(key);
            }

            if (hadProgressUpdates) changed = true;
            if (changed) advancementsDirty = true;
        }

        if (changed) maybeFlushPlayerMetaFiles();
    }

    @SuppressWarnings("unchecked")
    private void bootstrapAdvancementsFromClientCache() {
        if (!isSaving || mc.getConnection() == null || cachedAdvancements == null) return;
        try {
            ClientAdvancements advancements = mc.getConnection().getAdvancements();
            var progressField = ClientAdvancements.class.getDeclaredField("progress");
            progressField.setAccessible(true);
            Map<AdvancementHolder, AdvancementProgress> progressMap =
                (Map<AdvancementHolder, AdvancementProgress>) progressField.get(advancements);
            if (progressMap == null || progressMap.isEmpty()) return;

            boolean seeded = false;
            synchronized (metaLock) {
                for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progressMap.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    String key = entry.getKey().id().toString();
                    JsonObject existing = getJsonObject(cachedAdvancements, key);
                    cachedAdvancements.add(key, mergeAdvancementObjects(existing, buildAdvancementJson(entry.getValue())));
                    seeded = true;
                }
                if (seeded) advancementsDirty = true;
            }
        } catch (ReflectiveOperationException e) {
            System.err.println("Failed to bootstrap advancements from client cache: " + e.getMessage());
        }
    }

    private void maybeFlushPlayerMetaFiles() {
        flushPlayerMetaFiles(false);
    }

    private void flushPlayerMetaFiles(boolean force) {
        synchronized (metaLock) {
            if ((!statsDirty && !advancementsDirty) && !force) return;
            if (path == null) return;

            long now = System.currentTimeMillis();
            if (!force && now - lastMetaFlushTimeMs < META_FLUSH_INTERVAL_MS) return;

            UUID target = cachePlayerUuid;
            if (target == null && mc.player != null) target = mc.player.getUUID();
            if (target == null) return;

            try {
                if (statsDirty || force) {
                    writeStatsFile(path.resolve("stats").resolve(target + ".json"));
                    statsDirty = false;
                }
                if (advancementsDirty || force) {
                    writeAdvancementsFile(path.resolve("advancements").resolve(target + ".json"));
                    advancementsDirty = false;
                }
                lastMetaFlushTimeMs = now;
            } catch (IOException e) {
                System.err.println("Failed to write player advancement/stats files: " + e.getMessage());
            }
        }
    }

    private void writeStatsFile(Path statsFile) throws IOException {
        JsonObject existingRoot = readJsonObject(statsFile);
        JsonObject mergedStats = new JsonObject();

        JsonObject existingStats = getJsonObject(existingRoot, "stats");
        if (existingStats != null) {
            for (Map.Entry<String, JsonElement> typeEntry : existingStats.entrySet()) {
                if (!typeEntry.getValue().isJsonObject()) continue;
                mergedStats.add(typeEntry.getKey(), typeEntry.getValue().getAsJsonObject().deepCopy());
            }
        }

        if (cachedStatsByType != null) {
            for (Map.Entry<String, JsonElement> typeEntry : cachedStatsByType.entrySet()) {
                if (!typeEntry.getValue().isJsonObject()) continue;
                JsonObject mergedType = getOrCreateJsonObject(mergedStats, typeEntry.getKey());
                for (Map.Entry<String, JsonElement> statEntry : typeEntry.getValue().getAsJsonObject().entrySet()) {
                    int incoming = statEntry.getValue().isJsonPrimitive() ? statEntry.getValue().getAsInt() : 0;
                    int existing = getInt(mergedType, statEntry.getKey(), Integer.MIN_VALUE);
                    if (incoming > existing) mergedType.addProperty(statEntry.getKey(), incoming);
                }
            }
        }

        JsonObject root = new JsonObject();
        root.add("stats", mergedStats);
        root.addProperty("DataVersion", dataVersion());
        writeJsonObject(statsFile, root);
    }

    private void writeAdvancementsFile(Path advancementsFile) throws IOException {
        JsonObject existingRoot = readJsonObject(advancementsFile);
        JsonObject mergedRoot = new JsonObject();

        if (!advancementsResetThisSession) {
            for (Map.Entry<String, JsonElement> entry : existingRoot.entrySet()) {
                if ("DataVersion".equals(entry.getKey())) continue;
                mergedRoot.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }

        if (removedAdvancements != null) removedAdvancements.forEach(mergedRoot::remove);

        if (cachedAdvancements != null) {
            for (Map.Entry<String, JsonElement> entry : cachedAdvancements.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject existing = getJsonObject(mergedRoot, entry.getKey());
                mergedRoot.add(entry.getKey(), mergeAdvancementObjects(existing, entry.getValue().getAsJsonObject()));
            }
        }

        mergedRoot.addProperty("DataVersion", dataVersion());
        writeJsonObject(advancementsFile, mergedRoot);
    }

    private JsonObject buildAdvancementJson(AdvancementProgress progress) {
        JsonObject result = new JsonObject();
        JsonObject criteria = new JsonObject();
        for (String criterionName : progress.getCompletedCriteria()) {
            CriterionProgress cp = progress.getCriterion(criterionName);
            if (cp == null || !cp.isDone()) continue;
            Instant obtained = cp.getObtained();
            if (obtained != null) criteria.addProperty(criterionName, ADVANCEMENT_TIME_FORMAT.format(obtained));
        }
        result.add("criteria", criteria);
        result.addProperty("done", progress.isDone());
        return result;
    }

    private JsonObject mergeAdvancementObjects(JsonObject base, JsonObject incoming) {
        JsonObject merged = new JsonObject();
        JsonObject baseCriteria = getJsonObject(base, "criteria");
        JsonObject incomingCriteria = getJsonObject(incoming, "criteria");
        JsonObject mergedCriteria = new JsonObject();
        if (baseCriteria != null) {
            for (Map.Entry<String, JsonElement> e : baseCriteria.entrySet()) mergedCriteria.add(e.getKey(), e.getValue().deepCopy());
        }
        if (incomingCriteria != null) {
            for (Map.Entry<String, JsonElement> e : incomingCriteria.entrySet()) mergedCriteria.add(e.getKey(), e.getValue().deepCopy());
        }
        merged.add("criteria", mergedCriteria);
        boolean done = getBoolean(base, "done") || getBoolean(incoming, "done");
        merged.addProperty("done", done);
        return merged;
    }

    private String getStatTypeId(Stat<?> stat) {
        Identifier id = BuiltInRegistries.STAT_TYPE.getKey(stat.getType());
        return id == null ? null : id.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String getStatValueId(Stat<?> stat) {
        Identifier id = (Identifier) ((Registry) stat.getType().getRegistry()).getKey(stat.getValue());
        if (id != null) return id.toString();
        Object raw = stat.getValue();
        return raw == null ? null : raw.toString();
    }

    private JsonObject getOrCreateJsonObject(JsonObject parent, String key) {
        JsonObject existing = getJsonObject(parent, key);
        if (existing != null) return existing;
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    private JsonObject getJsonObject(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private int getInt(JsonObject object, String key, int fallback) {
        if (object == null) return fallback;
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        try { return value.getAsInt(); } catch (Exception ignored) { return fallback; }
    }

    private boolean getBoolean(JsonObject object, String key) {
        if (object == null) return false;
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private JsonObject readJsonObject(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        try {
            JsonElement element = JsonParser.parseString(Files.readString(file));
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            System.err.println("Invalid JSON at " + file + ", starting from empty object");
            return new JsonObject();
        }
    }

    private void writeJsonObject(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(json));
        moveIntoPlace(temp, file);
    }

    @Override
    public void close() {
        stop();
    }
}
