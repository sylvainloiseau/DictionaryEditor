package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import fr.cnrs.lacito.liftgui.undo.History;
import java.util.prefs.Preferences;
import javafx.scene.Scene;

/**
 * The user's settings: font size, theme and default directory. They are stored with the
 * interface language and the recent files, in the preferences node of the {@code ui} package.
 */
public final class AppPreferences {

    private static final String FONT_SIZE = "ui.fontSize";
    private static final String THEME = "ui.theme";
    private static final String DEFAULT_PATH = "ui.defaultPath";
    private static final String HISTORY_LIMIT = "undo.historyLimit";
    private static final String DARK_STYLE =
        "-fx-base: #2b2b2b; -fx-background: #3c3f41; -fx-control-inner-background: #45494a;";
    private static final String DARK_CSS = "/fr/cnrs/lacito/liftgui/ui/dark.css";

    private final Preferences prefs;

    public AppPreferences() {
        this(Preferences.userNodeForPackage(I18n.class));
    }

    public AppPreferences(Preferences prefs) {
        this.prefs = prefs;
    }

    /** The underlying preferences node (also used for the recent files). */
    public Preferences node() {
        return prefs;
    }

    public int fontSize() {
        try {
            return Integer.parseInt(prefs.get(FONT_SIZE, "13"));
        } catch (NumberFormatException e) {
            return 13;
        }
    }

    public void setFontSize(int size) {
        prefs.put(FONT_SIZE, String.valueOf(size));
    }

    /** How many undo steps are kept (the oldest are dropped). */
    public int historyLimit() {
        try {
            return Math.max(1, Integer.parseInt(prefs.get(HISTORY_LIMIT, String.valueOf(History.DEFAULT_LIMIT))));
        } catch (NumberFormatException e) {
            return History.DEFAULT_LIMIT;
        }
    }

    public void setHistoryLimit(int limit) {
        prefs.put(HISTORY_LIMIT, String.valueOf(Math.max(1, limit)));
    }

    public boolean darkTheme() {
        return "dark".equals(prefs.get(THEME, "light"));
    }

    public void setDarkTheme(boolean dark) {
        prefs.put(THEME, dark ? "dark" : "light");
    }

    public String defaultPath() {
        return prefs.get(DEFAULT_PATH, System.getProperty("user.home"));
    }

    public void setDefaultPath(String path) {
        prefs.put(DEFAULT_PATH, path);
    }

    /** The default directory for file choosers, or null when it does not exist. */
    public File defaultDirectory() {
        File dir = new File(defaultPath());
        return dir.isDirectory() ? dir : null;
    }

    /** Applies the font size and the theme to a scene. */
    public void applyAppearance(Scene scene) {
        if (scene == null) return;
        var sheets = scene.getStylesheets();
        sheets.removeIf(s -> s.endsWith("/dark.css"));
        String rootStyle = "-fx-font-size: " + fontSize() + "px;";
        if (darkTheme()) {
            var css = AppPreferences.class.getResource(DARK_CSS);
            if (css != null) sheets.add(css.toExternalForm());
            else rootStyle += " " + DARK_STYLE;
        }
        scene.getRoot().setStyle(rootStyle);
    }
}
