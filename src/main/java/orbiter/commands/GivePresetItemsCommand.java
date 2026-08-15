package orbiter.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.network.Filterable;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class GivePresetItemsCommand extends Command {
    private static final SimpleCommandExceptionType NOT_IN_CREATIVE =
            new SimpleCommandExceptionType(Component.literal("You must be in creative mode to use this."));
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private record Preset(String name, String desc, Supplier<ItemStack> creator) {}
    private final List<Preset> allPresets = new ArrayList<>();

    private void reg(String name, String desc, Supplier<ItemStack> creator) {
        allPresets.add(new Preset(name, desc, creator));
    }

    private record ToolMat(String id, String display, Item sword, Item axe, Item pick, Item shovel, Item hoe,
                           Item helmet, Item chest, Item legs, Item boots) {}

    private static final ToolMat[] MATS = {
        new ToolMat("wooden", "Wood", Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS),
        new ToolMat("stone", "Stone", Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_HOE,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS),
        new ToolMat("iron", "Iron", Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
        new ToolMat("golden", "Golden", Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
            Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS),
        new ToolMat("diamond", "Diamond", Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
        new ToolMat("netherite", "Netherite", Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS),
    };

    private static final IntList FIREWORK_COLORS = new IntArrayList(new int[]{
            16711680, 65280, 255, 16776960, 16777215, 8388736, 16711935, 65535
    });
    private static final IntList FIREWORK_FADE = new IntArrayList(new int[]{16777215, 0, 16711680});

    public GivePresetItemsCommand() {
        super("givepresetitems", "300+ useful creative-mode presets. Send via Creative Write Packet.", "gpi");
        registerAll();
    }

    private void registerAll() {
        registerTools();
        registerArmor();
        registerWeapons();
        registerFireworks();
        registerPotions();
        registerTippedArrows();
        registerSpawnEggs();
        registerEnchantedBooks();
        registerKits();
        registerUtility();
        registerMisc();
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> { showList(); return SINGLE_SUCCESS; });

        builder.then(literal("list").executes(context -> { showList(); return SINGLE_SUCCESS; }));

        builder.then(literal("all").executes(context -> {
            ensureCreative();
            int count = 0;
            for (Preset p : allPresets) {
                giveItem(p.creator().get());
                count++;
            }
            info("Gave " + count + " preset items.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("head")
                .then(argument("player", StringArgumentType.word()).executes(context -> {
                    givePlayerHead(StringArgumentType.getString(context, "player"));
                    return SINGLE_SUCCESS;
                })));

        for (Preset p : allPresets) {
            String safeName = p.name();
            builder.then(literal(safeName).executes(context -> {
                runPreset(safeName, p.creator().get());
                return SINGLE_SUCCESS;
            }));
        }
    }

    private void registerTools() {
        for (ToolMat m : MATS) {
            reg("op-" + m.id + "-sword", m.display + " Sword • Sharp 255, Fire Aspect 255, Looting 255",
                () -> makeGodSword(m));
            reg("op-" + m.id + "-axe", m.display + " Axe • Sharp 255, Efficiency 255, Fortune 255",
                () -> makeGodAxe(m));
            reg("op-" + m.id + "-pickaxe", m.display + " Pickaxe • Efficiency 255, Fortune 255, Silk Touch",
                () -> makeGodPickaxe(m));
            reg("op-" + m.id + "-shovel", m.display + " Shovel • Efficiency 255, Fortune 255, Silk Touch",
                () -> makeGodShovel(m));
            reg("op-" + m.id + "-hoe", m.display + " Hoe • Efficiency 255, Fortune 255, Silk Touch, Unbreaking 255",
                () -> makeGodHoe(m));
        }
    }

    private ItemStack makeGodSword(ToolMat m) {
        ItemStack s = new ItemStack(m.sword, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Sword", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "sharpness", 255);
        addEnchant(eb, "fire_aspect", 255);
        addEnchant(eb, "looting", 255);
        addEnchant(eb, "sweeping_edge", 255);
        addEnchant(eb, "knockback", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        ab.add(Attributes.ATTACK_SPEED, mod("spd", 2048), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Sharp 255 + 10000 damage + Fire Aspect 255 + Looting 255"),
            line("Sweeping Edge 255 + Knockback 255"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodAxe(ToolMat m) {
        ItemStack s = new ItemStack(m.axe, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Axe", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "sharpness", 255);
        addEnchant(eb, "efficiency", 255);
        addEnchant(eb, "fortune", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        ab.add(Attributes.ATTACK_SPEED, mod("spd", 2048), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Sharp 255 + 10000 damage + Efficiency 255 + Fortune 255"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodPickaxe(ToolMat m) {
        ItemStack s = new ItemStack(m.pick, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Pickaxe", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "efficiency", 255);
        addEnchant(eb, "fortune", 255);
        addEnchant(eb, "silk_touch", 1);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        ab.add(Attributes.ATTACK_SPEED, mod("spd", 2048), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Efficiency 255 + Fortune 255 + Silk Touch + 10000 damage"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodShovel(ToolMat m) {
        ItemStack s = new ItemStack(m.shovel, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Shovel", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "efficiency", 255);
        addEnchant(eb, "fortune", 255);
        addEnchant(eb, "silk_touch", 1);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Efficiency 255 + Fortune 255 + Silk Touch • instant mine"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodHoe(ToolMat m) {
        ItemStack s = new ItemStack(m.hoe, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Hoe", ChatFormatting.GREEN));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "efficiency", 255);
        addEnchant(eb, "fortune", 255);
        addEnchant(eb, "silk_touch", 1);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Efficiency 255 + Fortune 255 + Silk Touch"),
            gold("Orbiter Preset"))));
        return s;
    }

    private void registerArmor() {
        for (ToolMat m : MATS) {
            reg("op-" + m.id + "-helmet", m.display + " Helmet • all Protection 255, Respiration 255",
                () -> makeGodHelmet(m));
            reg("op-" + m.id + "-chestplate", m.display + " Chestplate • all Protection 255, Thorns 255",
                () -> makeGodChest(m));
            reg("op-" + m.id + "-leggings", m.display + " Leggings • all Protection 255, Swift Sneak 255",
                () -> makeGodLegs(m));
            reg("op-" + m.id + "-boots", m.display + " Boots • all Protection 255, Depth Strider 255, Soul Speed 255",
                () -> makeGodBoots(m));
        }

        reg("op-turtle-helmet", "Turtle Shell • Respiration 255 + all Protection 255", this::makeGodTurtleShell);

        reg("god-elytra", "God Elytra • Unbreaking 255 + Mending", this::makeGodElytra);
        reg("op-elytra", "OP Elytra • Unbreaking 255 + Mending + extra firework boost", this::makeOpElytra);
    }

    private void addGodArmorEnchants(ItemEnchantments.Mutable eb) {
        addEnchant(eb, "protection", 255);
        addEnchant(eb, "blast_protection", 255);
        addEnchant(eb, "fire_protection", 255);
        addEnchant(eb, "projectile_protection", 255);
        addEnchant(eb, "thorns", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
    }

    private ItemStack makeGodHelmet(ToolMat m) {
        ItemStack s = new ItemStack(m.helmet, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Helmet", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addGodArmorEnchants(eb);
        addEnchant(eb, "respiration", 255);
        addEnchant(eb, "aqua_affinity", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ARMOR, mod("armor", 1000), EquipmentSlotGroup.HEAD);
        ab.add(Attributes.ARMOR_TOUGHNESS, mod("tough", 1000), EquipmentSlotGroup.HEAD);
        ab.add(Attributes.KNOCKBACK_RESISTANCE, mod("kb", 1), EquipmentSlotGroup.HEAD);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("All Protection 255 + Respiration 255 + 1000 Armor"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodChest(ToolMat m) {
        ItemStack s = new ItemStack(m.chest, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Chestplate", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addGodArmorEnchants(eb);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ARMOR, mod("armor", 1000), EquipmentSlotGroup.CHEST);
        ab.add(Attributes.ARMOR_TOUGHNESS, mod("tough", 1000), EquipmentSlotGroup.CHEST);
        ab.add(Attributes.KNOCKBACK_RESISTANCE, mod("kb", 1), EquipmentSlotGroup.CHEST);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("All Protection 255 + Thorns 255 + 1000 Armor"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodLegs(ToolMat m) {
        ItemStack s = new ItemStack(m.legs, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Leggings", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addGodArmorEnchants(eb);
        addEnchant(eb, "swift_sneak", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ARMOR, mod("armor", 1000), EquipmentSlotGroup.LEGS);
        ab.add(Attributes.ARMOR_TOUGHNESS, mod("tough", 1000), EquipmentSlotGroup.LEGS);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("All Protection 255 + Swift Sneak 255 + 1000 Armor"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodBoots(ToolMat m) {
        ItemStack s = new ItemStack(m.boots, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP " + m.display + " Boots", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addGodArmorEnchants(eb);
        addEnchant(eb, "depth_strider", 255);
        addEnchant(eb, "soul_speed", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ARMOR, mod("armor", 1000), EquipmentSlotGroup.FEET);
        ab.add(Attributes.ARMOR_TOUGHNESS, mod("tough", 1000), EquipmentSlotGroup.FEET);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("All Protection 255 + Depth Strider 255 + Soul Speed 255"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodTurtleShell() {
        ItemStack s = new ItemStack(Items.TURTLE_HELMET, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP Turtle Shell", ChatFormatting.GREEN));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addGodArmorEnchants(eb);
        addEnchant(eb, "respiration", 255);
        addEnchant(eb, "aqua_affinity", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Respiration 255 + All Protection 255 • underwater god"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodElytra() {
        ItemStack s = new ItemStack(Items.ELYTRA, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("God Elytra", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Unbreaking 255 + Mending • fly forever"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpElytra() {
        ItemStack s = new ItemStack(Items.ELYTRA, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP Elytra", ChatFormatting.DARK_AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        addEnchant(eb, "protection", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Unbreaking 255 + Mending + Protection 255"),
            line("Pair with Flight 127 Rockets for max flight"),
            gold("Orbiter Preset"))));
        return s;
    }

    private void registerWeapons() {
        reg("op-bow", "Bow • Power 255, Flame 255, Punch 255, Infinity", this::makeOpBow);
        reg("op-crossbow", "Crossbow • Multishot, Piercing 255, Quick Charge 255", this::makeOpCrossbow);
        reg("op-trident", "Trident • Impaling 255, Loyalty 255, Channeling", this::makeOpTrident);
        reg("op-mace", "Mace • Density 255, Breach 255, Wind Burst 255", this::makeOpMace);
        reg("god-shield", "Shield • Unbreaking 255 + Mending", this::makeGodShield);
        reg("void-star", "Void Star (Nether Star) • 10000 damage, 2048 attack speed", this::makeVoidStar);
        reg("speed-ring", "Ring of Speed (Clock) • +1.0 Movement Speed", this::makeSpeedRing);
        reg("reach-ring", "Ring of Reach (Stick) • +10 Block/Entity Range", this::makeReachRing);
        reg("god-fishing-rod", "God Fishing Rod • Luck 255, Lure 255, Unbreaking 255", this::makeGodFishingRod);
        reg("god-shears", "God Shears • Efficiency 255, Unbreaking 255, Mending", this::makeGodShears);
        reg("god-flint-steel", "God Flint and Steel • Unbreaking 255, Mending", this::makeGodFlintSteel);
        reg("god-lead", "Extended Lead • +50 Block Interaction Range", this::makeGodLead);
    }

    private ItemStack makeOpBow() {
        ItemStack s = new ItemStack(Items.BOW, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP Bow", ChatFormatting.DARK_GREEN));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "power", 255);
        addEnchant(eb, "flame", 255);
        addEnchant(eb, "punch", 255);
        addEnchant(eb, "infinity", 1);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Power 255 + Flame 255 + Punch 255 + Infinity"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpCrossbow() {
        ItemStack s = new ItemStack(Items.CROSSBOW, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP Crossbow", ChatFormatting.DARK_GREEN));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "multishot", 1);
        addEnchant(eb, "piercing", 255);
        addEnchant(eb, "quick_charge", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Multishot + Piercing 255 + Quick Charge 255"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP Trident", ChatFormatting.DARK_AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "impaling", 255);
        addEnchant(eb, "loyalty", 255);
        addEnchant(eb, "channeling", 1);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Impaling 255 + Loyalty 255 + Channeling + 10000 damage"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpMace() {
        ItemStack s = new ItemStack(Items.MACE, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("OP Mace", ChatFormatting.DARK_RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "density", 255);
        addEnchant(eb, "breach", 255);
        addEnchant(eb, "wind_burst", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        ab.add(Attributes.ATTACK_SPEED, mod("spd", 2048), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Density 255 + Breach 255 + Wind Burst 255 + 10000 damage"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodShield() {
        ItemStack s = new ItemStack(Items.SHIELD, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("God Shield", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Unbreaking 255 + Mending • never breaks"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeVoidStar() {
        ItemStack s = new ItemStack(Items.NETHER_STAR, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Void Star", ChatFormatting.DARK_PURPLE));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        ab.add(Attributes.ATTACK_SPEED, mod("spd", 2048), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("10000 damage + 2048 attack speed • one hit anything"),
            line("The power of the void in your hand"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSpeedRing() {
        ItemStack s = new ItemStack(Items.CLOCK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Ring of Speed", ChatFormatting.AQUA));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.MOVEMENT_SPEED, mod("speed", 1.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+1.0 Movement Speed • hold in any slot for extreme speed"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeReachRing() {
        ItemStack s = new ItemStack(Items.STICK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Ring of Reach", ChatFormatting.GREEN));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.BLOCK_INTERACTION_RANGE, mod("block", 10.0), EquipmentSlotGroup.ANY);
        ab.add(Attributes.ENTITY_INTERACTION_RANGE, mod("entity", 10.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+10 Block Interaction Range + +10 Entity Interaction Range"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodFishingRod() {
        ItemStack s = new ItemStack(Items.FISHING_ROD, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("God Fishing Rod", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "luck", 255);
        addEnchant(eb, "lure", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Luck 255 + Lure 255 • instant rare catches"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodShears() {
        ItemStack s = new ItemStack(Items.SHEARS, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("God Shears", ChatFormatting.GREEN));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "efficiency", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Efficiency 255 • instant mine wool/leaves"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodFlintSteel() {
        ItemStack s = new ItemStack(Items.FLINT_AND_STEEL, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("God Flint & Steel", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Unbreaking 255 + Mending • never runs out"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodLead() {
        ItemStack s = new ItemStack(Items.LEAD, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Extended Lead", ChatFormatting.YELLOW));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.BLOCK_INTERACTION_RANGE, mod("reach", 50.0), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+50 Block Interaction Range • leash anything from afar"),
            gold("Orbiter Preset"))));
        return s;
    }

    private void registerFireworks() {

        int[] flights = {1, 2, 3, 5, 8, 10, 15, 20, 30, 50, 75, 100, 127};
        for (int f : flights) {
            final int flight = f;
            reg("rocket-f" + f, "Firework Rocket • Flight " + f + " x64",
                () -> makeRocket(flight, 64, 1, false));
        }

        reg("rocket-max", "Lag Rocket • Flight 127 + 256 explosions x64", this::makeMaxRocket);

        reg("rocket-rainbow", "Rainbow Rocket • Flight 127, all colors x64", this::makeRainbowRocket);
        reg("rocket-star", "Star Rocket • Flight 127, star burst x64", this::makeStarRocket);
        reg("rocket-creeper", "Creeper Rocket • Flight 127, creeper shape x64", this::makeCreeperRocket);
    }

    private ItemStack makeRocket(int flight, int count, int explosionCount, boolean big) {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET, count);
        s.set(DataComponents.CUSTOM_NAME, name("Flight " + flight + " Rocket", ChatFormatting.AQUA));
        List<FireworkExplosion> exps = new ArrayList<>();
        for (int i = 0; i < explosionCount; i++) {
            exps.add(new FireworkExplosion(
                big ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.SMALL_BALL,
                new IntArrayList(new int[]{16777215}), new IntArrayList(new int[]{0}), false, false));
        }
        s.set(DataComponents.FIREWORKS, new Fireworks(flight, exps));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Flight Duration " + flight + " • massive elytra boost"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeMaxRocket() {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Max Lag Rocket", ChatFormatting.DARK_RED));
        List<FireworkExplosion> exps = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            exps.add(new FireworkExplosion(FireworkExplosion.Shape.STAR,
                FIREWORK_COLORS, FIREWORK_FADE, true, true));
        }
        s.set(DataComponents.FIREWORKS, new Fireworks(127, exps));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Flight 127 + 256 explosions • max flight + max visual"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeRainbowRocket() {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Rainbow Rocket", ChatFormatting.LIGHT_PURPLE));
        List<FireworkExplosion> exps = new ArrayList<>();
        int[] colors = {0xFF0000, 0xFF8800, 0xFFFF00, 0x00FF00, 0x0088FF, 0x8800FF};
        for (int c : colors) {
            exps.add(new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                new IntArrayList(new int[]{c}), new IntArrayList(new int[]{0}), true, false));
        }
        s.set(DataComponents.FIREWORKS, new Fireworks(127, exps));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Flight 127 • rainbow trail across the sky"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeStarRocket() {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Star Rocket", ChatFormatting.GOLD));
        List<FireworkExplosion> exps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            exps.add(new FireworkExplosion(FireworkExplosion.Shape.STAR,
                new IntArrayList(new int[]{0xFFFF00}), new IntArrayList(new int[]{0xFF8800}), true, true));
        }
        s.set(DataComponents.FIREWORKS, new Fireworks(127, exps));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Flight 127 • 10 golden star bursts"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeCreeperRocket() {
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Creeper Rocket", ChatFormatting.GREEN));
        List<FireworkExplosion> exps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            exps.add(new FireworkExplosion(FireworkExplosion.Shape.CREEPER,
                new IntArrayList(new int[]{0x00FF00}), new IntArrayList(new int[]{0}), true, false));
        }
        s.set(DataComponents.FIREWORKS, new Fireworks(127, exps));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Flight 127 • creeper face fireworks"),
            gold("Orbiter Preset"))));
        return s;
    }

    private record PotionDef(String name, Holder<Potion> base, List<MobEffectInstance> effects) {}

    private static final PotionDef[] POTIONS = {
        new PotionDef("God Speed", Potions.STRONG_SWIFTNESS, List.of(
            new MobEffectInstance(MobEffects.SPEED, 9600, 2),
            new MobEffectInstance(MobEffects.HASTE, 9600, 2),
            new MobEffectInstance(MobEffects.SLOW_FALLING, 9600, 0))),
        new PotionDef("God Strength", Potions.STRONG_STRENGTH, List.of(
            new MobEffectInstance(MobEffects.STRENGTH, 9600, 2),
            new MobEffectInstance(MobEffects.RESISTANCE, 9600, 2))),
        new PotionDef("God Regen", Potions.STRONG_REGENERATION, List.of(
            new MobEffectInstance(MobEffects.REGENERATION, 9600, 2),
            new MobEffectInstance(MobEffects.ABSORPTION, 9600, 2))),
        new PotionDef("God Fire Resist", Potions.FIRE_RESISTANCE, List.of(
            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 9600, 0),
            new MobEffectInstance(MobEffects.RESISTANCE, 9600, 1))),
        new PotionDef("God Night Vision", Potions.LONG_NIGHT_VISION, List.of(
            new MobEffectInstance(MobEffects.NIGHT_VISION, 9600, 0),
            new MobEffectInstance(MobEffects.WATER_BREATHING, 9600, 0))),
        new PotionDef("God Jump", Potions.STRONG_LEAPING, List.of(
            new MobEffectInstance(MobEffects.JUMP_BOOST, 9600, 2),
            new MobEffectInstance(MobEffects.SLOW_FALLING, 9600, 0))),
        new PotionDef("God Haste", Potions.STRONG_SWIFTNESS, List.of(
            new MobEffectInstance(MobEffects.HASTE, 9600, 2),
            new MobEffectInstance(MobEffects.SPEED, 9600, 1))),
        new PotionDef("God Resistance", Potions.STRONG_STRENGTH, List.of(
            new MobEffectInstance(MobEffects.RESISTANCE, 9600, 2),
            new MobEffectInstance(MobEffects.ABSORPTION, 9600, 2))),
        new PotionDef("God Invisibility", Potions.LONG_INVISIBILITY, List.of(
            new MobEffectInstance(MobEffects.INVISIBILITY, 9600, 0),
            new MobEffectInstance(MobEffects.NIGHT_VISION, 9600, 0))),
        new PotionDef("God Conduit", Potions.STRONG_SWIFTNESS, List.of(
            new MobEffectInstance(MobEffects.WATER_BREATHING, 9600, 0),
            new MobEffectInstance(MobEffects.HASTE, 9600, 2),
            new MobEffectInstance(MobEffects.RESISTANCE, 9600, 1),
            new MobEffectInstance(MobEffects.SPEED, 9600, 1))),
    };

    private void registerPotions() {
        for (PotionDef p : POTIONS) {
            final String safeName = p.name.toLowerCase(Locale.ROOT).replace(' ', '-');

            reg("potion-" + safeName, "Potion: " + p.name + " (Drinkable)", () -> makePotion(p, false, false));

            reg("splash-" + safeName, "Splash Potion: " + p.name, () -> makePotion(p, true, false));

            reg("lingering-" + safeName, "Lingering Potion: " + p.name, () -> makePotion(p, true, true));
        }
    }

    private ItemStack makePotion(PotionDef p, boolean splash, boolean lingering) {
        Item item = lingering ? Items.LINGERING_POTION : (splash ? Items.SPLASH_POTION : Items.POTION);
        ItemStack s = new ItemStack(item, 1);
        s.set(DataComponents.CUSTOM_NAME, name((splash ? "Splash " : lingering ? "Lingering " : "") + p.name, ChatFormatting.LIGHT_PURPLE));
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(
            Optional.of(p.base), Optional.empty(), new ArrayList<>(p.effects), Optional.empty()));
        List<Component> lore = new ArrayList<>();
        for (MobEffectInstance eff : p.effects) {
            lore.add(line(eff.getDescriptionId().replace("potion.", "") + " " + (eff.getAmplifier() + 1) + " • " + (eff.getDuration() / 20) + "s"));
        }
        lore.add(gold("Orbiter Preset"));
        s.set(DataComponents.LORE, new ItemLore(lore));
        return s;
    }

    private void registerTippedArrows() {
        record ArrowDef(String name, Holder<Potion> base, int duration, int amplifier) {}
        ArrowDef[] arrows = {
            new ArrowDef("Harming II", Potions.STRONG_HARMING, 0, 0),
            new ArrowDef("Poison II", Potions.STRONG_POISON, 840, 1),
            new ArrowDef("Slowness II", Potions.STRONG_SLOWNESS, 840, 1),
            new ArrowDef("Weakness II", Potions.LONG_WEAKNESS, 840, 1),
            new ArrowDef("Healing II", Potions.STRONG_HEALING, 0, 0),
            new ArrowDef("Swiftness II", Potions.STRONG_SWIFTNESS, 840, 1),
            new ArrowDef("Strength II", Potions.STRONG_STRENGTH, 840, 1),
            new ArrowDef("Leaping II", Potions.STRONG_LEAPING, 840, 1),
            new ArrowDef("Fire Resist", Potions.FIRE_RESISTANCE, 3600, 0),
            new ArrowDef("Night Vision", Potions.LONG_NIGHT_VISION, 9600, 0),
            new ArrowDef("Water Breathing", Potions.LONG_WATER_BREATHING, 9600, 0),
            new ArrowDef("Regeneration II", Potions.STRONG_REGENERATION, 480, 1),
            new ArrowDef("Strength II", Potions.STRONG_STRENGTH, 840, 1),
            new ArrowDef("Slow Falling", Potions.SLOW_FALLING, 840, 0),
            new ArrowDef("Invisibility", Potions.LONG_INVISIBILITY, 9600, 0),
            new ArrowDef("Haste II", Potions.STRONG_SWIFTNESS, 840, 1),
            new ArrowDef("Luck", Potions.LUCK, 9600, 0),
            new ArrowDef("Absorption", Potions.LEAPING, 840, 1),
            new ArrowDef("Slow Falling", Potions.SLOW_FALLING, 840, 0),
            new ArrowDef("Poison", Potions.LONG_POISON, 9600, 0),
        };
        for (ArrowDef a : arrows) {
            final String safeName = a.name.toLowerCase(Locale.ROOT).replace(' ', '-').replace("ii", "2");
            final Holder<Potion> basePotion = a.base;
            reg("arrow-" + safeName, "Tipped Arrow: " + a.name + " x64", () -> {
                ItemStack s = new ItemStack(Items.TIPPED_ARROW, 64);
                s.set(DataComponents.CUSTOM_NAME, name(a.name + " Arrow x64", ChatFormatting.AQUA));
                s.set(DataComponents.POTION_CONTENTS, new PotionContents(basePotion));
                s.set(DataComponents.LORE, new ItemLore(List.of(
                    line("64x " + a.name + " Tipped Arrows"),
                    gold("Orbiter Preset"))));
                return s;
            });
        }
    }

    private void registerSpawnEggs() {
        record EggDef(String name, Item egg, String entityId, String customName, int count,
                       int customNameVisible, ChatFormatting color) {}
        EggDef[] eggs = {
            new EggDef("Dragon", Items.ENDER_DRAGON_SPAWN_EGG, "minecraft:ender_dragon", "Ender Dragon", 1, 1, ChatFormatting.DARK_PURPLE),
            new EggDef("Wither", Items.WITHER_SKELETON_SPAWN_EGG, "minecraft:wither", "Wither Boss", 1, 1, ChatFormatting.DARK_GRAY),
            new EggDef("Elder Guardian", Items.ELDER_GUARDIAN_SPAWN_EGG, "minecraft:elder_guardian", "Elder Guardian", 64, 1, ChatFormatting.AQUA),
            new EggDef("Wither Storm", Items.WARDEN_SPAWN_EGG, "minecraft:warden", "Warden", 64, 1, ChatFormatting.DARK_BLUE),
            new EggDef("Ravager", Items.RAVAGER_SPAWN_EGG, "minecraft:ravager", "Ravager", 64, 1, ChatFormatting.GRAY),
            new EggDef("Iron Golem", Items.IRON_GOLEM_SPAWN_EGG, "minecraft:iron_golem", "Iron Golem", 64, 1, ChatFormatting.WHITE),
            new EggDef("Snow Golem", Items.SNOW_GOLEM_SPAWN_EGG, "minecraft:snow_golem", "Snow Golem", 64, 1, ChatFormatting.WHITE),
            new EggDef("Allay", Items.ALLAY_SPAWN_EGG, "minecraft:allay", "Allay", 64, 1, ChatFormatting.AQUA),
            new EggDef("Axolotl", Items.AXOLOTL_SPAWN_EGG, "minecraft:axolotl", "Axolotl", 64, 1, ChatFormatting.RED),
            new EggDef("Bee", Items.BEE_SPAWN_EGG, "minecraft:bee", "Bee", 64, 1, ChatFormatting.GOLD),
            new EggDef("Cat", Items.CAT_SPAWN_EGG, "minecraft:cat", "Cat", 64, 1, ChatFormatting.YELLOW),
            new EggDef("Dolphin", Items.DOLPHIN_SPAWN_EGG, "minecraft:dolphin", "Dolphin", 64, 1, ChatFormatting.AQUA),
            new EggDef("Fox", Items.FOX_SPAWN_EGG, "minecraft:fox", "Fox", 64, 1, ChatFormatting.GOLD),
            new EggDef("Frog", Items.FROG_SPAWN_EGG, "minecraft:frog", "Frog", 64, 1, ChatFormatting.GREEN),
            new EggDef("Glow Squid", Items.GLOW_SQUID_SPAWN_EGG, "minecraft:glow_squid", "Glow Squid", 64, 1, ChatFormatting.AQUA),
            new EggDef("Goat", Items.GOAT_SPAWN_EGG, "minecraft:goat", "Goat", 64, 1, ChatFormatting.WHITE),
            new EggDef("Horse", Items.HORSE_SPAWN_EGG, "minecraft:horse", "Horse", 64, 1, ChatFormatting.GOLD),
            new EggDef("Mooshroom", Items.MOOSHROOM_SPAWN_EGG, "minecraft:mooshroom", "Mooshroom", 64, 1, ChatFormatting.RED),
            new EggDef("Ocelot", Items.OCELOT_SPAWN_EGG, "minecraft:ocelot", "Ocelot", 64, 1, ChatFormatting.GOLD),
            new EggDef("Panda", Items.PANDA_SPAWN_EGG, "minecraft:panda", "Panda", 64, 1, ChatFormatting.WHITE),
            new EggDef("Parrot", Items.PARROT_SPAWN_EGG, "minecraft:parrot", "Parrot", 64, 1, ChatFormatting.RED),
            new EggDef("Polar Bear", Items.POLAR_BEAR_SPAWN_EGG, "minecraft:polar_bear", "Polar Bear", 64, 1, ChatFormatting.WHITE),
            new EggDef("Rabbit", Items.RABBIT_SPAWN_EGG, "minecraft:rabbit", "Rabbit", 64, 1, ChatFormatting.GOLD),
            new EggDef("Turtle", Items.TURTLE_SPAWN_EGG, "minecraft:turtle", "Turtle", 64, 1, ChatFormatting.GREEN),
            new EggDef("Wolf", Items.WOLF_SPAWN_EGG, "minecraft:wolf", "Wolf", 64, 1, ChatFormatting.GRAY),
            new EggDef("Sniffer", Items.SNIFFER_SPAWN_EGG, "minecraft:sniffer", "Sniffer", 64, 1, ChatFormatting.RED),
            new EggDef("Armadillo", Items.ARMADILLO_SPAWN_EGG, "minecraft:armadillo", "Armadillo", 64, 1, ChatFormatting.GOLD),
            new EggDef("Breeze", Items.BREEZE_SPAWN_EGG, "minecraft:breeze", "Breeze", 64, 1, ChatFormatting.AQUA),
            new EggDef("Bogged", Items.BOGGED_SPAWN_EGG, "minecraft:bogged", "Bogged", 64, 1, ChatFormatting.GREEN),
        };
        for (EggDef e : eggs) {
            final String safeName = e.name.toLowerCase(Locale.ROOT).replace(' ', '-');
            final Item eggItem = e.egg;
            final String eid = e.entityId;
            final String cname = e.customName;
            final int cnt = e.count;
            final int cnv = e.customNameVisible;
            final ChatFormatting col = e.color;
            reg("egg-" + safeName, "Spawn Egg: " + e.name + " x" + e.count, () -> {
                ItemStack s = new ItemStack(eggItem, cnt);
                s.set(DataComponents.CUSTOM_NAME, name(cname, col));
                CompoundTag nbt = new CompoundTag();
                nbt.putString("id", eid);
                nbt.putInt("CustomNameVisible", cnv);
                s.set(DataComponents.ENTITY_DATA, TypedEntityData.of(
                    net.minecraft.world.entity.EntityTypes.CAVE_SPIDER, nbt));
                s.set(DataComponents.LORE, new ItemLore(List.of(
                    line(cname + " spawn egg x" + cnt),
                    gold("Orbiter Preset"))));
                return s;
            });
        }
    }

    private void registerEnchantedBooks() {
        String[][] books = {
            {"sharpness", "Sharpness"},
            {"smite", "Smite"},
            {"bane_of_arthropods", "Bane of Arthropods"},
            {"knockback", "Knockback"},
            {"fire_aspect", "Fire Aspect"},
            {"looting", "Looting"},
            {"sweeping_edge", "Sweeping Edge"},
            {"efficiency", "Efficiency"},
            {"fortune", "Fortune"},
            {"silk_touch", "Silk Touch"},
            {"unbreaking", "Unbreaking"},
            {"power", "Power"},
            {"punch", "Punch"},
            {"flame", "Flame"},
            {"infinity", "Infinity"},
            {"protection", "Protection"},
            {"blast_protection", "Blast Protection"},
            {"fire_protection", "Fire Protection"},
            {"projectile_protection", "Projectile Protection"},
            {"thorns", "Thorns"},
            {"respiration", "Respiration"},
            {"aqua_affinity", "Aqua Affinity"},
            {"depth_strider", "Depth Strider"},
            {"frost_walker", "Frost Walker"},
            {"soul_speed", "Soul Speed"},
            {"swift_sneak", "Swift Sneak"},
            {"impaling", "Impaling"},
            {"loyalty", "Loyalty"},
            {"channeling", "Channeling"},
            {"mending", "Mending"},
        };
        for (String[] b : books) {
            final String enchantId = b[0];
            final String displayName = b[1];
            final String safeName = enchantId.replace('_', '-');
            reg("book-" + safeName, "Enchanted Book: " + displayName + " 255", () -> {
                ItemStack s = new ItemStack(Items.ENCHANTED_BOOK, 1);
                s.set(DataComponents.CUSTOM_NAME, name(displayName + " 255", ChatFormatting.GREEN));
                ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                addEnchant(eb, enchantId, 255);
                s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
                s.set(DataComponents.LORE, new ItemLore(List.of(
                    line(displayName + " at level 255 • impossible in vanilla"),
                    gold("Orbiter Preset"))));
                return s;
            });
        }
    }

    private void registerKits() {
        reg("kit-pvp", "PvP Kit • Full god armor + OP weapons + potions + totems", this::makePvpKit);
        reg("kit-mining", "Mining Kit • OP tools + shulker boxes + torches", this::makeMiningKit);
        reg("kit-building", "Building Kit • Every building block x64 + tools", this::makeBuildingKit);
        reg("kit-explorer", "Explorer Kit • Elytra + rockets + potions + food + maps", this::makeExplorerKit);
        reg("kit-god", "God Kit • Everything OP in one box", this::makeGodKit);
        reg("kit-cleanup", "Cleanup Kit • TNT + bedrock + world edit tools", this::makeCleanupKit);
    }

    private ItemStack makePvpKit() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CUSTOM_NAME, name("PvP Kit", ChatFormatting.RED));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(makeGodSword(MATS[5]));
        contents.add(makeOpBow());
        contents.add(makeOpCrossbow());
        contents.add(makeGodHelmet(MATS[5]));
        contents.add(makeGodChest(MATS[5]));
        contents.add(makeGodLegs(MATS[5]));
        contents.add(makeGodBoots(MATS[5]));
        contents.add(makeGodShield());
        for (int i = 0; i < 8; i++) {
            ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING, 64);
            totem.set(DataComponents.CUSTOM_NAME, name("Totem x64", ChatFormatting.GOLD));
            contents.add(totem);
        }
        for (PotionDef p : POTIONS) {
            contents.add(makePotion(p, true, false));
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        box.set(DataComponents.LORE, new ItemLore(List.of(
            line("Full PvP loadout in a box • armor, weapons, potions, totems"),
            gold("Orbiter Preset"))));
        return box;
    }

    private ItemStack makeMiningKit() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CUSTOM_NAME, name("Mining Kit", ChatFormatting.AQUA));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(makeGodPickaxe(MATS[5]));
        contents.add(makeGodAxe(MATS[5]));
        contents.add(makeGodShovel(MATS[5]));
        contents.add(makeGodHoe(MATS[5]));
        contents.add(makeGodShears());
        contents.add(makeGodFlintSteel());
        for (int i = 0; i < 4; i++) {
            ItemStack torches = new ItemStack(Items.TORCH, 64);
            torches.set(DataComponents.CUSTOM_NAME, name("Torches x64", ChatFormatting.YELLOW));
            contents.add(torches);
        }
        ItemStack lanterns = new ItemStack(Items.LANTERN, 64);
        lanterns.set(DataComponents.CUSTOM_NAME, name("Lanterns x64", ChatFormatting.YELLOW));
        contents.add(lanterns);
        for (PotionDef p : new PotionDef[]{POTIONS[0], POTIONS[5], POTIONS[6]}) {
            contents.add(makePotion(p, false, false));
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        box.set(DataComponents.LORE, new ItemLore(List.of(
            line("Everything you need to mine • OP tools + torches + potions"),
            gold("Orbiter Preset"))));
        return box;
    }

    private ItemStack makeBuildingKit() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CUSTOM_NAME, name("Building Kit", ChatFormatting.GREEN));
        List<ItemStack> contents = new ArrayList<>();
        Item[] blocks = {
            Items.STONE, Items.DEEPSLATE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
            Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
            Items.COBBLESTONE, Items.BRICKS, Items.STONE_BRICKS, Items.NETHER_BRICKS,
            Items.GLASS, Items.STAINED_GLASS.pick(DyeColor.WHITE), Items.OBSIDIAN, Items.CRYING_OBSIDIAN,
            Items.GLOWSTONE, Items.SEA_LANTERN, Items.SHROOMLIGHT, Items.IRON_BLOCK,
            Items.DIAMOND_BLOCK, Items.GOLD_BLOCK, Items.EMERALD_BLOCK, Items.NETHERITE_BLOCK,
            Items.BEDROCK, Items.BARRIER, Items.STRUCTURE_VOID,
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG,
        };
        for (Item b : blocks) {
            ItemStack stack = new ItemStack(b, 64);
            stack.set(DataComponents.CUSTOM_NAME, name(b.getName(net.minecraft.world.item.ItemStack.EMPTY).getString() + " x64", ChatFormatting.GREEN));
            contents.add(stack);
        }
        contents.add(makeGodPickaxe(MATS[5]));
        contents.add(makeGodAxe(MATS[5]));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        box.set(DataComponents.LORE, new ItemLore(List.of(
            line("30 block types x64 + OP tools • complete building kit"),
            gold("Orbiter Preset"))));
        return box;
    }

    private ItemStack makeExplorerKit() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CUSTOM_NAME, name("Explorer Kit", ChatFormatting.GOLD));
        List<ItemStack> contents = new ArrayList<>();
        contents.add(makeGodElytra());
        for (int f : new int[]{50, 100, 127}) {
            contents.add(makeRocket(f, 64, 1, false));
        }
        for (PotionDef p : new PotionDef[]{POTIONS[0], POTIONS[2], POTIONS[4], POTIONS[6], POTIONS[8]}) {
            contents.add(makePotion(p, false, false));
        }
        ItemStack food = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64);
        food.set(DataComponents.CUSTOM_NAME, name("God Apples x64", ChatFormatting.GOLD));
        contents.add(food);
        ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 64);
        pearls.set(DataComponents.CUSTOM_NAME, name("Ender Pearls x64", ChatFormatting.DARK_PURPLE));
        contents.add(pearls);
        ItemStack compass = new ItemStack(Items.COMPASS, 1);
        compass.set(DataComponents.CUSTOM_NAME, name("Explorer Compass", ChatFormatting.YELLOW));
        contents.add(compass);
        ItemStack clock = new ItemStack(Items.CLOCK, 1);
        clock.set(DataComponents.CUSTOM_NAME, name("Explorer Clock", ChatFormatting.YELLOW));
        contents.add(clock);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        box.set(DataComponents.LORE, new ItemLore(List.of(
            line("Elytra + Flight 127 rockets + potions + food + navigation"),
            gold("Orbiter Preset"))));
        return box;
    }

    private ItemStack makeGodKit() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CUSTOM_NAME, name("God Kit", ChatFormatting.DARK_PURPLE));
        List<ItemStack> contents = new ArrayList<>();

        contents.add(makeGodHelmet(MATS[5]));
        contents.add(makeGodChest(MATS[5]));
        contents.add(makeGodLegs(MATS[5]));
        contents.add(makeGodBoots(MATS[5]));
        contents.add(makeGodElytra());

        contents.add(makeGodSword(MATS[5]));
        contents.add(makeGodAxe(MATS[5]));
        contents.add(makeOpBow());
        contents.add(makeOpCrossbow());
        contents.add(makeOpTrident());
        contents.add(makeOpMace());
        contents.add(makeGodShield());

        contents.add(makeGodPickaxe(MATS[5]));
        contents.add(makeGodShovel(MATS[5]));
        contents.add(makeGodHoe(MATS[5]));
        contents.add(makeGodShears());
        contents.add(makeGodFishingRod());

        for (PotionDef p : POTIONS) {
            contents.add(makePotion(p, true, false));
        }
        ItemStack totems = new ItemStack(Items.TOTEM_OF_UNDYING, 64);
        totems.set(DataComponents.CUSTOM_NAME, name("Totems x64", ChatFormatting.GOLD));
        contents.add(totems);
        ItemStack apples = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64);
        apples.set(DataComponents.CUSTOM_NAME, name("God Apples x64", ChatFormatting.GOLD));
        contents.add(apples);
        contents.add(makeRocket(127, 64, 1, false));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        box.set(DataComponents.LORE, new ItemLore(List.of(
            line("THE ultimate kit • every god item in one shulker box"),
            gold("Orbiter Preset"))));
        return box;
    }

    private ItemStack makeCleanupKit() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX, 1);
        box.set(DataComponents.CUSTOM_NAME, name("Cleanup Kit", ChatFormatting.RED));
        List<ItemStack> contents = new ArrayList<>();
        ItemStack tnt = new ItemStack(Items.TNT, 64);
        tnt.set(DataComponents.CUSTOM_NAME, name("TNT x64", ChatFormatting.RED));
        contents.add(tnt);
        ItemStack bedrock = new ItemStack(Items.BEDROCK, 64);
        bedrock.set(DataComponents.CUSTOM_NAME, name("Bedrock x64", ChatFormatting.DARK_GRAY));
        contents.add(bedrock);
        ItemStack barrier = new ItemStack(Items.BARRIER, 64);
        barrier.set(DataComponents.CUSTOM_NAME, name("Barrier x64", ChatFormatting.RED));
        contents.add(barrier);
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL, 1);
        flint.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        flint.set(DataComponents.CUSTOM_NAME, name("Unbreaking Flint & Steel", ChatFormatting.RED));
        contents.add(flint);
        ItemStack ender = new ItemStack(Items.ENDER_PEARL, 64);
        ender.set(DataComponents.CUSTOM_NAME, name("Ender Pearls x64", ChatFormatting.DARK_PURPLE));
        contents.add(ender);
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN, 64);
        obsidian.set(DataComponents.CUSTOM_NAME, name("Obsidian x64", ChatFormatting.DARK_PURPLE));
        contents.add(obsidian);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        box.set(DataComponents.LORE, new ItemLore(List.of(
            line("TNT + Bedrock + Barriers + Flint & Steel • world cleanup"),
            gold("Orbiter Preset"))));
        return box;
    }

    private void registerUtility() {

        reg("totems-64", "64x Totems of Undying", this::makeTotems64);
        reg("totems-stack", "Stacked Totems x64", this::makeTotemStack);

        reg("god-apples-64", "64x Enchanted Golden Apples", this::makeGodApples64);
        reg("golden-carrots-64", "64x Golden Carrots", this::makeGoldenCarrots64);
        reg("cooked-beef-64", "64x Cooked Beef", this::makeCookedBeef64);
        reg("cake-stack", "64x Cakes", this::makeCakeStack);
        reg("enchanted-bread", "Enchanted Bread x64 • Regeneration", this::makeEnchantedBread);
        reg("enchanted-steak", "Enchanted Steak x64 • All buffs", this::makeEnchantedSteak);

        reg("arrows-harm-64", "64x Instant Damage II Arrows", () -> makeTippedArrow64(Potions.STRONG_HARMING, "Harming II"));
        reg("arrows-poison-64", "64x Poison II Arrows", () -> makeTippedArrow64(Potions.STRONG_POISON, "Poison II"));
        reg("arrows-slow-64", "64x Slowness II Arrows", () -> makeTippedArrow64(Potions.STRONG_SLOWNESS, "Slowness II"));
        reg("arrows-heal-64", "64x Instant Health II Arrows", () -> makeTippedArrow64(Potions.STRONG_HEALING, "Healing II"));

        reg("ender-pearls-64", "64x Ender Pearls", this::makeEnderPearls64);
        reg("eyes-64", "64x Eyes of Ender", this::makeEyes64);

        reg("sponge-64", "64x Sponges", this::makeSponge64);
        reg("wet-sponge-64", "64x Wet Sponges", this::makeWetSponge64);

        reg("shulker-shells-64", "64x Shulker Shells", this::makeShulkerShells64);
        reg("echo-shards-64", "64x Echo Shards", this::makeEchoShards64);
        reg("nether-stars-64", "64x Nether Stars", this::makeNetherStars64);
        reg("dragon-eggs", "64x Dragon Eggs", this::makeDragonEggs);
        reg("end-crystals-64", "64x End Crystals", this::makeEndCrystals64);

        reg("speed-apple", "Speed Apple • Enchanted Golden Apple + Movement Speed", this::makeSpeedApple);
        reg("speed-carrot", "Speed Carrot • Golden Carrot + Movement Speed", this::makeSpeedCarrot);
        reg("reach-stick", "Reach Stick • +20 Block/Entity Interaction Range", this::makeReachStick);
        reg("flight-stick", "Flight Stick • +100 Knockback Resistance + +50 Flying Speed", this::makeFlightStick);

        reg("health-band", "Health Band (Emerald) • +100 Max Health", this::makeHealthBand);
        reg("damage-ring", "Damage Ring (Redstone) • +500 Attack Damage", this::makeDamageRing);
        reg("knockback-gauntlet", "Knockback Gauntlet (Gold) • +10 Knockback", this::makeKnockbackGauntlet);

        reg("name-tag-red", "Red Name Tag", () -> makeNameTag("Red Tag", ChatFormatting.RED));
        reg("name-tag-gold", "Gold Name Tag", () -> makeNameTag("Gold Tag", ChatFormatting.GOLD));
        reg("name-tag-aqua", "Aqua Name Tag", () -> makeNameTag("Aqua Tag", ChatFormatting.AQUA));
        reg("name-tag-green", "Green Name Tag", () -> makeNameTag("Green Tag", ChatFormatting.GREEN));
        reg("name-tag-lp", "Purple Name Tag", () -> makeNameTag("Purple Tag", ChatFormatting.LIGHT_PURPLE));

        reg("music-discs", "All Music Discs", this::makeAllMusicDiscs);
        reg("jukebox-64", "64x Jukeboxes", this::makeJukebox64);

        reg("lodestone-compass", "Lodestone Compass", this::makeLodestoneCompass);
        reg("recovery-compass", "Recovery Compass", this::makeRecoveryCompass);

        reg("boat-64", "64x Oak Boats", this::makeBoat64);
        reg("chest-boat-64", "64x Oak Chest Boats", this::makeChestBoat64);

        reg("egg-villager-64", "64x Villager Spawn Eggs", () -> makeMobEgg64(Items.VILLAGER_SPAWN_EGG, "Villager"));
        reg("egg-iron-golem-64", "64x Iron Golem Spawn Eggs", () -> makeMobEgg64(Items.IRON_GOLEM_SPAWN_EGG, "Iron Golem"));
        reg("egg-wandering-trader-64", "64x Wandering Trader Eggs", () -> makeMobEgg64(Items.WANDERING_TRADER_SPAWN_EGG, "Wandering Trader"));
        reg("egg-cat-64", "64x Cat Spawn Eggs", () -> makeMobEgg64(Items.CAT_SPAWN_EGG, "Cat"));
        reg("egg-dolphin-64", "64x Dolphin Spawn Eggs", () -> makeMobEgg64(Items.DOLPHIN_SPAWN_EGG, "Dolphin"));
        reg("egg-parrot-64", "64x Parrot Spawn Eggs", () -> makeMobEgg64(Items.PARROT_SPAWN_EGG, "Parrot"));
        reg("egg-bee-64", "64x Bee Spawn Eggs", () -> makeMobEgg64(Items.BEE_SPAWN_EGG, "Bee"));
        reg("egg-fox-64", "64x Fox Spawn Eggs", () -> makeMobEgg64(Items.FOX_SPAWN_EGG, "Fox"));
        reg("egg-axolotl-64", "64x Axolotl Spawn Eggs", () -> makeMobEgg64(Items.AXOLOTL_SPAWN_EGG, "Axolotl"));
        reg("egg-allay-64", "64x Allay Spawn Eggs", () -> makeMobEgg64(Items.ALLAY_SPAWN_EGG, "Allay"));
        reg("egg-frog-64", "64x Frog Spawn Eggs", () -> makeMobEgg64(Items.FROG_SPAWN_EGG, "Frog"));
        reg("egg-turtle-64", "64x Turtle Spawn Eggs", () -> makeMobEgg64(Items.TURTLE_SPAWN_EGG, "Turtle"));
        reg("egg-wolf-64", "64x Wolf Spawn Eggs", () -> makeMobEgg64(Items.WOLF_SPAWN_EGG, "Wolf"));
        reg("egg-horse-64", "64x Horse Spawn Eggs", () -> makeMobEgg64(Items.HORSE_SPAWN_EGG, "Horse"));
        reg("egg-ravager-64", "64x Ravager Spawn Eggs", () -> makeMobEgg64(Items.RAVAGER_SPAWN_EGG, "Ravager"));
        reg("egg-warden-64", "64x Warden Spawn Eggs", () -> makeMobEgg64(Items.WARDEN_SPAWN_EGG, "Warden"));
        reg("egg-sniffer-64", "64x Sniffer Spawn Eggs", () -> makeMobEgg64(Items.SNIFFER_SPAWN_EGG, "Sniffer"));
        reg("egg-breeze-64", "64x Breeze Spawn Eggs", () -> makeMobEgg64(Items.BREEZE_SPAWN_EGG, "Breeze"));
        reg("egg-polar-bear-64", "64x Polar Bear Eggs", () -> makeMobEgg64(Items.POLAR_BEAR_SPAWN_EGG, "Polar Bear"));
        reg("egg-panda-64", "64x Panda Spawn Eggs", () -> makeMobEgg64(Items.PANDA_SPAWN_EGG, "Panda"));
        reg("egg-mooshroom-64", "64x Mooshroom Eggs", () -> makeMobEgg64(Items.MOOSHROOM_SPAWN_EGG, "Mooshroom"));
        reg("egg-glow-squid-64", "64x Glow Squid Eggs", () -> makeMobEgg64(Items.GLOW_SQUID_SPAWN_EGG, "Glow Squid"));
        reg("egg-goat-64", "64x Goat Spawn Eggs", () -> makeMobEgg64(Items.GOAT_SPAWN_EGG, "Goat"));
        reg("egg-snow-golem-64", "64x Snow Golem Eggs", () -> makeMobEgg64(Items.SNOW_GOLEM_SPAWN_EGG, "Snow Golem"));
        reg("egg-ocelot-64", "64x Ocelot Spawn Eggs", () -> makeMobEgg64(Items.OCELOT_SPAWN_EGG, "Ocelot"));
        reg("egg-rabbit-64", "64x Rabbit Spawn Eggs", () -> makeMobEgg64(Items.RABBIT_SPAWN_EGG, "Rabbit"));
        reg("egg-armadillo-64", "64x Armadillo Eggs", () -> makeMobEgg64(Items.ARMADILLO_SPAWN_EGG, "Armadillo"));
        reg("egg-bogged-64", "64x Bogged Spawn Eggs", () -> makeMobEgg64(Items.BOGGED_SPAWN_EGG, "Bogged"));

        reg("golden-apple-64", "64x Golden Apples", () -> makeStackItem(Items.GOLDEN_APPLE, 64, "Golden Apples x64", ChatFormatting.GOLD));
        reg("ender-pearl-64", "64x Ender Pearls", () -> makeStackItem(Items.ENDER_PEARL, 64, "Ender Pearls x64", ChatFormatting.DARK_PURPLE));
        reg("experience-bottle-64", "64x XP Bottles", () -> makeStackItem(Items.EXPERIENCE_BOTTLE, 64, "XP Bottles x64", ChatFormatting.GREEN));
        reg("ender-pearl-16", "16x Ender Pearls", () -> makeStackItem(Items.ENDER_PEARL, 16, "Ender Pearls x16", ChatFormatting.DARK_PURPLE));
        reg("bone-meal-64", "64x Bone Meal", () -> makeStackItem(Items.BONE_MEAL, 64, "Bone Meal x64", ChatFormatting.WHITE));
        reg("slime-ball-64", "64x Slime Balls", () -> makeStackItem(Items.SLIME_BALL, 64, "Slime Balls x64", ChatFormatting.GREEN));
        reg("blaze-powder-64", "64x Blaze Powder", () -> makeStackItem(Items.BLAZE_POWDER, 64, "Blaze Powder x64", ChatFormatting.GOLD));
        reg("blaze-rod-64", "64x Blaze Rods", () -> makeStackItem(Items.BLAZE_ROD, 64, "Blaze Rods x64", ChatFormatting.GOLD));
        reg("nether-wart-64", "64x Nether Wart", () -> makeStackItem(Items.NETHER_WART, 64, "Nether Wart x64", ChatFormatting.RED));
        reg("diamond-64", "64x Diamonds", () -> makeStackItem(Items.DIAMOND, 64, "Diamonds x64", ChatFormatting.AQUA));
        reg("emerald-64", "64x Emeralds", () -> makeStackItem(Items.EMERALD, 64, "Emeralds x64", ChatFormatting.GREEN));
        reg("netherite-ingot-64", "64x Netherite Ingots", () -> makeStackItem(Items.NETHERITE_INGOT, 64, "Netherite Ingots x64", ChatFormatting.DARK_GRAY));
        reg("elytra-stack", "Elytra x64", () -> makeStackItem(Items.ELYTRA, 64, "Elytra x64", ChatFormatting.AQUA));
        reg("trident-stack", "Trident x64", () -> makeStackItem(Items.TRIDENT, 64, "Trident x64", ChatFormatting.DARK_AQUA));
        reg("totem-stack-64", "Totem x64", () -> makeStackItem(Items.TOTEM_OF_UNDYING, 64, "Totems x64", ChatFormatting.GOLD));
        reg("trident-far", "Trident • +50 Reach", this::makeReachTrident);
        reg("fishing-far", "Fishing Rod • +30 Reach", this::makeReachFishingRod);
        reg("crossbow-fire", "Fire Crossbow • Flame + Multishot + Quick Charge", this::makeFireCrossbow);
        reg("bow-fire", "Fire Bow • Flame 255 + Punch 255", this::makeFireBow);
    }

    private ItemStack makeTotems64() {
        ItemStack s = new ItemStack(Items.TOTEM_OF_UNDYING, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Infinity Totems x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64 lives in your pocket • never die again"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeTotemStack() {
        ItemStack s = new ItemStack(Items.TOTEM_OF_UNDYING, 64);
        s.set(DataComponents.CUSTOM_NAME, name("God Totem Stack", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x Totem of Undying • max death protection"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGodApples64() {
        ItemStack s = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64);
        s.set(DataComponents.CUSTOM_NAME, name("God Apple Stack x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64 Notch Apples • infinite Absorption + Regen + Resistance"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeGoldenCarrots64() {
        ItemStack s = new ItemStack(Items.GOLDEN_CARROT, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Golden Carrots x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x best food in the game • max saturation"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeCookedBeef64() {
        ItemStack s = new ItemStack(Items.COOKED_BEEF, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Steak Stack x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x steak • instant hunger fill"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeCakeStack() {
        ItemStack s = new ItemStack(Items.CAKE, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Cake Stack x64", ChatFormatting.YELLOW));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64 cakes • place and eat everywhere"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeEnchantedBread() {
        ItemStack s = new ItemStack(Items.BREAD, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Enchanted Bread x64", ChatFormatting.GREEN));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Glowing bread • looks magical, feeds well"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeEnchantedSteak() {
        ItemStack s = new ItemStack(Items.COOKED_BEEF, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Enchanted Steak x64", ChatFormatting.RED));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Glowing steak • the fanciest food"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeTippedArrow64(Holder<Potion> base, String displayName) {
        ItemStack s = new ItemStack(Items.TIPPED_ARROW, 64);
        s.set(DataComponents.CUSTOM_NAME, name(displayName + " Arrow x64", ChatFormatting.AQUA));
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(base));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x " + displayName + " Tipped Arrows"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeEnderPearls64() {
        ItemStack s = new ItemStack(Items.ENDER_PEARL, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Ender Pearls x64", ChatFormatting.DARK_PURPLE));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x ender pearls • teleport at will"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeEyes64() {
        ItemStack s = new ItemStack(Items.ENDER_EYE, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Eyes of Ender x64", ChatFormatting.DARK_PURPLE));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x eyes of ender • find the stronghold instantly"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSponge64() {
        ItemStack s = new ItemStack(Items.SPONGE, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Sponges x64", ChatFormatting.YELLOW));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x sponges • drain entire ocean monuments"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeWetSponge64() {
        ItemStack s = new ItemStack(Items.WET_SPONGE, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Wet Sponges x64", ChatFormatting.AQUA));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x wet sponges • decorative or drain in the nether"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeShulkerShells64() {
        ItemStack s = new ItemStack(Items.SHULKER_SHELL, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Shulker Shells x64", ChatFormatting.LIGHT_PURPLE));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x shulker shells • craft 32 shulker boxes"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeEchoShards64() {
        ItemStack s = new ItemStack(Items.ECHO_SHARD, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Echo Shards x64", ChatFormatting.DARK_PURPLE));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x echo shards • craft 16 recovery compasses"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeNetherStars64() {
        ItemStack s = new ItemStack(Items.NETHER_STAR, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Nether Stars x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x nether stars • craft 64 beacons"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeDragonEggs() {
        ItemStack s = new ItemStack(Items.DRAGON_EGG, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Dragon Egg", ChatFormatting.DARK_PURPLE));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("The rarest block in vanilla • unobtainable in survival"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeEndCrystals64() {
        ItemStack s = new ItemStack(Items.END_CRYSTAL, 64);
        s.set(DataComponents.CUSTOM_NAME, name("End Crystals x64", ChatFormatting.RED));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x end crystals • respawn the dragon or use as weapon"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSpeedApple() {
        ItemStack s = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Speed Apple", ChatFormatting.AQUA));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.MOVEMENT_SPEED, mod("speed", 0.4), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+0.4 Movement Speed • always active when in inventory"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSpeedCarrot() {
        ItemStack s = new ItemStack(Items.GOLDEN_CARROT, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Speed Carrots x64", ChatFormatting.AQUA));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.MOVEMENT_SPEED, mod("speed", 0.3), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+0.3 Movement Speed • eat and run fast"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeReachStick() {
        ItemStack s = new ItemStack(Items.STICK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Mega Reach Stick", ChatFormatting.GREEN));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.BLOCK_INTERACTION_RANGE, mod("block", 20.0), EquipmentSlotGroup.ANY);
        ab.add(Attributes.ENTITY_INTERACTION_RANGE, mod("entity", 20.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+20 Block/Entity Range • reach anything from 25 blocks away"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeFlightStick() {
        ItemStack s = new ItemStack(Items.STICK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Flight Stick", ChatFormatting.AQUA));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.KNOCKBACK_RESISTANCE, mod("kb", 1.0), EquipmentSlotGroup.ANY);
        ab.add(Attributes.FLYING_SPEED, mod("fly", 50.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+50 Flying Speed + Full Knockback Resistance"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeHealthBand() {
        ItemStack s = new ItemStack(Items.EMERALD, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Health Band", ChatFormatting.GREEN));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.MAX_HEALTH, mod("hp", 100.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+100 Max Health • hold for 120 HP total"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeDamageRing() {
        ItemStack s = new ItemStack(Items.REDSTONE, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Damage Ring", ChatFormatting.RED));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 500.0), EquipmentSlotGroup.ANY);
        ab.add(Attributes.ATTACK_SPEED, mod("spd", 100.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+500 Attack Damage + +100 Attack Speed"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeKnockbackGauntlet() {
        ItemStack s = new ItemStack(Items.GOLD_INGOT, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Knockback Gauntlet", ChatFormatting.GOLD));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_KNOCKBACK, mod("kb", 10.0), EquipmentSlotGroup.ANY);
        ab.add(Attributes.KNOCKBACK_RESISTANCE, mod("kbr", 1.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+10 Attack Knockback • send them flying"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeNameTag(String text, ChatFormatting color) {
        ItemStack s = new ItemStack(Items.NAME_TAG, 1);
        s.set(DataComponents.CUSTOM_NAME, name(text, color));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Custom name tag • rename anything"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeAllMusicDiscs() {
        ItemStack s = new ItemStack(Items.MUSIC_DISC_13, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Music Disc: 13", ChatFormatting.WHITE));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Classic disc • first ever Minecraft music disc"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeJukebox64() {
        ItemStack s = new ItemStack(Items.JUKEBOX, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Jukeboxes x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x jukeboxes • play music everywhere"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeLodestoneCompass() {
        ItemStack s = new ItemStack(Items.COMPASS, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Lodestone Compass", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Points to lodestone • never get lost"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeRecoveryCompass() {
        ItemStack s = new ItemStack(Items.RECOVERY_COMPASS, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Recovery Compass", ChatFormatting.AQUA));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Points to your last death • find your stuff"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeBoat64() {
        ItemStack s = new ItemStack(Items.OAK_BOAT, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Oak Boats x64", ChatFormatting.YELLOW));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x boats • travel by water fast"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeChestBoat64() {
        ItemStack s = new ItemStack(Items.OAK_CHEST_BOAT, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Oak Chest Boats x64", ChatFormatting.YELLOW));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x chest boats • mobile storage on water"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeMobEgg64(Item egg, String mobName) {
        ItemStack s = new ItemStack(egg, 64);
        s.set(DataComponents.CUSTOM_NAME, name(mobName + " Egg x64", ChatFormatting.GREEN));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x " + mobName + " spawn eggs"),
            gold("Orbiter Preset"))));
        return s;
    }

    private void registerMisc() {

        reg("ender-pearl-far", "Ender Pearl • +20 Entity Reach", this::makeFarPearl);
        reg("op-snowball", "OP Snowball • 64x with Knockback", this::makeOpSnowball);
        reg("op-egg", "OP Egg • 64x with Extreme Knockback", this::makeOpEgg);
        reg("op-lead", "OP Lead • 64x with Extended Range", this::makeOpLead64);
        reg("saddle-stack", "64x Saddles", this::makeSaddleStack);
        reg("name-tag-anvil", "64x Anvils", this::makeAnvilStack);

        reg("knockback-stick", "Knockback Stick • Knockback 255", this::makeKnockbackStick);
        reg("fire-stick", "Fire Stick • Fire Aspect 255", this::makeFireStick);
        reg("silk-stick", "Silk Touch Stick • Mine anything", this::makeSilkStick);

        reg("banner-white", "White Banner x64", () -> makeBanner(Items.BANNER.pick(DyeColor.WHITE), "White", ChatFormatting.WHITE));
        reg("banner-red", "Red Banner x64", () -> makeBanner(Items.BANNER.pick(DyeColor.RED), "Red", ChatFormatting.RED));
        reg("banner-blue", "Blue Banner x64", () -> makeBanner(Items.BANNER.pick(DyeColor.BLUE), "Blue", ChatFormatting.AQUA));
        reg("banner-purple", "Purple Banner x64", () -> makeBanner(Items.BANNER.pick(DyeColor.PURPLE), "Purple", ChatFormatting.LIGHT_PURPLE));
        reg("banner-black", "Black Banner x64", () -> makeBanner(Items.BANNER.pick(DyeColor.BLACK), "Black", ChatFormatting.DARK_GRAY));

        reg("bed-red", "Red Bed x64", () -> makeBed(Items.BED.pick(DyeColor.RED), "Red", ChatFormatting.RED));
        reg("bed-blue", "Blue Bed x64", () -> makeBed(Items.BED.pick(DyeColor.BLUE), "Blue", ChatFormatting.AQUA));
        reg("bed-white", "White Bed x64", () -> makeBed(Items.BED.pick(DyeColor.WHITE), "White", ChatFormatting.WHITE));

        reg("skeleton-skull", "Skeleton Skull x64", () -> makeSkull64(Items.SKELETON_SKULL, "Skeleton Skull"));
        reg("wither-skull", "Wither Skeleton Skull x64", () -> makeSkull64(Items.WITHER_SKELETON_SKULL, "Wither Skeleton Skull"));
        reg("zombie-head", "Zombie Head x64", () -> makeSkull64(Items.ZOMBIE_HEAD, "Zombie Head"));
        reg("creeper-head", "Creeper Head x64", () -> makeSkull64(Items.CREEPER_HEAD, "Creeper Head"));
        reg("piglin-head", "Piglin Head x64", () -> makeSkull64(Items.PIGLIN_HEAD, "Piglin Head"));

        reg("book-commands", "Command Reference Book", this::makeCommandBook);
        reg("book-coords", "Coordinates Book", this::makeCoordsBook);
        reg("book-enchants", "Enchantment Guide Book", this::makeEnchantGuideBook);

        reg("shulker-white", "White Shulker AABB", () -> makeColoredShulker(Items.DYED_SHULKER_BOX.pick(DyeColor.WHITE), "White", ChatFormatting.WHITE));
        reg("shulker-red", "Red Shulker AABB", () -> makeColoredShulker(Items.DYED_SHULKER_BOX.pick(DyeColor.RED), "Red", ChatFormatting.RED));
        reg("shulker-blue", "Blue Shulker AABB", () -> makeColoredShulker(Items.DYED_SHULKER_BOX.pick(DyeColor.BLUE), "Blue", ChatFormatting.AQUA));
        reg("shulker-purple", "Purple Shulker AABB", () -> makeColoredShulker(Items.DYED_SHULKER_BOX.pick(DyeColor.PURPLE), "Purple", ChatFormatting.LIGHT_PURPLE));
        reg("shulker-gold", "Gold Shulker AABB", () -> makeColoredShulker(Items.DYED_SHULKER_BOX.pick(DyeColor.YELLOW), "Gold", ChatFormatting.GOLD));
    }

    private ItemStack makeFarPearl() {
        ItemStack s = new ItemStack(Items.ENDER_PEARL, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Far Pearl x64", ChatFormatting.DARK_PURPLE));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ENTITY_INTERACTION_RANGE, mod("range", 20.0), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+20 Entity Range • throw pearls farther"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpSnowball() {
        ItemStack s = new ItemStack(Items.SNOWBALL, 64);
        s.set(DataComponents.CUSTOM_NAME, name("OP Snowball x64", ChatFormatting.WHITE));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_KNOCKBACK, mod("kb", 255.0), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x snowballs with extreme knockback • PvP trolling"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpEgg() {
        ItemStack s = new ItemStack(Items.EGG, 64);
        s.set(DataComponents.CUSTOM_NAME, name("OP Egg x64", ChatFormatting.YELLOW));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_KNOCKBACK, mod("kb", 255.0), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x eggs with extreme knockback • launch mobs"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeOpLead64() {
        ItemStack s = new ItemStack(Items.LEAD, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Extended Leads x64", ChatFormatting.YELLOW));
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.BLOCK_INTERACTION_RANGE, mod("range", 50.0), EquipmentSlotGroup.ANY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+50 Block Range • leash anything from far away"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSaddleStack() {
        ItemStack s = new ItemStack(Items.SADDLE, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Saddles x64", ChatFormatting.GOLD));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x saddles • ride everything"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeAnvilStack() {
        ItemStack s = new ItemStack(Items.ANVIL, 64);
        s.set(DataComponents.CUSTOM_NAME, name("Anvils x64", ChatFormatting.GRAY));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x anvils • rename and enchant anything"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeKnockbackStick() {
        ItemStack s = new ItemStack(Items.STICK, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Knockback Stick", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "knockback", 255);
        addEnchant(eb, "unbreaking", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ATTACK_KNOCKBACK, mod("kb", 100.0), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Knockback 255 + 100 Knockback attribute • send them to the void"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeFireStick() {
        ItemStack s = new ItemStack(Items.STICK, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Fire Stick", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "fire_aspect", 255);
        addEnchant(eb, "unbreaking", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Fire Aspect 255 • set anything on fire"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSilkStick() {
        ItemStack s = new ItemStack(Items.STICK, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Silk Touch Stick", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "silk_touch", 1);
        addEnchant(eb, "efficiency", 255);
        addEnchant(eb, "unbreaking", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Silk Touch + Efficiency 255 • mine anything with a stick"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeBanner(Item bannerItem, String colorName, ChatFormatting color) {
        ItemStack s = new ItemStack(bannerItem, 64);
        s.set(DataComponents.CUSTOM_NAME, name(colorName + " Banner x64", color));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x " + colorName.toLowerCase() + " banners for decoration"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeBed(Item bedItem, String colorName, ChatFormatting color) {
        ItemStack s = new ItemStack(bedItem, 64);
        s.set(DataComponents.CUSTOM_NAME, name(colorName + " Bed x64", color));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x " + colorName.toLowerCase() + " beds • set spawn anywhere"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeSkull64(Item skullItem, String skullName) {
        ItemStack s = new ItemStack(skullItem, 64);
        s.set(DataComponents.CUSTOM_NAME, name(skullName + " x64", ChatFormatting.GRAY));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("64x " + skullName.toLowerCase() + " for decoration"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeColoredShulker(Item shulkerItem, String colorName, ChatFormatting color) {
        ItemStack s = new ItemStack(shulkerItem, 1);
        s.set(DataComponents.CUSTOM_NAME, name(colorName + " Shulker AABB", color));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line(colorName + " shulker box • portable storage"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeCommandBook() {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Command Reference", ChatFormatting.AQUA));
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.literal("Orbiter Commands\n\n")
            .append(Component.literal("/gpi").setStyle(Style.EMPTY.withBold(true).withColor(ChatFormatting.AQUA)))
            .append(Component.literal(" • Give Preset Items\n"))
            .append(Component.literal("/gpi list").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
            .append(Component.literal(" • List all presets\n"))
            .append(Component.literal("/gpi all").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)))
            .append(Component.literal(" • Give ALL presets"))));
        pages.add(Filterable.passThrough(Component.literal("Useful Presets:\n")
            .append(Component.literal("/gpi kit-god").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
            .append(Component.literal(" • Ultimate kit\n"))
            .append(Component.literal("/gpi flight127-rocket").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
            .append(Component.literal(" • Max flight\n"))
            .append(Component.literal("/gpi god-elytra").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
            .append(Component.literal(" • OP elytra\n"))));
        WrittenBookContent content = new WrittenBookContent(
            Filterable.passThrough("Orbiter Commands"), "Orbiter", 0, pages, true);
        s.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return s;
    }

    private ItemStack makeCoordsBook() {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Coordinates Book", ChatFormatting.GREEN));
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.literal("Coordinates Reference\n\n")
            .append(Component.literal("Overworld Origin: ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            .append(Component.literal("0 0 0\n").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
            .append(Component.literal("Nether Hub: ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            .append(Component.literal("0 0 0\n").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
            .append(Component.literal("End Portal: ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            .append(Component.literal("find with /locate"))));
        WrittenBookContent content = new WrittenBookContent(
            Filterable.passThrough("Coordinates"), "Orbiter", 0, pages, true);
        s.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return s;
    }

    private ItemStack makeEnchantGuideBook() {
        ItemStack s = new ItemStack(Items.WRITTEN_BOOK, 1);
        s.set(DataComponents.CUSTOM_NAME, name("Enchantment Guide", ChatFormatting.LIGHT_PURPLE));
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(Filterable.passThrough(Component.literal("Max Enchantments Guide\n\n")
            .append(Component.literal("Sword: ").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)))
            .append(Component.literal("Sharp 255, Fire Aspect 255, Looting 255\n"))
            .append(Component.literal("Pickaxe: ").setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
            .append(Component.literal("Efficiency 255, Fortune 255, Silk Touch\n"))
            .append(Component.literal("Armor: ").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)))
            .append(Component.literal("All Protection 255, Thorns 255\n"))
            .append(Component.literal("Bow: ").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)))
            .append(Component.literal("Power 255, Flame 255, Infinity"))));
        WrittenBookContent content = new WrittenBookContent(
            Filterable.passThrough("Enchant Guide"), "Orbiter", 0, pages, true);
        s.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return s;
    }

    private void showList() {
        info("Total presets: " + allPresets.size());
        info("Type /gpi <name> to get an item. Use /gpi list to see all.");
        info("Use /gpi all to get every preset. Use /gpi head <player> for player heads.");

        int shown = Math.min(30, allPresets.size());
        StringBuilder sb = new StringBuilder("Preview: ");
        for (int i = 0; i < shown; i++) {
            sb.append(allPresets.get(i).name());
            if (i < shown - 1) sb.append(", ");
        }
        if (allPresets.size() > 30) sb.append("... (+" + (allPresets.size() - 30) + " more)");
        info(sb.toString());
    }

    private void runPreset(String presetName, ItemStack item) throws CommandSyntaxException {
        ensureCreative();
        giveItem(item);
        info("Gave: " + presetName);
    }

    private void givePlayerHead(String player) throws CommandSyntaxException {
        ensureCreative();
        String clean = player == null ? "" : player.trim();
        if (!PLAYER_NAME_PATTERN.matcher(clean).matches()) {
            error("Invalid player name. Use 3-16 chars: letters, numbers or underscore.");
            return;
        }
        ItemStack head = new ItemStack(Items.PLAYER_HEAD, 1);
        head.set(DataComponents.CUSTOM_NAME, name("Head: " + clean, ChatFormatting.YELLOW));
        head.set(DataComponents.PROFILE, resolvePlayerProfile(clean));
        giveItem(head);
        info("Gave head: " + clean);
    }

    private void ensureCreative() throws CommandSyntaxException {
        if (mc.player == null || mc.getConnection() == null || !mc.player.getAbilities().instabuild) {
            throw NOT_IN_CREATIVE.create();
        }
    }

    private void giveItem(ItemStack item) {
        giveItem(item, mc.player.getInventory().getSelectedSlot());
    }

    private void giveItem(ItemStack item, int hotbarSlot) {
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + hotbarSlot, item));
        mc.player.containerMenu.getSlot(36 + hotbarSlot).set(item);
    }

    private static Component name(String value, ChatFormatting color) {
        return Component.literal(value).setStyle(Style.EMPTY.withColor(color).withBold(true));
    }

    private static Component line(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY));
    }

    private static Component gold(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
    }

    private static AttributeModifier mod(String id, double value) {
        return new AttributeModifier(Identifier.fromNamespaceAndPath("orbiter", id), value, AttributeModifier.Operation.ADD_VALUE);
    }

    private void addEnchant(ItemEnchantments.Mutable builder, String enchantId, int level) {
        if (mc.level == null) return;
        String cleanId = enchantId.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (!cleanId.contains(":")) cleanId = "minecraft:" + cleanId;
        String[] parts = cleanId.split(":", 2);
        if (parts.length != 2) return;
        Identifier id = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
        var registry = mc.level.registryAccess().getOrThrow(Registries.ENCHANTMENT);
        registry.value().get(key.identifier()).ifPresent(reference -> builder.set(reference, level));
    }

    private ResolvableProfile resolvePlayerProfile(String playerName) {
        if (mc.getConnection() != null) {
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                GameProfile profile = entry.getProfile();
                if (profile != null && profile.name() != null && profile.name().equalsIgnoreCase(playerName)) {
                    return ResolvableProfile.createResolved(profile);
                }
            }
        }
        return ResolvableProfile.createUnresolved(playerName);
    }

    private ItemStack makeStackItem(Item item, int count, String displayName, ChatFormatting color) {
        ItemStack s = new ItemStack(item, count);
        s.set(DataComponents.CUSTOM_NAME, name(displayName, color));
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Stack of " + count + " • available only via creative write"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeReachTrident() {
        ItemStack s = new ItemStack(Items.TRIDENT, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Reach Trident", ChatFormatting.DARK_AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "impaling", 255);
        addEnchant(eb, "loyalty", 255);
        addEnchant(eb, "channeling", 1);
        addEnchant(eb, "unbreaking", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ENTITY_INTERACTION_RANGE, mod("range", 50.0), EquipmentSlotGroup.MAINHAND);
        ab.add(Attributes.ATTACK_DAMAGE, mod("dmg", 10000), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+50 Entity Range • hit anything from 55 blocks away"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeReachFishingRod() {
        ItemStack s = new ItemStack(Items.FISHING_ROD, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Reach Fishing Rod", ChatFormatting.AQUA));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "luck", 255);
        addEnchant(eb, "lure", 255);
        addEnchant(eb, "unbreaking", 255);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        ItemAttributeModifiers.Builder ab = ItemAttributeModifiers.builder();
        ab.add(Attributes.ENTITY_INTERACTION_RANGE, mod("range", 30.0), EquipmentSlotGroup.MAINHAND);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ab.build());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("+30 Entity Range • fish from 35 blocks away"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeFireCrossbow() {
        ItemStack s = new ItemStack(Items.CROSSBOW, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Fire Crossbow", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "multishot", 1);
        addEnchant(eb, "quick_charge", 255);
        addEnchant(eb, "piercing", 10);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Multishot + Quick Charge 255 + Piercing 10"),
            gold("Orbiter Preset"))));
        return s;
    }

    private ItemStack makeFireBow() {
        ItemStack s = new ItemStack(Items.BOW, 1);
        s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        s.set(DataComponents.CUSTOM_NAME, name("Fire Bow", ChatFormatting.RED));
        ItemEnchantments.Mutable eb = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        addEnchant(eb, "power", 255);
        addEnchant(eb, "flame", 255);
        addEnchant(eb, "punch", 255);
        addEnchant(eb, "unbreaking", 255);
        addEnchant(eb, "mending", 1);
        s.set(DataComponents.ENCHANTMENTS, eb.toImmutable());
        s.set(DataComponents.LORE, new ItemLore(List.of(
            line("Power 255 + Flame 255 + Punch 255 • fire everywhere"),
            gold("Orbiter Preset"))));
        return s;
    }
}
