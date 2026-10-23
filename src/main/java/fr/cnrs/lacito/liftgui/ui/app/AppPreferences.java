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

    /* ─── LaTeX export ─── */

    private static final String LATEX_PROGRAM = "latex.program.";
    private static final String LATEX_OPEN_PDF = "latex.openPdf";
    private static final String LATEX_SETTINGS = "latex.settings.";
    private static final String LATEX_OUTPUT = "latex.output.";

    /** The program chosen for an engine kind (xelatex, lualatex), or null: it belongs to the machine. */
    public String latexProgram(String kind) {
        return prefs.get(LATEX_PROGRAM + kind, null);
    }

    public void setLatexProgram(String kind, String path) {
        if (path == null) prefs.remove(LATEX_PROGRAM + kind);
        else prefs.put(LATEX_PROGRAM + kind, path);
    }

    /** Whether the PDF is opened at the end of an export. */
    public boolean latexOpenPdf() {
        return prefs.getBoolean(LATEX_OPEN_PDF, true);
    }

    public void setLatexOpenPdf(boolean open) {
        prefs.putBoolean(LATEX_OPEN_PDF, open);
    }

    /** The JSON settings of the last LaTeX export of a dictionary, or null. */
    public String latexSettings(File dictionary) {
        return prefs.get(LATEX_SETTINGS + key(dictionary), null);
    }

    public void setLatexSettings(File dictionary, String json) {
        if (json.length() <= Preferences.MAX_VALUE_LENGTH) prefs.put(LATEX_SETTINGS + key(dictionary), json);
    }

    /** The .tex of the last LaTeX export of a dictionary, or null. */
    public String latexOutput(File dictionary) {
        return prefs.get(LATEX_OUTPUT + key(dictionary), null);
    }

    public void setLatexOutput(File dictionary, String path) {
        prefs.put(LATEX_OUTPUT + key(dictionary), path);
    }

    /** A preferences key (at most 80 characters) for a dictionary: a digest of its path. */
    private static String key(File dictionary) {
        String path = dictionary == null ? "" : dictionary.getAbsolutePath();
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                .digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d, 0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(path.hashCode());
        }
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
