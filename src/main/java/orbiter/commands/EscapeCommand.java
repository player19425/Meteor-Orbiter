package orbiter.commands;

import orbiter.modules.ForceInvisibility;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class EscapeCommand extends Command {
    public EscapeCommand() {
        super("escape", "Controls ForceInvisibility escape logic. Usage: .escape sky [y], .escape bedrock, .escape cancel");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("sky")
            .executes(context -> {
                ForceInvisibility module = getModule();
                if (module == null) return SINGLE_SUCCESS;

                module.requestEscapeSky(null);
                ensureEnabled(module);
                info("Sky escape started with module default Y.");
                return SINGLE_SUCCESS;
            })
            .then(argument("y", DoubleArgumentType.doubleArg(-4096.0, 4096.0))
                .executes(context -> {
                    ForceInvisibility module = getModule();
                    if (module == null) return SINGLE_SUCCESS;

                    double y = DoubleArgumentType.getDouble(context, "y");
                    module.requestEscapeSky(y);
                    ensureEnabled(module);
                    info("Sky escape started at Y " + Math.round(y) + ".");
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("bedrock").executes(context -> {
            ForceInvisibility module = getModule();
            if (module == null) return SINGLE_SUCCESS;

            module.requestEscapeBedrock();
            ensureEnabled(module);
            info("Bedrock escape started.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("cancel").executes(context -> {
            ForceInvisibility module = getModule();
            if (module == null) return SINGLE_SUCCESS;

            module.cancelEscapeSpam();
            info("Escape spam canceled.");
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            ForceInvisibility module = getModule();
            if (module == null) return SINGLE_SUCCESS;

            info(
                "ForceInvisibility: "
                    + (module.isActive() ? "ON" : "OFF")
                    + " | Escape spam: "
                    + (module.isEscapeSpamActive() ? "active" : "idle")
            );
            info("Use: .escape sky [y], .escape bedrock, .escape cancel");
            return SINGLE_SUCCESS;
        });
    }

    private ForceInvisibility getModule() {
        Modules modules = Modules.get();
        if (modules == null) { error("Modules not initialized."); return null; }
        ForceInvisibility module = modules.get(ForceInvisibility.class);
        if (module == null) error("ForceInvisibility module not found.");
        return module;
    }

    private void ensureEnabled(ForceInvisibility module) {
        if (!module.isActive()) module.toggle();
    }
}
