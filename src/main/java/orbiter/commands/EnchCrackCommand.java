package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import orbiter.modules.misc.EnchCracker;

public class EnchCrackCommand extends Command {
    public EnchCrackCommand() {
        this("enchantmentcracker");
    }

    protected EnchCrackCommand(String name) {
        super(name,
                "Cracks the enchanting seed and predicts every table row. Usage: ." + name + " [status|reset|fullscan|target <name>]");
        cmdName = name;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        register(builder);
    }

    void register(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            EnchCracker module = module();
            if (module == null) return SINGLE_SUCCESS;

            if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
                error("Open an enchanting table first.");
                return SINGLE_SUCCESS;
            }

            ItemStack item = menu.getSlot(0).getItem();
            if (item.isEmpty()) {
                error("The table has no item in it.");
                return SINGLE_SUCCESS;
            }

            info("Synced seed " + menu.getEnchantmentSeed() + " | lapis: " + menu.getGoldCount());
            if (!module.reportOffers(item)) {
                info("Not cracked yet. 1) Right-click the table once. 2) Put the item in and keep it still for ~1 second. 3) Check .encc status.");
            }
            return SINGLE_SUCCESS;
        });

        builder.then(literal("status").executes(context -> {
            EnchCracker module = module();
            if (module != null) info("Cracker state: " + module.getInfoString());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("reset").executes(context -> {
            EnchCracker module = module();
            if (module != null) {
                module.forceReset();
                info("Cracker state cleared. It will re-crack on the next stable table reading.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("fullscan").executes(context -> {
            EnchCracker module = module();
            if (module == null) return SINGLE_SUCCESS;
            if (module.requestFullScan()) {
                info("Full 2^32 scan started. Expect several minutes of CPU use; .encc reset or toggling the module cancels it.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("target")
                .executes(context -> {
                    EnchCracker module = module();
                    if (module != null) {
                        module.setTarget("");
                        info("Target enchantment cleared.");
                    }
                    return SINGLE_SUCCESS;
                })
                .then(argument("name", StringArgumentType.greedyString()).executes(context -> {
                    EnchCracker module = module();
                    if (module == null) return SINGLE_SUCCESS;
                    String name = StringArgumentType.getString(context, "name");
                    module.setTarget(name);
                    info("Target enchantment set to " + name.trim() + ".");
                    if (mc.player != null && mc.player.containerMenu instanceof EnchantmentMenu menu) {
                        ItemStack item = menu.getSlot(0).getItem();
                        if (!item.isEmpty()) module.reportOffers(item);
                    }
                    return SINGLE_SUCCESS;
                })));
    }

    private final String cmdName;

    private EnchCracker module() {
        EnchCracker module = Modules.get().get(EnchCracker.class);
        if (module == null) {
            error("Enchantment Cracker module missing.");
            return null;
        }
        if (!module.isActive()) {
            error("Enable the Enchantment Cracker module first.");
            return null;
        }
        return module;
    }
}
