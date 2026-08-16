package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ItemCrashCommand extends Command {
    public ItemCrashCommand() {
        super("itemcrash", "Overloads your held item with extreme enchantments, attributes, and NBT data.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null)
                return SINGLE_SUCCESS;

            if (!mc.player.getAbilities().instabuild) {
                error("You must be in Creative mode!");
                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) {
                error("Hold an item first!");
                return SINGLE_SUCCESS;
            }

            info("Overloading item with extreme data...");

            if (mc.level != null) {
                var registry = mc.level.registryAccess().getOrThrow(Registries.ENCHANTMENT);
                ItemEnchantments.Mutable enchBuilder = new ItemEnchantments.Mutable(
                        ItemEnchantments.EMPTY);

                var reg = registry.value();
                reg.stream().forEach(e -> enchBuilder.set(reg.wrapAsHolder(e), 255));
                stack.set(DataComponents.ENCHANTMENTS, enchBuilder.toImmutable());
            }

            ItemAttributeModifiers.Builder attrBuilder = ItemAttributeModifiers.builder();
            addCrashAttr(attrBuilder, Attributes.ATTACK_DAMAGE, 0);
            addCrashAttr(attrBuilder, Attributes.ATTACK_SPEED, 1);
            addCrashAttr(attrBuilder, Attributes.MAX_HEALTH, 2);
            addCrashAttr(attrBuilder, Attributes.MOVEMENT_SPEED, 3);
            addCrashAttr(attrBuilder, Attributes.ARMOR, 4);
            addCrashAttr(attrBuilder, Attributes.ARMOR_TOUGHNESS, 5);
            addCrashAttr(attrBuilder, Attributes.KNOCKBACK_RESISTANCE, 6);
            addCrashAttr(attrBuilder, Attributes.LUCK, 7);
            addCrashAttr(attrBuilder, Attributes.ATTACK_KNOCKBACK, 8);
            addCrashAttr(attrBuilder, Attributes.FLYING_SPEED, 9);
            addCrashAttr(attrBuilder, Attributes.FOLLOW_RANGE, 10);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrBuilder.build());

            StringBuilder nameSb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                nameSb.append("\u00A7k\u2588\u2588\u2588\u2588");
            }
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(nameSb.toString()).setStyle(
                            Style.EMPTY.withColor(ChatFormatting.RED).withBold(true).withObfuscated(true)));

            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

            if (mc.getConnection() != null) {
                int slot = mc.player.getInventory().getSelectedSlot();
                mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(36 + slot, stack));
            }

            info("Item overloaded! (Enchants: ALL@255, Attributes: ALL@999999999)");
            return SINGLE_SUCCESS;
        });
    }

    private void addCrashAttr(ItemAttributeModifiers.Builder builder,
            Holder<Attribute> attr, int index) {
        AttributeModifier mod = new AttributeModifier(
                Identifier.fromNamespaceAndPath("orbiter", "crash_attr_" + index),
                999999999.0,
                AttributeModifier.Operation.ADD_VALUE);
        builder.add(attr, mod, EquipmentSlotGroup.ANY);
    }
}
