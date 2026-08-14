package orbiter.modules.render;

import orbiter.Orbiter;
import orbiter.util.ConfigModifier;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;

public class Camera360 extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSync   = settings.createGroup("Server Sync");

    private final Setting<Boolean> noLimitPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("unlock-pitch").description("Remove pitch (up/down) clamping (-90° to +90°).").defaultValue(true).build());

    private final Setting<Boolean> noLimitYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("unlock-yaw").description("Remove yaw wrapping (allows infinite yaw).").defaultValue(false).build());

    private final Setting<Boolean> invertMouse = sgGeneral.add(new BoolSetting.Builder()
        .name("invert-mouse").description("Invert mouse Y-axis.").defaultValue(false).build());

    private final Setting<Boolean> serverSync = sgSync.add(new BoolSetting.Builder()
        .name("server-sync").description("Send look packets to sync camera rotation to server (works on Paper).").defaultValue(true).build());

    private final Setting<Integer> syncInterval = sgSync.add(new IntSetting.Builder()
        .name("sync-interval").description("Ticks between server sync packets.").defaultValue(1).min(1).max(20).sliderRange(1, 10).visible(serverSync::get).build());

    private float rawPitch = 0f;
    private float rawYaw = 0f;
    private int syncTicks = 0;

    public Camera360() {
        super(Orbiter.CATEGORY_STUPID, "360-camera", "Removes camera rotation limits for full 360°+ movement.");
    }

    @Override
    public void onActivate() {
        if (!ConfigModifier.get().stupidModules.get()) {
            info("Stupid Modules is disabled. Enable it in Meteor Config → Orbiter → Stupid Modules");
            toggle();
            return;
        }
        if (mc.player != null) {
            rawPitch = mc.player.getPitch();
            rawYaw = mc.player.getYaw();
        }
        syncTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {

            mc.player.setPitch(MathHelper.clamp(mc.player.getPitch(), -90f, 90f));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (!ConfigModifier.get().stupidModules.get()) {
            info("Stupid Modules was disabled — 360 Camera auto-disabled.");
            toggle();
            return;
        }

        rawPitch = mc.player.getPitch();
        rawYaw = mc.player.getYaw();

        if (serverSync.get()) {
            syncTicks++;
            if (syncTicks >= syncInterval.get()) {
                syncTicks = 0;
                sendSyncPacket();
            }
        }
    }

    private void sendSyncPacket() {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        float syncYaw = MathHelper.wrapDegrees(rawYaw);
        float syncPitch = MathHelper.clamp(rawPitch, -90f, 90f);

        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(syncYaw, syncPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    public boolean shouldUnlockPitch() { return isActive() && noLimitPitch.get(); }
    public boolean shouldUnlockYaw()   { return isActive() && noLimitYaw.get(); }
    public boolean shouldInvertMouse() { return isActive() && invertMouse.get(); }
}
