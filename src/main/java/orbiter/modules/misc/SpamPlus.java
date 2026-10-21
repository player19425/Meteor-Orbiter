package orbiter.modules.misc;

import orbiter.Orbiter;
import orbiter.util.GlobalSendLimiter;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SpamPlus extends Module {
    private static final int MAX_MESSAGE_LENGTH = 256;

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgBypass = settings.createGroup("Bypass");
    private final SettingGroup sgSplit = settings.createGroup("Split");
    private final SettingGroup sgLadder = settings.createGroup("Letter Ladder");

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Messages to spam.")
        .defaultValue(List.of("hello"))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between messages.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> random = sgGeneral.add(new BoolSetting.Builder()
        .name("random")
        .description("Shuffle message order.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableOnLeave = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-leave")
        .description("Disable when leaving server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-disconnect")
        .description("Disable on disconnect.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> commandPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("command-prefix")
        .description("Sent before every message, for example '/msg bob '.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> messageSuffix = sgGeneral.add(new StringSetting.Builder()
        .name("suffix")
        .description("Text appended to every message.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> startDelay = sgGeneral.add(new IntSetting.Builder()
        .name("start-delay")
        .description("Ticks to wait before sending the first message.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> randomDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("random-delay")
        .description("Use a random delay between messages instead of the fixed delay.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delayMin = sgGeneral.add(new IntSetting.Builder()
        .name("delay-min")
        .description("Minimum random delay in ticks.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 100)
        .visible(randomDelay::get)
        .build()
    );

    private final Setting<Integer> delayMax = sgGeneral.add(new IntSetting.Builder()
        .name("delay-max")
        .description("Maximum random delay in ticks.")
        .defaultValue(40)
        .min(0)
        .sliderRange(0, 200)
        .visible(randomDelay::get)
        .build()
    );

    private final Setting<Boolean> bypass = sgBypass.add(new BoolSetting.Builder()
        .name("bypass")
        .description("Add random suffix to bypass spam filter.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> bypassChars = sgBypass.add(new StringSetting.Builder()
        .name("bypass-chars")
        .description("Characters to use for the random bypass suffix.")
        .defaultValue("\u200B\u200C\u200D\uFEFF")
        .visible(bypass::get)
        .build()
    );

    private final Setting<Integer> bypassLength = sgBypass.add(new IntSetting.Builder()
        .name("bypass-length")
        .description("Number of random bypass characters to append.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 20)
        .visible(bypass::get)
        .build()
    );

    private final Setting<Boolean> uppercase = sgBypass.add(new BoolSetting.Builder()
        .name("uppercase")
        .description("Uppercase all messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSplit = sgSplit.add(new BoolSetting.Builder()
        .name("auto-split")
        .description("Split long messages automatically.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> splitLength = sgSplit.add(new IntSetting.Builder()
        .name("split-length")
        .description("Max chars per split.")
        .defaultValue(80)
        .min(10)
        .sliderRange(10, 256)
        .visible(autoSplit::get)
        .build()
    );

    private final Setting<Integer> splitDelay = sgSplit.add(new IntSetting.Builder()
        .name("split-delay")
        .description("Ticks between split parts.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 50)
        .visible(autoSplit::get)
        .build()
    );

    private final Setting<Boolean> ladder = sgLadder.add(new BoolSetting.Builder()
        .name("ladder")
        .description("Enable letter ladder mode • progressively appends a character.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> ladderChar = sgLadder.add(new StringSetting.Builder()
        .name("ladder-char")
        .description("Character to append each step.")
        .defaultValue("y")
        .visible(ladder::get)
        .build()
    );

    private final Setting<Integer> ladderCount = sgLadder.add(new IntSetting.Builder()
        .name("ladder-count")
        .description("Number of times to append the character.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(ladder::get)
        .build()
    );

    private final Setting<Boolean> ladderSeparate = sgLadder.add(new BoolSetting.Builder()
        .name("ladder-separate")
        .description("Send each ladder step as a separate message.")
        .defaultValue(true)
        .visible(ladder::get)
        .build()
    );

    private final Setting<Boolean> stupid = sgLadder.add(new BoolSetting.Builder()
        .name("stupid")
        .description("Enable stupid mode • repeats each message inline many times.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> stupidRepeat = sgLadder.add(new IntSetting.Builder()
        .name("stupid-repeat")
        .description("Number of times to duplicate each message inline.")
        .defaultValue(5)
        .min(2)
        .sliderRange(2, 30)
        .visible(stupid::get)
        .build()
    );

    private final Setting<Boolean> stupidExpand = sgLadder.add(new BoolSetting.Builder()
        .name("stupid-expand")
        .description("Each step duplicates the previous output, growing the message exponentially.")
        .defaultValue(true)
        .visible(stupid::get)
        .build()
    );

    private int timer;
    private int messageIndex;
    private int ladderIndex;
    private int stupidStep;
    private String pendingSplitText;
    private int splitNum;
    private List<String> orderedMessages;
    private final Random rng = new Random();

    public SpamPlus() {
        super(Orbiter.CATEGORY_STUPID, "spam-plus", "Chat spam with letter ladders.");
    }

    @Override
    public void onActivate() {
        timer = startDelay.get();
        messageIndex = 0;
        ladderIndex = 0;
        stupidStep = 0;
        splitNum = 0;
        pendingSplitText = null;
        rebuildOrderedMessages();
    }

    @Override
    public void onDeactivate() {
        pendingSplitText = null;
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (disableOnDisconnect.get() && event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disableOnLeave.get()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (messages.get().isEmpty()) return;

        if (pendingSplitText != null) {
            if (timer <= 0) {
                sendSplitPart();
            } else {
                timer--;
            }
            return;
        }

        if (timer <= 0) {
            if (stupid.get()) {
                tickStupid();
            } else if (ladder.get()) {
                tickLadder();
            } else {
                tickNormal();
            }
        } else {
            timer--;
        }
    }

    private void tickNormal() {
        if (orderedMessages.isEmpty()) rebuildOrderedMessages();
        if (orderedMessages.isEmpty()) return;

        if (!sendMessage(getNextMessage())) timer = nextDelay();
    }

    private void tickStupid() {
        if (orderedMessages.isEmpty()) {
            stupidStep = 0;
            return;
        }

        String base = orderedMessages.get(messageIndex);
        int repeat = stupidRepeat.get();

        String current = base;
        for (int s = 0; s <= stupidStep; s++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < repeat; i++) {
                sb.append(current);
                if (i < repeat - 1) sb.append(' ');
                if (sb.length() > MAX_MESSAGE_LENGTH) break;
            }
            current = sb.toString();
            if (current.length() > MAX_MESSAGE_LENGTH) {
                int cut = MAX_MESSAGE_LENGTH;
                if (Character.isHighSurrogate(current.charAt(cut - 1))) cut--;
                current = current.substring(0, cut);
                break;
            }
        }

        if (!sendMessage(current)) timer = nextDelay();

        if (stupidExpand.get()) {
            stupidStep++;
            int maxStep = 4;
            if (stupidStep > maxStep) {
                stupidStep = 0;
                advanceMessage();
            }
        } else {
            stupidStep = 0;
            advanceMessage();
        }
    }

    private void tickLadder() {
        if (orderedMessages.isEmpty()) return;

        String base = orderedMessages.get(messageIndex);
        String msg = base + ladderChar.get().repeat(ladderIndex);

        boolean splitStarted = false;
        if (ladderSeparate.get()) splitStarted = sendMessage(msg);

        ladderIndex++;

        if (ladderIndex >= ladderCount.get()) {
            if (!ladderSeparate.get()) splitStarted = sendMessage(joinLadderSteps(base));
            ladderIndex = 0;
            advanceMessage();
        }

        if (!splitStarted) timer = nextDelay();
    }

    private String joinLadderSteps(String base) {
        int count = ladderCount.get();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(base).append(ladderChar.get().repeat(i));
            if (i < count - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private String getNextMessage() {
        if (random.get()) {
            if (orderedMessages.isEmpty()) rebuildOrderedMessages();
            return orderedMessages.remove(0);
        }
        String msg = orderedMessages.get(messageIndex);
        messageIndex++;
        if (messageIndex >= orderedMessages.size()) {
            messageIndex = 0;
            rebuildOrderedMessages();
        }
        return msg;
    }

    private void advanceMessage() {
        messageIndex++;
        if (messageIndex >= orderedMessages.size()) {
            messageIndex = 0;
            if (random.get()) {
                rebuildOrderedMessages();
            }
        }
    }

    private void rebuildOrderedMessages() {
        orderedMessages = new ArrayList<>(messages.get());
        if (random.get()) Collections.shuffle(orderedMessages, rng);
    }

    private boolean sendMessage(String msg) {
        if (commandPrefix.get() != null && !commandPrefix.get().isEmpty()) msg = commandPrefix.get() + msg;
        if (messageSuffix.get() != null && !messageSuffix.get().isEmpty()) msg = msg + messageSuffix.get();
        if (uppercase.get()) msg = msg.toUpperCase(Locale.ROOT);
        if (bypass.get()) msg = msg + " " + randomBypassSuffix();

        if (autoSplit.get() && msg.length() > splitLength.get()) {
            startSplit(msg);
            return true;
        }

        sendNow(msg);
        return false;
    }

    private void startSplit(String msg) {
        pendingSplitText = msg;
        splitNum = 0;
        sendSplitPart();
    }

    private void sendSplitPart() {
        if (pendingSplitText == null) return;

        int chunk = Math.min(splitLength.get(), MAX_MESSAGE_LENGTH);
        int start = splitNum * chunk;
        int end = Math.min(start + chunk, pendingSplitText.length());
        int totalParts = (int) Math.ceil((double) pendingSplitText.length() / chunk);

        if (start < end && !sendNow(pendingSplitText.substring(start, end))) {
            timer = 1;
            return;
        }

        splitNum++;

        if (splitNum >= totalParts) {
            pendingSplitText = null;
            splitNum = 0;
            timer = nextDelay();
        } else {
            timer = splitDelay.get();
        }
    }

    private boolean sendNow(String msg) {
        if (mc.player == null || mc.getConnection() == null) return false;
        if (msg.length() > MAX_MESSAGE_LENGTH) msg = msg.substring(0, MAX_MESSAGE_LENGTH);
        if (!GlobalSendLimiter.tryAcquireOne()) return false;

        ChatUtils.sendPlayerMsg(msg, false);
        return true;
    }

    private int nextDelay() {
        if (randomDelay.get()) {
            int min = Math.min(delayMin.get(), delayMax.get());
            int max = Math.max(delayMin.get(), delayMax.get());
            return min == max ? min : min + rng.nextInt(max - min + 1);
        }
        return delay.get();
    }

    private String randomBypassSuffix() {
        String chars = bypassChars.get();
        if (chars == null || chars.isEmpty()) chars = "\u200B\u200C\u200D\uFEFF";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bypassLength.get(); i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
