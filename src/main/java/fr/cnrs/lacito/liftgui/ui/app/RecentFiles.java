package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

/** The most recently opened files, kept in the preferences and listed in a menu. */
public final class RecentFiles {

    private static final int MAX = 5;
    private static final String KEY_PREFIX = "recent.file.";

    private final Preferences prefs;
    private final Menu menu;
    private final Consumer<File> onOpen;

    /**
     * @param menu   the menu listing the files; may be null
     * @param onOpen called when a file of the menu is chosen
     */
    public RecentFiles(Preferences prefs, Menu menu, Consumer<File> onOpen) {
        this.prefs = prefs;
        this.menu = menu;
        this.onOpen = onOpen;
    }

    /** The recent files' paths, most recent first. */
    public List<String> paths() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            String path = prefs.get(KEY_PREFIX + i, null);
            if (path != null) paths.add(path);
        }
        return paths;
    }

    /** Puts a file at the top of the list. */
    public void add(File f) {
        List<String> paths = paths();
        paths.remove(f.getAbsolutePath());
        paths.add(0, f.getAbsolutePath());
        for (int i = 0; i < MAX; i++) {
            if (i < paths.size()) prefs.put(KEY_PREFIX + i, paths.get(i));
            else prefs.remove(KEY_PREFIX + i);
        }
        refreshMenu();
    }

    public void refreshMenu() {
        if (menu == null) return;
        menu.getItems().clear();
        List<String> paths = paths();
        if (paths.isEmpty()) {
            MenuItem empty = new MenuItem(I18n.get(Keys.MENU_FILE_NO_RECENT));
            empty.setDisable(true);
            menu.getItems().add(empty);
            return;
        }
        for (String path : paths) {
            File f = new File(path);
            MenuItem item = new MenuItem(f.getName() + "  (" + f.getParent() + ")");
            item.setOnAction(e -> onOpen.accept(f));
            item.setDisable(!f.exists());
            menu.getItems().add(item);
        }
    }
}
