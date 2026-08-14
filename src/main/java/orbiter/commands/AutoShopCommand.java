package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import orbiter.modules.AutoShop;
import net.minecraft.command.CommandSource;

public class AutoShopCommand extends Command {
    public AutoShopCommand() {
        super("autoshop", "Toggle and control the AutoShop module.", "autoshopdetect", "autoshopsave", "autoshopstatus");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            AutoShop module = modules.get(AutoShop.class);
            if (module == null) { error("AutoShop module not found!"); return SINGLE_SUCCESS; }
            module.toggle();
            info("AutoShop is now " + (module.isActive() ? "§aON" : "§cOFF"));
            return SINGLE_SUCCESS;
        });

        builder.then(literal("status").executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            AutoShop module = modules.get(AutoShop.class);
            if (module == null) { error("AutoShop module not found!"); return SINGLE_SUCCESS; }
            info("AutoShop " + (module.isActive() ? "§aON" : "§cOFF") + " | Buy attempts: " + module.getBuyAttempts());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("reset").executes(context -> {
            Modules modules = Modules.get();
            if (modules == null) { error("Modules not initialized."); return SINGLE_SUCCESS; }
            AutoShop module = modules.get(AutoShop.class);
            if (module == null) { error("AutoShop module not found!"); return SINGLE_SUCCESS; }
            module.resetFullChests();
            info("Full chests memory cleared.");
            return SINGLE_SUCCESS;
        }));
    }
}
