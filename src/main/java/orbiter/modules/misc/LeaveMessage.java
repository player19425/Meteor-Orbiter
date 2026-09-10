package orbiter.modules.misc;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class LeaveMessage extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> leaveMessage = sgGeneral.add(new StringSetting.Builder()
        .name("leave-message")
        .description("Message or command sent before delayed disconnect. Use / for commands.")
        .defaultValue("/spawn")
        .build());

    private final Setting<Integer> delayMs = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ms")
        .description("Delay before disconnect after close intercept.")
        .defaultValue(1000)
        .min(100)
        .sliderRange(100, 10000)
        .build());

    private final Setting<Boolean> multiplayerOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("multiplayer-only")
        .description("Only intercept disconnect while connected to multiplayer.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> quitAfterDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("quit-after-disconnect")
        .description("Close the whole game after the delayed disconnect instead of staying in the title screen.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> notifyInChat = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-in-chat")
        .description("Show a local alert when the leave sequence starts.")
        .defaultValue(true)
        .build());

    private volatile boolean pendingLeave = false;
    private static volatile boolean quitRequested = false;

    public LeaveMessage() {
        super(Orbiter.CATEGORY, "leave-message", "Sends a chat message before leaving.");
    }

    @Override
    public void onActivate() {
        pendingLeave = false;
    }

    @Override
    public void onDeactivate() {
        pendingLeave = false;
    }

    public boolean onPlayerDisconnect() {
        return handleLeaveSequence("disconnect");
    }

    public boolean onScheduleStopIntercept() {
        return handleLeaveSequence("scheduleStop");
    }

    public boolean isPendingLeave() {
        return pendingLeave;
    }

    public static boolean shouldQuitAfterLeave() {
        if (!quitRequested) return false;
        quitRequested = false;
        return true;
    }

    private boolean handleLeaveSequence(String source) {
        if (!isActive()) return false;
        if (pendingLeave) return true;
        if (mc.player == null || mc.getConnection() == null) return false;
        if (multiplayerOnly.get() && (mc.hasSingleplayerServer() || mc.getCurrentServer() == null)) return false;

        pendingLeave = true;

        String msg = leaveMessage.get();
        if (msg != null && !msg.isBlank()) {
            if (msg.startsWith("/")) mc.player.connection.sendCommand(msg.substring(1));
            else mc.player.connection.sendChat(msg);
        }

        if (notifyInChat.get()) {
            info("[LeaveMessage] Intercepted " + source + ", disconnecting in " + delayMs.get() + "ms...");
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            mc.execute(this::disconnectNow);
        });

        return true;
    }

    private void disconnectNow() {
        pendingLeave = false;
        quitRequested = quitAfterDisconnect.get();
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal("[LeaveMessage] delayed disconnect"));
        }
    }
}
