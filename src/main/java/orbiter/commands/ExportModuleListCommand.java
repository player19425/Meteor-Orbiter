package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ExportModuleListCommand extends Command {

    public ExportModuleListCommand() {
        super("exportmodulelist", "Exports all module names to clipboard.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Collection<Module> all = Modules.get().getAll();
            if (all == null || all.isEmpty()) {
                error("No modules found.");
                return SINGLE_SUCCESS;
            }

            List<String> lines = new ArrayList<>();
            for (Module module : all) {
                if (module == null || module.name == null) continue;
                String cat = module.category != null ? module.category.name : "Unknown";
                lines.add(module.name + " [" + cat + "]");
            }
            Collections.sort(lines);

            StringBuilder sb = new StringBuilder();
            sb.append("All Modules (").append(lines.size()).append(" total):\n");
            for (int i = 0; i < lines.size(); i++) {
                sb.append(i + 1).append(". ").append(lines.get(i));
                if (i < lines.size() - 1) sb.append('\n');
            }

            String output = sb.toString();
            mc.keyboard.setClipboard(output);
            info("Copied " + lines.size() + " module names (all addons) to clipboard.");

            return SINGLE_SUCCESS;
        });
    }
}
