package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class CloseKPInvCommand extends Command {

    private static final java.util.Map<String, Integer> SLOT_MAP = new java.util.LinkedHashMap<>();
    static {
        SLOT_MAP.put("craft1", 0);
        SLOT_MAP.put("craft2", 1);
        SLOT_MAP.put("craft3", 2);
        SLOT_MAP.put("craft4", 3);
        SLOT_MAP.put("craft5", 4);
        SLOT_MAP.put("helmet", 5);
        SLOT_MAP.put("chestplate", 6);
        SLOT_MAP.put("chest", 6);
        SLOT_MAP.put("leggings", 7);
        SLOT_MAP.put("legs", 7);
        SLOT_MAP.put("boots", 8);
        SLOT_MAP.put("offhand", 45);
        SLOT_MAP.put("all", -1);
    }

    public CloseKPInvCommand() {
        super("ckp", "Close Keep Inventory — stash held item into XCarry slots.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("§eCloseKPInv Slots:");
            info("§7Craft: craft1, craft2, craft3, craft4, craft5");
            info("§7Armor: helmet, chestplate, leggings, boots");
            info("§7Other: offhand, all");
            info("§7Usage: §f.ckp <slot> §7— stashes held item into that slot");
            info("§7Usage: §f.ckp all §7— stash into all configured slots");
            return SINGLE_SUCCESS;
        });

        builder.then(argument("slot", StringArgumentType.word())
            .executes(context -> {
                String slotName = StringArgumentType.getString(context, "slot").toLowerCase();
                stashItem(slotName);
                return SINGLE_SUCCESS;
            }));
    }

    private void stashItem(String slotName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) {
            error("Not in a world.");
            return;
        }

        ItemStack held = player.getMainHandStack().copy();
        if (held.isEmpty()) {
            error("Hold an item first.");
            return;
        }

        ScreenHandler menu = player.currentScreenHandler;
        if (menu != player.playerScreenHandler) {
            error("Open your inventory first (E), then run the command.");
            return;
        }

        String itemName = held.getName().getString();

        if (slotName.equals("all")) {
            int stashed = 0;
            orbiter.modules.player.CloseKPInv mod = orbiter.modules.player.CloseKPInv.get();
            if (mod != null && mod.isActive()) {
                if (mod.shouldKeepCrafting()) {
                    for (int i = 0; i <= 4; i++) { if (stashInto(menu, i)) stashed++; }
                }
                if (mod.shouldKeepArmor()) {
                    for (int i = 5; i <= 8; i++) { if (stashInto(menu, i)) stashed++; }
                }
                if (mod.shouldKeepOffhand()) {
                    if (stashInto(menu, 45)) stashed++;
                }
            } else {

                for (int i = 0; i <= 4; i++) { if (stashInto(menu, i)) stashed++; }
                for (int i = 5; i <= 8; i++) { if (stashInto(menu, i)) stashed++; }
                if (stashInto(menu, 45)) stashed++;
            }
            info("§aStashed §f" + itemName + " §ainto " + stashed + " slots.");
            return;
        }

        Integer slotIdx = SLOT_MAP.get(slotName);
        if (slotIdx == null) {
            error("Unknown slot: " + slotName + ". Valid: craft1-5, helmet, chestplate, leggings, boots, offhand, all");
            return;
        }

        if (stashInto(menu, slotIdx)) {
            info("§aStashed §f" + itemName + " §ainto §f" + slotName + "§a.");
        } else {
            error("Could not place into " + slotName + " — slot may be restricted.");
        }
    }

    private boolean stashInto(ScreenHandler menu, int slotIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) return false;

        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return false;

        int selectedSlot = player.getInventory().getSelectedSlot();
        int hotbarSlotId = 36 + selectedSlot;

        mc.interactionManager.clickSlot(menu.syncId, hotbarSlotId, 0,
            net.minecraft.screen.slot.SlotActionType.PICKUP, player);

        ItemStack carried = menu.getCursorStack();
        if (carried.isEmpty()) return false;

        mc.interactionManager.clickSlot(menu.syncId, slotIndex, 0,
            net.minecraft.screen.slot.SlotActionType.PICKUP, player);

        ItemStack slotStack = menu.getSlot(slotIndex).getStack();
        if (!slotStack.isEmpty()) return true;

        mc.interactionManager.clickSlot(menu.syncId, hotbarSlotId, 0,
            net.minecraft.screen.slot.SlotActionType.PICKUP, player);
        return false;
    }
}
