package orbiter.modules;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatter extends Module {

    private final SettingGroup sgGeneral          = settings.getDefaultGroup();
    private final SettingGroup sgCodes            = settings.createGroup("Codes");
    private final SettingGroup sgGradient         = settings.createGroup("Gradient");
    private final SettingGroup sgFonts            = settings.createGroup("Fonts");
    private final SettingGroup sgZalgo             = settings.createGroup("Zalgo");
    private final SettingGroup sgCharInsertion     = settings.createGroup("Character Insertion");
    private final SettingGroup sgRainbow           = settings.createGroup("Rainbow");
    private final SettingGroup sgLeetSpeak         = settings.createGroup("Leet Speak");
    private final SettingGroup sgAntiCensor        = settings.createGroup("Anti-Censor");
    private final SettingGroup sgPerCharColors     = settings.createGroup("Per-Char Colors");

    private final Setting<Boolean> formatSlashCommands  = sgGeneral.add(new BoolSetting.Builder()
        .name("format-slash-commands").description("Apply formatting to /commands.").defaultValue(false).build());
    private final Setting<Boolean> formatMeteorCommands  = sgGeneral.add(new BoolSetting.Builder()
        .name("format-meteor-commands").description("Apply formatting to Meteor commands.").defaultValue(false).build());
    private final Setting<Boolean> appendReset           = sgGeneral.add(new BoolSetting.Builder()
        .name("append-reset").description("Append §r at the end of formatted messages.").defaultValue(true).build());
    private final Setting<Boolean> sanitizeSectionCodes  = sgGeneral.add(new BoolSetting.Builder()
        .name("sanitize-section-codes").description("Converts § formatting codes to & before the message leaves the client. Vanilla servers disconnect you with 'Illegal characters in chat' when a raw section sign is sent in chat or commands.").defaultValue(true).build());
    private final Setting<Boolean> randomMessage         = sgGeneral.add(new BoolSetting.Builder()
        .name("random-message").description("Append random characters to bypass spam filters.").defaultValue(false).build());
    private final Setting<Boolean> previewEnabled        = sgGeneral.add(new BoolSetting.Builder()
        .name("preview-enabled").description("Preview formatted message in chat.").defaultValue(true).build());
    private final Setting<Boolean> appendTimestamp        = sgGeneral.add(new BoolSetting.Builder()
        .name("append-timestamp").description("Append timestamp to messages.").defaultValue(false).build());
    private final Setting<Integer> repeatCount           = sgGeneral.add(new IntSetting.Builder()
        .name("repeat-count").description("Number of times to repeat the message.").defaultValue(1).min(1).max(10).sliderRange(1, 10).build());

    private final Setting<String> prefix                 = sgGeneral.add(new StringSetting.Builder()
        .name("prefix").description("Text prepended to every formatted message. Kept raw (no formatting). Can contain spaces, e.g. '/team chat ' to send the message into that command.").defaultValue("").build());
    private final Setting<String> suffix                 = sgGeneral.add(new StringSetting.Builder()
        .name("suffix").description("Text appended after every formatted message. Kept raw (no formatting).").defaultValue("").build());

    private final Setting<CodeOutputMode> codeOutputMode = sgCodes.add(new EnumSetting.Builder<CodeOutputMode>()
        .name("code-output-mode").description("Use § or & for color codes.").defaultValue(CodeOutputMode.Section).build());
    private final Setting<String> customPrefixCodes      = sgCodes.add(new StringSetting.Builder()
        .name("custom-prefix-codes").description("Custom prefix for color codes.").defaultValue("").build());
    private final Setting<QuickPreset> quickPreset       = sgCodes.add(new EnumSetting.Builder<QuickPreset>()
        .name("quick-preset").description("Quick color/format preset.").defaultValue(QuickPreset.None).build());
    private final Setting<ChatColor> baseColor           = sgCodes.add(new EnumSetting.Builder<ChatColor>()
        .name("base-color").description("Base color for messages.").defaultValue(ChatColor.None).build());
    private final Setting<Boolean> bold                   = sgCodes.add(new BoolSetting.Builder()
        .name("bold").description("Apply bold formatting.").defaultValue(false).build());
    private final Setting<Boolean> italic                 = sgCodes.add(new BoolSetting.Builder()
        .name("italic").description("Apply italic formatting.").defaultValue(false).build());
    private final Setting<Boolean> underline              = sgCodes.add(new BoolSetting.Builder()
        .name("underline").description("Apply underline formatting.").defaultValue(false).build());
    private final Setting<Boolean> strikethrough          = sgCodes.add(new BoolSetting.Builder()
        .name("strikethrough").description("Apply strikethrough formatting.").defaultValue(false).build());
    private final Setting<Boolean> obfuscated             = sgCodes.add(new BoolSetting.Builder()
        .name("obfuscated").description("Apply obfuscated formatting.").defaultValue(false).build());

    private final Setting<Boolean> gradientEnabled       = sgGradient.add(new BoolSetting.Builder()
        .name("enabled").description("Enable gradient coloring.").defaultValue(false).build());
    private final Setting<String> gradientStartColor     = sgGradient.add(new StringSetting.Builder()
        .name("start-color").description("Gradient start color (hex).").defaultValue("#55FF55").build());
    private final Setting<String> gradientEndColor       = sgGradient.add(new StringSetting.Builder()
        .name("end-color").description("Gradient end color (hex).").defaultValue("#FFFFFF").build());
    private final Setting<Boolean> gradientSkipSpaces    = sgGradient.add(new BoolSetting.Builder()
        .name("skip-spaces").description("Skip gradient on space characters.").defaultValue(true).build());

    private final Setting<FontPreset> fontPreset          = sgFonts.add(new EnumSetting.Builder<FontPreset>()
        .name("font-preset").description("Font preset to apply.").defaultValue(FontPreset.None).build());
    private final Setting<String> customAlphabet          = sgFonts.add(new StringSetting.Builder()
        .name("custom-alphabet").description("Custom alphabet mapping for font replacement.").defaultValue("")
        .visible(() -> fontPreset.get() == FontPreset.Custom).build());

    private final Setting<Boolean> zalgoEnabled           = sgZalgo.add(new BoolSetting.Builder()
        .name("zalgo-enabled").description("Enable Zalgo text.").defaultValue(false).build());
    private final Setting<Integer> zalgoIntensity        = sgZalgo.add(new IntSetting.Builder()
        .name("zalgo-intensity").description("Number of Zalgo combining marks per character.").defaultValue(3).min(1).max(15).sliderRange(1, 15).build());
    private final Setting<Boolean> zalgoAbove            = sgZalgo.add(new BoolSetting.Builder()
        .name("zalgo-above").description("Add Zalgo marks above characters.").defaultValue(true).build());
    private final Setting<Boolean> zalgoMiddle            = sgZalgo.add(new BoolSetting.Builder()
        .name("zalgo-middle").description("Add Zalgo marks in the middle of characters.").defaultValue(false).build());
    private final Setting<Boolean> zalgoBelow             = sgZalgo.add(new BoolSetting.Builder()
        .name("zalgo-below").description("Add Zalgo marks below characters.").defaultValue(true).build());

    private final Setting<Boolean> insertEnabled         = sgCharInsertion.add(new BoolSetting.Builder()
        .name("insert-enabled").description("Enable character insertion.").defaultValue(false).build());
    private final Setting<String> insertCharacters       = sgCharInsertion.add(new StringSetting.Builder()
        .name("characters").description("Characters to insert.").defaultValue("⭐").build());
    private final Setting<Integer> insertEveryN          = sgCharInsertion.add(new IntSetting.Builder()
        .name("insert-every-n").description("Insert character every N characters (0=only between words).").defaultValue(0).min(0).max(20).sliderRange(0, 20).build());

    private final Setting<Boolean> rainbow               = sgRainbow.add(new BoolSetting.Builder()
        .name("rainbow").description("Cycle through colors per character in rainbow order.").defaultValue(false).build());

    private final Setting<Boolean> leetSpeak              = sgLeetSpeak.add(new BoolSetting.Builder()
        .name("leet-speak").description("Convert letters to leet speak (a→4, e→3, i→1, etc.).").defaultValue(false).build());

    private final Setting<Boolean> antiCensor             = sgAntiCensor.add(new BoolSetting.Builder()
        .name("anti-censor").description("Insert zero-width spaces between every character.").defaultValue(false).build());

    private final Setting<String> charColorSequence       = sgPerCharColors.add(new StringSetting.Builder()
        .name("char-color-sequence").description("Color codes to cycle per character (e.g. '4c6e9').").defaultValue("").build());

    private final Setting<Boolean> reverseText             = sgGeneral.add(new BoolSetting.Builder()
        .name("reverse-text").description("Reverse the message text.").defaultValue(false).build());
    private final Setting<Boolean> morseCode              = sgGeneral.add(new BoolSetting.Builder()
        .name("morse-code").description("Convert message to Morse code.").defaultValue(false).build());
    private final Setting<Boolean> scrambleWords           = sgGeneral.add(new BoolSetting.Builder()
        .name("scramble-words").description("Scramble internal letters of each word (keeps first/last).").defaultValue(false).build());

    public enum CodeOutputMode {
        Ampersand("&"),
        Section("§");

        private final String symbol;
        CodeOutputMode(String symbol) { this.symbol = symbol; }
        public String getSymbol() { return symbol; }
    }

    public enum ChatColor {
        None("none", '0', false),
        Black("black", '0', true),
        DarkBlue("dark-blue", '1', true),
        DarkGreen("dark-green", '2', true),
        DarkAqua("dark-aqua", '3', true),
        DarkRed("dark-red", '4', true),
        DarkPurple("dark-purple", '5', true),
        Gold("gold", '6', true),
        Gray("gray", '7', true),
        DarkGray("dark-gray", '8', true),
        Blue("blue", '9', true),
        Green("green", 'a', true),
        Aqua("aqua", 'b', true),
        Red("red", 'c', true),
        LightPurple("light-purple", 'd', true),
        Yellow("yellow", 'e', true),
        White("white", 'f', true);

        private final String name;
        private final char code;
        private final boolean hasCode;
        ChatColor(String name, char code, boolean hasCode) {
            this.name = name;
            this.code = code;
            this.hasCode = hasCode;
        }
        public char getCode() { return code; }
        public boolean hasCode() { return hasCode; }
        public String format(String prefix) { return hasCode ? prefix + code : ""; }
    }

    public enum QuickPreset {
        None, Green, Aqua, Red, Yellow, Bold, Obfuscated, Reset
    }

    public enum FontPreset {
        None, SmallCaps, FullWidth, Circle, Script, DoubleStruck, Fraktur, Monospace, Squared, UpsideDown, Custom
    }

    private static final Map<Character, Character> SMALL_CAPS = new HashMap<>();
    private static final Map<Character, Character> CIRCLE      = new HashMap<>();
    private static final Map<Character, Character> SCRIPT      = new HashMap<>();
    private static final Map<Character, Character> DOUBLE_STRUCK = new HashMap<>();
    private static final Map<Character, Character> FRAKTUR     = new HashMap<>();
    private static final Map<Character, Character> MONOSPACE   = new HashMap<>();
    private static final Map<Character, Character> SQUARED    = new HashMap<>();
    private static final Map<Character, Character> UPSIDE_DOWN = new HashMap<>();

    private static final char[] ZALGO_ABOVE = {
        '\u0300', '\u0301', '\u0302', '\u0303', '\u0304', '\u0305', '\u0306', '\u0307',
        '\u0308', '\u0309', '\u030A', '\u030B', '\u030C', '\u030D', '\u030E', '\u030F',
        '\u0310', '\u0311', '\u0312', '\u0313', '\u0314', '\u0315', '\u031A', '\u031B',
        '\u033D', '\u033E', '\u033F', '\u0340', '\u0341', '\u0342', '\u0343', '\u0344',
        '\u0346', '\u034A', '\u034B', '\u034C', '\u0350', '\u0351', '\u0352', '\u0357',
        '\u035B', '\u0363', '\u0364', '\u0365', '\u0366', '\u0367', '\u0368', '\u0369',
        '\u036A', '\u036B', '\u036C', '\u036D', '\u036E', '\u036F'
    };
    private static final char[] ZALGO_MIDDLE = {
        '\u0315', '\u031B', '\u0340', '\u0341', '\u0358', '\u035B', '\u035C', '\u035D',
        '\u035E', '\u035F', '\u0360', '\u0361', '\u0362'
    };
    private static final char[] ZALGO_BELOW = {
        '\u0316', '\u0317', '\u0318', '\u0319', '\u031C', '\u031D', '\u031E', '\u031F',
        '\u0320', '\u0321', '\u0322', '\u0323', '\u0324', '\u0325', '\u0326', '\u0327',
        '\u0328', '\u0329', '\u032A', '\u032B', '\u032C', '\u032D', '\u032E', '\u032F',
        '\u0330', '\u0331', '\u0332', '\u0333', '\u0334', '\u0335', '\u0336', '\u0337',
        '\u0338', '\u0339', '\u033A', '\u033B', '\u033C', '\u0345', '\u0347', '\u0348',
        '\u0349', '\u034D', '\u034E', '\u034F', '\u0353', '\u0354', '\u0355', '\u0356',
        '\u0359', '\u035A'
    };

    private static final Map<Character, String> MORSE = new HashMap<>();
    static {
        MORSE.put('a', ".-");   MORSE.put('b', "-..."); MORSE.put('c', "-.-."); MORSE.put('d', "-..");
        MORSE.put('e', ".");    MORSE.put('f', "..-."); MORSE.put('g', "--.");  MORSE.put('h', "....");
        MORSE.put('i', "..");   MORSE.put('j', ".---"); MORSE.put('k', "-.-");   MORSE.put('l', ".-..");
        MORSE.put('m', "--");   MORSE.put('n', "-.");   MORSE.put('o', "---");   MORSE.put('p', ".--.");
        MORSE.put('q', "--.-"); MORSE.put('r', ".-.");  MORSE.put('s', "...");   MORSE.put('t', "-");
        MORSE.put('u', "..-");  MORSE.put('v', "...-"); MORSE.put('w', ".--");   MORSE.put('x', "-..-");
        MORSE.put('y', "-.--"); MORSE.put('z', "--..");
        MORSE.put('0', "-----"); MORSE.put('1', ".----"); MORSE.put('2', "..---");
        MORSE.put('3', "...--"); MORSE.put('4', "....-"); MORSE.put('5', ".....");
        MORSE.put('6', "-...."); MORSE.put('7', "--..."); MORSE.put('8', "---..");
        MORSE.put('9', "----.");
    }

    private static final ChatColor[] RAINBOW_ORDER = {
        ChatColor.Red, ChatColor.Gold, ChatColor.Yellow, ChatColor.Green,
        ChatColor.Aqua, ChatColor.Blue, ChatColor.LightPurple
    };

    private final Random random = new Random();
    private transient boolean isFormatting = false;

    private static final class PendingRepeat {
        final String message;
        final int remaining;
        final int tickInterval;
        final long targetTick;
        PendingRepeat(String message, int remaining, int tickInterval, long targetTick) {
            this.message = message;
            this.remaining = remaining;
            this.tickInterval = tickInterval;
            this.targetTick = targetTick;
        }
    }
    private final ConcurrentLinkedQueue<PendingRepeat> pendingRepeats = new ConcurrentLinkedQueue<>();
    private long repeatTickClock = 0;

    public MessageFormatter() {
        super(Orbiter.CATEGORY, "message-formatter", "Formats outgoing chat.");
        initFontTables();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        repeatTickClock++;
        if (pendingRepeats.isEmpty()) return;
        ClientPacketListener handler = mc.getConnection();
        if (handler == null) { pendingRepeats.clear(); return; }

        List<PendingRepeat> ready = new ArrayList<>();
        for (PendingRepeat pr = pendingRepeats.peek(); pr != null && pr.targetTick <= repeatTickClock; pr = pendingRepeats.peek()) {
            ready.add(pendingRepeats.poll());
        }
        for (PendingRepeat pr : ready) {
            if (mc.getConnection() == null) { continue; }
            String toSend = sanitizeOutgoing(truncateSafe(pr.message, 256));
            handler.sendChat(toSend);

            if (pr.remaining > 1) {
                pendingRepeats.add(new PendingRepeat(pr.message, pr.remaining - 1, pr.tickInterval, repeatTickClock + pr.tickInterval));
            }
        }
    }

    private void initFontTables() {

        String scLower = "abcdefghijklmnopqrstuvwxyz";
        String scUpper = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        for (int i = 0; i < 26; i++) {
            SMALL_CAPS.put(scLower.charAt(i), scUpper.charAt(i));
            SMALL_CAPS.put(scUpper.charAt(i), scUpper.charAt(i));
        }

        for (char c = 'a'; c <= 'z'; c++) {  }

        String circleLower = "abcdefghijklmnopqrstuvwxyz";
        String circleUpper = "ⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ";
        for (int i = 0; i < 26; i++) {
            CIRCLE.put(circleLower.charAt(i), circleUpper.charAt(i));
            CIRCLE.put(Character.toUpperCase(circleLower.charAt(i)), circleUpper.charAt(i));
        }

        String scriptLower = "abcdefghijklmnopqrstuvwxyz";
        String scriptLowerMap = "𝒶𝒷𝒸𝒹ℯ𝒻ℊ𝒽𝒾𝒿𝓀𝓁𝓂𝓃ℴ𝓅𝓆𝓇𝓈𝓉𝓊𝓋𝓌𝓍𝓎𝓏";
        for (int i = 0; i < 26; i++) {
            SCRIPT.put(scriptLower.charAt(i), scriptLowerMap.charAt(i));
        }
        String scriptUpper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String scriptUpperMap = "𝒜ℬ𝒞𝒟ℰℱ𝒢ℋℐ𝒥𝒦ℒℳ𝒩𝒪𝒫𝒬ℛ𝒮𝒯𝒰𝒱𝒲𝒳𝒴𝒵";
        for (int i = 0; i < 26; i++) {
            SCRIPT.put(scriptUpper.charAt(i), scriptUpperMap.charAt(i));
        }

        String dsLower = "abcdefghijklmnopqrstuvwxyz";
        String dsLowerMap = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫";
        for (int i = 0; i < 26; i++) {
            DOUBLE_STRUCK.put(dsLower.charAt(i), dsLowerMap.charAt(i));
        }
        String dsUpper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String dsUpperMap = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ";
        for (int i = 0; i < 26; i++) {
            DOUBLE_STRUCK.put(dsUpper.charAt(i), dsUpperMap.charAt(i));
        }

        String frakturLower = "abcdefghijklmnopqrstuvwxyz";
        String frakturLowerMap = "𝔞𝔟𝔠𝔡𝔢𝔣𝔤𝔥𝔦𝔧𝔨𝔩𝔪𝔫𝔬𝔭𝔮𝔯𝔰𝔱𝔲𝔳𝔴𝔵𝔶𝔷";
        for (int i = 0; i < 26; i++) {
            FRAKTUR.put(frakturLower.charAt(i), frakturLowerMap.charAt(i));
        }
        String frakturUpper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String frakturUpperMap = "𝔄𝔅ℭ𝔇𝔈𝔉𝔊ℌℑ𝔍𝔎𝔏𝔐𝔑𝔒𝔓𝔔ℜ𝔖𝔗𝔘𝔙𝔚𝔛𝔜ℨ";
        for (int i = 0; i < 26; i++) {
            FRAKTUR.put(frakturUpper.charAt(i), frakturUpperMap.charAt(i));
        }

        String monoLower = "abcdefghijklmnopqrstuvwxyz";
        String monoLowerMap = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣";
        for (int i = 0; i < 26; i++) {
            MONOSPACE.put(monoLower.charAt(i), monoLowerMap.charAt(i));
        }
        String monoUpper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String monoUpperMap = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉";
        for (int i = 0; i < 26; i++) {
            MONOSPACE.put(monoUpper.charAt(i), monoUpperMap.charAt(i));
        }

        String sqLower = "abcdefghijklmnopqrstuvwxyz";
        String sqMap = "🄰🄱🄲🄳🄴🄵🄶🄷🄸🄹🄺🄻🄼🄽🄾🄿🅀🅁🅂🅃🅄🅅🅆🅇🅈🅉";
        for (int i = 0; i < 26; i++) {
            SQUARED.put(sqLower.charAt(i), sqMap.charAt(i));
            SQUARED.put(Character.toUpperCase(sqLower.charAt(i)), sqMap.charAt(i));
        }

        String udFrom = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.!?(),'";
        String udTo   = "ɐqɔpǝɟƃɥᴉɾʞʅɯuodbɹsʇnʌʍxʎzⱯᗺƆᗡƎᖵ⅁HIſꓘ˥WNOԀ῁ɹS⊥∩ΛMX⅄Z0⇂ᄅƐ߈ϛ9ㄥ86˙¡¿)(,'";
        for (int i = 0; i < Math.min(udFrom.length(), udTo.length()); i++) {
            UPSIDE_DOWN.put(udFrom.charAt(i), udTo.charAt(i));
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (isFormatting) return;
        if (!(event.packet instanceof ServerboundChatPacket)) return;
        ServerboundChatPacket packet = (ServerboundChatPacket) event.packet;
        String original = packet.message();
        if (original == null || original.isEmpty()) return;

        if (original.startsWith("/") && !formatSlashCommands.get()) return;

        if (original.startsWith("-") && !formatMeteorCommands.get()) return;

        String formatted = formatMessage(original);
        if (!formatted.equals(original)) {
            event.cancel();
            isFormatting = true;
            try {
                sendChatMessage(formatted);
            } finally {
                isFormatting = false;
            }
        }
    }

    public String formatMessage(String input) {
        String msg = input;

        msg = applyFontPreset(msg);

        if (leetSpeak.get()) {
            msg = applyLeetSpeak(msg);
        }

        if (scrambleWords.get()) {
            msg = applyScrambleWords(msg);
        }

        if (reverseText.get()) {
            msg = applyReverseText(msg);
        }

        if (morseCode.get()) {
            msg = applyMorseCode(msg);
        }

        msg = applyColorCodes(msg);

        if (gradientEnabled.get()) {
            msg = applyGradient(msg);
        }

        if (zalgoEnabled.get()) {
            msg = applyZalgo(msg);
        }

        if (antiCensor.get()) {
            msg = applyAntiCensor(msg);
        }

        if (!charColorSequence.get().isEmpty()) {
            msg = applyCharColorSequence(msg);
        }

        if (rainbow.get()) {
            msg = applyRainbow(msg);
        }

        if (insertEnabled.get()) {
            msg = applyCharInsertion(msg);
        }

        if (!prefix.get().isEmpty()) {
            msg = prefix.get() + msg;
        }

        if (appendReset.get()) {
            msg += codePrefix() + "r";
        }

        if (appendTimestamp.get()) {
            msg += " [" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "]";
        }

        if (randomMessage.get()) {
            msg += " " + generateRandomSuffix();
        }

        if (!suffix.get().isEmpty()) {
            msg += suffix.get();
        }

        return msg;
    }

    private String applyFontPreset(String input) {
        FontPreset preset = fontPreset.get();
        if (preset == FontPreset.None) return input;

        Map<Character, Character> table = null;
        boolean fullWidth = false;

        switch (preset) {
            case SmallCaps:   table = SMALL_CAPS; break;
            case FullWidth:   fullWidth = true; break;
            case Circle:      table = CIRCLE; break;
            case Script:      table = SCRIPT; break;
            case DoubleStruck: table = DOUBLE_STRUCK; break;
            case Fraktur:     table = FRAKTUR; break;
            case Monospace:   table = MONOSPACE; break;
            case Squared:     table = SQUARED; break;
            case UpsideDown:  table = UPSIDE_DOWN; break;
            case Custom:      return applyCustomAlphabet(input);
            default:          return input;
        }

        if (fullWidth) return applyFullWidth(input);
        if (table == null) return input;

        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            Character replacement = table.get(c);
            sb.append(replacement != null ? replacement : c);
        }
        return sb.toString();
    }

    private String applyFullWidth(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c >= '!' && c <= '~') {
                sb.append((char) (c + 0xFEE0));
            } else if (c == ' ') {
                sb.append('\u3000');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String applyCustomAlphabet(String input) {
        String mapping = customAlphabet.get();
        if (mapping == null || mapping.isEmpty()) return input;

        Map<Character, Character> customMap = new HashMap<>();
        for (int i = 0; i + 1 < mapping.length(); i += 2) {
            customMap.put(mapping.charAt(i), mapping.charAt(i + 1));
        }

        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            Character replacement = customMap.get(c);
            sb.append(replacement != null ? replacement : c);
        }
        return sb.toString();
    }

    private String applyLeetSpeak(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (Character.toLowerCase(c)) {
                case 'a': sb.append('4'); break;
                case 'e': sb.append('3'); break;
                case 'i': sb.append('1'); break;
                case 'o': sb.append('0'); break;
                case 's': sb.append('5'); break;
                case 't': sb.append('7'); break;
                case 'l': sb.append('1'); break;
                case 'b': sb.append('8'); break;
                case 'g': sb.append('9'); break;
                default:  sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private String applyScrambleWords(String input) {
        String[] words = input.split("(?<=\\s)");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(scrambleWord(word));
        }
        return sb.toString();
    }

    private String scrambleWord(String word) {

        int first = 0, last = word.length() - 1;
        while (first < word.length() && !Character.isLetter(word.charAt(first))) first++;
        while (last > first && !Character.isLetter(word.charAt(last))) last--;

        if (last - first < 2) return word;

        char[] inner = word.substring(first + 1, last).toCharArray();

        for (int i = inner.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = inner[i];
            inner[i] = inner[j];
            inner[j] = tmp;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(word, 0, first + 1);
        sb.append(inner);
        sb.append(word, last, word.length());
        return sb.toString();
    }

    private String applyReverseText(String input) {
        return new StringBuilder(input).reverse().toString();
    }

    private String applyMorseCode(String input) {
        StringBuilder sb = new StringBuilder();
        String lower = input.toLowerCase();
        boolean first = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ' ') {
                sb.append(" / ");
            } else if (MORSE.containsKey(c)) {
                if (!first) sb.append(' ');
                sb.append(MORSE.get(c));
                first = false;
            } else {
                if (!first) sb.append(' ');
                sb.append(c);
                first = false;
            }
        }
        return sb.toString();
    }

    private String applyColorCodes(String input) {
        String prefix = codePrefix();

        String presetCode = getQuickPresetCode(prefix);
        if (presetCode == null) presetCode = "";

        String baseColorCode = "";
        if (baseColor.get().hasCode()) {
            baseColorCode = baseColor.get().format(prefix);
        }

        StringBuilder formatCodes = new StringBuilder();
        if (bold.get())          formatCodes.append(prefix).append('l');
        if (italic.get())        formatCodes.append(prefix).append('o');
        if (underline.get())     formatCodes.append(prefix).append('n');
        if (strikethrough.get()) formatCodes.append(prefix).append('m');
        if (obfuscated.get())    formatCodes.append(prefix).append('k');

        return presetCode + baseColorCode + formatCodes.toString() + input;
    }

    private String codePrefix() {
        if (!customPrefixCodes.get().isEmpty()) return customPrefixCodes.get();
        return codeOutputMode.get().getSymbol();
    }

    private String getQuickPresetCode(String prefix) {
        switch (quickPreset.get()) {
            case Green:       return prefix + "a";
            case Aqua:        return prefix + "b";
            case Red:         return prefix + "c";
            case Yellow:      return prefix + "e";
            case Bold:        return prefix + "l";
            case Obfuscated:  return prefix + "k";
            case Reset:       return prefix + "r";
            default:          return null;
        }
    }

    private String applyGradient(String input) {
        int startRGB = parseHexColor(gradientStartColor.get());
        int endRGB   = parseHexColor(gradientEndColor.get());

        int startR = (startRGB >> 16) & 0xFF, startG = (startRGB >> 8) & 0xFF, startB = startRGB & 0xFF;
        int endR   = (endRGB >> 16) & 0xFF,   endG   = (endRGB >> 8) & 0xFF,   endB   = endRGB & 0xFF;

        String prefix = codePrefix();

        int visibleCount = 0;
        boolean skipCode = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '§' || c == '&') { skipCode = true; continue; }
            if (skipCode) { skipCode = false; continue; }
            if (!gradientSkipSpaces.get() || c != ' ') visibleCount++;
        }

        if (visibleCount == 0) return input;

        StringBuilder sb = new StringBuilder();
        int visibleIndex = 0;
        skipCode = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '§' || c == '&') {
                skipCode = true;
                sb.append(c);
                continue;
            }
            if (skipCode) {
                skipCode = false;
                sb.append(c);
                continue;
            }

            if (c == ' ' && gradientSkipSpaces.get()) {
                sb.append(c);
                continue;
            }

            float t = visibleCount > 1 ? (float) visibleIndex / (visibleCount - 1) : 0f;
            int r = clamp((int) (startR + (endR - startR) * t));
            int g = clamp((int) (startG + (endG - startG) * t));
            int b = clamp((int) (startB + (endB - startB) * t));

            sb.append(prefix).append('x');
            String hexR = String.format("%02X", r);
            String hexG = String.format("%02X", g);
            String hexB = String.format("%02X", b);
            sb.append(prefix).append(hexR.charAt(0));
            sb.append(prefix).append(hexR.charAt(1));
            sb.append(prefix).append(hexG.charAt(0));
            sb.append(prefix).append(hexG.charAt(1));
            sb.append(prefix).append(hexB.charAt(0));
            sb.append(prefix).append(hexB.charAt(1));

            sb.append(c);
            visibleIndex++;
        }

        return sb.toString();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String applyZalgo(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            sb.append(c);

            if (c == ' ' || c == '§' || c == '&') continue;

            int intensity = zalgoIntensity.get();

            if (zalgoAbove.get()) {
                for (int i = 0; i < intensity; i++) {
                    sb.append(ZALGO_ABOVE[random.nextInt(ZALGO_ABOVE.length)]);
                }
            }
            if (zalgoMiddle.get()) {
                for (int i = 0; i < intensity; i++) {
                    sb.append(ZALGO_MIDDLE[random.nextInt(ZALGO_MIDDLE.length)]);
                }
            }
            if (zalgoBelow.get()) {
                for (int i = 0; i < intensity; i++) {
                    sb.append(ZALGO_BELOW[random.nextInt(ZALGO_BELOW.length)]);
                }
            }
        }
        return sb.toString();
    }

    private String applyAntiCensor(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            sb.append(input.charAt(i));
            if (i < input.length() - 1) {
                sb.append('\u200B');
            }
        }
        return sb.toString();
    }

    private String applyCharColorSequence(String input) {
        String sequence = charColorSequence.get();
        if (sequence == null || sequence.isEmpty()) return input;

        String prefix = codePrefix();
        StringBuilder sb = new StringBuilder();
        int colorIndex = 0;
        boolean inFormatCode = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '§' || (prefix.equals("&") && c == '&')) {
                inFormatCode = true;
                sb.append(c);
                continue;
            }
            if (inFormatCode) {
                inFormatCode = false;
                sb.append(c);
                continue;
            }

            if (c != ' ') {
                char colorChar = sequence.charAt(colorIndex % sequence.length());
                sb.append(prefix).append(colorChar);
                colorIndex++;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String applyRainbow(String input) {
        String prefix = codePrefix();
        StringBuilder sb = new StringBuilder();
        int colorIndex = 0;
        boolean inFormatCode = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '§' || c == '&') {
                inFormatCode = true;
                sb.append(c);
                continue;
            }
            if (inFormatCode) {
                inFormatCode = false;
                sb.append(c);
                continue;
            }

            if (c != ' ') {
                ChatColor color = RAINBOW_ORDER[colorIndex % RAINBOW_ORDER.length];
                sb.append(color.format(prefix));
                colorIndex++;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String applyCharInsertion(String input) {
        String chars = insertCharacters.get();
        if (chars == null || chars.isEmpty()) return input;

        int everyN = insertEveryN.get();
        StringBuilder sb = new StringBuilder();
        int charCount = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            sb.append(c);

            if (c == ' ') {

                if (everyN == 0 && i < input.length() - 1) {
                    sb.append(chars);
                }
                continue;
            }

            charCount++;
            if (everyN > 0 && charCount % everyN == 0 && i < input.length() - 1) {

                if (i < input.length() - 1) {
                    sb.append(chars);
                }
            }
        }
        return sb.toString();
    }

    private int parseHexColor(String hex) {
        try {
            hex = hex.replace("#", "");
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private String generateRandomSuffix() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void sendChatMessage(String msg) {
        ClientPacketListener handler = mc.getConnection();
        if (handler == null) return;

        int count = Math.max(1, repeatCount.get());

        int tickInterval = 4;

        String first = msg;
        if (count > 1 && randomMessage.get()) first = msg + " [1]";
        handler.sendChat(sanitizeOutgoing(truncateSafe(first, 256)));

        if (count > 1) {
            for (int i = 1; i < count; i++) {
                String toSend = msg;
                if (randomMessage.get()) toSend = msg + " [" + (i + 1) + "]";
                pendingRepeats.add(new PendingRepeat(sanitizeOutgoing(truncateSafe(toSend, 256)), 1, tickInterval, repeatTickClock + tickInterval * i));
            }
        }
    }

    private String sanitizeOutgoing(String message) {
        if (message == null || !sanitizeSectionCodes.get()) return message;
        if (message.indexOf('\u00A7') < 0) return message;
        return message.replace('\u00A7', '&');
    }

    private String truncateSafe(String msg, int max) {
        if (msg == null || msg.length() <= max) return msg;

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < msg.length() && count < max; i++) {
            char c = msg.charAt(i);
            sb.append(c);
            count++;

            if (c == '§' || c == '&') {
                if (i + 1 < msg.length() && count < max) {
                    sb.append(msg.charAt(i + 1));
                    count++;
                    i++;
                } else {
                    sb.setLength(sb.length() - 1);
                    break;
                }
            }
        }
        return sb.toString();
    }

    public String getPreview(String input) {
        if (!previewEnabled.get()) return input;
        return formatMessage(input);
    }

    public static final String[] PIPELINE_ORDER = {
        "fontPreset", "leetSpeak", "scrambleWords", "reverseText", "morseCode",
        "colorCodes", "gradient", "zalgo", "antiCensor", "charColorSequence",
        "rainbow", "characterInsertion", "repeat", "timestamp"
    };

    public String[] getStepByStepPreview(String input) {
        if (!previewEnabled.get()) return new String[]{input};

        String[] steps = new String[PIPELINE_ORDER.length + 1];
        steps[0] = input;
        String msg = input;

        msg = applyFontPreset(msg);
        steps[1] = msg;

        if (leetSpeak.get()) {
            msg = applyLeetSpeak(msg);
            steps[2] = msg;
        } else { steps[2] = msg; }

        if (scrambleWords.get()) {
            msg = applyScrambleWords(msg);
            steps[3] = msg;
        } else { steps[3] = msg; }

        if (reverseText.get()) {
            msg = applyReverseText(msg);
            steps[4] = msg;
        } else { steps[4] = msg; }

        if (morseCode.get()) {
            msg = applyMorseCode(msg);
            steps[5] = msg;
        } else { steps[5] = msg; }

        msg = applyColorCodes(msg);
        steps[6] = msg;

        if (gradientEnabled.get()) {
            msg = applyGradient(msg);
            steps[7] = msg;
        } else { steps[7] = msg; }

        if (zalgoEnabled.get()) {
            msg = applyZalgo(msg);
            steps[8] = msg;
        } else { steps[8] = msg; }

        if (antiCensor.get()) {
            msg = applyAntiCensor(msg);
            steps[9] = msg;
        } else { steps[9] = msg; }

        if (!charColorSequence.get().isEmpty()) {
            msg = applyCharColorSequence(msg);
            steps[10] = msg;
        } else { steps[10] = msg; }

        if (rainbow.get()) {
            msg = applyRainbow(msg);
            steps[11] = msg;
        } else { steps[11] = msg; }

        if (insertEnabled.get()) {
            msg = applyCharInsertion(msg);
            steps[12] = msg;
        } else { steps[12] = msg; }

        if (appendReset.get()) msg += codePrefix() + "r";
        if (appendTimestamp.get()) msg += " [" + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + "]";
        if (randomMessage.get()) msg += " " + generateRandomSuffix();
        steps[13] = msg;

        return steps;
    }

    public boolean processCommand(String command) {
        if (command == null) return false;
        if (!command.startsWith(".mf") && !command.startsWith(".messageformatter")) return false;

        String[] parts = command.split("\\s+", 3);
        if (parts.length < 2) {
            info("Usage: .mf preview <text> | .mf pipeline");
            return true;
        }

        String sub = parts[1].toLowerCase();
        switch (sub) {
            case "preview": {
                String text = parts.length >= 3 ? parts[2] : "Hello Level";
                String formatted = formatMessage(text);
                info("Preview: %s", formatted);
                return true;
            }
            case "pipeline": {
                info("Pipeline order: %s", String.join(" → ", PIPELINE_ORDER));
                return true;
            }
            case "steps": {
                String text = parts.length >= 3 ? parts[2] : "Hello Level";
                String[] stepResults = getStepByStepPreview(text);
                for (int i = 0; i < PIPELINE_ORDER.length; i++) {
                    info("  %d. %s: %s", i + 1, PIPELINE_ORDER[i], stepResults[i]);
                }
                return true;
            }
            default:
                info("Unknown subcommand: %s", sub);
                return true;
        }
    }

    public String exportPreset() {
        StringBuilder sb = new StringBuilder();
        sb.append("# MessageFormatter Preset\n");
        sb.append("codeOutputMode=").append(codeOutputMode.get()).append("\n");
        sb.append("quickPreset=").append(quickPreset.get()).append("\n");
        sb.append("baseColor=").append(baseColor.get()).append("\n");
        sb.append("bold=").append(bold.get()).append("\n");
        sb.append("italic=").append(italic.get()).append("\n");
        sb.append("underline=").append(underline.get()).append("\n");
        sb.append("strikethrough=").append(strikethrough.get()).append("\n");
        sb.append("obfuscated=").append(obfuscated.get()).append("\n");
        sb.append("gradientEnabled=").append(gradientEnabled.get()).append("\n");
        sb.append("gradientStartColor=").append(gradientStartColor.get()).append("\n");
        sb.append("gradientEndColor=").append(gradientEndColor.get()).append("\n");
        sb.append("gradientSkipSpaces=").append(gradientSkipSpaces.get()).append("\n");
        sb.append("fontPreset=").append(fontPreset.get()).append("\n");
        sb.append("zalgoEnabled=").append(zalgoEnabled.get()).append("\n");
        sb.append("zalgoIntensity=").append(zalgoIntensity.get()).append("\n");
        sb.append("zalgoAbove=").append(zalgoAbove.get()).append("\n");
        sb.append("zalgoMiddle=").append(zalgoMiddle.get()).append("\n");
        sb.append("zalgoBelow=").append(zalgoBelow.get()).append("\n");
        sb.append("insertEnabled=").append(insertEnabled.get()).append("\n");
        sb.append("insertCharacters=").append(insertCharacters.get()).append("\n");
        sb.append("insertEveryN=").append(insertEveryN.get()).append("\n");
        sb.append("rainbow=").append(rainbow.get()).append("\n");
        sb.append("leetSpeak=").append(leetSpeak.get()).append("\n");
        sb.append("reverseText=").append(reverseText.get()).append("\n");
        sb.append("morseCode=").append(morseCode.get()).append("\n");
        sb.append("scrambleWords=").append(scrambleWords.get()).append("\n");
        sb.append("antiCensor=").append(antiCensor.get()).append("\n");
        sb.append("charColorSequence=").append(charColorSequence.get()).append("\n");
        sb.append("appendReset=").append(appendReset.get()).append("\n");
        sb.append("appendTimestamp=").append(appendTimestamp.get()).append("\n");
        sb.append("randomMessage=").append(randomMessage.get()).append("\n");
        sb.append("repeatCount=").append(repeatCount.get()).append("\n");
        sb.append("prefix=").append(prefix.get()).append("\n");
        sb.append("suffix=").append(suffix.get()).append("\n");
        return sb.toString();
    }

    public List<String> getActiveFeatures() {
        List<String> active = new ArrayList<>();
        if (fontPreset.get() != FontPreset.None) active.add("Font: " + fontPreset.get());
        if (leetSpeak.get()) active.add("Leet Speak");
        if (scrambleWords.get()) active.add("Scramble Words");
        if (reverseText.get()) active.add("Reverse Component");
        if (morseCode.get()) active.add("Morse Code");
        if (baseColor.get().hasCode()) active.add("Base Color: " + baseColor.get());
        if (bold.get()) active.add("Bold");
        if (italic.get()) active.add("Italic");
        if (underline.get()) active.add("Underline");
        if (strikethrough.get()) active.add("Strikethrough");
        if (obfuscated.get()) active.add("Obfuscated");
        if (gradientEnabled.get()) active.add("Gradient");
        if (zalgoEnabled.get()) active.add("Zalgo (" + zalgoIntensity.get() + ")");
        if (antiCensor.get()) active.add("Anti-Censor");
        if (!charColorSequence.get().isEmpty()) active.add("Per-Char Colors");
        if (rainbow.get()) active.add("Rainbow");
        if (insertEnabled.get()) active.add("Char Insertion");
        if (appendReset.get()) active.add("Append Reset");
        if (appendTimestamp.get()) active.add("Timestamp");
        if (randomMessage.get()) active.add("Random Suffix");
        if (repeatCount.get() > 1) active.add("Repeat: " + repeatCount.get());
        return active;
    }

    @Override
    public void onActivate() {

        pendingRepeats.clear();
        repeatTickClock = 0;
        List<String> features = getActiveFeatures();
        if (features.isEmpty()) {
            info("Message Formatter activated (no features enabled).");
        } else {
            info("Message Formatter activated. Pipeline: %s", String.join(" → ", features));
        }
    }

    @Override
    public void onDeactivate() {

        pendingRepeats.clear();
        info("Message Formatter deactivated.");
    }
}
