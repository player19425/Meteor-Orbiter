package orbiter.modules.misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
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
import orbiter.util.TableOps;

public class EnchCracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> targetEnchantment = sgGeneral.add(new StringSetting.Builder()
            .name("target-enchantment")
            .description("Enchantment to farm for when auto-farm is on (empty = off).")
            .defaultValue("")
            .build());

    public final Setting<Integer> targetLevel = sgGeneral.add(new IntSetting.Builder()
            .name("target-level")
            .description("Exact level wanted (0 = any level).")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 5)
            .build());

    public final Setting<String> targetItem = sgGeneral.add(new StringSetting.Builder()
            .name("target-item")
            .description("Item id to enchant, e.g. diamond_sword (empty = use held item when starting).")
            .defaultValue("")
            .build());

    private final Setting<Boolean> autoFarm = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-farm")
            .description("Automatically start farming when you open an enchanting table with a target set.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> maxRerolls = sgGeneral.add(new IntSetting.Builder()
            .name("max-rerolls")
            .description("Maximum rerolls per run before giving up.")
            .defaultValue(300)
            .min(1)
            .sliderRange(10, 2000)
            .build());

    private final Setting<Integer> actionDelayTicks = sgGeneral.add(new IntSetting.Builder()
            .name("action-delay-ticks")
            .description("Ticks between automatic inventory clicks.")
            .defaultValue(2)
            .min(1)
            .sliderRange(1, 10)
            .build());

    private final Setting<Boolean> autoReport = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-report")
            .description("Print the exact offers of all three rows whenever they change and you are not farming.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> allowReroll = sgGeneral.add(new BoolSetting.Builder()
            .name("reroll-books")
            .description("Burn rolls on plain books when the target is not offered at any shelf count.")
            .defaultValue(false)
            .build());

    public enum ScanPhase { IDLE, SCANNING, LOCKED }
    public enum ScanMode { MASKED, FULL }

    private enum Step { IDLE, EVALUATE, LAPIS_TAKE, LAPIS_PUT, CLEAR_SLOT0, TAKE_SRC, PUT_ONE, RETURN_SRC,
            CLICK_ROW, AWAIT_RESULT, VERIFY_WIN, TIDY }

    private static final int FILTER_PER_TICK = 1 << 16;
    private static final int STABLE_TICKS = 6;
    private static final long FULL_TOTAL = 1L << 32;
    private static final long FULL_CHUNK = 1L << 20;

    private static EnchCracker instance;

    private ScanPhase scanPhase = ScanPhase.IDLE;
    private ScanMode scanMode = ScanMode.MASKED;
    private final HashSet<Integer> possibleSeeds = new HashSet<>(1 << 20);
    private long trueSeed = -1;
    private int power = -1;
    private long lastMasked = Long.MIN_VALUE;
    private BlockPos tablePos;

    private boolean distrustSync = false;
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

    private boolean farmActive = false;
    private boolean autoFarmBlocked = false;
    private boolean runTargetItemResolved = false;
    private String activeEnchant = "";
    private int activeLevel = 0;
    private String activeItemSpec = "";
    private int placeSrc = -1;
    private int pendingRow = -1;
    private boolean clickedWin = false;
    private int maskedAtClick = Integer.MIN_VALUE;
    private List<EnchantmentInstance> predictedWin = null;
    private Step step = Step.IDLE;
    private Step queuedAfter = Step.EVALUATE;
    private int stepTicks = 0;
    private int clock = 0;
    private int statusClock = 0;
    private boolean tidyPending = false;
    private int rerollCount = 0;

    private final int[] obsCosts = {-1, -1, -1};
    private final int[] obsClue = {-1, -1, -1};
    private final int[] obsLevel = {-1, -1, -1};
    private String obsItemKey = "";
    private int stableTicks = 0;
    private int lastScanSize = -1;
    private int stallTicks = 0;
    private boolean shelfHintGiven = false;

    private int lastSeedSnapshot = Integer.MIN_VALUE;
    private String lastItemSnapshot = "";
    private int lastCost0 = -1;
    private int lastCost1 = -1;
    private int lastCost2 = -1;

    public EnchCracker() {
        super(Orbiter.CATEGORY, "enchantment-cracker",
                "Cracks the enchanting seed from the table clues and lands your enchantment in one click.");
        instance = this;
    }

    @Override
    public void onActivate() {
        resetCrack();
        resetSnapshot();
        hardStop();
        autoFarmBlocked = false;
    }

    @Override
    public void onDeactivate() {
        resetCrack();
        hardStop();
    }

    public static boolean isFarming() {
        return instance != null && instance.farmActive;
    }

    public static void onTableUsed(BlockPos pos) {
        if (instance != null) instance.tablePos = pos.immutable();
    }

    public void startFarm(String enchantment, int level, String itemSpec) {
        activeEnchant = enchantment.toLowerCase();
        activeLevel = Math.max(0, level);
        activeItemSpec = itemSpec == null ? "" : itemSpec.toLowerCase();
        runTargetItemResolved = !activeItemSpec.isEmpty();
        farmActive = true;
        autoFarmBlocked = false;
        step = Step.EVALUATE;
        stepTicks = 0;
        rerollCount = 0;
        pendingRow = -1;
        clickedWin = false;
        predictedWin = null;
        resetSnapshot();
        info("Goal set: " + activeEnchant + (activeLevel > 0 ? " " + activeLevel : "")
                + " on " + displayName(activeItemSpec) + ". Step 1: cracking the hidden seed from this table.");
    }

    public void stopFarm(String reason) {
        if (farmActive && reason != null) error(reason);
        farmActive = false;
        tidyPending = true;
    }

    private void hardStop() {
        farmActive = false;
        step = Step.IDLE;
        placeSrc = -1;
        pendingRow = -1;
        predictedWin = null;
        tidyPending = false;
    }

    private void blockFarm(String reason) {
        error(reason);
        autoFarmBlocked = true;
        stopFarm(null);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (statusClock > 0) statusClock--;

        boolean atTable = mc.player.containerMenu instanceof EnchantmentMenu;

        if (!farmActive) {
            if (atTable && autoFarm.get() && !autoFarmBlocked
                    && !targetEnchantment.get().isEmpty() && !targetItem.get().isEmpty()) {
                startFarm(targetEnchantment.get(), targetLevel.get(), targetItem.get());
            } else {
                watcherTick(atTable);
                return;
            }
        }

        if (!atTable) {
            if (++clock >= 150) {
                clock = 0;
                info("Waiting: open an enchanting table so I can work.");
            }
            return;
        }

        EnchantmentMenu menu = (EnchantmentMenu) mc.player.containerMenu;

        if (tidyPending) {
            runTidy(menu);
            return;
        }

        updateCrack(menu);

        boolean clueSwap = step == Step.TAKE_SRC || step == Step.PUT_ONE || step == Step.RETURN_SRC;

        if (scanPhase != ScanPhase.LOCKED) {
            if (clueSwap) {
                if (++clock < actionDelayTicks.get()) return;
                clock = 0;
                drive(menu);
                return;
            }
            if (++clock >= 150) {
                clock = 0;
                info(scanStatusLine());
            }
            return;
        }

        if (++clock < actionDelayTicks.get()) return;
        clock = 0;

        drive(menu);
    }

    private String scanStatusLine() {
        if (scanMode == ScanMode.FULL && scanPhase == ScanPhase.SCANNING) {
            long done = Math.min(fullCursor.get(), FULL_TOTAL);
            return "Server sent a fake seed, searching all 4 billion instead (" + (done * 100 / FULL_TOTAL) + " pct done).";
        }
        return possibleSeeds.isEmpty()
                ? "Reading the table... put any enchantable item inside and let it settle."
                : "Cracking seed: " + possibleSeeds.size() + " possible seeds remaining.";
    }

    private void runTidy(EnchantmentMenu menu) {
        if (TableOps.carrying(menu)) {
            int spot = TableOps.parkTarget(menu);
            if (spot != -1) TableOps.pickupAll(menu, spot);
            return;
        }
        if (!TableOps.slot0(menu).isEmpty()) {
            TableOps.shiftMove(menu, 0);
            return;
        }
        tidyPending = false;
    }

    private void watcherTick(boolean atTable) {
        if (!atTable || !(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
            resetSnapshot();
            return;
        }

        updateCrack(menu);

        ItemStack item = TableOps.slot0(menu);
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

        Offer[] offers = predictOffers(item);
        if (offers == null || !autoReport.get()) return;

        info("Predicted offers right now (seed " + String.format("%08X", trueSeed) + ", "
                + power + " shelves, lapis in table: " + menu.getGoldCount() + "). Put your item in and click the matching row:");
        for (int r = 0; r < offers.length; r++) {
            info("Row " + (r + 1) + (r == 0 ? " (top)" : r == 1 ? " (middle)" : " (bottom)") + " - cost "
                    + offers[r].cost() + ": " + describeList(offers[r].enchantments()));
        }
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
            ItemStack item = TableOps.slot0(menu);
            String itemKey = keyOf(item);
            boolean changed = masked != lastMasked || !observationMatches(menu, itemKey);
            lastMasked = masked;
            if (changed) {
                snapshotObservation(menu, itemKey);
                return;
            }
            if (++stableTicks == STABLE_TICKS && !item.isEmpty() && item.isEnchantable()
                    && !verifyCurrent(menu, TableOps.slot0(menu))) {
                resetCrack();
            }
            return;
        }

        ItemStack item = TableOps.slot0(menu);
        String itemKey = keyOf(item);
        if (!item.isEmpty() && !item.isEnchantable()) return;

        boolean obsChanged = masked != lastMasked || !observationMatches(menu, itemKey);
        if (obsChanged) {
            if (masked != lastMasked) {
                if (masked != 0) zeroStreak = 0;
                if (scanPhase == ScanPhase.SCANNING) restartScan();
            }
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
                    warning("No table detected nearby: right-click your enchanting table once so I can measure its bookshelves.");
                    statusClock = 100;
                }
                return;
            }
        }
        power = countBookshelves(tablePos);

        if (scanPhase == ScanPhase.IDLE) {
            if (menu.costs[0] <= 0 && menu.costs[1] <= 0 && menu.costs[2] <= 0) return;
            if (!isEnchantableSpec(activeItemSpec)) {
                blockFarm("target item " + displayName(activeItemSpec)
                        + " cant be enchanted. hold your real item and run .encc get again");
                return;
            }
            if (masked == 0) {
                zeroStreak++;
                if (zeroStreak >= 2) distrustSync = true;
            } else {
                zeroStreak = 0;
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
            int fixedBits = (int) masked & 0xFFF0;
            for (int high = 0; high < 65536; high++) {
                int base = (high << 16) | fixedBits;
                for (int low = 0; low < 16; low++) possibleSeeds.add(base | low);
            }
            scanPhase = ScanPhase.SCANNING;
            progressClock = 0;
            stallTicks = 0;
            lastScanSize = -1;
            info("Cracking seed: " + possibleSeeds.size() + " candidates (table power = " + power
                    + " shelves). Usually takes a few seconds.");
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
        scan:
        while (iterator.hasNext() && processed < FILTER_PER_TICK) {
            processed++;
            int candidate = iterator.next();
            rand.setSeed(candidate);
            if (!candidateMatches(registry, idMap, rand, candidate, item,
                    menu.costs, menu.enchantClue, menu.levelClue)) {
                iterator.remove();
            }
        }
    }

    private boolean candidateMatches(net.minecraft.core.Registry<Enchantment> registry,
            net.minecraft.core.IdMap<Holder<Enchantment>> idMap, RandomSource rand, int candidate, ItemStack item,
            int[] costs, int[] clues, int[] levels) {
        rand.setSeed(candidate);

        for (int row = 0; row < 3; row++) {
            int cost = EnchantmentHelper.getEnchantmentCost(rand, row, power, item);
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
        int[] c = snapCosts;
        int[] cl = snapClue;
        int[] lv = snapLevel;

        fullCursor.set(0);
        fullFound.clear();
        fullCancelled = false;
        int cores = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
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
                            if (candidateMatches(registry, idMap, rand, candidate, snapRef, c, cl, lv)) {
                                fullFound.add(candidate);
                            }
                        }
                        LockSupport.parkNanos(400_000L);
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
        info("synced seed looks fake, searching the whole space on " + cores + " threads (game may lag a bit)");
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
        if (progressClock % 100 == 0) {
            long done = Math.min(fullCursor.get(), FULL_TOTAL);
            info("Server sent a fake seed - searching all 4 billion instead (" + (done * 100 / FULL_TOTAL) + " pct done).");
        }
    }

    private void stopFullScan() {
        fullCancelled = true;
        fullThreads.clear();
    }

    private void evaluateScanResult() {
        if (possibleSeeds.size() == 1) {
            trueSeed = possibleSeeds.iterator().next();
            scanPhase = ScanPhase.LOCKED;
            noteChain((int) trueSeed);
            info("Seed found: " + String.format("%08X", trueSeed)
                    + ". Predictions are now exact - do NOT click the table rows yourself, I place items and click when your enchant shows up.");
        } else if (possibleSeeds.isEmpty()) {
            if (scanMode == ScanMode.MASKED && !distrustSync) {
                distrustSync = true;
                resetCrack();
                return;
            }
            resetCrack();
            if (statusClock <= 0) {
                warning("Crack failed: the table data changed mid-scan. Restarting with a fresh reading.");
                statusClock = 100;
            }
        } else {
            int size = possibleSeeds.size();
            if (size != lastScanSize) {
                lastScanSize = size;
                stallTicks = 0;
                shelfHintGiven = false;
            }
            progressClock++;
            if (progressClock % 100 == 0) info("Cracking seed: " + size + " possible seeds remaining.");
            if (scanMode == ScanMode.MASKED && ++stallTicks >= 80) attemptObservationSwap();
        }
    }

    private void attemptObservationSwap() {
        if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) return;
        if (TableOps.carrying(menu) || !TableOps.slot0(menu).isEmpty() || step != Step.EVALUATE) return;
        if (!farmActive) return;

        String currentKey = obsItemKey;
        int pick = -1;
        for (int pass = 0; pass < 2 && pick == -1; pass++) {
            for (int i = 3; i < menu.slots.size(); i++) {
                if (!TableOps.isPlayerSlot(menu, i)) continue;
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty() || !s.isEnchantable()) continue;
                boolean different = !keyOf(s).equals(currentKey);
                if (different && (pass == 1 || isPlainBook(s))) {
                    pick = i;
                    break;
                }
            }
        }

        if (pick == -1) {
            if (!shelfHintGiven) {
                warning("Need more clues to finish the crack: put a DIFFERENT enchantable item in the table once (a plain book works), or add/remove one bookshelf.");
                shelfHintGiven = true;
            }
            stallTicks = 0;
            return;
        }

        placeSrc = pick;
        queuedAfter = Step.EVALUATE;
        step = Step.TAKE_SRC;
        stepTicks = 0;
    }

    private boolean verifyCurrent(EnchantmentMenu menu, ItemStack item) {
        if (item.isEmpty() || !item.isEnchantable()) return true;
        RandomSource rand = RandomSource.create();
        rand.setSeed((int) trueSeed);
        for (int slot = 0; slot < 3; slot++) {
            int cost = EnchantmentHelper.getEnchantmentCost(rand, slot, power, item);
            if (cost < slot + 1) cost = 0;
            if (cost != menu.costs[slot]) return false;
        }
        return true;
    }

    protected void resetCrack() {
        stopFullScan();
        possibleSeeds.clear();
        scanPhase = ScanPhase.IDLE;
        scanMode = ScanMode.MASKED;
        trueSeed = -1;
        stableTicks = 0;
        lastScanSize = -1;
        stallTicks = 0;
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
        return tablePos != null && mc.level != null
                && mc.level.getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE);
    }

    private int countBookshelves(BlockPos pos) {
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

    private void drive(EnchantmentMenu menu) {
        if (step != Step.IDLE && ++stepTicks > 120) {
            warning("Got stuck mid-action, cleaning up and retrying.");
            stepTicks = 0;
            step = Step.TIDY;
            queuedAfter = Step.EVALUATE;
        }

        switch (step) {
            case EVALUATE -> doEvaluate(menu);
            case LAPIS_TAKE -> { TableOps.pickupAll(menu, placeSrc); go(Step.LAPIS_PUT); }
            case LAPIS_PUT -> { TableOps.pickupAll(menu, 1); go(Step.EVALUATE); }
            case CLEAR_SLOT0 -> { TableOps.shiftMove(menu, 0); go(queuedAfter); }
            case TAKE_SRC -> {
                if (placeSrc == -1 || menu.getSlot(placeSrc).getItem().isEmpty()) { go(Step.EVALUATE); return; }
                TableOps.pickupAll(menu, placeSrc);
                go(Step.PUT_ONE);
            }
            case PUT_ONE -> { TableOps.depositOne(menu, 0); go(Step.RETURN_SRC); }
            case RETURN_SRC -> {
                if (TableOps.carrying(menu) && placeSrc >= 0) TableOps.pickupAll(menu, placeSrc);
                go(queuedAfter);
            }
            case CLICK_ROW -> doClickRow(menu);
            case AWAIT_RESULT -> doAwaitResult(menu);
            case VERIFY_WIN -> doVerifyWin(menu);
            case TIDY -> { runTidy(menu); if (!tidyPending) go(farmActive ? Step.EVALUATE : Step.IDLE); }
            default -> go(farmActive ? Step.EVALUATE : Step.IDLE);
        }
    }

    private void go(Step next) {
        step = next;
        stepTicks = 0;
    }

    private void doEvaluate(EnchantmentMenu menu) {
        if (!runTargetItemResolved) {
            String spec = activeItemSpec.isEmpty() ? guessHeldItemSpec() : activeItemSpec;
            if (spec.isEmpty()) {
                blockFarm("Blocked: hold the item you want enchanted, or run .encc get <enchant> [level] [item]");
                return;
            }
            activeItemSpec = spec;
            runTargetItemResolved = true;
        }

        if (TableOps.gold(menu) <= 0) {
            int lapis = findPlayerSlot(menu, EnchCracker::isLapis);
            if (lapis == -1) {
                waitForResources("Blocked: no lapis lazuli in your inventory. Get some and keep it on you - I load it into the table myself.");
                return;
            }
            placeSrc = lapis;
            go(Step.LAPIS_TAKE);
            return;
        }

        ItemStack simStack = resolveSimStack(menu);
        Offer[] offers = predictOffers(simStack);
        if (offers == null) return;

        ItemStack slot0 = TableOps.slot0(menu);

        for (int i = 0; i < offers.length; i++) {
            if (listMatchesTarget(offers[i].enchantments()) && affordable(offers[i].cost(), menu)) {
                pendingRow = i;
                clickedWin = true;
                predictedWin = List.copyOf(offers[i].enchantments());
                if (matchesSpec(slot0, activeItemSpec)) {
                    go(Step.CLICK_ROW);
                } else if (slot0.isEmpty()) {
                    startPlace(menu, activeItemSpec, Step.CLICK_ROW);
                } else {
                    queuedAfter = Step.EVALUATE;
                    go(Step.CLEAR_SLOT0);
                }
                return;
            }
        }

        if (!allowReroll.get()) {
            int[] plan = planFor(menu, simStack);
            if (plan == null) {
                blockFarm(activeEnchant + (activeLevel > 0 ? " " + activeLevel : "")
                        + " never appears at any shelf count with this seed. Options: enable the reroll-books setting, change target, or enchant something (the seed changes each time).");
                return;
            }
            int want = plan[0];
            int cost = plan[2];
            if (want == power) {
                waitForResources("Your enchant IS offered right now (row " + (plan[1] + 1)
                        + "). I just need " + cost + " XP levels and " + (plan[1] + 1) + " lapis to click it.");
            } else {
                waitForResources(want < power
                        ? "Your enchant only appears with exactly " + want + " bookshelves (you have " + power
                                + "). Break shelves until " + want + " remain and I click automatically."
                        : "Your enchant only appears with " + want + " bookshelves (you have " + power
                                + "). Add shelves up to " + want + " and I click automatically.");
            }
            return;
        }

        if (rerollCount >= maxRerolls.get()) {
            blockFarm("Giving up: " + rerollCount + " rolls burned and " + activeEnchant + " never appeared.");
            return;
        }

        if (isPlainBook(slot0)) {
            int row = cheapestAffordableRow(menu, offers[0], offers[1], offers[2]);
            if (row == -1) {
                waitForResources("Waiting: every table row costs more XP/lapis than you have. Gain levels or add lapis and I continue alone.");
                return;
            }
            pendingRow = row;
            clickedWin = false;
            go(Step.CLICK_ROW);
            return;
        }

        if (slot0.isEmpty()) {
            startPlace(menu, "book", Step.EVALUATE);
            return;
        }

        queuedAfter = Step.EVALUATE;
        go(Step.CLEAR_SLOT0);
    }

    private void startPlace(EnchantmentMenu menu, String spec, Step after) {
        int src = findPlayerSlot(menu, s -> matchesSpec(s, spec));
        if (src == -1) {
            if (spec.equals(activeItemSpec)) {
                blockFarm("Blocked: your target item disappeared from the inventory.");
            } else {
                waitForResources("Blocked: no plain books in your inventory (I need them to burn bad rolls).");
            }
            return;
        }
        placeSrc = src;
        queuedAfter = after;
        go(Step.TAKE_SRC);
    }

    private void doClickRow(EnchantmentMenu menu) {
        if (pendingRow < 0) {
            go(Step.EVALUATE);
            return;
        }
        maskedAtClick = (int) (menu.getEnchantmentSeed() & 0xFFFFL);
        mc.gameMode.handleInventoryButtonClick(menu.containerId, pendingRow);
        rerollCount++;
        stepTicks = 0;
        step = Step.AWAIT_RESULT;
    }

    private void doAwaitResult(EnchantmentMenu menu) {
        int masked = (int) (menu.getEnchantmentSeed() & 0xFFFFL);
        boolean moved = masked != maskedAtClick;
        boolean enchanted = TableOps.slot0(menu).isEnchanted();

        if (!moved && !enchanted) {
            if (stepTicks > 60) go(clickedWin ? Step.VERIFY_WIN : Step.EVALUATE);
            return;
        }

        if (clickedWin) {
            go(Step.VERIFY_WIN);
        } else {
            info("Burned one roll on a book. New seed: " + String.format("%08X", menu.getEnchantmentSeed())
                    + " - re-cracking so the next prediction stays exact.");
            go(Step.CLEAR_SLOT0);
            queuedAfter = Step.EVALUATE;
        }
    }

    private void doVerifyWin(EnchantmentMenu menu) {
        ItemStack result = TableOps.slot0(menu);
        if (result.isEmpty()) {
            go(Step.EVALUATE);
            return;
        }
        if (!result.isEnchanted()) {
            if (stepTicks > 100) {
                warning("The table never confirmed the enchant in time. Re-cracking.");
                go(Step.EVALUATE);
            }
            return;
        }

        if (predictedWin != null && sameEnchantments(predictedWin, result)) {
            List<EnchantmentInstance> won = predictedWin;
            TableOps.shiftMove(menu, 0);
            farmActive = false;
            autoFarmBlocked = true;
            step = Step.IDLE;
            pendingRow = -1;
            info("SUCCESS: " + displayName(activeItemSpec) + " enchanted with " + describeList(won)
                    + " (" + rerollCount + " clicks used). Item moved back to your inventory.");
        } else {
            warning("MISMATCH: I predicted [" + (predictedWin == null ? "?" : describeList(predictedWin))
                    + "] but the server gave [" + actualList(result)
                    + "]. Something is off - throwing away my model and re-cracking from zero.");
            go(Step.EVALUATE);
        }
    }

    private static String actualList(ItemStack stack) {
        var map = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (map.isEmpty()) return "(nothing)";
        StringBuilder sb = new StringBuilder();
        for (var entry : map.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            String n = entry.getKey().getRegisteredName();
            sb.append(n.startsWith("minecraft:") ? n.substring(10) : n).append(' ').append(entry.getIntValue());
        }
        return sb.toString();
    }

    private boolean sameEnchantments(List<EnchantmentInstance> predicted, ItemStack result) {
        var map = EnchantmentHelper.getEnchantmentsForCrafting(result);
        if (map.size() != predicted.size()) return false;
        for (var entry : map.entrySet()) {
            boolean found = false;
            for (EnchantmentInstance inst : predicted) {
                if (inst.enchantment() == entry.getKey() && inst.level() == entry.getIntValue()) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private int cheapestAffordableRow(EnchantmentMenu menu, Offer... offers) {
        int best = -1;
        for (int row = 0; row < 3; row++) {
            int cost = offers.length == 3 ? offers[row].cost() : menu.costs[row];
            if (cost <= 0 || !affordable(cost, menu)) continue;
            if (best == -1 || cost < (offers.length == 3 ? offers[best].cost() : menu.costs[best])) best = row;
        }
        return best;
    }

    private boolean affordable(int cost, EnchantmentMenu menu) {
        return cost > 0 && cost <= menu.getGoldCount() && cost <= mc.player.experienceLevel;
    }

    private int findPlayerSlot(EnchantmentMenu menu, java.util.function.Predicate<ItemStack> pred) {
        for (int i = 3; i < menu.slots.size(); i++) {
            if (!TableOps.isPlayerSlot(menu, i)) continue;
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty() && pred.test(s)) return i;
        }
        return -1;
    }

    private ItemStack resolveSimStack(EnchantmentMenu menu) {
        if (matchesSpec(TableOps.slot0(menu), activeItemSpec)) return TableOps.slot0(menu);
        int slot = findPlayerSlot(menu, s -> matchesSpec(s, activeItemSpec));
        return slot == -1 ? null : menu.getSlot(slot).getItem();
    }

    private String guessHeldItemSpec() {
        if (mc.player == null) return "";
        return pathOf(mc.player.getMainHandItem());
    }

    private static boolean isPlainBook(ItemStack stack) {
        return pathOf(stack).equals("book") && !stack.isEnchanted();
    }

    private static boolean isLapis(ItemStack stack) {
        return pathOf(stack).equals("lapis_lazuli");
    }

    private static String pathOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.getPath();
    }

    private static String keyOf(ItemStack stack) {
        if (stack.isEmpty()) return "";
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static boolean matchesSpec(ItemStack stack, String spec) {
        if (stack.isEmpty() || spec.isEmpty()) return false;
        String path = pathOf(stack);
        return path.equalsIgnoreCase(spec.startsWith("minecraft:") ? spec.substring(10) : spec);
    }

    private static boolean isEnchantableSpec(String spec) {
        if (spec == null || spec.isEmpty()) return false;
        try {
            var id = net.minecraft.resources.Identifier.withDefaultNamespace(
                    spec.startsWith("minecraft:") ? spec.substring(10) : spec);
            var item = BuiltInRegistries.ITEM.getOptional(id);
            return item.isPresent() && new ItemStack(item.get()).isEnchantable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String displayName(ItemStack stack) {
        String path = pathOf(stack);
        return path.isEmpty() ? "empty" : path;
    }

    private static String displayName(String idOrSpec) {
        if (idOrSpec == null || idOrSpec.isEmpty()) return "empty";
        int slash = idOrSpec.indexOf(':');
        return slash == -1 ? idOrSpec : idOrSpec.substring(slash + 1);
    }

    private void resetSnapshot() {
        lastSeedSnapshot = Integer.MIN_VALUE;
        lastItemSnapshot = "";
        lastCost0 = -1;
        lastCost1 = -1;
        lastCost2 = -1;
    }

    private void waitForResources(String message) {
        if (statusClock <= 0) {
            warning(message);
            statusClock = 200;
        }
    }

    public Offer[] offersFor(EnchantmentMenu menu, ItemStack stack) {
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

    private boolean listMatchesTarget(List<EnchantmentInstance> list) {
        for (EnchantmentInstance inst : list) {
            if (!nameOf(inst).equalsIgnoreCase(activeEnchant)) continue;
            if (activeLevel > 0 && inst.level() != activeLevel) continue;
            return true;
        }
        return false;
    }

    private int[] planFor(EnchantmentMenu menu, ItemStack stack) {
        if (stack == null || stack.isEmpty() || mc.level == null || scanPhase != ScanPhase.LOCKED) return null;
        try {
            var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            int seed = (int) trueSeed;
            for (int p = 0; p <= 15; p++) {
                RandomSource costsRand = RandomSource.create();
                costsRand.setSeed(seed);
                for (int row = 0; row < 3; row++) {
                    int cost = EnchantmentHelper.getEnchantmentCost(costsRand, row, p, stack);
                    if (cost < row + 1) cost = 0;
                    if (cost <= 0) continue;
                    List<EnchantmentInstance> list =
                            vanillaList(registry, RandomSource.create(), seed, stack, row, cost);
                    if (listMatchesTarget(list)) return new int[]{p, row, cost};
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Integer tryChainPredict(ItemStack item) {
        if (chainState < 0 || mc.level == null) return null;
        try {
            long next = stepChain(chainState);
            int candidate = (int) (next >>> 16);
            var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var idMap = registry.asHolderIdMap();
            if (candidateMatches(registry, idMap, RandomSource.create(), candidate, item,
                    obsCosts, obsClue, obsLevel)) {
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

    @Override
    public String getInfoString() {
        if (farmActive) return "farming r" + rerollCount;
        if (scanPhase == ScanPhase.LOCKED) return String.format("%08X", trueSeed);
        if (scanMode == ScanMode.FULL && scanPhase == ScanPhase.SCANNING) {
            return "full " + (Math.min(fullCursor.get(), FULL_TOTAL) * 100 / FULL_TOTAL) + "pct";
        }
        return scanPhase.name().toLowerCase();
    }

    public record Offer(int cost, List<EnchantmentInstance> enchantments) {}
}
