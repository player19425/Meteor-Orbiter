package orbiter.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import orbiter.Orbiter;
import orbiter.systems.combat.CombatEngine;

public class AutoTotemPlus extends Module {
    public enum TotemPriority {
        LowestSlot,
        HighestSlot,
        ClosestToHotbar
    }

    private enum Phase {
        Idle,
        Reaction,
        Pick,
        Place,
        Settle,
        Close
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> emergencyOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("emergency-only")
        .description("Only swap when your effective health drops to the threshold. Off keeps a totem ready at all times.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Swap to a totem when effective health drops to this value. Used on its own or with emergency-only.")
        .defaultValue(10.0)
        .min(1.0)
        .max(20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Boolean> smartHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-health")
        .description("Include incoming damage like crystals, falls and explosions when checking the threshold.")
        .defaultValue(false)
        .build()
    );

    private final Setting<TotemPriority> totemPriority = sgGeneral.add(new EnumSetting.Builder<TotemPriority>()
        .name("totem-priority")
        .description("Which totem slot gets picked when several are available.")
        .defaultValue(TotemPriority.LowestSlot)
        .build()
    );

    private final Setting<Boolean> sendClosePacket = sgTiming.add(new BoolSetting.Builder()
        .name("send-close-packet")
        .description("Send a container close packet at the end of the session, like closing your inventory by hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> reaction = sgTiming.add(new IntSetting.Builder()
        .name("reaction")
        .description("Ticks before the first click after a swap is decided.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> clickDelay = sgTiming.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks between the pick-up and put-down clicks.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> confirmWait = sgTiming.add(new IntSetting.Builder()
        .name("confirm-wait")
        .description("Ticks to wait for the server to confirm the totem before retrying.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> settle = sgTiming.add(new IntSetting.Builder()
        .name("settle")
        .description("Ticks to stay still after the totem lands in the offhand.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> closeDelay = sgTiming.add(new IntSetting.Builder()
        .name("close-delay")
        .description("Ticks between settle and closing the session.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> spacing = sgTiming.add(new IntSetting.Builder()
        .name("spacing")
        .description("Minimum ticks between two swap sessions.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final InputFreezer freezer = new InputFreezer();

    private Phase phase = Phase.Idle;
    private int phaseTicks;
    private int totemSlot = -1;
    private int retries;
    private long lastSessionEnd;

    public AutoTotemPlus() {
        super(Orbiter.CATEGORY_WIP, "auto-totem-plus", "Keeps a totem in your offhand using a real inventory session while you stand completely still.");
    }

    @Override
    public void onActivate() {
        phase = Phase.Idle;
        phaseTicks = 0;
        totemSlot = -1;
        retries = 0;
        lastSessionEnd = 0;

        AutoTotem builtin = Modules.get().get(AutoTotem.class);
        if (builtin != null && builtin.isActive()) {
            builtin.toggle();
            warning("Meteor's built-in AutoTotem was disabled to avoid fighting over the offhand.");
        }
    }

    @Override
    public void onDeactivate() {
        returnCarriedTotem();
        lastSessionEnd = 0;
        releaseFreeze();
        phase = Phase.Idle;
        phaseTicks = 0;
        totemSlot = -1;
        retries = 0;
    }

    @Override
    public String getInfoString() {
        return phase == Phase.Idle ? "idle" : "swap";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            abortSession(false);
            return;
        }

        if (phase != Phase.Idle) {
            if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
                abortSession(true);
                return;
            }
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                abortSession(true);
                return;
            }
            if (mc.player.isDeadOrDying()) {
                abortSession(false);
                return;
            }
            freezeInputs();
        }

        if (phase == Phase.Idle) {
            if (lastSessionEnd != 0 && mc.level.getGameTime() - lastSessionEnd < spacing.get()) return;
            if (!shouldSwap()) return;
            freezeInputs();
            enter(Phase.Reaction);
            return;
        }

        phaseTicks++;

        switch (phase) {
            case Reaction -> {
                if (phaseTicks >= reaction.get()) {
                    totemSlot = findTotemSlot();
                    if (totemSlot == -1) {
                        abortSession(false);
                        return;
                    }
                    click(totemSlot);
                    enter(Phase.Pick);
                }
            }
            case Pick -> {
                if (phaseTicks >= clickDelay.get()) {
                    if (carriedTotem()) {
                        click(InventoryMenu.SHIELD_SLOT);
                        enter(Phase.Place);
                    } else if (offhandTotem()) {
                        enter(Phase.Settle);
                    } else {
                        retryOrAbort();
                    }
                }
            }
            case Place -> {
                if (offhandTotem()) {
                    enter(Phase.Settle);
                } else if (phaseTicks >= confirmWait.get()) {
                    if (carriedTotem()) {
                        click(InventoryMenu.SHIELD_SLOT);
                        retries++;
                        if (retries > 2) {
                            abortSession(true);
                        } else {
                            enter(Phase.Place);
                        }
                    } else {
                        retryOrAbort();
                    }
                }
            }
            case Settle -> {
                if (phaseTicks >= settle.get()) enter(Phase.Close);
            }
            case Close -> {
                if (phaseTicks >= closeDelay.get()) finishSession();
            }
        }
    }

    private boolean shouldSwap() {
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return false;
        if (mc.player.containerMenu != mc.player.inventoryMenu) return false;
        if (offhandTotem()) return false;

        if (emergencyOnly.get()) {
            double effective = PlayerUtils.getTotalHealth();
            if (smartHealth.get()) effective -= PlayerUtils.possibleHealthReductions(true, true);
            if (effective > healthThreshold.get()) return false;
        }

        return findTotemSlot() != -1;
    }

    private boolean offhandTotem() {
        return mc.player.inventoryMenu.getSlot(InventoryMenu.SHIELD_SLOT).getItem().is(Items.TOTEM_OF_UNDYING);
    }

    private boolean carriedTotem() {
        return mc.player.inventoryMenu.getCarried().is(Items.TOTEM_OF_UNDYING);
    }

    private int findTotemSlot() {
        int best = -1;
        long bestScore = Long.MAX_VALUE;

        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            if (!mc.player.inventoryMenu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) continue;

            long score = switch (totemPriority.get()) {
                case LowestSlot -> i;
                case HighestSlot -> -i;
                case ClosestToHotbar -> Math.abs(i - InventoryMenu.USE_ROW_SLOT_START);
            };

            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }

        return best;
    }

    private void click(int slot) {
        mc.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, slot, 0, ContainerInput.PICKUP, mc.player);
    }

    private void retryOrAbort() {
        retries++;
        if (retries > 2) abortSession(true);
        else enter(Phase.Reaction);
    }

    private void abortSession(boolean warn) {
        returnCarriedTotem();
        releaseFreeze();
        lastSessionEnd = mc.level == null ? 0 : mc.level.getGameTime();
        phase = Phase.Idle;
        phaseTicks = 0;
        totemSlot = -1;
        retries = 0;
        if (warn) warning("AutoTotem+ could not complete the swap");
    }

    private void returnCarriedTotem() {
        if (mc.player == null || !carriedTotem()) return;
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;
        if (totemSlot != -1) click(totemSlot);
    }

    private void finishSession() {
        returnCarriedTotem();
        if (sendClosePacket.get() && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundContainerClosePacket(InventoryMenu.CONTAINER_ID));
        }
        releaseFreeze();
        lastSessionEnd = mc.level.getGameTime();
        phase = Phase.Idle;
        phaseTicks = 0;
        totemSlot = -1;
        retries = 0;
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
        if (next == Phase.Reaction && !CombatEngine.get().isFrozen()) CombatEngine.get().freeze(this);
    }

    private void releaseFreeze() {
        if (CombatEngine.get().isFrozen()) CombatEngine.get().unfreeze(this);
        freezer.restore();
    }

    private void freezeInputs() {
        freezer.freeze();
        mc.player.setSprinting(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (!CombatEngine.get().isFrozen()) return;
        if (event.packet instanceof ServerboundContainerClickPacket || event.packet instanceof ServerboundContainerClosePacket) return;

        if (event.packet instanceof ServerboundInteractPacket
            || event.packet instanceof ServerboundUseItemPacket
            || event.packet instanceof ServerboundUseItemOnPacket
            || event.packet instanceof ServerboundPlayerActionPacket
            || event.packet instanceof ServerboundSwingPacket) {
            event.cancel();
        }
    }

    private static class InputFreezer {
        private static final KeyMapping[] KEYS = {
            Minecraft.getInstance().options.keyUp,
            Minecraft.getInstance().options.keyDown,
            Minecraft.getInstance().options.keyLeft,
            Minecraft.getInstance().options.keyRight,
            Minecraft.getInstance().options.keyJump,
            Minecraft.getInstance().options.keyShift,
            Minecraft.getInstance().options.keySprint,
            Minecraft.getInstance().options.keyAttack,
            Minecraft.getInstance().options.keyUse,
            Minecraft.getInstance().options.keyDrop,
            Minecraft.getInstance().options.keySwapOffhand,
            Minecraft.getInstance().options.keyInventory
        };

        private final boolean[] snapshot = new boolean[KEYS.length];
        private boolean holding;

        private void freeze() {
            if (!holding) {
                for (int i = 0; i < KEYS.length; i++) {
                    snapshot[i] = KEYS[i].isDown();
                    KEYS[i].setDown(false);
                }
                holding = true;
            } else {
                for (KeyMapping key : KEYS) key.setDown(false);
            }
        }

        private void restore() {
            if (!holding) return;
            for (int i = 0; i < KEYS.length; i++) {
                KEYS[i].setDown(snapshot[i]);
            }
            holding = false;
        }
    }
}
