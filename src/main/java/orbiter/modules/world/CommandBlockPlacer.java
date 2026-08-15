package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class CommandBlockPlacer extends CreativeSafetyModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCommands = settings.createGroup("Commands");
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgFlags = settings.createGroup("Flags");

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
            .name("amount")
            .description("Number of command blocks to place.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 50)
            .build());

    private final Setting<CmdBlockType> blockType = sgGeneral.add(new EnumSetting.Builder<CmdBlockType>()
            .name("block-type")
            .description("Type of command block to place.")
            .defaultValue(CmdBlockType.Impulse)
            .build());

    private final Setting<String> command1 = sgCommands.add(new StringSetting.Builder()
            .name("command-1")
            .description("Command for the first block (without /).")
            .defaultValue("say Orbiter On Crack!")
            .build());

    private final Setting<String> command2 = sgCommands.add(new StringSetting.Builder()
            .name("command-2")
            .description("Command for the second block (leave empty to reuse command-1).")
            .defaultValue("")
            .build());

    private final Setting<String> command3 = sgCommands.add(new StringSetting.Builder()
            .name("command-3")
            .description("Command for the third block.")
            .defaultValue("")
            .build());

    private final Setting<String> command4 = sgCommands.add(new StringSetting.Builder()
            .name("command-4")
            .description("Command for the fourth block.")
            .defaultValue("")
            .build());

    private final Setting<String> command5 = sgCommands.add(new StringSetting.Builder()
            .name("command-5")
            .description("Command for the fifth block.")
            .defaultValue("")
            .build());

    private final Setting<String> command6 = sgCommands.add(new StringSetting.Builder()
            .name("command-6").defaultValue("").build());

    private final Setting<String> command7 = sgCommands.add(new StringSetting.Builder()
            .name("command-7").defaultValue("").build());

    private final Setting<String> command8 = sgCommands.add(new StringSetting.Builder()
            .name("command-8").defaultValue("").build());

    private final Setting<String> command9 = sgCommands.add(new StringSetting.Builder()
            .name("command-9").defaultValue("").build());

    private final Setting<String> command10 = sgCommands.add(new StringSetting.Builder()
            .name("command-10").defaultValue("").build());

    private final Setting<PlaceDirection> direction = sgPlacement.add(new EnumSetting.Builder<PlaceDirection>()
            .name("direction")
            .description("Direction to place command blocks in a line.")
            .defaultValue(PlaceDirection.East)
            .build());

    private final Setting<Integer> startDistance = sgPlacement.add(new IntSetting.Builder()
            .name("start-distance")
            .description("Blocks away from you to start placing.")
            .defaultValue(2)
            .min(1)
            .sliderRange(1, 10)
            .build());

    private final Setting<Integer> placeDelay = sgTiming.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Ticks between placing each command block.")
            .defaultValue(4)
            .min(1)
            .sliderRange(1, 40)
            .build());

    private final Setting<Integer> updateDelay = sgTiming.add(new IntSetting.Builder()
            .name("update-delay")
            .description("Ticks to wait after placing before sending command update packet.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 20)
            .build());

    private final Setting<Boolean> autoActivate = sgFlags.add(new BoolSetting.Builder()
            .name("always-active")
            .description("Set blocks to always active (no redstone needed).")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> conditional = sgFlags.add(new BoolSetting.Builder()
            .name("conditional")
            .description("Set blocks to conditional mode.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> trackOutput = sgFlags.add(new BoolSetting.Builder()
            .name("track-output")
            .description("Track command output.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> chainAutoLink = sgFlags.add(new BoolSetting.Builder()
            .name("chain-auto-link")
            .description(
                    "For chain blocks: set first as impulse and rest as chain blocks pointing in the right direction.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> quickRepeat = sgFlags.add(new BoolSetting.Builder()
            .name("quick-repeat")
            .description("Convenience: auto-sets block type to Repeat and Always Active on every block placed.")
            .defaultValue(false)
            .build());

    private int placedCount = 0;
    private int tickCounter = 0;
    private List<BlockPos> positions;

    private int phase = 0;
    private int phaseTickCounter = 0;

    public CommandBlockPlacer() {
        super("command-block-placer",
                "Places command blocks with set commands. Requires Creative + OP.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        if (!mc.player.getAbilities().instabuild) {
            warning("You must be in Creative mode!");
            toggle();
            return;
        }

        info("Make sure you have OP permissions for command blocks to work.");

        placedCount = 0;
        tickCounter = 0;
        phase = 0;
        phaseTickCounter = 0;
        positions = new ArrayList<>();

        BlockPos start = mc.player.blockPosition();
        Direction dir = getDirection();

        for (int i = 0; i < amount.get(); i++) {
            BlockPos pos = start.offset(dir.getStepX() * (startDistance.get() + i), dir.getStepY() * (startDistance.get() + i), dir.getStepZ() * (startDistance.get() + i));
            positions.add(pos);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null || mc.gameMode == null || positions == null)
            return;

        if (placedCount >= positions.size()) {
            info("Placed " + placedCount + " command block(s)!");
            toggle();
            return;
        }

        if (phase == 0) {

            tickCounter++;
            if (tickCounter < placeDelay.get() && placedCount > 0)
                return;
            tickCounter = 0;

            BlockPos pos = positions.get(placedCount);

            CmdBlockType actualType = blockType.get();
            if (quickRepeat.get()) {
                actualType = CmdBlockType.Repeat;
            } else if (chainAutoLink.get() && placedCount > 0) {
                actualType = CmdBlockType.Chain;
            }

            String blockId = getBlockId(actualType);
            String facingState = getFacingState();

            String cmd = String.format("setblock %d %d %d %s[facing=%s]",
                    pos.getX(), pos.getY(), pos.getZ(), blockId, facingState);
            mc.player.connection.sendCommand(cmd);

            phase = 1;
            phaseTickCounter = 0;

        } else if (phase == 1) {

            phaseTickCounter++;
            if (phaseTickCounter < updateDelay.get())
                return;

            BlockPos pos = positions.get(placedCount);
            String cmd = getCommandForIndex(placedCount);

            CmdBlockType actualType = blockType.get();
            if (quickRepeat.get()) {
                actualType = CmdBlockType.Repeat;
            } else if (chainAutoLink.get() && placedCount > 0) {
                actualType = CmdBlockType.Chain;
            }

            CommandBlockEntity.Mode cbType = switch (actualType) {
                case Impulse -> CommandBlockEntity.Mode.REDSTONE;
                case Chain -> CommandBlockEntity.Mode.SEQUENCE;
                case Repeat -> CommandBlockEntity.Mode.AUTO;
            };

            boolean autoAct = quickRepeat.get() || autoActivate.get();

            mc.player.connection.send(new ServerboundSetCommandBlockPacket(
                    pos,
                    cmd,
                    cbType,
                    trackOutput.get(),
                    conditional.get(),
                    autoAct));

            placedCount++;
            phase = 0;
            phaseTickCounter = 0;
        }
    }

    @Override
    public void onDeactivate() {
        positions = null;
    }

    private String getCommandForIndex(int index) {
        String[] commands = {
                command1.get(), command2.get(), command3.get(), command4.get(), command5.get(),
                command6.get(), command7.get(), command8.get(), command9.get(), command10.get()
        };

        if (index < commands.length && !commands[index].isEmpty()) {
            return commands[index];
        }
        return command1.get();
    }

    private String getBlockId(CmdBlockType type) {
        return switch (type) {
            case Impulse -> "minecraft:command_block";
            case Chain -> "minecraft:chain_command_block";
            case Repeat -> "minecraft:repeating_command_block";
        };
    }

    private String getFacingState() {
        return switch (direction.get()) {
            case North -> "north";
            case South -> "south";
            case East -> "east";
            case West -> "west";
            case Up -> "up";
            case Down -> "down";
        };
    }

    private Direction getDirection() {
        return switch (direction.get()) {
            case North -> Direction.NORTH;
            case South -> Direction.SOUTH;
            case East -> Direction.EAST;
            case West -> Direction.WEST;
            case Up -> Direction.UP;
            case Down -> Direction.DOWN;
        };
    }

    public enum CmdBlockType {
        Impulse,
        Chain,
        Repeat
    }

    public enum PlaceDirection {
        North,
        South,
        East,
        West,
        Up,
        Down
    }
}
