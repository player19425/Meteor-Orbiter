package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemGenerator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Item Selection");
    private final SettingGroup sgStack = settings.createGroup("Stack Amount");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgEnchants = settings.createGroup("Random Enchants");
    private final SettingGroup sgAttributes = settings.createGroup("Random Attributes");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay in ticks between each generation cycle.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 40)
            .build());

    private final Setting<Integer> itemsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("items-per-cycle")
            .description("Number of items generated per cycle. Keep low to avoid packet kicks.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 10)
            .build());

    private final Setting<Boolean> dropItems = sgGeneral.add(new BoolSetting.Builder()
            .name("drop-to-ground")
            .description("Drop items directly to the ground instead of placing in inventory.")
            .defaultValue(true)
            .build());

    private final Setting<ItemMode> itemMode = sgItems.add(new EnumSetting.Builder<ItemMode>()
            .name("item-mode")
            .description("How to select items to generate.")
            .defaultValue(ItemMode.Random)
            .build());

    private final Setting<List<Item>> specificItems = sgItems.add(new ItemListSetting.Builder()
            .name("specific-items")
            .description("Items to generate when mode is Manual.")
            .defaultValue(Items.DIAMOND, Items.EMERALD, Items.GOLDEN_APPLE)
            .visible(() -> itemMode.get() == ItemMode.Manual)
            .build());

    private final Setting<StackMode> stackMode = sgStack.add(new EnumSetting.Builder<StackMode>()
            .name("stack-mode")
            .description("How to determine stack size.")
            .defaultValue(StackMode.Random)
            .build());

    private final Setting<Integer> specificCount = sgStack.add(new IntSetting.Builder()
            .name("specific-count")
            .description("Exact stack size to use when mode is Specific.")
            .defaultValue(64)
            .min(1)
            .sliderRange(1, 64)
            .visible(() -> stackMode.get() == StackMode.Specific)
            .build());

    private final Setting<Boolean> maxStacks = sgStack.add(new BoolSetting.Builder()
            .name("max-stacks")
            .description("Always use the maximum stack size for each item.")
            .defaultValue(false)
            .visible(() -> stackMode.get() == StackMode.Random)
            .build());

    private final Setting<Boolean> disableOnLeave = sgSafety.add(new BoolSetting.Builder()
            .name("disable-on-leave")
            .description("Disable Item Generator automatically after leaving the world/server.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> safePacketMode = sgSafety.add(new BoolSetting.Builder()
            .name("safe-packet-mode")
            .description("Apply safety limits to prevent invalid or oversized creative packets.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> safeItemsPerCycleCap = sgSafety.add(new IntSetting.Builder()
            .name("safe-items-per-cycle-cap")
            .description("Maximum items sent per cycle while safe mode is enabled.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 10)
            .visible(safePacketMode::get)
            .build());

    private final Setting<Integer> safeMaxPacketsPerSecond = sgSafety.add(new IntSetting.Builder()
            .name("safe-max-packets-per-second")
            .description("Hard rate limit for creative packets while safe mode is enabled.")
            .defaultValue(8)
            .min(1)
            .sliderRange(1, 20)
            .visible(safePacketMode::get)
            .build());

    private final Setting<Integer> safeMaxStackCount = sgSafety.add(new IntSetting.Builder()
            .name("safe-max-stack-count")
            .description("Maximum stack count while safe mode is enabled (protocol-safe upper bound is 99).")
            .defaultValue(64)
            .min(1)
            .max(99)
            .sliderRange(1, 99)
            .visible(safePacketMode::get)
            .build());

    private final Setting<Integer> safeMaxSerializedLength = sgSafety.add(new IntSetting.Builder()
            .name("safe-max-serialized-length")
            .description("Skip generated items with very large serialized payloads.")
            .defaultValue(8000)
            .min(512)
            .sliderRange(512, 32000)
            .visible(safePacketMode::get)
            .build());

    private final Setting<Boolean> randomEnchants = sgEnchants.add(new BoolSetting.Builder()
            .name("random-enchants")
            .description("Apply random enchantments to generated items.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> minEnchants = sgEnchants.add(new IntSetting.Builder()
            .name("min-enchants")
            .description("Minimum number of enchantments to apply.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 10)
            .visible(randomEnchants::get)
            .build());

    private final Setting<Integer> maxEnchants = sgEnchants.add(new IntSetting.Builder()
            .name("max-enchants")
            .description("Maximum number of enchantments to apply.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .visible(randomEnchants::get)
            .build());

    private final Setting<Integer> minEnchantLevel = sgEnchants.add(new IntSetting.Builder()
            .name("min-enchant-level")
            .description("Minimum enchantment level.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 255)
            .visible(randomEnchants::get)
            .build());

    private final Setting<Integer> maxEnchantLevel = sgEnchants.add(new IntSetting.Builder()
            .name("max-enchant-level")
            .description("Maximum enchantment level.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 255)
            .visible(randomEnchants::get)
            .build());

    private final Setting<Boolean> randomAttributes = sgAttributes.add(new BoolSetting.Builder()
            .name("random-attributes")
            .description("Apply random attribute modifiers to generated items.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> minAttributes = sgAttributes.add(new IntSetting.Builder()
            .name("min-attributes")
            .description("Minimum number of attribute modifiers.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 5)
            .visible(randomAttributes::get)
            .build());

    private final Setting<Integer> maxAttributes = sgAttributes.add(new IntSetting.Builder()
            .name("max-attributes")
            .description("Maximum number of attribute modifiers.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 10)
            .visible(randomAttributes::get)
            .build());

    private final Setting<Double> minAttrValue = sgAttributes.add(new DoubleSetting.Builder()
            .name("min-attribute-value")
            .description("Minimum attribute modifier value.")
            .defaultValue(1.0)
            .min(-1024.0)
            .sliderRange(-100.0, 100.0)
            .visible(randomAttributes::get)
            .build());

    private final Setting<Double> maxAttrValue = sgAttributes.add(new DoubleSetting.Builder()
            .name("max-attribute-value")
            .description("Maximum attribute modifier value.")
            .defaultValue(100.0)
            .min(-1024.0)
            .sliderRange(-100.0, 1000.0)
            .visible(randomAttributes::get)
            .build());

    private final Random random = new Random();
    private int tickCounter = 0;
    private List<Item> itemCache = null;
    private boolean warnedBurstClamp = false;
    private boolean warnedRateThrottle = false;
    private long lastCreativePacketAtMs = 0L;

    @SuppressWarnings("unchecked")
    private static final RegistryEntry<EntityAttribute>[] ATTRIBUTE_POOL = new RegistryEntry[] {
            EntityAttributes.ATTACK_DAMAGE,
            EntityAttributes.ATTACK_SPEED,
            EntityAttributes.MAX_HEALTH,
            EntityAttributes.MOVEMENT_SPEED,
            EntityAttributes.ARMOR,
            EntityAttributes.ARMOR_TOUGHNESS,
            EntityAttributes.KNOCKBACK_RESISTANCE,
            EntityAttributes.LUCK,
            EntityAttributes.ATTACK_KNOCKBACK
    };

    public ItemGenerator() {
        super(Orbiter.CATEGORY_OP, "item-generator",
                "Spawns random or specific items with optional random enchants/attributes. Requires Creative mode.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        warnedBurstClamp = false;
        warnedRateThrottle = false;
        lastCreativePacketAtMs = 0L;
        rebuildCache();

        if (mc.player != null && !mc.player.getAbilities().creativeMode) {
            warning("You must be in Creative mode!");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        itemCache = null;
        warnedBurstClamp = false;
        warnedRateThrottle = false;
        lastCreativePacketAtMs = 0L;
    }

    private void rebuildCache() {
        if (itemMode.get() == ItemMode.Manual) {
            List<Item> manual = specificItems.get();
            if (manual == null || manual.isEmpty()) {
                itemCache = List.of(Items.STONE);
                return;
            }

            List<Item> filtered = new ArrayList<>();
            for (Item item : manual) {
                if (item == null || item == Items.AIR) continue;
                filtered.add(item);
            }

            itemCache = filtered.isEmpty() ? List.of(Items.STONE) : filtered;
        } else {
            itemCache = Registries.ITEM.stream()
                    .filter(item -> item != null && item != Items.AIR)
                    .toList();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!disableOnLeave.get() || !isActive()) return;

        info("Disconnected from world/server. Item Generator disabled by safety setting.");
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.networkHandler == null)
            return;

        if (!mc.player.getAbilities().creativeMode) {
            warning("Creative mode required! Disabling.");
            toggle();
            return;
        }

        tickCounter++;
        if (tickCounter < delay.get())
            return;
        tickCounter = 0;

        if (itemCache == null || itemCache.isEmpty())
            rebuildCache();
        if (itemCache == null || itemCache.isEmpty())
            return;

        boolean safe = safePacketMode.get();
        int requestedItems = itemsPerTick.get();
        int burst = safe ? Math.min(requestedItems, safeItemsPerCycleCap.get()) : requestedItems;
        long minMsBetweenPackets = safe ? Math.max(1L, 1000L / Math.max(1, safeMaxPacketsPerSecond.get())) : 0L;

        if (safe && requestedItems > burst && !warnedBurstClamp) {
            warning("Safe mode limited items-per-cycle to " + burst + ". Increase safe-items-per-cycle-cap to allow more.");
            warnedBurstClamp = true;
        }

        for (int i = 0; i < burst; i++) {
            if (safe && !canSendCreativePacket(minMsBetweenPackets)) {
                if (!warnedRateThrottle) {
                    warning("Safe mode throttling creative packets to " + safeMaxPacketsPerSecond.get()
                            + "/s to prevent kicks.");
                    warnedRateThrottle = true;
                }
                break;
            }

            Item item = itemCache.get(random.nextInt(itemCache.size()));
            if (item == Items.AIR) continue;

            int count = getStackCount(item);
            if (safe) count = Math.min(count, safeMaxStackCount.get());

            ItemStack stack = new ItemStack(item, count);
            if (stack.isEmpty()) continue;

            if (randomEnchants.get() && mc.world != null) applyRandomEnchants(stack);

            if (randomAttributes.get()) applyRandomAttributes(stack);

            if (safe && !isStackSafeForPacket(stack)) continue;

            if (dropItems.get()) {
                mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(-1, stack));
            } else {
                int slot = 36 + (i % 9);
                mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot, stack));
            }

            lastCreativePacketAtMs = System.currentTimeMillis();
        }
    }

    private boolean canSendCreativePacket(long minMsBetweenPackets) {
        return System.currentTimeMillis() - lastCreativePacketAtMs >= minMsBetweenPackets;
    }

    private void applyRandomEnchants(ItemStack stack) {
        if (mc.world == null)
            return;

        var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        List<RegistryEntry.Reference<Enchantment>> allEnchants = registry.streamEntries().toList();

        if (allEnchants.isEmpty())
            return;

        int numEnchants = minEnchants.get() + random.nextInt(Math.max(1, maxEnchants.get() - minEnchants.get() + 1));
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                ItemEnchantmentsComponent.DEFAULT);

        for (int i = 0; i < numEnchants; i++) {
            RegistryEntry.Reference<Enchantment> enchant = allEnchants.get(random.nextInt(allEnchants.size()));
            int level = minEnchantLevel.get()
                    + random.nextInt(Math.max(1, maxEnchantLevel.get() - minEnchantLevel.get() + 1));
            builder.add(enchant, level);
        }

        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
    }

    private void applyRandomAttributes(ItemStack stack) {
        int numAttrs = minAttributes.get() + random.nextInt(Math.max(1, maxAttributes.get() - minAttributes.get() + 1));
        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();

        for (int i = 0; i < numAttrs; i++) {
            RegistryEntry<EntityAttribute> attr = ATTRIBUTE_POOL[random.nextInt(ATTRIBUTE_POOL.length)];
            double value = minAttrValue.get() + random.nextDouble() * (maxAttrValue.get() - minAttrValue.get());

            EntityAttributeModifier modifier = new EntityAttributeModifier(
                    Identifier.of("orbiter", "gen_attr_" + i + "_" + random.nextInt(10000)),
                    value,
                    EntityAttributeModifier.Operation.ADD_VALUE);

            builder.add(attr, modifier, AttributeModifierSlot.ANY);
        }

        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
    }

    private int getStackCount(Item item) {
        int maxCount = Math.max(1, item.getMaxCount());
        maxCount = Math.min(maxCount, 99);

        return switch (stackMode.get()) {
            case Random -> maxStacks.get() ? maxCount : 1 + random.nextInt(maxCount);
            case Specific -> Math.min(specificCount.get(), maxCount);
            case Max -> maxCount;
        };
    }

    private boolean isStackSafeForPacket(ItemStack stack) {

        return stack.toString().length() <= safeMaxSerializedLength.get();
    }

    public enum ItemMode {
        Random,
        Manual
    }

    public enum StackMode {
        Random,
        Specific,
        Max
    }
}
