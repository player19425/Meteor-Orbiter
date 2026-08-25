package orbiter.modules.render;

import orbiter.util.CommandUtils;
import orbiter.modules.CreativeSafetyModule;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class FireworkShow extends CreativeSafetyModule {
    private static final int MAX_CHAT_COMMAND_LENGTH = 255;
    private static final int MAX_COMMAND_BLOCK_COMMAND_LENGTH = 32700;
    private static final int OMEGA_PLUS_HARD_MAX_EXPLOSIONS = 24;
    private static final int OMEGA_PLUS_HARD_MAX_COLORS = 4;
    private static final int OMEGA_PLUS_HARD_MAX_CLONES = 1;
    private static final int OMEGA_PLUS_FALLBACK_BUDGET = 40;
    private static final int OMEGA_PLUS_HARD_MAX_BUDGET = 80;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFirework = settings.createGroup("Firework Settings");
    private final SettingGroup sgShow = settings.createGroup("Show Settings");

    private final Setting<Integer> totalFireworks = sgGeneral.add(new IntSetting.Builder()
            .name("total-fireworks")
            .description("Total fireworks to launch (0 = infinite).")
            .defaultValue(50)
            .min(0)
            .sliderRange(0, 500)
            .build());

    private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("commands-per-tick")
            .description("Number of fireworks launched per tick.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 20)
            .build());

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks between firework launches.")
            .defaultValue(5)
            .min(0)
            .sliderRange(0, 40)
            .build());

    private final Setting<Integer> spreadRadius = sgGeneral.add(new IntSetting.Builder()
            .name("spread-radius")
            .description("Horizontal spread radius.")
            .defaultValue(10)
            .min(0)
            .sliderRange(0, 50)
            .build());

    private final Setting<FireworkShape> shape = sgFirework.add(new EnumSetting.Builder<FireworkShape>()
            .name("shape")
            .description("Explosion shape type.")
            .defaultValue(FireworkShape.Random)
            .build());

    private final Setting<Integer> flight = sgFirework.add(new IntSetting.Builder()
            .name("flight-duration")
            .description("How high the firework flies (1-3).")
            .defaultValue(2)
            .min(1)
            .max(3)
            .sliderRange(1, 3)
            .build());

    private final Setting<Boolean> trail = sgFirework.add(new BoolSetting.Builder()
            .name("trail")
            .description("Add trail effect to explosions.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> twinkle = sgFirework.add(new BoolSetting.Builder()
            .name("twinkle")
            .description("Add twinkle/flicker effect.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> multiColor = sgFirework.add(new BoolSetting.Builder()
            .name("multi-color")
            .description("Use multiple random colors per firework.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> enableMultiExplosions = sgFirework.add(new BoolSetting.Builder()
            .name("multi-explosions")
            .description("Enable multiple explosions per firework.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> multiExplosions = sgFirework.add(new IntSetting.Builder()
            .name("explosions-per-firework")
            .description("Number of explosions per firework.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 12)
            .visible(enableMultiExplosions::get)
            .build());

    private final Setting<ShowMode> showMode = sgShow.add(new EnumSetting.Builder<ShowMode>()
            .name("show-mode")
            .description("Pattern for the firework show.")
            .defaultValue(ShowMode.Random)
            .build());

    private final Setting<Integer> omegaAbuseExplosions = sgShow.add(new IntSetting.Builder()
            .name("omega-explosions")
            .description("Minimum explosions per firework in Omega mode.")
            .defaultValue(8)
            .min(1)
            .sliderRange(1, 30)
            .visible(this::isOmegaMode)
            .build());

    private final Setting<Integer> omegaExtraCommands = sgShow.add(new IntSetting.Builder()
            .name("omega-extra-commands")
            .description("Extra launches per tick in Omega mode.")
            .defaultValue(3)
            .min(0)
            .sliderRange(0, 20)
            .visible(this::isOmegaMode)
            .build());

    private final Setting<OmegaPlusDelivery> omegaPlusDelivery = sgShow.add(new EnumSetting.Builder<OmegaPlusDelivery>()
            .name("omega-plus-delivery")
            .description("How Omega+ injects long commands.")
            .defaultValue(OmegaPlusDelivery.CommandBlock)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusExplosions = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-explosions")
            .description("Explosions per rocket in Omega+ before clamping.")
            .defaultValue(90)
            .min(1)
            .sliderRange(1, 1200)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusColors = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-colors")
            .description("Colors per explosion in Omega+.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 10)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusClones = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-clones")
            .description("How many command blocks are spawned per Omega+ launch.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 4)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusCloneSpacing = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-clone-spacing")
            .description("Horizontal spacing between Omega+ command block clones.")
            .defaultValue(2)
            .min(1)
            .sliderRange(1, 8)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusPlaceDistance = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-place-distance")
            .description("Distance from player to place Omega+ command blocks.")
            .defaultValue(2)
            .min(1)
            .sliderRange(1, 12)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusMaxLaunchesPerTick = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-max-launches-per-tick")
            .description("Hard safety cap for Omega+ launches per tick.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 4)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusPlaceCooldownMs = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-place-cooldown-ms")
            .description("Minimum milliseconds between Omega+ command block placements.")
            .defaultValue(250)
            .min(0)
            .sliderRange(0, 500)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusMaxActiveBlocks = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-max-active-blocks")
            .description("Max active Omega+ command blocks waiting for cleanup.")
            .defaultValue(8)
            .min(1)
            .sliderRange(1, 128)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Integer> omegaPlusCommandLifetimeTicks = sgShow.add(new IntSetting.Builder()
            .name("omega-plus-command-lifetime")
            .description("Ticks before auto-removing Omega+ command blocks.")
            .defaultValue(6)
            .min(1)
            .sliderRange(1, 80)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Boolean> omegaPlusTrackOutput = sgShow.add(new BoolSetting.Builder()
            .name("omega-plus-track-output")
            .description("Track command output on Omega+ command blocks.")
            .defaultValue(false)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Boolean> omegaPlusUseCreativeGive = sgShow.add(new BoolSetting.Builder()
            .name("omega-plus-creative-give")
            .description("Also inject command block item into hotbar via creative packet.")
            .defaultValue(false)
            .visible(this::isOmegaPlusMode)
            .build());

    private final Setting<Boolean> finaleMode = sgShow.add(new BoolSetting.Builder()
            .name("finale-burst")
            .description("Launch a rapid burst at the end of the show.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> finaleCount = sgShow.add(new IntSetting.Builder()
            .name("finale-count")
            .description("Fireworks in the finale burst.")
            .defaultValue(20)
            .min(5)
            .sliderRange(5, 100)
            .visible(finaleMode::get)
            .build());

    private final Random random = new Random();
    private int launchedCount = 0;
    private int tickCounter = 0;
    private int currentTick = 0;
    private boolean inFinale = false;
    private int finaleRemaining = 0;
    private int finaleIndex = 0;
    private int omegaPlusBudgetLeft = -1;
    private long omegaLastPlacementAtMs = 0L;
    private ResourceKey<Level> lastDimension = null;
    private final Map<BlockPos, Integer> omegaCleanupTicks = new HashMap<>();
    private final Map<BlockPos, Integer> omegaRedstoneCleanupTicks = new HashMap<>();

    private static final int[] FIREWORK_COLORS = {
            16711680,
            16744448,
            16776960,
            65280,
            65535,
            255,
            16711935,
            16777215,
            16738740,
            8388736,
            4312372,
            11743532,
    };

    public FireworkShow() {
        super("firework-show",
                "Firework shows with shapes and colors.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        launchedCount = 0;
        tickCounter = 0;
        currentTick = 0;
        inFinale = false;
        finaleRemaining = 0;
        finaleIndex = 0;
        omegaPlusBudgetLeft = -1;
        omegaLastPlacementAtMs = 0L;
        lastDimension = null;
        omegaCleanupTicks.clear();
        omegaRedstoneCleanupTicks.clear();
        if (isOmegaPlusMode()) {
            int requested = totalFireworks.get() > 0 ? totalFireworks.get() : OMEGA_PLUS_FALLBACK_BUDGET;
            omegaPlusBudgetLeft = Math.min(requested, OMEGA_PLUS_HARD_MAX_BUDGET);
            info("Omega+ safe budget: " + omegaPlusBudgetLeft + " launches.");
        }
        info("Firework Show started! Mode: " + showMode.get());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        omegaCleanupTicks.clear();
        omegaRedstoneCleanupTicks.clear();
        omegaLastPlacementAtMs = 0L;
        lastDimension = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null)
            return;

        if (mc.level != null) {
            ResourceKey<Level> dimension = mc.level.dimension();
            if (!dimension.equals(lastDimension)) {
                lastDimension = dimension;
                omegaCleanupTicks.clear();
                omegaRedstoneCleanupTicks.clear();
                omegaLastPlacementAtMs = 0L;
            }
        }

        currentTick++;
        processOmegaCleanup();
        tickCounter++;

        if (totalFireworks.get() > 0 && launchedCount >= totalFireworks.get()) {
            if (finaleMode.get() && !inFinale) {
                inFinale = true;
                info("FINALE!!!");
                int finaleLaunches = finaleCount.get();
                if (isOmegaPlusMode()) {
                    finaleLaunches = Math.min(finaleLaunches + omegaExtraCommands.get() * 2 + omegaPlusClones.get() * 2, 120);
                } else if (isOmegaMode()) {
                    finaleLaunches = Math.min(finaleLaunches + omegaExtraCommands.get() * 2, 300);
                }
                finaleRemaining = finaleLaunches;
            }

            if (!inFinale || finaleRemaining <= 0) {
                info("Firework Show complete! Launched " + launchedCount + " fireworks.");
                toggle();
                return;
            }
        }

        if (tickCounter < delayTicks.get() && !inFinale)
            return;
        tickCounter = 0;

        int launchesPerTick = commandsPerTick.get();
        if (isOmegaPlusMode()) {
            launchesPerTick = Math.min(1, omegaPlusMaxLaunchesPerTick.get());
        } else if (isOmegaMode()) {
            launchesPerTick = Math.min(launchesPerTick + omegaExtraCommands.get(), 80);
        }
        if (inFinale && finaleRemaining < launchesPerTick)
            launchesPerTick = finaleRemaining;

        for (int i = 0; i < launchesPerTick; i++) {
            if (isOmegaPlusMode()) {
                if (omegaPlusBudgetLeft == 0) {
                    info("Omega+ budget exhausted. Disabling to prevent freezes.");
                    toggle();
                    return;
                }
            }
            if (!inFinale && totalFireworks.get() > 0 && launchedCount >= totalFireworks.get()) break;
            if (!launchFirework(inFinale ? finaleIndex : launchedCount)) break;
            if (isOmegaPlusMode() && omegaPlusBudgetLeft > 0) omegaPlusBudgetLeft--;
            if (inFinale) {
                finaleIndex++;
                finaleRemaining--;
            } else {
                launchedCount++;
            }
        }
    }

    private boolean launchFirework(int index) {
        if (mc.player == null || mc.player.connection == null) return false;

        double x;
        double y;
        double z;

        switch (showMode.get()) {
            case Spiral -> {
                double angle = index * 0.3;
                double r = (index % 20) * 0.5;
                x = mc.player.getX() + Math.cos(angle) * r;
                y = mc.player.getY() + 1.0;
                z = mc.player.getZ() + Math.sin(angle) * r;
            }
            case Wave -> {
                x = mc.player.getX() + (index % 20 - 10) * 1.5;
                y = mc.player.getY() + 1.0;
                z = mc.player.getZ() + Math.sin(index * 0.5) * spreadRadius.get() * 0.5;
            }
            case Circle -> {
                int total = totalFireworks.get() > 0 ? totalFireworks.get() : 50;
                double angle = (2 * Math.PI / total) * index;
                x = mc.player.getX() + Math.cos(angle) * spreadRadius.get();
                y = mc.player.getY() + 1.0;
                z = mc.player.getZ() + Math.sin(angle) * spreadRadius.get();
            }
            case Omega -> {
                double base = Math.max(2.0, spreadRadius.get() * 0.3);
                double angle = index * 0.95;
                double r = base + (index % 10) * 0.75;
                x = mc.player.getX() + Math.cos(angle) * r + (random.nextDouble() - 0.5) * 1.5;
                y = mc.player.getY() + 1.0 + (index % 4) * 0.35;
                z = mc.player.getZ() + Math.sin(angle) * r + (random.nextDouble() - 0.5) * 1.5;
            }
            case OmegaPlus -> {
                double base = Math.max(2.0, spreadRadius.get() * 0.4);
                double angle = index * 1.15;
                double r = base + (index % 14) * 0.95;
                x = mc.player.getX() + Math.cos(angle) * r + (random.nextDouble() - 0.5) * 2.0;
                y = mc.player.getY() + 1.2 + (index % 5) * 0.4;
                z = mc.player.getZ() + Math.sin(angle) * r + (random.nextDouble() - 0.5) * 2.0;
            }
            default -> {
                x = mc.player.getX() + (random.nextDouble() * 2 - 1) * spreadRadius.get();
                y = mc.player.getY() + 1.0;
                z = mc.player.getZ() + (random.nextDouble() * 2 - 1) * spreadRadius.get();
            }
        }

        if (isOmegaPlusMode()) {
            if (!mc.player.getAbilities().instabuild || mc.level == null || mc.gameMode == null) {
                warning("Omega+ requires Creative mode and interaction manager.");
                toggle();
                return false;
            }
            String cmd = buildOmegaPlusSummonCommand(x, y, z);
            return launchOmegaPlusCommand(cmd, index);
        }

        String cmd = buildFireworkSummonCommand(x, y, z);
        mc.player.connection.sendCommand(CommandUtils.vanilla(cmd));
        return true;
    }

    private String buildFireworkSummonCommand(double x, double y, double z) {
        int explosionCount = enableMultiExplosions.get() ? Math.max(1, multiExplosions.get()) : 1;
        if (isOmegaMode()) explosionCount = Math.max(explosionCount, omegaAbuseExplosions.get());

        int colorsPerExplosion = multiColor.get() ? 3 : 1;
        if (isOmegaMode()) colorsPerExplosion = multiColor.get() ? 4 : 2;

        boolean includeFadeColors = true;
        boolean includeTrail = trail.get();
        boolean includeTwinkle = twinkle.get();
        if (isOmegaMode()) {
            includeTrail = true;
            includeTwinkle = true;
        }

        while (true) {
            String entityNbt = buildFireworkEntityNBT(
                    explosionCount,
                    colorsPerExplosion,
                    includeFadeColors,
                    includeTrail,
                    includeTwinkle);
            String cmd = CommandUtils.formatCommand(
                    "summon minecraft:firework_rocket %.2f %.2f %.2f %s",
                    x,
                    y,
                    z,
                    entityNbt);

            if (cmd.length() <= MAX_CHAT_COMMAND_LENGTH) return cmd;

            if (explosionCount > 1) {
                explosionCount--;
                continue;
            }
            if (colorsPerExplosion > 1) {
                colorsPerExplosion--;
                continue;
            }
            if (includeFadeColors) {
                includeFadeColors = false;
                continue;
            }
            if (includeTrail || includeTwinkle) {
                includeTrail = false;
                includeTwinkle = false;
                continue;
            }

            String fallbackNbt = buildFireworkEntityNBT(1, 1, false, false, false);
            String fallback = CommandUtils.formatCommand("summon minecraft:firework_rocket %.2f %.2f %.2f %s",
                    x,
                    y,
                    z,
                    fallbackNbt);
            if (fallback.length() <= MAX_CHAT_COMMAND_LENGTH) return fallback;
            return CommandUtils.formatCommand("summon minecraft:firework_rocket %.2f %.2f %.2f",
                    x,
                    y,
                    z);
        }
    }

    private String buildOmegaPlusSummonCommand(double x, double y, double z) {
        int explosionCount = Math.max(1, omegaPlusExplosions.get());
        if (enableMultiExplosions.get()) explosionCount = Math.max(explosionCount, multiExplosions.get());
        explosionCount = Math.max(explosionCount, omegaAbuseExplosions.get());
        explosionCount = Math.min(explosionCount, OMEGA_PLUS_HARD_MAX_EXPLOSIONS);

        int colorsPerExplosion = Math.max(1, omegaPlusColors.get());
        if (multiColor.get()) colorsPerExplosion = Math.max(colorsPerExplosion, 3);
        colorsPerExplosion = Math.min(colorsPerExplosion, OMEGA_PLUS_HARD_MAX_COLORS);

        while (true) {
            String entityNbt = buildFireworkEntityNBT(explosionCount, colorsPerExplosion, true, true, true);
            String cmd = CommandUtils.formatCommand("summon minecraft:firework_rocket %.2f %.2f %.2f %s", x, y, z, entityNbt);
            if (cmd.length() <= MAX_COMMAND_BLOCK_COMMAND_LENGTH) return cmd;
            if (explosionCount > 1) {
                explosionCount--;
                continue;
            }
            if (colorsPerExplosion > 1) {
                colorsPerExplosion--;
                continue;
            }
            return CommandUtils.formatCommand("summon minecraft:firework_rocket %.2f %.2f %.2f", x, y, z);
        }
    }

    private boolean launchOmegaPlusCommand(String command, int index) {
        if (mc.player == null || mc.player.connection == null || mc.level == null) return false;

        if (!canPlaceOmegaPlusBlockNow()) return false;

        BlockPos playerPos = mc.player.blockPosition();
        Direction facing = mc.player.getDirection();
        Direction right = facing.getClockWise();
        int distance = omegaPlusPlaceDistance.get() + (index % 3);
        BlockPos base = playerPos.offset(facing.getStepX() * distance, facing.getStepY() * distance, facing.getStepZ() * distance).above(1 + (index % 2));
        int clones = Math.min(Math.max(1, omegaPlusClones.get()), OMEGA_PLUS_HARD_MAX_CLONES);
        int spacing = Math.max(1, omegaPlusCloneSpacing.get());
        Set<BlockPos> used = new HashSet<>();
        boolean sent = false;

        OmegaPlusDelivery mode = omegaPlusDelivery.get();
        boolean creativeGive = omegaPlusUseCreativeGive.get() || mode == OmegaPlusDelivery.CreativeGiveAndPlace || mode == OmegaPlusDelivery.Both;

        for (int c = 0; c < clones; c++) {
            if (!canPlaceOmegaPlusBlockNow()) break;

            double angle = (Math.PI * 2.0 * c) / clones + (index * 0.37);
            int ox = (int) Math.round(Math.cos(angle) * spacing);
            int oz = (int) Math.round(Math.sin(angle) * spacing);
            int oy = c % 3;
            BlockPos cloneBase = base.offset(ox, oy, oz);
            if (!used.add(cloneBase)) continue;

            if (creativeGive) giveCommandBlockToHotbar();

            BlockPos placePos = mode == OmegaPlusDelivery.Both ? cloneBase.offset(right.getStepX(), right.getStepY(), right.getStepZ()) : cloneBase;
            placeAndProgramCommandBlockViaSetblock(placePos, command, facing);
            markOmegaPlacement();
            sent = true;
        }
        return sent;
    }

    private void placeAndProgramCommandBlockViaSetblock(BlockPos pos, String command, Direction facing) {
        if (mc.player == null || mc.player.connection == null) return;

        String setblock = CommandUtils.formatCommand(
                "setblock %d %d %d minecraft:command_block[facing=%s]",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                facing.getSerializedName());
        if (setblock.length() <= MAX_CHAT_COMMAND_LENGTH) mc.player.connection.sendCommand(CommandUtils.vanilla(setblock));

        mc.player.connection.send(new ServerboundSetCommandBlockPacket(
                pos,
                command,
                CommandBlockEntity.Mode.REDSTONE,
                omegaPlusTrackOutput.get(),
                false,
                false));

        BlockPos powerPos = pos.above();
        String power = CommandUtils.formatCommand("setblock %d %d %d minecraft:redstone_block",
                powerPos.getX(),
                powerPos.getY(),
                powerPos.getZ());
        if (power.length() <= MAX_CHAT_COMMAND_LENGTH) mc.player.connection.sendCommand(CommandUtils.vanilla(power));

        queueOmegaCleanup(pos, powerPos);
    }

    private void placeAndProgramCommandBlockViaCreative(BlockPos pos, String command, Direction facing) {
        giveCommandBlockToHotbar();
        placeAndProgramCommandBlockViaSetblock(pos, command, facing);
    }

    private void giveCommandBlockToHotbar() {
        if (mc.player == null || mc.player.connection == null) return;
        if (!(mc.player.containerMenu instanceof InventoryMenu)) return;

        int selected = mc.player.getInventory().getSelectedSlot();
        int targetSlot = selected;
        ItemStack selectedStack = mc.player.getInventory().getItem(selected);
        if (!selectedStack.isEmpty() && !selectedStack.is(Items.COMMAND_BLOCK)) {
            FindItemResult empty = InvUtils.find(ItemStack::isEmpty, 0, 8);
            if (!empty.found()) return;
            targetSlot = empty.slot();
        }

        ItemStack stack = new ItemStack(Items.COMMAND_BLOCK);
        int packetSlot = 36 + targetSlot;
        mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(packetSlot, stack));
        mc.player.containerMenu.getSlot(packetSlot).set(stack.copy());
    }

    private boolean canPlaceOmegaPlusBlockNow() {
        if (omegaCleanupTicks.size() >= omegaPlusMaxActiveBlocks.get()) return false;
        long now = System.currentTimeMillis();
        return now - omegaLastPlacementAtMs >= omegaPlusPlaceCooldownMs.get();
    }

    private void markOmegaPlacement() {
        omegaLastPlacementAtMs = System.currentTimeMillis();
    }

    private void queueOmegaCleanup(BlockPos commandPos, BlockPos powerPos) {
        omegaCleanupTicks.put(commandPos, currentTick + omegaPlusCommandLifetimeTicks.get());
        omegaRedstoneCleanupTicks.put(powerPos, currentTick + 2);
    }

    private void processOmegaCleanup() {
        if (mc.player == null || mc.player.connection == null) return;

        Iterator<Map.Entry<BlockPos, Integer>> redstoneIt = omegaRedstoneCleanupTicks.entrySet().iterator();
        while (redstoneIt.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = redstoneIt.next();
            if (currentTick < entry.getValue()) continue;
            sendSetblockAir(entry.getKey());
            redstoneIt.remove();
        }

        Iterator<Map.Entry<BlockPos, Integer>> cmdIt = omegaCleanupTicks.entrySet().iterator();
        while (cmdIt.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = cmdIt.next();
            if (currentTick < entry.getValue()) continue;
            sendSetblockAir(entry.getKey());
            cmdIt.remove();
        }
    }

    private void sendSetblockAir(BlockPos pos) {
        if (mc.player == null || mc.player.connection == null) return;
        String clear = CommandUtils.formatCommand("setblock %d %d %d minecraft:air",
                pos.getX(),
                pos.getY(),
                pos.getZ());
        if (clear.length() <= MAX_CHAT_COMMAND_LENGTH) mc.player.connection.sendCommand(CommandUtils.vanilla(clear));
    }

    private String buildFireworkEntityNBT(int explosionCount, int colorsPerExplosion, boolean includeFadeColors,
            boolean includeTrail, boolean includeTwinkle) {
        StringBuilder sb = new StringBuilder();
        sb.append("{LifeTime:").append(18 + random.nextInt(18));
        sb.append(",FireworksItem:{id:\"minecraft:firework_rocket\",count:1,components:{\"minecraft:fireworks\":{");
        sb.append("flight_duration:").append(flight.get());
        sb.append(",explosions:[");

        for (int e = 0; e < explosionCount; e++) {
            if (e > 0) sb.append(',');
            sb.append('{');
            sb.append("shape:\"").append(getShapeName()).append('\"');

            sb.append(",colors:[I;");
            for (int c = 0; c < Math.max(1, colorsPerExplosion); c++) {
                if (c > 0) sb.append(',');
                sb.append(FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.length)]);
            }
            sb.append(']');

            if (includeFadeColors) {
                sb.append(",fade_colors:[I;");
                sb.append(FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.length)]);
                sb.append(']');
            }

            if (includeTrail) sb.append(",has_trail:1b");
            if (includeTwinkle) sb.append(",has_twinkle:1b");

            sb.append('}');
        }

        sb.append("]}}}}");
        return sb.toString();
    }

    private String getShapeName() {
        FireworkShape selected = shape.get();
        if (selected == FireworkShape.Random) {
            selected = switch (random.nextInt(5)) {
                case 0 -> FireworkShape.SmallBall;
                case 1 -> FireworkShape.LargeBall;
                case 2 -> FireworkShape.Star;
                case 3 -> FireworkShape.Creeper;
                default -> FireworkShape.Burst;
            };
        }

        return switch (selected) {
            case SmallBall -> "small_ball";
            case LargeBall -> "large_ball";
            case Star -> "star";
            case Creeper -> "creeper";
            case Burst -> "burst";
            case Random -> "small_ball";
        };
    }

    @Override
    public void onDeactivate() {
        processOmegaCleanup();
        for (BlockPos pos : omegaRedstoneCleanupTicks.keySet()) sendSetblockAir(pos);
        for (BlockPos pos : omegaCleanupTicks.keySet()) sendSetblockAir(pos);
        omegaRedstoneCleanupTicks.clear();
        omegaCleanupTicks.clear();
        if (launchedCount > 0) info("Show ended. Total launched: " + launchedCount);
    }

    public enum FireworkShape {
        Random,
        SmallBall,
        LargeBall,
        Star,
        Creeper,
        Burst
    }

    public enum ShowMode {
        Random,
        Spiral,
        Wave,
        Circle,
        Omega,
        OmegaPlus
    }

    public enum OmegaPlusDelivery {
        CommandBlock,
        CreativeGiveAndPlace,
        Both
    }

    private boolean isOmegaMode() {
        ShowMode mode = showMode.get();
        return mode == ShowMode.Omega || mode == ShowMode.OmegaPlus;
    }

    private boolean isOmegaPlusMode() {
        return showMode.get() == ShowMode.OmegaPlus;
    }
}
