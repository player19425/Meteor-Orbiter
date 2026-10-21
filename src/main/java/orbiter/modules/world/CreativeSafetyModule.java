package orbiter.modules.world;

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
            .defaultValue(true)
            .build());

    protected final Setting<Integer> sharedSendLimit = sgSafety.add(new IntSetting.Builder()
            .name("global-commands-per-second")
            .description("Shared by every module, the last one you touch wins. 20 is the vanilla kick rate.")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 100)
            .onChanged(v -> GlobalSendLimiter.setPermitsPerSecond(v))
            .build());

    protected final Setting<Integer> frameTimeBudgetMs = sgSafety.add(new IntSetting.Builder()
            .name("frame-time-budget-ms")
            .description("Work is sliced across ticks, total throughput unchanged.")
            .defaultValue(4)
            .min(1)
            .sliderRange(1, 20)
            .visible(this::supportsFrameBudget)
            .build());

    protected final Setting<Boolean> suppressCommandFeedback = sgSafety.add(new BoolSetting.Builder()
            .name("suppress-command-feedback")
            .description("Some servers reject it. Restored to true, not to its previous value.")
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

    protected boolean supportsFrameBudget() {
        return false;
    }

    protected long sliceDeadline() {
        return System.nanoTime() + frameTimeBudgetMs.get() * 1_000_000L;
    }

    protected void safetyActivate() {
        NetworkOptimizer.ensureInstalled();
        GlobalSendLimiter.setPermitsPerSecond(sharedSendLimit.get());

        if (suppressCommandFeedback.get() && hasGameruleCommand()) {
            feedbackSuppressedByMe = true;
            if (FEEDBACK_USERS.getAndIncrement() == 0) sendFeedbackGamerule(false);
        }
    }

    protected void safetyDeactivate() {
        releaseFeedback(true);
    }

    private void releaseFeedback(boolean restore) {
        if (!feedbackSuppressedByMe) return;
        feedbackSuppressedByMe = false;
        if (FEEDBACK_USERS.decrementAndGet() <= 0 && restore) sendFeedbackGamerule(true);
    }

    private boolean hasGameruleCommand() {
        if (mc.player == null || mc.player.connection == null) return false;
        var dispatcher = mc.player.connection.getCommands();
        return dispatcher != null && dispatcher.getRoot() != null
                && dispatcher.getRoot().getChild("gamerule") != null;
    }

    private void sendFeedbackGamerule(boolean enabled) {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.sendCommand(
                CommandUtils.vanilla("gamerule send_command_feedback " + (enabled ? "true" : "false")));
    }
}
