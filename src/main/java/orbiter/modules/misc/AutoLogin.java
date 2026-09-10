package orbiter.modules.misc;

import orbiter.Orbiter;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import orbiter.mixin.DialogScreenAccessor;
import orbiter.util.FastSend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AutoLogin extends Module {
    public enum CommandMode {
        Auto, LoginOnly, RegisterOnly
    }

    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();
    private static final Pattern PATTERN_FAILED = Pattern.compile("(?!x)x");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPatterns = settings.createGroup("Chat Detection");
    private final SettingGroup sgCommands = settings.createGroup("Commands");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgDialogs = settings.createGroup("Dialogs");

    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("Password to use. Sent as a chat command so it never shows in local chat.")
        .defaultValue("")
        .onChanged(this::onPasswordTyped)
        .build()
    );

    private final Setting<String> realPassword = sgGeneral.add(new StringSetting.Builder()
        .name("real-password")
        .description("Internal copy of the password kept while Hide Password masks the field.")
        .defaultValue("")
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> hidePassword = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-password")
        .description("Shows asterisks in the password field and strips any chat line containing the password from your chat.")
        .defaultValue(true)
        .onChanged(this::onHidePasswordToggled)
        .build()
    );

    private final Setting<Boolean> detectionEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("detection-enabled")
        .description("Watch chat for login and register prompts.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> loginPatterns = sgPatterns.add(new StringListSetting.Builder()
        .name("login-patterns")
        .description("Patterns that trigger the login command. Prefix with regex: for regex, otherwise * and ? wildcards.")
        .defaultValue(List.of(
            "*please login with /login <password>*",
            "*/login <password>*",
            "*you must login*",
            "*login:*",
            "*please log in*"
        ))
        .build()
    );

    private final Setting<List<String>> registerPatterns = sgPatterns.add(new StringListSetting.Builder()
        .name("register-patterns")
        .description("Patterns that trigger the register command. Prefix with regex: for regex, otherwise * and ? wildcards.")
        .defaultValue(List.of(
            "*you must register*",
            "*/register <password>*",
            "*please register*",
            "*register with /register*"
        ))
        .build()
    );

    private final Setting<Boolean> enableRegex = sgPatterns.add(new BoolSetting.Builder()
        .name("enable-regex")
        .description("Allow regex: prefixed patterns in the chat lists.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> loginCommand = sgCommands.add(new StringSetting.Builder()
        .name("login-command")
        .description("Command sent when a login prompt is detected. {pass} is replaced with the password.")
        .defaultValue("/login {pass}")
        .build()
    );

    private final Setting<String> registerCommand = sgCommands.add(new StringSetting.Builder()
        .name("register-command")
        .description("Command sent when a register prompt is detected. {pass} is replaced with the password.")
        .defaultValue("/register {pass} {pass}")
        .build()
    );

    private final Setting<CommandMode> mode = sgCommands.add(new EnumSetting.Builder<CommandMode>()
        .name("mode")
        .description("Which commands are allowed to fire.")
        .defaultValue(CommandMode.Auto)
        .build()
    );

    private final Setting<Integer> joinDelay = sgTiming.add(new IntSetting.Builder()
        .name("join-delay")
        .description("Ticks after joining before detection arms.")
        .defaultValue(40)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Integer> sendDelay = sgTiming.add(new IntSetting.Builder()
        .name("send-delay")
        .description("Ticks between matching a prompt and sending the command.")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> retryDelay = sgTiming.add(new IntSetting.Builder()
        .name("retry-delay")
        .description("Ticks to wait after a send before a repeated prompt counts as a retry.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 400)
        .build()
    );

    private final Setting<Integer> maxRetries = sgTiming.add(new IntSetting.Builder()
        .name("max-retries")
        .description("How many times to answer repeated prompts before giving up.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> oncePerSession = sgTiming.add(new BoolSetting.Builder()
        .name("once-per-session")
        .description("Stop after one successful answer until the module is toggled or you rejoin.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dialogEnabled = sgDialogs.add(new BoolSetting.Builder()
        .name("dialog-enabled")
        .description("Fill and submit 1.21.6+ dialog screen prompts automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> dialogPatterns = sgDialogs.add(new StringListSetting.Builder()
        .name("dialog-patterns")
        .description("Patterns matched against the dialog title and body text.")
        .defaultValue(List.of("regex:(?i).*(login|sign in|register|password).*"))
        .build()
    );

    private final Setting<String> submitButtonText = sgDialogs.add(new StringSetting.Builder()
        .name("submit-button-text")
        .description("Text pattern used to find the submit button. Prefix with regex: for regex, otherwise * and ? wildcards.")
        .defaultValue("regex:(?i)(sign in|submit|login|continue|confirm)")
        .build()
    );

    private final Setting<Integer> dialogSubmitDelay = sgDialogs.add(new IntSetting.Builder()
        .name("dialog-submit-delay")
        .description("Ticks to wait before filling and submitting a matched dialog.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private int ticks;
    private boolean armed;
    private boolean stopped;
    private boolean latched;
    private int joinTimer;
    private boolean pending;
    private boolean pendingRegister;
    private int pendingTick;
    private int respondedAtTick = -1;
    private int attempts;

    private DialogScreen<?> dialogScreen;
    private int dialogSubmitTick = -1;

    private static Screen capturedScreen;
    private static HeaderAndFooterLayout capturedLayout;
    private static List<EditBox> dialogBoxes;
    private static Button submitButton;

    public AutoLogin() {
        super(Orbiter.CATEGORY, "auto-login", "Auto-answers login and register prompts.");
    }

    private void onPasswordTyped(String value) {
        if (!value.isEmpty() && !isMask(value)) stopped = false;
        if (!hidePassword.get()) return;
        if (value.isEmpty()) {
            realPassword.set("");
            return;
        }
        if (isMask(value)) return;
        realPassword.set(value);
        String masked = mask(value);
        if (!masked.equals(value)) password.set(masked);
    }

    private void onHidePasswordToggled(boolean on) {
        String real = realPassword.get();
        String visible = password.get();

        if (on) {
            if (!visible.isBlank() && !isMask(visible)) realPassword.set(visible);
            String current = realPassword.get();
            if (!current.isBlank() && !visible.equals(mask(current))) password.set(mask(current));
        } else {
            if (!real.isBlank() && !visible.equals(real)) password.set(real);
        }
    }

    private boolean isMask(String value) {
        if (value.isEmpty()) return false;
        for (char c : value.toCharArray()) {
            if (c != '*') return false;
        }
        return true;
    }

    private String mask(String value) {
        return "*".repeat(value.length());
    }

    private String effectivePassword() {
        String visible = password.get();
        if (!hidePassword.get()) return visible;
        if (visible.isBlank() || isMask(visible)) return realPassword.get();
        realPassword.set(visible);
        String masked = mask(visible);
        if (!masked.equals(visible)) password.set(masked);
        return visible;
    }

    @Override
    public void onActivate() {
        resetState();
        joinTimer = joinDelay.get();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        ticks = 0;
        armed = false;
        stopped = false;
        latched = false;
        joinTimer = 0;
        pending = false;
        pendingRegister = false;
        pendingTick = 0;
        respondedAtTick = -1;
        attempts = 0;
        dialogScreen = null;
        dialogSubmitTick = -1;
        dialogBoxes = null;
        submitButton = null;
        capturedScreen = null;
        capturedLayout = null;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
        joinTimer = joinDelay.get();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ticks++;

        if (!armed) {
            if (joinTimer > 0) joinTimer--;
            if (joinTimer <= 0 && detectionEnabled.get() && !stopped && !(oncePerSession.get() && latched)) {
                armed = true;
            }
        }

        if (pending && ticks >= pendingTick) {
            pending = false;
            sendPending();
        }

        if (dialogSubmitTick >= 0 && ticks >= dialogSubmitTick) {
            dialogSubmitTick = -1;
            submitDialog();
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!detectionEnabled.get()) return;
        if (!armed || stopped) return;
        if (pending) return;
        if (event.getMessage() == null) return;

        String text = stripCodes(event.getMessage().getString());
        if (text.isBlank() || text.startsWith("/")) return;

        String pass = effectivePassword();
        if (!pass.isEmpty() && text.contains(pass)) {
            event.cancel();
            return;
        }

        boolean register = matchesAny(registerPatterns.get(), text);
        boolean login = !register && matchesAny(loginPatterns.get(), text);
        if (!register && !login) return;

        if (respondedAtTick >= 0) {
            if (ticks - respondedAtTick < retryDelay.get()) return;
            if (attempts >= maxRetries.get()) {
                warning("AutoLogin gave up after " + attempts + " attempts");
                stopped = true;
                latched = true;
                armed = false;
                return;
            }
        } else if (latched) {
            return;
        }

        if (register && mode.get() != CommandMode.LoginOnly) queue(true);
        else if (login && mode.get() != CommandMode.RegisterOnly) queue(false);
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!dialogEnabled.get() || stopped) return;
        if (oncePerSession.get() && latched) return;
        if (!(event.screen instanceof DialogScreen<?> screen)) return;

        if (dialogScreen == screen) return;
        if (!dialogMatches(screen)) return;

        dialogScreen = screen;
        dialogSubmitTick = ticks + dialogSubmitDelay.get();
        tryCollectControls(screen);
    }

    private void queue(boolean register) {
        pending = true;
        pendingRegister = register;
        pendingTick = ticks + sendDelay.get();
    }

    private void sendPending() {
        String pass = effectivePassword();
        if (pass.isEmpty()) {
            error("No password set in AutoLogin.");
            stopped = true;
            armed = false;
            return;
        }

        String template = pendingRegister ? registerCommand.get() : loginCommand.get();
        String command = template.replace("{pass}", pass);
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank()) return;

        if (FastSend.command(command)) {
            respondedAtTick = ticks;
            attempts++;
            if (oncePerSession.get()) latched = true;
        }
    }

    private void submitDialog() {
        DialogScreen<?> screen = dialogScreen;
        dialogScreen = null;

        List<EditBox> boxes = dialogBoxes;
        Button button = submitButton;
        dialogBoxes = null;
        submitButton = null;

        if (screen == null || boxes == null || button == null) return;
        if (mc.gui.screen() != screen) return;

        String pass = effectivePassword();
        if (pass.isEmpty()) {
            error("No password set in AutoLogin.");
            stopped = true;
            armed = false;
            return;
        }

        for (EditBox box : boxes) {
            box.setValue(pass);
        }
        button.onPress(new KeyEvent(-1, -1, 0));

        if (oncePerSession.get()) latched = true;
    }

    public static void onDialogLayout(DialogScreen<?> screen) {
        if (screen == null) return;

        AutoLogin module = Modules.get().get(AutoLogin.class);
        if (module == null || !module.isActive() || !module.dialogEnabled.get() || module.stopped) return;
        if (module.oncePerSession.get() && module.latched) return;

        capturedScreen = screen;
        capturedLayout = ((DialogScreenAccessor) screen).orbiter$getLayout();
        module.tryCollectControls(screen);
    }

    private void tryCollectControls(Screen screen) {
        if (!isActive() || !dialogEnabled.get() || stopped) return;
        if (oncePerSession.get() && latched) return;
        if (dialogScreen != screen) return;
        if (capturedScreen != screen || capturedLayout == null) return;

        List<EditBox> boxes = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        capturedLayout.visitChildren(element -> {
            if (element instanceof EditBox box) boxes.add(box);
            else if (element instanceof Button button) buttons.add(button);
        });
        if (boxes.isEmpty() || buttons.isEmpty()) return;

        Button submit = findSubmitButton(buttons);
        if (submit == null) return;

        dialogBoxes = boxes;
        submitButton = submit;
    }

    private boolean dialogMatches(DialogScreen<?> screen) {
        Dialog dialog = ((DialogScreenAccessor) screen).orbiter$getDialog();
        if (dialog == null) return false;

        CommonDialogData data = dialog.common();
        if (data == null) return false;

        StringBuilder sb = new StringBuilder();
        if (data.title() != null) sb.append(data.title().getString());
        if (data.body() != null) {
            for (DialogBody body : data.body()) {
                if (body instanceof PlainMessage message && message.contents() != null) {
                    sb.append(' ').append(message.contents().getString());
                }
            }
        }

        String text = sb.toString();
        for (PromptPattern pattern : parsePatterns(dialogPatterns.get())) {
            if (pattern.test(text)) return true;
        }
        return false;
    }

    private Button findSubmitButton(List<Button> buttons) {
        PromptPattern pattern = PromptPattern.parse(submitButtonText.get(), true);
        for (Button button : buttons) {
            if (pattern.test(stripCodes(button.getMessage().getString()))) return button;
        }
        return null;
    }

    private boolean matchesAny(List<String> raw, String text) {
        for (PromptPattern pattern : parsePatterns(raw)) {
            if (pattern.test(text)) return true;
        }
        return false;
    }

    private List<PromptPattern> parsePatterns(List<String> raw) {
        List<PromptPattern> patterns = new ArrayList<>(raw.size());
        for (String entry : raw) {
            if (entry != null && !entry.isBlank()) patterns.add(PromptPattern.parse(entry, enableRegex.get()));
        }
        return patterns;
    }

    private String stripCodes(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)\u00a7.", "").trim();
    }

    private static boolean wildcardMatch(String pattern, String text) {
        int p = 0;
        int t = 0;
        int starP = -1;
        int starT = 0;

        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                starP = p;
                starT = t;
                p++;
            } else if (starP != -1) {
                p = starP + 1;
                starT++;
                t = starT;
            } else {
                return false;
            }
        }

        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }

    @Override
    public String getInfoString() {
        return armed ? "armed" : "idle";
    }

    private record PromptPattern(Pattern regex, String wildcard) {
        static PromptPattern parse(String raw, boolean allowRegex) {
            if (allowRegex && raw.startsWith("regex:")) {
                String source = raw.substring(6);
                Pattern compiled = REGEX_CACHE.computeIfAbsent(source, key -> {
                    try {
                        return Pattern.compile(key);
                    } catch (PatternSyntaxException e) {
                        return PATTERN_FAILED;
                    }
                });
                if (compiled == PATTERN_FAILED) return new PromptPattern(null, source.toLowerCase(Locale.ROOT));
                return new PromptPattern(compiled, null);
            }
            return new PromptPattern(null, raw.toLowerCase(Locale.ROOT));
        }

        boolean test(String text) {
            if (regex != null) return regex.matcher(text).find();
            return wildcardMatch(wildcard, text.toLowerCase(Locale.ROOT));
        }
    }
}
