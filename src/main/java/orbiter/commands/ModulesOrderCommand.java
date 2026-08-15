package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import orbiter.util.OrbiterModuleOrder;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ModulesOrderCommand extends Command {

    public ModulesOrderCommand() {
        super("modules-order", "Reorder module display in HUD.", "morder");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("move")
            .then(argument("module", StringArgumentType.word())
                .suggests((context, suggestBuilder) -> {
                    Collection<Module> all = Modules.get().getAll();
                    for (Module m : all) {
                        if (m != null && m.name != null) suggestBuilder.suggest(m.name);
                    }
                    return suggestBuilder.buildFuture();
                })
                .then(argument("direction", StringArgumentType.word())
                    .suggests((context, suggestBuilder) -> {
                        suggestBuilder.suggest("up");
                        suggestBuilder.suggest("down");
                        suggestBuilder.suggest("top");
                        suggestBuilder.suggest("bottom");
                        return suggestBuilder.buildFuture();
                    })
                    .executes(this::moveModule))));

        builder.then(literal("list").executes(this::listOrder));
    }

    private int moveModule(CommandContext<ClientSuggestionProvider> ctx) {
        String moduleName = StringArgumentType.getString(ctx, "module");
        String direction = StringArgumentType.getString(ctx, "direction");

        Module module = Modules.get().get(moduleName);
        if (module == null) {

            for (Module m : Modules.get().getAll()) {
                if (m != null && m.name != null && m.name.equalsIgnoreCase(moduleName)) {
                    module = m;
                    moduleName = m.name;
                    break;
                }
            }
        }

        if (module == null) {
            error("Module '" + moduleName + "' not found.");
            return 0;
        }

        if (!OrbiterModuleOrder.hasOrder(moduleName)) {
            OrbiterModuleOrder.setOrder(moduleName, 0);
        }

        boolean success;
        String label;

        switch (direction.toLowerCase()) {
            case "up" -> {
                success = OrbiterModuleOrder.moveUp(moduleName);
                label = "up";
            }
            case "down" -> {
                success = OrbiterModuleOrder.moveDown(moduleName);
                label = "down";
            }
            case "top" -> {
                success = OrbiterModuleOrder.moveTop(moduleName);
                label = "to top";
            }
            case "bottom" -> {
                success = OrbiterModuleOrder.moveBottom(moduleName);
                label = "to bottom";
            }
            default -> {
                error("Unknown direction '" + direction + "'. Use: up, down, top, bottom.");
                return 0;
            }
        }

        if (success) {
            int newOrder = OrbiterModuleOrder.getOrder(moduleName);
            ChatUtils.info("Moved " + moduleName + " " + label + " (priority: " + newOrder + ").");
        } else {
            warning("Failed to move " + moduleName + " " + label + ".");
        }
        return SINGLE_SUCCESS;
    }

    private int listOrder(CommandContext<ClientSuggestionProvider> ctx) {
        Map<String, Integer> all = OrbiterModuleOrder.getAll();

        if (all.isEmpty()) {
            ChatUtils.info("No custom module order set. Use .morder move <module> <up|down|top|bottom> to set one.");
            return SINGLE_SUCCESS;
        }

        ChatUtils.info("Custom module order (" + all.size() + " entries, lower = shows first):");

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        for (Map.Entry<String, Integer> entry : sorted) {
            ChatUtils.info("  " + entry.getKey() + " = " + entry.getValue());
        }

        return SINGLE_SUCCESS;
    }
}
