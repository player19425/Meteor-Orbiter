package orbiter.modules.misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import orbiter.Orbiter;

public class EnchCracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoReport = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-report")
            .description("Print the exact offers of all three rows whenever the table data changes.")
            .defaultValue(false)
            .build());

    private final Setting<String> target = sgGeneral.add(new StringSetting.Builder()
            .name("target-enchantment")
            .description("Enchantment to highlight in the report, for example sharpness or silk touch.")
            .defaultValue("")
            .build());

    private final Setting<Integer> filterPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("filter-per-tick")
            .description("How many seed candidates to test per tick while cracking. Lower this if the game stutters.")
            .defaultValue(8192)
            .min(4096)
            .sliderRange(4096, 131072)
            .build());

    private final Setting<Integer> fullScanThreads = sgGeneral.add(new IntSetting.Builder()
            .name("full-scan-threads")
            .description("Worker threads for the rare full 2^32 scan fallback.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 8)
            .build());

    private final Setting<Boolean> autoFullScan = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-full-scan")
            .description("Automatically start the full 2^32 scan when the synced seed is unusable. Very heavy: expect several minutes of CPU and lag.")
            .defaultValue(false)
            .build());

    public enum ScanPhase { IDLE, SCANNING, LOCKED }
    public enum ScanMode { MASKED, FULL }

    private static final int STABLE_TICKS = 6;
    private static final long FULL_TOTAL = 1L << 32;
    private static final long FULL_CHUNK = 1L << 20;

    private static EnchCracker instance;

    private ScanPhase scanPhase = ScanPhase.IDLE;
    private ScanMode scanMode = ScanMode.MASKED;
    private final HashSet<Integer> possibleSeeds = new HashSet<>(1 << 20);
    private long trueSeed = -1;
    private int power = -1;
    private int lockedPower = -1;
    private long lastMasked = Long.MIN_VALUE;
    private BlockPos tablePos;

    private boolean distrustSync = false;
    private boolean fullScanRequested = false;
    private int zeroStreak = 0;

    private final AtomicLong fullCursor = new AtomicLong();
    private final AtomicInteger fullWorkersLeft = new AtomicInteger();
    private final java.util.Set<Integer> fullFound = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final java.util.List<Thread> fullThreads = new ArrayList<>();
    private volatile boolean fullCancelled;
    private int[] snapCosts = new int[3];
    private int[] snapClue = new int[3];
    private int[] snapLevel = new int[3];
    private ItemStack snapItem = ItemStack.EMPTY;
    private int progressClock = 0;

    private long chainState = -1;
    private int prevLockedSeed = Integer.MIN_VALUE;

    private final int[] obsCosts = {-1, -1, -1};
    private final int[] obsClue = {-1, -1, -1};
    private final int[] obsLevel = {-1, -1, -1};
    private String obsItemKey = "";
    private int stableTicks = 0;
    private int lastScanSize = -1;
    private int stallTicks = 0;
    private int statusClock = 0;

    private int lastSeedSnapshot = Integer.MIN_VALUE;
    private String lastItemSnapshot = "";
    private int lastCost0 = -1;
    private int lastCost1 = -1;
    private int lastCost2 = -1;

    public EnchCracker() {
        super(Orbiter.CATEGORY, "enchantment-cracker",
                "Cracks the enchanting seed from the table clues and predicts every row exactly.");
        instance = this;
    }

    @Override
    public void onActivate() {
        resetCrack();
        resetSnapshot();
    }

    @Override
    public void onDeactivate() {
        resetCrack();
    }

    @EventHandler
    private void onGameLeft(meteordevelopment.meteorclient.events.game.GameLeftEvent event) {
        distrustSync = false;
        chainState = -1;
        prevLockedSeed = Integer.MIN_VALUE;
        resetCrack();
    }

    public static void onTableUsed(BlockPos pos) {
        if (instance != null) instance.tablePos = pos.immutable();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (statusClock > 0) statusClock--;

        if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
            resetSnapshot();
            return;
        }

        updateCrack(menu);
        reportTick(menu);
    }

    private void reportTick(EnchantmentMenu menu) {
        ItemStack item = menu.getSlot(0).getItem();
        String itemKey = keyOf(item);

        boolean changed = menu.getEnchantmentSeed() != lastSeedSnapshot || !itemKey.equals(lastItemSnapshot)
                || menu.costs[0] != lastCost0 || menu.costs[1] != lastCost1 || menu.costs[2] != lastCost2;
        if (!changed) return;

        lastSeedSnapshot = menu.getEnchantmentSeed();
        lastItemSnapshot = itemKey;
        lastCost0 = menu.costs[0];
        lastCost1 = menu.costs[1];
        lastCost2 = menu.costs[2];

        if (item.isEmpty() || scanPhase != ScanPhase.LOCKED) return;
        if (autoReport.get()) reportOffers(item);
    }

    public boolean reportOffers(ItemStack item) {
        Offer[] offers = predictOffers(item);
        if (offers == null) return false;

        info("Offers for " + pathOf(item) + " (seed " + String.format("%08X", trueSeed)
                + ", " + power + " shelves):");
        String targetName = normalizeName(target.get());
        int foundRow = -1;
        int foundLevel = 0;
        String foundName = "";
        for (int r = 0; r < offers.length; r++) {
            boolean hit = false;
            if (!targetName.isEmpty()) {
                for (EnchantmentInstance inst : offers[r].enchantments()) {
                    if (nameMatches(targetName, nameOf(inst))) {
                        hit = true;
                        foundName = nameOf(inst);
                        foundLevel = inst.level();
                        break;
                    }
                }
            }
            info("Row " + (r + 1) + (hit ? " (TARGET) - " : " - ") + describe(offers[r]));
            if (hit && foundRow == -1) foundRow = r;
        }

        if (targetName.isEmpty()) return true;
        if (foundRow >= 0) {
            info("Target " + foundName + " " + foundLevel + " on row " + (foundRow + 1)
                    + ", cost " + offers[foundRow].cost() + ".");
        } else {
            warning("Target " + targetName + " is not in the three rows for this item.");
        }
        return true;
    }

    public static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace(' ', '_');
    }

    public boolean targetMatches(EnchantmentInstance instance) {
        return nameMatches(normalizeName(target.get()), nameOf(instance));
    }

    private static boolean nameMatches(String user, String name) {
        if (user.isEmpty()) return false;
        return name.equals(user) || name.contains(user);
    }

    public void setTarget(String value) {
        target.set(value == null ? "" : value.trim());
    }

    private boolean observationMatches(EnchantmentMenu menu, String itemKey) {
        return menu.costs[0] == obsCosts[0] && menu.costs[1] == obsCosts[1] && menu.costs[2] == obsCosts[2]
                && menu.enchantClue[0] == obsClue[0] && menu.enchantClue[1] == obsClue[1] && menu.enchantClue[2] == obsClue[2]
                && menu.levelClue[0] == obsLevel[0] && menu.levelClue[1] == obsLevel[1] && menu.levelClue[2] == obsLevel[2]
                && itemKey.equals(obsItemKey);
    }

    private void snapshotObservation(EnchantmentMenu menu, String itemKey) {
        System.arraycopy(menu.costs, 0, obsCosts, 0, 3);
        System.arraycopy(menu.enchantClue, 0, obsClue, 0, 3);
        System.arraycopy(menu.levelClue, 0, obsLevel, 0, 3);
        obsItemKey = itemKey;
        stableTicks = 1;
    }

    private void updateCrack(EnchantmentMenu menu) {
        long masked = menu.getEnchantmentSeed() & 0x0000FFFFL;

        if (scanPhase == ScanPhase.LOCKED) {
            ItemStack item = menu.getSlot(0).getItem();
            String itemKey = keyOf(item);
            boolean changed = masked != lastMasked || !observationMatches(menu, itemKey);
            lastMasked = masked;
            if (changed) {
                snapshotObservation(menu, itemKey);
                return;
            }
            if (++stableTicks == STABLE_TICKS) {
                int currentPower = countBookshelves(tablePos);
                if (currentPower != lockedPower) {
                    info("Shelf count changed (" + lockedPower + " to " + currentPower + "), re-cracking.");
                    resetCrack();
                    return;
                }
                if (!item.isEmpty() && item.isEnchantable() && !verifyCurrent(menu, item)) {
                    resetCrack();
                }
            }
            return;
        }

        ItemStack item = menu.getSlot(0).getItem();
        String itemKey = keyOf(item);
        if (!item.isEmpty() && !item.isEnchantable()) return;

        boolean obsChanged = masked != lastMasked || !observationMatches(menu, itemKey);
        if (obsChanged) {
            if (scanPhase == ScanPhase.SCANNING) restartScan();
            snapshotObservation(menu, itemKey);
            lastMasked = masked;
            return;
        }

        stableTicks++;
        if (stableTicks < STABLE_TICKS) return;
        if (item.isEmpty()) return;

        if (tablePos == null || !isValidTablePos()) {
            scanForTable();
            if (tablePos == null) {
                if (statusClock <= 0) {
                    warning("No enchanting table found nearby: right-click the table once so the shelf count is measured from the right block.");
                    statusClock = 100;
                }
                return;
            }
        }
        power = countBookshelves(tablePos);

        if (scanPhase == ScanPhase.IDLE) {
            if (menu.costs[0] <= 0 && menu.costs[1] <= 0 && menu.costs[2] <= 0) return;
            if (masked == 0) {
                zeroStreak++;
                if (zeroStreak >= 3 && !distrustSync) {
                    distrustSync = true;
                    if (autoFullScan.get() || fullScanRequested) {
                        warning("The synced seed looks unreliable, starting a full scan.");
                    } else {
                        warning("The synced seed looks unusable on this server. Enable 'auto-full-scan' in settings or run .encc fullscan to brute-force 2^32 (heavy: minutes of CPU).");
                        return;
                    }
                }
            } else {
                zeroStreak = 0;
                if (distrustSync && !fullScanRequested) {
                    distrustSync = false;
                    info("The synced seed is being sent again, resuming normal cracking.");
                }
            }
            beginScan(masked, item);
            return;
        }

        if (scanMode == ScanMode.FULL) {
            pollFullScan();
            return;
        }

        filterMasked(menu, item);
        evaluateScanResult();
    }

    private void beginScan(long masked, ItemStack item) {
        stopFullScan();
        possibleSeeds.clear();
        fullFound.clear();

        Integer chained = tryChainPredict(item);
        if (chained != null) {
            possibleSeeds.add(chained);
            scanMode = ScanMode.MASKED;
            scanPhase = ScanPhase.SCANNING;
            lastScanSize = -1;
            evaluateScanResult();
            return;
        }

        if (!distrustSync && masked != 0) {
            scanMode = ScanMode.MASKED;
            int fixedBits = (int) masked & 0xFFFF;
            for (int high = 0; high < 65536; high++) {
                possibleSeeds.add((high << 16) | fixedBits);
            }
            scanPhase = ScanPhase.SCANNING;
            progressClock = 0;
            stallTicks = 0;
            lastScanSize = -1;
            info("Cracking seed: " + possibleSeeds.size() + " candidates (table power = " + power + " shelves).");
            return;
        }

        if (!autoFullScan.get() && !fullScanRequested) {
            warning("Cannot crack from the current clues. Run .encc fullscan to brute-force the whole 2^32 space (heavy), or re-place the item for a fresh reading.");
            scanPhase = ScanPhase.IDLE;
            return;
        }

        startFullScan(item);
    }

    private void restartScan() {
        if (scanPhase == ScanPhase.SCANNING && scanMode == ScanMode.FULL) {
            stopFullScan();
            scanPhase = ScanPhase.IDLE;
        } else if (scanPhase == ScanPhase.SCANNING) {
            resetCrack();
        }
    }

    private void filterMasked(EnchantmentMenu menu, ItemStack item) {
        var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var idMap = registry.asHolderIdMap();
        RandomSource rand = RandomSource.create();

        Iterator<Integer> iterator = possibleSeeds.iterator();
        int processed = 0;
        while (iterator.hasNext() && processed < filterPerTick.get()) {
            processed++;
            int candidate = iterator.next();
            rand.setSeed(candidate);
            if (!candidateMatches(registry, idMap, rand, candidate, item,
                    menu.costs, menu.enchantClue, menu.levelClue, power)) {
                iterator.remove();
            }
        }
    }

    private boolean candidateMatches(net.minecraft.core.Registry<Enchantment> registry,
            net.minecraft.core.IdMap<Holder<Enchantment>> idMap, RandomSource rand, int candidate, ItemStack item,
            int[] costs, int[] clues, int[] levels, int tablePower) {
        rand.setSeed(candidate);

        for (int row = 0; row < 3; row++) {
            int cost = EnchantmentHelper.getEnchantmentCost(rand, row, tablePower, item);
            if (cost < row + 1) cost = 0;
            if (cost != costs[row]) return false;
        }

        for (int row = 0; row < 3; row++) {
            if (costs[row] <= 0) continue;
            List<EnchantmentInstance> rolled = vanillaList(registry, rand, candidate, item, row, costs[row]);
            if (rolled.isEmpty()) {
                if (clues[row] != -1 || levels[row] != -1) return false;
            } else {
                EnchantmentInstance clue = rolled.get(rand.nextInt(rolled.size()));
                if (idMap.getId(clue.enchantment()) != clues[row] || clue.level() != levels[row]) return false;
            }
        }
        return true;
    }

    private void startFullScan(ItemStack item) {
        stopFullScan();
        scanMode = ScanMode.FULL;
        scanPhase = ScanPhase.SCANNING;
        progressClock = 0;

        for (int i = 0; i < 3; i++) {
            snapCosts[i] = obsCosts[i];
            snapClue[i] = obsClue[i];
            snapLevel[i] = obsLevel[i];
        }
        snapItem = item.copy();

        var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var idMap = registry.asHolderIdMap();
        int scanPower = power;
        ItemStack snapRef = snapItem;
        int[] c = new int[3];
        int[] cl = new int[3];
        int[] lv = new int[3];
        System.arraycopy(snapCosts, 0, c, 0, 3);
        System.arraycopy(snapClue, 0, cl, 0, 3);
        System.arraycopy(snapLevel, 0, lv, 0, 3);

        fullCursor.set(0);
        fullFound.clear();
        fullCancelled = false;
        int cores = Math.max(1, Math.min(fullScanThreads.get(), Runtime.getRuntime().availableProcessors() - 1));
        fullWorkersLeft.set(cores);

        for (int t = 0; t < cores; t++) {
            RandomSource rand = RandomSource.create();
            Thread worker = new Thread(() -> {
                try {
                    while (!fullCancelled) {
                        long base = fullCursor.getAndAdd(FULL_CHUNK);
                        if (base >= FULL_TOTAL) break;
                        long end = Math.min(base + FULL_CHUNK, FULL_TOTAL);
                        for (long s = base; s < end && !fullCancelled; s++) {
                            int candidate = (int) s;
                            if (candidateMatches(registry, idMap, rand, candidate, snapRef, c, cl, lv, scanPower)) {
                                fullFound.add(candidate);
                            }
                        }
                        java.util.concurrent.locks.LockSupport.parkNanos(400_000L);
                    }
                } finally {
                    fullWorkersLeft.decrementAndGet();
                }
            }, "orbiter-ench-scan");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            fullThreads.add(worker);
            worker.start();
        }
        info("Synced seed unusable, searching the whole 2^32 space on " + cores + " threads.");
    }

    private void pollFullScan() {
        progressClock++;
        if (!fullFound.isEmpty()) {
            possibleSeeds.addAll(fullFound);
            fullFound.clear();
            if (possibleSeeds.size() == 1) {
                stopFullScan();
                evaluateScanResult();
                return;
            }
        }
        if (fullWorkersLeft.get() == 0) {
            possibleSeeds.addAll(fullFound);
            fullFound.clear();
            stopFullScan();
            evaluateScanResult();
            return;
        }
        if (progressClock % 200 == 0) {
            long done = Math.min(fullCursor.get(), FULL_TOTAL);
            info("Full scan progress: " + (done * 100 / FULL_TOTAL) + " out of 100.");
        }
    }

    private void stopFullScan() {
        fullCancelled = true;
        if (!fullThreads.isEmpty()) {
            for (Thread worker : fullThreads) {
                try {
                    worker.join(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            fullThreads.clear();
        }
    }

    private void evaluateScanResult() {
        if (possibleSeeds.size() == 1) {
            trueSeed = possibleSeeds.iterator().next();
            lockedPower = power;
            scanPhase = ScanPhase.LOCKED;
            distrustSync = false;
            zeroStreak = 0;
            fullScanRequested = false;
            noteChain((int) trueSeed);
            info("Seed found: " + String.format("%08X", trueSeed) + ". Every row is now predicted exactly.");
        } else if (possibleSeeds.isEmpty()) {
            if (scanMode == ScanMode.FULL) {
                distrustSync = false;
                chainState = -1;
                prevLockedSeed = Integer.MIN_VALUE;
            } else if (scanMode == ScanMode.MASKED && !distrustSync) {
                distrustSync = true;
                resetCrack();
                return;
            }
            resetCrack();
            if (statusClock <= 0) {
                warning("Crack failed: the table data changed mid-scan. Waiting for a stable reading.");
                statusClock = 100;
            }
        } else {
            int size = possibleSeeds.size();
            if (size != lastScanSize) {
                lastScanSize = size;
                stallTicks = 0;
            }
            progressClock++;
            if (progressClock % 100 == 0) info("Cracking seed: " + size + " possible seeds remaining.");
        }
    }

    private boolean verifyCurrent(EnchantmentMenu menu, ItemStack item) {
        if (item.isEmpty() || !item.isEnchantable()) return true;
        var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var idMap = registry.asHolderIdMap();
        RandomSource rand = RandomSource.create();
        int seed = (int) trueSeed;
        rand.setSeed(seed);

        for (int slot = 0; slot < 3; slot++) {
            int cost = EnchantmentHelper.getEnchantmentCost(rand, slot, power, item);
            if (cost < slot + 1) cost = 0;
            if (cost != menu.costs[slot]) return false;
        }
        for (int slot = 0; slot < 3; slot++) {
            if (menu.costs[slot] <= 0) continue;
            List<EnchantmentInstance> rolled = vanillaList(registry, rand, seed, item, slot, menu.costs[slot]);
            if (rolled.isEmpty()) {
                if (menu.enchantClue[slot] != -1 || menu.levelClue[slot] != -1) return false;
            } else {
                EnchantmentInstance clue = rolled.get(rand.nextInt(rolled.size()));
                if (idMap.getId(clue.enchantment()) != menu.enchantClue[slot]
                        || clue.level() != menu.levelClue[slot]) return false;
            }
        }
        return true;
    }

    private void resetCrack() {
        stopFullScan();
        possibleSeeds.clear();
        scanPhase = ScanPhase.IDLE;
        scanMode = ScanMode.MASKED;
        trueSeed = -1;
        lockedPower = -1;
        stableTicks = 0;
        lastScanSize = -1;
        stallTicks = 0;
        tablePos = null;
    }

    public void forceReset() {
        chainState = -1;
        prevLockedSeed = Integer.MIN_VALUE;
        distrustSync = false;
        fullScanRequested = false;
        zeroStreak = 0;
        resetCrack();
        resetSnapshot();
    }

    public boolean requestFullScan() {
        if (mc.player == null || mc.level == null) return false;
        if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
            warning("Open an enchanting table with an item in it first.");
            return false;
        }
        ItemStack item = menu.getSlot(0).getItem();
        if (item.isEmpty() || !item.isEnchantable()) {
            warning("Put an enchantable item in the table first.");
            return false;
        }
        distrustSync = true;
        fullScanRequested = true;
        zeroStreak = 0;
        resetCrack();
        stableTicks = STABLE_TICKS - 1;
        updateCrack(menu);
        return scanPhase != ScanPhase.IDLE;
    }

    private void scanForTable() {
        if (mc.player == null || mc.level == null) return;
        BlockPos base = mc.player.blockPosition();
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) continue;
                    int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
        }
        if (best != null) tablePos = best;
    }

    private boolean isValidTablePos() {
        return tablePos != null && mc.level != null && mc.level.getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE);
    }

    private int countBookshelves(BlockPos pos) {
        if (pos == null || mc.level == null) return 0;
        int result = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(mc.level, pos, offset)) result++;
        }
        return result;
    }

    private List<EnchantmentInstance> vanillaList(net.minecraft.core.Registry<Enchantment> registry,
            RandomSource rand, int xpSeed, ItemStack stack, int slot, int level) {
        rand.setSeed(xpSeed + slot);
        var tag = registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE);
        List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(
                rand, stack, level, StreamSupport.stream(tag.spliterator(), false));
        if (stack.getItem() == Items.BOOK && list.size() > 1) {
            list.remove(rand.nextInt(list.size()));
        }
        return list;
    }

    private Integer tryChainPredict(ItemStack item) {
        if (chainState < 0 || mc.level == null) return null;
        try {
            long next = stepChain(chainState);
            int candidate = (int) (next >>> 16);
            var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var idMap = registry.asHolderIdMap();
            if (candidateMatches(registry, idMap, RandomSource.create(), candidate, item,
                    obsCosts, obsClue, obsLevel, power)) {
                chainState = next;
                return candidate;
            }
            chainState = -1;
        } catch (Throwable ignored) {
            chainState = -1;
        }
        return null;
    }

    private void noteChain(int lockedSeed) {
        if (chainState >= 0) {
            prevLockedSeed = lockedSeed;
            return;
        }
        if (prevLockedSeed != Integer.MIN_VALUE && prevLockedSeed != lockedSeed) {
            chainState = recover48(prevLockedSeed, lockedSeed);
        }
        prevLockedSeed = lockedSeed;
    }

    private static long stepChain(long state) {
        return (state * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
    }

    private static long recover48(int u1raw, int u2raw) {
        long u1 = Integer.toUnsignedLong(u1raw);
        long u2 = Integer.toUnsignedLong(u2raw);
        long max1 = u1 + 1;
        long max2 = u2 + 1;
        long a = (24667315L * max1 + 18218081L * max2) >> 32;
        long b = (-4824621L * u1 + 7847617L * max2) >> 32;
        long seed = (7847617L * a - 18218081L * b) & 0xFFFFFFFFFFFFL;
        if ((int) (seed >>> 16) != u1raw) return -1;
        long advanced = stepChain(seed);
        if ((int) (advanced >>> 16) != u2raw) return -1;
        return advanced;
    }

    public Offer[] offersFor(ItemStack stack) {
        return predictOffers(stack);
    }

    private Offer[] predictOffers(ItemStack stack) {
        if (stack == null || stack.isEmpty() || scanPhase != ScanPhase.LOCKED || mc.level == null || power < 0) return null;
        try {
            var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            int seed = (int) trueSeed;
            RandomSource costsRand = RandomSource.create();
            costsRand.setSeed(seed);
            int[] costs = new int[3];
            for (int row = 0; row < 3; row++) {
                int cost = EnchantmentHelper.getEnchantmentCost(costsRand, row, power, stack);
                if (cost < row + 1) cost = 0;
                costs[row] = cost;
            }
            Offer[] offers = new Offer[3];
            for (int row = 0; row < 3; row++) {
                if (costs[row] <= 0) {
                    offers[row] = new Offer(0, List.of());
                    continue;
                }
                offers[row] = new Offer(costs[row],
                        vanillaList(registry, RandomSource.create(), seed, stack, row, costs[row]));
            }
            return offers;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String describe(Offer offer) {
        StringBuilder sb = new StringBuilder();
        sb.append("cost ").append(offer.cost()).append(": ");
        if (offer.enchantments().isEmpty()) sb.append("(nothing)");
        sb.append(describeList(offer.enchantments()));
        return sb.toString();
    }

    private static String describeList(List<EnchantmentInstance> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            EnchantmentInstance inst = list.get(i);
            sb.append(nameOf(inst)).append(' ').append(inst.level());
        }
        return sb.toString();
    }

    public static String nameOf(EnchantmentInstance instance) {
        String name = instance.enchantment().getRegisteredName();
        return name.startsWith("minecraft:") ? name.substring(10) : name;
    }

    private void resetSnapshot() {
        lastSeedSnapshot = Integer.MIN_VALUE;
        lastItemSnapshot = "";
        lastCost0 = -1;
        lastCost1 = -1;
        lastCost2 = -1;
    }

    private static String pathOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "item" : id.getPath();
    }

    private static String keyOf(ItemStack stack) {
        if (stack.isEmpty()) return "";
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    @Override
    public String getInfoString() {
        if (scanPhase == ScanPhase.LOCKED) return String.format("%08X", trueSeed);
        if (scanPhase == ScanPhase.SCANNING) {
            if (scanMode == ScanMode.FULL) {
                long pct = Math.min(fullCursor.get(), FULL_TOTAL) * 100 / FULL_TOTAL;
                return "full scan " + pct + "% (sync broken)";
            }
            return "cracking (" + possibleSeeds.size() + " left)";
        }
        if (distrustSync) return "idle (sync broken)";
        return "idle";
    }

    public record Offer(int cost, List<EnchantmentInstance> enchantments) {}
}
