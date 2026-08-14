package orbiter.modules.render;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ViewBlocks extends Module {
    public enum RenderMode { Full, Corners, Wireframe, Sides }
    public enum BlockPreset { None, Utility, Ores, Dungeons, Caves, All }
    public enum ScanMode { ChunkBased, Incremental }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Block Selection");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgPerf = settings.createGroup("Performance");

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius").description("Search radius.")
        .defaultValue(15).min(1).max(50).sliderRange(1, 50).build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval").description("Ticks between scan passes.")
        .defaultValue(5).min(1).max(40).sliderRange(1, 20).build());

    private final Setting<BlockPreset> preset = sgGeneral.add(new EnumSetting.Builder<BlockPreset>()
        .name("preset").description("Quick-select block types.")
        .defaultValue(BlockPreset.None)
        .onChanged(p -> applyPreset(p)).build());

    private final Setting<Boolean> barriers = sgBlocks.add(new BoolSetting.Builder()
        .name("barriers").description("Show barrier blocks.").defaultValue(true).build());
    private final Setting<SettingColor> barrierColor = sgBlocks.add(new ColorSetting.Builder()
        .name("barrier-color").defaultValue(new SettingColor(255, 0, 0, 200)).visible(barriers::get).build());

    private final Setting<Boolean> lightBlocks = sgBlocks.add(new BoolSetting.Builder()
        .name("light-blocks").description("Show light blocks.").defaultValue(true).build());
    private final Setting<SettingColor> lightColor = sgBlocks.add(new ColorSetting.Builder()
        .name("light-color").defaultValue(new SettingColor(255, 255, 0, 200)).visible(lightBlocks::get).build());

    private final Setting<Boolean> structureBlocks = sgBlocks.add(new BoolSetting.Builder()
        .name("structure-blocks").description("Show structure void/block.").defaultValue(true).build());
    private final Setting<SettingColor> structureColor = sgBlocks.add(new ColorSetting.Builder()
        .name("structure-color").defaultValue(new SettingColor(255, 100, 200, 200)).visible(structureBlocks::get).build());

    private final Setting<Boolean> spawners = sgBlocks.add(new BoolSetting.Builder()
        .name("spawners").description("Show mob spawners.").defaultValue(false).build());
    private final Setting<SettingColor> spawnerColor = sgBlocks.add(new ColorSetting.Builder()
        .name("spawner-color").defaultValue(new SettingColor(0, 255, 100, 200)).visible(spawners::get).build());

    private final Setting<Boolean> commandBlocks = sgBlocks.add(new BoolSetting.Builder()
        .name("command-blocks").description("Show command blocks.").defaultValue(false).build());
    private final Setting<SettingColor> commandBlockColor = sgBlocks.add(new ColorSetting.Builder()
        .name("command-block-color").defaultValue(new SettingColor(210, 130, 50, 200)).visible(commandBlocks::get).build());

    private final Setting<Boolean> endPortals = sgBlocks.add(new BoolSetting.Builder()
        .name("end-portals").description("Show end portal/frame.").defaultValue(false).build());
    private final Setting<SettingColor> endPortalColor = sgBlocks.add(new ColorSetting.Builder()
        .name("end-portal-color").defaultValue(new SettingColor(150, 0, 200, 200)).visible(endPortals::get).build());

    private final Setting<Boolean> ores = sgBlocks.add(new BoolSetting.Builder()
        .name("ores").description("Show valuable ores.").defaultValue(false).build());
    private final Setting<SettingColor> oreColor = sgBlocks.add(new ColorSetting.Builder()
        .name("ore-color").defaultValue(new SettingColor(0, 255, 255, 200)).visible(ores::get).build());

    private final Setting<Boolean> useCustomBlockList = sgBlocks.add(new BoolSetting.Builder()
        .name("custom-block-list").description("Use custom block list.").defaultValue(false).build());
    @SuppressWarnings("unchecked")
    private final Setting<List<Block>> customBlocks = sgBlocks.add((Setting<List<Block>>) (Setting<?>) new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
        .name("custom-blocks").description("Custom blocks to highlight.")
        .visible(useCustomBlockList::get).build());
    private final Setting<SettingColor> customBlockColor = sgBlocks.add(new ColorSetting.Builder()
        .name("custom-block-color").defaultValue(new SettingColor(0, 200, 255, 200)).visible(useCustomBlockList::get).build());

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode").defaultValue(RenderMode.Corners).build());
    private final Setting<Integer> fillOpacity = sgRender.add(new IntSetting.Builder()
        .name("fill-opacity").defaultValue(20).min(0).max(255).sliderRange(0, 255).build());
    private final Setting<Integer> lineOpacity = sgRender.add(new IntSetting.Builder()
        .name("line-opacity").defaultValue(200).min(0).max(255).sliderRange(0, 255).build());
    private final Setting<Double> cornerLength = sgRender.add(new DoubleSetting.Builder()
        .name("corner-length").defaultValue(0.25).min(0.05).max(0.5).sliderRange(0.05, 0.5)
        .visible(() -> renderMode.get() == RenderMode.Corners).build());

    private final Setting<Integer> blocksPerTick = sgPerf.add(new IntSetting.Builder()
        .name("blocks-per-tick").description("Max blocks inspected per scan tick.")
        .defaultValue(8192).min(512).max(65536).sliderRange(512, 16384).build());

    private final Setting<Integer> maxBlocks = sgPerf.add(new IntSetting.Builder()
        .name("max-blocks").description("Maximum tracked blocks.")
        .defaultValue(2000).min(50).max(10000).sliderRange(50, 5000).build());

    private final Setting<Integer> maxRenderedPerFrame = sgPerf.add(new IntSetting.Builder()
        .name("max-rendered-per-frame").description("Max blocks rendered per frame.")
        .defaultValue(2000).min(50).max(10000).sliderRange(50, 5000).build());

    private final Setting<Double> distanceCull = sgPerf.add(new DoubleSetting.Builder()
        .name("distance-cull").description("Fraction of render distance for culling.")
        .defaultValue(0.8).min(0.1).max(1.0).sliderRange(0.1, 1.0).build());

    private final Setting<Boolean> showStats = sgPerf.add(new BoolSetting.Builder()
        .name("show-stats").description("Show scan stats in module info.")
        .defaultValue(true).build());

    private static class FoundBlock {
        BlockPos pos;
        Color fill, line;
        double distSq;
        long tick;
        FoundBlock(BlockPos p, Color f, Color l, double d, long t) {
            pos = p; fill = f; line = l; distSq = d; tick = t;
        }
    }

    private final LinkedBlockingQueue<FoundBlock> pool = new LinkedBlockingQueue<>();
    private final List<FoundBlock> activeBlocks = new ArrayList<>();
    private final Map<Long, FoundBlock> posMap = new HashMap<>();
    private final Set<Long> scannedChunks = new HashSet<>();

    private BlockPos scanCenter = null;
    private int scanChunkX = 0, scanChunkZ = 0;
    private int scanMaxChunkX = 0, scanMaxChunkZ = 0;
    private boolean scanActive = false;

    private int lastScanChunks = 0;
    private int lastScanMatched = 0;
    private long lastScanMs = 0;
    private int lastRendered = 0;

    private static final Set<Block> ORE_BLOCKS = Set.of(
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.ANCIENT_DEBRIS
    );

    public ViewBlocks() {
        super(Orbiter.CATEGORY, "view-blocks", "ESP for invisible and custom blocks with chunk-based scanning.");
    }

    @Override public void onActivate() { reset(); }
    @Override public void onDeactivate() { reset(); }

    private void reset() {
        activeBlocks.clear();
        posMap.clear();
        scannedChunks.clear();
        scanCenter = null;
        scanActive = false;
        lastScanChunks = 0;
        lastScanMatched = 0;
        lastScanMs = 0;
        lastRendered = 0;
    }

    private void applyPreset(BlockPreset p) {
        if (!isActive()) return;
        switch (p) {
            case Utility -> { barriers.set(true); lightBlocks.set(true); structureBlocks.set(true); spawners.set(true); commandBlocks.set(true); endPortals.set(true); ores.set(false); useCustomBlockList.set(false); }
            case Ores -> { barriers.set(false); lightBlocks.set(false); structureBlocks.set(false); spawners.set(false); commandBlocks.set(false); endPortals.set(false); ores.set(true); useCustomBlockList.set(false); }
            case Dungeons -> { barriers.set(false); lightBlocks.set(false); structureBlocks.set(false); spawners.set(true); commandBlocks.set(false); endPortals.set(false); ores.set(false); useCustomBlockList.set(false); }
            case Caves -> { barriers.set(false); lightBlocks.set(false); structureBlocks.set(false); spawners.set(false); commandBlocks.set(false); endPortals.set(false); ores.set(false); useCustomBlockList.set(false); }
            case All -> { barriers.set(true); lightBlocks.set(true); structureBlocks.set(true); spawners.set(true); commandBlocks.set(true); endPortals.set(true); ores.set(true); useCustomBlockList.set(false); }
            default -> {}
        }
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        scanTickCounter++;
        if (scanTickCounter < scanInterval.get()) return;
        scanTickCounter = 0;

        long start = System.currentTimeMillis();
        int budget = blocksPerTick.get();
        int scanned = 0;
        int matched = 0;
        int chunkCount = 0;

        int r = radius.get();
        int chunkRadius = (r >> 4) + 1;
        ChunkPos center = new ChunkPos(mc.player.getBlockPos());
        int playerX = mc.player.getBlockPos().getX();
        int playerY = mc.player.getBlockPos().getY();
        int playerZ = mc.player.getBlockPos().getZ();
        int rSq = r * r;
        int fOp = fillOpacity.get();
        int lOp = lineOpacity.get();

        Iterator<FoundBlock> it = activeBlocks.iterator();
        while (it.hasNext()) {
            FoundBlock fb = it.next();
            int dx = fb.pos.getX() - playerX, dy = fb.pos.getY() - playerY, dz = fb.pos.getZ() - playerZ;
            if (dx * dx + dy * dy + dz * dz > rSq) {
                posMap.remove(packPos(fb.pos));
                it.remove();
                pool.offer(fb);
            }
        }

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = center.x + cx;
                int chunkZ = center.z + cz;
                long chunkLong = ChunkPos.toLong(chunkX, chunkZ);

                if (!mc.world.isChunkLoaded(chunkX, chunkZ)) continue;
                if (scannedChunks.contains(chunkLong)) {

                    if (globalScanTick % 20 != 0) continue;
                }
                scannedChunks.add(chunkLong);
                chunkCount++;

                Chunk chunk = mc.world.getChunk(chunkX, chunkZ);
                ChunkSection[] sections = chunk.getSectionArray();
                int baseY = chunk.getBottomY();

                for (int si = 0; si < sections.length; si++) {
                    ChunkSection section = sections[si];
                    if (section == null || section.isEmpty()) continue;

                    int sectionY = baseY + (si << 4);

                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                if (scanned >= budget) break;

                                int wx = (chunkX << 4) + lx;
                                int wy = sectionY + ly;
                                int wz = (chunkZ << 4) + lz;

                                int dx = wx - playerX, dy = wy - playerY, dz = wz - playerZ;
                                if (dx * dx + dy * dy + dz * dz > rSq) continue;

                                Block block = section.getBlockState(lx, ly, lz).getBlock();
                                Color[] colors = getBlockColors(block, fOp, lOp);
                                if (colors != null) {
                                    long packed = packCoords(wx, wy, wz);
                                    FoundBlock existing = posMap.get(packed);
                                    if (existing != null) {
                                        existing.fill = colors[0];
                                        existing.line = colors[1];
                                        existing.distSq = dx * dx + dy * dy + dz * dz;
                                        existing.tick = globalScanTick;
                                    } else {
                                        BlockPos pos = new BlockPos(wx, wy, wz);
                                        FoundBlock fb = new FoundBlock(pos, colors[0], colors[1], dx * dx + dy * dy + dz * dz, globalScanTick);
                                        if (activeBlocks.size() < maxBlocks.get()) {
                                            activeBlocks.add(fb);
                                            posMap.put(packed, fb);
                                            matched++;
                                        }
                                    }
                                }
                                scanned++;
                            }
                            if (scanned >= budget) break;
                        }
                        if (scanned >= budget) break;
                    }
                    if (scanned >= budget) break;
                }
                if (scanned >= budget) break;
            }
            if (scanned >= budget) break;
        }

        globalScanTick++;
        lastScanChunks = chunkCount;
        lastScanMatched = matched;
        lastScanMs = System.currentTimeMillis() - start;
    }

    private int scanTickCounter = 0;
    private long globalScanTick = 0;

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        double maxDist = getEffectiveRenderDistance();
        double maxDistSq = maxDist * maxDist;
        int budget = maxRenderedPerFrame.get();
        lastRendered = 0;

        activeBlocks.sort((a, b) -> Double.compare(a.distSq, b.distSq));

        for (FoundBlock fb : activeBlocks) {
            if (lastRendered >= budget) break;
            if (fb.distSq > maxDistSq) continue;

            switch (renderMode.get()) {
                case Full -> event.renderer.box(fb.pos, fb.fill, fb.line, ShapeMode.Both, 0);
                case Wireframe -> event.renderer.box(fb.pos, fb.fill, fb.line, ShapeMode.Lines, 0);
                case Sides -> event.renderer.box(fb.pos, fb.fill, fb.line, ShapeMode.Sides, 0);
                case Corners -> renderCorners(event, fb);
            }
            lastRendered++;
        }
    }

    private void renderCorners(Render3DEvent event, FoundBlock fb) {
        double x1 = fb.pos.getX(), y1 = fb.pos.getY(), z1 = fb.pos.getZ();
        double x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1;
        double cl = cornerLength.get();
        Color c = fb.line;

        event.renderer.line(x1, y1, z1, x1 + cl, y1, z1, c);
        event.renderer.line(x1, y1, z1, x1, y1 + cl, z1, c);
        event.renderer.line(x1, y1, z1, x1, y1, z1 + cl, c);

        event.renderer.line(x2, y1, z1, x2 - cl, y1, z1, c);
        event.renderer.line(x2, y1, z1, x2, y1 + cl, z1, c);
        event.renderer.line(x2, y1, z1, x2, y1, z1 + cl, c);

        event.renderer.line(x1, y1, z2, x1 + cl, y1, z2, c);
        event.renderer.line(x1, y1, z2, x1, y1 + cl, z2, c);
        event.renderer.line(x1, y1, z2, x1, y1, z2 - cl, c);

        event.renderer.line(x2, y1, z2, x2 - cl, y1, z2, c);
        event.renderer.line(x2, y1, z2, x2, y1 + cl, z2, c);
        event.renderer.line(x2, y1, z2, x2, y1, z2 - cl, c);

        event.renderer.line(x1, y2, z1, x1 + cl, y2, z1, c);
        event.renderer.line(x1, y2, z1, x1, y2 - cl, z1, c);
        event.renderer.line(x1, y2, z1, x1, y2, z1 + cl, c);

        event.renderer.line(x2, y2, z1, x2 - cl, y2, z1, c);
        event.renderer.line(x2, y2, z1, x2, y2 - cl, z1, c);
        event.renderer.line(x2, y2, z1, x2, y2, z1 + cl, c);

        event.renderer.line(x1, y2, z2, x1 + cl, y2, z2, c);
        event.renderer.line(x1, y2, z2, x1, y2 - cl, z2, c);
        event.renderer.line(x1, y2, z2, x1, y2, z2 - cl, c);

        event.renderer.line(x2, y2, z2, x2 - cl, y2, z2, c);
        event.renderer.line(x2, y2, z2, x2, y2 - cl, z2, c);
        event.renderer.line(x2, y2, z2, x2, y2, z2 - cl, c);
    }

    private double getEffectiveRenderDistance() {
        if (mc.options == null || mc.options.getViewDistance() == null) return 128.0;
        return mc.options.getViewDistance().getValue() * 16.0 * distanceCull.get();
    }

    private Color[] getBlockColors(Block block, int fOp, int lOp) {
        if (barriers.get() && block == Blocks.BARRIER) return colors(barrierColor.get(), fOp, lOp);
        if (lightBlocks.get() && block == Blocks.LIGHT) return colors(lightColor.get(), fOp, lOp);
        if (structureBlocks.get() && (block == Blocks.STRUCTURE_VOID || block == Blocks.STRUCTURE_BLOCK)) return colors(structureColor.get(), fOp, lOp);
        if (spawners.get() && block == Blocks.SPAWNER) return colors(spawnerColor.get(), fOp, lOp);
        if (commandBlocks.get() && (block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK || block == Blocks.REPEATING_COMMAND_BLOCK)) return colors(commandBlockColor.get(), fOp, lOp);
        if (endPortals.get() && (block == Blocks.END_PORTAL || block == Blocks.END_PORTAL_FRAME)) return colors(endPortalColor.get(), fOp, lOp);
        if (ores.get() && ORE_BLOCKS.contains(block)) return colors(oreColor.get(), fOp, lOp);
        if (useCustomBlockList.get() && customBlocks.get() != null && customBlocks.get().contains(block)) return colors(customBlockColor.get(), fOp, lOp);
        return null;
    }

    private Color[] colors(SettingColor c, int fOp, int lOp) {
        return new Color[]{
            new Color(c.r, c.g, c.b, fOp),
            new Color(c.r, c.g, c.b, lOp)
        };
    }

    private static long packCoords(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFFF) << 20) | ((long)(z & 0x3FFFFFF));
    }

    private static long packPos(BlockPos pos) {
        return packCoords(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public String getInfoString() {
        if (!showStats.get()) return null;
        return activeBlocks.size() + " tracked | rendered=" + lastRendered + " | chunks=" + lastScanChunks + (lastScanMs > 0 ? " | " + lastScanMs + "ms" : "");
    }
}
