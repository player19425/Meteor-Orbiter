package orbiter.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import orbiter.modules.misc.EnchCracker;

public class EnchCrackCommand extends Command {
    public EnchCrackCommand() {
        this("enchantmentcracker");
    }

    protected EnchCrackCommand(String name) {
        super(name,
                "Full-auto enchantment farmer. Usage: ." + name + " get <enchant> [level] [item], stop, hand, clear");
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

            info("Seed " + menu.getEnchantmentSeed() + " | lapis: " + menu.getGoldCount());
            EnchCracker.Offer[] offers = module.offersFor(menu, item);
            if (offers == null) {
                error("Seed not cracked yet. Keep the item in the table a few seconds until the crack locks, then run this again.");
                return SINGLE_SUCCESS;
            }
            for (EnchCracker.Offer offer : offers) info(EnchCracker.describe(offer));
            return SINGLE_SUCCESS;
        });

        builder.then(literal("get")
                .executes(context -> {
                    error("Usage: ." + cmdName + " get <enchant> [level] [item]");
                    return SINGLE_SUCCESS;
                })
                .then(argument("enchantment", StringArgumentType.word())
                        .executes(context -> startGet(StringArgumentType.getString(context, "enchantment"), -1, null))
                        .then(argument("level", IntegerArgumentType.integer(1))
                                .executes(context -> startGet(
                                        StringArgumentType.getString(context, "enchantment"),
                                        IntegerArgumentType.getInteger(context, "level"),
                                        null))
                                .then(argument("item", StringArgumentType.word())
                                        .executes(context -> startGet(
                                                StringArgumentType.getString(context, "enchantment"),
                                                IntegerArgumentType.getInteger(context, "level"),
                                                StringArgumentType.getString(context, "item")))))));

        builder.then(literal("stop").executes(context -> {
            EnchCracker module = module();
            if (module != null) {
                module.stopFarm(null);
                info("Farming stopped.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("hand").executes(context -> {
            EnchCracker module = module();
            if (module == null) return SINGLE_SUCCESS;

            if (!(mc.player.containerMenu instanceof EnchantmentMenu menu)) {
                error("Stand at an enchanting table so I can read the seed.");
                return SINGLE_SUCCESS;
            }

            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) {
                error("Hold the item you want to simulate.");
                return SINGLE_SUCCESS;
            }

            info("Simulated offers for " + BuiltInRegistries.ITEM.getKey(held.getItem()).toString().replace("minecraft:", "") + ":");
            EnchCracker.Offer[] handOffers = module.offersFor(menu, held);
            if (handOffers == null) {
                error("Seed not cracked yet. Open the table and let the crack finish, then try again.");
                return SINGLE_SUCCESS;
            }
            for (EnchCracker.Offer offer : handOffers) info(EnchCracker.describe(offer));
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            EnchCracker module = module();
            if (module != null) {
                module.stopFarm(null);
                module.targetEnchantment.set("");
                module.targetItem.set("");
                info("Target cleared.");
            }
            return SINGLE_SUCCESS;
        }));
    }

    private final String cmdName;

    private int startGet(String enchantment, int level, String item) {
        EnchCracker module = module();
        if (module == null) return SINGLE_SUCCESS;

        String spec = item;
        if (spec == null && !mc.player.getMainHandItem().isEmpty()) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEnchantable()) {
                error("you are holding " + pathOf(held) + " and that cant be enchanted. hold the target or type its name");
                return SINGLE_SUCCESS;
            }
            spec = pathOf(held);
        }
        if (spec == null || spec.isEmpty()) spec = module.targetItem.get();
        if (spec == null || spec.isEmpty()) {
            error("No target item. Hold your item once while running this, or add an item argument: ."
                    + cmdName + " get " + enchantment + " " + (level > 0 ? level : 3) + " diamond_sword");
            return SINGLE_SUCCESS;
        }

        module.targetEnchantment.set(enchantment.toLowerCase());
        module.targetLevel.set(Math.max(0, level));
        module.targetItem.set(spec.toLowerCase());
        module.startFarm(enchantment.toLowerCase(), level, spec.toLowerCase());
        return SINGLE_SUCCESS;
    }

    private static String pathOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.getPath();
    }

    private EnchCracker module() {
        EnchCracker module = Modules.get().get(EnchCracker.class);
        if (module == null) {
            error("Enchantment Cracker module missing.");
            return null;
        }
        if (!module.isActive()) module.toggle();
        return module;
    }
}
