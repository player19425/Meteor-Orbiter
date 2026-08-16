package orbiter.modules;

import orbiter.util.CommandUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;

import java.util.Locale;
import java.util.Random;

public class RNGSpammer extends CreativeSafetyModule {
    public enum LootTableSet { All, Chests, Entities, Custom }
    public enum Profile { Balanced, TrialChambers, Dungeon, EntityDrops, Custom }
    public enum TargetMode { Self, NearestPlayer, PlayerName, Selector }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSafety = settings.createGroup("Command Safety");

    private final Setting<Profile> profile = sgGeneral.add(new EnumSetting.Builder<Profile>()
        .name("profile").description("Built-in valid loot-table profile.").defaultValue(Profile.Balanced).build());
    private final Setting<Integer> commandsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("commands-per-tick").description("Number of /loot commands per burst.").defaultValue(3)
        .min(1).sliderRange(1, 20).build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Ticks between loot command bursts.").defaultValue(1)
        .min(0).sliderRange(0, 20).build());
    private final Setting<Integer> spreadRadius = sgGeneral.add(new IntSetting.Builder()
        .name("spread-radius").description("Radius around each target to spawn loot.").defaultValue(5)
        .min(0).sliderRange(0, 30).build());
    private final Setting<Boolean> randomPosition = sgGeneral.add(new BoolSetting.Builder()
        .name("random-position").description("Randomize spawn position within the radius.").defaultValue(true).build());
    private final Setting<LootTableSet> lootTableSet = sgGeneral.add(new EnumSetting.Builder<LootTableSet>()
        .name("loot-tables").description("Which loot tables to use when the profile is Custom.")
        .defaultValue(LootTableSet.All).visible(() -> profile.get() == Profile.Custom).build());
    private final Setting<String> customLootTable = sgGeneral.add(new StringSetting.Builder()
        .name("custom-loot-table").description("Custom loot table ID when profile is Custom and mode is Custom.")
        .defaultValue("minecraft:chests/simple_dungeon")
        .visible(() -> profile.get() == Profile.Custom && lootTableSet.get() == LootTableSet.Custom).build());

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode").description("One target or a server selector that may match multiple players.")
        .defaultValue(TargetMode.Self).build());
    private final Setting<String> targetPlayerName = sgTarget.add(new StringSetting.Builder()
        .name("target-player").description("Exact player name used as the loot center.").defaultValue("")
        .visible(() -> targetMode.get() == TargetMode.PlayerName).build());
    private final Setting<String> targetSelector = sgTarget.add(new StringSetting.Builder()
        .name("target-selector").description("Safe player selector, for example @a[tag=loot-target,limit=3].")
        .defaultValue("@a[tag=loot-target,limit=3]")
        .visible(() -> targetMode.get() == TargetMode.Selector).build());
    private final Setting<Double> nearestRange = sgTarget.add(new DoubleSetting.Builder()
        .name("nearest-range").description("Maximum range for nearest-player targeting.").defaultValue(64)
        .min(1).sliderRange(1, 256).visible(() -> targetMode.get() == TargetMode.NearestPlayer).build());
    private final Setting<Integer> maxTargets = sgSafety.add(new IntSetting.Builder()
        .name("max-targets").description("Maximum players matched by a multi-player @a selector.").defaultValue(3)
        .min(1).sliderRange(1, 16).build());
    private final Setting<Integer> maxCommands = sgSafety.add(new IntSetting.Builder()
        .name("max-commands").description("Hard cap on commands sent per burst.").defaultValue(8)
        .min(1).sliderRange(1, 32).build());

    private static final String[] CHEST_LOOT_TABLES = {
        "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
        "minecraft:chests/nether_bridge", "minecraft:chests/stronghold_library",
        "minecraft:chests/stronghold_corridor", "minecraft:chests/stronghold_crossing",
        "minecraft:chests/end_city_treasure", "minecraft:chests/desert_pyramid",
        "minecraft:chests/jungle_temple", "minecraft:chests/woodland_mansion",
        "minecraft:chests/buried_treasure", "minecraft:chests/underwater_ruin_big",
        "minecraft:chests/underwater_ruin_small", "minecraft:chests/shipwreck_treasure",
        "minecraft:chests/shipwreck_supply", "minecraft:chests/pillager_outpost",
        "minecraft:chests/bastion_bridge", "minecraft:chests/bastion_hoglin_stable",
        "minecraft:chests/bastion_other", "minecraft:chests/bastion_treasure",
        "minecraft:chests/ruined_portal", "minecraft:chests/igloo_chest",
        "minecraft:chests/village/village_weaponsmith", "minecraft:chests/village/village_toolsmith",
        "minecraft:chests/village/village_armorer", "minecraft:chests/ancient_city",
        "minecraft:chests/ancient_city_ice_box", "minecraft:chests/trial_chambers/reward",
        "minecraft:chests/trial_chambers/reward_common", "minecraft:chests/trial_chambers/reward_rare",
        "minecraft:chests/trial_chambers/reward_unique", "minecraft:chests/trial_chambers/reward_ominous",
        "minecraft:chests/trial_chambers/reward_ominous_common", "minecraft:chests/trial_chambers/reward_ominous_rare",
        "minecraft:chests/trial_chambers/reward_ominous_unique", "minecraft:chests/trial_chambers/supply"
    };
    private static final String[] TRIAL_LOOT_TABLES = {
        "minecraft:chests/trial_chambers/reward", "minecraft:chests/trial_chambers/reward_common",
        "minecraft:chests/trial_chambers/reward_rare", "minecraft:chests/trial_chambers/reward_unique",
        "minecraft:chests/trial_chambers/reward_ominous", "minecraft:chests/trial_chambers/reward_ominous_common",
        "minecraft:chests/trial_chambers/reward_ominous_rare", "minecraft:chests/trial_chambers/reward_ominous_unique",
        "minecraft:chests/trial_chambers/supply"
    };
    private static final String[] DUNGEON_LOOT_TABLES = {
        "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
        "minecraft:chests/stronghold_library", "minecraft:chests/stronghold_corridor",
        "minecraft:chests/stronghold_crossing", "minecraft:chests/desert_pyramid",
        "minecraft:chests/jungle_temple", "minecraft:chests/igloo_chest"
    };
    private static final String[] ENTITY_LOOT_TABLES = {
        "minecraft:entities/zombie", "minecraft:entities/skeleton", "minecraft:entities/creeper",
        "minecraft:entities/spider", "minecraft:entities/enderman", "minecraft:entities/blaze",
        "minecraft:entities/wither_skeleton", "minecraft:entities/ghast", "minecraft:entities/witch",
        "minecraft:entities/pig", "minecraft:entities/cow", "minecraft:entities/sheep",
        "minecraft:entities/chicken", "minecraft:entities/iron_golem", "minecraft:entities/elder_guardian",
        "minecraft:entities/guardian", "minecraft:entities/phantom", "minecraft:entities/drowned",
        "minecraft:entities/pillager", "minecraft:entities/ravager", "minecraft:entities/piglin_brute",
        "minecraft:entities/hoglin", "minecraft:entities/warden"
    };

    private final Random random = new Random();
    private int tickCounter;
    private int tableIndex;

    public RNGSpammer() {
        super("rng-spammer", "Spawns loot tables around players. OP.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        tableIndex = 0;
        info("RNG Spammer active! Spawning loot...");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.connection == null) return;
        if (++tickCounter < Math.max(1, delay.get())) return;
        tickCounter = 0;

        String target = resolveTarget();
        if (target == null) return;
        int total = Math.min(commandsPerTick.get(), maxCommands.get());
        for (int i = 0; i < total; i++) {
            String table = getNextLootTable();
            double x = (randomPosition.get() ? offset(random, spreadRadius.get()) : 0);
            double y = randomPosition.get() ? random.nextDouble() * 3 : 0;
            double z = (randomPosition.get() ? offset(random, spreadRadius.get()) : 0);
            String cmd = CommandUtils.formatCommand(
                "execute at %s run loot spawn ~%.2f ~%.2f ~%.2f loot %s", target, x, y, z, table);
            mc.player.connection.sendCommand(CommandUtils.vanilla(cmd));
        }
    }

    private String resolveTarget() {
        return switch (targetMode.get()) {
            case Self -> "@s";
            case NearestPlayer -> CommandUtils.formatCommand(
                "@p[name=!%s,distance=..%.2f]", mc.player.getGameProfile().name(), nearestRange.get());
            case PlayerName -> validatePlayerName(targetPlayerName.get());
            case Selector -> validateSelector(targetSelector.get());
        };
    }

    private String validatePlayerName(String value) {
        if (value == null || value.isBlank() || value.length() > 16) {
            warning("Target player name rejected.");
            toggle();
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                warning("Target player name rejected.");
                toggle();
                return null;
            }
        }
        return value;
    }

    private String validateSelector(String value) {
        if (value == null) return rejectSelector();
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.length() > 180 || trimmed.indexOf(' ') >= 0 || trimmed.indexOf('\n') >= 0
            || trimmed.indexOf('\r') >= 0 || !(lower.startsWith("@a") || lower.startsWith("@p") || lower.startsWith("@r"))) {
            return rejectSelector();
        }
        if (lower.startsWith("@a") && !lower.contains("limit=")) {
            trimmed = trimmed.equalsIgnoreCase("@a")
                ? "@a[limit=" + maxTargets.get() + "]"
                : trimmed.substring(0, trimmed.length() - 1) + ",limit=" + maxTargets.get() + "]";
        }
        for (int i = 2; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!(Character.isLetterOrDigit(c) || "_[],:.=!+-".indexOf(c) >= 0)) return rejectSelector();
        }
        return trimmed;
    }

    private String rejectSelector() {
        warning("Target selector rejected. Use a whitespace-free @a, @p, or @r selector.");
        toggle();
        return null;
    }

    private double offset(Random rng, int radius) {
        return (rng.nextDouble() * 2 - 1) * radius;
    }

    private String getNextLootTable() {
        String[] tables;
        switch (profile.get()) {
            case TrialChambers -> tables = TRIAL_LOOT_TABLES;
            case Dungeon -> tables = DUNGEON_LOOT_TABLES;
            case EntityDrops -> tables = ENTITY_LOOT_TABLES;
            case Custom -> tables = getCustomTables();
            default -> tables = CHEST_LOOT_TABLES;
        }
        String table = tables[tableIndex % tables.length];
        tableIndex++;
        return table;
    }

    private String[] getCustomTables() {
        return switch (lootTableSet.get()) {
            case Chests -> CHEST_LOOT_TABLES;
            case Entities -> ENTITY_LOOT_TABLES;
            case Custom -> new String[]{customLootTable.get().trim()};
            case All -> {
                String[] combined = new String[CHEST_LOOT_TABLES.length + ENTITY_LOOT_TABLES.length];
                System.arraycopy(CHEST_LOOT_TABLES, 0, combined, 0, CHEST_LOOT_TABLES.length);
                System.arraycopy(ENTITY_LOOT_TABLES, 0, combined, CHEST_LOOT_TABLES.length, ENTITY_LOOT_TABLES.length);
                yield combined;
            }
        };
    }
}
