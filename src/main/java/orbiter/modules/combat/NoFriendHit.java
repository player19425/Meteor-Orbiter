package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NoFriendHit extends Module {
    public enum Mode {
        AttackOnly,
        AllInteractions
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("AttackOnly blocks friend attacks. AllInteractions blocks every friend entity interaction packet.")
        .defaultValue(Mode.AttackOnly)
        .build());

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Show a chat message when a friend hit is blocked.")
        .defaultValue(true)
        .build());

    private int notifyCooldownTicks = 0;
    private Method packetGetEntityMethod;
    private boolean packetGetEntityLookupFailed = false;
    private Field packetEntityIdField;
    private boolean packetEntityIdLookupFailed = false;
    private Field packetTypeField;
    private boolean packetTypeLookupFailed = false;

    public NoFriendHit() {
        super(Orbiter.CATEGORY, "no-friend-hit", "Don't hit Meteor friends.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (notifyCooldownTicks > 0) notifyCooldownTicks--;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundInteractPacket packet)) return;

        if (!shouldBlockPacket(packet, false)) return;
        event.cancel();

        if (notify.get() && notifyCooldownTicks <= 0) {
            warning("Blocked friend hit.");
            notifyCooldownTicks = 20;
        }
    }

    public boolean shouldBlockPacket(ServerboundInteractPacket packet, boolean forceStrict) {
        if (mc.player == null || mc.level == null) return false;

        Entity target = resolvePacketEntity(packet);
        if (!(target instanceof Player playerTarget)) return false;
        if (Friends.get().shouldAttack(playerTarget)) return false;

        if (forceStrict || mode.get() == Mode.AllInteractions) return true;
        return isAttackPacket(packet);
    }

    private Entity resolvePacketEntity(ServerboundInteractPacket packet) {
        if (mc.level == null) return mc.crosshairPickEntity;

        Method method = getPacketGetEntityMethod();
        if (method != null) {
            try {
                Object result = method.invoke(packet, mc.level);
                if (result instanceof Entity entity) return entity;
            } catch (Throwable ignored) {
            }
        }

        Field idField = getPacketEntityIdField();
        if (idField != null) {
            try {
                int entityId = idField.getInt(packet);
                Entity entity = mc.level.getEntity(entityId);
                if (entity != null) return entity;
            } catch (Throwable ignored) {
            }
        }

        return mc.crosshairPickEntity;
    }

    private Method getPacketGetEntityMethod() {
        if (packetGetEntityLookupFailed) return null;
        if (packetGetEntityMethod != null) return packetGetEntityMethod;

        try {
            packetGetEntityMethod = ServerboundInteractPacket.class.getMethod("getEntity", Level.class);
            packetGetEntityMethod.setAccessible(true);
            return packetGetEntityMethod;
        } catch (Throwable ignored) {
            packetGetEntityLookupFailed = true;
            return null;
        }
    }

    private Field getPacketEntityIdField() {
        if (packetEntityIdLookupFailed) return null;
        if (packetEntityIdField != null) return packetEntityIdField;

        try {
            packetEntityIdField = ServerboundInteractPacket.class.getDeclaredField("entityId");
            packetEntityIdField.setAccessible(true);
            return packetEntityIdField;
        } catch (Throwable ignored) {
            packetEntityIdLookupFailed = true;
            return null;
        }
    }

    private boolean isAttackPacket(ServerboundInteractPacket packet) {
        Field typeField = getPacketTypeField();
        if (typeField != null) {
            try {
                Object action = typeField.get(packet);
                if (action != null) {
                    if (isAttackAction(action)) return true;
                    String name = action.getClass().getSimpleName().toLowerCase();
                    if (name.contains("interact")) return false;
                }
            } catch (Throwable ignored) {
            }
        }

        return mc.options != null && mc.options.keyAttack != null && mc.options.keyAttack.isDown();
    }

    private boolean isAttackAction(Object action) {
        try {
            Class<?> attackClass = Class.forName(ServerboundInteractPacket.class.getName() + "$AttackAction");
            if (attackClass.isInstance(action)) return true;
        } catch (Throwable ignored) {
        }
        return action.getClass().getSimpleName().toLowerCase().contains("attack");
    }

    private Field getPacketTypeField() {
        if (packetTypeLookupFailed) return null;
        if (packetTypeField != null) return packetTypeField;

        try {
            packetTypeField = ServerboundInteractPacket.class.getDeclaredField("action");
            packetTypeField.setAccessible(true);
            return packetTypeField;
        } catch (Throwable ignored) {
        }

        try {
            packetTypeField = ServerboundInteractPacket.class.getDeclaredField("type");
            packetTypeField.setAccessible(true);
            return packetTypeField;
        } catch (Throwable ignored) {
            packetTypeLookupFailed = true;
            return null;
        }
    }
}
