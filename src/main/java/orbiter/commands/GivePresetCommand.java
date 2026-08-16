package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.network.Filterable;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class GivePresetCommand extends Command {

    private final Map<String, PresetFactory> presets = new LinkedHashMap<>();

    public GivePresetCommand() {
        super("orbitergivepreset", "Gives 120+ special preset items with lore.", "ogp");
        registerPresets();
    }

    private void registerPresets() {

        presets.put("god-sword", this::godSword);
        presets.put("god-bow", this::godBow);
        presets.put("god-helmet", () -> godArmor(Items.NETHERITE_HELMET, "God Helmet", 0));
        presets.put("god-chestplate", () -> godArmor(Items.NETHERITE_CHESTPLATE, "God Chestplate", 1));
        presets.put("god-leggings", () -> godArmor(Items.NETHERITE_LEGGINGS, "God Leggings", 2));
        presets.put("god-boots", () -> godArmor(Items.NETHERITE_BOOTS, "God Boots", 3));
        presets.put("god-axe", this::godAxe);
        presets.put("god-pickaxe", this::godPickaxe);
        presets.put("god-trident", this::godTrident);
        presets.put("god-mace", this::godMace);
        presets.put("god-crossbow", this::godCrossbow);
        presets.put("god-fishing-rod", this::godFishingRod);

        presets.put("excalibur", this::excalibur);
        presets.put("poseidon-trident", this::poseidon);
        presets.put("mjolnir", this::mjolnir);
        presets.put("anubis", this::anubis);
        presets.put("thunder-blade", this::thunderBlade);
        presets.put("frostbite", this::frostbite);
        presets.put("infinity-blade", this::infinityBlade);
        presets.put("smite-sword", this::smiteSword);
        presets.put("arthropod-axe", this::arthropodAxe);
        presets.put("fortune-pick", this::fortunePick);
        presets.put("silk-pick", this::silkPick);
        presets.put("riptide-trident", this::riptideTrident);
        presets.put("channeling-trident", this::channelingTrident);
        presets.put("feather-boots", this::featherBoots);
        presets.put("respiration-helmet", this::respirationHelmet);
        presets.put("thorns-shield", this::thornsShield);
        presets.put("speed-boots", this::speedBoots);
        presets.put("frost-boots", this::frostBoots);
        presets.put("looting-sword", this::lootingSword);

        presets.put("totem-stack", () -> stackLore(Items.TOTEM_OF_UNDYING, 64, "Totem Stack", ChatFormatting.GOLD, "Never die again", "64 lives in your pocket"));
        presets.put("elytra", this::elytra);
        presets.put("firework-stack", () -> stackLore(Items.FIREWORK_ROCKET, 64, "Firework Stack", ChatFormatting.RED, "Launch into the sky", "64 rockets for elytra flights"));
        presets.put("golden-apple-stack", () -> stackLore(Items.ENCHANTED_GOLDEN_APPLE, 64, "Notch Apples", ChatFormatting.GOLD, "The most powerful food", "64 golden legends"));
        presets.put("ender-pearl-stack", () -> stackLore(Items.ENDER_PEARL, 64, "Ender Pearl Stack", ChatFormatting.DARK_PURPLE, "Teleport anywhere", "64 pearls of void travel"));
        presets.put("chorus-fruit-stack", () -> stackLore(Items.CHORUS_FRUIT, 64, "Chorus Fruit Stack", ChatFormatting.LIGHT_PURPLE, "Random teleportation food", "64 fruits of the end"));
        presets.put("crystal-stack", () -> stackLore(Items.END_CRYSTAL, 64, "End Crystal Stack", ChatFormatting.AQUA, "Explosive decorations", "64 crystals of destruction"));
        presets.put("saddle-stack", () -> stackLore(Items.SADDLE, 64, "Saddle Stack", ChatFormatting.GOLD, "Ride anything", "64 saddles for every mount"));
        presets.put("name-tag-stack", () -> stackLore(Items.NAME_TAG, 64, "Name Tag Stack", ChatFormatting.AQUA, "Name everything", "64 blank name tags"));
        presets.put("trident-stack", () -> stackLore(Items.TRIDENT, 64, "Trident Stack", ChatFormatting.AQUA, "64 tridents of the deep", "Raining weapons"));
        presets.put("totem-of-dying", this::totemOfDying);

        presets.put("command-block", () -> simpleLore(Items.COMMAND_BLOCK, "Command Block", ChatFormatting.GOLD, "The power block", "Run any command on placement"));
        presets.put("chain-command-block", () -> simpleLore(Items.CHAIN_COMMAND_BLOCK, "Chain Command Block", ChatFormatting.GOLD, "Chain reactions", "Runs after the previous block"));
        presets.put("repeating-command-block", () -> simpleLore(Items.REPEATING_COMMAND_BLOCK, "Repeating Command Block", ChatFormatting.GOLD, "Infinite loop power", "Runs every tick continuously"));
        presets.put("bedrock", () -> simpleLore(Items.BEDROCK, "Bedrock", ChatFormatting.DARK_GRAY, "Indestructible", "The ultimate block"));
        presets.put("barrier", () -> simpleLore(Items.BARRIER, "Barrier", ChatFormatting.RED, "Invisible wall", "Blocks movement but not sight"));
        presets.put("light-block", () -> simpleLore(Items.LIGHT, "Light Block", ChatFormatting.YELLOW, "Invisible light source", "Set any light level"));
        presets.put("structure-block", () -> simpleLore(Items.STRUCTURE_BLOCK, "Structure Block", ChatFormatting.AQUA, "Save and load structures", "Creative only block"));
        presets.put("jigsaw-block", () -> simpleLore(Items.JIGSAW, "Jigsaw Block", ChatFormatting.LIGHT_PURPLE, "Structure generation", "Connect structures together"));
        presets.put("end-portal-frame", () -> stackLore(Items.END_PORTAL_FRAME, 64, "End Portal Frame", ChatFormatting.DARK_PURPLE, "Gateway to the End", "64 frames of destiny"));
        presets.put("dragon-egg", () -> simpleLore(Items.DRAGON_EGG, "Dragon Egg", ChatFormatting.DARK_PURPLE, "The rarest block", "Drop of the final boss"));
        presets.put("command-block-minecart", () -> simpleLore(Items.COMMAND_BLOCK_MINECART, "Command Block Minecart", ChatFormatting.GOLD, "Mobile command block", "Runs commands while moving"));
        presets.put("structure-void", () -> simpleLore(Items.STRUCTURE_VOID, "Structure Void", ChatFormatting.DARK_RED, "Invisible structure", "Preserves blocks underneath"));
        presets.put("barrier-stack", () -> stackLore(Items.BARRIER, 64, "Barrier Stack", ChatFormatting.RED, "64 invisible walls", "Mass construction"));
        presets.put("piston-stack", () -> stackLore(Items.PISTON, 64, "Piston Stack", ChatFormatting.GRAY, "64 pistons", "Redstone automation"));
        presets.put("sticky-piston-stack", () -> stackLore(Items.STICKY_PISTON, 64, "Sticky Piston Stack", ChatFormatting.GREEN, "64 sticky pistons", "Push and pull blocks"));

        presets.put("spawn-wither", () -> simpleLore(Items.WITHER_SKELETON_SPAWN_EGG, "Wither Spawn Egg", ChatFormatting.DARK_GRAY, "Summon the Wither", "Brings destruction"));
        presets.put("spawn-ender-dragon", () -> simpleLore(Items.ENDER_DRAGON_SPAWN_EGG, "Ender Dragon Egg", ChatFormatting.DARK_PURPLE, "The final boss", "Spawns in the overworld"));
        presets.put("spawn-warden", () -> simpleLore(Items.WARDEN_SPAWN_EGG, "Warden Spawn Egg", ChatFormatting.DARK_AQUA, "Blind beast of the deep", "Detects through vibration"));
        presets.put("spawn-elder-guardian", () -> simpleLore(Items.ELDER_GUARDIAN_SPAWN_EGG, "Elder Guardian Egg", ChatFormatting.AQUA, "The ocean fortress boss", "Gives mining fatigue"));
        presets.put("spawn-ravager", () -> simpleLore(Items.RAVAGER_SPAWN_EGG, "Ravager Spawn Egg", ChatFormatting.DARK_RED, "Village destroyer", "Breaks blocks on charge"));
        presets.put("spawn-ghast", () -> simpleLore(Items.GHAST_SPAWN_EGG, "Ghast Spawn Egg", ChatFormatting.WHITE, "Floating fireball shooter", "Nether terror"));
        presets.put("spawn-blaze", () -> simpleLore(Items.BLAZE_SPAWN_EGG, "Blaze Spawn Egg", ChatFormatting.GOLD, "Fire elemental", "Shoots fireballs"));
        presets.put("spawn-piglin-brute", () -> simpleLore(Items.PIGLIN_BRUTE_SPAWN_EGG, "Piglin Brute Egg", ChatFormatting.YELLOW, "Always hostile piglin", "Guards the bastion"));
        presets.put("spawn-breeze", () -> simpleLore(Items.BREEZE_SPAWN_EGG, "Breeze Spawn Egg", ChatFormatting.AQUA, "Wind mob from trial chambers", "Shoots wind charges"));
        presets.put("spawn-creaking", () -> simpleLore(Items.CREAKING_SPAWN_EGG, "Creaking Spawn Egg", ChatFormatting.DARK_GREEN, "Pale garden guardian", "Appears at night"));
        presets.put("charged-creeper", () -> simpleLore(Items.CREEPER_SPAWN_EGG, "Charged Creeper Egg", ChatFormatting.GREEN, "Explosion x2 power", "Lightning strikes made it"));
        presets.put("spawn-elder", () -> simpleLore(Items.WITHER_SKELETON_SPAWN_EGG, "Wither Skeleton Egg", ChatFormatting.DARK_GRAY, "Fortress warrior", "Drops wither skulls"));
        presets.put("all-spawn-eggs", this::allSpawnEggs);

        presets.put("netherite-block-64", () -> stackLore(Items.NETHERITE_BLOCK, 64, "Netherite Block", ChatFormatting.DARK_GRAY, "The most valuable block", "64 blocks of ancient debris"));
        presets.put("diamond-block-64", () -> stackLore(Items.DIAMOND_BLOCK, 64, "Diamond Block", ChatFormatting.AQUA, "Pure diamond", "64 blocks of wealth"));
        presets.put("emerald-block-64", () -> stackLore(Items.EMERALD_BLOCK, 64, "Emerald Block", ChatFormatting.GREEN, "Villager currency", "64 blocks of trade"));
        presets.put("gold-block-64", () -> stackLore(Items.GOLD_BLOCK, 64, "Gold Block", ChatFormatting.YELLOW, "Precious metal", "64 blocks of gold"));
        presets.put("iron-block-64", () -> stackLore(Items.IRON_BLOCK, 64, "Iron Block", ChatFormatting.GRAY, "Industrial strength", "64 blocks of iron"));
        presets.put("obsidian-64", () -> stackLore(Items.OBSIDIAN, 64, "Obsidian", ChatFormatting.DARK_PURPLE, "Nether portal material", "64 blocks of darkness"));
        presets.put("end-stone-64", () -> stackLore(Items.END_STONE, 64, "End Stone", ChatFormatting.YELLOW, "End dimension floor", "64 blocks of the void"));
        presets.put("crying-obsidian-64", () -> stackLore(Items.CRYING_OBSIDIAN, 64, "Crying Obsidian", ChatFormatting.DARK_PURPLE, "Weeps with ancient power", "64 blocks of sorrow"));
        presets.put("ancient-debris-64", () -> stackLore(Items.ANCIENT_DEBRIS, 64, "Ancient Debris", ChatFormatting.DARK_RED, "Rarest ore in the Nether", "64 chunks of netherite"));
        presets.put("copper-block-64", () -> stackLore(Items.COPPER_BLOCK.weathering().unaffected(), 64, "Copper Block", ChatFormatting.RED, "Oxidizes over time", "64 blocks of copper"));
        presets.put("amethyst-block-64", () -> stackLore(Items.AMETHYST_BLOCK, 64, "Amethyst Block", ChatFormatting.LIGHT_PURPLE, "Crystal resonance", "64 blocks of amethyst"));
        presets.put("tnt-64", () -> stackLore(Items.TNT, 64, "TNT Stack", ChatFormatting.RED, "Maximum destruction", "64 blocks of boom"));
        presets.put("packed-ice-64", () -> stackLore(Items.PACKED_ICE, 64, "Packed Ice", ChatFormatting.AQUA, "Slippery surface", "64 blocks of ice"));
        presets.put("blue-ice-64", () -> stackLore(Items.BLUE_ICE, 64, "Blue Ice", ChatFormatting.AQUA, "Fastest ice", "64 blocks of speed"));
        presets.put("mossy-cobble-64", () -> stackLore(Items.MOSSY_COBBLESTONE, 64, "Mossy Cobblestone", ChatFormatting.GREEN, "Ancient ruins", "64 blocks of age"));
        presets.put("snow-block-64", () -> stackLore(Items.SNOW_BLOCK, 64, "Snow Block", ChatFormatting.WHITE, "Winter wonderland", "64 blocks of frost"));
        presets.put("deepslate-64", () -> stackLore(Items.DEEPSLATE, 64, "Deepslate", ChatFormatting.DARK_GRAY, "Deep underground", "64 blocks of depth"));
        presets.put("resin-block-64", () -> stackLore(Items.COPPER_BLOCK.weathering().unaffected(), 64, "Resin Block", ChatFormatting.GOLD, "Creaking resin", "64 blocks of the pale garden"));

        presets.put("potion-strength-ii", () -> potionLore(Potions.STRONG_STRENGTH, "Strength II Splash", ChatFormatting.RED, "Double your damage", "Melee power boost"));
        presets.put("potion-speed-ii", () -> potionLore(Potions.STRONG_SWIFTNESS, "Speed II Splash", ChatFormatting.AQUA, "Run faster than light", "Movement speed boost"));
        presets.put("potion-regen-ii", () -> potionLore(Potions.STRONG_REGENERATION, "Regeneration II Splash", ChatFormatting.LIGHT_PURPLE, "Rapid healing", "Regenerate health fast"));
        presets.put("potion-healing-ii", () -> potionLore(Potions.STRONG_HEALING, "Healing II Splash", ChatFormatting.RED, "Instant full health", "Splash healing potion"));
        presets.put("potion-fire-resist", () -> potionLore(Potions.FIRE_RESISTANCE, "Fire Resistance Splash", ChatFormatting.GOLD, "Walk through lava", "8 minutes of immunity"));
        presets.put("potion-invisibility", () -> potionLore(Potions.INVISIBILITY, "Invisibility Splash", ChatFormatting.GRAY, "Become invisible", "8 minutes of stealth"));
        presets.put("potion-night-vision", () -> potionLore(Potions.NIGHT_VISION, "Night Vision Splash", ChatFormatting.DARK_PURPLE, "See in the dark", "8 minutes of sight"));
        presets.put("potion-water-breathing", () -> potionLore(Potions.WATER_BREATHING, "Water Breathing Splash", ChatFormatting.AQUA, "Breathe underwater", "8 minutes of gills"));
        presets.put("potion-slow-falling", () -> potionLore(Potions.SLOW_FALLING, "Slow Falling Splash", ChatFormatting.WHITE, "Float gently down", "Safe descents"));
        presets.put("potion-poison-ii", () -> potionLore(Potions.STRONG_POISON, "Poison II Splash", ChatFormatting.DARK_GREEN, "Toxic cloud", "Damage over time"));
        presets.put("potion-harming-ii", () -> potionLore(Potions.STRONG_HARMING, "Harming II Splash", ChatFormatting.DARK_RED, "Instant damage", "Deals 12 hearts"));
        presets.put("potion-harming", () -> potionLore(Potions.HARMING, "Harming Splash", ChatFormatting.DARK_RED, "Instant damage", "Deals 6 hearts"));
        presets.put("potion-leaping", () -> potionLore(Potions.LEAPING, "Leaping Splash", ChatFormatting.GREEN, "Jump super high", "Leap over walls"));

        presets.put("ultimate-kit", this::ultimateKit);
        presets.put("pvp-kit", this::pvpKit);
        presets.put("builder-kit", this::builderKit);
        presets.put("end-kit", this::endKit);
        presets.put("nether-kit", this::netherKit);
        presets.put("fishing-kit", this::fishingKit);
        presets.put("redstone-kit", this::redstoneKit);

        presets.put("shulker-full", this::fullShulker);
        presets.put("music-discs", this::musicDiscs);

        presets.put("lodestone", () -> simpleLore(Items.LODESTONE, "Lodestone", ChatFormatting.GRAY, "Compass anchor", "Points to this block"));
        presets.put("echo-shard-stack", () -> stackLore(Items.ECHO_SHARD, 64, "Echo Shard", ChatFormatting.DARK_AQUA, "Sculk resonance", "64 shards of echo"));
        presets.put("recovery-compass", () -> simpleLore(Items.RECOVERY_COMPASS, "Recovery Compass", ChatFormatting.AQUA, "Find your death location", "Points to last death"));
        presets.put("bundle-stack", () -> stackLore(Items.BUNDLE, 64, "Bundle Stack", ChatFormatting.GOLD, "Carry more items", "64 empty bundles"));
        presets.put("saddle-stack", () -> stackLore(Items.SADDLE, 64, "Saddle Stack", ChatFormatting.GOLD, "Mount everything", "64 saddles"));
        presets.put("debug-stick", () -> simpleLore(Items.STICK, "Debug Stick", ChatFormatting.AQUA, "Edit block states", "Creative only"));
        presets.put("lodestone-compass", () -> simpleLore(Items.COMPASS, "Lodestone Compass", ChatFormatting.YELLOW, "Points to lodestone", "Navigate with precision"));
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(c -> { showList(); return SINGLE_SUCCESS; });
        builder.then(literal("list").executes(c -> { showList(); return SINGLE_SUCCESS; }));
        for (var e : presets.entrySet()) {
            String name = e.getKey();
            builder.then(literal(name).executes(c -> { giveItem(e.getValue().create()); info("Gave: " + name); return SINGLE_SUCCESS; }));
        }
        builder.then(literal("random").executes(c -> {
            String[] keys = presets.keySet().toArray(new String[0]);
            String k = keys[ThreadLocalRandom.current().nextInt(keys.length)];
            giveItem(presets.get(k).create());
            info("Random: " + k);
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("category").then(argument("name", StringArgumentType.word()).executes(c -> {
            String q = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
            List<String> m = new ArrayList<>();
            for (String k : presets.keySet()) if (k.contains(q)) m.add(k);
            if (m.isEmpty()) { error("No matches for: " + q); return SINGLE_SUCCESS; }
            for (String k : m) giveItem(presets.get(k).create());
            info("Gave " + m.size() + " items for: " + q);
            return SINGLE_SUCCESS;
        })));
        builder.then(literal("search").then(argument("query", StringArgumentType.greedyString()).executes(c -> {
            String q = StringArgumentType.getString(c, "query").toLowerCase(Locale.ROOT);
            List<String> m = new ArrayList<>();
            for (String k : presets.keySet()) if (k.contains(q)) m.add(k);
            if (m.isEmpty()) { error("No matches for: " + q); return SINGLE_SUCCESS; }
            for (String k : m) giveItem(presets.get(k).create());
            info("Gave " + m.size() + " matches.");
            return SINGLE_SUCCESS;
        })));
    }

    private void showList() {
        StringBuilder sb = new StringBuilder();
        sb.append("Orbiter GivePreset (").append(presets.size()).append(" items)\n");
        sb.append("Use: .ogp <name> | .ogp random | .ogp list | .ogp category <name> | .ogp search <query>\n\n");
        sb.append("Gear: god-sword, god-bow, god-helmet, god-chestplate, god-leggings, god-boots, god-axe, god-pickaxe, god-trident, god-mace, god-crossbow, god-fishing-rod\n");
        sb.append("Named: excalibur, poseidon-trident, mjolnir, anubis, thunder-blade, frostbite, infinity-blade, smite-sword, arthropod-axe, fortune-pick, silk-pick, riptide-trident, channeling-trident, feather-boots, respiration-helmet, thorns-shield, speed-boots, frost-boots, looting-sword\n");
        sb.append("Utility: totem-stack, elytra, firework-stack, golden-apple-stack, ender-pearl-stack, chorus-fruit-stack, crystal-stack, saddle-stack, name-tag-stack, trident-stack, totem-of-dying\n");
        sb.append("OP Blocks: command-block, chain-command-block, repeating-command-block, bedrock, barrier, light-block, structure-block, jigsaw-block, end-portal-frame, dragon-egg, command-block-minecart, structure-void, barrier-stack, piston-stack, sticky-piston-stack\n");
        sb.append("Spawns: spawn-wither, spawn-ender-dragon, spawn-warden, spawn-elder-guardian, spawn-ravager, spawn-ghast, spawn-blaze, spawn-piglin-brute, spawn-breeze, spawn-creaking, charged-creeper, all-spawn-eggs\n");
        sb.append("Blocks: netherite-block-64, diamond-block-64, emerald-block-64, gold-block-64, iron-block-64, obsidian-64, end-stone-64, crying-obsidian-64, ancient-debris-64, copper-block-64, amethyst-block-64, tnt-64, packed-ice-64, blue-ice-64, mossy-cobble-64, snow-block-64, deepslate-64, resin-block-64\n");
        sb.append("Potions: potion-strength-ii, potion-speed-ii, potion-regen-ii, potion-healing-ii, potion-fire-resist, potion-invisibility, potion-night-vision, potion-water-breathing, potion-absorption-ii, potion-slow-falling, potion-poison-ii, potion-wither-ii, potion-harming-ii, potion-leaping-ii\n");
        sb.append("Kits: ultimate-kit, pvp-kit, builder-kit, end-kit, nether-kit, fishing-kit, redstone-kit\n");
        sb.append("Special: shulker-full, music-discs, lodestone, echo-shard-stack, recovery-compass, bundle-stack, saddle-stack, debug-stick, lodestone-compass");
        info(sb.toString());
    }

    private void giveItem(ItemStack item) {
        int slot = mc.player.getInventory().getSelectedSlot();
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + slot, item));
        mc.player.containerMenu.getSlot(36 + slot).set(item);
    }

    private Component name(String value, ChatFormatting color) {
        return Component.literal(value).setStyle(Style.EMPTY.withColor(color).withBold(true));
    }

    private Component lore(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY));
    }

    private Component orbiterLore() {
        return Component.literal("\u00a76\u2b50 Orbiter Preset").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
    }

    private ItemStack simpleLore(Item item, String n, ChatFormatting c, String l1, String l2) {
        ItemStack s = new ItemStack(item, 1);
        s.set(DataComponents.CUSTOM_NAME, name(n, c));
        s.set(DataComponents.LORE, new ItemLore(List.of(lore(l1), lore(l2), orbiterLore())));
        return s;
    }

    private ItemStack stackLore(Item item, int count, String n, ChatFormatting c, String l1, String l2) {
        ItemStack s = new ItemStack(item, count);
        s.set(DataComponents.CUSTOM_NAME, name(n, c));
        s.set(DataComponents.LORE, new ItemLore(List.of(lore(l1), lore(l2), orbiterLore())));
        return s;
    }

    private ItemStack potionLore(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> ref, String n, ChatFormatting c, String l1, String l2) {
        ItemStack s = new ItemStack(Items.SPLASH_POTION);
        s.set(DataComponents.CUSTOM_NAME, name(n, c));
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(ref));
        s.set(DataComponents.LORE, new ItemLore(List.of(lore(l1), lore(l2), orbiterLore())));
        return s;
    }

    private void addEnchant(ItemEnchantments.Mutable b, String id, int lv) {
        if (mc.level == null) return;
        String clean = id.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (!clean.contains(":")) clean = "minecraft:" + clean;
        String[] p = clean.split(":", 2);
        if (p.length != 2) return;
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(p[0], p[1]));
        mc.level.registryAccess().getOrThrow(Registries.ENCHANTMENT).value().get(key.identifier()).ifPresent(r -> b.set(r, lv));
    }

    private ItemEnchantments.Mutable baseEnchants(String... enchants) {
        ItemEnchantments.Mutable b = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (String e : enchants) {
            String[] parts = e.split(":");
            addEnchant(b, parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 255);
        }
        return b;
    }

    private void setLore(ItemStack s, String... lines) {
        List<Component> loreList = new ArrayList<>();
        for (String l : lines) loreList.add(lore(l));
        loreList.add(orbiterLore());
        s.set(DataComponents.LORE, new ItemLore(loreList));
    }

    private ItemStack godSword() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("God Sword", ChatFormatting.RED));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("sharpness:255","smite:255","bane_of_arthropods:255","knockback:255","fire_aspect:255","looting:255","sweeping_edge:255","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","gs_d"), 2048, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","gs_s"), 1024, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "The ultimate melee weapon", "One strike, one kill");
        return s;
    }

    private ItemStack godBow() {
        ItemStack s = new ItemStack(Items.BOW);
        s.set(DataComponents.CUSTOM_NAME, name("God Bow", ChatFormatting.GREEN));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("power:255","punch:255","flame:1","infinity:1","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Never miss, never run out", "Arrows of destruction");
        return s;
    }

    private ItemStack godArmor(Item item, String n, int idx) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, name(n, ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("protection:255","blast_protection:255","fire_protection:255","projectile_protection:255","thorns:255","unbreaking:255","mending:1","respiration:255","aqua_affinity:1","depth_strider:255","soul_speed:255","swift_sneak:255").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ARMOR, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","ga_a"+idx), 1000, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
        a.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","ga_t"+idx), 1000, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
        a.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","ga_k"+idx), 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Immovable defense", "Walk through fire and explosions");
        return s;
    }

    private ItemStack godAxe() {
        ItemStack s = new ItemStack(Items.NETHERITE_AXE);
        s.set(DataComponents.CUSTOM_NAME, name("God Axe", ChatFormatting.GOLD));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("sharpness:255","efficiency:255","fortune:255","silk_touch:1","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","ga_d"), 2048, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","ga_s"), 1024, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Chop anything in one hit", "The woodcutter's dream");
        return s;
    }

    private ItemStack godPickaxe() {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        s.set(DataComponents.CUSTOM_NAME, name("God Pickaxe", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("efficiency:255","fortune:255","silk_touch:1","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","gp_d"), 1024, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","gp_s"), 1024, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Mine anything instantly", "Fortune and silk touch in one");
        return s;
    }

    private ItemStack godTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponents.CUSTOM_NAME, name("God Trident", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("loyalty:255","impaling:255","channeling:1","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Command the seas and storms", "Returns after every throw");
        return s;
    }

    private ItemStack godMace() {
        ItemStack s = new ItemStack(Items.MACE);
        s.set(DataComponents.CUSTOM_NAME, name("God Mace", ChatFormatting.GOLD));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("density:255","wind_burst:255","smashing:1","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","gm_d"), 4096, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Smash from the heavens", "Wind burst sends enemies flying");
        return s;
    }

    private ItemStack godCrossbow() {
        ItemStack s = new ItemStack(Items.CROSSBOW);
        s.set(DataComponents.CUSTOM_NAME, name("God Crossbow", ChatFormatting.RED));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("quick_charge:255","piercing:255","multishot:1","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Instant fire, pierces all", "3 arrows at once");
        return s;
    }

    private ItemStack godFishingRod() {
        ItemStack s = new ItemStack(Items.FISHING_ROD);
        s.set(DataComponents.CUSTOM_NAME, name("God Fishing Rod", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("luck_of_the_sea:255","lure:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Catch anything in the water", "Max luck, max speed");
        return s;
    }

    private ItemStack excalibur() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Excalibur", ChatFormatting.YELLOW));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("sharpness:255","smite:255","knockback:255","fire_aspect:255","sweeping_edge:255","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","exc_d"), 4096, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","exc_s"), 2048, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.LUCK, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","exc_l"), 100, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "The legendary sword of King Arthur", "Forged in dragon fire", "Only the worthy may wield it");
        return s;
    }

    private ItemStack poseidon() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponents.CUSTOM_NAME, name("Poseidon\u2019s Trident", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("loyalty:255","impaling:255","channeling:1","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","pos_d"), 4096, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","pos_s"), 1000, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Weapon of the sea god", "Commands lightning and tide", "Swim faster than dolphins");
        return s;
    }

    private ItemStack mjolnir() {
        ItemStack s = new ItemStack(Items.MACE);
        s.set(DataComponents.CUSTOM_NAME, name("Mjolnir", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("smite:255","density:255","wind_burst:255","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","mjol_d"), 8192, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","mjol_s"), 4096, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Thor\u2019s legendary hammer", "Strikes with the power of thunder", "Only the worthy may lift it");
        return s;
    }

    private ItemStack anubis() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Anubis", ChatFormatting.DARK_GRAY));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("smite:255","fire_aspect:255","knockback:255","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","anu_d"), 4096, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        a.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","anu_s"), 2048, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Blade of the Egyptian death god", "Judges the souls of the fallen", "Smite the undead into oblivion");
        return s;
    }

    private ItemStack thunderBlade() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Thunder Blade", ChatFormatting.YELLOW));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("sharpness:255","fire_aspect:255","knockback:255","sweeping_edge:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Forged in a thunderstorm", "Each strike brings lightning", "Electrify your enemies");
        return s;
    }

    private ItemStack frostbite() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Frostbite", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("sharpness:255","knockback:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Frozen in eternal ice", "Slows enemies on hit", "Winter\u2019s vengeance");
        return s;
    }

    private ItemStack infinityBlade() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Infinity Blade", ChatFormatting.LIGHT_PURPLE));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("sharpness:255","smite:255","bane_of_arthropods:255","knockback:255","fire_aspect:255","looting:255","sweeping_edge:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Contains every damage enchantment", "255 levels of pure destruction", "No entity survives this blade");
        return s;
    }

    private ItemStack smiteSword() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Smite Sword", ChatFormatting.DARK_RED));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("smite:255","fire_aspect:255","looting:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "The undead\u2019s worst nightmare", "Extra damage to all undead mobs");
        return s;
    }

    private ItemStack arthropodAxe() {
        ItemStack s = new ItemStack(Items.NETHERITE_AXE);
        s.set(DataComponents.CUSTOM_NAME, name("Arthropod Slayer", ChatFormatting.DARK_GREEN));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("bane_of_arthropods:255","sharpness:255","looting:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Spider and bug exterminator", "One-shots all arthropods");
        return s;
    }

    private ItemStack fortunePick() {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        s.set(DataComponents.CUSTOM_NAME, name("Fortune King", ChatFormatting.GOLD));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("fortune:255","efficiency:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Every ore drops maximum items", "The mining jackpot pickaxe");
        return s;
    }

    private ItemStack silkPick() {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        s.set(DataComponents.CUSTOM_NAME, name("Silk Touch Master", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("silk_touch:1","efficiency:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Collect blocks as they are", "No ore conversion, pure blocks");
        return s;
    }

    private ItemStack riptideTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponents.CUSTOM_NAME, name("Riptide Rider", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("riptide:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Launch yourself through the sky", "Ride rain and thunder");
        return s;
    }

    private ItemStack channelingTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponents.CUSTOM_NAME, name("Storm Bringer", ChatFormatting.YELLOW));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("channeling:1","loyalty:255","impaling:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Summon lightning on every throw", "The sky bows to your will");
        return s;
    }

    private ItemStack featherBoots() {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        s.set(DataComponents.CUSTOM_NAME, name("Gravity Defier", ChatFormatting.WHITE));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("feather_falling:255","protection:255","depth_strider:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Fall from any height safely", "Land like a feather");
        return s;
    }

    private ItemStack respirationHelmet() {
        ItemStack s = new ItemStack(Items.NETHERITE_HELMET);
        s.set(DataComponents.CUSTOM_NAME, name("Deep Sea Diver", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("respiration:255","aqua_affinity:1","protection:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Breathe underwater forever", "See clearly in the deep ocean");
        return s;
    }

    private ItemStack thornsShield() {
        ItemStack s = new ItemStack(Items.SHIELD);
        s.set(DataComponents.CUSTOM_NAME, name("Pain Reflector", ChatFormatting.RED));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("unbreaking:255","mending:1").toImmutable());
        setLore(s, "Every hit hurts the attacker", "Reflect damage back");
        return s;
    }

    private ItemStack speedBoots() {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        s.set(DataComponents.CUSTOM_NAME, name("Speed Boots", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("protection:255","depth_strider:255","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","sb"), 1000, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Run at lightning speed", "Outrun anything");
        return s;
    }

    private ItemStack frostBoots() {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        s.set(DataComponents.CUSTOM_NAME, name("Frost Boots", ChatFormatting.WHITE));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("frost_walker:255","protection:255","unbreaking:255","mending:1").toImmutable());
        setLore(s, "Walk on water and lava", "Freeze everything underfoot");
        return s;
    }

    private ItemStack lootingSword() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponents.CUSTOM_NAME, name("Looting Master", ChatFormatting.GOLD));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("looting:255","sharpness:255","fire_aspect:255","unbreaking:255","mending:1").toImmutable());
        ItemAttributeModifiers.Builder a = ItemAttributeModifiers.builder();
        a.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter","ls_d"), 1024, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Maximum loot from every kill", "Looting 255 = infinite drops");
        return s;
    }

    private ItemStack elytra() {
        ItemStack s = new ItemStack(Items.ELYTRA);
        s.set(DataComponents.CUSTOM_NAME, name("God Elytra", ChatFormatting.AQUA));
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.ENCHANTMENTS, baseEnchants("unbreaking:255","mending:1").toImmutable());
        setLore(s, "Fly across the world", "Never breaks, never stops");
        return s;
    }

    private ItemStack totemOfDying() {
        ItemStack s = new ItemStack(Items.TOTEM_OF_UNDYING);
        s.set(DataComponents.CUSTOM_NAME, name("Totem of Dying", ChatFormatting.GOLD));
        setLore(s, "Extra life in your offhand", "Respawn on death");
        return s;
    }

    private ItemStack ultimateKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("Ultimate Kit", ChatFormatting.LIGHT_PURPLE));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(godSword()); contents.add(godBow()); contents.add(godAxe());
        contents.add(godPickaxe()); contents.add(godMace());
        contents.add(godArmor(Items.NETHERITE_HELMET, "God Helmet", 0));
        contents.add(godArmor(Items.NETHERITE_CHESTPLATE, "God Chest", 1));
        contents.add(godArmor(Items.NETHERITE_LEGGINGS, "God Legs", 2));
        contents.add(godArmor(Items.NETHERITE_BOOTS, "God Boots", 3));
        contents.add(elytra());
        contents.add(new ItemStack(Items.FIREWORK_ROCKET, 64));
        contents.add(new ItemStack(Items.TOTEM_OF_UNDYING, 64));
        contents.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64));
        contents.add(new ItemStack(Items.ENDER_PEARL, 64));
        contents.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "Everything you need to dominate", "27 god-tier items in one shulker");
        return s;
    }

    private ItemStack pvpKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("PvP Kit", ChatFormatting.RED));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(godSword()); contents.add(godBow()); contents.add(godCrossbow());
        contents.add(godArmor(Items.NETHERITE_HELMET, "PvP Helmet", 0));
        contents.add(godArmor(Items.NETHERITE_CHESTPLATE, "PvP Chest", 1));
        contents.add(godArmor(Items.NETHERITE_LEGGINGS, "PvP Legs", 2));
        contents.add(speedBoots());
        for (int i = 0; i < 6; i++) contents.add(new ItemStack(Items.TOTEM_OF_UNDYING));
        contents.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 16));
        contents.add(new ItemStack(Items.ENDER_PEARL, 16));
        contents.add(new ItemStack(Items.FIREWORK_ROCKET, 32));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "Ready for any PvP encounter", "God gear + consumables");
        return s;
    }

    private ItemStack builderKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("Builder Kit", ChatFormatting.GREEN));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(silkPick());
        contents.add(fortunePick());
        contents.add(new ItemStack(Items.NETHERITE_SHOVEL));
        contents.add(new ItemStack(Items.NETHERITE_AXE));
        contents.add(new ItemStack(Items.OBSIDIAN, 64));
        contents.add(new ItemStack(Items.DIAMOND_BLOCK, 64));
        contents.add(new ItemStack(Items.OAK_PLANKS, 64));
        contents.add(new ItemStack(Items.STONE, 64));
        contents.add(new ItemStack(Items.GLASS, 64));
        contents.add(new ItemStack(Items.TORCH, 64));
        contents.add(new ItemStack(Items.CRAFTING_TABLE));
        contents.add(new ItemStack(Items.CHEST, 64));
        contents.add(new ItemStack(Items.FURNACE, 64));
        contents.add(new ItemStack(Items.BONE_MEAL, 64));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "Everything a builder needs", "Blocks, tools, and utilities");
        return s;
    }

    private ItemStack endKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("End Kit", ChatFormatting.DARK_PURPLE));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(new ItemStack(Items.END_CRYSTAL, 64));
        contents.add(new ItemStack(Items.ENDER_PEARL, 64));
        contents.add(new ItemStack(Items.ENDER_EYE, 64));
        contents.add(new ItemStack(Items.CHORUS_FRUIT, 64));
        contents.add(elytra());
        contents.add(new ItemStack(Items.FIREWORK_ROCKET, 64));
        contents.add(new ItemStack(Items.ENDER_CHEST, 64));
        contents.add(godSword());
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "Conquer the End dimension", "Crystals, pearls, and firework elytra");
        return s;
    }

    private ItemStack netherKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("Nether Kit", ChatFormatting.DARK_RED));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(new ItemStack(Items.FIREWORK_ROCKET, 64));
        contents.add(new ItemStack(Items.ENDER_PEARL, 16));
        contents.add(new ItemStack(Items.GOLDEN_APPLE, 16));
        contents.add(godSword());
        contents.add(godArmor(Items.NETHERITE_HELMET, "Nether Helm", 0));
        contents.add(godArmor(Items.NETHERITE_CHESTPLATE, "Nether Chest", 1));
        contents.add(godArmor(Items.NETHERITE_LEGGINGS, "Nether Legs", 2));
        contents.add(speedBoots());
        contents.add(new ItemStack(Items.OBSIDIAN, 16));
        contents.add(new ItemStack(Items.FLINT_AND_STEEL));
        contents.add(new ItemStack(Items.BLAZE_ROD, 64));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "Survive the Nether like a pro", "Fire protection and supplies");
        return s;
    }

    private ItemStack fishingKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("Fishing Kit", ChatFormatting.AQUA));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(godFishingRod());
        contents.add(new ItemStack(Items.LILY_PAD, 64));
        contents.add(new ItemStack(Items.WATER_BUCKET, 64));
        contents.add(new ItemStack(Items.CHEST, 64));
        contents.add(new ItemStack(Items.NETHERITE_AXE));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "Master fisherman setup", "Catch everything in the water");
        return s;
    }

    private ItemStack redstoneKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("Redstone Kit", ChatFormatting.RED));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(new ItemStack(Items.REPEATER, 64));
        contents.add(new ItemStack(Items.COMPARATOR, 64));
        contents.add(new ItemStack(Items.REDSTONE, 64));
        contents.add(new ItemStack(Items.REDSTONE_TORCH, 64));
        contents.add(new ItemStack(Items.PISTON, 64));
        contents.add(new ItemStack(Items.STICKY_PISTON, 64));
        contents.add(new ItemStack(Items.REDSTONE_LAMP, 64));
        contents.add(new ItemStack(Items.OBSERVER, 64));
        contents.add(new ItemStack(Items.HOPPER, 64));
        contents.add(new ItemStack(Items.DROPPER, 64));
        contents.add(new ItemStack(Items.DISPENSER, 64));
        contents.add(new ItemStack(Items.LEVER, 64));
        contents.add(new ItemStack(Items.STONE_BUTTON, 64));
        contents.add(new ItemStack(Items.DAYLIGHT_DETECTOR, 64));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "All the redstone you need", "Build anything automated");
        return s;
    }

    private ItemStack fullShulker() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponents.CUSTOM_NAME, name("Random Enchanted Shulker", ChatFormatting.LIGHT_PURPLE));
        Item[] items = {Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.SHIELD, Items.ELYTRA, Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.SHEARS, Items.END_CRYSTAL, Items.TOTEM_OF_UNDYING, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.ENDER_EYE, Items.BLAZE_ROD, Items.NETHER_STAR, Items.DRAGON_BREATH, Items.TOTEM_OF_UNDYING};
        List<ItemStack> contents = new ArrayList<>();
        for (Item item : items) {
            ItemStack is = new ItemStack(item);
            is.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            if (mc.level != null) {
                ItemEnchantments.Mutable b = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                var enchRegistry = mc.level.registryAccess().getOrThrow(Registries.ENCHANTMENT).value();
                enchRegistry.keySet().stream().limit(3).forEach(id -> enchRegistry.get(id).ifPresent(r -> b.set(r, 255)));
                is.set(DataComponents.ENCHANTMENTS, b.toImmutable());
    }

            contents.add(is);
        }
        s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        setLore(s, "27 randomly enchanted items", "Each with 3 random max enchantments");
        return s;
    }

    private ItemStack musicDiscs() {
        ItemStack s = new ItemStack(Items.MUSIC_DISC_CREATOR);
        s.set(DataComponents.CUSTOM_NAME, name("Music Disc: Creator", ChatFormatting.LIGHT_PURPLE));
        setLore(s, "The latest music disc", "Beautiful soundtrack");
        return s;
    }

    private ItemStack allSpawnEggs() {
        ItemStack s = new ItemStack(Items.ENDER_DRAGON_SPAWN_EGG);
        s.set(DataComponents.CUSTOM_NAME, name("Spawn Egg Collection", ChatFormatting.RED));
        setLore(s, "Check inventory for all spawn eggs", "One of each mob type");
        Item[] eggs = {Items.ZOMBIE_SPAWN_EGG, Items.SKELETON_SPAWN_EGG, Items.CREEPER_SPAWN_EGG, Items.SPIDER_SPAWN_EGG, Items.CAVE_SPIDER_SPAWN_EGG, Items.ENDERMAN_SPAWN_EGG, Items.BLAZE_SPAWN_EGG, Items.GHAST_SPAWN_EGG, Items.WITCH_SPAWN_EGG, Items.WITHER_SKELETON_SPAWN_EGG, Items.GUARDIAN_SPAWN_EGG, Items.ELDER_GUARDIAN_SPAWN_EGG, Items.ENDERMITE_SPAWN_EGG, Items.SILVERFISH_SPAWN_EGG, Items.MAGMA_CUBE_SPAWN_EGG, Items.SLIME_SPAWN_EGG, Items.HUSK_SPAWN_EGG, Items.STRAY_SPAWN_EGG, Items.VINDICATOR_SPAWN_EGG, Items.EVOKER_SPAWN_EGG, Items.VEX_SPAWN_EGG, Items.RAVAGER_SPAWN_EGG, Items.PHANTOM_SPAWN_EGG, Items.DROWNED_SPAWN_EGG, Items.SHULKER_SPAWN_EGG, Items.PILLAGER_SPAWN_EGG, Items.WARDEN_SPAWN_EGG, Items.PIGLIN_BRUTE_SPAWN_EGG, Items.BREEZE_SPAWN_EGG};
        for (Item egg : eggs) giveItem(new ItemStack(egg));
        return s;
    }

    @FunctionalInterface
    private interface PresetFactory {
        ItemStack create();
    }
}
