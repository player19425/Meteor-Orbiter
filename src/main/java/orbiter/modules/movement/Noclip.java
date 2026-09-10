package orbiter.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.phys.Vec3;
import orbiter.Orbiter;

public class Noclip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal speed in blocks per tick.")
        .defaultValue(0.6)
        .min(0.1).max(5.0).sliderRange(0.1, 5.0)
        .build());

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical flight speed in blocks per tick.")
        .defaultValue(0.6)
        .min(0.1).max(3.0).sliderRange(0.1, 3.0)
        .build());

    private final Setting<Boolean> disableOnDamage = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-damage")
        .description("Begins disabling when you take damage.")
        .defaultValue(true)
        .build());

    private static Noclip instance;

    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean hasAnchor;
    private int dipTimer;
    private double currentDip;

    public Noclip() {
        super(Orbiter.CATEGORY, "noclip", "Flies through blocks. The server accepts the position claim that lands in open air on the far side. In survival, let a falling sand or gravel block land on your head first.");
        instance = this;
    }

    public static boolean isActiveStatic() {
        Noclip self = instance;
        return self != null && self.isActive();
    }

    @Override
    public void onActivate() {
        dipTimer = 0;
        if (mc.player != null) {
            anchorX = mc.player.getX();
            anchorY = mc.player.getY();
            anchorZ = mc.player.getZ();
            hasAnchor = true;
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.level != null && hasAnchor) {
            if (!mc.level.noCollision(mc.player, mc.player.getBoundingBox())) {
                mc.player.absSnapTo(anchorX, anchorY, anchorZ);
            }
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.fallDistance = 0;
        }
        hasAnchor = false;
        dipTimer = 0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        hasAnchor = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        mc.player.fallDistance = 0;

        if (disableOnDamage.get() && mc.player.hurtTime > 0) {
            toggle();
            return;
        }

        dipTimer++;
        if (dipTimer >= 55 && !mc.player.onGround() && !mc.options.keyShift.isDown()) {
            dipTimer = 0;
            currentDip = -0.5;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPlayerPositionPacket packet)) return;
        if (mc.player == null || mc.player.connection == null) return;

        mc.player.connection.send(new ServerboundAcceptTeleportationPacket(packet.id()));
        event.cancel();
    }

    public static Vec3 currentMotion(net.minecraft.client.player.LocalPlayer player) {
        Noclip self = instance;
        if (self == null || !self.isActive() || player != self.mc.player) return Vec3.ZERO;

        double forward = (self.mc.options.keyUp.isDown() ? 1 : 0) - (self.mc.options.keyDown.isDown() ? 1 : 0);
        double strafe = (self.mc.options.keyLeft.isDown() ? 1 : 0) - (self.mc.options.keyRight.isDown() ? 1 : 0);

        double yawRad = Math.toRadians(player.getYRot());
        double base = self.speed.get();
        double vx = 0;
        double vz = 0;

        if (forward != 0 || strafe != 0) {
            double mag = Math.sqrt(forward * forward + strafe * strafe);
            vx = (-Math.sin(yawRad) * forward + Math.cos(yawRad) * strafe) / mag * base;
            vz = (Math.cos(yawRad) * forward + Math.sin(yawRad) * strafe) / mag * base;
        }

        double vs = self.verticalSpeed.get();
        double vy = (self.mc.options.keyJump.isDown() ? vs : 0) + (self.mc.options.keyShift.isDown() ? -vs : 0);

        double dip = self.currentDip;
        self.currentDip = 0;
        vy += dip;

        return new Vec3(vx, vy, vz);
    }

    @Override
    public String getInfoString() {
        return String.format("%.1fx", speed.get());
    }
}
