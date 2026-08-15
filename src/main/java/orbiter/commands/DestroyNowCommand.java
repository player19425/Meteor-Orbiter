package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import orbiter.modules.world.DestroyNow;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class DestroyNowCommand extends Command {
    public DestroyNowCommand() {
        super("destroynow", "Inspect, arm, preview, execute, or cancel DestroyNow.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> run(DestroyNow::status));
        builder.then(literal("inspect").executes(context -> run(DestroyNow::inspectAndLoad)));
        builder.then(literal("cancel").executes(context ->
            run(module -> module.cancel("Emergency cancellation requested."))));
        builder.then(literal("status").executes(context -> run(DestroyNow::status)));
    }

    private int run(java.util.function.Consumer<DestroyNow> action) {
        DestroyNow module = Modules.get() == null ? null : Modules.get().get(DestroyNow.class);
        if (module == null) {
            error("DestroyNow module is not registered.");
            return SINGLE_SUCCESS;
        }
        if (!module.isActive()) {
            error("DestroyNow must be active. Activation only inspects and never executes.");
            return SINGLE_SUCCESS;
        }
        action.accept(module);
        return SINGLE_SUCCESS;
    }
}
