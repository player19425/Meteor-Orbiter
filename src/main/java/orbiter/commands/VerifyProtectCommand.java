package orbiter.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import meteordevelopment.meteorclient.systems.modules.Modules;
import orbiter.mixin.OrbiterMixinPlugin;
import orbiter.modules.misc.ServerProtect;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class VerifyProtectCommand extends Command {
    public VerifyProtectCommand() {
        super("verify-protect", "Tests ServerProtect crash-item detection against known payloads.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;

            ServerProtect module = Modules.get().get(ServerProtect.class);
            info("§eServerProtect status: §7active=" + (module != null && module.isActive())
                + ", CrashFixer delegated=" + OrbiterMixinPlugin.isCrashFixerDelegated()
                + ", external packet protection=" + OrbiterMixinPlugin.isExternalPacketProtectionPresent());
            info("§eDialog suppression remaining: §7" + (ServerProtect.dialogSuppressionRemainingMs() / 1000L) + " seconds");
            if (module != null && module.isLegacyEntityRemovalEnabled()) {
                info("§cWARNING: legacy destructive entity-data removal is enabled.");
            }

            int passed = 0;
            int total = 0;

            total++;
            ItemStack translateEgg = makeTranslateBombEgg();
            if (orbiter.modules.misc.ServerProtect.isMaliciousItem(translateEgg)) {
                info("\u00a7a[PASS] \u00a77Nested-translate egg detected");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77Nested-translate egg NOT detected");
            }

            total++;
            ItemStack infinityEgg = makeInfinityEgg();
            if (orbiter.modules.misc.ServerProtect.isMaliciousItem(infinityEgg)) {
                info("\u00a7a[PASS] \u00a77Infinity-absorption egg detected");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77Infinity-absorption egg NOT detected");
            }

            total++;
            ItemStack loreBomb = makeLoreBomb();
            if (orbiter.modules.misc.ServerProtect.isMaliciousItem(loreBomb)) {
                info("\u00a7a[PASS] \u00a77Obfuscated lore bomb detected");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77Obfuscated lore bomb NOT detected");
            }

            total++;
            ItemStack clean = new ItemStack(Items.DIAMOND, 1);
            clean.set(DataComponents.CUSTOM_NAME, Component.literal("Diamond").setStyle(
                Style.EMPTY.withColor(ChatFormatting.AQUA)));
            if (!orbiter.modules.misc.ServerProtect.isMaliciousItem(clean)) {
                info("\u00a7a[PASS] \u00a77Clean item not falsely flagged");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77Clean item WRONGLY flagged as malicious");
            }

            total++;
            ItemStack variant = makeVariantTranslateEgg();
            if (orbiter.modules.misc.ServerProtect.isMaliciousItem(variant)) {
                info("\u00a7a[PASS] \u00a77%2$s translate variant detected");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77%2$s translate variant NOT detected");
            }

            total++;
            ItemStack elderGuardian = makeElderGuardianEgg();
            if (orbiter.modules.misc.ServerProtect.isMaliciousItem(elderGuardian)) {
                info("\u00a7a[PASS] \u00a77Elder guardian Radius:Infinity egg detected");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77Elder guardian Radius:Infinity egg NOT detected");
            }

            total++;
            ItemStack extremeAttr = makeExtremeAttributeItem();
            if (orbiter.modules.misc.ServerProtect.isMaliciousItem(extremeAttr)) {
                info("\u00a7a[PASS] \u00a77Extreme attribute modifier detected");
                passed++;
            } else {
                info("\u00a7c[FAIL] \u00a77Extreme attribute modifier NOT detected");
            }

            total++;
            ItemStack safeEgg = makeSafeSpawnEgg();
            String before = itemFingerprint(safeEgg);
            boolean malicious = ServerProtect.isMaliciousItem(safeEgg);
            java.util.List<Component> safeTooltip = ServerProtect.createSafeItemTooltip(safeEgg);
            String after = itemFingerprint(safeEgg);
            if (!malicious && safeTooltip.isEmpty() && before.equals(after)) {
                info("§a[PASS] §7Safe custom spawn egg accepted without mutation");
                passed++;
            } else {
                info("§c[FAIL] §7Safe custom spawn egg was rejected or changed");
            }

            total++;
            ItemStack immutableBomb = makeTranslateBombEgg();
            String bombBefore = itemFingerprint(immutableBomb);
            java.util.List<Component> replacement = ServerProtect.createSafeItemTooltip(immutableBomb);
            Component replacementName = ServerProtect.createSafeItemName(immutableBomb);
            String bombAfter = itemFingerprint(immutableBomb);
            if (!replacement.isEmpty() && replacementName != null && bombBefore.equals(bombAfter)) {
                info("§a[PASS] §7Unsafe item hidden client-side without source mutation");
                passed++;
            } else {
                info("§c[FAIL] §7Safe item view modified the source stack");
            }

            info("\u00a7eResult: " + passed + "/" + total + " tests passed");
            if (passed == total) {
                info("\u00a7aAll detection checks passed.");
            } else {
                info("\u00a7cSome checks failed - review the detector.");
            }
            return SINGLE_SUCCESS;
        });
    }

    private ItemStack makeTranslateBombEgg() {
        ItemStack egg = new ItemStack(Items.CAVE_SPIDER_SPAWN_EGG, 1);
        egg.set(DataComponents.CUSTOM_NAME, Component.literal("Tesla's ICBM").setStyle(
            Style.EMPTY.withColor(0x8219F3)));

        Component inner = Component.translatable("%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s", "x");
        Component mid = Component.translatable("%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s%1$s", inner);
        Component outer = Component.translatable("%1$s%1$s%1$s%1$s%1$s%1$s%1$s", mid);
        egg.set(DataComponents.CUSTOM_NAME, outer);
        return egg;
    }

    private ItemStack makeVariantTranslateEgg() {
        ItemStack egg = new ItemStack(Items.EGG, 1);
        Component inner = Component.translatable("%2$s%2$s%2$s%2$s%2$s%2$s%2$s%2$s%2$s%2$s", "a", "b");
        Component outer = Component.translatable("%2$s%2$s%2$s%2$s%2$s%2$s%2$s", "a", inner);
        egg.set(DataComponents.CUSTOM_NAME, outer);
        return egg;
    }

    private ItemStack makeInfinityEgg() {
        ItemStack egg = new ItemStack(Items.CAVE_SPIDER_SPAWN_EGG, 1);
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("AbsorptionAmount", Double.POSITIVE_INFINITY);
        nbt.putString("id", "minecraft:cave_spider");
        TypedEntityData<EntityType<?>> data = TypedEntityData.of(EntityTypes.CAVE_SPIDER, nbt);
        egg.set(DataComponents.ENTITY_DATA, data);
        return egg;
    }

    private ItemStack makeLoreBomb() {
        ItemStack item = new ItemStack(Items.STICK, 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) sb.append("\u00A7k\u2588");
        Component line = Component.literal(sb.toString()).setStyle(Style.EMPTY.withObfuscated(true));
        item.set(DataComponents.LORE, new ItemLore(List.of(line)));
        return item;
    }

    private ItemStack makeElderGuardianEgg() {
        ItemStack egg = new ItemStack(Items.ELDER_GUARDIAN_SPAWN_EGG, 64);
        egg.set(DataComponents.CUSTOM_NAME, Component.literal("Laggy Jumpscare").setStyle(
            Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true)));
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:area_effect_cloud");
        nbt.putInt("Duration", 2147483627);
        nbt.putDouble("Radius", Double.POSITIVE_INFINITY);
        nbt.putInt("ReapplicationDelay", 1);
        CompoundTag particle = new CompoundTag();
        particle.putString("type", "minecraft:elder_guardian");
        nbt.put("Particle", particle);
        TypedEntityData<EntityType<?>> data = TypedEntityData.of(EntityTypes.CAVE_SPIDER, nbt);
        egg.set(DataComponents.ENTITY_DATA, data);

        ItemAttributeModifiers.Builder attrs = ItemAttributeModifiers.builder();
        attrs.add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(Identifier.fromNamespaceAndPath("itemeditor", "generated/0"),
                2.147483627e9, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND);
        egg.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs.build());
        return egg;
    }

    private ItemStack makeExtremeAttributeItem() {
        ItemStack item = new ItemStack(Items.STICK, 1);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Extreme Stick").setStyle(
            Style.EMPTY.withColor(ChatFormatting.RED)));
        ItemAttributeModifiers.Builder attrs = ItemAttributeModifiers.builder();
        attrs.add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(Identifier.fromNamespaceAndPath("test", "extreme"),
                2.147483627e9, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND);
        item.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs.build());
        return item;
    }

    private ItemStack makeSafeSpawnEgg() {
        ItemStack egg = new ItemStack(Items.ZOMBIE_SPAWN_EGG, 1);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:zombie");
        nbt.putFloat("Health", 20.0f);
        nbt.putBoolean("NoAI", true);
        nbt.putBoolean("Glowing", true);
        egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(EntityTypes.ZOMBIE, nbt));
        return egg;
    }

    private String itemFingerprint(ItemStack stack) {
        return stack.getItem().toString() + "|" + stack.getCount() + "|"
            + String.valueOf(stack.getComponents());
    }
}
