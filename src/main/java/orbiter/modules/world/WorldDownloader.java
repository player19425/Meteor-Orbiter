package orbiter.modules.world;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
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
        .description("Only re-save chunks that actually changed since the last save (skips re-encoding identical chunk NBT every cycle). Massively reduces CPU/IO under steady state.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chunksPerTickBudget = sgChunks.add(new IntSetting.Builder()
        .name("chunks-per-tick-budget")
        .description("Maximum chunks to build+save per save cycle. Caps the per-tick work so the saver never causes a frame hitch even with a huge radius.")
        .defaultValue(8)
        .min(1)
        .sliderRange(1, 64)
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
        .description("Manual = capture only what you interact with. DetectAll = open nearby containers automatically.")
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
    private int fullSweepCounter = 0;
    private int lastContainerOpenTick = 0;

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
        interactedEntities.clear();
        openedContainers.clear();
        dirtyChunks.clear();
        saveTickCounter = 0;
        detectTickCounter = 0;
        entityScanCounter = 0;
        fullSweepCounter = 0;

        if (statusMessages.get()) info("WorldDownloader started: saving to " + savePath);
    }

    @Override
    public void onDeactivate() {
        if (saveManager != null) {
            if (mc.player != null) saveManager.savePlayerInventory(mc.player.getUUID(), mc.player.getInventory());
            saveManager.close();
            saveManager = null;
        }
        if (statusMessages.get()) info("WorldDownloader stopped.");
        interactedEntities.clear();
        openedContainers.clear();
        dirtyChunks.clear();
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

        saveTickCounter++;
        if (saveTickCounter >= saveIntervalTicks.get()) {
            saveTickCounter = 0;
            saveChunksAround();
        }

        if (detectMode.get() == DetectMode.DetectAll) {
            detectTickCounter++;
            if (detectTickCounter >= detectIntervalTicks.get()) {
                detectTickCounter = 0;
                scanAndOpenNearbyContainers();
            }
        }

        if (saveEntities.get()) {
            entityScanCounter++;
            if (entityScanCounter >= 20) {
                entityScanCounter = 0;
                saveEntitiesAround();
            }
        }
    }

    private void scanAndOpenNearbyContainers() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null) return;

        if (lastContainerOpenTick > 0 && mc.player.tickCount - lastContainerOpenTick < 20) return;

        BlockPos center = mc.player.blockPosition();
        int r = detectRadius.get();

        int blockBudget = 512;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (--blockBudget < 0) return;
                    BlockPos pos = center.offset(x, y, z);
                    if (openedContainers.contains(pos)) continue;

                    BlockState state = mc.level.getBlockState(pos);
                    if (!isContainerBlock(state)) continue;

                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (!(be instanceof Container)) continue;

                    openedContainers.add(pos);
                    lastClicked = pos;
                    saveManager.lastClicked = pos;
                    lastContainerOpenTick = mc.player.tickCount;
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    if (mc.player != null) mc.player.closeContainer();
                    return;
                }
            }
        }
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

    private void saveChunksAround() {
        if (mc.player == null || mc.level == null || saveManager == null) return;
        int r = chunkRadius.get();
        int pcx = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).x();
        int pcz = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).z();

        int budget = Math.max(1, chunksPerTickBudget.get());

        boolean onlyChanged = saveOnlyChanged.get();

        fullSweepCounter++;
        boolean forceFullSweep = !onlyChanged || (fullSweepCounter % 5 == 0);
        if (forceFullSweep) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    dirtyChunks.add(ChunkPos.pack(pcx + dx, pcz + dz));
                }
            }
        }

        int processed = 0;
        for (int dx = -r; dx <= r && processed < budget; dx++) {
            for (int dz = -r; dz <= r && processed < budget; dz++) {
                long key = ChunkPos.pack(pcx + dx, pcz + dz);
                if (!dirtyChunks.remove(key)) continue;
                LevelChunk chunk = mc.level.getChunk(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                saveManager.saveChunk(chunk);
                processed++;
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
    }

    private void saveEntitiesAround() {
        if (mc.player == null || mc.level == null || saveManager == null) return;
        if (!saveEntities.get()) return;
        int r = chunkRadius.get();
        int pcx = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).x();
        int pcz = net.minecraft.world.level.ChunkPos.containing(mc.player.blockPosition()).z();

        int budget = Math.max(1, chunksPerTickBudget.get());
        int scanned = 0;
        int entityCount = 0;
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (scanned >= budget) break outer;
                LevelChunk chunk = mc.level.getChunk(pcx + dx, pcz + dz);
                scanned++;
                if (chunk != null) {
                    saveManager.saveEntitiesForChunk(chunk);

                    int minY = mc.level.getMinY();
                    int maxY = minY + mc.level.getHeight();
                    BlockPos start = new BlockPos(chunk.getPos().getMinBlockX(), minY, chunk.getPos().getMinBlockZ());
                    BlockPos end = new BlockPos(chunk.getPos().getMaxBlockX() + 1, maxY, chunk.getPos().getMaxBlockZ() + 1);
                    entityCount += mc.level.getEntities(EntityTypeTest.forClass(Entity.class), new net.minecraft.world.phys.AABB(start.getX(), start.getY(), start.getZ(),
                            end.getX(), end.getY(), end.getZ()),
                        e -> !(e instanceof Player)).size();
                }
            }
        }
        if (statusMessages.get() && mc.player != null && entityCount > 0) {
            mc.player.sendOverlayMessage(Component.literal("§6[WDL] §fSaved §a" + entityCount + "§f entities (scanned " + scanned + "/" + ((2 * r + 1) * (2 * r + 1)) + " chunks)"));
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (saveManager == null || !saveManager.isSaving) return;
        if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {

            LevelChunk chunk = mc.level != null ? mc.level.getChunk(packet.getX(), packet.getZ()) : null;
            if (chunk != null) {
                saveManager.saveChunk(chunk);
                dirtyChunks.add(chunk.getPos().pack());
            }
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
            BlockPos pos = blockPacket.getHitResult().getBlockPos();
            lastClicked = pos;
            saveManager.lastClicked = pos;
            openedContainers.add(pos);
        }
    }

    @EventHandler
    private void onTickCheckScreen(TickEvent.Pre event) {
        if (saveManager == null || !saveManager.isSaving) return;
        Screen current = mc.gui.screen();

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
                CompoundTag overlay = new CompoundTag();
                MerchantOffers offers = capturedMerchant.getOffers();
                if (!offers.isEmpty()) {
                    MerchantOffers.CODEC.encodeStart(NbtOps.INSTANCE, offers)
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
}
