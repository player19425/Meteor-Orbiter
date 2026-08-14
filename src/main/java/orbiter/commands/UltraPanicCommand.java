package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class UltraPanicCommand extends Command {

    private static final Set<String> KNOWN_CLIENT_FOLDERS = Set.of(
        "meteor-client", "wurst", "impact", "aristois", "sigma",
        "novoline", "rise", "vape", "inertia",
        "salhack", "gamesense", "phobos"
    );

    private static final Set<String> MOD_LOADER_FOLDERS = Set.of(
        "mods", "modstore", "fabric", "forge", "neoforge",
        "libraries", "versions"
    );

    private static final Set<String> EVIDENCE_FOLDERS = Set.of(
        "crash-reports", "logs", "debug"
    );

    private static final Set<String> LAUNCHER_FILES = Set.of(
        "launcher_profiles.json", "launcher_accounts.json",
        "versions", "options.txt"
    );

    public UltraPanicCommand() {
        super("ultrapanic", "Reversibly swap .minecraft to a clean vanilla copy, hiding all mod evidence.", "up");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            showCompactStatus();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("help").executes(ctx -> {
            showHelp();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("confirm").executes(ctx -> {
            executePanic();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("status").executes(ctx -> {
            showStatus();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("restore").executes(ctx -> {
            executeRestore();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("prepare-clean").executes(ctx -> {
            prepareCleanCopyInstructions();
            return SINGLE_SUCCESS;
        }));
    }

    private void showHelp() {
        info("§6[.ultrapanic] §fReversibly hide ALL mod evidence by swapping .minecraft folders.");
        info("§7Usage:");
        info("  §e.ultrapanic confirm  §7• Swap to clean vanilla .minecraft");
        info("  §e.ultrapanic restore §7• Swap back to your real modded .minecraft");
        info("  §e.ultrapanic status  §7• Check if currently in panic/clean state");
        info("  §e.ultrapanic prepare-clean §7• Instructions for setting up clean copy");
        info("");
        info("§c⚠ WARNING: You MUST have a clean vanilla .minecraft_clean folder ready!");
        info("§7The clean folder should contain ONLY default vanilla files •");
        info("§7no mods/, no meteor-client/, no crash reports, no modded profiles.");
        info("§7No files are ever deleted • only renamed/moved.");
        info("§7A restore script is generated at .minecraft/restore_orbiter.bat/.sh");
    }

    private void showCompactStatus() {
        File mcDir = getMinecraftDir();
        if (mcDir == null) { error("Cannot locate .minecraft directory."); return; }
        File realDir = new File(mcDir.getParentFile(), getRealFolderName());
        File cleanDir = new File(mcDir.getParentFile(), getCleanFolderName());
        boolean inPanic = realDir.exists() && !realDir.equals(mcDir);
        boolean hasClean = cleanDir.exists();
        if (inPanic) {
            info("§c[Panicked] §e.ultrapanic restore §7to swap back.");
        } else if (hasClean) {
            info("§a[Ready] §e.ultrapanic confirm §7to panic. §e.ultrapanic restore §7to revert.");
        } else {
            info("§e[Need Clean Copy] §7Create a .minecraft_clean folder first.");
        }
    }

    private void showStatus() {
        File mcDir = getMinecraftDir();
        if (mcDir == null) { error("Cannot locate .minecraft directory."); return; }

        File realDir = new File(mcDir.getParentFile(), getRealFolderName());
        File cleanDir = new File(mcDir.getParentFile(), getCleanFolderName());

        boolean inPanic = realDir.exists() && !realDir.equals(mcDir);
        boolean hasClean = cleanDir.exists();

        if (inPanic) {
            info("§c⚠ PANIC MODE ACTIVE • You are running on the clean vanilla copy.");
            info("§7Run §e.ultrapanic restore §7to swap back to your real .minecraft");
        } else {
            info("§a✓ Normal mode • You are running on your real .minecraft");
        }

        info("§7Clean copy available: " + (hasClean ? "§aYes" : "§cNo • run .ultrapanic prepare-clean"));
        info("§7Real folder backup: " + (realDir.exists() ? "§a" + realDir.getName() : "§cNot found"));
        info("§7Current .minecraft: " + mcDir.getAbsolutePath());
    }

    private void executePanic() {
        File mcDir = getMinecraftDir();
        if (mcDir == null) { error("Cannot locate .minecraft directory."); return; }

        File parentDir = mcDir.getParentFile();
        File realDir = new File(parentDir, getRealFolderName());
        File cleanDir = new File(parentDir, getCleanFolderName());

        if (realDir.exists()) {
            error("Already in panic mode! Real .minecraft is backed up at: " + realDir.getAbsolutePath());
            info("§7Run §e.ultrapanic restore §7to swap back.");
            return;
        }

        if (!cleanDir.exists()) {
            error("§cClean vanilla .minecraft folder not found!");
            info("§7Expected at: " + cleanDir.getAbsolutePath());
            info("§7You need to create a clean vanilla .minecraft first.");
            info("§7Run §e.ultrapanic prepare-clean §7for instructions.");
            return;
        }

        disableAllModules();

        info("§eStep 1: Renaming real .minecraft → " + realDir.getName() + "...");
        if (!mcDir.renameTo(realDir)) {
            error("Failed to rename .minecraft! Check if files are locked.");
            return;
        }

        info("§eStep 2: Activating clean vanilla copy...");
        if (!cleanDir.renameTo(mcDir)) {

            error("Failed to activate clean copy! Rolling back...");
            realDir.renameTo(mcDir);
            return;
        }

        generateRestoreScript(parentDir, mcDir, realDir, cleanDir);

        info("§a✔ ULTRAPANIC ACTIVATED! You are now on a clean vanilla profile.");
        info("§7To restore, run: §e.ultrapanic restore");
        info("§7Or use the restore script: §e" + mcDir.getName() + "/restore_orbiter.bat");
        info("§c⚠ Close and restart Minecraft for changes to take full effect.");
    }

    private void executeRestore() {
        File mcDir = getMinecraftDir();
        if (mcDir == null) { error("Cannot locate .minecraft directory."); return; }

        File parentDir = mcDir.getParentFile();
        File realDir = new File(parentDir, getRealFolderName());
        File cleanDir = new File(parentDir, getCleanFolderName());

        if (!realDir.exists()) {
            error("Not in panic mode • real .minecraft backup not found at: " + realDir.getAbsolutePath());
            return;
        }

        info("§eStep 1: Storing clean copy as " + cleanDir.getName() + "...");
        if (!mcDir.renameTo(cleanDir)) {
            error("Failed to move clean copy back! Check if files are locked.");
            return;
        }

        info("§eStep 2: Restoring real modded .minecraft...");
        if (!realDir.renameTo(mcDir)) {
            error("Failed to restore real .minecraft! Clean copy is at: " + cleanDir.getAbsolutePath());
            info("§7You can manually rename: " + cleanDir.getName() + " → .minecraft");
            return;
        }

        info("§a✔ RESTORED! You are back on your real modded .minecraft.");
        info("§c⚠ Close and restart Minecraft for changes to take full effect.");
    }

    private void prepareCleanCopyInstructions() {
        File mcDir = getMinecraftDir();
        if (mcDir == null) { error("Cannot locate .minecraft directory."); return; }

        File cleanDir = new File(mcDir.getParentFile(), getCleanFolderName());

        info("§6════════════════════════════════════════════════════");
        info("§6  How to Prepare a Clean Vanilla .minecraft");
        info("§6════════════════════════════════════════════════════");
        info("");
        info("§eStep 1:§r Launch the vanilla Minecraft launcher and");
        info("  start a fresh vanilla installation (no mods, no Fabric/Forge).");
        info("");
        info("§eStep 2:§r Let it create a clean .minecraft profile.");
        info("  You can use a different game directory in the launcher");
        info("  to avoid overwriting your current setup.");
        info("");
        info("§eStep 3:§r Copy that clean .minecraft folder to:");
        info("  §b" + cleanDir.getAbsolutePath());
        info("");
        info("§eStep 4:§r Verify the clean folder contains ONLY:");
        info("  §7- assets/, libraries/, launcher profiles");
        info("  §7- A single vanilla version folder");
        info("  §7- options.txt (clean vanilla settings)");
        info("  §7- NO mods/, NO meteor-client/, NO crash-reports/");
        info("  §7- NO Fabric/Forge/NeoForge folders");
        info("  §7- NO suspicious .jar files");
        info("");
        info("§eStep 5:§r Run §b.ultrapanic confirm§r to activate.");
        info("");
        info("§7Quick method (if you have a second .minecraft):");
        info("  §bxcopy /E /I \"<clean_path>\" \"" + cleanDir.getAbsolutePath() + "\"");
    }

    private void disableAllModules() {
        if (Modules.get() == null) return;
        info("§eDisabling all active modules...");
        int count = 0;
        for (Module m : Modules.get().getActive()) {

            m.toggle();
            count++;
        }
        info("§7Disabled " + count + " active modules.");
    }

    private void generateRestoreScript(File parentDir, File mcDir, File realDir, File cleanDir) {

        String batContent = String.format(
            "@echo off\r\n" +
            "echo ============================================\r\n" +
            "echo  Orbiter UltraPanic Restore Script\r\n" +
            "echo ============================================\r\n" +
            "echo.\r\n" +
            "echo This will restore your original modded .minecraft\r\n" +
            "echo Make sure Minecraft is CLOSED before continuing.\r\n" +
            "echo.\r\n" +
            "pause\r\n" +
            "echo.\r\n" +
            "echo Restoring real .minecraft...\r\n" +
            "if exist \"%s\" (\r\n" +
            "    ren \"%s\" \"%s\"\r\n" +
            "    echo Clean copy stored as: %s\r\n" +
            ") else (\r\n" +
            "    echo ERROR: Real .minecraft backup not found at: %s\r\n" +
            "    echo You may already be restored.\r\n" +
            "    pause\r\n" +
            "    exit /b 1\r\n" +
            ")\r\n" +
            "if exist \"%s\" (\r\n" +
            "    ren \"%s\" \"%s\"\r\n" +
            "    echo Restored original .minecraft successfully!\r\n" +
            ") else (\r\n" +
            "    echo ERROR: Clean .minecraft not found at: %s\r\n" +
            "    echo Trying to restore from backup...\r\n" +
            ")\r\n" +
            "echo.\r\n" +
            "echo Done! You can now launch Minecraft normally.\r\n" +
            "pause\r\n",

            realDir.getAbsolutePath(),
            mcDir.getAbsolutePath(), cleanDir.getName(),
            cleanDir.getAbsolutePath(),
            realDir.getAbsolutePath(),
            cleanDir.getAbsolutePath(),
            realDir.getAbsolutePath(), mcDir.getName(),
            cleanDir.getAbsolutePath()
        );

        String shContent = String.format(
            "#!/bin/bash\n" +
            "echo \"============================================\"\n" +
            "echo \" Orbiter UltraPanic Restore Script\"\n" +
            "echo \"============================================\"\n" +
            "echo \"\"\n" +
            "echo \"This will restore your original modded .minecraft\"\n" +
            "echo \"Make sure Minecraft is CLOSED before continuing.\"\n" +
            "echo \"\"\n" +
            "read -p \"Press Enter to continue...\"\n" +
            "echo \"\"\n" +
            "echo \"Restoring real .minecraft...\"\n" +
            "if [ -d \"%s\" ]; then\n" +
            "    mv \"%s\" \"%s\"\n" +
            "    echo \"Clean copy stored as: %s\"\n" +
            "else\n" +
            "    echo \"ERROR: Real .minecraft backup not found at: %s\"\n" +
            "    echo \"You may already be restored.\"\n" +
            "    exit 1\n" +
            "fi\n" +
            "if [ -d \"%s\" ]; then\n" +
            "    mv \"%s\" \"%s\"\n" +
            "    echo \"Restored original .minecraft successfully!\"\n" +
            "else\n" +
            "    echo \"ERROR: Clean .minecraft not found at: %s\"\n" +
            "fi\n" +
            "echo \"\"\n" +
            "echo \"Done! You can now launch Minecraft normally.\"\n",
            realDir.getAbsolutePath(),
            mcDir.getAbsolutePath(), cleanDir.getAbsolutePath(),
            cleanDir.getAbsolutePath(),
            realDir.getAbsolutePath(),
            cleanDir.getAbsolutePath(),
            realDir.getAbsolutePath(), mcDir.getAbsolutePath(),
            cleanDir.getAbsolutePath()
        );

        try {
            Path batPath = mcDir.toPath().resolve("restore_orbiter.bat");
            Files.writeString(batPath, batContent);
            info("§7Generated restore script: " + batPath);

            Path shPath = mcDir.toPath().resolve("restore_orbiter.sh");
            Files.writeString(shPath, shContent);
            shPath.toFile().setExecutable(true);
            info("§7Generated restore script: " + shPath);
        } catch (IOException e) {
            warning("Failed to generate restore script: " + e.getMessage());
        }
    }

    private File getMinecraftDir() {
        if (mc == null) return null;
        File dir = mc.runDirectory;
        return dir != null ? dir : null;
    }

    private String getRealFolderName() {
        return ".minecraft_real";
    }

    private String getCleanFolderName() {
        return ".minecraft_clean";
    }
}
