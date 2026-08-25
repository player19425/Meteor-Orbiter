package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class SetPrefixCommand extends Command {
    public SetPrefixCommand() {
        super("setprefix", "Sets Meteor's chat command prefix.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("prefix", StringArgumentType.greedyString())
            .executes(ctx -> {
                String newPrefix = StringArgumentType.getString(ctx, "prefix");
                if (newPrefix == null || newPrefix.trim().isEmpty()) {
                    error("Prefix cannot be empty.");
                    return SINGLE_SUCCESS;
                }
                newPrefix = newPrefix.trim();

                Config.get().prefix.set(newPrefix);
                Systems.save();
                info("Command prefix set to (highlight)%s", newPrefix);
                return SINGLE_SUCCESS;
            }));

        builder.executes(ctx -> {
            info("Current prefix: (highlight)%s", Config.get().prefix.get());
            info("Usage: setprefix <new-prefix>");
            return SINGLE_SUCCESS;
        });
    }
}
