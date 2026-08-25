package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientSideMine extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> antiRubberBand = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-rubber-band")
        .description("Prevents the server from teleporting you back when you break blocks client-side.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> blockServerUpdates = sgGeneral.add(new BoolSetting.Builder()
        .name("block-server-updates")
        .description("Prevents the server from re-placing blocks you have mined client-side.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxTrackedBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-tracked-blocks")
        .description("Maximum number of client-side broken blocks to remember.")
        .defaultValue(200)
        .min(10).sliderRange(10, 1000)
        .build());

    private final Set<BlockPos> clientMinedBlocks = new HashSet<>();
    private final Map<BlockPos, BlockState> originalStates = new HashMap<>();
    private boolean expectingTeleport = false;
    private int teleportCooldown = 0;

    public ClientSideMine() {
        super(Orbiter.CATEGORY, "client-side-mine", "Instantly breaks blocks client-side.");
    }

    @Override
    public void onDeactivate() {
        performReset();
    }

    private void performReset() {
        if (mc.level != null) {
            for (BlockPos pos : clientMinedBlocks) {
                BlockState restore = originalStates.get(pos);
                if (restore != null && !restore.isAir()) {
                    mc.level.setBlock(pos, restore, 3);
                    mc.level.setBlocksDirty(pos, Blocks.AIR.defaultBlockState(), restore);
                } else {

                    if (mc.levelRenderer != null) {
                        mc.level.setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                    }
                }
            }
        }
        int count = clientMinedBlocks.size();
        clientMinedBlocks.clear();
        originalStates.clear();
        expectingTeleport = false;
        teleportCooldown = 0;
        if (isActive() && count > 0) {
            info("Reset " + count + " tracked blocks. Re-synced with server.");
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc.level == null || mc.player == null || event.blockPos == null) return;

        BlockState state = mc.level.getBlockState(event.blockPos);
        if (state.isAir()) return;

        boolean anyMitigation = antiRubberBand.get() || blockServerUpdates.get();

        if (anyMitigation && originalStates.size() < 8192) {
            originalStates.put(event.blockPos.immutable(), state);
        }

        mc.level.setBlock(event.blockPos, Blocks.AIR.defaultBlockState(), 3);

        if (anyMitigation) {
            BlockPos immutable = event.blockPos.immutable();
            clientMinedBlocks.add(immutable);

            if (clientMinedBlocks.size() > maxTrackedBlocks.get()) {
                int toRemove = clientMinedBlocks.size() / 2;
                var iterator = clientMinedBlocks.iterator();
                for (int i = 0; i < toRemove && iterator.hasNext(); i++) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }

        if (antiRubberBand.get()) {
            expectingTeleport = true;
            teleportCooldown = 10;
        }

        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (teleportCooldown > 0) {
            teleportCooldown--;
            if (teleportCooldown == 0) {
                expectingTeleport = false;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (antiRubberBand.get() && expectingTeleport && event.packet instanceof ClientboundPlayerPositionPacket) {
            event.cancel();
            if (mc.level != null) {
                for (BlockPos pos : clientMinedBlocks) {
                    mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            return;
        }

        if (blockServerUpdates.get()) {
            if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
                if (clientMinedBlocks.contains(packet.getPos())) {
                    BlockPos pos = packet.getPos().immutable();
                    mc.execute(() -> {
                        if (mc.level != null) {
                            mc.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    });
                }
            }

            if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {
                packet.runUpdates((pos, state) -> {
                    BlockPos immutable = pos.immutable();
                    if (clientMinedBlocks.contains(immutable)) {
                        mc.execute(() -> {
                            if (mc.level != null) {
                                mc.level.setBlock(immutable, Blocks.AIR.defaultBlockState(), 3);
                            }
                        });
                    }
                });
            }
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String msg = event.message;
        if (msg == null) return;

        String prefix = null;
        if (msg.startsWith(".csm reset")) prefix = ".csm reset";
        else if (msg.startsWith(".csm sync")) prefix = ".csm sync";
        else return;

        event.cancel();
        performReset();
    }

    public boolean isMinedClientSide(BlockPos pos) {
        return clientMinedBlocks.contains(pos);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton resetBtn = table.add(theme.button("Reset & Sync")).expandCellX().widget();
        resetBtn.action = () -> performReset();
        return table;
    }
}
