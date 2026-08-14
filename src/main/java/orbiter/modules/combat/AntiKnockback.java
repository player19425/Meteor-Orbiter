package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

public class AntiKnockback extends Module {
    public enum Mode {
        Cancel,
        Multiplier
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to handle knockback velocity packets.")
        .defaultValue(Mode.Cancel)
        .build());

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal")
        .description("Horizontal multiplier for Multiplier mode.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .visible(() -> mode.get() == Mode.Multiplier)
        .build());

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical")
        .description("Vertical multiplier for Multiplier mode.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .visible(() -> mode.get() == Mode.Multiplier)
        .build());

    public AntiKnockback() {
        super(Orbiter.CATEGORY, "anti-knockback", "Handles knockback only.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isActive() || mc.player == null) return;
        var player = mc.player;

        if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() != player.getId()) return;
            event.cancel();

            if (mode.get() == Mode.Cancel) {
                mc.execute(() -> {
                    if (mc.player != player) return;
                    player.setVelocity(0.0, 0.0, 0.0);
                });
                return;
            }

            double vx = packet.getVelocity().x * horizontal.get();
            double vy = packet.getVelocity().y * vertical.get();
            double vz = packet.getVelocity().z * horizontal.get();
            mc.execute(() -> {
                if (mc.player != player) return;
                player.setVelocity(vx, vy, vz);
            });
        }
    }
}
