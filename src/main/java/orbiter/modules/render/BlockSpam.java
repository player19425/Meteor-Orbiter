package orbiter.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import orbiter.modules.CreativeSafetyModule;
import orbiter.util.CommandUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class BlockSpam extends CreativeSafetyModule {
    public enum Mode { Animation, Rain }
    public enum Delivery { FallingBlock, Setblock }
    public enum BlockPickMode { Single, List, AllBlocks }
    public enum Shape {
        Cube, Sphere, Ring, Torus, Helix, DoubleHelix, Pyramid, Diamond,
        Cone, Cylinder, Spiral, Galaxy, Heart, Star, Wave, Atom
    }
    public enum CenterTargetMode { Self, NearestPlayer, PlayerName, Selector, FixedPosition }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAnimation = settings.createGroup("Animation");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgTransform = settings.createGroup("Transform");
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgRain = settings.createGroup("Rain");
    private final SettingGroup sgRate = settings.createGroup("Rate");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Animation spawns blocks along a rotating shape like ParticleControl; Rain drops blocks from above that land and stay on the floor as normal blocks.")
        .defaultValue(Mode.Rain).build());
    private final Setting<Delivery> delivery = sgGeneral.add(new EnumSetting.Builder<Delivery>()
        .name("delivery").description("FallingBlock summons falling block entities; Setblock places blocks directly with /setblock.")
        .defaultValue(Delivery.FallingBlock).build());
    private final Setting<BlockPickMode> blockSource = sgGeneral.add(new EnumSetting.Builder<BlockPickMode>()
        .name("block-source").description("Single uses the block setting; List picks a random block from the block list; AllBlocks picks from every valid Minecraft block (air, water and lava excluded).")
        .defaultValue(BlockPickMode.Single).build());
    private final Setting<String> block = sgGeneral.add(new StringSetting.Builder()
        .name("block").description("Block to spam. Accepts ids with or without the minecraft: prefix and optional properties, e.g. minecraft:stone or minecraft:oak_stairs[facing=north].")
        .defaultValue("minecraft:stone")
        .visible(() -> blockSource.get() == BlockPickMode.Single).build());
    private final Setting<List<String>> blockList = sgGeneral.add(new StringListSetting.Builder()
        .name("block-list").description("Block pool used when block-source is List. Falls back to the block setting when empty.")
        .defaultValue(List.of("minecraft:stone", "minecraft:obsidian", "minecraft:netherite_block", "minecraft:tnt"))
        .visible(() -> blockSource.get() == BlockPickMode.List).build());

    private final Setting<Shape> shape = sgAnimation.add(new EnumSetting.Builder<Shape>()
        .name("shape").description("Geometric block shape.").defaultValue(Shape.Torus)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> radius = sgAnimation.add(new DoubleSetting.Builder()
        .name("radius").description("Shape radius.").defaultValue(2.0).min(0.5).sliderRange(0.5, 10.0)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> quality = sgAnimation.add(new DoubleSetting.Builder()
        .name("quality").description("Virtual geometry resolution. Higher values produce denser block shapes.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 4.0)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> animationSpeed = sgAnimation.add(new DoubleSetting.Builder()
        .name("animation-speed").description("Overall animation speed multiplier.").defaultValue(1.0).min(0.0).sliderRange(0.0, 5.0)
        .visible(() -> mode.get() == Mode.Animation).build());

    private final Setting<Double> rotateXSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("x-speed").description("X-axis degrees per animation tick.").defaultValue(0.8).sliderRange(-10, 10)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> rotateYSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("y-speed").description("Y-axis degrees per animation tick.").defaultValue(2.0).sliderRange(-10, 10)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> rotateZSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("z-speed").description("Z-axis degrees per animation tick.").defaultValue(0.4).sliderRange(-10, 10)
        .visible(() -> mode.get() == Mode.Animation).build());

    private final Setting<Double> translateX = sgTransform.add(new DoubleSetting.Builder()
        .name("translate-x").description("X offset from the selected center.").defaultValue(0.0).sliderRange(-10, 10)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> translateY = sgTransform.add(new DoubleSetting.Builder()
        .name("translate-y").description("Y offset from the selected center.").defaultValue(1.0).sliderRange(-10, 10)
        .visible(() -> mode.get() == Mode.Animation).build());
    private final Setting<Double> translateZ = sgTransform.add(new DoubleSetting.Builder()
        .name("translate-z").description("Z offset from the selected center.").defaultValue(0.0).sliderRange(-10, 10)
        .visible(() -> mode.get() == Mode.Animation).build());

    private final Setting<CenterTargetMode> centerTargetMode = sgTarget.add(new EnumSetting.Builder<CenterTargetMode>()
        .name("center-target-mode").description("Who or what the blocks are centered on.")
        .defaultValue(CenterTargetMode.Self).build());
    private final Setting<String> centerPlayerName = sgTarget.add(new StringSetting.Builder()
        .name("center-player-name").description("Exact player name used as the block center.").defaultValue("")
        .visible(() -> centerTargetMode.get() == CenterTargetMode.PlayerName).build());
    private final Setting<String> centerSelector = sgTarget.add(new StringSetting.Builder()
        .name("center-selector").description("Player selector used as the block center. Multiple matches create one shape per player.")
        .defaultValue("@a[tag=block-target]")
        .visible(() -> centerTargetMode.get() == CenterTargetMode.Selector).build());
    private final Setting<Double> nearestRange = sgTarget.add(new DoubleSetting.Builder()
        .name("nearest-range").description("Maximum range when centering on the nearest other player.").defaultValue(64).min(1).sliderRange(1, 256)
        .visible(() -> centerTargetMode.get() == CenterTargetMode.NearestPlayer).build());

    private final Setting<Integer> rainHeight = sgRain.add(new IntSetting.Builder()
        .name("rain-height").description("How many blocks above the target the rain starts.").defaultValue(40).min(5).sliderRange(5, 120)
        .visible(() -> mode.get() == Mode.Rain).build());
    private final Setting<Double> rainSpread = sgRain.add(new DoubleSetting.Builder()
        .name("rain-spread").description("Horizontal radius around the target where blocks rain.").defaultValue(5.0).min(0.5).sliderRange(0.5, 20)
        .visible(() -> mode.get() == Mode.Rain).build());
    private final Setting<Boolean> motion = sgRain.add(new BoolSetting.Builder()
        .name("motion").description("Apply a Motion tag to raining blocks so they scatter sideways as they fall.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Rain).build());
    private final Setting<Double> motionSpeed = sgRain.add(new DoubleSetting.Builder()
        .name("motion-speed").description("Horizontal motion speed in blocks per tick, applied in a random direction.")
        .defaultValue(0.15).min(0.0).sliderRange(0.0, 1.0)
        .visible(() -> mode.get() == Mode.Rain).build());
    private final Setting<Double> motionVertical = sgRain.add(new DoubleSetting.Builder()
        .name("motion-vertical").description("Vertical motion speed. Negative pushes blocks down faster, positive launches them up.")
        .defaultValue(0.0).sliderRange(-2.0, 2.0)
        .visible(() -> mode.get() == Mode.Rain).build());

    private final Setting<Integer> maxCommandsPerBatch = sgRate.add(new IntSetting.Builder()
        .name("max-commands-per-batch").description("Commands sent per batch. In Rain mode this is how many blocks rain per batch; in Animation it caps shape points per batch.")
        .defaultValue(10).min(1).sliderRange(1, 512).build());
    private final Setting<Integer> delayTicks = sgRate.add(new IntSetting.Builder()
        .name("delay-ticks").description("Ticks between command batches. 1 sends a batch every tick (20 batches per second).")
        .defaultValue(1).min(1).sliderRange(1, 20).build());

    private static final double GOLDEN_FRACTION = 0.6180339887498949;
    private final Random random = new Random();
    private double phase;
    private double samplePhase;
    private int tickCounter;
    private Vec3 fixedCenter;

    public BlockSpam() {
        super("block-spam", "let the blocks rain.");
    }

    @Override
    public void onActivate() {
        phase = 0;
        samplePhase = 0;
        tickCounter = 0;
        fixedCenter = mc.player == null ? null : playerPosition();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;
        if (++tickCounter < delayTicks.get()) return;
        tickCounter = 0;

        if (mode.get() == Mode.Rain) {
            rainTick();
        } else {
            animationTick();
        }
    }

    private void animationTick() {
        String target = resolveTargetString();
        if (target == null && centerTargetMode.get() != CenterTargetMode.FixedPosition) return;
        if (centerTargetMode.get() == CenterTargetMode.FixedPosition && fixedCenter == null) fixedCenter = playerPosition();

        phase += animationSpeed.get() * delayTicks.get();
        samplePhase = fractional(samplePhase + GOLDEN_FRACTION);

        int pointCount = Math.max(12, (int) Math.round(basePointCount(shape.get()) * quality.get()));
        int commands = Math.min(pointCount, maxCommandsPerBatch.get());
        double ax = Math.toRadians(phase * rotateXSpeed.get());
        double ay = Math.toRadians(phase * rotateYSpeed.get());
        double az = Math.toRadians(phase * rotateZSpeed.get());

        Vec3 base = fixedCenter;
        for (int n = 0; n < commands; n++) {
            double distributed = fractional((n + samplePhase) / commands);
            int index = Math.min(pointCount - 1, (int) Math.floor(distributed * pointCount));
            Vec3 offset = rotate(pointFor(shape.get(), index, pointCount, radius.get(), phase), ax, ay, az)
                .add(translateX.get(), translateY.get(), translateZ.get());

            if (delivery.get() == Delivery.FallingBlock) {
                emitFallingBlock(target, base, offset, true, false);
            } else {
                emitSetblock(target, base, offset);
            }
        }
    }

    private void rainTick() {
        if (centerTargetMode.get() == CenterTargetMode.FixedPosition && fixedCenter == null) fixedCenter = playerPosition();

        double spread = rainSpread.get();
        int count = Math.min(maxCommandsPerBatch.get(), 5000);

        if (delivery.get() == Delivery.FallingBlock) {
            String target = resolveTargetString();
            boolean withMotion = motion.get();
            for (int i = 0; i < count; i++) {
                Vec3 offset = new Vec3(randomOffset(spread), rainHeight.get(), randomOffset(spread));
                emitFallingBlock(target, fixedCenter, offset, false, withMotion);
            }
        } else {
            Vec3 center = resolveTargetPosition();
            if (center == null) {
                warning("Could not resolve the target position client-side for Rain + Setblock. Use Self, Nearest Player, a player name, or Fixed Position.");
                return;
            }
            for (int i = 0; i < count; i++) {
                double rx = randomOffset(spread);
                double rz = randomOffset(spread);
                BlockPos floor = floorAt(center.x + rx, center.z + rz);
                if (floor == null) continue;
                String cmd = CommandUtils.formatCommand("setblock %d %d %d %s",
                    floor.getX(), floor.getY(), floor.getZ(), normalizeBlock(pickBlock()));
                mc.player.connection.sendCommand(CommandUtils.vanilla(cmd));
            }
        }
    }

    private void emitFallingBlock(String target, Vec3 base, Vec3 offset, boolean floating, boolean withMotion) {
        String nbt = fallingBlockNbt(floating, withMotion);
        String cmd;
        if (target != null) {
            cmd = CommandUtils.formatCommand("execute at %s run summon minecraft:falling_block ~%.2f ~%.2f ~%.2f %s",
                target, offset.x, offset.y, offset.z, nbt);
        } else {
            Vec3 abs = base.add(offset);
            cmd = CommandUtils.formatCommand("summon minecraft:falling_block %.2f %.2f %.2f %s",
                abs.x, abs.y, abs.z, nbt);
        }
        mc.player.connection.sendCommand(CommandUtils.vanilla(cmd));
    }

    private void emitSetblock(String target, Vec3 base, Vec3 offset) {
        String blockStr = normalizeBlock(pickBlock());
        String cmd;
        if (target != null) {
            cmd = CommandUtils.formatCommand("execute at %s run setblock ~%d ~%d ~%d %s",
                target, (int) Math.floor(offset.x), (int) Math.floor(offset.y), (int) Math.floor(offset.z), blockStr);
        } else {
            Vec3 abs = base.add(offset);
            cmd = CommandUtils.formatCommand("setblock %d %d %d %s",
                (int) Math.floor(abs.x), (int) Math.floor(abs.y), (int) Math.floor(abs.z), blockStr);
        }
        mc.player.connection.sendCommand(CommandUtils.vanilla(cmd));
    }

    private String fallingBlockNbt(boolean floating, boolean withMotion) {
        StringBuilder sb = new StringBuilder("{").append(blockStateNbt(pickBlock()));
        if (floating) {
            sb.append(",NoGravity:1b");
        }
        sb.append(",DropItem:0b");
        if (withMotion) {
            double angle = random.nextDouble() * Math.PI * 2;
            double h = motionSpeed.get();
            sb.append(",Motion:[")
                .append(fmt(Math.cos(angle) * h)).append(',')
                .append(fmt(motionVertical.get())).append(',')
                .append(fmt(Math.sin(angle) * h)).append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private String pickBlock() {
        return switch (blockSource.get()) {
            case Single -> block.get();
            case List -> {
                List<String> list = blockList.get();
                if (list == null || list.isEmpty()) yield block.get();
                String picked = list.get(random.nextInt(list.size()));
                yield picked == null || picked.isBlank() ? block.get() : picked.trim();
            }
            case AllBlocks -> {
                String[] all = allBlocks();
                yield all.length == 0 ? block.get() : all[random.nextInt(all.length)];
            }
        };
    }

    private static volatile String[] allBlocksCache;

    private String[] allBlocks() {
        String[] cached = allBlocksCache;
        if (cached != null) return cached;
        synchronized (BlockSpam.class) {
            cached = allBlocksCache;
            if (cached != null) return cached;
            List<String> ids = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                BlockState state = block.defaultBlockState();
                if (state.isAir()) continue;
                if (!state.getFluidState().isEmpty()) continue;
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (id != null) ids.add(id.toString());
            }
            cached = ids.toArray(new String[0]);
            allBlocksCache = cached;
        }
        return cached;
    }

    private String resolveTargetString() {
        return switch (centerTargetMode.get()) {
            case Self -> mc.player.getGameProfile().name();
            case NearestPlayer -> CommandUtils.formatCommand(
                "@p[name=!%s,distance=..%.2f]", mc.player.getGameProfile().name(), nearestRange.get());
            case PlayerName -> rejectInvalidCenter(safePlayerTarget(centerPlayerName.get(), false), "player name");
            case Selector -> rejectInvalidCenter(safeCenterSelector(centerSelector.get()), "center selector");
            case FixedPosition -> null;
        };
    }

    private Vec3 resolveTargetPosition() {
        return switch (centerTargetMode.get()) {
            case Self -> playerPosition();
            case NearestPlayer -> nearestPlayerPosition();
            case PlayerName -> playerByName(centerPlayerName.get());
            case Selector -> nearestPlayerPosition();
            case FixedPosition -> fixedCenter;
        };
    }

    private String rejectInvalidCenter(String value, String label) {
        if (value != null) return value;
        warning("Invalid " + label + ". The module has been disabled before sending any command.");
        toggle();
        return null;
    }

    private Vec3 nearestPlayerPosition() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 self = playerPosition();
        double range = nearestRange.get();
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            double distance = player.distanceToSqr(self);
            if (distance < best && distance <= range * range) {
                best = distance;
                nearest = player;
            }
        }
        return nearest == null ? null : nearest.position();
    }

    private Vec3 playerByName(String name) {
        if (name == null || name.isBlank() || mc.level == null) return null;
        for (Player player : mc.level.players()) {
            if (player.getName().getString().equalsIgnoreCase(name)) return player.position();
        }
        return null;
    }

    private BlockPos floorAt(double x, double z) {
        if (mc.level == null) return null;
        int minY = mc.level.getMinY();
        int maxY = minY + mc.level.dimensionType().height() - 1;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int y = maxY; y > minY; y--) {
            if (!mc.level.getBlockState(new BlockPos(bx, y, bz)).isAir()) {
                return new BlockPos(bx, y + 1, bz);
            }
        }
        return null;
    }

    private double randomOffset(double spread) {
        return (random.nextDouble() * 2 - 1) * spread;
    }

    private String normalizeBlock(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "minecraft:stone";
        if (!normalized.contains(":")) return "minecraft:" + normalized;
        return normalized;
    }

    private String blockStateNbt(String value) {
        String blockStr = normalizeBlock(value);
        String name = blockStr;
        String props = "";
        int bracket = blockStr.indexOf('[');
        if (bracket >= 0 && blockStr.endsWith("]")) {
            name = blockStr.substring(0, bracket);
            props = blockStr.substring(bracket + 1, blockStr.length() - 1);
        }

        if (props.isEmpty()) return "BlockState:{Name:\"" + name + "\"}";

        StringBuilder sb = new StringBuilder("BlockState:{Name:\"").append(name).append("\",Properties:{");
        boolean first = true;
        for (String entry : props.split(",")) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(kv[0].trim()).append(":\"").append(kv[1].trim()).append('"');
        }
        sb.append("}}");
        return sb.toString();
    }

    private String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private Vec3 playerPosition() {
        return new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private String safePlayerTarget(String value, boolean allowSelfSelector) {
        if (value == null) return null;
        String trimmed = value.trim();
        String name = safePlayerName(trimmed);
        if (name != null) return name;
        if (!trimmed.startsWith("@")) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (isSelectorType(lower, '@', 's') && allowSelfSelector && selectorCharactersAreSafe(trimmed)) return trimmed;
        if ((isSelectorType(lower, '@', 'a') || isSelectorType(lower, '@', 'p') || isSelectorType(lower, '@', 'r'))
            && selectorCharactersAreSafe(trimmed)) return trimmed;
        return null;
    }

    private boolean isSelectorType(String value, char prefix, char type) {
        return value.length() == 2 && value.charAt(0) == prefix && value.charAt(1) == type
            || value.length() > 2 && value.charAt(0) == prefix && value.charAt(1) == type && value.charAt(2) == '[';
    }

    private String safeCenterSelector(String value) {
        String safe = safePlayerTarget(value, false);
        return safe != null && safe.startsWith("@") ? safe : null;
    }

    private boolean selectorCharactersAreSafe(String value) {
        if (value.isEmpty() || value.length() > 180) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) continue;
            if ("@_[],:.=!+-.".indexOf(c) < 0) return false;
        }
        return true;
    }

    private String safePlayerName(String value) {
        if (value == null || value.isEmpty() || value.length() > 16) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return null;
        }
        return value;
    }

    private int basePointCount(Shape value) {
        return switch (value) {
            case Cube -> 120;
            case Sphere -> 144;
            case Ring -> 96;
            case Torus -> 192;
            case Helix, DoubleHelix -> 144;
            case Pyramid, Diamond -> 120;
            case Cone, Cylinder -> 144;
            case Spiral, Galaxy, Heart, Star, Wave -> 128;
            case Atom -> 168;
        };
    }

    private Vec3 pointFor(Shape value, int index, int count, double r, double time) {
        double t = count <= 1 ? 0 : index / (double) count;
        return switch (value) {
            case Sphere -> {
                double golden = Math.PI * (3.0 - Math.sqrt(5.0));
                double y = 1.0 - 2.0 * ((index + 0.5) / count);
                double radial = Math.sqrt(Math.max(0, 1.0 - y * y));
                double a = golden * index;
                yield new Vec3(Math.cos(a) * radial * r, y * r, Math.sin(a) * radial * r);
            }
            case Ring -> new Vec3(Math.cos(t * Math.PI * 2) * r, 0, Math.sin(t * Math.PI * 2) * r);
            case Torus -> {
                double major = t * Math.PI * 2;
                double minor = fractional(index * GOLDEN_FRACTION + time * 0.008) * Math.PI * 2;
                double tube = r * 0.30;
                yield new Vec3((r + tube * Math.cos(minor)) * Math.cos(major), tube * Math.sin(minor),
                    (r + tube * Math.cos(minor)) * Math.sin(major));
            }
            case Helix -> {
                double a = t * Math.PI * 6 + time * 0.05;
                yield new Vec3(Math.cos(a) * r, (t - 0.5) * r * 2, Math.sin(a) * r);
            }
            case DoubleHelix -> {
                int strand = index & 1;
                int strandCount = Math.max(1, count / 2);
                double u = (index / 2.0) / strandCount;
                double a = u * Math.PI * 6 + strand * Math.PI + time * 0.05;
                yield new Vec3(Math.cos(a) * r, (u - 0.5) * r * 2, Math.sin(a) * r);
            }
            case Cone -> conePoint(index, count, r);
            case Cylinder -> cylinderPoint(index, count, r);
            case Spiral -> {
                double a = t * Math.PI * 8 + time * 0.035;
                double radial = r * (0.08 + 0.92 * t);
                yield new Vec3(Math.cos(a) * radial, (t - 0.5) * r * 0.35, Math.sin(a) * radial);
            }
            case Galaxy -> galaxyPoint(index, count, r, time);
            case Heart -> heartPoint(t, r);
            case Star -> starPoint(index, count, r);
            case Wave -> {
                double x = (t * 2 - 1) * r;
                double a = t * Math.PI * 4 + time * 0.06;
                yield new Vec3(x, Math.sin(a) * r * 0.45, Math.cos(a * 0.5) * r * 0.25);
            }
            case Atom -> atomPoint(index, count, r, time);
            case Cube -> cubePoint(index, count, r);
            case Pyramid -> pyramidPoint(index, count, r);
            case Diamond -> diamondPoint(index, count, r);
        };
    }

    private Vec3 conePoint(int index, int count, double r) {
        if (index % 4 == 0) {
            double a = (index / 4.0) / Math.max(1.0, count / 4.0) * Math.PI * 2;
            return new Vec3(Math.cos(a) * r, -r, Math.sin(a) * r);
        }
        double t = index / (double) count;
        double a = t * Math.PI * 10;
        double radial = r * (1.0 - t);
        return new Vec3(Math.cos(a) * radial, -r + 2 * r * t, Math.sin(a) * radial);
    }

    private Vec3 cylinderPoint(int index, int count, double r) {
        int lane = index % 3;
        double t = (index / 3.0) / Math.max(1.0, count / 3.0);
        double a = t * Math.PI * 6;
        if (lane == 0) return new Vec3(Math.cos(a) * r, -r, Math.sin(a) * r);
        if (lane == 1) return new Vec3(Math.cos(a) * r, r, Math.sin(a) * r);
        return new Vec3(Math.cos(a) * r, (fractional(t * 3) * 2 - 1) * r, Math.sin(a) * r);
    }

    private Vec3 galaxyPoint(int index, int count, double r, double time) {
        int arms = 4;
        int arm = index % arms;
        double t = (index / (double) arms) / Math.max(1.0, count / (double) arms);
        double distance = r * Math.sqrt(Math.min(1.0, t));
        double a = arm * Math.PI * 2 / arms + t * Math.PI * 3 + time * 0.025;
        return new Vec3(Math.cos(a) * distance, Math.sin(t * Math.PI * 8) * r * 0.06, Math.sin(a) * distance);
    }

    private Vec3 heartPoint(double t, double r) {
        double a = t * Math.PI * 2;
        double x = 16 * Math.pow(Math.sin(a), 3);
        double y = 13 * Math.cos(a) - 5 * Math.cos(2 * a) - 2 * Math.cos(3 * a) - Math.cos(4 * a);
        return new Vec3(x * r / 17.0, y * r / 17.0, 0);
    }

    private Vec3 starPoint(int index, int count, double r) {
        int vertices = 10;
        double progress = index / (double) count * vertices;
        int vertex = (int) Math.floor(progress) % vertices;
        double u = fractional(progress);
        Vec3 a = starVertex(vertex, r);
        Vec3 b = starVertex((vertex + 1) % vertices, r);
        return a.lerp(b, u);
    }

    private Vec3 starVertex(int index, double r) {
        double radial = (index & 1) == 0 ? r : r * 0.42;
        double a = -Math.PI / 2 + index * Math.PI / 5;
        return new Vec3(Math.cos(a) * radial, Math.sin(a) * radial, 0);
    }

    private Vec3 atomPoint(int index, int count, double r, double time) {
        int orbit = index % 3;
        double t = (index / 3.0) / Math.max(1.0, count / 3.0);
        double a = t * Math.PI * 2 + time * 0.025;
        Vec3 ring = new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
        return switch (orbit) {
            case 0 -> rotate(ring, Math.toRadians(60), 0, 0);
            case 1 -> rotate(ring, Math.toRadians(-60), 0, Math.toRadians(60));
            default -> rotate(ring, 0, 0, Math.toRadians(60));
        };
    }

    private Vec3 cubePoint(int index, int count, double r) {
        int edge = index % 12;
        double u = (index / 12.0) / Math.max(1.0, count / 12.0 - 1.0);
        u = fractional(u);
        double v = -r + 2 * r * u;
        return switch (edge) {
            case 0 -> new Vec3(v, -r, -r); case 1 -> new Vec3(v, -r, r);
            case 2 -> new Vec3(v, r, -r); case 3 -> new Vec3(v, r, r);
            case 4 -> new Vec3(-r, v, -r); case 5 -> new Vec3(-r, v, r);
            case 6 -> new Vec3(r, v, -r); case 7 -> new Vec3(r, v, r);
            case 8 -> new Vec3(-r, -r, v); case 9 -> new Vec3(-r, r, v);
            case 10 -> new Vec3(r, -r, v); default -> new Vec3(r, r, v);
        };
    }

    private Vec3 pyramidPoint(int index, int count, double r) {
        Vec3 top = new Vec3(0, r, 0);
        Vec3[] base = {new Vec3(r, -r, r), new Vec3(-r, -r, r), new Vec3(-r, -r, -r), new Vec3(r, -r, -r)};
        int segment = index % 8;
        Vec3 a = segment < 4 ? top : base[segment - 4];
        Vec3 b = segment < 4 ? base[segment] : base[(segment - 3) % 4];
        double u = (index / 8.0) / Math.max(1.0, count / 8.0);
        return a.lerp(b, fractional(u));
    }

    private Vec3 diamondPoint(int index, int count, double r) {
        Vec3 top = new Vec3(0, r, 0);
        Vec3 bottom = new Vec3(0, -r, 0);
        Vec3[] ring = {new Vec3(r, 0, 0), new Vec3(0, 0, r), new Vec3(-r, 0, 0), new Vec3(0, 0, -r)};
        int segment = index % 12;
        Vec3 a;
        Vec3 b;
        if (segment < 4) { a = top; b = ring[segment]; }
        else if (segment < 8) { a = bottom; b = ring[segment - 4]; }
        else { a = ring[segment - 8]; b = ring[(segment - 7) % 4]; }
        double u = (index / 12.0) / Math.max(1.0, count / 12.0);
        return a.lerp(b, fractional(u));
    }

    private Vec3 rotate(Vec3 p, double ax, double ay, double az) {
        double x1 = p.x;
        double y1 = p.y * Math.cos(ax) - p.z * Math.sin(ax);
        double z1 = p.y * Math.sin(ax) + p.z * Math.cos(ax);
        double x2 = x1 * Math.cos(ay) + z1 * Math.sin(ay);
        double z2 = -x1 * Math.sin(ay) + z1 * Math.cos(ay);
        double x3 = x2 * Math.cos(az) - y1 * Math.sin(az);
        double y3 = x2 * Math.sin(az) + y1 * Math.cos(az);
        return new Vec3(x3, y3, z2);
    }

    private double fractional(double value) {
        return value - Math.floor(value);
    }
}
