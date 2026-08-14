package orbiter.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
        if (mc.player == null || mc.world == null || !isActive()) return;
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
        ServerCapabilities caps = ServerCapabilities.capture(mc.player.networkHandler);
        return caps.has("attribute") ? Method.OpAttributes : Method.CreativeReachItem;
    }

    private void applyOpAttributes() {
        ServerCapabilities caps = ServerCapabilities.capture(mc.player.networkHandler);
        if (!caps.has("attribute")) {
            if (debug.get()) info("/attribute not available on this server.");
            return;
        }
        String root = caps.preferredVanilla("attribute");
        mc.player.networkHandler.sendChatCommand(root + " @s minecraft:block_interaction_range base set " + fmt(reach.get()));
        mc.player.networkHandler.sendChatCommand(root + " @s minecraft:entity_interaction_range base set " + fmt(reach.get()));
        if (debug.get()) info("Sent /attribute commands. Range: " + fmt(reach.get()));
    }

    private void applyCreativeItem() {
        if (!mc.player.isCreative()) {
            if (debug.get()) info("CreativeReachItem requires creative mode.");
            return;
        }

        if (!hasSaved) {
            savedOffhand = mc.player.getOffHandStack().copy();
            hasSaved = true;
        }

        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(""));

        AttributeModifiersComponent.Builder attrs = AttributeModifiersComponent.builder();
        attrs.add(EntityAttributes.BLOCK_INTERACTION_RANGE,
            mod("orbiter:block_reach", reach.get() - 4.5), AttributeModifierSlot.OFFHAND);
        attrs.add(EntityAttributes.ENTITY_INTERACTION_RANGE,
            mod("orbiter:entity_reach", reach.get() - 4.5), AttributeModifierSlot.OFFHAND);
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, attrs.build());

        mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(45, stack));

        if (debug.get()) info("Installed invisible reach barrier in offhand. Range: " + fmt(reach.get()));
    }

    private void restoreOffhand() {
        if (!restoreOnDisable.get() || !hasSaved || mc.player == null || mc.player.networkHandler == null) return;

        ItemStack current = mc.player.getOffHandStack();
        boolean isOurs = !current.isEmpty()
            && current.get(DataComponentTypes.CUSTOM_NAME) != null
            && current.get(DataComponentTypes.CUSTOM_NAME).getString().isEmpty()
            && current.isOf(Items.BARRIER);

        if (isOurs) {
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(45, savedOffhand));
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

    private EntityAttributeModifier mod(String id, double value) {
        return new EntityAttributeModifier(Identifier.of(id), value, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, Math.min(32, v)));
    }
}
