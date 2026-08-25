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
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SwdSaveManager implements AutoCloseable {

    private static final int DATA_VERSION = 4671;

    public final Path path;
    public volatile boolean isSaving = false;
    public Object lastClicked = null;

    private final net.minecraft.client.Minecraft mc;
    private final DynamicOps<Tag> ops;
    private final SwdRegionStorage regionStorage;
    private final Path regionDir;
    private final Map<BlockPos, List<ItemStack>> blockInventoryCache = new HashMap<>();
    private final Map<UUID, List<ItemStack>> entityInventoryCache = new HashMap<>();
    private final Map<UUID, CompoundTag> entityOverrideCache = new HashMap<>();
    private final Map<UUID, List<ItemStack>> enderChestCache = new HashMap<>();

    private ExecutorService diskWorker;
    private final AtomicInteger inFlightWrites = new AtomicInteger(0);

    private final ConcurrentLinkedQueue<String> writeErrors = new ConcurrentLinkedQueue<>();

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
    private UUID cachePlayerUuid;

    public SwdSaveManager(net.minecraft.client.Minecraft mc, Path path) {
        this.mc = mc;
        this.path = path;
        this.ops = mc.level != null
            ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess())
            : NbtOps.INSTANCE;
        this.regionDir = path.resolve("region");
        this.regionStorage = new SwdRegionStorage(regionDir, mc.level != null ? mc.level.dimension() : net.minecraft.world.level.Level.OVERWORLD);
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
            createLevelDat(name);
            bootstrapAdvancementsFromClientCache();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            flushPlayerMetaFiles(true);
        } finally {
            isSaving = false;
            if (diskWorker != null) {
                diskWorker.shutdown();
                try {

                    if (!diskWorker.awaitTermination(10, TimeUnit.SECONDS)) {
                        diskWorker.shutdownNow();
                        diskWorker.awaitTermination(2, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    diskWorker.shutdownNow();
                }
                diskWorker = null;
            }
            inFlightWrites.set(0);
            try {
                regionStorage.close();
            } catch (IOException ignored) {}
            blockInventoryCache.clear();
            entityInventoryCache.clear();
            entityOverrideCache.clear();
            enderChestCache.clear();
            drainWriteErrors();
        }
    }

    public void drainWriteErrors() {
        if (writeErrors.isEmpty()) return;
        String error;
        while ((error = writeErrors.poll()) != null) {
            System.err.println("[SwdSaveManager] " + error);
        }
    }

    private void createLevelDat(String name) throws IOException {
        Files.createDirectories(path);
        CompoundTag data = new CompoundTag();

        data.putInt("DataVersion", DATA_VERSION);
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
        version.putString("Name", "1.21.11");
        version.putInt("Id", DATA_VERSION);
        version.putString("Series", "main");
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
        NbtIo.writeCompressed(root, path.resolve("level.dat"));

        long now = System.currentTimeMillis();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8).putLong(now);
        Files.write(path.resolve("session.lock"), buf.array());
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
                    regionStorage.write(pos, nbt);
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
        if (chunk != null) saveChunk(chunk);
    }

    public void saveEntitiesForChunk(LevelChunk chunk) {
        if (!isSaving || chunk == null) return;
        if (mc.level == null) return;
        try {

            final ListTag entityList = buildEntityListSnapshot(chunk);
            final ChunkPos pos = chunk.getPos();
            submitDiskWrite(() -> {
                try {
                    CompoundTag existing = regionStorage.read(pos);
                    if (existing == null) {

                        return;
                    }
                    existing.put("Entities", entityList);
                    regionStorage.write(pos, existing);
                } catch (Exception e) {
                    writeErrors.offer("Failed to update entities for chunk " + pos + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {

            saveChunk(chunk);
        }
    }

    private void submitDiskWrite(Runnable task) {
        if (diskWorker == null || diskWorker.isShutdown()) {

            runWriteSynchronized(task);
            return;
        }
        inFlightWrites.incrementAndGet();
        diskWorker.execute(() -> {
            try {
                task.run();
            } finally {
                inFlightWrites.decrementAndGet();
            }
        });
    }

    private void runWriteSynchronized(Runnable task) {
        synchronized (regionStorage) {
            task.run();
        }
    }

    private ListTag buildEntityListSnapshot(LevelChunk chunk) {
        ListTag list = new ListTag();
        if (mc.level == null) return list;
        ChunkPos pos = chunk.getPos();
        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.getHeight();
        AABB box = new AABB(pos.getMinBlockX(), minY, pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1, maxY, pos.getMaxBlockZ() + 1);
        for (Entity entity : mc.level.getEntities(EntityTypeTest.forClass(Entity.class), box, e -> !(e instanceof Player))) {
            CompoundTag tag = encodeEntity(entity);
            if (tag != null) list.add(tag);
        }
        return list;
    }

    private CompoundTag buildChunkNbt(LevelChunk chunk) {

        if (mc.level == null) return null;
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("DataVersion", DATA_VERSION);
        nbt.putInt("xPos", chunk.getPos().x());
        nbt.putInt("yPos", chunk.getMinSectionY());
        nbt.putInt("zPos", chunk.getPos().z());
        nbt.putLong("LastUpdate", 0L);
        nbt.putLong("InhabitedTime", 0L);
        nbt.putString("Status", "full");

        writeSections(chunk, nbt);
        writeBlockEntities(chunk, nbt);
        writeEntities(chunk, nbt);
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

    private void writeEntities(LevelChunk chunk, CompoundTag nbt) {
        ListTag list = new ListTag();
        if (mc.level == null) {
            nbt.put("Entities", list);
            return;
        }

        ChunkPos pos = chunk.getPos();
        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.getHeight();
        AABB box = new AABB(pos.getMinBlockX(), minY, pos.getMinBlockZ(),
            pos.getMaxBlockX() + 1, maxY, pos.getMaxBlockZ() + 1);

        for (Entity entity : mc.level.getEntities(EntityTypeTest.forClass(Entity.class), box, e -> !(e instanceof Player))) {
            CompoundTag tag = encodeEntity(entity);
            if (tag != null) list.add(tag);
        }
        nbt.put("Entities", list);
    }

    private CompoundTag encodeEntity(Entity entity) {
        if (mc.level == null) return null;
        CompoundTag tag = null;
        boolean fullSave = false;
        try {
            TagValueOutput view = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mc.level.registryAccess());
            if (entity.save(view)) {
                tag = view.buildResult();
                fullSave = true;
            }
        } catch (Exception e) {
            System.err.println("Full entity save failed for " + entity.getType() + ", falling back: " + e.getMessage());
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
        tag.putDouble("x", entity.getX());
        tag.putDouble("y", entity.getY());
        tag.putDouble("z", entity.getZ());
        tag.putFloat("Yaw", entity.getYRot());
        tag.putFloat("Pitch", entity.getXRot());
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());

        UUID uuid = entity.getUUID();
        tag.putLong("UUIDMost", uuid.getMostSignificantBits());
        tag.putLong("UUIDLeast", uuid.getLeastSignificantBits());

        if (entity.hasCustomName()) {
            ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entity.getCustomName())
                .result()
                .ifPresent(json -> tag.putString("CustomName", json.toString()));
            tag.putBoolean("CustomNameVisible", true);
        }
        if (entity.isInvisible()) tag.putBoolean("Invisible", true);
        if (entity.isSilent()) tag.putBoolean("Silent", true);
        tag.putInt("Fire", entity.getRemainingFireTicks());
        tag.putInt("Air", entity.getAirSupply());
        tag.putBoolean("OnGround", entity.onGround());

        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            ListTag armorItems = new ListTag();
            for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
            }) {
                ItemStack stack = living.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(armorItems::add);
                } else {
                    armorItems.add(new CompoundTag());
                }
            }
            if (!armorItems.isEmpty()) tag.put("ArmorItems", armorItems);

            ListTag handItems = new ListTag();
            ItemStack mainHand = living.getItemBySlot(EquipmentSlot.MAINHAND);
            ItemStack offHand = living.getItemBySlot(EquipmentSlot.OFFHAND);
            ItemStack.CODEC.encodeStart(ops, mainHand.isEmpty() ? ItemStack.EMPTY : mainHand).result().ifPresent(handItems::add);
            ItemStack.CODEC.encodeStart(ops, offHand.isEmpty() ? ItemStack.EMPTY : offHand).result().ifPresent(handItems::add);
            if (!handItems.isEmpty()) tag.put("HandItems", handItems);
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
        tag.putDouble("x", entity.getX());
        tag.putDouble("y", entity.getY());
        tag.putDouble("z", entity.getZ());
        tag.putFloat("Yaw", entity.getYRot());
        tag.putFloat("Pitch", entity.getXRot());

        UUID uuid = entity.getUUID();
        tag.putLong("UUIDMost", uuid.getMostSignificantBits());
        tag.putLong("UUIDLeast", uuid.getLeastSignificantBits());

        if (entity.getCustomName() != null) {
            ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entity.getCustomName())
                .result()
                .ifPresent(json -> tag.putString("CustomName", json.toString()));
        }
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

            CompoundTag player;
            if (Files.exists(file)) {
                player = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            } else {
                player = new CompoundTag();
            }
            player.put("Inventory", encodePlayerInventory(inv));
            player.putInt("DataVersion", DATA_VERSION);
            NbtIo.writeCompressed(player, file);
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

        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND
        }) {
            ItemStack stack = inv.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            int nbtSlot = slot == EquipmentSlot.OFFHAND ? -106 : 100 + slot.getIndex();
            ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .ifPresent(tag -> {
                    CompoundTag c = (CompoundTag) tag;
                    c.putByte("Slot", (byte) nbtSlot);
                    list.add(c);
                });
        }
        return list;
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
        enderChestCache.put(uuid, new ArrayList<>(items));
        writePlayerEnderChest(uuid, items);
    }

    private void writePlayerEnderChest(UUID uuid, List<ItemStack> items) {
        try {
            Path playersDir = path.resolve("playerdata");
            Files.createDirectories(playersDir);
            Path file = playersDir.resolve(uuid + ".dat");

            CompoundTag player;
            if (Files.exists(file)) {
                player = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            } else {
                player = new CompoundTag();
            }
            player.put("EnderItems", encodeItems(items));
            player.putInt("DataVersion", DATA_VERSION);
            NbtIo.writeCompressed(player, file);
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
        boolean hadProgressUpdates = !packet.getProgress().isEmpty();
        boolean hadRemovals = !packet.getRemoved().isEmpty();

        synchronized (metaLock) {
            if (cachedAdvancements == null) cachedAdvancements = new JsonObject();
            if (removedAdvancements == null) removedAdvancements = new HashSet<>();

            if (packet.shouldReset()) {
                cachedAdvancements = new JsonObject();
                removedAdvancements.clear();
                advancementsResetThisSession = true;
                if (hadProgressUpdates || hadRemovals) changed = true;
            }

            for (Identifier removedId : packet.getRemoved()) {
                String key = removedId.toString();
                cachedAdvancements.remove(key);
                removedAdvancements.add(key);
                changed = true;
            }

            for (Map.Entry<Identifier, AdvancementProgress> e : packet.getProgress().entrySet()) {
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

            Path playersPath = path;
            try {
                if (statsDirty || force) {
                    writeStatsFile(playersPath.resolve("stats").resolve(target + ".json"));
                    statsDirty = false;
                }
                if (advancementsDirty || force) {
                    writeAdvancementsFile(playersPath.resolve("advancements").resolve(target + ".json"));
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
        root.addProperty("DataVersion", DATA_VERSION);
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

        mergedRoot.addProperty("DataVersion", DATA_VERSION);
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
        Files.writeString(file, GSON.toJson(json));
    }

    @Override
    public void close() {
        stop();
    }
}
