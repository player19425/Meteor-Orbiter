package orbiter.commands;

import orbiter.modules.TNTRain;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class TNTRainCommand extends Command {
    public TNTRainCommand() {
        super("tntrain", "Triggers TNT rain with specified parameters. Usage: .tntrain [amount] [radius] [height]");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {

        builder.executes(context -> {
            startRain(50, 10, 30);
            return SINGLE_SUCCESS;
        });

        builder.then(argument("amount", IntegerArgumentType.integer(1))
                .executes(context -> {
                    int amount = IntegerArgumentType.getInteger(context, "amount");
                    startRain(amount, 10, 30);
                    return SINGLE_SUCCESS;
                })

                .then(argument("radius", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int amount = IntegerArgumentType.getInteger(context, "amount");
                            int radius = IntegerArgumentType.getInteger(context, "radius");
                            startRain(amount, radius, 30);
                            return SINGLE_SUCCESS;
                        })

                        .then(argument("height", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                    int height = IntegerArgumentType.getInteger(context, "height");
                                    startRain(amount, radius, height);
                                    return SINGLE_SUCCESS;
                                }))));
    }

    private void startRain(int amount, int radius, int height) {
        Modules modules = Modules.get();
        if (modules == null) {
            error("Modules not initialized.");
            return;
        }
        TNTRain module = modules.get(TNTRain.class);
        if (module == null) {
            error("TNTRain module not found!");
            return;
        }

        info("Starting TNT Rain: " + amount + " TNT, radius " + radius + ", height " + height);
        module.startWithParams(amount, radius, height);
    }
}
