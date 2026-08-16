package orbiter.modules.world;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import orbiter.util.MojangApiUtil;

import java.util.Optional;
import java.util.UUID;

public class UUIDBan extends Module {
    public enum Delivery { SummonCommand, SpawnEgg }

    private final SettingGroup sgEntity = settings.createGroup("Entity");
    private final SettingGroup sgAction = settings.createGroup("Action");

    private final Setting<String> targetName = sgEntity.add(new StringSetting.Builder()
        .name("target").description("Player name to ban.")
        .defaultValue("").build());

    private final Setting<String> entityTypeStr = sgEntity.add(new StringSetting.Builder()
        .name("entity-type").description("Entity type to summon. Uses its spawn egg when available, armor stand otherwise.")
        .defaultValue("minecraft:villager").build());

    private final Setting<Boolean> glowing = sgEntity.add(new BoolSetting.Builder()
        .name("glowing").description("Make the ban entity glow.")
        .defaultValue(true).build());

    private final Setting<Delivery> delivery = sgEntity.add(new EnumSetting.Builder<Delivery>()
        .name("delivery").description("How to spawn the ban entity. Summon Command applies the UUID on every server version; Spawn Egg only keeps the UUID on servers 1.21.4 and older (newer versions reset it).")
        .defaultValue(Delivery.SummonCommand).build());

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
            "Summon an entity carrying the target's UUID to lock them out of the server. Summon Command works on all versions; the spawn egg only keeps the UUID on 1.21.4 and older servers.");
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

        if (delivery.get() == Delivery.SpawnEgg && !mc.player.isCreative()) {
            warning("Spawn Egg delivery requires creative mode for the item.");
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

        if (delivery.get() == Delivery.SummonCommand) {
            summonViaCommand(name, uuid);
        } else {
            summonViaEgg(name, uuid);
        }
    }

    private void summonViaCommand(String name, UUID uuid) {
        if (mc.player == null || mc.player.connection == null) return;

        EntityType<?> type = resolveEntityType();
        if (type == null) {
            warning("Invalid entity type: " + entityTypeStr.get());
            return;
        }

        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        int[] uuidArray = uuidToIntArray(uuid);

        StringBuilder nbt = new StringBuilder();
        nbt.append("{UUID:[I;").append(uuidArray[0]).append(',').append(uuidArray[1]).append(',')
            .append(uuidArray[2]).append(',').append(uuidArray[3]).append(']');
        nbt.append(",NoAI:1b,Invulnerable:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b,Invisible:1b");
        if (glowing.get()) nbt.append(",Glowing:1b");
        nbt.append('}');

        String command = CommandUtils.vanilla("summon " + typeId + " ~ ~ ~ " + nbt);
        mc.player.connection.sendCommand(command);

        if (debug.get()) {
            info("Summoned %s with UUID of %s (%s).", typeId, name, uuid);
            info("If the target is online the server rejects the duplicate UUID. Kick them first and re-run so the entity can claim the UUID.");
        }
    }

    private void summonViaEgg(String name, UUID uuid) {
        if (mc.player == null || mc.player.connection == null) return;

        int[] uuidArray = uuidToIntArray(uuid);

        CompoundTag entityData = new CompoundTag();
        entityData.putIntArray("UUID", uuidArray);
        entityData.putBoolean("NoAI", true);
        entityData.putBoolean("Invulnerable", true);
        entityData.putBoolean("NoGravity", true);
        entityData.putBoolean("PersistenceRequired", true);
        entityData.putBoolean("Silent", true);
        entityData.putBoolean("Invisible", true);
        if (glowing.get()) entityData.putBoolean("Glowing", true);

        EntityType<?> type = resolveEntityType();
        ItemStack egg;

        if (type != null && type != EntityTypes.ARMOR_STAND) {
            Optional<Holder<Item>> eggHolder = SpawnEggItem.byId(type);
            if (eggHolder.isPresent()) {
                egg = new ItemStack(eggHolder.get());
                entityData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            } else {
                egg = new ItemStack(Items.ARMOR_STAND);
                type = EntityTypes.ARMOR_STAND;
            }
        } else {
            egg = new ItemStack(Items.ARMOR_STAND);
            type = EntityTypes.ARMOR_STAND;
        }

        egg.set(DataComponents.CUSTOM_NAME, Component.literal("§c§lUUIDBan: " + name));
        egg.set(DataComponents.ENTITY_DATA, TypedEntityData.of(type, entityData));

        int slot = mc.player.getInventory().getSelectedSlot();
        mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(36 + slot, egg));

        if (debug.get()) info("Placed UUIDBan egg in slot %d for %s (UUID: %s). Right-click to summon.",
            slot + 1, name, uuid);
    }

    private int[] uuidToIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[]{(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    private EntityType<?> resolveEntityType() {
        try {
            Identifier id = Identifier.tryParse(entityTypeStr.get());
            if (id == null) return null;
            return BuiltInRegistries.ENTITY_TYPE.get(id).map(Holder::value).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendKick(String name) {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand(CommandUtils.vanilla("kick " + name));
        if (debug.get()) info("Sent kick for " + name);
    }

    public void executeCommand(String username) {
        targetName.set(username);
        if (!isActive()) toggle();
        else onActivate();
    }

    public void cleanup() {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand(CommandUtils.vanilla("kill @e[tag=orbiter_uuidban]"));
        info("Cleaned up UUIDBan entities.");
    }
}
