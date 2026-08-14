package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class TransferCommand extends Command {

    public TransferCommand() {
        super("transfer", "Transfer to another server without disconnecting. Usage: .transfer <ip>");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("ip", StringArgumentType.greedyString())
            .executes(context -> {
                if (mc.player == null || mc.getNetworkHandler() == null) {
                    error("Not connected to a server.");
                    return SINGLE_SUCCESS;
                }

                String ip = StringArgumentType.getString(context, "ip").trim();
                if (ip.isEmpty()) {
                    error("Server IP cannot be empty.");
                    return SINGLE_SUCCESS;
                }

                String host = ip;
                int port = 25565;
                int colonIdx = ip.lastIndexOf(':');
                if (colonIdx != -1) {
                    try {
                        port = Integer.parseInt(ip.substring(colonIdx + 1).trim());
                        host = ip.substring(0, colonIdx).trim();
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (host.isEmpty()) {
                    error("Invalid server IP.");
                    return SINGLE_SUCCESS;
                }

                mc.getNetworkHandler().sendChatCommand("transfer " + host + " " + port);
                info("Transferring to " + host + ":" + port + " ...");
                return SINGLE_SUCCESS;
            }));

        builder.executes(context -> {
            info("Usage: .transfer <ip>[:port]");
            info("Example: .transfer hypixel.net");
            info("Example: .transfer localhost:25566");
            return SINGLE_SUCCESS;
        });
    }
}
