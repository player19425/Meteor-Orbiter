package orbiter.modules.misc;

import orbiter.Orbiter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemInfo extends Module {

    private final SettingGroup sgDisplay = settings.getDefaultGroup();

    private final Setting<Boolean> showBasic = sgDisplay.add(new BoolSetting.Builder()
        .name("basic-info")
        .description("Show item ID and count as lore.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDurability = sgDisplay.add(new BoolSetting.Builder()
        .name("durability")
        .description("Show current/max durability as lore.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showEnchantments = sgDisplay.add(new BoolSetting.Builder()
        .name("enchantments")
        .description("Show all enchantments, ignoring HideFlags, as lore.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPluginEnchantments = sgDisplay.add(new BoolSetting.Builder()
        .name("plugin-enchantments")
        .description("Show CrazyEnchantments and other plugin enchantments stored in custom_data.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showComponents = sgDisplay.add(new BoolSetting.Builder()
        .name("all-components")
        .description("Show every vanilla component attached to the item as lore.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showNbt = sgDisplay.add(new BoolSetting.Builder()
        .name("full-nbt")
        .description("Show the item's full encoded NBT tag as lore (can be long).")
        .defaultValue(false)
        .build()
    );

    public ItemInfo() {
        super(Orbiter.CATEGORY, "item-info",
            "Adds client-side-only lore to item tooltips: durability, enchantments, components, and full NBT.");
    }

    public static ItemInfo get() {
        return Modules.get().get(ItemInfo.class);
    }

    public static boolean isEnabled() {
        ItemInfo m = get();
        return m != null && m.isActive();
    }

    public static void appendTooltip(ItemStack stack, List<Text> lines) {
        if (!isEnabled()) return;
        if (stack == null || stack.isEmpty()) return;

        ItemInfo m = get();
        if (m == null) return;

        List<Text> additions = new ArrayList<>();

        if (m.showBasic.get()) {
            additions.add(lore("ID: " + Registries.ITEM.getId(stack.getItem())));
            additions.add(lore("Count: " + stack.getCount()));
        }

        if (m.showDurability.get() && stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int damage = stack.getDamage();
            int remaining = max - damage;
            additions.add(lore("Durability: " + remaining + " / " + max + " (" + damage + " damaged)"));
        }

        if (m.showEnchantments.get()) {
            ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (enchants != null && !enchants.isEmpty()) {
                additions.add(lore("Enchantments:"));
                for (Map.Entry<RegistryEntry<Enchantment>, Integer> entry : enchants.getEnchantmentEntries()) {
                    Text name = Enchantment.getName(entry.getKey(), entry.getValue());
                    additions.add(lore("  " + name.getString()));
                }
            }
            ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                additions.add(lore("Stored Enchantments:"));
                for (Map.Entry<RegistryEntry<Enchantment>, Integer> entry : stored.getEnchantmentEntries()) {
                    Text name = Enchantment.getName(entry.getKey(), entry.getValue());
                    additions.add(lore("  " + name.getString()));
                }
            }
        }

        if (m.showPluginEnchantments.get()) {
            appendPluginEnchantments(stack, additions);
        }

        if (m.showComponents.get()) {
            additions.add(lore("Components:"));
            boolean any = false;
            for (Component<?> component : stack.getComponents()) {
                any = true;
                ComponentType<?> type = component.type();
                Object value = component.value();
                additions.add(lore("  " + type + ": " + value));
            }
            if (!any) additions.add(lore("  (none)"));
        }

        if (m.showNbt.get()) {
            additions.add(lore("Full NBT:"));
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                .result()
                .ifPresentOrElse(
                    tag -> additions.add(lore("  " + tag.toString())),
                    () -> additions.add(lore("  <failed to encode>"))
                );
        }

        if (!additions.isEmpty()) {
            try {
            lines.add(Text.empty());
            lines.add(lore("=== Item Info ==="));
                lines.addAll(additions);
            } catch (UnsupportedOperationException ignored) {

            }
        }
    }

    private static void appendPluginEnchantments(ItemStack stack, List<Text> additions) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return;
        NbtCompound tag = custom.copyNbt();
        if (tag == null || !tag.contains("PublicBukkitValues")) return;

        NbtCompound pbv = tag.getCompound("PublicBukkitValues").orElse(null);
        if (pbv == null) return;

        String ceJson = pbv.getString("crazyenchantments:crazyenchants").orElse("");
        if (!ceJson.isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(ceJson).getAsJsonObject();
                JsonObject enchants = root.getAsJsonObject("enchants");
                if (enchants != null && !enchants.entrySet().isEmpty()) {
                    additions.add(lore("CrazyEnchantments:"));
                    for (Map.Entry<String, JsonElement> e : enchants.entrySet()) {
                        int lvl = e.getValue().getAsInt();
                        additions.add(lore("  " + formatEnchantName(e.getKey()) + " " + roman(lvl)));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static String roman(int num) {
        if (num < 1 || num > 3999) return String.valueOf(num);
        String[] m = {"", "M", "MM", "MMM"};
        String[] c = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] x = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] i = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return m[num / 1000] + c[(num % 1000) / 100] + x[(num % 100) / 10] + i[num % 10];
    }

    private static String formatEnchantName(String key) {
        String s = key;
        int idx = s.lastIndexOf(':');
        if (idx >= 0) s = s.substring(idx + 1);
        s = s.replace("CrazyEnchantments_", "").replace("crazyenchantments_", "");
        s = s.replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder();
        boolean capNext = true;
        for (char c : s.toCharArray()) {
            if (c == ' ') {
                capNext = true;
                out.append(c);
            } else if (capNext) {
                out.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private static Text lore(String text) {
        return Text.literal(text).formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
    }
}
