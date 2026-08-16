package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class OperatorNuker extends CreativeSafetyModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMode = settings.createGroup("Nuke Mode");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
            .name("radius")
            .description("Nuke radius.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<NukeShape> shape = sgGeneral.add(new EnumSetting.Builder<NukeShape>()
            .name("shape")
            .description("Shape of the nuke area.")
            .defaultValue(NukeShape.Cube)
            .build());

    private final Setting<NukeMethod> method = sgGeneral.add(new EnumSetting.Builder<NukeMethod>()
            .name("method")
            .description("How to nuke blocks. Fill is faster, Setblock is more precise.")
            .defaultValue(NukeMethod.Fill)
            .build());

    private final Setting<Boolean> includeBedrock = sgGeneral.add(new BoolSetting.Builder()
            .name("include-bedrock")
            .description("Allow removing bedrock in setblock mode.")
            .defaultValue(false)
            .build());

    private final Setting<String> targetBlock = sgGeneral.add(new StringSetting.Builder()
            .name("target-block")
            .description("Only nuke specific block type (empty = all blocks).")
            .defaultValue("")
            .build());

    private final Setting<NukeDirection> nukeDirection = sgMode.add(new EnumSetting.Builder<NukeDirection>()
            .name("direction")
            .description("Which direction to nuke.")
            .defaultValue(NukeDirection.Around)
            .build());

    private final Setting<Boolean> continuous = sgMode.add(new BoolSetting.Builder()
            .name("continuous")
            .description("Keep nuking as you move, instead of a one-shot.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> continuousDelay = sgMode.add(new IntSetting.Builder()
            .name("continuous-delay")
            .description("Ticks between continuous nuke pulses.")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 100)
            .visible(continuous::get)
            .build());

    private final Setting<Integer> commandsPerTick = sgTiming.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of commands sent per tick.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<Integer> tickDelay = sgTiming.add(new IntSetting.Builder()
            .name("tick-delay")
            .description("Ticks between command batches.")
            .defaultValue(1)
            .min(0)
            .sliderRange(0, 20)
            .build());

    private List<String> pendingCommands;
    private int cmdIndex = 0;
    private int tickCounter = 0;
    private int continuousTicks = 0;

    public OperatorNuker() {
        super("operator-nuker",
                "Nuke blocks using /fill or /setblock commands. Requires OP permissions.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        if (!hasCommandPermission()) {
            warning("OP or command permissions are required.");
            toggle();
            return;
        }

        buildAndQueue();
    }

    private void buildAndQueue() {
        pendingCommands = buildCommands();
        cmdIndex = 0;
        tickCounter = 0;

        if (pendingCommands.isEmpty()) {
            warning("No commands generated!");
            if (!continuous.get()) toggle();
        } else {
            info("Queued " + pendingCommands.size() + " nuke commands.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null || pendingCommands == null)
            return;

        if (cmdIndex < pendingCommands.size()) {
            tickCounter++;
            if (tickCounter < tickDelay.get() && cmdIndex > 0)
                return;
            tickCounter = 0;

            int sent = 0;
            while (cmdIndex < pendingCommands.size() && sent < commandsPerTick.get()) {
                mc.player.connection.sendCommand(pendingCommands.get(cmdIndex));
                cmdIndex++;
                sent++;
            }
        } else {
            if (continuous.get()) {
                continuousTicks++;
                if (continuousTicks >= continuousDelay.get()) {
                    continuousTicks = 0;
                    buildAndQueue();
                }
            } else {
                info("Nuke complete! Sent " + pendingCommands.size() + " commands.");
                toggle();
            }
        }
    }

    private List<String> buildCommands() {
        List<String> commands = new ArrayList<>();
        if (mc.player == null || mc.level == null) return commands;

        BlockPos center = mc.player.blockPosition();
        int r = radius.get();

        BlockPos nukeCenter = switch (nukeDirection.get()) {
            case Around -> center;
            case Forward -> {
                double yaw = Math.toRadians(mc.player.getYRot());
                yield new BlockPos(
                        center.getX() + (int) (-Math.sin(yaw) * r),
                        center.getY(),
                        center.getZ() + (int) (Math.cos(yaw) * r));
            }
            case Flat -> center;
        };

        String target = normalizeTargetBlock(targetBlock.get());
        String replaceBlock = "minecraft:air";

        int minX = nukeCenter.getX() - r;
        int maxX = nukeCenter.getX() + r;
        int minZ = nukeCenter.getZ() - r;
        int maxZ = nukeCenter.getZ() + r;

        int minY;
        int maxY;
        if (nukeDirection.get() == NukeDirection.Flat) {
            minY = nukeCenter.getY();
            maxY = nukeCenter.getY();
        } else {
            minY = nukeCenter.getY() - r;
            maxY = nukeCenter.getY() + r;
        }

        minY = Math.max(worldBottomY(), minY);
        maxY = Math.min(worldTopY(), maxY);
        if (minY > maxY) return commands;

        if (!hasBlocksInArea(nukeCenter, minX, maxX, minY, maxY, minZ, maxZ, r)) {
            warning("No non-air blocks found in the nuke area.");
            return commands;
        }

        if (method.get() == NukeMethod.Fill) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int runStart = Integer.MIN_VALUE;

                    for (int x = minX; x <= maxX; x++) {
                        boolean include = isInsideShape(nukeCenter, x, y, z, r);
                        if (include) {
                            if (runStart == Integer.MIN_VALUE) runStart = x;
                        } else if (runStart != Integer.MIN_VALUE) {
                            addFillCommand(commands, runStart, y, z, x - 1, replaceBlock, target);
                            runStart = Integer.MIN_VALUE;
                        }
                    }

                    if (runStart != Integer.MIN_VALUE) {
                        addFillCommand(commands, runStart, y, z, maxX, replaceBlock, target);
                    }
                }
            }
        } else {
            Identifier targetId = target == null ? null : Identifier.tryParse(target);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!isInsideShape(nukeCenter, x, y, z, r)) continue;

                        mutable.set(x, y, z);
                        BlockState state = mc.level.getBlockState(mutable);

                        if (!includeBedrock.get() && state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) continue;

                        if (targetId != null) {
                            Identifier stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            if (!targetId.equals(stateId)) continue;
                        }

                        commands.add(String.format("setblock %d %d %d %s", x, y, z, replaceBlock));
                    }
                }
            }
        }

        return commands;
    }

    private boolean hasBlocksInArea(BlockPos center, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int r) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isInsideShape(center, x, y, z, r)) continue;
                    mutable.set(x, y, z);
                    if (!mc.level.getBlockState(mutable).isAir()) return true;
                }
            }
        }
        return false;
    }

    private boolean isInsideShape(BlockPos center, int x, int y, int z, int radius) {
        int dx = x - center.getX();
        int dy = y - center.getY();
        int dz = z - center.getZ();
        int r2 = radius * radius;

        return switch (shape.get()) {
            case Cube -> Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius;
            case Sphere -> (dx * dx + dy * dy + dz * dz) <= r2;
            case Cylinder -> (dx * dx + dz * dz) <= r2;
        };
    }

    private void addFillCommand(List<String> commands, int x1, int y, int z, int x2, String replacement, String target) {
        if (target != null) {
            commands.add(String.format("fill %d %d %d %d %d %d %s replace %s", x1, y, z, x2, y, z, replacement, target));
        } else {
            commands.add(String.format("fill %d %d %d %d %d %d %s", x1, y, z, x2, y, z, replacement));
        }
    }

    private String normalizeTargetBlock(String value) {
        if (value == null || value.isBlank()) return null;
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private boolean hasCommandPermission() {
        if (mc.player == null || mc.player.connection == null) return false;

        var dispatcher = mc.player.connection.getCommands();
        if (dispatcher == null || dispatcher.getRoot() == null) return false;

        return dispatcher.getRoot().getChild("fill") != null
                || dispatcher.getRoot().getChild("setblock") != null;
    }

    private int worldBottomY() {
        return mc.level.getMinY();
    }

    private int worldTopY() {
        return mc.level.getMinY() + mc.level.dimensionType().height() - 1;
    }

    @Override
    public void onDeactivate() {
        pendingCommands = null;
    }

    public enum NukeShape {
        Cube,
        Sphere,
        Cylinder
    }

    public enum NukeMethod {
        Fill,
        Setblock
    }

    public enum NukeDirection {
        Around,
        Forward,
        Flat
    }
}
