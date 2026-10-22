package orbiter.modules.combat;

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
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class NoFriendHit extends Module {
    public enum Mode {
        AttackOnly,
        AllInteractions
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("AllInteractions also blocks interactions, not only attacks.")
        .defaultValue(Mode.AttackOnly)
        .build());

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Show a chat message when a friend hit is blocked.")
        .defaultValue(true)
        .build());

    private int notifyCooldownTicks = 0;

    public NoFriendHit() {
        super(Orbiter.CATEGORY, "no-friend-hit", "Don't hit Meteor friends.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (notifyCooldownTicks > 0) notifyCooldownTicks--;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundAttackPacket attack && isFriend(attack.entityId())) {
            event.cancel();
            blocked();
            return;
        }

        if (mode.get() == Mode.AllInteractions
            && event.packet instanceof ServerboundInteractPacket interact
            && isFriend(interact.entityId())) {
            event.cancel();
            blocked();
        }
    }

    public boolean shouldBlockPacket(ServerboundInteractPacket packet, boolean forceStrict) {
        if (packet == null || !isFriend(packet.entityId())) return false;
        return forceStrict || mode.get() == Mode.AllInteractions;
    }

    private boolean isFriend(int entityId) {
        if (mc.player == null || mc.level == null) return false;

        Entity target = mc.level.getEntity(entityId);
        return target instanceof Player playerTarget && !Friends.get().shouldAttack(playerTarget);
    }

    private void blocked() {
        if (!notify.get() || notifyCooldownTicks > 0) return;
        warning("Blocked friend hit.");
        notifyCooldownTicks = 20;
    }
}
