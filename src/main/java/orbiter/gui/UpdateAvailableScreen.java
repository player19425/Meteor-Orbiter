package orbiter.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import orbiter.util.ConfigModifier;
import orbiter.util.UpdateChecker;

public class UpdateAvailableScreen extends WidgetScreen {
    private final String tag;
    private final String pageUrl;
    private final String downloadUrl;

    public UpdateAvailableScreen(GuiTheme theme, String tag, String pageUrl, String downloadUrl) {
        super(theme, "Orbiter Update");
        this.tag = tag;
        this.pageUrl = pageUrl;
        this.downloadUrl = downloadUrl;
    }

    @Override
    public void initWidgets() {
        WWindow window = add(theme.window("Orbiter Update")).center().widget();

        window.add(theme.label("There is a new update available (" + tag + ")."));

        WButton update = window.add(theme.button("Update now")).expandX().widget();
        update.action = () -> {
            UpdateChecker.install(tag, downloadUrl);
            onClose();
        };

        WButton ignore = window.add(theme.button("Ignore this version")).expandX().widget();
        ignore.action = () -> {
            ConfigModifier.get().setIgnoredVersion(tag);
            onClose();
        };

        WButton changelog = window.add(theme.button("See Changelog")).expandX().widget();
        changelog.action = () -> UpdateChecker.sendChangelogLink(pageUrl);
    }
}
