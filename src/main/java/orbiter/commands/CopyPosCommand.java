package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.commands.SharedSuggestionProvider;
import orbiter.util.CameraPosition;

public class CopyPosCommand extends Command {

    public CopyPosCommand() {
        super("copypos", "Copies your camera position to the clipboard (works with Freecam).", "cpos");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(ctx -> copy(CameraPosition.coords()));

        builder.then(literal("look").executes(ctx -> copy(CameraPosition.coordsWithLook())));

        builder.then(literal("block").executes(ctx -> copy(CameraPosition.blockCoords())));
    }

    private int copy(String value) {
        if (mc.level == null) {
            error("Not in a world.");
            return SINGLE_SUCCESS;
        }
        mc.keyboardHandler.setClipboard(value);
        info("Copied %s", value);
        return SINGLE_SUCCESS;
    }
}
