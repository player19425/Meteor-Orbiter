package orbiter.commands;

import orbiter.modules.misc.ItemStealer;
import orbiter.mixin.HandledScreenAccessor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

import java.util.Set;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ItemStealerCommand extends Command {

    private static final SuggestionProvider<ClientSuggestionProvider> IDS = (context, builder) -> {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod != null) {
            for (String id : mod.listItems()) builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ClientSuggestionProvider> HOTBAR_PRESETS = (context, builder) -> {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod != null) {
            for (String id : mod.listHotbarPresets()) builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ClientSuggestionProvider> SNAPSHOTS = (context, builder) -> {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod != null) {
            for (String id : mod.listSnapshots()) builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ClientSuggestionProvider> PRESET_NAMES = (context, builder) -> {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod != null) {
            for (String name : mod.getPresetNames()) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public ItemStealerCommand() {
        super("itemstealer", "Save / load / manage cloned items. Aliases: .is, .steal", "is", "steal");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {

        builder.then(literal("save").then(argument("id", StringArgumentType.string()).executes(this::save)));
        builder.then(literal("load").then(argument("id", StringArgumentType.string()).suggests(IDS).executes(this::load)));
        builder.then(literal("delete").then(argument("id", StringArgumentType.string()).suggests(IDS).executes(this::delete)));
        builder.then(literal("list").executes(this::listLegacy));
        builder.then(literal("inject").then(argument("id", StringArgumentType.string()).suggests(IDS).executes(this::inject)));

        builder.then(literal("steal-all").executes(this::stealAll));

        builder.then(literal("steal-save").then(argument("name", StringArgumentType.string()).executes(this::stealSave)));

        builder.then(literal("steal-load").then(argument("name", StringArgumentType.string()).suggests(HOTBAR_PRESETS).executes(this::stealLoad)));

        builder.then(literal("steal-list").executes(this::stealList));

        builder.then(literal("steal-dump-trades").executes(this::stealDumpTrades));

        builder.then(literal("steal-range")
            .then(argument("start", IntegerArgumentType.integer(0))
                .then(argument("end", IntegerArgumentType.integer(0))
                    .executes(this::stealRange))));

        builder.then(literal("steal-snapshot")
            .then(argument("id", StringArgumentType.string()).suggests(SNAPSHOTS).executes(this::stealSnapshot)));

        builder.then(literal("steal-preset")
            .then(argument("name", StringArgumentType.string()).suggests(PRESET_NAMES).executes(this::stealPreset)));

        builder.then(literal("steal-hotbar-delete")
            .then(argument("name", StringArgumentType.string()).suggests(HOTBAR_PRESETS).executes(this::stealHotbarDelete)));
    }

    private int save(CommandContext<ClientSuggestionProvider> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        ItemStack toSave = null;
        if (mc.player != null) {

            if (!mc.player.getMainHandItem().isEmpty()) toSave = mc.player.getMainHandItem().copy();

            if (toSave == null && mc.gui.screen() instanceof AbstractContainerScreen<?> handled) {
                Slot slot = ((HandledScreenAccessor) handled).getHoveredSlot();
                if (slot != null && !slot.getItem().isEmpty()) toSave = slot.getItem().copy();
            }

            if (toSave == null) {
                ItemStack lastCloned = mod.getLastClonedItem();
                if (lastCloned != null && !lastCloned.isEmpty()) toSave = lastCloned.copy();
            }

            if (toSave == null && !mc.player.getOffhandItem().isEmpty()) {
                toSave = mc.player.getOffhandItem().copy();
            }
        }

        if (toSave == null || toSave.isEmpty()) {
            warning("No item to save.");
            return 0;
        }

        if (mod.saveItem(id, toSave)) {
            ChatUtils.info("Saved '" + toSave.getItemName().getString() + "' as '" + id + "'.");
            return SINGLE_SUCCESS;
        }
        error("Failed to save item.");
        return 0;
    }

    private int load(CommandContext<ClientSuggestionProvider> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }
        ItemStack stack = mod.loadItem(id);
        if (stack == null || stack.isEmpty()) {
            warning("No saved item with id '" + id + "'.");
            return 0;
        }
        mod.injectClonedIntoInventory(stack);
        ChatUtils.info("Loaded '" + id + "': " + stack.getItemName().getString() + " x" + stack.getCount());
        return SINGLE_SUCCESS;
    }

    private int delete(CommandContext<ClientSuggestionProvider> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }
        if (mod.deleteItem(id)) {
            ChatUtils.info("Deleted '" + id + "'.");
            return SINGLE_SUCCESS;
        }
        warning("Nothing to delete for id '" + id + "'.");
        return 0;
    }

    private int listLegacy(CommandContext<ClientSuggestionProvider> ctx) {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }
        Set<String> ids = mod.listItems();
        if (ids.isEmpty()) {
            ChatUtils.info("No saved items.");
        } else {
            ChatUtils.info("Saved items (" + ids.size() + "):");
            for (String id : ids) {
                ItemStack stack = mod.peekItem(id);
                String name = (stack != null && !stack.isEmpty()) ? stack.getItemName().getString() : "(unreadable)";
                ChatUtils.info("  - " + id + ": " + name);
            }
        }
        return SINGLE_SUCCESS;
    }

    private int inject(CommandContext<ClientSuggestionProvider> ctx) {
        return load(ctx);
    }

    private int stealAll(CommandContext<ClientSuggestionProvider> ctx) {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        if (!mod.isInGui()) {
            warning("No GUI is currently open. Open a container first.");
            return 0;
        }

        int count = mod.stealAllSlots();
        if (count > 0) {
            ChatUtils.info("Stole " + count + " item(s) from the current GUI.");
        } else {
            info("No items to steal in the current GUI (or all filtered out).");
        }
        return SINGLE_SUCCESS;
    }

    private int stealSave(CommandContext<ClientSuggestionProvider> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        if (mc.player == null) {
            warning("Not in-game.");
            return 0;
        }

        if (mod.saveHotbarPreset(name)) {
            ChatUtils.info("Hotbar preset saved as '" + name + "'.");
        } else {
            error("Failed to save hotbar preset.");
        }
        return SINGLE_SUCCESS;
    }

    private int stealLoad(CommandContext<ClientSuggestionProvider> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        if (mc.player == null) {
            warning("Not in-game.");
            return 0;
        }

        if (mod.loadHotbarPreset(name)) {
            ChatUtils.info("Hotbar preset '" + name + "' loaded.");
        } else {
            error("Hotbar preset '" + name + "' not found.");
        }
        return SINGLE_SUCCESS;
    }

    private int stealList(CommandContext<ClientSuggestionProvider> ctx) {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        Set<String> items = mod.listItems();
        if (items.isEmpty()) {
            ChatUtils.info("Saved items: (none)");
        } else {
            ChatUtils.info("Saved items (" + items.size() + "):");
            for (String id : items) {
                ItemStack stack = mod.peekItem(id);
                String name = (stack != null && !stack.isEmpty()) ? stack.getItemName().getString() : "(unreadable)";
                ChatUtils.info("  - " + id + ": " + name);
            }
        }

        Set<String> hotbars = mod.listHotbarPresets();
        if (hotbars.isEmpty()) {
            ChatUtils.info("Hotbar presets: (none)");
        } else {
            ChatUtils.info("Hotbar presets (" + hotbars.size() + "):");
            for (String presetName : hotbars) {
                ChatUtils.info("  - " + presetName);
            }
        }

        Set<String> snapshots = mod.listSnapshots();
        if (snapshots.isEmpty()) {
            ChatUtils.info("Container snapshots: (none)");
        } else {
            ChatUtils.info("Container snapshots (" + snapshots.size() + "):");
            for (String snapId : snapshots) {
                ChatUtils.info("  - " + snapId);
            }
        }

        ChatUtils.info("Creative presets: " + String.join(", ", mod.getPresetNames()));

        return SINGLE_SUCCESS;
    }

    private int stealDumpTrades(CommandContext<ClientSuggestionProvider> ctx) {
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        int count = mod.dumpTrades();
        if (count < 0) {
            warning("No villager trade GUI is currently open.");
            return 0;
        }

        if (count > 0) {
            ChatUtils.info("Dumped " + count + " trade result(s).");
        } else {
            info("No trade results to clone (or all filtered out).");
        }
        return SINGLE_SUCCESS;
    }

    private int stealRange(CommandContext<ClientSuggestionProvider> ctx) {
        int start = IntegerArgumentType.getInteger(ctx, "start");
        int end = IntegerArgumentType.getInteger(ctx, "end");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        if (!mod.isInGui()) {
            warning("No GUI is currently open. Open a container first.");
            return 0;
        }

        if (start > end) {
            warning("Start slot must be <= end slot.");
            return 0;
        }

        int count = mod.stealSlotRange(start, end);
        if (count > 0) {
            ChatUtils.info("Stole " + count + " item(s) from slots " + start + "-" + end + ".");
        } else {
            info("No items to steal in slot range " + start + "-" + end + ".");
        }
        return SINGLE_SUCCESS;
    }

    private int stealSnapshot(CommandContext<ClientSuggestionProvider> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        String description = mod.describeSnapshot(id);
        ChatUtils.info(description);
        return SINGLE_SUCCESS;
    }

    private int stealPreset(CommandContext<ClientSuggestionProvider> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        if (mc.player == null) {
            warning("Not in-game.");
            return 0;
        }

        if (!mc.player.getAbilities().instabuild) {
            warning("Creative mode is required to give preset items.");
            return 0;
        }

        if (mod.givePresetItem(name)) {
            ChatUtils.info("Gave preset item: " + name);
        } else {
            error("Unknown preset: " + name + ". Available: " + String.join(", ", mod.getPresetNames()));
        }
        return SINGLE_SUCCESS;
    }

    private int stealHotbarDelete(CommandContext<ClientSuggestionProvider> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ItemStealer mod = Modules.get().get(ItemStealer.class);
        if (mod == null) { error("ItemStealer not available."); return 0; }

        if (mod.deleteHotbarPreset(name)) {
            ChatUtils.info("Deleted hotbar preset '" + name + "'.");
        } else {
            warning("Hotbar preset '" + name + "' not found.");
        }
        return SINGLE_SUCCESS;
    }
}
