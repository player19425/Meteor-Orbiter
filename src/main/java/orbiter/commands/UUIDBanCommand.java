package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import orbiter.modules.world.UUIDBan;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class UUIDBanCommand extends Command {
    public UUIDBanCommand() {
        super("uuidban", "Ban a player by summoning a UUID entity.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("username", StringArgumentType.word())
            .executes(context -> {
                UUIDBan mod = Modules.get().get(UUIDBan.class);
                if (mod == null) { error("UUIDBan not registered."); return SINGLE_SUCCESS; }
                mod.executeCommand(StringArgumentType.getString(context, "username"));
                return SINGLE_SUCCESS;
            }));

        builder.then(literal("cleanup").executes(context -> {
            UUIDBan mod = Modules.get().get(UUIDBan.class);
            if (mod != null) mod.cleanup();
            return SINGLE_SUCCESS;
        }));
    }
}
