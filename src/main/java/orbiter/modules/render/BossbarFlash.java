package orbiter.modules.render;

import orbiter.Orbiter;
import orbiter.modules.CreativeSafetyModule;
import orbiter.util.CommandUtils;
import orbiter.util.CommandBatcher;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;

import java.util.Random;
import java.util.Arrays;

public class BossbarFlash extends CreativeSafetyModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> barsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("bars-per-tick")
            .description("Number of boss bars to create per tick.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 20)
            .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks between bar creation bursts.")
            .defaultValue(2)
            .min(0)
            .sliderRange(0, 20)
            .build());

    private final Setting<Boolean> randomColors = sgGeneral.add(new BoolSetting.Builder()
            .name("random-colors")
            .description("Use random boss bar colors.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> randomTitles = sgGeneral.add(new BoolSetting.Builder()
            .name("random-titles")
            .description("Use random spam titles.")
            .defaultValue(true)
            .build());

    private final Setting<String> customTitle = sgGeneral.add(new StringSetting.Builder()
            .name("custom-title")
            .description("Custom title when random-titles is off.")
            .defaultValue("Orbiter On Crack!")
            .visible(() -> !randomTitles.get())
            .build());

    private final Setting<TitleColor> titleColor = sgGeneral.add(new EnumSetting.Builder<TitleColor>()
            .name("title-color")
            .description("Color code applied to all generated bossbar titles.")
            .defaultValue(TitleColor.None)
            .build());

    private final Setting<Boolean> titleBold = sgGeneral.add(new BoolSetting.Builder()
            .name("title-bold")
            .description("Apply bold formatting to titles.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> titleItalic = sgGeneral.add(new BoolSetting.Builder()
            .name("title-italic")
            .description("Apply italic formatting to titles.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> titleUnderline = sgGeneral.add(new BoolSetting.Builder()
            .name("title-underline")
            .description("Apply underline formatting to titles.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> titleStrikethrough = sgGeneral.add(new BoolSetting.Builder()
            .name("title-strikethrough")
            .description("Apply strikethrough formatting to titles.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> titleObfuscated = sgGeneral.add(new BoolSetting.Builder()
            .name("title-obfuscated")
            .description("Apply obfuscated formatting to titles.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> translateAmpersandCodes = sgGeneral.add(new BoolSetting.Builder()
            .name("translate-ampersand")
            .description("Translate '&' formatting codes in custom titles to section-sign codes.")
            .defaultValue(true)
            .visible(() -> !randomTitles.get())
            .build());

    private final Setting<Boolean> titleReset = sgGeneral.add(new BoolSetting.Builder()
            .name("title-reset")
            .description("Append reset code after each generated title.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> autoCleanup = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-cleanup")
            .description("Remove all created boss bars when module is disabled.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> cleanNow = sgGeneral.add(new BoolSetting.Builder()
            .name("clean-now")
            .description("Toggle on to immediately remove all Orbiter boss bars.")
            .defaultValue(false)
            .onChanged(this::onCleanNowChanged)
            .build());

    private final Setting<Boolean> cleanAllNow = sgGeneral.add(new BoolSetting.Builder()
            .name("clean-all-now")
            .description("Toggle on to remove every Orbiter bossbar slot if any are active.")
            .defaultValue(false)
            .onChanged(this::onCleanAllNowChanged)
            .build());

    private final Setting<Integer> maxBars = sgGeneral.add(new IntSetting.Builder()
            .name("max-bars")
            .description("Maximum number of boss bars before recycling.")
            .defaultValue(50)
            .min(5)
            .sliderRange(5, 200)
            .build());

    private final Setting<Integer> maxCommandsPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("max-bossbar-commands-per-tick")
            .description("Hard outgoing command budget for updates and cleanup.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 20)
            .build());

    private static final String[] COLORS = { "blue", "green", "pink", "purple", "red", "white", "yellow" };
    private static final String[] STYLES = { "progress", "notched_6", "notched_10", "notched_12", "notched_20" };
    private static final String[] SPAM_TITLES = {
            "Orbiter On Crack!", "AAAAAAAA", "OWNED",
            "Get Rekt", "Orbiter", "BOOM",
            "EZ", "GG", "LMAO",
            "???", "Destroyed"
    };

    private final Random random = new Random();
    private int tickCounter = 0;
    private int barCounter = 0;
    private boolean[] createdBars = new boolean[0];
    private BossbarState[] states = new BossbarState[0];
    private final CommandBatcher commandBatcher = new CommandBatcher(3);

    private static final class BossbarState {
        String title;
        String color;
        String style;
        int value = -1;
        boolean players;
        boolean visible;
    }

    public BossbarFlash() {
        super("bossbar-flash",
                "Rapidly creates/updates boss bars with random colors and titles. OP required.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        barCounter = 0;
        createdBars = new boolean[Math.max(1, maxBars.get())];
        states = new BossbarState[createdBars.length];
        commandBatcher.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.networkHandler == null)
            return;

        commandBatcher.setBudgetPerTick(maxCommandsPerTick.get());
        commandBatcher.drain(mc.player.networkHandler::sendChatCommand);

        tickCounter++;
        if (tickCounter < delay.get())
            return;
        tickCounter = 0;

        if (createdBars.length != Math.max(1, maxBars.get())) {
            createdBars = new boolean[Math.max(1, maxBars.get())];
            states = new BossbarState[createdBars.length];
        }

        for (int i = 0; i < barsPerTick.get(); i++) {
            int barIndex = barCounter % Math.max(1, maxBars.get());
            String barId = "orbiter:bar_" + barIndex;

            String color = randomColors.get() ? COLORS[random.nextInt(COLORS.length)] : "red";
            String style = STYLES[random.nextInt(STYLES.length)];
            String title = randomTitles.get() ? SPAM_TITLES[random.nextInt(SPAM_TITLES.length)] : customTitle.get();
            String titleJson = buildTitleJson(title);
            int value = random.nextInt(101);

            BossbarState state = states[barIndex];
            if (state == null) states[barIndex] = state = new BossbarState();

            if (!createdBars[barIndex]) {
                queue(barIndex, "add", CommandUtils.formatCommand("bossbar add %s %s", barId, titleJson));
                createdBars[barIndex] = true;
                state.title = titleJson;
            } else if (!titleJson.equals(state.title)) {
                queue(barIndex, "name", CommandUtils.formatCommand("bossbar set %s name %s", barId, titleJson));
                state.title = titleJson;
            }

            if (!color.equals(state.color)) { queue(barIndex, "color", CommandUtils.formatCommand("bossbar set %s color %s", barId, color)); state.color = color; }
            if (!style.equals(state.style)) { queue(barIndex, "style", CommandUtils.formatCommand("bossbar set %s style %s", barId, style)); state.style = style; }
            if (value != state.value) { queue(barIndex, "value", CommandUtils.formatCommand("bossbar set %s value %d", barId, value)); state.value = value; }
            if (!state.players) { queue(barIndex, "players", CommandUtils.formatCommand("bossbar set %s players @a", barId)); state.players = true; }
            if (!state.visible) { queue(barIndex, "visible", CommandUtils.formatCommand("bossbar set %s visible true", barId)); state.visible = true; }

            barCounter++;
        }
    }

    @Override
    public void onDeactivate() {
        commandBatcher.clear();
        if (autoCleanup.get() && mc.player != null && mc.player.networkHandler != null) {
            int removed = 0;
            for (int i = 0; i < createdBars.length; i++) {
                if (!createdBars[i]) continue;
                mc.player.networkHandler.sendChatCommand("bossbar remove orbiter:bar_" + i);
                removed++;
            }
            if (removed > 0) info("Cleaned up " + removed + " tracked boss bars.");
        }
        createdBars = new boolean[Math.max(1, maxBars.get())];
        states = new BossbarState[createdBars.length];
    }

    private void onCleanNowChanged(Boolean value) {
        if (!value) return;
        cleanBossBars(false);
        if (cleanNow.get()) cleanNow.set(false);
    }

    private void onCleanAllNowChanged(Boolean value) {
        if (!value) return;
        cleanBossBars(true);
        if (cleanAllNow.get()) cleanAllNow.set(false);
    }

    private void cleanBossBars(boolean fullSlotsClean) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        if (!hasTrackedBars()) {
            info("No Orbiter boss bars to clean.");
            return;
        }

        int slotCount = Math.max(1, maxBars.get());
        int removed = 0;

        if (fullSlotsClean) {
            for (int i = 0; i < slotCount; i++) {
                queue(i, "remove", "bossbar remove orbiter:bar_" + i);
                if (i < createdBars.length && createdBars[i]) removed++;
            }
        } else {
            int limit = Math.min(createdBars.length, slotCount);
            for (int i = 0; i < limit; i++) {
                if (!createdBars[i]) continue;
                queue(i, "remove", "bossbar remove orbiter:bar_" + i);
                removed++;
            }
        }

        createdBars = new boolean[slotCount];
        states = new BossbarState[slotCount];
        if (fullSlotsClean) info("Forced full bossbar cleanup.");
        else info("Cleaned up " + removed + " boss bars.");
    }

    private void queue(int index, String field, String command) {
        commandBatcher.offer(new CommandBatcher.Step("bossbar-flash", index + ":" + field, 0, command));
    }

    private boolean hasTrackedBars() {
        for (boolean createdBar : createdBars) {
            if (createdBar) return true;
        }
        return false;
    }

    private String buildTitleJson(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle;
        if (translateAmpersandCodes.get()) title = title.replace("\\u00a7", "&").replace("\\u00A7", "&");
        title = CommandUtils.stripLegacyFormatting(title);

        StringBuilder json = new StringBuilder(96);
        json.append('{');
        json.append("\"text\":\"").append(CommandUtils.escapeJson(title)).append('"');
        if (titleColor.get() != TitleColor.None) {
            json.append(",\"color\":\"").append(titleColor.get().jsonName).append('"');
        }
        if (titleBold.get()) json.append(",\"bold\":true");
        if (titleItalic.get()) json.append(",\"italic\":true");
        if (titleUnderline.get()) json.append(",\"underlined\":true");
        if (titleStrikethrough.get()) json.append(",\"strikethrough\":true");
        if (titleObfuscated.get()) json.append(",\"obfuscated\":true");
        json.append('}');
        return json.toString();
    }

    public enum TitleColor {
        None(""),
        Black("0"),
        DarkBlue("1"),
        DarkGreen("2"),
        DarkAqua("3"),
        DarkRed("4"),
        DarkPurple("5"),
        Gold("6"),
        Gray("7"),
        DarkGray("8"),
        Blue("9"),
        Green("a"),
        Aqua("b"),
        Red("c"),
        LightPurple("d"),
        Yellow("e"),
        White("f");

        private final String code;
        private final String jsonName;

        TitleColor(String code) {
            this(code, switch (code) {
                case "0" -> "black";
                case "1" -> "dark_blue";
                case "2" -> "dark_green";
                case "3" -> "dark_aqua";
                case "4" -> "dark_red";
                case "5" -> "dark_purple";
                case "6" -> "gold";
                case "7" -> "gray";
                case "8" -> "dark_gray";
                case "9" -> "blue";
                case "a" -> "green";
                case "b" -> "aqua";
                case "c" -> "red";
                case "d" -> "light_purple";
                case "e" -> "yellow";
                case "f" -> "white";
                default -> "white";
            });
        }

        TitleColor(String code, String jsonName) {
            this.code = code;
            this.jsonName = jsonName;
        }
    }
}
