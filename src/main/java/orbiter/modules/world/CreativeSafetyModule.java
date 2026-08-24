package orbiter.modules;

import orbiter.Orbiter;
import orbiter.util.CommandUtils;
import orbiter.util.GlobalSendLimiter;
import orbiter.util.NetworkOptimizer;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class CreativeSafetyModule extends Module {
    private static final AtomicInteger FEEDBACK_USERS = new AtomicInteger();

    protected final SettingGroup sgSafety = settings.createGroup("Safety");

    protected final Setting<Boolean> disableOnLeave = sgSafety.add(new BoolSetting.Builder()
            .name("disable-on-leave")
            .description("Disable this module automatically when you leave the server/world.")
            .defaultValue(true)
            .build());

    protected final Setting<Integer> sharedSendLimit = sgSafety.add(new IntSetting.Builder()
            .name("shared-send-limit")
            .description("Combined emergency cap of commands ALL Orbiter spam modules may send per tick. High enough by default to never throttle normal use; lower only to stop runaway configs.")
            .defaultValue(32768)
            .min(64)
            .sliderRange(64, 65536)
            .onChanged(GlobalSendLimiter::setPerTick)
            .build());

    protected final Setting<Integer> frameTimeBudgetMs = sgSafety.add(new IntSetting.Builder()
            .name("frame-time-budget-ms")
            .description("Maximum milliseconds per tick this module may spend building and sending commands. Spreads huge batches across ticks so a single frame doesn't freeze, without reducing total throughput.")
            .defaultValue(4)
            .min(1)
            .sliderRange(1, 20)
            .build());

    protected final Setting<Boolean> suppressCommandFeedback = sgSafety.add(new BoolSetting.Builder()
            .name("suppress-command-feedback")
            .description("Set send_command_feedback to false while active so command result messages (Filled blocks, Summoned, etc) don't flood your chat and lag the client. Restored to true when the last module stops. Off by default because some servers reject gamerule changes.")
            .defaultValue(false)
            .build());

    private boolean feedbackSuppressedByMe = false;

    protected CreativeSafetyModule(String name, String description) {
        super(Orbiter.CATEGORY_OP, name, description);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        releaseFeedback(false);

        if (!disableOnLeave.get() || !isActive()) return;

        info("Disconnected from world/server. " + title + " disabled by safety setting.");
        toggle();
    }

    protected long sliceDeadline() {
        return System.nanoTime() + frameTimeBudgetMs.get() * 1_000_000L;
    }

    protected void safetyActivate() {
        NetworkOptimizer.ensureInstalled();
        GlobalSendLimiter.setPerTick(sharedSendLimit.get());

        if (suppressCommandFeedback.get() && hasGameruleCommand()) {
            feedbackSuppressedByMe = true;
            if (FEEDBACK_USERS.getAndIncrement() == 0) sendFeedbackGamerule(false);
        }
    }

    protected void safetyDeactivate() {
        GlobalSendLimiter.setPerTick(sharedSendLimit.get());
        releaseFeedback(true);
    }

    private void releaseFeedback(boolean restore) {
        if (!feedbackSuppressedByMe) return;
        feedbackSuppressedByMe = false;
        if (restore && FEEDBACK_USERS.decrementAndGet() == 0) sendFeedbackGamerule(true);
    }

    private boolean hasGameruleCommand() {
        if (mc.player == null || mc.player.connection == null) return false;
        var dispatcher = mc.player.connection.getCommands();
        return dispatcher != null && dispatcher.getRoot() != null
                && dispatcher.getRoot().getChild("gamerule") != null;
    }

    private void sendFeedbackGamerule(boolean enabled) {
        mc.player.connection.sendCommand(
                CommandUtils.vanilla("gamerule send_command_feedback " + (enabled ? "true" : "false")));
    }
}
