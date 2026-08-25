package orbiter.modules.misc;

import orbiter.Orbiter;
import orbiter.util.PeakScanCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PeakPluginScanner extends Module {

    private static final Set<String> VANILLA_NAMESPACES = Set.of(
        "minecraft", "brigadier", "bukkit", "spigot", "paper", "purpur",
        "velocity", "bungeecord", "waterfall"
    );

    private static final Set<String> VANILLA_COMMAND_ROOTS = Set.of(
        "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear",
        "clone", "damage", "data", "datapack", "debug", "defaultgamemode",
        "deop", "difficulty", "effect", "enchant", "execute", "experience", "xp",
        "fill", "fillbiome", "forceload", "function", "gamemode", "gamerule",
        "give", "help", "item", "jfr", "kick", "kill", "list", "locate", "loot",
        "me", "msg", "op", "pardon", "pardon-ip", "particle", "perf", "place",
        "playsound", "publish", "random", "recipe", "reload", "return", "ride",
        "rotate", "save-all", "save-off", "save-on", "say", "schedule", "scoreboard",
        "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint", "spectate",
        "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg",
        "tm", "teleport", "tell", "tellraw", "tick", "time", "title", "tp",
        "transfer", "trigger", "w", "weather", "whitelist", "worldborder"
    );

    private static final String ROOT_PROBE_PREFIXES = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final Map<String, String> ROOT_COMMAND_PLUGIN_ALIASES = Map.ofEntries(
        Map.entry("lp", "luckperms"),
        Map.entry("we", "worldedit"),
        Map.entry("rg", "worldguard"),
        Map.entry("mv", "multiverse-core"),
        Map.entry("npc", "citizens"),
        Map.entry("papi", "placeholderapi"),
        Map.entry("cmi", "cmi"),
        Map.entry("co", "coreprotect"),
        Map.entry("grim", "grimac"),
        Map.entry("geyser", "geysermc"),
        Map.entry("floodgate", "floodgate"),
        Map.entry("viaver", "viaversion"),
        Map.entry("sr", "skinsrestorer"),
        Map.entry("authme", "authme"),
        Map.entry("authmereloaded", "authme"),
        Map.entry("librelogin", "librelogin"),
        Map.entry("nlogin", "nlogin"),
        Map.entry("openlogin", "openlogin"),
        Map.entry("limboauth", "limboauth"),
        Map.entry("loginsecurity", "loginsecurity"),
        Map.entry("fastlogin", "fastlogin"),
        Map.entry("bungeeguard", "bungeeguard"),
        Map.entry("dm", "deluxemenus"),
        Map.entry("plots", "plotsquared"),
        Map.entry("sv", "supervanish"),
        Map.entry("spawn", "essentialsx"),
        Map.entry("home", "essentialsx"),
        Map.entry("homes", "essentialsx"),
        Map.entry("warp", "essentialsx"),
        Map.entry("warps", "essentialsx"),
        Map.entry("tpa", "essentialsx"),
        Map.entry("tpahere", "essentialsx"),
        Map.entry("bal", "essentialsx"),
        Map.entry("balance", "essentialsx"),
        Map.entry("money", "essentialsx"),
        Map.entry("eco", "essentialsx"),
        Map.entry("ban", "essentialsx"),
        Map.entry("kick", "essentialsx"),
        Map.entry("mute", "essentialsx"),
        Map.entry("jail", "essentialsx"),
        Map.entry("seen", "essentialsx"),
        Map.entry("ptime", "essentialsx"),
        Map.entry("pweather", "essentialsx"),
        Map.entry("tppos", "essentialsx"),
        Map.entry("near", "essentialsx"),
        Map.entry("back", "essentialsx"),
        Map.entry("afk", "essentialsx"),
        Map.entry("msg", "essentialsx"),
        Map.entry("reply", "essentialsx"),
        Map.entry("mail", "essentialsx"),
        Map.entry("pay", "essentialsx"),
        Map.entry("sell", "essentialsx"),
        Map.entry("worth", "essentialsx"),
        Map.entry("kit", "essentialsx"),
        Map.entry("kits", "essentialsx"),
        Map.entry("lb", "litebans"),
        Map.entry("litebans", "litebans"),
        Map.entry("ab", "advancedban"),
        Map.entry("advancedban", "advancedban"),
        Map.entry("tab", "tab"),
        Map.entry("pat", "proantitab"),
        Map.entry("proantitab", "proantitab"),
        Map.entry("spark", "spark"),
        Map.entry("plan", "plan"),
        Map.entry("votingplugin", "votingplugin"),
        Map.entry("vote", "votingplugin"),
        Map.entry("votes", "votingplugin"),
        Map.entry("jobs", "jobsreborn"),
        Map.entry("mcmmo", "mcmmo"),
        Map.entry("towny", "towny"),
        Map.entry("f", "factions"),
        Map.entry("factions", "factions"),
        Map.entry("lands", "lands"),
        Map.entry("res", "residence"),
        Map.entry("residence", "residence"),
        Map.entry("qs", "quickshop"),
        Map.entry("quickshop", "quickshop"),
        Map.entry("auctionhouse", "auctionhouse"),
        Map.entry("crazycrates", "crazycrates"),
        Map.entry("excellentcrates", "excellentcrates"),
        Map.entry("goldencrates", "goldencrates"),
        Map.entry("cratereloaded", "cratereloaded"),
        Map.entry("magiccrates", "magiccrates"),
        Map.entry("simplelootcrates", "simplelootcrates"),
        Map.entry("cratesplus", "cratesplus"),
        Map.entry("cratekeys", "cratekeys"),
        Map.entry("casino", "casino"),
        Map.entry("casinoslots", "casinoslots"),
        Map.entry("slotmachine", "slotmachine"),
        Map.entry("coinflip", "coinflip"),
        Map.entry("jackpot", "jackpot"),
        Map.entry("lottery", "lottery"),
        Map.entry("mythicmobs", "mythicmobs"),
        Map.entry("mm", "mythicmobs"),
        Map.entry("mmoitems", "mmoitems"),
        Map.entry("denizen", "denizen"),
        Map.entry("sentinel", "sentinel"),
        Map.entry("vulcan", "vulcan"),
        Map.entry("matrix", "matrix"),
        Map.entry("karhu", "karhu"),
        Map.entry("verus", "verus"),
        Map.entry("ncp", "nocheatplus"),
        Map.entry("ess", "essentialsx"),
        Map.entry("essentials", "essentialsx"),
        Map.entry("essentialsx", "essentialsx"),
        Map.entry("sethome", "essentialsx"),
        Map.entry("delhome", "essentialsx"),
        Map.entry("setwarp", "essentialsx"),
        Map.entry("delwarp", "essentialsx"),
        Map.entry("tpaccept", "essentialsx"),
        Map.entry("tpdeny", "essentialsx"),
        Map.entry("tpacancel", "essentialsx"),
        Map.entry("heal", "essentialsx"),
        Map.entry("feed", "essentialsx"),
        Map.entry("god", "essentialsx"),
        Map.entry("fly", "essentialsx"),
        Map.entry("speed", "essentialsx"),
        Map.entry("hat", "essentialsx"),
        Map.entry("nick", "essentialsx"),
        Map.entry("realname", "essentialsx"),
        Map.entry("broadcast", "essentialsx"),
        Map.entry("baltop", "essentialsx"),
        Map.entry("tpall", "essentialsx"),
        Map.entry("spawnmob", "essentialsx"),
        Map.entry("burn", "essentialsx"),
        Map.entry("clearinventory", "essentialsx"),
        Map.entry("geoip", "essentialsx"),
        Map.entry("wge", "worldguard"),
        Map.entry("worldguard", "worldguard"),
        Map.entry("worldedit", "worldedit"),
        Map.entry("wedit", "worldedit"),
        Map.entry("lpe", "luckperms"),
        Map.entry("luckperms", "luckperms"),
        Map.entry("grime", "grimac"),
        Map.entry("vvanish", "supervanish"),
        Map.entry("placeholderapi", "placeholderapi"),
        Map.entry("oraxen", "oraxen"),
        Map.entry("ia", "itemsadder"),
        Map.entry("itemsadder", "itemsadder"),
        Map.entry("mmo", "mythicmobs"),
        Map.entry("ml", "mythiclib"),
        Map.entry("mythiclib", "mythiclib"),
        Map.entry("modelengine", "modelengine"),
        Map.entry("az", "authme"),
        Map.entry("cs", "chestshop"),
        Map.entry("chestshop", "chestshop"),
        Map.entry("shop", "shopkeepers"),
        Map.entry("skp", "shopkeepers"),
        Map.entry("minions", "minions"),
        Map.entry("bs", "bossshop"),
        Map.entry("bossshop", "bossshop"),
        Map.entry("bspro", "bossshoppro"),
        Map.entry("bossshoppro", "bossshoppro"),
        Map.entry("neg", "negativity"),
        Map.entry("negativity", "negativity"),
        Map.entry("themis", "themis"),
        Map.entry("polar", "polar"),
        Map.entry("horizon", "horizon"),
        Map.entry("intave", "intave"),
        Map.entry("sentry", "sentry"),
        Map.entry("sahara", "sahara"),
        Map.entry("warden", "warden"),
        Map.entry("reflex", "reflex"),
        Map.entry("antiaura", "antiaura"),
        Map.entry("playeranalytics", "plan"),
        Map.entry("dynmap", "dynmap"),
        Map.entry("bluemap", "bluemap"),
        Map.entry("bm", "bluemap"),
        Map.entry("ajl", "ajleaderboards"),
        Map.entry("ajq", "ajqueue"),
        Map.entry("mmt", "minimotd"),
        Map.entry("votifier", "votifier"),
        Map.entry("factionsuuid", "factionsuuid"),
        Map.entry("fu", "factionsuuid"),
        Map.entry("plot", "plotsquared"),
        Map.entry("p2", "plotsquared"),
        Map.entry("cl", "combatlogx"),
        Map.entry("clx", "combatlogx"),
        Map.entry("cb", "combatlogx"),
        Map.entry("crazyauctions", "crazyauctions"),
        Map.entry("axah", "axauctionhouse"),
        Map.entry("axa", "axauctions"),
        Map.entry("deluxeauctions", "deluxeauctions"),
        Map.entry("qsh", "quickshop"),
        Map.entry("cshop", "chestshop"),
        Map.entry("sgui", "shopguiplus"),
        Map.entry("shopguiplus", "shopguiplus"),
        Map.entry("economyshopgui", "economyshopgui"),
        Map.entry("esgui", "economyshopgui"),
        Map.entry("fawe", "fastasyncworldedit"),
        Map.entry("citizenscmd", "citizenscmd"),
        Map.entry("fh", "fancyholograms"),
        Map.entry("fn", "fancynpcs"),
        Map.entry("bb", "bentobox"),
        Map.entry("gd", "griefdefender"),
        Map.entry("huskclaims", "huskclaims"),
        Map.entry("husktowns", "husktowns"),
        Map.entry("ps", "protectionstones"),
        Map.entry("rp", "redprotect"),
        Map.entry("lwc", "lwc"),
        Map.entry("lwcx", "lwcx"),
        Map.entry("cc", "claimchunk"),
        Map.entry("kingdoms", "kingdoms"),
        Map.entry("kingdomsx", "kingdomsx"),
        Map.entry("cp", "commandpanels"),
        Map.entry("ic", "interactivechat"),
        Map.entry("chatcontrol", "chatcontrol"),
        Map.entry("chatcontrolred", "chatcontrolred"),
        Map.entry("ccr", "chatcontrolred"),
        Map.entry("chatguard", "chatguard"),
        Map.entry("chunky", "chunky"),
        Map.entry("dsrv", "discordsrv"),
        Map.entry("headdb", "HeadDatabase"),
        Map.entry("hdb", "HeadDatabase"),
        Map.entry("pvpm", "PvPManager"),
        Map.entry("pvpmanager", "PvPManager"),
        Map.entry("dh", "DecentHolograms"),
        Map.entry("decentholograms", "DecentHolograms"),
        Map.entry("hd", "HolographicDisplays"),
        Map.entry("holograms", "HolographicDisplays"),
        Map.entry("ce", "CrazyEnchantments"),
        Map.entry("crazyenchant", "CrazyEnchantments"),
        Map.entry("crazyenchants", "CrazyEnchantments"),
        Map.entry("portal", "AdvancedPortals"),
        Map.entry("advancedportals", "AdvancedPortals"),
        Map.entry("aportals", "AdvancedPortals"),
        Map.entry("controlp", "ControlPlayer"),
        Map.entry("controlplayer", "ControlPlayer"),
        Map.entry("cplayer", "ControlPlayer"),
        Map.entry("lpc", "LPC"),
        Map.entry("checkhacks", "CheckHacks"),
        Map.entry("itemedit", "ItemEdit"),
        Map.entry("iedit", "ItemEdit"),
        Map.entry("itemtag", "ItemTag"),
        Map.entry("mem", "MemCheck"),
        Map.entry("memcheck", "MemCheck"),
        Map.entry("rainbow", "RainbowBlocks"),
        Map.entry("rb", "RainbowBlocks"),
        Map.entry("rainbowblocks", "RainbowBlocks"),
        Map.entry("ashulkers", "AxShulkers"),
        Map.entry("axshulkers", "AxShulkers"),
        Map.entry("cmine", "CataMines"),
        Map.entry("catamines", "CataMines"),
        Map.entry("pa", "Parkour"),
        Map.entry("parkour", "Parkour"),
        Map.entry("crashplayer", "PlayerCrasher"),
        Map.entry("playercrasher", "PlayerCrasher"),
        Map.entry("wesui", "WorldEditSUI"),
        Map.entry("worldeditsui", "WorldEditSUI"),
        Map.entry("toastedafk", "ToastedAFK"),
        Map.entry("serversnpc", "ServersNPC"),
        Map.entry("znpcs", "ServersNPC"),
        Map.entry("mcpai", "McpAIPlugin"),
        Map.entry("commandwhitelist", "CommandWhitelist"),
        Map.entry("cmdwl", "CommandWhitelist"),
        Map.entry("discordsrv", "discordsrv")
    );

    private static final String[] COMMON_PLUGIN_NAMESPACES = {
        "essentials", "essentialsx", "worldedit", "worldguard", "luckperms", "vault",
        "citizens", "cmi", "cmilib", "multiverse-core", "multiverse", "viaversion",
        "viabackwards", "viarewind", "geysermc", "geyser", "floodgate", "protocollib",
        "coreprotect", "griefprevention", "shopkeepers", "dynmap", "placeholderapi",
        "skinsrestorer", "skript", "advancedanticheat", "vulcan", "grimac", "matrix",
        "spartan", "aac", "karhu", "verus", "nocheatplus", "authme", "authmereloaded",
        "librelogin", "nlogin", "openlogin", "limboauth", "loginsecurity", "fastlogin",
        "bungeeguard", "deluxemenus",
        "plotsquared", "supervanish", "packetevents", "oraxen", "itemsadder",
        "fawe", "fastasyncworldedit", "luckpermsbukkit", "essentialsgeoip", "essentialsprotect",
        "essentialsspawn", "essentialsxspawn", "multiverse-inventories", "multiverse-netherportals",
        "worldborder", "votifier", "nuVotifier", "votingplugin", "excellentcrates", "crazycrates",
        "goldencrates", "goldencrate", "cratereloaded", "magiccrates", "simplelootcrates",
        "cratesplus", "cratekeys", "advancedcrates", "phoenixcrates", "mysterycrates",
        "deluxecrates", "crazyenvoys", "crazyrewards", "jobs", "jobsreborn", "mcmmo", "towny", "factions", "factionsuuid",
        "lands", "residence", "claimchunk", "quickshop", "quickshop-hikari", "chestshop",
        "shopgui", "auctionhouse", "combatlogx", "litebans", "advancedban", "libertybans",
        "luckpermsgui", "tab", "tablist", "scoreboard", "animatedscoreboard", "ajleaderboards",
        "ajqueue", "spark", "sparkbukkit", "plan", "minimotd", "protocolsupport",
        "excellentenchants", "eco", "ecoenchants", "mythicmobs", "mythiclib", "modelengine",
        "mmoitems", "mmocore", "denizen", "citizenscmd", "sentinel", "npcs", "proantitab",
        "pat", "papiproxybridge", "groupmanager", "vulcanbungee",
        "grimacbukkit", "negativity", "intave", "polar", "horizon", "themis", "libreforge",
        "casinoslots", "slotmachine", "slotmachineplus", "roulette", "casino", "crazycasino",
        "coinflip", "deluxecoinflip", "crazycoinflip", "jackpot", "lottery", "lotteryplus",
        "blackjack", "crash", "plinko", "casebattle",
        "fancyholograms", "fancynpcs", "bentobox",
        "huskclaims", "husktowns", "protectionstones", "redprotect", "lwc", "lwcx",
        "kingdoms", "kingdomsx", "griefdefender", "openpartiesandclaims",
        "polymer", "commandpanels", "interactivechat",
        "chatcontrol", "chatcontrolred", "chatguard",
        "economyshopgui", "shopguiplus", "bossshop", "bossshoppro",
        "crazyauctions", "zauctionhouse", "auctionmaster", "auctionguiplus",
        "excellentauctionhouse", "axauctionhouse", "axauctions",
        "axcrates", "azcrates", "specializedcrates",
        "auraskills", "aureliumskills",
        "betonquest", "quests", "questing",
        "discordsrv",
        "combatlogx", "combatlog", "combattag",
        "chunky",
        "cardinal-components", "cca",
        "shopx", "bettershop", "bettershops", "guishop", "simpleshopgui",
        "nexo", "elitemobs",
        "customstructures", "anticheat"
    };

    private static final Set<String> CLIENT_SIDE_NAMESPACES = new HashSet<>();
    static {

        Collections.addAll(CLIENT_SIDE_NAMESPACES,
            "labymod", "labymod3", "essential", "essentialmod", "feather", "feathermod",
            "minecraftafk", "donutaddon", "donutsmp", "nighthawk", "badlion", "lunac",

            "optiplus", "optiplus-crystal", "optiplus-anchor",
            "clientcrystal", "fastcrystal", "consumable_optimizer",

            "xaerominimap", "xaeroworldmap", "voxelmap", "journeymap",
            "ftbchunks", "ftbteams", "worldinfo",

            "voicechat", "vc", "plasmovoice", "plasmo", "emotecraft",

            "fabric", "fabric-networking-api-v1", "fabric-networking-v0",
            "quilt", "quilt-networking", "quilt-networking-api-v1",
            "forge", "fml", "neoforge", "modmenu",

            "jei", "emi", "rei", "roughlyenoughitems", "justenoughitems",
            "cloth-config", "configured",

            "meteorclient", "meteor", "orbiter"
        );
    }

    private static final String[] HIGH_VALUE_PLUGIN_HINTS = {
        "essentials", "essentialsx", "worldedit", "worldguard", "luckperms", "vault",
        "cmi", "citizens", "multiverse", "viaversion", "geyser", "floodgate",
        "protocollib", "coreprotect", "placeholderapi", "skinsrestorer", "skript",
        "vulcan", "grimac", "matrix", "spartan", "authme", "librelogin", "nlogin",
        "limboauth", "loginsecurity", "crazycrates", "excellentcrates", "goldencrates",
        "cratereloaded", "deluxemenus", "plotsquared", "tab", "spark", "proantitab"
    };

    private static final Set<String> SCANNER_AUTOCOMPLETE_BLOCKLIST = Set.of(
        "aliases", "bukkit:aliases", "icanhasbukkit", "plugins", "plugin", "pl",
        "version", "ver", "about", "help", "?", "bukkit:plugins", "bukkit:pl",
        "bukkit:version", "bukkit:ver", "bukkit:help", "minecraft:help"
    );

    private static final Map<String, String> COMMAND_FEATURE_LABELS = Map.ofEntries(
        Map.entry("shop", "Shop"), Map.entry("shops", "Shop"), Map.entry("store", "Shop"),
        Map.entry("stores", "Shop"), Map.entry("market", "Market"), Map.entry("marketplace", "Market"),
        Map.entry("ah", "Auction"), Map.entry("auction", "Auction"), Map.entry("auctions", "Auction"),
        Map.entry("crate", "Crates"), Map.entry("crates", "Crates"), Map.entry("key", "Crates"),
        Map.entry("keys", "Crates"), Map.entry("case", "Crates"), Map.entry("cases", "Crates"),
        Map.entry("box", "Crates"), Map.entry("boxes", "Crates"), Map.entry("lootbox", "Crates"),
        Map.entry("lootboxes", "Crates"), Map.entry("cratekey", "Crates"), Map.entry("cratekeys", "Crates"),
        Map.entry("votekey", "Crates"), Map.entry("votekeys", "Crates"),
        Map.entry("casino", "Gambling"), Map.entry("slot", "Gambling"), Map.entry("slots", "Gambling"),
        Map.entry("slotmachine", "Gambling"), Map.entry("roulette", "Gambling"), Map.entry("coinflip", "Gambling"),
        Map.entry("cf", "Gambling"), Map.entry("jackpot", "Gambling"), Map.entry("lottery", "Gambling"),
        Map.entry("lotto", "Gambling"), Map.entry("raffle", "Gambling"), Map.entry("dice", "Gambling"),
        Map.entry("blackjack", "Gambling"), Map.entry("poker", "Gambling"), Map.entry("crash", "Gambling"),
        Map.entry("plinko", "Gambling"), Map.entry("casebattle", "Gambling"), Map.entry("casebattles", "Gambling"),
        Map.entry("wheel", "Gambling"), Map.entry("spin", "Gambling"),
        Map.entry("login", "Auth"), Map.entry("register", "Auth"), Map.entry("reg", "Auth"),
        Map.entry("logout", "Auth"), Map.entry("unregister", "Auth"), Map.entry("changepassword", "Auth"),
        Map.entry("changepw", "Auth"), Map.entry("captcha", "Auth"), Map.entry("2fa", "Auth"),
        Map.entry("totp", "Auth"), Map.entry("email", "Auth"), Map.entry("verify", "Auth"),
        Map.entry("verification", "Auth"), Map.entry("premium", "Auth"), Map.entry("cracked", "Auth"),
        Map.entry("backpack", "Backpacks"), Map.entry("backpacks", "Backpacks"), Map.entry("bp", "Backpacks"),
        Map.entry("pv", "Player Vaults"), Map.entry("playervault", "Player Vaults"),
        Map.entry("playervaults", "Player Vaults"), Map.entry("vaults", "Player Vaults"),
        Map.entry("enderchest", "Ender Chest"), Map.entry("echest", "Ender Chest"), Map.entry("ec", "Ender Chest"),
        Map.entry("shulker", "Shulker"), Map.entry("shulkers", "Shulker"),
        Map.entry("minion", "Minions"), Map.entry("minions", "Minions"),
        Map.entry("mine", "Mines"), Map.entry("mines", "Mines"),
        Map.entry("kit", "Kits"), Map.entry("kits", "Kits")
    );

    private static final Set<String> ANTICHEATS = Set.of(
        "nocheatplus", "aac", "spartan", "matrix", "vulcan", "grim",
        "grimac", "intave", "karhu", "verus", "polar", "negativity",
        "themis", "fairfight", "wraith", "horizon", "reflex", "antiaura",
        "guardian", "hac", "thotpatrol", "alice"
    );

    private enum ProbeKind { ROOT, HELP, PLUGIN_LIST, VERSION, NAMESPACE }

    public enum Confidence { EXACT, STRONG, FEATURE, UNKNOWN }

    public static class DetectedPlugin {
        public final String name;
        public final String category;
        public final String evidence;
        public final Confidence confidence;
        public final List<String> commands;

        public DetectedPlugin(String name, String category, String evidence, Confidence confidence) {
            this.name = name;
            this.category = category;
            this.evidence = evidence;
            this.confidence = confidence;
            this.commands = new ArrayList<>();
        }
    }

    private static class ProbeSpec {
        final String query;
        final ProbeKind kind;
        final String hint;
        int attempts;

        ProbeSpec(String query, ProbeKind kind, String hint) {
            this.query = query;
            this.kind = kind;
            this.hint = hint;
            this.attempts = 0;
        }
    }

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgProbing = settings.createGroup("Probing");

    private final Setting<Boolean> scanOnJoin = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-on-join").description("Auto-scan when joining a server.")
        .defaultValue(true).build());

    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
        .name("scan-delay").description("Ticks to wait before scanning after join.")
        .defaultValue(20).min(0).sliderRange(0, 200).build());

    private final Setting<Boolean> showResults = sgGeneral.add(new BoolSetting.Builder()
        .name("show-results").description("Display scan results in chat on completion.")
        .defaultValue(true).build());

    private final Setting<Boolean> scanChannels = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-channels").description("Monitor custom payloads for plugin channels.")
        .defaultValue(true).build());

    private final Setting<Boolean> scanCommandTree = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-command-tree").description("Analyze the Brigadier command tree.")
        .defaultValue(true).build());

    private final Setting<Boolean> enableProbing = sgProbing.add(new BoolSetting.Builder()
        .name("enable-probing").description("Send systematic tab-complete probes.")
        .defaultValue(true).build());

    private final Setting<Integer> probeDelay = sgProbing.add(new IntSetting.Builder()
        .name("probe-delay").description("Ticks between probe batches.")
        .defaultValue(1).min(1).sliderRange(1, 10).visible(enableProbing::get).build());

    private final Setting<Integer> probesPerTick = sgProbing.add(new IntSetting.Builder()
        .name("probes-per-tick").description("Probes sent per tick (higher = faster).")
        .defaultValue(3).min(1).sliderRange(1, 10).visible(enableProbing::get).build());

    private final Setting<Integer> maxProbes = sgProbing.add(new IntSetting.Builder()
        .name("max-probes").description("Maximum probes to send (0 = unlimited).")
        .defaultValue(0).min(0).sliderRange(0, 500).visible(enableProbing::get).build());

    private final Setting<Integer> probeRetries = sgProbing.add(new IntSetting.Builder()
        .name("probe-retries").description("How many times to resend a probe when the send fails.")
        .defaultValue(3).min(0).max(8).sliderRange(0, 8).visible(enableProbing::get).build());

    private final Setting<Boolean> observeOwnCommands = sgProbing.add(new BoolSetting.Builder()
        .name("observe-own-commands").description("Use commands you execute as evidence for plugin detection.")
        .defaultValue(true).build());

    private final Setting<Boolean> useCache = sgGeneral.add(new BoolSetting.Builder()
        .name("use-cache").description("Load and save scan results per server so rejoins show them instantly.")
        .defaultValue(true).build());

    private final Setting<Integer> cacheAgeHours = sgGeneral.add(new IntSetting.Builder()
        .name("cache-age-hours").description("How long a cached scan is considered fresh before a full rescan.")
        .defaultValue(24).min(1).max(168).sliderRange(1, 168).build());

    private final Map<String, DetectedPlugin> detectedPlugins = new LinkedHashMap<>();

    private final Set<String> observedPluginCommands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private final Map<Integer, ProbeSpec> probeMap = new LinkedHashMap<>();

    private final Deque<ProbeSpec> probeQueue = new ArrayDeque<>();

    private final Queue<Object> pendingPackets = new ConcurrentLinkedQueue<>();

    private boolean scanScheduled;
    private int scanTickCounter;
    private boolean scanned;

    private boolean probing;
    private int probeTickCounter;
    private static final int PROBE_ID_BASE = 1_000_000_000 + new Random().nextInt(100_000_000);
    private int nextCompletionId = PROBE_ID_BASE;
    private int totalProbesSent;
    private int totalProbesQueued;
    private int sentProbes;
    private int retriedProbes;
    private int failedProbes;

    private String serverBrand;
    private String serverIp;
    private String serverVersion;
    private int protocolVersion;
    private int totalCommandsScanned;

    private volatile boolean waitingForPluginList;
    private int querySentTick;
    private static final Pattern PLUGIN_LIST_PATTERN = Pattern.compile(
        "(?:plugins|running plugins|server plugins|\\d+ plugins)[^:]*?:\\s*(.+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PLUGIN_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_+\\-. ]+");

    public PeakPluginScanner() {
        super(Orbiter.CATEGORY, "peak-plugin-scanner",
            "Detects server plugins.");
    }

    @Override
    public void onActivate() {
        reset();
        if (scanOnJoin.get()) {
            scanScheduled = true;
            scanTickCounter = 0;
        }
    }

    @Override
    public void onDeactivate() {
        probing = false;
        waitingForPluginList = false;
        saveCache();
        RawPacketCapture.clearPending();
    }

    public boolean shouldCaptureChannels() {
        return isActive() && scanChannels.get();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        saveCache();
        reset();
    }

    private void reset() {
        detectedPlugins.clear();
        observedPluginCommands.clear();
        probeMap.clear();
        probeQueue.clear();
        pendingPackets.clear();
        scanScheduled = false;
        scanTickCounter = 0;
        scanned = false;
        probing = false;
        probeTickCounter = 0;
        nextCompletionId = PROBE_ID_BASE;
        totalProbesSent = 0;
        totalProbesQueued = 0;
        sentProbes = 0;
        retriedProbes = 0;
        failedProbes = 0;
        serverBrand = null;
        serverIp = null;
        serverVersion = null;
        protocolVersion = 0;
        totalCommandsScanned = 0;
        waitingForPluginList = false;
        querySentTick = 0;
        RawPacketCapture.resetStats();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        if (event.packet instanceof ClientboundCustomPayloadPacket
            || event.packet instanceof ClientboundCommandSuggestionsPacket
            || (waitingForPluginList && event.packet instanceof ClientboundSystemChatPacket)) {
            pendingPackets.add(event.packet);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive() || !observeOwnCommands.get()) return;

        String command = null;
        if (event.packet instanceof ServerboundChatCommandPacket pkt) {
            command = pkt.command();
        } else if (event.packet instanceof ServerboundChatPacket pkt) {
            String msg = pkt.message();
            if (msg != null && msg.trim().startsWith("/")) {
                command = msg.trim().substring(1);
            }
        }
        if (command == null || command.isBlank()) return;

        String token = addObservedPluginCommand(command);
        if (!token.isEmpty()) inferPluginsFromObservedCommands();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null || mc.level == null) return;

        drainPendingPackets();

        if (scanScheduled) {
            scanTickCounter++;
            if (scanTickCounter >= scanDelay.get()) {
                scanScheduled = false;
                executeScan();
            }
        }

        if (probing) {
            probeTickCounter++;
            if (probeTickCounter >= probeDelay.get()) {
                probeTickCounter = 0;

                int batchSize = probesPerTick.get();
                for (int i = 0; i < batchSize && probing; i++) {
                    sendNextProbe();
                }
            }
        }

        if (waitingForPluginList) {
            querySentTick++;
            if (querySentTick > 60) waitingForPluginList = false;
        }

        if (scanChannels.get()) processRawBytes();

        if (totalCommandsScanned == 0 || mc.player.tickCount % 100 == 0) fetchServerInfo();
    }

    private void drainPendingPackets() {
        Object packet;
        while ((packet = pendingPackets.poll()) != null) {
            if (packet instanceof ClientboundCustomPayloadPacket pkt) {
                handlePayload(pkt.payload());
            }
            if (packet instanceof ClientboundCommandSuggestionsPacket pkt) {
                onCommandSuggestions(pkt.id(), pkt);
            }
            if (waitingForPluginList && packet instanceof ClientboundSystemChatPacket pkt) {
                handlePluginListResponse(pkt.content().getString());
            }
        }
    }

    private void executeScan() {
        if (scanned) return;
        scanned = true;

        fetchServerInfo();

        if (useCache.get() && tryLoadCache()) {
            return;
        }

        if (scanCommandTree.get()) {
            parseCommandTree();
            inferPluginsFromObservedCommands();
        }

        if (enableProbing.get()) {
            buildPluginProbes();
            probing = true;
            probeTickCounter = 0;
            if (mc.player != null) {
                mc.player.sendSystemMessage(txt("§e[PeakScanner] §7Starting probe scan: §f" + totalProbesQueued + " §7probes queued"));
            }
        }

    }

    private void parseCommandTree() {
        if (mc.player == null || mc.player.connection == null) return;
        CommandDispatcher<?> dispatcher = mc.player.connection.getCommands();
        if (dispatcher == null || dispatcher.getRoot() == null) return;

        totalCommandsScanned = 0;

        for (CommandNode<?> child : dispatcher.getRoot().getChildren()) {
            String name = child.getName();
            if (name == null || name.isEmpty()) continue;
            totalCommandsScanned++;

            if (name.contains(":")) {

                String[] parts = name.split(":", 2);
                String ns = parts[0].toLowerCase(Locale.ROOT);
                String subCmd = parts.length > 1 ? parts[1] : "";

                if (!VANILLA_NAMESPACES.contains(ns)) {

                    String pluginKey = resolvePluginFromNamespace(ns);
                    if (pluginKey != null) {
                        String displayName = humanizePluginName(pluginKey);
                        addDetection(displayName, "CommandTree", "Cmd: /" + name, Confidence.STRONG);

                        addCommandToPlugin(displayKey(pluginKey), subCmd);
                    }
                }
            } else {

                addObservedPluginCommand(name);

                String token = normalizeCommandToken(name);
                if (!token.isEmpty() && COMMAND_FEATURE_LABELS.containsKey(token)) {
                    String label = COMMAND_FEATURE_LABELS.get(token);
                    addDetection(label, "Feature", "Cmd: /" + name, Confidence.FEATURE);
                }

                PluginDatabase.PluginEntry entry = PluginDatabase.lookupCommand(name);
                if (entry != null) {
                    addDetection(entry.name(), entry.category(), "Cmd: /" + name, Confidence.EXACT);
                    addCommandToPlugin(entry.name().toLowerCase(Locale.ROOT), name);
                }
            }
        }

        if (mc.player != null) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7Command tree: §f" + totalCommandsScanned + " §7roots scanned, §f" + observedPluginCommands.size() + " §7observed"));
        }
    }

    private void inferPluginsFromObservedCommands() {
        for (String command : observedPluginCommands) {
            String plugin = null;
            String clean = command.toLowerCase(Locale.ROOT);

            if (isKnownPluginNamespace(clean)) {
                plugin = clean;
            } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(clean)) {
                plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(clean);
            }

            if (plugin != null && !plugin.isEmpty()) {
                String displayName = humanizePluginName(plugin);
                addDetection(displayName, "CommandAlias", "Cmd: /" + command, Confidence.STRONG);
                addCommandToPlugin(displayKey(plugin), command);
            }
        }
    }

    private void buildPluginProbes() {
        probeQueue.clear();
        probeMap.clear();
        totalProbesSent = 0;
        totalProbesQueued = 0;

        addProbe(new ProbeSpec("/", ProbeKind.ROOT, null));
        addProbe(new ProbeSpec("/ ", ProbeKind.ROOT, null));

        addProbe(new ProbeSpec("/plugins", ProbeKind.PLUGIN_LIST, null));
        addProbe(new ProbeSpec("/pl", ProbeKind.PLUGIN_LIST, null));
        addProbe(new ProbeSpec("/bukkit:plugins", ProbeKind.PLUGIN_LIST, null));
        addProbe(new ProbeSpec("/bukkit:pl", ProbeKind.PLUGIN_LIST, null));

        addProbe(new ProbeSpec("/ver", ProbeKind.VERSION, null));
        addProbe(new ProbeSpec("/version", ProbeKind.VERSION, null));
        addProbe(new ProbeSpec("/about", ProbeKind.VERSION, null));
        addProbe(new ProbeSpec("/icanhasbukkit", ProbeKind.VERSION, null));
        addProbe(new ProbeSpec("/bukkit:ver", ProbeKind.VERSION, null));
        addProbe(new ProbeSpec("/bukkit:version", ProbeKind.VERSION, null));

        addProbe(new ProbeSpec("/help", ProbeKind.HELP, null));
        addProbe(new ProbeSpec("/?", ProbeKind.HELP, null));
        addProbe(new ProbeSpec("/bukkit:help", ProbeKind.HELP, null));
        addProbe(new ProbeSpec("/minecraft:help", ProbeKind.HELP, null));

        for (char c : ROOT_PROBE_PREFIXES.toCharArray()) {
            String s = String.valueOf(c);
            addProbe(new ProbeSpec("/" + s, ProbeKind.ROOT, s));
            addProbe(new ProbeSpec("/help " + s, ProbeKind.HELP, s));
            addProbe(new ProbeSpec("/? " + s, ProbeKind.HELP, s));
        }

        for (String ns : COMMON_PLUGIN_NAMESPACES) {
            addProbe(new ProbeSpec("/" + ns + ":", ProbeKind.NAMESPACE, ns));
        }

        for (String plugin : HIGH_VALUE_PLUGIN_HINTS) {
            addProbe(new ProbeSpec("/version " + plugin, ProbeKind.VERSION, plugin));
            addProbe(new ProbeSpec("/ver " + plugin, ProbeKind.VERSION, plugin));
            addProbe(new ProbeSpec("/help " + plugin, ProbeKind.HELP, plugin));
        }

        totalProbesQueued = probeQueue.size();
    }

    private void addProbe(ProbeSpec spec) {
        int limit = maxProbes.get();
        if (limit > 0 && totalProbesSent + probeQueue.size() >= limit) return;
        probeMap.put(nextCompletionId, spec);
        probeQueue.addLast(spec);
        nextCompletionId++;
    }

    private void sendNextProbe() {
        if (probeQueue.isEmpty()) {
            finishProbing();
            return;
        }

        ProbeSpec spec = probeQueue.pollFirst();
        if (spec == null) {
            finishProbing();
            return;
        }

        int probeId = -1;
        for (Map.Entry<Integer, ProbeSpec> entry : probeMap.entrySet()) {
            if (entry.getValue() == spec) {
                probeId = entry.getKey();
                break;
            }
        }
        if (probeId < 0) {

            return;
        }

        if (mc.getConnection() == null) {
            finishProbing();
            return;
        }

        try {
            mc.getConnection().send(new ServerboundCommandSuggestionPacket(probeId, spec.query));
            totalProbesSent++;
            sentProbes++;
        } catch (Exception e) {
            if (spec.attempts < probeRetries.get()) {
                spec.attempts++;
                retriedProbes++;
                probeQueue.addLast(spec);
            } else {
                failedProbes++;
            }
        }
    }

    private void finishProbing() {
        probing = false;

        inferPluginsFromObservedCommands();

        if (mc.getConnection() != null && mc.player != null) {
            waitingForPluginList = true;
            querySentTick = 0;
            mc.getConnection().sendCommand("pl");
        }

        if (mc.player != null) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7Probing complete. §f" + totalProbesSent + " §7probes sent, §f" + detectedPlugins.size() + " §7plugins found so far"));
        }

        if (showResults.get()) displayResults();
        saveCache();
    }

    private void onCommandSuggestions(int id, ClientboundCommandSuggestionsPacket packet) {
        ProbeSpec probe = probeMap.remove(id);
        if (probe == null) return;
        if (packet.suggestions() == null) return;

        for (ClientboundCommandSuggestionsPacket.Entry s : packet.suggestions()) {
            String text = s.text();
            if (text == null || text.isEmpty()) continue;

            String normalizedToken = normalizeCommandToken(text);

            if (!normalizedToken.isEmpty()) addObservedPluginCommand(normalizedToken);

            if (text.contains(":")) {

                String[] parts = text.split(":", 2);
                String ns = parts[0].toLowerCase(Locale.ROOT);
                String subCmd = parts.length > 1 ? parts[1] : "";

                if (!VANILLA_NAMESPACES.contains(ns)) {
                    String pluginKey = resolvePluginFromNamespace(ns);
                    if (pluginKey != null) {
                        addDetection(humanizePluginName(pluginKey), "Probe-NS", "Cmd: /" + text, Confidence.STRONG);
                        addCommandToPlugin(displayKey(pluginKey), subCmd);
                    }
                }
                continue;
            }

            if (probe.kind == ProbeKind.NAMESPACE && probe.hint != null) {

                if (normalizedToken.length() >= 3 && !normalizedToken.equals(probe.hint)) {
                    String pluginKey = resolvePluginFromNamespace(probe.hint);
                    if (pluginKey != null) {
                        addDetection(humanizePluginName(pluginKey), "Probe-NS", "Cmd: /" + probe.hint + ":" + normalizedToken, Confidence.STRONG);
                        addCommandToPlugin(displayKey(pluginKey), normalizedToken);
                    }
                }
            } else if (probe.kind == ProbeKind.PLUGIN_LIST || probe.kind == ProbeKind.VERSION) {

                if (isLikelyPluginNameCandidate(text)) {
                    String pluginName = text.trim();
                    Confidence conf = probe.kind == ProbeKind.PLUGIN_LIST ? Confidence.STRONG : Confidence.FEATURE;
                    String basis = probe.kind == ProbeKind.PLUGIN_LIST ? "PluginList" : "VersionHint";
                    addDetection(pluginName, basis, "Probe response from " + probe.query, conf);
                }
            } else if (probe.kind == ProbeKind.HELP && probe.hint != null) {

                if (normalizedToken.length() >= 2 && !normalizedToken.equals(probe.hint)) {
                    String pluginKey = resolvePluginFromNamespace(probe.hint);
                    if (pluginKey != null) {
                        addDetection(humanizePluginName(pluginKey), "Probe-Help", "Cmd: /" + normalizedToken, Confidence.FEATURE);
                        addCommandToPlugin(displayKey(pluginKey), normalizedToken);
                    }
                }
            } else if (probe.kind == ProbeKind.ROOT) {

                mergeScannerAutocompleteHint(normalizedToken);
            }
        }
    }

    private void mergeScannerAutocompleteHint(String token) {
        if (token == null || token.isEmpty()) return;
        String clean = normalizeCommandToken(token);
        if (clean.isEmpty()) return;

        if (!isScannerAutocompleteFallbackCandidate(clean)) {

            if (isKnownPluginNamespace(clean)) {
                addDetection(humanizePluginName(clean), "RootProbe", "Cmd: /" + clean, Confidence.STRONG);
                addCommandToPlugin(displayKey(clean), clean);
            } else if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(clean)) {
                String plugin = ROOT_COMMAND_PLUGIN_ALIASES.get(clean);
                addDetection(humanizePluginName(plugin), "RootAlias", "Cmd: /" + clean, Confidence.STRONG);
                addCommandToPlugin(displayKey(plugin), clean);
            }
            return;
        }

        PluginDatabase.PluginEntry entry = PluginDatabase.lookupCommand(clean);
        if (entry != null) {
            addDetection(entry.name(), entry.category(), "RootProbe: /" + clean, Confidence.EXACT);
            addCommandToPlugin(entry.name().toLowerCase(Locale.ROOT), clean);
            return;
        }

        String ownerKey = bestCommandOwnerKey(clean);
        if (ownerKey != null) {
            addCommandToPlugin(ownerKey, clean);
            return;
        }

    }

    private String bestCommandOwnerKey(String command) {
        if (command == null || command.isEmpty()) return null;
        String token = normalizeCommandToken(command);
        if (token.isEmpty()) return null;
        for (Map.Entry<String, DetectedPlugin> entry : new ArrayList<>(detectedPlugins.entrySet())) {
            DetectedPlugin dp = entry.getValue();
            if (dp == null || dp.commands == null) continue;
            if (dp.commands.contains(token)) return entry.getKey();
        }
        return null;
    }

    private boolean isScannerAutocompleteFallbackCandidate(String token) {
        if (!isTrackableCommandToken(token)) return false;
        String clean = token.trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 3) return false;
        if (SCANNER_AUTOCOMPLETE_BLOCKLIST.contains(clean)) return false;
        if (VANILLA_NAMESPACES.contains(clean) || VANILLA_COMMAND_ROOTS.contains(clean)) return false;
        if (COMMAND_FEATURE_LABELS.containsKey(clean)) return false;
        if (ROOT_COMMAND_PLUGIN_ALIASES.containsKey(clean) || isKnownPluginNamespace(clean)) return false;
        return true;
    }

    private void handlePayload(CustomPacketPayload payload) {
        if (!scanChannels.get()) return;

        if (payload instanceof BrandPayload brand) {
            if (serverBrand == null || serverBrand.isEmpty()) {
                serverBrand = brand.brand();
            }
        } else if (payload instanceof DiscardedPayload unknown) {
            Identifier channelId = unknown.id();
            String channelStr = channelId.toString();
            String ns = channelId.getNamespace();

            if (CLIENT_SIDE_NAMESPACES.contains(ns)) return;
            if (PluginDatabase.isNonPluginNamespace(ns)) return;

            PluginDatabase.PluginEntry entry = PluginDatabase.lookupChannel(channelStr);
            if (entry != null) {
                addDetection(entry.name(), entry.category(), "Channel: " + channelStr, Confidence.EXACT);
            } else {
                String alias = PluginDatabase.resolveNamespace(ns);
                if (alias != null) {
                    addDetection(alias, "Channel-NS", "NS: " + ns, Confidence.STRONG);
                }
            }
        }
    }

    private void processRawBytes() {
        List<String> rawChannels = RawPacketCapture.processPending();
        for (String channel : rawChannels) {
            int colon = channel.indexOf(':');
            if (colon <= 0) continue;
            String ns = channel.substring(0, colon);

            if (CLIENT_SIDE_NAMESPACES.contains(ns)) continue;
            if (PluginDatabase.isNonPluginNamespace(ns)) continue;

            PluginDatabase.PluginEntry entry = PluginDatabase.lookupChannel(channel);
            if (entry != null) {
                addDetection(entry.name(), entry.category(), "Raw: " + channel, Confidence.STRONG);
            } else {
                String alias = PluginDatabase.resolveNamespace(ns);
                if (alias != null) {
                    addDetection(alias, "Raw-NS", "RawNS: " + ns, Confidence.STRONG);
                }
            }
        }
    }

    private void handlePluginListResponse(String message) {
        if (!waitingForPluginList || message == null) return;
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("unknown command") || lower.contains("command not found")
            || lower.contains("no permission") || lower.contains("not allowed")) {
            waitingForPluginList = false;
            return;
        }

        Matcher matcher = PLUGIN_LIST_PATTERN.matcher(message);
        if (!matcher.find()) {
            if (message.length() < 5 || message.length() > 5000) {
                waitingForPluginList = false;
            }
            return;
        }

        String pluginListStr = matcher.group(1);
        Matcher nameMatcher = PLUGIN_NAME_PATTERN.matcher(pluginListStr);
        int found = 0;

        while (nameMatcher.find()) {
            String pluginName = nameMatcher.group().trim();
            if (pluginName.length() < 2) continue;
            String ln = pluginName.toLowerCase(Locale.ROOT);
            if (ln.equals("and") || ln.equals("none") || ln.equals("plugins")
                || ln.equals("showing") || ln.equals("running") || ln.equals("server")
                || ln.equals("total") || ln.equals("no") || ln.equals("has")) continue;
            addDetection(pluginName, "PluginList", "/plugins response", Confidence.STRONG);
            found++;
        }

        if (found > 0) {
            waitingForPluginList = false;
            if (mc.player != null) {
                mc.player.sendSystemMessage(txt("§e[PeakScanner] §7Plugin list: §f" + found + " §7plugins from /plugins"));
            }
        }
    }

    private void fetchServerInfo() {
        if (mc.player == null || mc.getConnection() == null) return;

        try {
            var serverInfo = mc.getConnection().getServerData();
            if (serverInfo != null) {
                if (serverInfo.ip != null) serverIp = serverInfo.ip;
                if (serverInfo.version != null && (serverVersion == null || serverVersion.isEmpty())) {
                    String v = serverInfo.version.getString();
                    if (v != null && !v.isBlank()) serverVersion = v;
                }
                if (serverInfo.protocol > 0) protocolVersion = serverInfo.protocol;
            }
        } catch (Exception ignored) {}

        if (protocolVersion == 0) protocolVersion = net.minecraft.SharedConstants.getProtocolVersion();
    }

    private String normalizeCommandToken(String raw) {
        if (raw == null) return "";
        String token = raw.trim();
        if (token.isEmpty()) return "";
        while (token.startsWith("/")) token = token.substring(1).trim();
        int spaceIndex = token.indexOf(' ');
        if (spaceIndex >= 0) token = token.substring(0, spaceIndex).trim();
        while (token.endsWith(":")) token = token.substring(0, token.length() - 1).trim();
        return token.toLowerCase(Locale.ROOT);
    }

    private boolean isKnownPluginNamespace(String key) {
        if (key == null || key.isEmpty()) return false;
        for (String ns : COMMON_PLUGIN_NAMESPACES) {
            if (ns.equalsIgnoreCase(key)) return true;
        }
        return false;
    }

    private boolean isTrackableCommandToken(String token) {
        if (token == null || token.isBlank()) return false;
        String clean = token.trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 2) return false;
        if (VANILLA_NAMESPACES.contains(clean) || VANILLA_COMMAND_ROOTS.contains(clean)) return false;
        return clean.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.' || ch == ':');
    }

    private boolean isLikelyPluginNameCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String key = candidate.trim().toLowerCase(Locale.ROOT);
        if (key.length() < 2) return false;
        if (key.contains(" ") || key.contains("/") || key.contains(":")) return false;
        if (VANILLA_NAMESPACES.contains(key)) return false;
        if (SCANNER_AUTOCOMPLETE_BLOCKLIST.contains(key)) return false;
        return key.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.');
    }

    private String resolvePluginFromNamespace(String ns) {
        if (ns == null || ns.isEmpty()) return null;
        String lower = ns.toLowerCase(Locale.ROOT);

        if (isKnownPluginNamespace(lower)) return lower;

        String alias = PluginDatabase.resolveNamespace(lower);
        if (alias != null) return alias;
        return null;
    }

    private String humanizePluginName(String namespace) {
        if (namespace == null || namespace.isEmpty()) return namespace;

        String alias = PluginDatabase.resolveNamespace(namespace);
        if (alias != null) return alias;

        StringBuilder sb = new StringBuilder();
        for (String part : namespace.split("[_\\-.]+")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.isEmpty() ? namespace : sb.toString();
    }

    private String addObservedPluginCommand(String raw) {
        String token = normalizeCommandToken(raw);
        if (!isTrackableCommandToken(token)) return token;
        observedPluginCommands.add(token);
        return token;
    }

    private String displayKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static final Map<String, String> CANONICAL_MAP;
    static {
        Map<String, String> m = new HashMap<>();

        m.put("grim", "grimac"); m.put("grimac", "grimac"); m.put("grimacbukkit", "grimac");

        m.put("authme", "authme"); m.put("authmereloaded", "authme");

        m.put("fawe", "fastasyncworldedit"); m.put("fastasyncworldedit", "fastasyncworldedit");

        m.put("worldedit", "worldedit"); m.put("we", "worldedit"); m.put("wedit", "worldedit");

        m.put("essentials", "essentialsx"); m.put("essentialsx", "essentialsx");
        m.put("essentialsspawn", "essentialsx"); m.put("essentialsxspawn", "essentialsx");
        m.put("essentialsprotect", "essentialsx"); m.put("essentialsgeoip", "essentialsx");

        m.put("viaversion", "viaversion"); m.put("vv", "viaversion"); m.put("viaver", "viaversion");

        m.put("supervanish", "supervanish"); m.put("premiumvanish", "supervanish");

        m.put("luckperms", "luckperms"); m.put("luckpermsbukkit", "luckperms");

        m.put("skript", "skript"); m.put("skript-placeholders", "skript");

        m.put("worldguard", "worldguard"); m.put("worldguardextraflags", "worldguardextraflags");
        CANONICAL_MAP = Collections.unmodifiableMap(m);
    }

    private String canonicalize(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        String canonical = CANONICAL_MAP.get(key);
        return canonical != null ? canonical : key;
    }

    private void addDetection(String name, String category, String evidence, Confidence confidence) {
        if (name == null || name.isEmpty()) return;
        String key = canonicalize(name);
        if (key.isEmpty()) return;

        if (VANILLA_NAMESPACES.contains(key)) return;

        if (key.equals("auth")) return;

        DetectedPlugin existing = detectedPlugins.get(key);
        if (existing == null) {
            detectedPlugins.put(key, new DetectedPlugin(name, category, evidence, confidence));
        } else {

            if (confidence.ordinal() < existing.confidence.ordinal()) {
                detectedPlugins.put(key, new DetectedPlugin(name, category, evidence, confidence));

                detectedPlugins.get(key).commands.addAll(existing.commands);
            }
        }
    }

    private void addCommandToPlugin(String pluginKey, String command) {
        if (pluginKey == null || command == null || command.isEmpty()) return;
        pluginKey = canonicalize(pluginKey);
        DetectedPlugin plugin = detectedPlugins.get(pluginKey);
        if (plugin != null && !plugin.commands.contains(command)) {
            plugin.commands.add(command);
        }
    }

    public void displayResults() {
        if (mc.player == null) return;
        if (detectedPlugins.isEmpty() && serverBrand == null) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7No plugins detected."));
            return;
        }

        List<String> anticheats = detectAnticheats();

        mc.player.sendSystemMessage(txt("§e═══════════════════════════════════"));
        mc.player.sendSystemMessage(txt("§e[PeakScanner] §f" + detectedPlugins.size() + " §7plugins detected"));
        if (serverBrand != null) mc.player.sendSystemMessage(txt("§b  Server: §f" + serverBrand + (serverVersion != null ? " " + serverVersion : "")));
        if (serverIp != null) mc.player.sendSystemMessage(txt("§b  IP: §f" + serverIp));
        if (!anticheats.isEmpty()) mc.player.sendSystemMessage(txt("§c  AntiCheat: §f" + String.join(", ", anticheats)));

        Map<Confidence, List<DetectedPlugin>> byConfidence = new LinkedHashMap<>();
        byConfidence.put(Confidence.EXACT, new ArrayList<>());
        byConfidence.put(Confidence.STRONG, new ArrayList<>());
        byConfidence.put(Confidence.FEATURE, new ArrayList<>());
        byConfidence.put(Confidence.UNKNOWN, new ArrayList<>());

        for (DetectedPlugin dp : new ArrayList<>(detectedPlugins.values())) {
            byConfidence.computeIfAbsent(dp.confidence, k -> new ArrayList<>()).add(dp);
        }

        for (Map.Entry<Confidence, List<DetectedPlugin>> entry : byConfidence.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String color = switch (entry.getKey()) {
                case EXACT -> "§a";
                case STRONG -> "§b";
                case FEATURE -> "§e";
                case UNKNOWN -> "§7";
            };
            mc.player.sendSystemMessage(txt(color + "── " + entry.getKey().name() + " (" + entry.getValue().size() + ") ──"));

            StringBuilder sb = new StringBuilder("§7  ");
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) sb.append("§7, ");
                sb.append("§f").append(entry.getValue().get(i).name);
            }
            mc.player.sendSystemMessage(txt(sb.toString()));
        }

        mc.player.sendSystemMessage(txt("§e═══════════════════════════════════"));
    }

    public void copyToClipboard() {
        if (mc.player == null) return;
        if (detectedPlugins.isEmpty()) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7No plugins detected yet."));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Peak Plugin Scanner Results\n");
        sb.append("===========================\n");
        sb.append("Server: ").append(serverBrand != null ? serverBrand : "unknown").append("\n");
        sb.append("IP: ").append(serverIp != null ? serverIp : "unknown").append("\n");
        sb.append("Plugins detected: ").append(detectedPlugins.size()).append("\n\n");

        for (Confidence conf : Confidence.values()) {
            List<DetectedPlugin> group = new ArrayList<>();
            for (DetectedPlugin dp : new ArrayList<>(detectedPlugins.values())) {
                if (dp.confidence == conf) group.add(dp);
            }
            if (group.isEmpty()) continue;
            group.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            sb.append("── ").append(conf.name()).append(" (").append(group.size()).append(") ──\n");
            for (DetectedPlugin dp : group) {
                sb.append("  ").append(dp.name);
                if (!dp.commands.isEmpty()) {
                    sb.append("  [").append(String.join(", ", dp.commands)).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        try {
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
            mc.player.sendSystemMessage(txt("§a[PeakScanner] §7Copied §f" + detectedPlugins.size() + " §7plugins to clipboard."));
        } catch (Exception e) {
            mc.player.sendSystemMessage(txt("§c[PeakScanner] Failed to copy: " + e.getMessage()));
        }
    }

    public void displayList() {
        if (mc.player == null) return;
        if (detectedPlugins.isEmpty()) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7No plugins detected yet. Enable and wait for scan."));
            return;
        }

        mc.player.sendSystemMessage(txt("§e[PeakScanner] §f" + detectedPlugins.size() + " plugins:"));

        List<DetectedPlugin> sorted = new ArrayList<>(detectedPlugins.values());
        sorted.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        StringBuilder sb = new StringBuilder("§7  ");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append("§7, ");
            sb.append("§f").append(sorted.get(i).name);
            if (sb.length() > 200) {
                mc.player.sendSystemMessage(txt(sb.toString()));
                sb = new StringBuilder("§7  ");
            }
        }
        if (sb.length() > 4) mc.player.sendSystemMessage(txt(sb.toString()));
    }

    public void exportToFile() {
        if (mc.player == null) return;
        try {
            File file = new File("peakscan_results.txt");
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                pw.println("Peak Plugin Scanner Results");
                pw.println("===========================");
                pw.println("Date: " + new Date());
                pw.println("Brand: " + (serverBrand != null ? serverBrand : "unknown"));
                pw.println("Version: " + (serverVersion != null ? serverVersion : "unknown"));
                pw.println("IP: " + (serverIp != null ? serverIp : "unknown"));
                pw.println("Protocol: " + protocolVersion);
                pw.println("Total commands scanned: " + totalCommandsScanned);
                pw.println("Total plugins detected: " + detectedPlugins.size());
                pw.println("Total probes sent: " + totalProbesSent);
                pw.println();

                List<String> anticheats = detectAnticheats();
                if (!anticheats.isEmpty()) {
                    pw.println("AntiCheat detected: " + String.join(", ", anticheats));
                    pw.println();
                }

                for (Confidence conf : Confidence.values()) {
                    List<DetectedPlugin> group = new ArrayList<>();
                    for (DetectedPlugin dp : new ArrayList<>(detectedPlugins.values())) {
                        if (dp.confidence == conf) group.add(dp);
                    }
                    if (group.isEmpty()) continue;

                    group.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                    pw.println("── " + conf.name() + " (" + group.size() + " plugins) " + "─".repeat(40));
                    for (DetectedPlugin dp : group) {
                        pw.println("  " + dp.name);
                        pw.println("    Category: " + dp.category);
                        pw.println("    Evidence: " + dp.evidence);
                        if (!dp.commands.isEmpty()) {
                            pw.println("    Commands: " + String.join(", ", dp.commands));
                        }
                        pw.println();
                    }
                }
            }

            mc.player.sendSystemMessage(txt("§a[PeakScanner] §7Exported to §fpeakscan_results.txt"));
        } catch (Exception e) {
            mc.player.sendSystemMessage(txt("§c[PeakScanner] Export failed: " + e.getMessage()));
        }
    }

    public String getServerBrand() { return serverBrand; }
    public String getServerVersion() { return serverVersion; }
    public String getServerIp() { return serverIp; }
    public int getProtocolVersion() { return protocolVersion; }
    public int getDetectedCount() { return detectedPlugins.size(); }
    public int getTotalProbesSent() { return totalProbesSent; }
    public int getTotalCommandsScanned() { return totalCommandsScanned; }

    public Collection<DetectedPlugin> getDetectedPlugins() {
        return Collections.unmodifiableCollection(new ArrayList<>(detectedPlugins.values()));
    }

    public List<String> getPluginNames() {
        List<String> names = new ArrayList<>();
        for (DetectedPlugin dp : new ArrayList<>(detectedPlugins.values())) names.add(dp.name);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> detectAnticheats() {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, DetectedPlugin> entry : new ArrayList<>(detectedPlugins.entrySet())) {
            if (ANTICHEATS.contains(entry.getKey())) {
                found.add(entry.getValue().name);
            }
        }
        return found;
    }

    public List<String> getDetectedAnticheats() {
        return detectAnticheats();
    }

    public void forceScan() {
        reset();
        scanScheduled = true;
        scanTickCounter = 0;
    }

    public void forceProbes() {
        if (!scanned) executeScan();
        else {
            buildPluginProbes();
            probing = true;
            probeTickCounter = 0;
        }
    }

    public void forceStop() {
        probing = false;
        waitingForPluginList = false;
        if (mc.player != null) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7Stopped. §f" + detectedPlugins.size() + " §7plugins detected."));
        }
        if (showResults.get() && !detectedPlugins.isEmpty()) displayResults();
    }

    private String currentAddress() {
        try {
            if (mc.getConnection() != null && mc.getConnection().getServerData() != null) {
                String ip = mc.getConnection().getServerData().ip;
                if (ip != null && !ip.isBlank()) return ip;
            }
        } catch (Exception ignored) {
        }
        return serverIp;
    }

    private boolean tryLoadCache() {
        String address = currentAddress();
        if (address == null || address.isBlank()) return false;

        PeakScanCache.Entry cached = PeakScanCache.get(address);
        if (cached == null || cached.plugins == null || cached.plugins.isEmpty()) return false;
        if (!"COMPLETE".equals(cached.status)) return false;

        long maxAgeMs = cacheAgeHours.get() * 3600_000L;
        if (System.currentTimeMillis() - cached.scannedAtMs > maxAgeMs) return false;

        seedFromCache(cached);
        if (mc.player != null) {
            mc.player.sendSystemMessage(txt("§e[PeakScanner] §7Loaded §f" + detectedPlugins.size()
                + " §7plugins from cache (§f" + address + "§7). Live evidence still being collected."));
        }
        return true;
    }

    private void seedFromCache(PeakScanCache.Entry entry) {
        detectedPlugins.clear();
        for (PeakScanCache.PluginData data : entry.plugins) {
            if (data == null || data.name == null || data.name.isBlank()) continue;
            Confidence conf = Confidence.UNKNOWN;
            try {
                conf = Confidence.valueOf(data.confidence);
            } catch (Exception ignored) {
            }
            DetectedPlugin dp = new DetectedPlugin(data.name, data.category, data.evidence, conf);
            if (data.commands != null) dp.commands.addAll(data.commands);
            detectedPlugins.put(canonicalize(data.name), dp);
        }
    }

    private void saveCache() {
        if (!useCache.get()) return;
        String address = currentAddress();
        if (address == null || address.isBlank()) return;

        PeakScanCache.Entry entry = new PeakScanCache.Entry();
        entry.address = address;
        entry.name = address;
        entry.brand = serverBrand == null ? "" : serverBrand;
        entry.version = serverVersion == null ? "" : serverVersion;
        entry.status = probing ? "IN_PROGRESS" : scanned ? "COMPLETE" : "PENDING";
        entry.scannedAtMs = System.currentTimeMillis();
        entry.totalProbes = totalProbesQueued;
        entry.sentProbes = sentProbes;

        for (DetectedPlugin dp : new ArrayList<>(detectedPlugins.values())) {
            PeakScanCache.PluginData data = new PeakScanCache.PluginData();
            data.name = dp.name;
            data.category = dp.category;
            data.evidence = dp.evidence;
            data.confidence = dp.confidence.name();
            data.commands = new ArrayList<>(dp.commands);
            entry.plugins.add(data);
        }

        PeakScanCache.put(address, entry);
    }

    private static Component txt(String s) { return Component.literal(s); }
}
