package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class TransferCommand extends Command {

    public TransferCommand() {
        super("transfer", "Transfer to another server without disconnecting. Usage: .transfer <ip>");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("ip", StringArgumentType.greedyString())
            .executes(context -> {
                if (mc.player == null || mc.getConnection() == null) {
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

                if (ip.startsWith("[")) {
                    int closeIdx = ip.indexOf(']');
                    if (closeIdx == -1) {
                        error("Invalid server IP.");
                        return SINGLE_SUCCESS;
                    }
                    host = ip.substring(1, closeIdx);
                    String rest = ip.substring(closeIdx + 1);
                    if (rest.startsWith(":")) {
                        Integer parsed = parsePort(rest.substring(1).trim());
                        if (parsed == null) return SINGLE_SUCCESS;
                        port = parsed;
                    }
                } else {
                    int colonIdx = ip.lastIndexOf(':');
                    if (colonIdx != -1 && colonIdx == ip.indexOf(':')) {
                        host = ip.substring(0, colonIdx).trim();
                        Integer parsed = parsePort(ip.substring(colonIdx + 1).trim());
                        if (parsed == null) return SINGLE_SUCCESS;
                        port = parsed;
                    }
                }

                if (host.isEmpty()) {
                    error("Invalid server IP.");
                    return SINGLE_SUCCESS;
                }

                mc.getConnection().sendCommand("transfer " + host + " " + port);
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

    private Integer parsePort(String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            error("Invalid port '" + value + "'.");
            return null;
        }
        if (parsed < 0 || parsed > 65535) {
            error("Port must be between 0 and 65535.");
            return null;
        }
        return parsed;
    }
}
