package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import orbiter.modules.misc.ISellWand;
import net.minecraft.command.CommandSource;

public class ISellWandCommand extends Command {
    public ISellWandCommand() {
        super("isellwand", "Control the ISellWand module.", "sellwand");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            ISellWand module = modules.get(ISellWand.class);
            if (module == null) { error("ISellWand module not found!"); return SINGLE_SUCCESS; }
            info("ISellWand is " + (module.isActive() ? "§aON" : "§cOFF")
                + " | recorded chests: " + module.recordedChestCount());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("record").executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            ISellWand module = modules.get(ISellWand.class);
            if (module == null) { error("ISellWand module not found!"); return SINGLE_SUCCESS; }
            module.toggleRecordMode();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            ISellWand module = modules.get(ISellWand.class);
            if (module == null) { error("ISellWand module not found!"); return SINGLE_SUCCESS; }
            module.clearChests();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            ISellWand module = modules.get(ISellWand.class);
            if (module == null) { error("ISellWand module not found!"); return SINGLE_SUCCESS; }
            java.util.List<String> list = module.getChestList();
            if (list.isEmpty()) {
                info("No recorded chests.");
            } else {
                info("Recorded chests (" + list.size() + "):");
                for (String s : list) info("  " + s);
            }
            return SINGLE_SUCCESS;
        }));
    }
}
