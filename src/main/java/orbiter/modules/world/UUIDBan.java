package orbiter.modules.world;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.chat.Component;
import orbiter.Orbiter;
import orbiter.util.ConfigModifier;
import orbiter.util.MojangApiUtil;

import java.util.Locale;
import java.util.UUID;

public class UUIDBan extends Module {
    private final SettingGroup sgEntity = settings.createGroup("Entity");
    private final SettingGroup sgAction = settings.createGroup("Action");

    private final Setting<String> targetName = sgEntity.add(new StringSetting.Builder()
        .name("target").description("Player name to ban.")
        .defaultValue("").build());

    private final Setting<String> entityTypeStr = sgEntity.add(new StringSetting.Builder()
        .name("entity-type").description("Entity type to summon (e.g. minecraft:villager, minecraft:armor_stand).")
        .defaultValue("minecraft:villager").build());

    private final Setting<Boolean> glowing = sgEntity.add(new BoolSetting.Builder()
        .name("glowing").description("Make the ban entity glow.")
        .defaultValue(true).build());

    private final Setting<Boolean> kickFirst = sgAction.add(new BoolSetting.Builder()
        .name("kick-first").description("Kick the target before placing the egg.")
        .defaultValue(false).build());

    private final Setting<Boolean> debug = sgAction.add(new BoolSetting.Builder()
        .name("debug").description("Show resolution details.")
        .defaultValue(true).build());

    private boolean waitingForTick = false;
    private int tickCount = 0;
    private String resolvedName = null;
    private UUID resolvedUuid = null;

    public UUIDBan() {
        super(Orbiter.CATEGORY_OP, "uuid-ban",
            "Place a UUID entity spawn item in your hotbar. Works via creative inventory packets (no chat limit).");
    }

    @Override
    public void onActivate() {
        waitingForTick = false;
        tickCount = 0;
        resolvedName = null;
        resolvedUuid = null;

        String name = targetName.get();
        if (name == null || name.isBlank()) {
            warning("Enter a player name in the target setting.");
            return;
        }

        if (mc.player == null || mc.level == null || mc.player.connection == null) {
            warning("Join a world first.");
            return;
        }

        if (!mc.player.isCreative()) {
            warning("UUIDBan requires creative mode for the spawn item.");
            return;
        }

        for (var player : mc.level.players()) {
            if (player.getName().getString().equalsIgnoreCase(name)) {
                resolvedName = name;
                resolvedUuid = player.getUUID();
                break;
            }
        }

        if (resolvedUuid != null) {

            if (kickFirst.get()) sendKick(resolvedName);
            waitingForTick = true;
            tickCount = 0;
        } else {

            if (debug.get()) info("Resolving UUID for %s from Mojang...", name);
            MojangApiUtil.resolveAsync(name).thenAccept(uuidStr -> mc.execute(() -> {
                UUID uuid = MojangApiUtil.parseUuid(uuidStr);
                if (uuid == null) {
                    warning("Could not resolve UUID for " + name + ".");
                    toggle();
                    return;
                }
                resolvedName = name;
                resolvedUuid = uuid;
                if (kickFirst.get()) sendKick(resolvedName);
                waitingForTick = true;
                tickCount = 0;
            }));
        }
    }

    @Override
    public void onDeactivate() {
        waitingForTick = false;
        resolvedName = null;
        resolvedUuid = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!waitingForTick || resolvedUuid == null) return;
        tickCount++;
        if (tickCount >= 5) {
            waitingForTick = false;
            placeEgg(resolvedName, resolvedUuid);
            toggle();
        }
    }

    private void placeEgg(String name, UUID uuid) {
        if (mc.player == null || mc.player.connection == null) return;

        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        String uuidNbt = String.format("[I;%d,%d,%d,%d]",
            (int)(most >> 32), (int)most, (int)(least >> 32), (int)least);

        String entityNbt = String.format(Locale.ROOT,
            "{UUID:%s,NoAI:1b,Invulnerable:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b%s}",
            uuidNbt,
            glowing.get() ? ",Glowing:1b" : "");

        ItemStack egg = new ItemStack(Items.ARMOR_STAND);
        egg.set(DataComponents.CUSTOM_NAME, Component.literal("§c§lUUIDBan: " + name));

        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", entityTypeStr.get());
        entityData.putLong("UUIDMost", most);
        entityData.putLong("UUIDLeast", least);
        entityData.putBoolean("NoAI", true);
        entityData.putBoolean("Invulnerable", true);
        entityData.putBoolean("NoGravity", true);
        entityData.putBoolean("PersistenceRequired", true);
        entityData.putBoolean("Silent", true);
        if (glowing.get()) entityData.putBoolean("Glowing", true);

        egg.set(DataComponents.ENTITY_DATA,
            TypedEntityData.of(EntityTypes.ARMOR_STAND, entityData));

        int slot = mc.player.getInventory().getSelectedSlot();
        mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(36 + slot, egg));

        if (debug.get()) info("Placed UUIDBan egg in slot %d for %s (UUID: %s). Right-click to summon.",
            slot + 1, name, uuid);
    }

    private void sendKick(String name) {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand("kick " + name);
        if (debug.get()) info("Sent kick for " + name);
    }

    public void executeCommand(String username) {
        targetName.set(username);
        if (!isActive()) toggle();
        else onActivate();
    }

    public void cleanup() {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand("kill @e[tag=orbiter_uuidban]");
        info("Cleaned up UUIDBan entities.");
    }
}
