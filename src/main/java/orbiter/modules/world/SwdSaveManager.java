package orbiter.modules.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.criterion.CriterionProgress;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.stat.Stat;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.PaletteProvider;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.chunk.WorldChunk;
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

    private final net.minecraft.client.MinecraftClient mc;
    private final DynamicOps<NbtElement> ops;
    private final SwdRegionStorage regionStorage;
    private final Path regionDir;
    private final Map<BlockPos, List<ItemStack>> blockInventoryCache = new HashMap<>();
    private final Map<UUID, List<ItemStack>> entityInventoryCache = new HashMap<>();
    private final Map<UUID, NbtCompound> entityOverrideCache = new HashMap<>();
    private final Map<UUID, List<ItemStack>> enderChestCache = new HashMap<>();

    private ExecutorService diskWorker;
    private final AtomicInteger inFlightWrites = new AtomicInteger(0);

    private final ConcurrentLinkedQueue<String> writeErrors = new ConcurrentLinkedQueue<>();

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

    public SwdSaveManager(net.minecraft.client.MinecraftClient mc, Path path) {
        this.mc = mc;
        this.path = path;
        this.ops = mc.world != null
            ? mc.world.getRegistryManager().getOps(NbtOps.INSTANCE)
            : NbtOps.INSTANCE;
        this.regionDir = path.resolve("region");
        this.regionStorage = new SwdRegionStorage(regionDir, mc.world != null ? mc.world.getRegistryKey() : net.minecraft.world.World.OVERWORLD);
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
        cachePlayerUuid = mc.player != null ? mc.player.getUuid() : null;
        try {
            Files.createDirectories(path);
            Files.createDirectories(regionDir);
            createLevelDat(name);
            saveServerIcon();
            bootstrapAdvancementsFromClientCache();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveServerIcon() {
        if (mc.getCurrentServerEntry() == null) return;
        byte[] icon = mc.getCurrentServerEntry().getFavicon();
        if (icon == null || icon.length == 0) return;
        try {
            Files.write(path.resolve("icon.png"), icon);
        } catch (IOException e) {
            System.err.println("Failed to write server icon: " + e.getMessage());
        }
    }

    public void stop() {
        flushPlayerMetaFiles(true);
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
    }

    private void createLevelDat(String name) throws IOException {
        Files.createDirectories(path);
        NbtCompound data = new NbtCompound();

        data.putInt("DataVersion", DATA_VERSION);
        data.putString("LevelName", name);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putInt("version", 19133);
        data.putInt("GameType", 1);
        data.putByte("initialized", (byte) 1);
        data.putByte("allowCommands", (byte) 1);

        NbtCompound difficulty = new NbtCompound();
        difficulty.putString("difficulty", "normal");
        difficulty.putByte("hardcore", (byte) 0);
        difficulty.putByte("locked", (byte) 0);
        data.put("difficulty_settings", difficulty);

        data.putLong("Time", 0L);
        data.putLong("DayTime", 0L);

        NbtCompound spawn = new NbtCompound();
        spawn.putFloat("pitch", 0);
        spawn.putFloat("yaw", 0);
        spawn.putString("dimension", "minecraft:overworld");
        spawn.putIntArray("pos", new int[]{
            mc.player != null ? mc.player.getBlockPos().getX() : 0,
            mc.player != null ? mc.player.getBlockPos().getY() : 64,
            mc.player != null ? mc.player.getBlockPos().getZ() : 0
        });
        data.put("spawn", spawn);

        NbtCompound version = new NbtCompound();
        version.putString("Name", "1.21.11");
        version.putInt("Id", DATA_VERSION);
        version.putString("Series", "main");
        version.putByte("Snapshot", (byte) 0);
        data.put("Version", version);

        NbtCompound dragonFight = new NbtCompound();
        dragonFight.putByte("DragonKilled", (byte) 1);
        dragonFight.putByte("DragonPreviouslyKilled", (byte) 1);
        dragonFight.put("EndGatewayList", new NbtList());
        dragonFight.putIntArray("ExitPortalLocation", new int[]{0, 64, 0});
        dragonFight.put("Gateways", new NbtList());
        data.put("DragonFight", dragonFight);

        NbtCompound gameRules = new NbtCompound();
        gameRules.putByte("minecraft:do_daylight_cycle", (byte) 1);
        gameRules.putByte("minecraft:do_weather_cycle", (byte) 1);
        gameRules.putInt("minecraft:random_tick_speed", 0);
        data.put("game_rules", gameRules);

        NbtCompound dataPacks = new NbtCompound();
        NbtList enabled = new NbtList();
        enabled.add(net.minecraft.nbt.NbtString.of("vanilla"));
        dataPacks.put("Enabled", enabled);
        NbtList disabled = new NbtList();
        dataPacks.put("Disabled", disabled);
        data.put("DataPacks", dataPacks);

        NbtCompound worldGenSettings = new NbtCompound();
        worldGenSettings.putByte("bonus_chest", (byte) 0);
        worldGenSettings.putByte("generate_structures", (byte) 0);
        worldGenSettings.putLong("seed", 0L);
        NbtCompound dimensions = new NbtCompound();
        dimensions.put("minecraft:overworld", createDimensionEntry("minecraft:overworld", "minecraft:plains"));
        dimensions.put("minecraft:the_nether", createDimensionEntry("minecraft:the_nether", "minecraft:the_nether"));
        dimensions.put("minecraft:the_end", createDimensionEntry("minecraft:the_end", "minecraft:the_end"));
        worldGenSettings.put("dimensions", dimensions);
        data.put("WorldGenSettings", worldGenSettings);

        NbtCompound root = new NbtCompound();
        root.put("Data", data);
        NbtIo.writeCompressed(root, path.resolve("level.dat"));

        long now = System.currentTimeMillis();
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(8).putLong(now);
        Files.write(path.resolve("session.lock"), buf.array());
    }

    private NbtCompound createDimensionEntry(String type, String biome) {
        NbtCompound dim = new NbtCompound();
        dim.putString("type", type);

        NbtCompound generator = new NbtCompound();
        generator.putString("type", "minecraft:flat");

        NbtCompound settings = new NbtCompound();
        settings.putByte("features", (byte) 0);
        settings.putString("biome", biome);
        settings.put("layers", new NbtList());
        settings.putByte("lakes", (byte) 0);
        settings.put("structure_overrides", new NbtList());
        generator.put("settings", settings);

        dim.put("generator", generator);
        return dim;
    }

    public void saveChunk(WorldChunk chunk) {
        if (!isSaving || chunk == null) return;

        if (mc.world == null) return;
        try {

            final NbtCompound nbt = buildChunkNbt(chunk);
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
        if (mc.world == null) return;
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) saveChunk(chunk);
    }

    public void saveEntitiesForChunk(WorldChunk chunk) {
        if (!isSaving || chunk == null) return;
        if (mc.world == null) return;
        try {

            final NbtList entityList = buildEntityListSnapshot(chunk);
            final ChunkPos pos = chunk.getPos();
            submitDiskWrite(() -> {
                try {
                    NbtCompound existing = regionStorage.read(pos);
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

    private NbtList buildEntityListSnapshot(WorldChunk chunk) {
        NbtList list = new NbtList();
        if (mc.world == null) return list;
        ChunkPos pos = chunk.getPos();
        int minY = mc.world.getBottomY();
        int maxY = minY + mc.world.getHeight();
        Box box = new Box(pos.getStartX(), minY, pos.getStartZ(),
            pos.getEndX() + 1, maxY, pos.getEndZ() + 1);
        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, box, e -> !(e instanceof PlayerEntity))) {
            NbtCompound tag = encodeEntity(entity);
            if (tag != null) list.add(tag);
        }
        return list;
    }

    private NbtCompound buildChunkNbt(WorldChunk chunk) {

        if (mc.world == null) return null;
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("DataVersion", DATA_VERSION);
        nbt.putInt("xPos", chunk.getPos().x);
        nbt.putInt("yPos", chunk.getBottomSectionCoord());
        nbt.putInt("zPos", chunk.getPos().z);
        nbt.putLong("LastUpdate", 0L);
        nbt.putLong("InhabitedTime", 0L);
        nbt.putString("Status", "full");

        writeSections(chunk, nbt);
        writeBlockEntities(chunk, nbt);
        writeEntities(chunk, nbt);
        return nbt;
    }

    private void writeSections(WorldChunk chunk, NbtCompound nbt) {
        Registry<Biome> biomeRegistry = mc.world != null ? mc.world.getRegistryManager().getOrThrow(RegistryKeys.BIOME) : null;
        if (biomeRegistry == null) return;

        Codec<PalettedContainer<BlockState>> blockCodec = PalettedContainer.createPalettedContainerCodec(
            BlockState.CODEC,
            PaletteProvider.forBlockStates(net.minecraft.block.Block.STATE_IDS),
            Blocks.AIR.getDefaultState()
        );

        RegistryEntry<Biome> defaultBiome = biomeRegistry.getOptional(BiomeKeys.PLAINS).orElse(null);
        if (defaultBiome == null) return;

        Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec = PalettedContainer.createReadableContainerCodec(
            RegistryElementCodec.of(RegistryKeys.BIOME, Biome.CODEC),
            PaletteProvider.forBiomes(biomeRegistry.getIndexedEntries()),
            defaultBiome
        );

        NbtList sections = new NbtList();
        ChunkSection[] sectionsArray = chunk.getSectionArray();
        int bottomSection = chunk.getBottomSectionCoord();
        for (int i = 0; i < sectionsArray.length; i++) {
            ChunkSection section = sectionsArray[i];
            if (section == null) continue;

            NbtCompound sec = new NbtCompound();
            sec.putByte("Y", (byte) (bottomSection + i));

            blockCodec.encodeStart(ops, section.getBlockStateContainer())
                .result()
                .ifPresent(tag -> sec.put("block_states", tag));

            biomeCodec.encodeStart(ops, section.getBiomeContainer())
                .result()
                .ifPresent(tag -> sec.put("biomes", tag));

            sections.add(sec);
        }
        nbt.put("sections", sections);
    }

    private void writeBlockEntities(WorldChunk chunk, NbtCompound nbt) {
        NbtList list = new NbtList();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            NbtCompound tag = be.createNbtWithIdentifyingData(mc.world.getRegistryManager());
            List<ItemStack> cached = blockInventoryCache.get(be.getPos());
            if (cached != null && !cached.isEmpty()) {
                tag.put("Items", encodeItems(cached));
            }
            list.add(tag);
        }
        nbt.put("block_entities", list);
    }

    private void writeEntities(WorldChunk chunk, NbtCompound nbt) {
        NbtList list = new NbtList();
        if (mc.world == null) {
            nbt.put("Entities", list);
            return;
        }

        ChunkPos pos = chunk.getPos();
        int minY = mc.world.getBottomY();
        int maxY = minY + mc.world.getHeight();
        Box box = new Box(pos.getStartX(), minY, pos.getStartZ(),
            pos.getEndX() + 1, maxY, pos.getEndZ() + 1);

        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, box, e -> !(e instanceof PlayerEntity))) {
            NbtCompound tag = encodeEntity(entity);
            if (tag != null) list.add(tag);
        }
        nbt.put("Entities", list);
    }

    private NbtCompound encodeEntity(Entity entity) {
        if (mc.world == null) return null;
        NbtCompound tag = null;
        boolean fullSave = false;
        try {
            NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, mc.world.getRegistryManager());
            if (entity.saveData(view)) {
                tag = view.getNbt();
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

        List<ItemStack> inv = entityInventoryCache.get(entity.getUuid());
        if (inv != null && !inv.isEmpty()) tag.put("Items", encodeItems(inv));

        NbtCompound override = entityOverrideCache.get(entity.getUuid());
        if (override != null) {
            for (String key : override.getKeys()) tag.put(key, override.get(key));
        }

        return tag;
    }

    private void encodeEntityExtra(NbtCompound tag, Entity entity) {
        tag.putDouble("x", entity.getX());
        tag.putDouble("y", entity.getY());
        tag.putDouble("z", entity.getZ());
        tag.putFloat("Yaw", entity.getYaw());
        tag.putFloat("Pitch", entity.getPitch());
        tag.putString("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());

        UUID uuid = entity.getUuid();
        tag.putLong("UUIDMost", uuid.getMostSignificantBits());
        tag.putLong("UUIDLeast", uuid.getLeastSignificantBits());

        if (entity.hasCustomName()) {
            TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entity.getCustomName())
                .result()
                .ifPresent(json -> tag.putString("CustomName", json.toString()));
            tag.putBoolean("CustomNameVisible", true);
        }
        if (entity.isInvisible()) tag.putBoolean("Invisible", true);
        if (entity.isSilent()) tag.putBoolean("Silent", true);
        tag.putInt("Fire", entity.getFireTicks());
        tag.putInt("Air", entity.getAir());
        tag.putBoolean("OnGround", entity.isOnGround());

        if (entity instanceof net.minecraft.entity.LivingEntity living) {
            NbtList armorItems = new NbtList();
            for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
            }) {
                ItemStack stack = living.getEquippedStack(slot);
                if (!stack.isEmpty()) {
                    ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(armorItems::add);
                } else {
                    armorItems.add(new NbtCompound());
                }
            }
            if (!armorItems.isEmpty()) tag.put("ArmorItems", armorItems);

            NbtList handItems = new NbtList();
            ItemStack mainHand = living.getEquippedStack(EquipmentSlot.MAINHAND);
            ItemStack offHand = living.getEquippedStack(EquipmentSlot.OFFHAND);
            ItemStack.CODEC.encodeStart(ops, mainHand.isEmpty() ? ItemStack.EMPTY : mainHand).result().ifPresent(handItems::add);
            ItemStack.CODEC.encodeStart(ops, offHand.isEmpty() ? ItemStack.EMPTY : offHand).result().ifPresent(handItems::add);
            if (!handItems.isEmpty()) tag.put("HandItems", handItems);
        }
    }

    private void encodeDisplayEntityData(NbtCompound tag, Entity entity) {
        if (entity instanceof net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity tde) {
            net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity.Data data = tde.getData();
            if (data != null && data.text() != null) {

                if (!tag.contains("text")) {
                    TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data.text())
                        .result()
                        .ifPresent(json -> tag.putString("text", json.toString()));
                }
            }
        }
    }

    private NbtCompound encodeEntityMinimal(Entity entity) {
        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (typeId == null) return null;

        NbtCompound tag = new NbtCompound();
        tag.putString("id", typeId.toString());
        tag.putDouble("x", entity.getX());
        tag.putDouble("y", entity.getY());
        tag.putDouble("z", entity.getZ());
        tag.putFloat("Yaw", entity.getYaw());
        tag.putFloat("Pitch", entity.getPitch());

        UUID uuid = entity.getUuid();
        tag.putLong("UUIDMost", uuid.getMostSignificantBits());
        tag.putLong("UUIDLeast", uuid.getLeastSignificantBits());

        if (entity.getCustomName() != null) {
            TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entity.getCustomName())
                .result()
                .ifPresent(json -> tag.putString("CustomName", json.toString()));
        }
        return tag;
    }

    private NbtList encodeItems(List<ItemStack> items) {
        NbtList list = new NbtList();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            int slot = i;
            ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .ifPresent(tag -> {
                    NbtCompound c = (NbtCompound) tag;
                    c.putByte("Slot", (byte) slot);
                    list.add(c);
                });
        }
        return list;
    }

    public void savePlayerInventory(UUID uuid, PlayerInventory inv) {
        if (!isSaving || inv == null) return;
        try {
            Path playersDir = path.resolve("playerdata");
            Files.createDirectories(playersDir);
            Path file = playersDir.resolve(uuid + ".dat");

            NbtCompound player;
            if (Files.exists(file)) {
                player = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            } else {
                player = new NbtCompound();
            }
            player.put("Inventory", encodePlayerInventory(inv));
            player.putInt("DataVersion", DATA_VERSION);
            NbtIo.writeCompressed(player, file);
        } catch (IOException e) {
            System.err.println("Failed to write player inventory: " + e.getMessage());
        }
    }

    private NbtList encodePlayerInventory(PlayerInventory inv) {
        NbtList list = new NbtList();
        DefaultedList<ItemStack> main = inv.getMainStacks();
        for (int i = 0; i < main.size(); i++) {
            ItemStack stack = main.get(i);
            if (stack.isEmpty()) continue;
            int slot = i;
            ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .ifPresent(tag -> {
                    NbtCompound c = (NbtCompound) tag;
                    c.putByte("Slot", (byte) slot);
                    list.add(c);
                });
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND
        }) {
            ItemStack stack = inv.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            int nbtSlot = slot == EquipmentSlot.OFFHAND ? -106 : 100 + slot.getEntitySlotId();
            ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .ifPresent(tag -> {
                    NbtCompound c = (NbtCompound) tag;
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

    public void cacheEntityOverride(UUID uuid, NbtCompound overlay) {
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

            NbtCompound player;
            if (Files.exists(file)) {
                player = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            } else {
                player = new NbtCompound();
            }
            player.put("EnderItems", encodeItems(items));
            player.putInt("DataVersion", DATA_VERSION);
            NbtIo.writeCompressed(player, file);
        } catch (IOException e) {
            System.err.println("Failed to write player ender chest: " + e.getMessage());
        }
    }

    public void cacheStatsPacket(StatisticsS2CPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.isInSingleplayer() || mc.getCurrentServerEntry() == null) return;
        if (cachedStatsByType == null) cachedStatsByType = new JsonObject();
        if (cachePlayerUuid == null) cachePlayerUuid = mc.player.getUuid();

        boolean changed = false;
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

        if (changed) {
            statsDirty = true;
            maybeFlushPlayerMetaFiles();
        }
    }

    public void cacheAdvancementPacket(AdvancementUpdateS2CPacket packet) {
        if (!isSaving || path == null || mc.player == null || mc.isInSingleplayer() || mc.getCurrentServerEntry() == null) return;
        if (cachedAdvancements == null) cachedAdvancements = new JsonObject();
        if (removedAdvancements == null) removedAdvancements = new HashSet<>();
        if (cachePlayerUuid == null) cachePlayerUuid = mc.player.getUuid();

        boolean changed = false;
        boolean hadProgressUpdates = !packet.getAdvancementsToProgress().isEmpty();
        boolean hadRemovals = !packet.getAdvancementIdsToRemove().isEmpty();

        if (packet.shouldClearCurrent()) {
            cachedAdvancements = new JsonObject();
            removedAdvancements.clear();
            advancementsResetThisSession = true;
            if (hadProgressUpdates || hadRemovals) changed = true;
        }

        for (Identifier removedId : packet.getAdvancementIdsToRemove()) {
            String key = removedId.toString();
            cachedAdvancements.remove(key);
            removedAdvancements.add(key);
            changed = true;
        }

        for (Map.Entry<Identifier, AdvancementProgress> e : packet.getAdvancementsToProgress().entrySet()) {
            String key = e.getKey().toString();
            JsonObject incoming = buildAdvancementJson(e.getValue());
            JsonObject existing = getJsonObject(cachedAdvancements, key);
            cachedAdvancements.add(key, mergeAdvancementObjects(existing, incoming));
            removedAdvancements.remove(key);
        }

        if (hadProgressUpdates) changed = true;
        if (changed) {
            advancementsDirty = true;
            maybeFlushPlayerMetaFiles();
        }
    }

    @SuppressWarnings("unchecked")
    private void bootstrapAdvancementsFromClientCache() {
        if (!isSaving || mc.getNetworkHandler() == null || cachedAdvancements == null) return;
        try {
            ClientAdvancementManager advancements = mc.getNetworkHandler().getAdvancementHandler();
            var progressField = ClientAdvancementManager.class.getDeclaredField("progress");
            progressField.setAccessible(true);
            Map<AdvancementEntry, AdvancementProgress> progressMap =
                (Map<AdvancementEntry, AdvancementProgress>) progressField.get(advancements);
            if (progressMap == null || progressMap.isEmpty()) return;

            boolean seeded = false;
            for (Map.Entry<AdvancementEntry, AdvancementProgress> entry : progressMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                String key = entry.getKey().id().toString();
                JsonObject existing = getJsonObject(cachedAdvancements, key);
                cachedAdvancements.add(key, mergeAdvancementObjects(existing, buildAdvancementJson(entry.getValue())));
                seeded = true;
            }
            if (seeded) advancementsDirty = true;
        } catch (ReflectiveOperationException e) {
            System.err.println("Failed to bootstrap advancements from client cache: " + e.getMessage());
        }
    }

    private void maybeFlushPlayerMetaFiles() {
        flushPlayerMetaFiles(false);
    }

    private void flushPlayerMetaFiles(boolean force) {
        if ((!statsDirty && !advancementsDirty) && !force) return;
        if (path == null) return;

        long now = System.currentTimeMillis();
        if (!force && now - lastMetaFlushTimeMs < META_FLUSH_INTERVAL_MS) return;

        UUID target = cachePlayerUuid;
        if (target == null && mc.player != null) target = mc.player.getUuid();
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
        for (String criterionName : progress.getObtainedCriteria()) {
            CriterionProgress cp = progress.getCriterionProgress(criterionName);
            if (cp == null || !cp.isObtained()) continue;
            Instant obtained = cp.getObtainedTime();
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
        Identifier id = Registries.STAT_TYPE.getId(stat.getType());
        return id == null ? null : id.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String getStatValueId(Stat<?> stat) {
        Identifier id = ((Registry) stat.getType().getRegistry()).getId(stat.getValue());
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
