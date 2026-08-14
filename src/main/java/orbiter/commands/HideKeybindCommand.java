package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.command.CommandSource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HideKeybindCommand extends Command {
    private static final String[] HIDDEN_IDS = {
        "key.meteor-client.open-gui",
        "key.meteor-client.open-commands"
    };

    private static volatile boolean hidden = false;
    private static volatile boolean permanent = false;

    public HideKeybindCommand() {
        super("hidekeybind", "Hides Meteor keybinds from the Controls screen.");
        loadState();
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("enable")
            .then(literal("permanent").executes(ctx -> apply(true, true)))
            .then(literal("temp").executes(ctx -> apply(true, false))));

        builder.then(literal("disable")
            .then(literal("permanent").executes(ctx -> apply(false, true)))
            .then(literal("temp").executes(ctx -> apply(false, false))));

        builder.executes(ctx -> {
            info("Keybind hiding: " + (hidden ? "(highlight)ON" : "OFF")
                + (permanent ? " permanent" : " temporary"));
            info("Usage: (highlight)hidekeybind <enable|disable> <permanent|temp>");
            return SINGLE_SUCCESS;
        });
    }

    private int apply(boolean hide, boolean persistMode) {
        hidden = hide;
        permanent = persistMode;
        saveState();

        int changed = hiddenCount();
        String mode = persistMode ? "permanently" : "temporarily";
        if (hide) {
            if (changed > 0) info("Hid (highlight)%d(default) Meteor keybind(s) %s. Reopen the Controls screen to see it.", changed, mode);
            else info("Meteor keybinds already hidden %s.", mode);
        } else {
            if (changed > 0) info("Restored (highlight)%d(default) Meteor keybind(s) %s. Reopen the Controls screen to see it.", changed, mode);
            else info("Meteor keybinds already visible.");
        }
        return SINGLE_SUCCESS;
    }

    public static void loadAndApplyOnStartup() {
        loadState();
    }

    public static boolean isHidden() {
        return hidden;
    }

    public static KeyBinding[] filterKeys(KeyBinding[] all) {
        if (!hidden || all == null) return all;

        List<KeyBinding> kept = new ArrayList<>(all.length);
        for (KeyBinding bind : all) {
            if (!isHiddenId(bind)) kept.add(bind);
        }
        return kept.toArray(new KeyBinding[0]);
    }

    private static boolean isHiddenId(KeyBinding bind) {
        if (bind == null) return false;
        String id = bind.getId();
        for (String hid : HIDDEN_IDS) {
            if (hid.equals(id)) return true;
        }
        return false;
    }

    private static int hiddenCount() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return 0;

        int count = 0;
        for (KeyBinding bind : mc.options.allKeys) {
            if (isHiddenId(bind)) count++;
        }
        return count;
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getGameDir()
            .resolve("meteor-client").resolve("orbiter-hidekeybind.json");
    }

    private static void saveState() {
        try {
            Files.createDirectories(configFile().getParent());
            try (Writer writer = Files.newBufferedWriter(configFile())) {
                writer.write("{\"hidden\":" + hidden + ",\"permanent\":" + permanent + "}");
            }
        } catch (IOException ignored) {}
    }

    private static void loadState() {
        try {
            Path file = configFile();
            if (!Files.exists(file)) return;

            String json;
            try (Reader reader = Files.newBufferedReader(file)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[256];
                int n;
                while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
                json = sb.toString();
            }
            hidden = json.contains("\"hidden\":true");
            permanent = json.contains("\"permanent\":true");
        } catch (IOException ignored) {}
    }
}
