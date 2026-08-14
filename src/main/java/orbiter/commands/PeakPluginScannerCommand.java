package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import orbiter.modules.misc.PeakPluginScanner;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PeakPluginScannerCommand extends Command {

    public PeakPluginScannerCommand() {
        super("peakscan", "Plugin scanner — detect server plugins.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {

        builder.executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.displayResults();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("list").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.displayList();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("export").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.exportToFile();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("scan").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.forceScan();
            info("Scan triggered. Results will appear shortly.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("probe").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.forceProbes();
            info("Probes started.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("stop").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.forceStop();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("copy").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            scanner.copyToClipboard();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("count").executes(context -> {
            PeakPluginScanner scanner = getScanner();
            if (scanner == null || !scanner.isActive()) {
                error("PeakScanner module is not active. Enable it in Modules.");
                return SINGLE_SUCCESS;
            }
            info("Detected " + scanner.getDetectedCount() + " plugins | Brand: "
                + (scanner.getServerBrand() != null ? scanner.getServerBrand() : "unknown")
                + " | Probes: " + scanner.getTotalProbesSent());
            return SINGLE_SUCCESS;
        }));
    }

    private PeakPluginScanner getScanner() {
        try {
            return Modules.get().get(PeakPluginScanner.class);
        } catch (Exception e) {
            return null;
        }
    }
}
