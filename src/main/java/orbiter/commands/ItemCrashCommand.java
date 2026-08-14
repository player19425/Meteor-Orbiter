package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ItemCrashCommand extends Command {
    public ItemCrashCommand() {
        super("itemcrash", "Overloads your held item with extreme enchantments, attributes, and NBT data.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null)
                return SINGLE_SUCCESS;

            if (!mc.player.getAbilities().creativeMode) {
                error("You must be in Creative mode!");
                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) {
                error("Hold an item first!");
                return SINGLE_SUCCESS;
            }

            info("Overloading item with extreme data...");

            if (mc.world != null) {
                var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                ItemEnchantmentsComponent.Builder enchBuilder = new ItemEnchantmentsComponent.Builder(
                        ItemEnchantmentsComponent.DEFAULT);

                registry.streamEntries().forEach(ref -> enchBuilder.add(ref, 255));
                stack.set(DataComponentTypes.ENCHANTMENTS, enchBuilder.build());
            }

            AttributeModifiersComponent.Builder attrBuilder = AttributeModifiersComponent.builder();
            addCrashAttr(attrBuilder, EntityAttributes.ATTACK_DAMAGE, 0);
            addCrashAttr(attrBuilder, EntityAttributes.ATTACK_SPEED, 1);
            addCrashAttr(attrBuilder, EntityAttributes.MAX_HEALTH, 2);
            addCrashAttr(attrBuilder, EntityAttributes.MOVEMENT_SPEED, 3);
            addCrashAttr(attrBuilder, EntityAttributes.ARMOR, 4);
            addCrashAttr(attrBuilder, EntityAttributes.ARMOR_TOUGHNESS, 5);
            addCrashAttr(attrBuilder, EntityAttributes.KNOCKBACK_RESISTANCE, 6);
            addCrashAttr(attrBuilder, EntityAttributes.LUCK, 7);
            addCrashAttr(attrBuilder, EntityAttributes.ATTACK_KNOCKBACK, 8);
            addCrashAttr(attrBuilder, EntityAttributes.FLYING_SPEED, 9);
            addCrashAttr(attrBuilder, EntityAttributes.FOLLOW_RANGE, 10);
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrBuilder.build());

            StringBuilder nameSb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                nameSb.append("\u00A7k\u2588\u2588\u2588\u2588");
            }
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(nameSb.toString()).setStyle(
                            Style.EMPTY.withColor(Formatting.RED).withBold(true).withObfuscated(true)));

            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

            if (mc.getNetworkHandler() != null) {
                int slot = mc.player.getInventory().getSelectedSlot();
                mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(36 + slot, stack));
            }

            info("Item overloaded! (Enchants: ALL@255, Attributes: ALL@999999999)");
            return SINGLE_SUCCESS;
        });
    }

    private void addCrashAttr(AttributeModifiersComponent.Builder builder,
            RegistryEntry<EntityAttribute> attr, int index) {
        EntityAttributeModifier mod = new EntityAttributeModifier(
                Identifier.of("orbiter", "crash_attr_" + index),
                999999999.0,
                EntityAttributeModifier.Operation.ADD_VALUE);
        builder.add(attr, mod, AttributeModifierSlot.ANY);
    }
}
