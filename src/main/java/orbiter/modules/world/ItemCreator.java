package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ItemCreator extends CreativeSafetyModule {
        private final SettingGroup sgGeneral = settings.getDefaultGroup();
        private final SettingGroup sgPresets = settings.createGroup("Presets");
        private final SettingGroup sgName = settings.createGroup("Custom Name");
        private final SettingGroup sgLore = settings.createGroup("Lore");
        private final SettingGroup sgEnchants = settings.createGroup("Enchantments");
        private final SettingGroup sgAttributes = settings.createGroup("Attributes");
        private final SettingGroup sgFlags = settings.createGroup("Item Flags");
        private final SettingGroup sgEntity = settings.createGroup("Entity / Spawn Egg");

        private final Setting<List<Item>> itemSelection = sgGeneral.add(new ItemListSetting.Builder()
                        .name("item")
                        .description("Item to create. Uses the first selected item.")
                        .defaultValue(Items.DIAMOND_SWORD)
                        .build());

        private final Setting<Integer> count = sgGeneral.add(new IntSetting.Builder()
                        .name("count")
                        .description("Stack size.")
                        .defaultValue(1)
                        .min(1)
                        .sliderRange(1, 64)
                        .build());

        private final Setting<Integer> giveSlot = sgGeneral.add(new IntSetting.Builder()
                        .name("give-slot")
                        .description("Hotbar slot to place the item (0-8).")
                        .defaultValue(0)
                        .min(0)
                        .max(8)
                        .sliderRange(0, 8)
                        .build());

        private final Setting<Boolean> giveOnActivate = sgGeneral.add(new BoolSetting.Builder()
                        .name("give-on-activate")
                        .description("Give the item immediately when enabled, then auto-disable.")
                        .defaultValue(true)
                        .build());

        private final Setting<Boolean> dropItem = sgGeneral.add(new BoolSetting.Builder()
                        .name("drop-item")
                        .description("Drop the item to ground instead of putting it in inventory.")
                        .defaultValue(false)
                        .build());

        private final Setting<PresetType> presetAction = sgPresets.add(new EnumSetting.Builder<PresetType>()
                        .name("preset")
                        .description("Load a built-in item preset instead of using custom settings.")
                        .defaultValue(PresetType.None)
                        .build());

        private final Setting<Integer> overrideStackSize = sgGeneral.add(new IntSetting.Builder()
                        .name("override-stack-size")
                        .description("Force stack size beyond normal limits (0 = respect vanilla max).")
                        .defaultValue(0)
                        .min(0)
                        .sliderRange(0, 127)
                        .build());

        private final Setting<Boolean> enableName = sgName.add(new BoolSetting.Builder()
                        .name("custom-name")
                        .description("Give the item a custom name.")
                        .defaultValue(false)
                        .build());

        private final Setting<String> customName = sgName.add(new StringSetting.Builder()
                        .name("name-text")
                        .description("The custom name text. Supports & color codes (e.g. &6Gold).")
                        .defaultValue("Custom Item")
                        .visible(enableName::get)
                        .build());

        private final Setting<NameColor> nameColor = sgName.add(new EnumSetting.Builder<NameColor>()
                        .name("name-color")
                        .description("Color of the custom name.")
                        .defaultValue(NameColor.Gold)
                        .visible(enableName::get)
                        .build());

        private final Setting<Boolean> nameBold = sgName.add(new BoolSetting.Builder()
                        .name("name-bold").defaultValue(false).visible(enableName::get).build());
        private final Setting<Boolean> nameItalic = sgName.add(new BoolSetting.Builder()
                        .name("name-italic").defaultValue(false).visible(enableName::get).build());
        private final Setting<Boolean> nameObfuscated = sgName.add(new BoolSetting.Builder()
                        .name("name-obfuscated").defaultValue(false).visible(enableName::get).build());
        private final Setting<Boolean> nameStrikethrough = sgName.add(new BoolSetting.Builder()
                        .name("name-strikethrough").defaultValue(false).visible(enableName::get).build());
        private final Setting<Boolean> nameUnderline = sgName.add(new BoolSetting.Builder()
                        .name("name-underline").defaultValue(false).visible(enableName::get).build());

        private final Setting<Boolean> enableLore = sgLore.add(new BoolSetting.Builder()
                        .name("custom-lore").defaultValue(false).build());

        private final Setting<String> lore1 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-1").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore2 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-2").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore3 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-3").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore4 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-4").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore5 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-5").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore6 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-6").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore7 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-7").defaultValue("").visible(enableLore::get).build());
        private final Setting<String> lore8 = sgLore.add(new StringSetting.Builder()
                        .name("lore-line-8").defaultValue("").visible(enableLore::get).build());

        private final Setting<NameColor> loreColor = sgLore.add(new EnumSetting.Builder<NameColor>()
                        .name("lore-color").defaultValue(NameColor.Gray).visible(enableLore::get).build());

        private final Setting<Boolean> loreBold = sgLore.add(new BoolSetting.Builder()
                        .name("lore-bold").defaultValue(false).visible(enableLore::get).build());

        private final Setting<Boolean> enableEnchants = sgEnchants.add(new BoolSetting.Builder()
                        .name("enchantments").defaultValue(false).build());

        private final Setting<Boolean> allEnchantments = sgEnchants.add(new BoolSetting.Builder()
                        .name("all-enchantments")
                        .description("Apply every enchantment in the game at the specified level.")
                        .defaultValue(false)
                        .visible(enableEnchants::get)
                        .build());

        private final Setting<Integer> allEnchantLevel = sgEnchants.add(new IntSetting.Builder()
                        .name("all-enchant-level")
                        .description("Level for all enchantments.")
                        .defaultValue(255)
                        .min(1)
                        .sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && allEnchantments.get())
                        .build());

        private final Setting<String> enchant1 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-1").description("Enchantment ID (e.g. sharpness).").defaultValue("sharpness")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant1Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-1-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant2 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-2").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant2Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-2-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant3 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-3").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant3Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-3-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant4 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-4").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant4Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-4-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant5 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-5").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant5Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-5-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant6 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-6").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant6Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-6-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant7 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-7").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant7Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-7-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant8 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-8").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant8Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-8-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant9 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-9").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant9Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-9-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<String> enchant10 = sgEnchants.add(new StringSetting.Builder()
                        .name("enchant-10").defaultValue("")
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());
        private final Setting<Integer> enchant10Level = sgEnchants.add(new IntSetting.Builder()
                        .name("enchant-10-level").defaultValue(5).min(1).sliderRange(1, 255)
                        .visible(() -> enableEnchants.get() && !allEnchantments.get()).build());

        private final Setting<Boolean> enchantGlint = sgEnchants.add(new BoolSetting.Builder()
                        .name("force-glint").description("Force enchantment glint even without enchantments.")
                        .defaultValue(false).build());

        private final Setting<Boolean> enableAttributes = sgAttributes.add(new BoolSetting.Builder()
                        .name("attributes").defaultValue(false).build());

        private final Setting<Boolean> allAttributes = sgAttributes.add(new BoolSetting.Builder()
                        .name("all-attributes")
                        .description("Apply every attribute type at the specified value.")
                        .defaultValue(false)
                        .visible(enableAttributes::get)
                        .build());

        private final Setting<Double> allAttrValue = sgAttributes.add(new DoubleSetting.Builder()
                        .name("all-attr-value")
                        .description("Value for all attribute modifiers.")
                        .defaultValue(100.0)
                        .min(-1024.0)
                        .sliderRange(-100.0, 1000.0)
                        .visible(() -> enableAttributes.get() && allAttributes.get())
                        .build());

        private final Setting<AttributeOp> attributeOperation = sgAttributes.add(new EnumSetting.Builder<AttributeOp>()
                        .name("attribute-operation")
                        .description("How attribute values are applied.")
                        .defaultValue(AttributeOp.Add)
                        .visible(enableAttributes::get)
                        .build());

        private final Setting<AttributeType> attribute1Type = sgAttributes.add(new EnumSetting.Builder<AttributeType>()
                        .name("attribute-1").defaultValue(AttributeType.AttackDamage)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()).build());
        private final Setting<Double> attribute1Value = sgAttributes.add(new DoubleSetting.Builder()
                        .name("attribute-1-value").defaultValue(100.0).min(-1024.0).sliderRange(-100.0, 1000.0)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()).build());

        private final Setting<AttributeType> attribute2Type = sgAttributes.add(new EnumSetting.Builder<AttributeType>()
                        .name("attribute-2").defaultValue(AttributeType.None)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()).build());
        private final Setting<Double> attribute2Value = sgAttributes.add(new DoubleSetting.Builder()
                        .name("attribute-2-value").defaultValue(0.0).min(-1024.0).sliderRange(-100.0, 1000.0)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()
                                        && attribute2Type.get() != AttributeType.None)
                        .build());

        private final Setting<AttributeType> attribute3Type = sgAttributes.add(new EnumSetting.Builder<AttributeType>()
                        .name("attribute-3").defaultValue(AttributeType.None)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()).build());
        private final Setting<Double> attribute3Value = sgAttributes.add(new DoubleSetting.Builder()
                        .name("attribute-3-value").defaultValue(0.0).min(-1024.0).sliderRange(-100.0, 1000.0)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()
                                        && attribute3Type.get() != AttributeType.None)
                        .build());

        private final Setting<AttributeType> attribute4Type = sgAttributes.add(new EnumSetting.Builder<AttributeType>()
                        .name("attribute-4").defaultValue(AttributeType.None)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()).build());
        private final Setting<Double> attribute4Value = sgAttributes.add(new DoubleSetting.Builder()
                        .name("attribute-4-value").defaultValue(0.0).min(-1024.0).sliderRange(-100.0, 1000.0)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()
                                        && attribute4Type.get() != AttributeType.None)
                        .build());

        private final Setting<AttributeType> attribute5Type = sgAttributes.add(new EnumSetting.Builder<AttributeType>()
                        .name("attribute-5").defaultValue(AttributeType.None)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()).build());
        private final Setting<Double> attribute5Value = sgAttributes.add(new DoubleSetting.Builder()
                        .name("attribute-5-value").defaultValue(0.0).min(-1024.0).sliderRange(-100.0, 1000.0)
                        .visible(() -> enableAttributes.get() && !allAttributes.get()
                                        && attribute5Type.get() != AttributeType.None)
                        .build());

        private final Setting<Boolean> unbreakable = sgFlags.add(new BoolSetting.Builder()
                        .name("unbreakable").description("Make the item unbreakable.").defaultValue(false).build());

        private final Setting<Integer> maxDamage = sgFlags.add(new IntSetting.Builder()
                        .name("custom-max-durability").description("Override max durability (0 = default).")
                        .defaultValue(0).min(0).sliderRange(0, 10000).build());

        private final Setting<Integer> customModelData = sgFlags.add(new IntSetting.Builder()
                        .name("custom-model-data").description("Custom model data value (0 = none).")
                        .defaultValue(0).min(0).sliderRange(0, 999999).build());

        private final Setting<Integer> maxStackOverride = sgFlags.add(new IntSetting.Builder()
                        .name("max-stack-size").description("Override the max stack size (0 = default).")
                        .defaultValue(0).min(0).sliderRange(0, 99).build());

        private final Setting<Integer> repairCost = sgFlags.add(new IntSetting.Builder()
                        .name("repair-cost").description("Repair cost in anvil (0 = default).")
                        .defaultValue(0).min(0).sliderRange(0, 9999).build());

        private final Setting<Boolean> enableEntity = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-settings").description("Configure spawn egg / entity data.").defaultValue(false)
                        .build());

        private final Setting<String> entityCustomName = sgEntity.add(new StringSetting.Builder()
                        .name("entity-name").defaultValue("").visible(enableEntity::get).build());

        private final Setting<NameColor> entityNameColor = sgEntity.add(new EnumSetting.Builder<NameColor>()
                        .name("entity-name-color").defaultValue(NameColor.Red).visible(enableEntity::get).build());

        private final Setting<Boolean> entityNameVisible = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-name-visible").defaultValue(true).visible(enableEntity::get).build());

        private final Setting<Integer> entityHealth = sgEntity.add(new IntSetting.Builder()
                        .name("entity-health").description("Health points (0 = default).").defaultValue(0)
                        .min(0).sliderRange(0, 1000).visible(enableEntity::get).build());

        private final Setting<Boolean> entityInvulnerable = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-invulnerable").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Boolean> entityNoAI = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-no-ai").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Boolean> entitySilent = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-silent").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Boolean> entityNoGravity = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-no-gravity").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Boolean> entityGlowing = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-glowing").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Boolean> entityPersistent = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-persistent").description("Prevent despawning.").defaultValue(false)
                        .visible(enableEntity::get).build());

        private final Setting<Boolean> entityVisualFire = sgEntity.add(new BoolSetting.Builder()
                        .name("entity-visual-fire").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Boolean> entityCharged = sgEntity.add(new BoolSetting.Builder()
                        .name("creeper-charged").defaultValue(false).visible(enableEntity::get).build());

        private final Setting<Integer> entityFuse = sgEntity.add(new IntSetting.Builder()
                        .name("creeper-fuse").description("Fuse ticks (0 = default).").defaultValue(0)
                        .min(0).sliderRange(0, 200).visible(enableEntity::get).build());

        private final Setting<Integer> entityExplosionPower = sgEntity.add(new IntSetting.Builder()
                        .name("explosion-power").description("Explosion radius (0 = default).").defaultValue(0)
                        .min(0).sliderRange(0, 127).visible(enableEntity::get).build());

        private final Setting<Integer> entitySlimeSize = sgEntity.add(new IntSetting.Builder()
                        .name("slime-size").description("Slime/Magma Cube size (0 = default).").defaultValue(0)
                        .min(0).sliderRange(0, 127).visible(enableEntity::get).build());

        public ItemCreator() {
                super("item-creator",
                                "Create custom items with names, enchants, attributes, and entity NBT. Creative only.");
        }

        @Override
        public void onActivate() {
                if (mc.player == null) {
                        toggle();
                        return;
                }

                if (!mc.player.getAbilities().creativeMode) {
                        warning("You must be in Creative mode!");
                        toggle();
                        return;
                }

                if (giveOnActivate.get()) {
                        try {

                                if (presetAction.get() != PresetType.None) {
                                        applyPreset(presetAction.get());
                                        toggle();
                                        return;
                                }

                                ItemStack stack = createItem();
                                if (stack.isEmpty()) {
                                        error("Failed to create item. Check your settings.");
                                        toggle();
                                        return;
                                }

                                giveItem(stack);
                                info("Created item: " + stack.getName().getString());
                        } catch (Exception e) {
                                error("Error creating item: " + e.getMessage());
                        }

                        toggle();
                }
        }

        private void applyPreset(PresetType preset) {
                if (mc.player == null || mc.player.networkHandler == null) return;
                switch (preset) {
                        case GodSword -> {
                                mc.player.networkHandler.sendChatCommand(
                                                "give @s netherite_sword[" +
                                                                "custom_name='{\"text\":\"God Sword\",\"color\":\"gold\",\"bold\":true}',"
                                                                +
                                                                "enchantments={levels:{sharpness:255,knockback:255,fire_aspect:255,looting:255,sweeping_edge:255,unbreaking:255,mending:1}},"
                                                                +
                                                                "unbreakable={}," +
                                                                "attribute_modifiers=[" +
                                                                "{type:'attack_damage',id:'orbiter:god',amount:1000,operation:'add_value',slot:'mainhand'},"
                                                                +
                                                                "{type:'attack_speed',id:'orbiter:spd',amount:1000,operation:'add_value',slot:'mainhand'}"
                                                                +
                                                                "]" +
                                                                "]");
                                info("Loaded preset: God Sword");
                        }
                        case GodAxe -> {
                                mc.player.networkHandler.sendChatCommand(
                                                "give @s netherite_axe[" +
                                                                "custom_name='{\"text\":\"God Axe\",\"color\":\"red\",\"bold\":true}',"
                                                                +
                                                                "enchantments={levels:{sharpness:255,efficiency:255,unbreaking:255,mending:1,silk_touch:1}},"
                                                                +
                                                                "unbreakable={}," +
                                                                "attribute_modifiers=[" +
                                                                "{type:'attack_damage',id:'orbiter:god',amount:1000,operation:'add_value',slot:'mainhand'}"
                                                                +
                                                                "]" +
                                                                "]");
                                info("Loaded preset: God Axe");
                        }
                        case GodBow -> {
                                mc.player.networkHandler.sendChatCommand(
                                                "give @s bow[" +
                                                                "custom_name='{\"text\":\"God Bow\",\"color\":\"aqua\",\"bold\":true}',"
                                                                +
                                                                "enchantments={levels:{power:255,punch:255,flame:1,infinity:1,unbreaking:255}},"
                                                                +
                                                                "unbreakable={}" +
                                                                "]");
                                info("Loaded preset: God Bow");
                        }
                        case GodArmor -> {
                                String[] pieces = { "helmet", "chestplate", "leggings", "boots" };
                                for (String piece : pieces) {
                                        mc.player.networkHandler.sendChatCommand(
                                                        "give @s netherite_" + piece + "[" +
                                                                        "custom_name='{\"text\":\"God "
                                                                        + piece.substring(0, 1).toUpperCase()
                                                                        + piece.substring(1)
                                                                        + "\",\"color\":\"gold\",\"bold\":true}'," +
                                                                        "enchantments={levels:{protection:255,unbreaking:255,mending:1,thorns:255,fire_protection:255,blast_protection:255,projectile_protection:255}},"
                                                                        +
                                                                        "unbreakable={}," +
                                                                        "attribute_modifiers=[" +
                                                                        "{type:'armor',id:'orbiter:arm',amount:1000,operation:'add_value'},"
                                                                        +
                                                                        "{type:'armor_toughness',id:'orbiter:tough',amount:1000,operation:'add_value'},"
                                                                        +
                                                                        "{type:'knockback_resistance',id:'orbiter:kb',amount:1.0,operation:'add_value'}"
                                                                        +
                                                                        "]" +
                                                                        "]");
                                }
                                info("Loaded preset: God Armor set (4 pieces)");
                        }
                        case CrashBook -> {
                                StringBuilder pages = new StringBuilder();
                                for (int i = 0; i < 100; i++) {
                                        if (i > 0)
                                                pages.append(",");
                                        pages.append("'{\"text\":\"" + "\u00A7k\u2588".repeat(200) + "\"}'");
                                }
                                mc.player.networkHandler.sendChatCommand(
                                                "give @s written_book[written_book_content={title:\"Crash\",author:\"Orbiter\",pages:["
                                                                + pages + "]}]");
                                info("Loaded preset: Crash Book");
                        }
                        case BanItem -> {
                                mc.player.networkHandler.sendChatCommand(
                                                "give @s shulker_box[custom_name='{\"text\":\"Ban Box\",\"color\":\"dark_red\",\"bold\":true}',"
                                                                +
                                                                "lore=['\"Opens = crash\"']," +
                                                                "container=[" +
                                                                "{slot:0,item:{id:'written_book',count:1,components:{written_book_content:{title:'x',author:'o',pages:['\""
                                                                + "\u00A7k".repeat(200) + "\"']}}}}" +
                                                                "]" +
                                                                "]");
                                info("Loaded preset: Ban Item (crash shulker)");
                        }
                        default -> {
                        }
                }
        }

        private void giveItem(ItemStack stack) {
                if (mc.player == null || mc.player.networkHandler == null) return;
                if (dropItem.get()) {
                        mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(-1, stack));
                } else {
                        int slot = 36 + giveSlot.get();
                        mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, stack));
                        mc.player.getInventory().setStack(giveSlot.get(), stack);
                }
        }

        private ItemStack createItem() {
                List<Item> items = itemSelection.get();
                if (items == null || items.isEmpty())
                        return ItemStack.EMPTY;

                Item item = items.get(0);
                int stackSize;
                if (overrideStackSize.get() > 0) {
                        stackSize = overrideStackSize.get();
                } else {
                        stackSize = Math.min(count.get(), Math.max(1, item.getMaxCount()));
                }
                ItemStack stack = new ItemStack(item, stackSize);

                applyCustomName(stack);

                applyLore(stack);

                applyEnchantments(stack);

                if (enchantGlint.get()) {
                        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                }

                applyAttributes(stack);

                applyFlags(stack);

                if (enableEntity.get()) {
                        applyEntityData(stack);
                }

                return stack;
        }

        private void applyCustomName(ItemStack stack) {
                if (!enableName.get() || customName.get().isEmpty())
                        return;

                String nameStr = customName.get()
                                .replace("&0", "\u00A70").replace("&1", "\u00A71").replace("&2", "\u00A72")
                                .replace("&3", "\u00A73").replace("&4", "\u00A74").replace("&5", "\u00A75")
                                .replace("&6", "\u00A76").replace("&7", "\u00A77").replace("&8", "\u00A78")
                                .replace("&9", "\u00A79").replace("&a", "\u00A7a").replace("&b", "\u00A7b")
                                .replace("&c", "\u00A7c").replace("&d", "\u00A7d").replace("&e", "\u00A7e")
                                .replace("&f", "\u00A7f").replace("&k", "\u00A7k").replace("&l", "\u00A7l")
                                .replace("&m", "\u00A7m").replace("&n", "\u00A7n").replace("&o", "\u00A7o")
                                .replace("&r", "\u00A7r");

                MutableText nameText = Text.literal(nameStr);
                Style style = Style.EMPTY
                                .withColor(getFormatting(nameColor.get()))
                                .withBold(nameBold.get())
                                .withItalic(nameItalic.get())
                                .withObfuscated(nameObfuscated.get())
                                .withStrikethrough(nameStrikethrough.get())
                                .withUnderline(nameUnderline.get());
                nameText.setStyle(style);
                stack.set(DataComponentTypes.CUSTOM_NAME, nameText);
        }

        private void applyLore(ItemStack stack) {
                if (!enableLore.get())
                        return;

                List<Text> loreLines = new ArrayList<>();
                addLoreLine(loreLines, lore1.get());
                addLoreLine(loreLines, lore2.get());
                addLoreLine(loreLines, lore3.get());
                addLoreLine(loreLines, lore4.get());
                addLoreLine(loreLines, lore5.get());
                addLoreLine(loreLines, lore6.get());
                addLoreLine(loreLines, lore7.get());
                addLoreLine(loreLines, lore8.get());

                if (!loreLines.isEmpty()) {
                        stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
                }
        }

        private void applyEnchantments(ItemStack stack) {
                if (!enableEnchants.get() || mc.world == null)
                        return;

                ItemEnchantmentsComponent.Builder enchBuilder = new ItemEnchantmentsComponent.Builder(
                                ItemEnchantmentsComponent.DEFAULT);

                if (allEnchantments.get()) {
                        var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                        registry.streamEntries().forEach(ref -> enchBuilder.add(ref, allEnchantLevel.get()));
                } else {
                        addEnchantment(enchBuilder, enchant1.get(), enchant1Level.get());
                        addEnchantment(enchBuilder, enchant2.get(), enchant2Level.get());
                        addEnchantment(enchBuilder, enchant3.get(), enchant3Level.get());
                        addEnchantment(enchBuilder, enchant4.get(), enchant4Level.get());
                        addEnchantment(enchBuilder, enchant5.get(), enchant5Level.get());
                        addEnchantment(enchBuilder, enchant6.get(), enchant6Level.get());
                        addEnchantment(enchBuilder, enchant7.get(), enchant7Level.get());
                        addEnchantment(enchBuilder, enchant8.get(), enchant8Level.get());
                        addEnchantment(enchBuilder, enchant9.get(), enchant9Level.get());
                        addEnchantment(enchBuilder, enchant10.get(), enchant10Level.get());
                }

                stack.set(DataComponentTypes.ENCHANTMENTS, enchBuilder.build());
        }

        private void applyAttributes(ItemStack stack) {
                if (!enableAttributes.get())
                        return;

                AttributeModifiersComponent.Builder attrBuilder = AttributeModifiersComponent.builder();

                if (allAttributes.get()) {
                        int idx = 0;
                        for (AttributeType type : AttributeType.values()) {
                                if (type == AttributeType.None)
                                        continue;
                                addAttribute(attrBuilder, type, allAttrValue.get(), "orbiter_all_" + idx);
                                idx++;
                        }
                } else {
                        addAttribute(attrBuilder, attribute1Type.get(), attribute1Value.get(), "orbiter_attr_1");
                        addAttribute(attrBuilder, attribute2Type.get(), attribute2Value.get(), "orbiter_attr_2");
                        addAttribute(attrBuilder, attribute3Type.get(), attribute3Value.get(), "orbiter_attr_3");
                        addAttribute(attrBuilder, attribute4Type.get(), attribute4Value.get(), "orbiter_attr_4");
                        addAttribute(attrBuilder, attribute5Type.get(), attribute5Value.get(), "orbiter_attr_5");
                }

                stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrBuilder.build());
        }

        private void applyFlags(ItemStack stack) {
                if (unbreakable.get()) {
                        stack.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
                }

                if (maxDamage.get() > 0) {
                        stack.set(DataComponentTypes.MAX_DAMAGE, maxDamage.get());
                }

                if (customModelData.get() > 0) {
                        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                                        List.of((float) customModelData.get()), List.of(), List.of(), List.of()));
                }

                if (maxStackOverride.get() > 0) {
                        stack.set(DataComponentTypes.MAX_STACK_SIZE, maxStackOverride.get());
                }

                if (repairCost.get() > 0) {
                        stack.set(DataComponentTypes.REPAIR_COST, repairCost.get());
                }
        }

        private void addLoreLine(List<Text> loreLines, String text) {
                if (text != null && !text.isEmpty()) {
                        MutableText loreText = Text.literal(text);
                        Style style = Style.EMPTY
                                        .withColor(getFormatting(loreColor.get()))
                                        .withItalic(false)
                                        .withBold(loreBold.get());
                        loreText.setStyle(style);
                        loreLines.add(loreText);
                }
        }

        private void addEnchantment(ItemEnchantmentsComponent.Builder builder, String enchantId, int level) {
                if (enchantId == null || enchantId.isEmpty() || mc.world == null)
                        return;

                String cleanId = enchantId.toLowerCase().replace(" ", "_");
                if (!cleanId.contains(":"))
                        cleanId = "minecraft:" + cleanId;
                String[] parts = cleanId.split(":");
                if (parts.length < 2) return;
                Identifier id = Identifier.of(parts[0], parts[1]);

                var key = net.minecraft.registry.RegistryKey.of(RegistryKeys.ENCHANTMENT, id);
                var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                Optional<RegistryEntry.Reference<Enchantment>> entry = registry.getOptional(key);

                entry.ifPresent(ref -> builder.add(ref, level));
        }

        private void addAttribute(AttributeModifiersComponent.Builder builder, AttributeType type, double value,
                        String name) {
                if (type == AttributeType.None)
                        return;

                RegistryEntry<EntityAttribute> attribute = getAttributeEntry(type);
                if (attribute == null)
                        return;

                EntityAttributeModifier.Operation op = switch (attributeOperation.get()) {
                        case Add -> EntityAttributeModifier.Operation.ADD_VALUE;
                        case MultiplyBase -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                        case MultiplyTotal -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                };

                EntityAttributeModifier modifier = new EntityAttributeModifier(
                                Identifier.of("orbiter", name), value, op);

                builder.add(attribute, modifier, AttributeModifierSlot.ANY);
        }

        private RegistryEntry<EntityAttribute> getAttributeEntry(AttributeType type) {
                return switch (type) {
                        case AttackDamage -> EntityAttributes.ATTACK_DAMAGE;
                        case AttackSpeed -> EntityAttributes.ATTACK_SPEED;
                        case MaxHealth -> EntityAttributes.MAX_HEALTH;
                        case MovementSpeed -> EntityAttributes.MOVEMENT_SPEED;
                        case Armor -> EntityAttributes.ARMOR;
                        case ArmorToughness -> EntityAttributes.ARMOR_TOUGHNESS;
                        case KnockbackResistance -> EntityAttributes.KNOCKBACK_RESISTANCE;
                        case Luck -> EntityAttributes.LUCK;
                        case AttackKnockback -> EntityAttributes.ATTACK_KNOCKBACK;
                        case FlyingSpeed -> EntityAttributes.FLYING_SPEED;
                        case FollowRange -> EntityAttributes.FOLLOW_RANGE;
                        default -> null;
                };
        }

        private void applyEntityData(ItemStack stack) {
                NbtCompound entityTag = new NbtCompound();

                if (!entityCustomName.get().isEmpty()) {
                        Formatting fmt = getFormatting(entityNameColor.get());
                        String jsonName = "{\"text\":\"" + entityCustomName.get().replace("\"", "\\\"")
                                        + "\",\"color\":\"" + fmt.getName() + "\"}";
                        entityTag.putString("CustomName", jsonName);
                        entityTag.putBoolean("CustomNameVisible", entityNameVisible.get());
                }

                if (entityHealth.get() > 0) {
                        entityTag.putFloat("Health", entityHealth.get());
                        NbtCompound healthAttr = new NbtCompound();
                        healthAttr.putString("id", "minecraft:max_health");
                        healthAttr.putDouble("base", entityHealth.get());
                        NbtList attrList = new NbtList();
                        attrList.add(healthAttr);
                        entityTag.put("attributes", attrList);
                }

                if (entityInvulnerable.get())
                        entityTag.putBoolean("Invulnerable", true);
                if (entityNoAI.get())
                        entityTag.putBoolean("NoAI", true);
                if (entitySilent.get())
                        entityTag.putBoolean("Silent", true);
                if (entityNoGravity.get())
                        entityTag.putBoolean("NoGravity", true);
                if (entityGlowing.get())
                        entityTag.putBoolean("Glowing", true);
                if (entityPersistent.get())
                        entityTag.putBoolean("PersistenceRequired", true);
                if (entityVisualFire.get())
                        entityTag.putBoolean("HasVisualFire", true);
                if (entityCharged.get())
                        entityTag.putBoolean("powered", true);
                if (entityFuse.get() > 0)
                        entityTag.putShort("Fuse", entityFuse.get().shortValue());
                if (entityExplosionPower.get() > 0) {
                        entityTag.putByte("ExplosionRadius", entityExplosionPower.get().byteValue());
                        entityTag.putInt("explosion_power", entityExplosionPower.get());
                }
                if (entitySlimeSize.get() > 0)
                        entityTag.putInt("Size", entitySlimeSize.get());

                if (!entityTag.isEmpty()) {
                        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                        String entityId = itemId.replace("minecraft:", "").replace("_spawn_egg", "");
                        entityTag.putString("id", "minecraft:" + entityId);
                        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(entityTag));
                }
        }

        private Formatting getFormatting(NameColor color) {
                return switch (color) {
                        case Black -> Formatting.BLACK;
                        case DarkBlue -> Formatting.DARK_BLUE;
                        case DarkGreen -> Formatting.DARK_GREEN;
                        case DarkAqua -> Formatting.DARK_AQUA;
                        case DarkRed -> Formatting.DARK_RED;
                        case DarkPurple -> Formatting.DARK_PURPLE;
                        case Gold -> Formatting.GOLD;
                        case Gray -> Formatting.GRAY;
                        case DarkGray -> Formatting.DARK_GRAY;
                        case Blue -> Formatting.BLUE;
                        case Green -> Formatting.GREEN;
                        case Aqua -> Formatting.AQUA;
                        case Red -> Formatting.RED;
                        case LightPurple -> Formatting.LIGHT_PURPLE;
                        case Yellow -> Formatting.YELLOW;
                        case White -> Formatting.WHITE;
                };
        }

        public enum NameColor {
                Black, DarkBlue, DarkGreen, DarkAqua, DarkRed, DarkPurple,
                Gold, Gray, DarkGray, Blue, Green, Aqua, Red, LightPurple,
                Yellow, White
        }

        public enum AttributeType {
                None, AttackDamage, AttackSpeed, AttackKnockback,
                MaxHealth, MovementSpeed, Armor, ArmorToughness,
                KnockbackResistance, Luck, FlyingSpeed, FollowRange
        }

        public enum AttributeOp {
                Add,
                MultiplyBase,
                MultiplyTotal
        }

        public enum PresetType {
                None,
                GodSword,
                GodAxe,
                GodBow,
                GodArmor,
                CrashBook,
                BanItem
        }
}
