package orbiter.modules.render;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockSpoof extends Module {

    public enum SpoofPreset {
        None("", ""),
        BedrockStone("minecraft:bedrock=minecraft:stone", "Replace bedrock with stone"),
        ObsidianCrying("minecraft:obsidian=minecraft:crying_obsidian", "Replace obsidian with crying obsidian"),
        DiamondCoal("minecraft:diamond_ore=minecraft:coal_ore;minecraft:deepslate_diamond_ore=minecraft:deepslate_coal_ore", "Hide diamond ores as coal"),
        LavaWater("minecraft:lava=minecraft:water", "Replace lava with water"),
        SpawnerIron("minecraft:spawner=minecraft:iron_block", "Replace spawners with iron blocks"),
        Custom("", "Use custom blockMap string");

        public final String map;
        public final String description;

        SpoofPreset(String map, String description) {
            this.map = map;
            this.description = description;
        }
    }

    public enum OreHighlightMode {
        Off,
        Outline,
        Glow,
        PulseOutline
    }

    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgMap      = settings.createGroup("Block Map");
    private final SettingGroup sgRange    = settings.createGroup("Range");
    private final SettingGroup sgOre      = settings.createGroup("Ore Highlight");
    private final SettingGroup sgRender   = settings.createGroup("Render");

    private final Setting<SpoofPreset> preset = sgGeneral.add(new EnumSetting.Builder<SpoofPreset>()
        .name("preset")
        .description("Common block replacement presets.")
        .defaultValue(SpoofPreset.None)
        .onChanged(p -> onPresetChanged(p))
        .build());

    private final Setting<Boolean> autoRefresh = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-refresh")
        .description("Automatically refresh chunk rendering when map changes.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> refreshDelay = sgGeneral.add(new IntSetting.Builder()
        .name("refresh-delay")
        .description("Ticks to wait after a change before refreshing chunks.")
        .defaultValue(5)
        .min(1).max(40).sliderRange(1, 20)
        .visible(autoRefresh::get)
        .build());

    private final Setting<Boolean> toggleKeyEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-key-enabled")
        .description("Enable a quick toggle keybind for BlockSpoof.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> toggleKeyCode = sgGeneral.add(new IntSetting.Builder()
        .name("toggle-key-code")
        .description("GL key code for the quick toggle key. Default B = 48.")
        .defaultValue(48)
        .min(0).max(350)
        .visible(toggleKeyEnabled::get)
        .build());

    private final Setting<String> blockMap = sgMap.add(new StringSetting.Builder()
        .name("block-map")
        .description("Custom replacement map. Format: minecraft:blockA=minecraft:blockB;...")
        .defaultValue("")
        .visible(() -> preset.get() == SpoofPreset.Custom)
        .onChanged(s -> onBlockMapChanged())
        .build());

    private final Setting<Integer> range = sgRange.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum range for block spoof rendering.")
        .defaultValue(64)
        .min(8).max(256).sliderRange(8, 128)
        .build());

    private final Setting<Boolean> rangeLimitEnabled = sgRange.add(new BoolSetting.Builder()
        .name("range-limit-enabled")
        .description("Only apply spoof within the specified range.")
        .defaultValue(true)
        .build());

    private final Setting<OreHighlightMode> oreHighlight = sgOre.add(new EnumSetting.Builder<OreHighlightMode>()
        .name("ore-highlight")
        .description("Special highlight mode that makes valuable ores stand out visually.")
        .defaultValue(OreHighlightMode.Off)
        .build());

    private final Setting<Boolean> highlightDiamond = sgOre.add(new BoolSetting.Builder()
        .name("highlight-diamond")
        .description("Highlight diamond ores.")
        .defaultValue(true)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> diamondColor = sgOre.add(new ColorSetting.Builder()
        .name("diamond-highlight-color")
        .description("Color for diamond ore highlights.")
        .defaultValue(new SettingColor(0, 255, 255, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightDiamond.get())
        .build());

    private final Setting<Boolean> highlightEmerald = sgOre.add(new BoolSetting.Builder()
        .name("highlight-emerald")
        .description("Highlight emerald ores.")
        .defaultValue(true)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> emeraldColor = sgOre.add(new ColorSetting.Builder()
        .name("emerald-highlight-color")
        .description("Color for emerald ore highlights.")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightEmerald.get())
        .build());

    private final Setting<Boolean> highlightGold = sgOre.add(new BoolSetting.Builder()
        .name("highlight-gold")
        .description("Highlight gold ores.")
        .defaultValue(true)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> goldColor = sgOre.add(new ColorSetting.Builder()
        .name("gold-highlight-color")
        .description("Color for gold ore highlights.")
        .defaultValue(new SettingColor(255, 215, 0, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightGold.get())
        .build());

    private final Setting<Boolean> highlightAncientDebris = sgOre.add(new BoolSetting.Builder()
        .name("highlight-ancient-debris")
        .description("Highlight ancient debris.")
        .defaultValue(true)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> ancientDebrisColor = sgOre.add(new ColorSetting.Builder()
        .name("ancient-debris-highlight-color")
        .description("Color for ancient debris highlights.")
        .defaultValue(new SettingColor(150, 0, 200, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightAncientDebris.get())
        .build());

    private final Setting<Boolean> highlightLapis = sgOre.add(new BoolSetting.Builder()
        .name("highlight-lapis")
        .description("Highlight lapis ores.")
        .defaultValue(false)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> lapisColor = sgOre.add(new ColorSetting.Builder()
        .name("lapis-highlight-color")
        .description("Color for lapis ore highlights.")
        .defaultValue(new SettingColor(0, 50, 255, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightLapis.get())
        .build());

    private final Setting<Boolean> highlightRedstone = sgOre.add(new BoolSetting.Builder()
        .name("highlight-redstone")
        .description("Highlight redstone ores.")
        .defaultValue(false)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> redstoneColor = sgOre.add(new ColorSetting.Builder()
        .name("redstone-highlight-color")
        .description("Color for redstone ore highlights.")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightRedstone.get())
        .build());

    private final Setting<Boolean> highlightIron = sgOre.add(new BoolSetting.Builder()
        .name("highlight-iron")
        .description("Highlight iron ores.")
        .defaultValue(false)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> ironColor = sgOre.add(new ColorSetting.Builder()
        .name("iron-highlight-color")
        .description("Color for iron ore highlights.")
        .defaultValue(new SettingColor(200, 180, 150, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightIron.get())
        .build());

    private final Setting<Boolean> highlightCoal = sgOre.add(new BoolSetting.Builder()
        .name("highlight-coal")
        .description("Highlight coal ores.")
        .defaultValue(false)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> coalColor = sgOre.add(new ColorSetting.Builder()
        .name("coal-highlight-color")
        .description("Color for coal ore highlights.")
        .defaultValue(new SettingColor(60, 60, 60, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightCoal.get())
        .build());

    private final Setting<Boolean> highlightCopper = sgOre.add(new BoolSetting.Builder()
        .name("highlight-copper")
        .description("Highlight copper ores.")
        .defaultValue(false)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> copperColor = sgOre.add(new ColorSetting.Builder()
        .name("copper-highlight-color")
        .description("Color for copper ore highlights.")
        .defaultValue(new SettingColor(180, 100, 50, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightCopper.get())
        .build());

    private final Setting<Boolean> highlightNetherQuartz = sgOre.add(new BoolSetting.Builder()
        .name("highlight-nether-quartz")
        .description("Highlight nether quartz ores.")
        .defaultValue(false)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> netherQuartzColor = sgOre.add(new ColorSetting.Builder()
        .name("nether-quartz-highlight-color")
        .description("Color for nether quartz ore highlights.")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightNetherQuartz.get())
        .build());

    private final Setting<Boolean> highlightNetherGold = sgOre.add(new BoolSetting.Builder()
        .name("highlight-nether-gold")
        .description("Highlight nether gold ores.")
        .defaultValue(true)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<SettingColor> netherGoldColor = sgOre.add(new ColorSetting.Builder()
        .name("nether-gold-highlight-color")
        .description("Color for nether gold ore highlights.")
        .defaultValue(new SettingColor(255, 180, 0, 200))
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off && highlightNetherGold.get())
        .build());

    private final Setting<Double> highlightScale = sgOre.add(new DoubleSetting.Builder()
        .name("highlight-scale")
        .description("Scale of the highlight box relative to block (0.5 = half block).")
        .defaultValue(1.02)
        .min(0.5).max(2.0).sliderRange(0.5, 1.5)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<Integer> highlightOpacity = sgOre.add(new IntSetting.Builder()
        .name("highlight-opacity")
        .description("Opacity of highlight fills (0 = invisible, 255 = solid).")
        .defaultValue(30)
        .min(0).max(255).sliderRange(0, 128)
        .visible(() -> oreHighlight.get() != OreHighlightMode.Off)
        .build());

    private final Setting<Boolean> showOriginalOnHover = sgRender.add(new BoolSetting.Builder()
        .name("show-original-on-hover")
        .description("Show the original block name when hovering over a spoofed block.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> debugPositions = sgRender.add(new BoolSetting.Builder()
        .name("debug-positions")
        .description("Log spoofed block positions to console for debugging.")
        .defaultValue(false)
        .build());

    private final Map<Block, Block> replacementMap = new ConcurrentHashMap<>();

    private final List<HighlightedOre> highlightedOres = new ArrayList<>();

    private boolean needsChunkRefresh = false;
    private int refreshTimer = 0;

    private boolean toggleKeyWasDown = false;

    private float pulsePhase = 0.0f;

    private int oreScanTickCounter = 0;

    private static final int ORE_SCAN_BLOCK_BUDGET = 8192;

    private final Map<String, Block> blockIdCache = new ConcurrentHashMap<>();

    private static class HighlightedOre {
        final BlockPos pos;
        final SettingColor color;
        final Block block;

        HighlightedOre(BlockPos pos, SettingColor color, Block block) {
            this.pos = pos;
            this.color = color;
            this.block = block;
        }
    }

    private static BlockSpoof instance = null;

    public static BlockSpoof getActive() {
        if (instance != null && instance.isActive()) return instance;
        return null;
    }

    public static Block getSpoofedBlock(Block original) {
        BlockSpoof self = getActive();
        if (self == null) return null;
        return self.replacementMap.get(original);
    }

    public static boolean isWithinRange(BlockPos pos) {
        BlockSpoof self = getActive();
        if (self == null) return false;
        if (!self.rangeLimitEnabled.get()) return true;
        if (self.mc.player == null) return true;
        return self.mc.player.getBlockPos().isWithinDistance(pos, self.range.get());
    }

    public static boolean hasActiveReplacements() {
        BlockSpoof self = getActive();
        if (self == null) return false;
        return !self.replacementMap.isEmpty();
    }

    public BlockSpoof() {
        super(Orbiter.CATEGORY, "block-spoof",
            "Replace block textures/models client-side for visual deception.");
    }

    @Override
    public void onActivate() {
        instance = this;
        rebuildMap();
        needsChunkRefresh = true;
        refreshTimer = 0;
        pulsePhase = 0.0f;
        oreScanTickCounter = 0;
        blockIdCache.clear();
    }

    @Override
    public void onDeactivate() {
        instance = null;
        replacementMap.clear();
        highlightedOres.clear();
        needsChunkRefresh = false;
        blockIdCache.clear();
        requestWorldRendererRefresh();
    }

    private void rebuildMap() {
        replacementMap.clear();

        String mapString = "";
        SpoofPreset currentPreset = preset.get();
        if (currentPreset != SpoofPreset.Custom && currentPreset != SpoofPreset.None) {
            mapString = currentPreset.map;
        } else if (currentPreset == SpoofPreset.Custom) {
            mapString = blockMap.get();
        }

        if (mapString == null || mapString.isBlank()) return;

        parseBlockMapString(mapString);
    }

    private void parseBlockMapString(String mapString) {
        String[] entries = mapString.split(";");
        int parsed = 0;
        int failed = 0;

        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            String[] parts = trimmed.split("=");
            if (parts.length != 2) {
                warning("Invalid map entry (missing '='): " + trimmed);
                failed++;
                continue;
            }

            String sourceId = parts[0].trim();
            String targetId = parts[1].trim();

            Block sourceBlock = resolveBlock(sourceId);
            Block targetBlock = resolveBlock(targetId);

            if (sourceBlock == null) {
                warning("Unknown source block: " + sourceId);
                failed++;
                continue;
            }
            if (targetBlock == null) {
                warning("Unknown target block: " + targetId);
                failed++;
                continue;
            }
            if (sourceBlock == targetBlock) {
                warning("Source and target are the same block: " + sourceId);
                failed++;
                continue;
            }

            replacementMap.put(sourceBlock, targetBlock);
            parsed++;
        }

        if (parsed > 0) {
            info("Parsed " + parsed + " block replacement(s)."
                + (failed > 0 ? " " + failed + " entry/entries failed." : ""));
        } else if (failed > 0) {
            warning("All " + failed + " map entries failed to parse.");
        }
    }

    private Block resolveBlock(String id) {
        if (id == null || id.isEmpty()) return null;

        String fullId = id.contains(":") ? id : "minecraft:" + id;

        try {
            Identifier identifier = Identifier.of(fullId);
            Block block = Registries.BLOCK.get(identifier);
            if (block != Blocks.AIR || fullId.equals("minecraft:air")) {
                return block;
            }
        } catch (Exception ignored) {

        }

        String lowerId = fullId.toLowerCase();
        Block cached = blockIdCache.get(lowerId);
        if (cached != null) {
            return cached == Blocks.AIR ? null : cached;
        }

        for (Block b : Registries.BLOCK) {
            Identifier bId = Registries.BLOCK.getId(b);
            if (bId != null && bId.toString().equalsIgnoreCase(lowerId)) {
                blockIdCache.put(lowerId, b);
                return b;
            }
        }

        blockIdCache.put(lowerId, Blocks.AIR);
        return null;
    }

    private void onPresetChanged(SpoofPreset p) {
        if (!isActive()) return;

        if (p == SpoofPreset.Custom) {
            rebuildMap();
        } else if (p == SpoofPreset.None) {
            replacementMap.clear();
        } else {

            replacementMap.clear();
            parseBlockMapString(p.map);
        }

        scheduleChunkRefresh();
    }

    private void onBlockMapChanged() {
        if (!isActive()) return;
        if (preset.get() != SpoofPreset.Custom) return;

        rebuildMap();
        scheduleChunkRefresh();
    }

    private void scheduleChunkRefresh() {

    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        handleToggleKey();

        if (oreHighlight.get() == OreHighlightMode.PulseOutline) {
            pulsePhase += 0.05f;
            if (pulsePhase > Math.PI * 2.0) pulsePhase -= (float) (Math.PI * 2.0);
        }

        if (oreHighlight.get() != OreHighlightMode.Off) {
            oreScanTickCounter++;
            if (oreScanTickCounter >= 40) {
                oreScanTickCounter = 0;
                scanOreHighlights();
            }
        } else {
            highlightedOres.clear();
        }
    }

    private void handleToggleKey() {
        if (!toggleKeyEnabled.get()) return;

        boolean isDown = false;
        try {
            int keyCode = toggleKeyCode.get();
            isDown = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == 1;
        } catch (Exception ignored) {
            return;
        }

        if (isDown && !toggleKeyWasDown) {
            toggle();
            return;
        }

        toggleKeyWasDown = isDown;
    }

    private void scanOreHighlights() {
        highlightedOres.clear();

        if (mc.player == null || mc.world == null) return;

        int r = range.get();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        int budget = ORE_SCAN_BLOCK_BUDGET;

        for (int x = -r; x <= r; x += 2) {
            for (int y = -r; y <= r; y += 2) {
                for (int z = -r; z <= r; z += 2) {
                    if (budget-- <= 0) return;
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);

                    if (playerPos.getSquaredDistance(mutable) > (long) r * r) continue;

                    Block block = mc.world.getBlockState(mutable).getBlock();
                    SettingColor color = getOreHighlightColor(block);
                    if (color != null) {
                        highlightedOres.add(new HighlightedOre(
                            mutable.toImmutable(), color, block));
                    }
                }
            }
        }
    }

    private SettingColor getOreHighlightColor(Block block) {
        if (highlightDiamond.get()
            && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return diamondColor.get();
        }
        if (highlightEmerald.get()
            && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) {
            return emeraldColor.get();
        }
        if (highlightGold.get()
            && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.NETHER_GOLD_ORE)) {
            return goldColor.get();
        }
        if (highlightAncientDebris.get() && block == Blocks.ANCIENT_DEBRIS) {
            return ancientDebrisColor.get();
        }
        if (highlightLapis.get()
            && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)) {
            return lapisColor.get();
        }
        if (highlightRedstone.get()
            && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return redstoneColor.get();
        }
        if (highlightIron.get()
            && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) {
            return ironColor.get();
        }
        if (highlightCoal.get()
            && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) {
            return coalColor.get();
        }
        if (highlightCopper.get()
            && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)) {
            return copperColor.get();
        }
        if (highlightNetherQuartz.get() && block == Blocks.NETHER_QUARTZ_ORE) {
            return netherQuartzColor.get();
        }
        if (highlightNetherGold.get() && block == Blocks.NETHER_GOLD_ORE) {
            return netherGoldColor.get();
        }
        return null;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (oreHighlight.get() == OreHighlightMode.Off || highlightedOres.isEmpty()) return;
        if (mc.player == null) return;

        float opacity = highlightOpacity.get() / 255.0f;
        double scale = highlightScale.get();
        float pulseAlpha = 1.0f;

        if (oreHighlight.get() == OreHighlightMode.PulseOutline) {
            pulseAlpha = 0.5f + 0.5f * (float) Math.sin(pulsePhase);
        }

        BlockPos playerPos = mc.player.getBlockPos();
        int rangeSq = range.get() * range.get();

        for (HighlightedOre ore : highlightedOres) {
            if (playerPos.getSquaredDistance(ore.pos) > rangeSq) continue;

            float finalAlpha = opacity * pulseAlpha;

            switch (oreHighlight.get()) {
                case Outline, PulseOutline -> renderOreOutline(event, ore, finalAlpha, scale);
                case Glow -> renderOreGlow(event, ore, opacity, scale);
            }
        }
    }

    private void renderOreOutline(Render3DEvent event, HighlightedOre ore,
                                    float alpha, double scale) {
        SettingColor c = ore.color;
        int r = c.r, g = c.g, b = c.b;

        meteordevelopment.meteorclient.utils.render.color.Color lineColor =
            new meteordevelopment.meteorclient.utils.render.color.Color(r, g, b,
                (int) (255 * alpha));

        meteordevelopment.meteorclient.utils.render.color.Color fillColor =
            new meteordevelopment.meteorclient.utils.render.color.Color(r, g, b,
                (int) (80 * alpha));

        double offset = (scale - 1.0) / 2.0;
        BlockPos pos = ore.pos;

        double x1 = pos.getX() - offset;
        double y1 = pos.getY() - offset;
        double z1 = pos.getZ() - offset;
        double x2 = pos.getX() + 1 + offset;
        double y2 = pos.getY() + 1 + offset;
        double z2 = pos.getZ() + 1 + offset;

        drawBoxLines(event, x1, y1, z1, x2, y2, z2, lineColor);
    }

    private void renderOreGlow(Render3DEvent event, HighlightedOre ore,
                                 float alpha, double scale) {
        SettingColor c = ore.color;
        int r = c.r, g = c.g, b = c.b;

        meteordevelopment.meteorclient.utils.render.color.Color lineColor =
            new meteordevelopment.meteorclient.utils.render.color.Color(r, g, b,
                (int) (255 * alpha));

        meteordevelopment.meteorclient.utils.render.color.Color fillColor =
            new meteordevelopment.meteorclient.utils.render.color.Color(r, g, b,
                (int) (40 * alpha));

        event.renderer.box(ore.pos, fillColor, lineColor,
            meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
    }

    private void drawBoxLines(Render3DEvent event, double x1, double y1, double z1,
                               double x2, double y2, double z2,
                               meteordevelopment.meteorclient.utils.render.color.Color color) {

        event.renderer.line(x1, y1, z1, x2, y1, z1, color);
        event.renderer.line(x2, y1, z1, x2, y1, z2, color);
        event.renderer.line(x2, y1, z2, x1, y1, z2, color);
        event.renderer.line(x1, y1, z2, x1, y1, z1, color);

        event.renderer.line(x1, y2, z1, x2, y2, z1, color);
        event.renderer.line(x2, y2, z1, x2, y2, z2, color);
        event.renderer.line(x2, y2, z2, x1, y2, z2, color);
        event.renderer.line(x1, y2, z2, x1, y2, z1, color);

        event.renderer.line(x1, y1, z1, x1, y2, z1, color);
        event.renderer.line(x2, y1, z1, x2, y2, z1, color);
        event.renderer.line(x2, y1, z2, x2, y2, z2, color);
        event.renderer.line(x1, y1, z2, x1, y2, z2, color);
    }

    private void requestWorldRendererRefresh() {
        if (mc.worldRenderer != null) {
            try {
                mc.worldRenderer.scheduleTerrainUpdate();
                if (debugPositions.get()) {
                    info("Scheduled world renderer terrain update.");
                }
            } catch (Exception e) {

                try {
                    mc.worldRenderer.reload();
                } catch (Exception ignored) {
                    warning("Failed to request world renderer refresh.");
                }
            }
        }
    }

    public Block getReplacement(Block original) {
        if (original == null) return null;
        return replacementMap.get(original);
    }

    public boolean hasReplacement(Block original) {
        return original != null && replacementMap.containsKey(original);
    }

    public int getReplacementCount() {
        return replacementMap.size();
    }

    public Map<Block, Block> getReplacementMapView() {
        return Collections.unmodifiableMap(replacementMap);
    }

    public boolean isPositionInRange(BlockPos pos) {
        if (!rangeLimitEnabled.get()) return true;
        if (mc.player == null) return true;
        return mc.player.getBlockPos().isWithinDistance(pos, range.get());
    }

    public OreHighlightMode getOreHighlightMode() {
        return oreHighlight.get();
    }

    public boolean shouldShowOriginalOnHover() {
        return showOriginalOnHover.get();
    }

    @Override
    public String getInfoString() {
        int mapSize = replacementMap.size();
        int highlightCount = highlightedOres.size();
        String info = mapSize + " spoof" + (mapSize != 1 ? "s" : "");
        if (oreHighlight.get() != OreHighlightMode.Off) {
            info += " | " + highlightCount + " ores";
        }
        return info;
    }
}
