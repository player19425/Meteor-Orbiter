package orbiter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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

        presets.put("totem-stack", () -> stackLore(Items.TOTEM_OF_UNDYING, 64, "Totem Stack", Formatting.GOLD, "Never die again", "64 lives in your pocket"));
        presets.put("elytra", this::elytra);
        presets.put("firework-stack", () -> stackLore(Items.FIREWORK_ROCKET, 64, "Firework Stack", Formatting.RED, "Launch into the sky", "64 rockets for elytra flights"));
        presets.put("golden-apple-stack", () -> stackLore(Items.ENCHANTED_GOLDEN_APPLE, 64, "Notch Apples", Formatting.GOLD, "The most powerful food", "64 golden legends"));
        presets.put("ender-pearl-stack", () -> stackLore(Items.ENDER_PEARL, 64, "Ender Pearl Stack", Formatting.DARK_PURPLE, "Teleport anywhere", "64 pearls of void travel"));
        presets.put("chorus-fruit-stack", () -> stackLore(Items.CHORUS_FRUIT, 64, "Chorus Fruit Stack", Formatting.LIGHT_PURPLE, "Random teleportation food", "64 fruits of the end"));
        presets.put("crystal-stack", () -> stackLore(Items.END_CRYSTAL, 64, "End Crystal Stack", Formatting.AQUA, "Explosive decorations", "64 crystals of destruction"));
        presets.put("saddle-stack", () -> stackLore(Items.SADDLE, 64, "Saddle Stack", Formatting.GOLD, "Ride anything", "64 saddles for every mount"));
        presets.put("name-tag-stack", () -> stackLore(Items.NAME_TAG, 64, "Name Tag Stack", Formatting.AQUA, "Name everything", "64 blank name tags"));
        presets.put("trident-stack", () -> stackLore(Items.TRIDENT, 64, "Trident Stack", Formatting.AQUA, "64 tridents of the deep", "Raining weapons"));
        presets.put("totem-of-dying", this::totemOfDying);

        presets.put("command-block", () -> simpleLore(Items.COMMAND_BLOCK, "Command Block", Formatting.GOLD, "The power block", "Run any command on placement"));
        presets.put("chain-command-block", () -> simpleLore(Items.CHAIN_COMMAND_BLOCK, "Chain Command Block", Formatting.GOLD, "Chain reactions", "Runs after the previous block"));
        presets.put("repeating-command-block", () -> simpleLore(Items.REPEATING_COMMAND_BLOCK, "Repeating Command Block", Formatting.GOLD, "Infinite loop power", "Runs every tick continuously"));
        presets.put("bedrock", () -> simpleLore(Items.BEDROCK, "Bedrock", Formatting.DARK_GRAY, "Indestructible", "The ultimate block"));
        presets.put("barrier", () -> simpleLore(Items.BARRIER, "Barrier", Formatting.RED, "Invisible wall", "Blocks movement but not sight"));
        presets.put("light-block", () -> simpleLore(Items.LIGHT, "Light Block", Formatting.YELLOW, "Invisible light source", "Set any light level"));
        presets.put("structure-block", () -> simpleLore(Items.STRUCTURE_BLOCK, "Structure Block", Formatting.AQUA, "Save and load structures", "Creative only block"));
        presets.put("jigsaw-block", () -> simpleLore(Items.JIGSAW, "Jigsaw Block", Formatting.LIGHT_PURPLE, "Structure generation", "Connect structures together"));
        presets.put("end-portal-frame", () -> stackLore(Items.END_PORTAL_FRAME, 64, "End Portal Frame", Formatting.DARK_PURPLE, "Gateway to the End", "64 frames of destiny"));
        presets.put("dragon-egg", () -> simpleLore(Items.DRAGON_EGG, "Dragon Egg", Formatting.DARK_PURPLE, "The rarest block", "Drop of the final boss"));
        presets.put("command-block-minecart", () -> simpleLore(Items.COMMAND_BLOCK_MINECART, "Command Block Minecart", Formatting.GOLD, "Mobile command block", "Runs commands while moving"));
        presets.put("structure-void", () -> simpleLore(Items.STRUCTURE_VOID, "Structure Void", Formatting.DARK_RED, "Invisible structure", "Preserves blocks underneath"));
        presets.put("barrier-stack", () -> stackLore(Items.BARRIER, 64, "Barrier Stack", Formatting.RED, "64 invisible walls", "Mass construction"));
        presets.put("piston-stack", () -> stackLore(Items.PISTON, 64, "Piston Stack", Formatting.GRAY, "64 pistons", "Redstone automation"));
        presets.put("sticky-piston-stack", () -> stackLore(Items.STICKY_PISTON, 64, "Sticky Piston Stack", Formatting.GREEN, "64 sticky pistons", "Push and pull blocks"));

        presets.put("spawn-wither", () -> simpleLore(Items.WITHER_SKELETON_SPAWN_EGG, "Wither Spawn Egg", Formatting.DARK_GRAY, "Summon the Wither", "Brings destruction"));
        presets.put("spawn-ender-dragon", () -> simpleLore(Items.ENDER_DRAGON_SPAWN_EGG, "Ender Dragon Egg", Formatting.DARK_PURPLE, "The final boss", "Spawns in the overworld"));
        presets.put("spawn-warden", () -> simpleLore(Items.WARDEN_SPAWN_EGG, "Warden Spawn Egg", Formatting.DARK_AQUA, "Blind beast of the deep", "Detects through vibration"));
        presets.put("spawn-elder-guardian", () -> simpleLore(Items.ELDER_GUARDIAN_SPAWN_EGG, "Elder Guardian Egg", Formatting.AQUA, "The ocean fortress boss", "Gives mining fatigue"));
        presets.put("spawn-ravager", () -> simpleLore(Items.RAVAGER_SPAWN_EGG, "Ravager Spawn Egg", Formatting.DARK_RED, "Village destroyer", "Breaks blocks on charge"));
        presets.put("spawn-ghast", () -> simpleLore(Items.GHAST_SPAWN_EGG, "Ghast Spawn Egg", Formatting.WHITE, "Floating fireball shooter", "Nether terror"));
        presets.put("spawn-blaze", () -> simpleLore(Items.BLAZE_SPAWN_EGG, "Blaze Spawn Egg", Formatting.GOLD, "Fire elemental", "Shoots fireballs"));
        presets.put("spawn-piglin-brute", () -> simpleLore(Items.PIGLIN_BRUTE_SPAWN_EGG, "Piglin Brute Egg", Formatting.YELLOW, "Always hostile piglin", "Guards the bastion"));
        presets.put("spawn-breeze", () -> simpleLore(Items.BREEZE_SPAWN_EGG, "Breeze Spawn Egg", Formatting.AQUA, "Wind mob from trial chambers", "Shoots wind charges"));
        presets.put("spawn-creaking", () -> simpleLore(Items.CREAKING_SPAWN_EGG, "Creaking Spawn Egg", Formatting.DARK_GREEN, "Pale garden guardian", "Appears at night"));
        presets.put("charged-creeper", () -> simpleLore(Items.CREEPER_SPAWN_EGG, "Charged Creeper Egg", Formatting.GREEN, "Explosion x2 power", "Lightning strikes made it"));
        presets.put("spawn-elder", () -> simpleLore(Items.WITHER_SKELETON_SPAWN_EGG, "Wither Skeleton Egg", Formatting.DARK_GRAY, "Fortress warrior", "Drops wither skulls"));
        presets.put("all-spawn-eggs", this::allSpawnEggs);

        presets.put("netherite-block-64", () -> stackLore(Items.NETHERITE_BLOCK, 64, "Netherite Block", Formatting.DARK_GRAY, "The most valuable block", "64 blocks of ancient debris"));
        presets.put("diamond-block-64", () -> stackLore(Items.DIAMOND_BLOCK, 64, "Diamond Block", Formatting.AQUA, "Pure diamond", "64 blocks of wealth"));
        presets.put("emerald-block-64", () -> stackLore(Items.EMERALD_BLOCK, 64, "Emerald Block", Formatting.GREEN, "Villager currency", "64 blocks of trade"));
        presets.put("gold-block-64", () -> stackLore(Items.GOLD_BLOCK, 64, "Gold Block", Formatting.YELLOW, "Precious metal", "64 blocks of gold"));
        presets.put("iron-block-64", () -> stackLore(Items.IRON_BLOCK, 64, "Iron Block", Formatting.GRAY, "Industrial strength", "64 blocks of iron"));
        presets.put("obsidian-64", () -> stackLore(Items.OBSIDIAN, 64, "Obsidian", Formatting.DARK_PURPLE, "Nether portal material", "64 blocks of darkness"));
        presets.put("end-stone-64", () -> stackLore(Items.END_STONE, 64, "End Stone", Formatting.YELLOW, "End dimension floor", "64 blocks of the void"));
        presets.put("crying-obsidian-64", () -> stackLore(Items.CRYING_OBSIDIAN, 64, "Crying Obsidian", Formatting.DARK_PURPLE, "Weeps with ancient power", "64 blocks of sorrow"));
        presets.put("ancient-debris-64", () -> stackLore(Items.ANCIENT_DEBRIS, 64, "Ancient Debris", Formatting.DARK_RED, "Rarest ore in the Nether", "64 chunks of netherite"));
        presets.put("copper-block-64", () -> stackLore(Items.COPPER_BLOCK, 64, "Copper Block", Formatting.RED, "Oxidizes over time", "64 blocks of copper"));
        presets.put("amethyst-block-64", () -> stackLore(Items.AMETHYST_BLOCK, 64, "Amethyst Block", Formatting.LIGHT_PURPLE, "Crystal resonance", "64 blocks of amethyst"));
        presets.put("tnt-64", () -> stackLore(Items.TNT, 64, "TNT Stack", Formatting.RED, "Maximum destruction", "64 blocks of boom"));
        presets.put("packed-ice-64", () -> stackLore(Items.PACKED_ICE, 64, "Packed Ice", Formatting.AQUA, "Slippery surface", "64 blocks of ice"));
        presets.put("blue-ice-64", () -> stackLore(Items.BLUE_ICE, 64, "Blue Ice", Formatting.AQUA, "Fastest ice", "64 blocks of speed"));
        presets.put("mossy-cobble-64", () -> stackLore(Items.MOSSY_COBBLESTONE, 64, "Mossy Cobblestone", Formatting.GREEN, "Ancient ruins", "64 blocks of age"));
        presets.put("snow-block-64", () -> stackLore(Items.SNOW_BLOCK, 64, "Snow Block", Formatting.WHITE, "Winter wonderland", "64 blocks of frost"));
        presets.put("deepslate-64", () -> stackLore(Items.DEEPSLATE, 64, "Deepslate", Formatting.DARK_GRAY, "Deep underground", "64 blocks of depth"));
        presets.put("resin-block-64", () -> stackLore(Items.COPPER_BLOCK, 64, "Resin Block", Formatting.GOLD, "Creaking resin", "64 blocks of the pale garden"));

        presets.put("potion-strength-ii", () -> potionLore(Potions.STRONG_STRENGTH, "Strength II Splash", Formatting.RED, "Double your damage", "Melee power boost"));
        presets.put("potion-speed-ii", () -> potionLore(Potions.STRONG_SWIFTNESS, "Speed II Splash", Formatting.AQUA, "Run faster than light", "Movement speed boost"));
        presets.put("potion-regen-ii", () -> potionLore(Potions.STRONG_REGENERATION, "Regeneration II Splash", Formatting.LIGHT_PURPLE, "Rapid healing", "Regenerate health fast"));
        presets.put("potion-healing-ii", () -> potionLore(Potions.STRONG_HEALING, "Healing II Splash", Formatting.RED, "Instant full health", "Splash healing potion"));
        presets.put("potion-fire-resist", () -> potionLore(Potions.FIRE_RESISTANCE, "Fire Resistance Splash", Formatting.GOLD, "Walk through lava", "8 minutes of immunity"));
        presets.put("potion-invisibility", () -> potionLore(Potions.INVISIBILITY, "Invisibility Splash", Formatting.GRAY, "Become invisible", "8 minutes of stealth"));
        presets.put("potion-night-vision", () -> potionLore(Potions.NIGHT_VISION, "Night Vision Splash", Formatting.DARK_PURPLE, "See in the dark", "8 minutes of sight"));
        presets.put("potion-water-breathing", () -> potionLore(Potions.WATER_BREATHING, "Water Breathing Splash", Formatting.AQUA, "Breathe underwater", "8 minutes of gills"));
        presets.put("potion-slow-falling", () -> potionLore(Potions.SLOW_FALLING, "Slow Falling Splash", Formatting.WHITE, "Float gently down", "Safe descents"));
        presets.put("potion-poison-ii", () -> potionLore(Potions.STRONG_POISON, "Poison II Splash", Formatting.DARK_GREEN, "Toxic cloud", "Damage over time"));
        presets.put("potion-harming-ii", () -> potionLore(Potions.STRONG_HARMING, "Harming II Splash", Formatting.DARK_RED, "Instant damage", "Deals 12 hearts"));
        presets.put("potion-harming", () -> potionLore(Potions.HARMING, "Harming Splash", Formatting.DARK_RED, "Instant damage", "Deals 6 hearts"));
        presets.put("potion-leaping", () -> potionLore(Potions.LEAPING, "Leaping Splash", Formatting.GREEN, "Jump super high", "Leap over walls"));

        presets.put("book-enchant-all", this::enchantBook);
        presets.put("book-survival-guide", this::survivalGuideBook);

        presets.put("ultimate-kit", this::ultimateKit);
        presets.put("pvp-kit", this::pvpKit);
        presets.put("builder-kit", this::builderKit);
        presets.put("end-kit", this::endKit);
        presets.put("nether-kit", this::netherKit);
        presets.put("fishing-kit", this::fishingKit);
        presets.put("redstone-kit", this::redstoneKit);

        presets.put("shulker-full", this::fullShulker);
        presets.put("music-discs", this::musicDiscs);

        presets.put("lodestone", () -> simpleLore(Items.LODESTONE, "Lodestone", Formatting.GRAY, "Compass anchor", "Points to this block"));
        presets.put("echo-shard-stack", () -> stackLore(Items.ECHO_SHARD, 64, "Echo Shard", Formatting.DARK_AQUA, "Sculk resonance", "64 shards of echo"));
        presets.put("recovery-compass", () -> simpleLore(Items.RECOVERY_COMPASS, "Recovery Compass", Formatting.AQUA, "Find your death location", "Points to last death"));
        presets.put("bundle-stack", () -> stackLore(Items.BUNDLE, 64, "Bundle Stack", Formatting.GOLD, "Carry more items", "64 empty bundles"));
        presets.put("saddle-stack", () -> stackLore(Items.SADDLE, 64, "Saddle Stack", Formatting.GOLD, "Mount everything", "64 saddles"));
        presets.put("debug-stick", () -> simpleLore(Items.STICK, "Debug Stick", Formatting.AQUA, "Edit block states", "Creative only"));
        presets.put("lodestone-compass", () -> simpleLore(Items.COMPASS, "Lodestone Compass", Formatting.YELLOW, "Points to lodestone", "Navigate with precision"));
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
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
        sb.append("Books: book-enchant-all, book-survival-guide\n");
        sb.append("Kits: ultimate-kit, pvp-kit, builder-kit, end-kit, nether-kit, fishing-kit, redstone-kit\n");
        sb.append("Special: shulker-full, music-discs, lodestone, echo-shard-stack, recovery-compass, bundle-stack, saddle-stack, debug-stick, lodestone-compass");
        info(sb.toString());
    }

    private void giveItem(ItemStack item) {
        int slot = mc.player.getInventory().getSelectedSlot();
        mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(36 + slot, item));
        mc.player.playerScreenHandler.getSlot(36 + slot).setStack(item);
    }

    private Text name(String value, Formatting color) {
        return Text.literal(value).setStyle(Style.EMPTY.withColor(color).withBold(true));
    }

    private Text lore(String text) {
        return Text.literal(text).setStyle(Style.EMPTY.withItalic(true).withColor(Formatting.GRAY));
    }

    private Text orbiterLore() {
        return Text.literal("\u00a76\u2b50 Orbiter Preset").setStyle(Style.EMPTY.withColor(Formatting.GOLD));
    }

    private ItemStack simpleLore(Item item, String n, Formatting c, String l1, String l2) {
        ItemStack s = new ItemStack(item, 1);
        s.set(DataComponentTypes.CUSTOM_NAME, name(n, c));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(lore(l1), lore(l2), orbiterLore())));
        return s;
    }

    private ItemStack stackLore(Item item, int count, String n, Formatting c, String l1, String l2) {
        ItemStack s = new ItemStack(item, count);
        s.set(DataComponentTypes.CUSTOM_NAME, name(n, c));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(lore(l1), lore(l2), orbiterLore())));
        return s;
    }

    private ItemStack potionLore(net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> ref, String n, Formatting c, String l1, String l2) {
        ItemStack s = new ItemStack(Items.SPLASH_POTION);
        s.set(DataComponentTypes.CUSTOM_NAME, name(n, c));
        s.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(ref));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(lore(l1), lore(l2), orbiterLore())));
        return s;
    }

    private void addEnchant(ItemEnchantmentsComponent.Builder b, String id, int lv) {
        if (mc.world == null) return;
        String clean = id.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (!clean.contains(":")) clean = "minecraft:" + clean;
        String[] p = clean.split(":", 2);
        if (p.length != 2) return;
        RegistryKey<Enchantment> key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(p[0], p[1]));
        mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key).ifPresent(r -> b.add(r, lv));
    }

    private ItemEnchantmentsComponent.Builder baseEnchants(String... enchants) {
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (String e : enchants) {
            String[] parts = e.split(":");
            addEnchant(b, parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 255);
        }
        return b;
    }

    private void setLore(ItemStack s, String... lines) {
        List<Text> loreList = new ArrayList<>();
        for (String l : lines) loreList.add(lore(l));
        loreList.add(orbiterLore());
        s.set(DataComponentTypes.LORE, new LoreComponent(loreList));
    }

    private ItemStack godSword() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Sword", Formatting.RED));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("sharpness:255","smite:255","bane_of_arthropods:255","knockback:255","fire_aspect:255","looting:255","sweeping_edge:255","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","gs_d"), 2048, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.ATTACK_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","gs_s"), 1024, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "The ultimate melee weapon", "One strike, one kill");
        return s;
    }

    private ItemStack godBow() {
        ItemStack s = new ItemStack(Items.BOW);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Bow", Formatting.GREEN));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("power:255","punch:255","flame:1","infinity:1","unbreaking:255","mending:1").build());
        setLore(s, "Never miss, never run out", "Arrows of destruction");
        return s;
    }

    private ItemStack godArmor(Item item, String n, int idx) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, name(n, Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("protection:255","blast_protection:255","fire_protection:255","projectile_protection:255","thorns:255","unbreaking:255","mending:1","respiration:255","aqua_affinity:1","depth_strider:255","soul_speed:255","swift_sneak:255").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ARMOR, new EntityAttributeModifier(Identifier.of("orbiter","ga_a"+idx), 1000, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.ANY);
        a.add(EntityAttributes.ARMOR_TOUGHNESS, new EntityAttributeModifier(Identifier.of("orbiter","ga_t"+idx), 1000, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.ANY);
        a.add(EntityAttributes.KNOCKBACK_RESISTANCE, new EntityAttributeModifier(Identifier.of("orbiter","ga_k"+idx), 1.0, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.ANY);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Immovable defense", "Walk through fire and explosions");
        return s;
    }

    private ItemStack godAxe() {
        ItemStack s = new ItemStack(Items.NETHERITE_AXE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Axe", Formatting.GOLD));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("sharpness:255","efficiency:255","fortune:255","silk_touch:1","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","ga_d"), 2048, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.ATTACK_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","ga_s"), 1024, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Chop anything in one hit", "The woodcutter's dream");
        return s;
    }

    private ItemStack godPickaxe() {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Pickaxe", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("efficiency:255","fortune:255","silk_touch:1","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","gp_d"), 1024, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.ATTACK_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","gp_s"), 1024, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Mine anything instantly", "Fortune and silk touch in one");
        return s;
    }

    private ItemStack godTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Trident", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("loyalty:255","impaling:255","channeling:1","unbreaking:255","mending:1").build());
        setLore(s, "Command the seas and storms", "Returns after every throw");
        return s;
    }

    private ItemStack godMace() {
        ItemStack s = new ItemStack(Items.MACE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Mace", Formatting.GOLD));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("density:255","wind_burst:255","smashing:1","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","gm_d"), 4096, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Smash from the heavens", "Wind burst sends enemies flying");
        return s;
    }

    private ItemStack godCrossbow() {
        ItemStack s = new ItemStack(Items.CROSSBOW);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Crossbow", Formatting.RED));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("quick_charge:255","piercing:255","multishot:1","unbreaking:255","mending:1").build());
        setLore(s, "Instant fire, pierces all", "3 arrows at once");
        return s;
    }

    private ItemStack godFishingRod() {
        ItemStack s = new ItemStack(Items.FISHING_ROD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Fishing Rod", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("luck_of_the_sea:255","lure:255","unbreaking:255","mending:1").build());
        setLore(s, "Catch anything in the water", "Max luck, max speed");
        return s;
    }

    private ItemStack excalibur() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Excalibur", Formatting.YELLOW));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("sharpness:255","smite:255","knockback:255","fire_aspect:255","sweeping_edge:255","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","exc_d"), 4096, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.ATTACK_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","exc_s"), 2048, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.LUCK, new EntityAttributeModifier(Identifier.of("orbiter","exc_l"), 100, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "The legendary sword of King Arthur", "Forged in dragon fire", "Only the worthy may wield it");
        return s;
    }

    private ItemStack poseidon() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Poseidon\u2019s Trident", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("loyalty:255","impaling:255","channeling:1","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","pos_d"), 4096, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.MOVEMENT_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","pos_s"), 1000, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Weapon of the sea god", "Commands lightning and tide", "Swim faster than dolphins");
        return s;
    }

    private ItemStack mjolnir() {
        ItemStack s = new ItemStack(Items.MACE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Mjolnir", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("smite:255","density:255","wind_burst:255","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","mjol_d"), 8192, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.ATTACK_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","mjol_s"), 4096, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Thor\u2019s legendary hammer", "Strikes with the power of thunder", "Only the worthy may lift it");
        return s;
    }

    private ItemStack anubis() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Anubis", Formatting.DARK_GRAY));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("smite:255","fire_aspect:255","knockback:255","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","anu_d"), 4096, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        a.add(EntityAttributes.ATTACK_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","anu_s"), 2048, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Blade of the Egyptian death god", "Judges the souls of the fallen", "Smite the undead into oblivion");
        return s;
    }

    private ItemStack thunderBlade() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Thunder Blade", Formatting.YELLOW));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("sharpness:255","fire_aspect:255","knockback:255","sweeping_edge:255","unbreaking:255","mending:1").build());
        setLore(s, "Forged in a thunderstorm", "Each strike brings lightning", "Electrify your enemies");
        return s;
    }

    private ItemStack frostbite() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Frostbite", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("sharpness:255","knockback:255","unbreaking:255","mending:1").build());
        setLore(s, "Frozen in eternal ice", "Slows enemies on hit", "Winter\u2019s vengeance");
        return s;
    }

    private ItemStack infinityBlade() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Infinity Blade", Formatting.LIGHT_PURPLE));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("sharpness:255","smite:255","bane_of_arthropods:255","knockback:255","fire_aspect:255","looting:255","sweeping_edge:255","unbreaking:255","mending:1").build());
        setLore(s, "Contains every damage enchantment", "255 levels of pure destruction", "No entity survives this blade");
        return s;
    }

    private ItemStack smiteSword() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Smite Sword", Formatting.DARK_RED));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("smite:255","fire_aspect:255","looting:255","unbreaking:255","mending:1").build());
        setLore(s, "The undead\u2019s worst nightmare", "Extra damage to all undead mobs");
        return s;
    }

    private ItemStack arthropodAxe() {
        ItemStack s = new ItemStack(Items.NETHERITE_AXE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Arthropod Slayer", Formatting.DARK_GREEN));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("bane_of_arthropods:255","sharpness:255","looting:255","unbreaking:255","mending:1").build());
        setLore(s, "Spider and bug exterminator", "One-shots all arthropods");
        return s;
    }

    private ItemStack fortunePick() {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Fortune King", Formatting.GOLD));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("fortune:255","efficiency:255","unbreaking:255","mending:1").build());
        setLore(s, "Every ore drops maximum items", "The mining jackpot pickaxe");
        return s;
    }

    private ItemStack silkPick() {
        ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Silk Touch Master", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("silk_touch:1","efficiency:255","unbreaking:255","mending:1").build());
        setLore(s, "Collect blocks as they are", "No ore conversion, pure blocks");
        return s;
    }

    private ItemStack riptideTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Riptide Rider", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("riptide:255","unbreaking:255","mending:1").build());
        setLore(s, "Launch yourself through the sky", "Ride rain and thunder");
        return s;
    }

    private ItemStack channelingTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Storm Bringer", Formatting.YELLOW));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("channeling:1","loyalty:255","impaling:255","unbreaking:255","mending:1").build());
        setLore(s, "Summon lightning on every throw", "The sky bows to your will");
        return s;
    }

    private ItemStack featherBoots() {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Gravity Defier", Formatting.WHITE));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("feather_falling:255","protection:255","depth_strider:255","unbreaking:255","mending:1").build());
        setLore(s, "Fall from any height safely", "Land like a feather");
        return s;
    }

    private ItemStack respirationHelmet() {
        ItemStack s = new ItemStack(Items.NETHERITE_HELMET);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Deep Sea Diver", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("respiration:255","aqua_affinity:1","protection:255","unbreaking:255","mending:1").build());
        setLore(s, "Breathe underwater forever", "See clearly in the deep ocean");
        return s;
    }

    private ItemStack thornsShield() {
        ItemStack s = new ItemStack(Items.SHIELD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Pain Reflector", Formatting.RED));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("unbreaking:255","mending:1").build());
        setLore(s, "Every hit hurts the attacker", "Reflect damage back");
        return s;
    }

    private ItemStack speedBoots() {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Speed Boots", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("protection:255","depth_strider:255","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.MOVEMENT_SPEED, new EntityAttributeModifier(Identifier.of("orbiter","sb"), 1000, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.FEET);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Run at lightning speed", "Outrun anything");
        return s;
    }

    private ItemStack frostBoots() {
        ItemStack s = new ItemStack(Items.NETHERITE_BOOTS);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Frost Boots", Formatting.WHITE));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("frost_walker:255","protection:255","unbreaking:255","mending:1").build());
        setLore(s, "Walk on water and lava", "Freeze everything underfoot");
        return s;
    }

    private ItemStack lootingSword() {
        ItemStack s = new ItemStack(Items.NETHERITE_SWORD);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Looting Master", Formatting.GOLD));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("looting:255","sharpness:255","fire_aspect:255","unbreaking:255","mending:1").build());
        AttributeModifiersComponent.Builder a = AttributeModifiersComponent.builder();
        a.add(EntityAttributes.ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.of("orbiter","ls_d"), 1024, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND);
        s.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, a.build());
        setLore(s, "Maximum loot from every kill", "Looting 255 = infinite drops");
        return s;
    }

    private ItemStack elytra() {
        ItemStack s = new ItemStack(Items.ELYTRA);
        s.set(DataComponentTypes.CUSTOM_NAME, name("God Elytra", Formatting.AQUA));
        s.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponentTypes.ENCHANTMENTS, baseEnchants("unbreaking:255","mending:1").build());
        setLore(s, "Fly across the world", "Never breaks, never stops");
        return s;
    }

    private ItemStack totemOfDying() {
        ItemStack s = new ItemStack(Items.TOTEM_OF_UNDYING);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Totem of Dying", Formatting.GOLD));
        setLore(s, "Extra life in your offhand", "Respawn on death");
        return s;
    }

    private ItemStack enchantBook() {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Enchantment Guide", Formatting.GOLD));
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        String[] data = {"Sharpness, Smite, Bane of Arthropods\nKnockback, Fire Aspect\nLooting, Sweeping Edge", "Power, Punch, Flame\nInfinity, Riptide, Loyalty\nChanneling, Impaling", "Protection, Blast Protection\nFire Protection, Projectile Protection\nThorns, Respiration", "Depth Strider, Frost Walker\nSoul Speed, Swift Sneak\nFeather Falling, Aqua Affinity", "Efficiency, Fortune, Silk Touch\nQuick Charge, Piercing, Multishot\nDensity, Wind Burst, Smashing", "Unbreaking, Mending\nCurse of Vanishing\nCurse of Binding"};
        for (String d : data) pages.add(RawFilteredPair.of(Text.literal(d)));
        s.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of("Enchantments"), "Orbiter", 0, pages, true));
        setLore(s, "Complete enchantment reference", "All enchantments listed");
        return s;
    }

    private ItemStack survivalGuideBook() {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Survival Guide", Formatting.GREEN));
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        pages.add(RawFilteredPair.of(Text.literal("Welcome to the Orbiter Survival Guide!\n\nTip 1: Always carry a totem\nTip 2: Netherite armor is king\nTip 3: Enchant everything")));
        pages.add(RawFilteredPair.of(Text.literal("Tip 4: Elytra + fireworks = freedom\nTip 5: Fortune 3 on diamonds\nTip 6: Never dig straight down\nTip 7: Keep away from creepers")));
        pages.add(RawFilteredPair.of(Text.literal("Tip 8: Beds explode in the Nether\nTip 9: Shield blocks most attacks\nTip 10: Mending keeps gear alive")));
        s.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of("Survival Guide"), "Orbiter", 0, pages, true));
        setLore(s, "Your survival companion", "10 tips for surviving Minecraft");
        return s;
    }

    private ItemStack ultimateKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Ultimate Kit", Formatting.LIGHT_PURPLE));
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
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "Everything you need to dominate", "27 god-tier items in one shulker");
        return s;
    }

    private ItemStack pvpKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("PvP Kit", Formatting.RED));
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
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "Ready for any PvP encounter", "God gear + consumables");
        return s;
    }

    private ItemStack builderKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Builder Kit", Formatting.GREEN));
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
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "Everything a builder needs", "Blocks, tools, and utilities");
        return s;
    }

    private ItemStack endKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("End Kit", Formatting.DARK_PURPLE));
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
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "Conquer the End dimension", "Crystals, pearls, and firework elytra");
        return s;
    }

    private ItemStack netherKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Nether Kit", Formatting.DARK_RED));
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
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "Survive the Nether like a pro", "Fire protection and supplies");
        return s;
    }

    private ItemStack fishingKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Fishing Kit", Formatting.AQUA));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(godFishingRod());
        contents.add(new ItemStack(Items.LILY_PAD, 64));
        contents.add(new ItemStack(Items.WATER_BUCKET, 64));
        contents.add(new ItemStack(Items.CHEST, 64));
        contents.add(new ItemStack(Items.NETHERITE_AXE));
        while (contents.size() < 27) contents.add(new ItemStack(Items.AIR));
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "Master fisherman setup", "Catch everything in the water");
        return s;
    }

    private ItemStack redstoneKit() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Redstone Kit", Formatting.RED));
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
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "All the redstone you need", "Build anything automated");
        return s;
    }

    private ItemStack fullShulker() {
        ItemStack s = new ItemStack(Items.SHULKER_BOX);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Random Enchanted Shulker", Formatting.LIGHT_PURPLE));
        Item[] items = {Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.SHIELD, Items.ELYTRA, Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.SHEARS, Items.END_CRYSTAL, Items.TOTEM_OF_UNDYING, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.ENDER_EYE, Items.BLAZE_ROD, Items.NETHER_STAR, Items.DRAGON_BREATH, Items.TOTEM_OF_UNDYING};
        List<ItemStack> contents = new ArrayList<>();
        for (Item item : items) {
            ItemStack is = new ItemStack(item);
            is.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
            if (mc.world != null) {
                ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
                mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).streamEntries().limit(3).forEach(ref -> b.add(ref, 255));
                is.set(DataComponentTypes.ENCHANTMENTS, b.build());
    @FunctionalInterface
    interface PresetFactory {
        ItemStack create();
    }
}

            contents.add(is);
        }
        s.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(contents));
        setLore(s, "27 randomly enchanted items", "Each with 3 random max enchantments");
        return s;
    }

    private ItemStack musicDiscs() {
        ItemStack s = new ItemStack(Items.MUSIC_DISC_CREATOR);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Music Disc: Creator", Formatting.LIGHT_PURPLE));
        setLore(s, "The latest music disc", "Beautiful soundtrack");
        return s;
    }

    private ItemStack allSpawnEggs() {
        ItemStack s = new ItemStack(Items.ENDER_DRAGON_SPAWN_EGG);
        s.set(DataComponentTypes.CUSTOM_NAME, name("Spawn Egg Collection", Formatting.RED));
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
