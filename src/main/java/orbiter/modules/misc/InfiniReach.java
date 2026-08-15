package orbiter.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import orbiter.Orbiter;
import orbiter.util.ConfigModifier;
import orbiter.util.ServerCapabilities;

public class InfiniReach extends Module {
    public enum Method { Auto, OpAttributes, CreativeReachItem }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Method> method = sg.add(new EnumSetting.Builder<Method>()
        .name("method").description("How to apply extended reach.")
        .defaultValue(Method.Auto).build());

    private final Setting<Double> reach = sg.add(new DoubleSetting.Builder()
        .name("reach").description("Interaction range to set.")
        .defaultValue(12.0).min(5.0).max(32.0).sliderRange(5.0, 32.0).build());

    private final Setting<Boolean> restoreOnDisable = sg.add(new BoolSetting.Builder()
        .name("restore-on-disable").description("Restore the previous offhand contents on disable.")
        .defaultValue(true).build());

    private final Setting<Boolean> debug = sg.add(new BoolSetting.Builder()
        .name("debug").description("Show method status in chat.")
        .defaultValue(true).build());

    private ItemStack savedOffhand = ItemStack.EMPTY;
    private boolean hasSaved = false;
    private Method lastMethod = null;
    private double lastReach = -1;

    public InfiniReach() {
        super(Orbiter.CATEGORY_STUPID, "infini-reach",
            "Extended reach via OP commands or an invisible offhand item. Server validates distance.");
    }

    @Override
    public void onActivate() {
        if (!ConfigModifier.get().stupidModules.get()) { info("Stupid Modules disabled."); toggle(); return; }
        reset();
        if (debug.get()) info("InfiniReach: OP attributes work best. Creative item goes in offhand (invisible barrier).");
    }

    @Override
    public void onDeactivate() {
        restoreOffhand();
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || !isActive()) return;
        if (!ConfigModifier.get().stupidModules.get()) { toggle(); return; }

        Method selected = resolveMethod();
        double currentReach = reach.get();

        if (selected == lastMethod && currentReach == lastReach) return;

        switch (selected) {
            case OpAttributes -> applyOpAttributes();
            case CreativeReachItem -> applyCreativeItem();
            default -> {
                if (debug.get()) info("Auto: no method available.");
                return;
            }
        }

        lastMethod = selected;
        lastReach = currentReach;
    }

    private Method resolveMethod() {
        Method m = method.get();
        if (m != Method.Auto) return m;
        ServerCapabilities caps = ServerCapabilities.capture(mc.player.connection);
        return caps.has("attribute") ? Method.OpAttributes : Method.CreativeReachItem;
    }

    private void applyOpAttributes() {
        ServerCapabilities caps = ServerCapabilities.capture(mc.player.connection);
        if (!caps.has("attribute")) {
            if (debug.get()) info("/attribute not available on this server.");
            return;
        }
        String root = caps.preferredVanilla("attribute");
        mc.player.connection.sendCommand(root + " @s minecraft:block_interaction_range base set " + fmt(reach.get()));
        mc.player.connection.sendCommand(root + " @s minecraft:entity_interaction_range base set " + fmt(reach.get()));
        if (debug.get()) info("Sent /attribute commands. Range: " + fmt(reach.get()));
    }

    private void applyCreativeItem() {
        if (!mc.player.isCreative()) {
            if (debug.get()) info("CreativeReachItem requires creative mode.");
            return;
        }

        if (!hasSaved) {
            savedOffhand = mc.player.getOffhandItem().copy();
            hasSaved = true;
        }

        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));

        ItemAttributeModifiers.Builder attrs = ItemAttributeModifiers.builder();
        attrs.add(Attributes.BLOCK_INTERACTION_RANGE,
            mod("orbiter:block_reach", reach.get() - 4.5), EquipmentSlotGroup.OFFHAND);
        attrs.add(Attributes.ENTITY_INTERACTION_RANGE,
            mod("orbiter:entity_reach", reach.get() - 4.5), EquipmentSlotGroup.OFFHAND);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs.build());

        mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(45, stack));

        if (debug.get()) info("Installed invisible reach barrier in offhand. Range: " + fmt(reach.get()));
    }

    private void restoreOffhand() {
        if (!restoreOnDisable.get() || !hasSaved || mc.player == null || mc.player.connection == null) return;

        ItemStack current = mc.player.getOffhandItem();
        boolean isOurs = !current.isEmpty()
            && current.get(DataComponents.CUSTOM_NAME) != null
            && current.get(DataComponents.CUSTOM_NAME).getString().isEmpty()
            && current.is(Items.BARRIER);

        if (isOurs) {
            mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(45, savedOffhand));
            if (debug.get()) info("Restored offhand.");
        } else if (debug.get()) {
            info("Offhand changed externally; restore skipped.");
        }
        hasSaved = false;
    }

    private void reset() {
        lastMethod = null;
        lastReach = -1;
        savedOffhand = ItemStack.EMPTY;
        hasSaved = false;
    }

    private AttributeModifier mod(String id, double value) {
        return new AttributeModifier(Identifier.withDefaultNamespace(id), value, AttributeModifier.Operation.ADD_VALUE);
    }

    private String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, Math.min(32, v)));
    }
}
