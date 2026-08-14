package orbiter.modules.world;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

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
    private MerchantScreenHandler capturedMerchant = null;
    private int saveTickCounter = 0;
    private int detectTickCounter = 0;
    private int entityScanCounter = 0;
    private int fullSweepCounter = 0;
    private int lastContainerOpenTick = 0;

    private final Set<Long> dirtyChunks = ConcurrentHashMap.newKeySet();

    public WorldDownloader() {
        super(Orbiter.CATEGORY_OP, "world-downloader",
            "Saves chunks, containers, and entities from any server to a local Minecraft world.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
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
            if (mc.player != null) saveManager.savePlayerInventory(mc.player.getUuid(), mc.player.getInventory());
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
        if (autoStart.get() && !isActive() && mc.player != null && mc.world != null && !mc.isInSingleplayer()) {
            toggle();
        }
    }

    private Path buildSavePath() {
        String name = worldName.get();
        if (name == null || name.isBlank()) {
            if (mc.getCurrentServerEntry() != null) name = sanitize(mc.getCurrentServerEntry().address);
            else name = "OrbiterWorld";
        } else name = sanitize(name);

        Path saves = mc.getLevelStorage().getSavesDirectory();
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
        if (saveManager == null || !saveManager.isSaving || mc.player == null || mc.world == null) return;

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
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (lastContainerOpenTick > 0 && mc.player.age - lastContainerOpenTick < 20) return;

        BlockPos center = mc.player.getBlockPos();
        int r = detectRadius.get();

        int blockBudget = 512;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (--blockBudget < 0) return;
                    BlockPos pos = center.add(x, y, z);
                    if (openedContainers.contains(pos)) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    if (!isContainerBlock(state)) continue;

                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (!(be instanceof Inventory)) continue;

                    openedContainers.add(pos);
                    lastClicked = pos;
                    saveManager.lastClicked = pos;
                    lastContainerOpenTick = mc.player.age;
                    BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                    if (mc.player != null) mc.player.closeHandledScreen();
                    return;
                }
            }
        }
    }

    private boolean isContainerBlock(BlockState state) {
        if (state.getBlock() instanceof ChestBlock) return true;
        Block block = state.getBlock();
        String id = Registries.BLOCK.getId(block).toString();
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
        if (mc.player == null || mc.world == null || saveManager == null) return;
        int r = chunkRadius.get();
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        int budget = Math.max(1, chunksPerTickBudget.get());

        boolean onlyChanged = saveOnlyChanged.get();

        fullSweepCounter++;
        boolean forceFullSweep = !onlyChanged || (fullSweepCounter % 5 == 0);
        if (forceFullSweep) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    dirtyChunks.add(ChunkPos.toLong(pcx + dx, pcz + dz));
                }
            }
        }

        int processed = 0;
        for (int dx = -r; dx <= r && processed < budget; dx++) {
            for (int dz = -r; dz <= r && processed < budget; dz++) {
                long key = ChunkPos.toLong(pcx + dx, pcz + dz);
                if (!dirtyChunks.remove(key)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (chunk == null) continue;
                saveManager.saveChunk(chunk);
                processed++;
            }
        }

        if (dirtyChunks.size() > 1024) {
            Iterator<Long> it = dirtyChunks.iterator();
            while (it.hasNext()) {
                long k = it.next();
                int cx = ChunkPos.getPackedX(k);
                int cz = ChunkPos.getPackedZ(k);
                if (Math.abs(cx - pcx) > r || Math.abs(cz - pcz) > r) it.remove();
            }
        }
    }

    private void saveEntitiesAround() {
        if (mc.player == null || mc.world == null || saveManager == null) return;
        if (!saveEntities.get()) return;
        int r = chunkRadius.get();
        int pcx = mc.player.getChunkPos().x;
        int pcz = mc.player.getChunkPos().z;

        int budget = Math.max(1, chunksPerTickBudget.get());
        int scanned = 0;
        int entityCount = 0;
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (scanned >= budget) break outer;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                scanned++;
                if (chunk != null) {
                    saveManager.saveEntitiesForChunk(chunk);

                    int minY = mc.world.getBottomY();
                    int maxY = minY + mc.world.getHeight();
                    BlockPos start = new BlockPos(chunk.getPos().getStartX(), minY, chunk.getPos().getStartZ());
                    BlockPos end = new BlockPos(chunk.getPos().getEndX() + 1, maxY, chunk.getPos().getEndZ() + 1);
                    entityCount += mc.world.getEntitiesByClass(Entity.class,
                        new net.minecraft.util.math.Box(start.getX(), start.getY(), start.getZ(),
                            end.getX(), end.getY(), end.getZ()),
                        e -> !(e instanceof PlayerEntity)).size();
                }
            }
        }
        if (statusMessages.get() && mc.player != null && entityCount > 0) {
            mc.player.sendMessage(Text.literal("§6[WDL] §fSaved §a" + entityCount + "§f entities (scanned " + scanned + "/" + ((2 * r + 1) * (2 * r + 1)) + " chunks)"), true);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (saveManager == null || !saveManager.isSaving) return;
        if (event.packet instanceof ChunkDataS2CPacket packet) {

            WorldChunk chunk = mc.world != null ? mc.world.getChunkManager().getWorldChunk(packet.getChunkX(), packet.getChunkZ()) : null;
            if (chunk != null) {
                saveManager.saveChunk(chunk);
                dirtyChunks.add(chunk.getPos().toLong());
            }
        } else if (event.packet instanceof BlockUpdateS2CPacket packet) {

            if (mc.world != null && saveOnlyChanged.get()) {
                dirtyChunks.add(new ChunkPos(packet.getPos()).toLong());
            }
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {

            if (mc.world != null && saveOnlyChanged.get()) {
                packet.visitUpdates((pos, state) -> dirtyChunks.add(new ChunkPos(pos).toLong()));
            }
        } else if (event.packet instanceof StatisticsS2CPacket packet) {
            saveManager.cacheStatsPacket(packet);
        } else if (event.packet instanceof AdvancementUpdateS2CPacket packet) {
            saveManager.cacheAdvancementPacket(packet);
        }
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (saveManager == null || !saveManager.isSaving) return;
        Entity entity = event.entity;
        if (entity == null) return;
        interactedEntities.add(entity.getUuid());
        lastClicked = entity;
        saveManager.lastClicked = entity;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (saveManager == null || mc.player == null) return;
        if (event.packet instanceof PlayerInteractBlockC2SPacket blockPacket) {
            BlockPos pos = blockPacket.getBlockHitResult().getBlockPos();
            lastClicked = pos;
            saveManager.lastClicked = pos;
            openedContainers.add(pos);
        }
    }

    @EventHandler
    private void onTickCheckScreen(TickEvent.Pre event) {
        if (saveManager == null || !saveManager.isSaving) return;
        Screen current = mc.currentScreen;

        if (current instanceof MerchantScreen && saveShopkeeperTrades.get()) {
            if (mc.player.currentScreenHandler instanceof MerchantScreenHandler merchant) {
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
                NbtCompound overlay = new NbtCompound();
                TradeOfferList offers = capturedMerchant.getRecipes();
                if (!offers.isEmpty()) {
                    TradeOfferList.CODEC.encodeStart(NbtOps.INSTANCE, offers)
                        .result().ifPresent(t -> overlay.put("Offers", t));
                }
                overlay.putInt("Xp", capturedMerchant.getExperience());
                saveManager.cacheEntityOverride(entity.getUuid(), overlay);
                saveManager.saveChunkAt(entity.getBlockPos());
                if (statusMessages.get()) info("Saved trade data for " + entity.getName().getString());
            }
            return;
        }

        if (saveContainers.get() && closed instanceof HandledScreen<?> handled) {
            List<ItemStack> items = new ArrayList<>(handled.getScreenHandler().getStacks());

            if (closed.getTitle().getString().equals(Text.translatable("container.enderchest").getString())) {
                List<ItemStack> chestItems = new ArrayList<>(items.subList(0, Math.min(27, items.size())));
                saveManager.cacheEnderChest(mc.player.getUuid(), chestItems);
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
            } else if (lastClicked instanceof Entity entity && interactedEntities.contains(entity.getUuid())) {
                if (entity instanceof AbstractHorseEntity && !saveHorseInventories.get()) return;
                saveManager.cacheEntityInventory(entity.getUuid(), new ArrayList<>(items));
                saveManager.saveChunkAt(entity.getBlockPos());
                if (statusMessages.get()) info("Saved entity inventory for " + entity.getName().getString());
            }
        }
    }
}
