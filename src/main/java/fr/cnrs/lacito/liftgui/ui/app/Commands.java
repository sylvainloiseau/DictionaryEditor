package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftgui.ui.undo.UndoKeys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.input.KeyEvent;

/**
 * The commands of the menus and their keyboard shortcuts.
 *
 * Every menu item with an id is a command, and its id is the command's id ("file.save",
 * "view.entries", "add.sense"). A command's shortcut is the accelerator of its menu item:
 * the default ({@link #defaultKeys}), unless the user chose another one, saved in the
 * {@link AppPreferences}. The fixed commands (undo, copy…) follow the system and cannot be
 * changed. This is also the single place that knows which combinations are refused.
 */
public final class Commands {

    /** Whether the platform is macOS: Cmd is the shortcut key, and the system takes Cmd+Q. */
    static final boolean MAC = System.getProperty("os.name", "").startsWith("Mac");

    private static final Modifier SC = KeyCombination.SHORTCUT_DOWN;
    private static final Modifier ALT = KeyCombination.ALT_DOWN;
    private static final Modifier SHIFT = KeyCombination.SHIFT_DOWN;

    /** A command: its menu item, the menu it is in, its default shortcut, and whether it is fixed. */
    public record Command(String id, MenuItem item, String group, KeyCombination defaultKeys, boolean fixed) {

        /** The label of the command, as in its menu. */
        public String label() {
            return item.getText();
        }
    }

    private final AppPreferences prefs;
    private final Map<String, Command> commands = new LinkedHashMap<>();

    public Commands(AppPreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Registers the items of a menu bar that have an id, and gives them their shortcuts.
     * Call it again after the menus changed (it keeps the commands already known).
     */
    public void register(MenuBar bar) {
        for (Menu menu : bar.getMenus()) register(menu, menu.getText());
        applyAll();
    }

    private void register(Menu menu, String group) {
        for (MenuItem item : menu.getItems()) {
            if (item instanceof SeparatorMenuItem) continue;
            if (item instanceof Menu sub) {
                register(sub, group + " ▸ " + sub.getText());
                continue;
            }
            String id = item.getId();
            if (id == null || id.isBlank() || commands.containsKey(id)) continue;
            commands.put(id, new Command(id, item, group, defaultKeys(id), FIXED.contains(id)));
        }
    }

    /** The commands, in the order of the menus. */
    public Collection<Command> all() {
        return commands.values();
    }

    public Optional<Command> get(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    /** The current shortcut of a command, or null when it has none. */
    public KeyCombination keys(String id) {
        Command c = commands.get(id);
        if (c == null) return null;
        if (c.fixed()) return c.defaultKeys();
        String saved = prefs.shortcut(id);
        if (saved == null) return c.defaultKeys();
        return parse(saved);
    }

    /** Sets the shortcut of a command (null: none), saves it, and applies it at once. */
    public void setKeys(String id, KeyCombination keys) {
        Command c = commands.get(id);
        if (c == null || c.fixed()) return;
        String name = keys == null ? "" : keys.getName();
        String def = c.defaultKeys() == null ? "" : c.defaultKeys().getName();
        prefs.setShortcut(id, name.equals(def) ? null : name);
        apply(c);
    }

    /** Puts back the default shortcut of every command. */
    public void resetAll() {
        for (Command c : commands.values()) {
            prefs.setShortcut(c.id(), null);
            apply(c);
        }
    }

    /** The command that has this shortcut, other than {@code exceptId}. */
    public Optional<Command> usedBy(KeyCombination keys, String exceptId) {
        if (keys == null) return Optional.empty();
        return commands.values().stream()
            .filter(c -> !c.id().equals(exceptId) && sameKeys(keys(c.id()), keys))
            .findFirst();
    }

    private void applyAll() {
        commands.values().forEach(this::apply);
    }

    private void apply(Command c) {
        // The fixed commands are handled elsewhere (UndoKeys, the text fields): undo and redo
        // show their keys in the menu; copy, paste and cut leave them to the focused field.
        if (c.fixed()) {
            if (c.id().equals("undoMenuItem") || c.id().equals("redoMenuItem")) c.item().setAccelerator(c.defaultKeys());
            return;
        }
        c.item().setAccelerator(keys(c.id()));
    }

    /* ─── Defaults ─── */

    /** The commands whose shortcut follows the system. */
    static final Set<String> FIXED = Set.of("undoMenuItem", "redoMenuItem", "edit.copy", "edit.paste", "edit.cut");

    /** The default shortcut of a command, or null. */
    public static KeyCombination defaultKeys(String id) {
        return DEFAULTS.get(id);
    }

    private static final Map<String, KeyCombination> DEFAULTS = defaults();

    private static Map<String, KeyCombination> defaults() {
        Map<String, KeyCombination> d = new LinkedHashMap<>();
        d.put("file.new", new KeyCodeCombination(KeyCode.N, SC));
        d.put("file.open", new KeyCodeCombination(KeyCode.O, SC));
        d.put("file.save", new KeyCodeCombination(KeyCode.S, SC));
        d.put("file.saveAs", new KeyCodeCombination(KeyCode.S, SC, SHIFT));
        d.put("file.preferences", new KeyCodeCombination(KeyCode.COMMA, SC));
        // On macOS, Cmd+Q is the application menu's Quit.
        if (!MAC) d.put("file.quit", new KeyCodeCombination(KeyCode.Q, SC));
        d.put("undoMenuItem", UndoKeys.UNDO);
        d.put("redoMenuItem", UndoKeys.REDO_ALT);
        d.put("edit.copy", new KeyCodeCombination(KeyCode.C, SC));
        d.put("edit.paste", new KeyCodeCombination(KeyCode.V, SC));
        d.put("edit.cut", new KeyCodeCombination(KeyCode.X, SC));
        String[] views = {"entries", "senses", "examples", "notes", "variants", "pronunciations", "relations",
            "etymologies", "fields"};
        KeyCode[] digits = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5,
            KeyCode.DIGIT6, KeyCode.DIGIT7, KeyCode.DIGIT8, KeyCode.DIGIT9};
        for (int i = 0; i < views.length; i++) d.put("view." + views[i], new KeyCodeCombination(digits[i], SC));
        d.put("add.sense", new KeyCodeCombination(KeyCode.S, SC, ALT));
        d.put("add.example", new KeyCodeCombination(KeyCode.E, SC, ALT));
        d.put("add.pronunciation", new KeyCodeCombination(KeyCode.P, SC, ALT));
        d.put("add.relation", new KeyCodeCombination(KeyCode.R, SC, ALT));
        d.put("add.note", new KeyCodeCombination(KeyCode.N, SC, ALT));
        d.put("add.field", new KeyCodeCombination(KeyCode.F, SC, ALT));
        d.put("add.trait", new KeyCodeCombination(KeyCode.T, SC, ALT));
        d.put("add.annotation", new KeyCodeCombination(KeyCode.A, SC, ALT));
        d.put("add.inSection", new KeyCodeCombination(KeyCode.N, SC, SHIFT));
        d.put("help.shortcuts", new KeyCodeCombination(KeyCode.SLASH, SC));
        d.put("view.columns", new KeyCodeCombination(KeyCode.K, SC, SHIFT));
        d.put("view.focusFilter", new KeyCodeCombination(KeyCode.F, SC, SHIFT));
        return d;
    }

    /** All the default shortcuts, by command id. */
    public static Map<String, KeyCombination> allDefaults() {
        return Map.copyOf(DEFAULTS);
    }

    /* ─── Combinations ─── */

    /** The combinations that text fields or the system take, which cannot be shortcuts. */
    private static final List<KeyCombination> REFUSED = refused();

    private static List<KeyCombination> refused() {
        List<KeyCombination> r = new ArrayList<>();
        for (KeyCode k : List.of(KeyCode.C, KeyCode.V, KeyCode.X, KeyCode.A, KeyCode.Z, KeyCode.Y)) {
            r.add(new KeyCodeCombination(k, SC));
        }
        r.add(new KeyCodeCombination(KeyCode.Z, SC, SHIFT));
        if (MAC) {
            for (KeyCode k : List.of(KeyCode.Q, KeyCode.H, KeyCode.M, KeyCode.W, KeyCode.TAB, KeyCode.SPACE)) {
                r.add(new KeyCodeCombination(k, SC));
            }
            for (KeyCode k : List.of(KeyCode.D, KeyCode.H, KeyCode.M, KeyCode.ESCAPE)) {
                r.add(new KeyCodeCombination(k, SC, ALT));
            }
        } else {
            r.add(new KeyCodeCombination(KeyCode.F4, ALT));
        }
        return List.copyOf(r);
    }

    /** Whether a combination is refused: taken by the text fields or the system. */
    public static boolean isRefused(KeyCombination keys) {
        return REFUSED.stream().anyMatch(r -> sameKeys(r, keys));
    }

    /** Whether a combination has a modifier (Cmd, Ctrl, Alt), or is a function key. */
    public static boolean hasModifier(KeyCombination keys) {
        if (keys instanceof KeyCodeCombination k && k.getCode().isFunctionKey()) return true;
        return keys.getShortcut() == KeyCombination.ModifierValue.DOWN
            || keys.getControl() == KeyCombination.ModifierValue.DOWN
            || keys.getMeta() == KeyCombination.ModifierValue.DOWN
            || keys.getAlt() == KeyCombination.ModifierValue.DOWN;
    }

    /**
     * The combination of a key event, with "Shortcut" for the platform's shortcut key, or
     * null for a modifier key alone.
     */
    public static KeyCombination fromEvent(KeyEvent e) {
        KeyCode code = e.getCode();
        if (code == null || code.isModifierKey() || code == KeyCode.UNDEFINED) return null;
        List<Modifier> mods = new ArrayList<>();
        if (e.isShortcutDown()) mods.add(SC);
        if (e.isAltDown()) mods.add(ALT);
        if (e.isShiftDown()) mods.add(SHIFT);
        if (MAC ? e.isControlDown() : e.isMetaDown()) mods.add(MAC ? KeyCombination.CONTROL_DOWN : KeyCombination.META_DOWN);
        return new KeyCodeCombination(code, mods.toArray(Modifier[]::new));
    }

    /** Parses a saved combination; "" or an unreadable one gives null (no shortcut). */
    static KeyCombination parse(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return KeyCombination.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether two combinations are the same keys (Shortcut being Cmd or Ctrl, as on this platform). */
    public static boolean sameKeys(KeyCombination a, KeyCombination b) {
        if (a == null || b == null) return a == b;
        return Objects.equals(normal(a), normal(b));
    }

    private static String normal(KeyCombination k) {
        boolean shortcut = k.getShortcut() == KeyCombination.ModifierValue.DOWN;
        boolean ctrl = k.getControl() == KeyCombination.ModifierValue.DOWN || (!MAC && shortcut);
        boolean meta = k.getMeta() == KeyCombination.ModifierValue.DOWN || (MAC && shortcut);
        String code = k instanceof KeyCodeCombination kc ? kc.getCode().name() : k.getName();
        return (ctrl ? "C" : "") + (meta ? "M" : "")
            + (k.getAlt() == KeyCombination.ModifierValue.DOWN ? "A" : "")
            + (k.getShift() == KeyCombination.ModifierValue.DOWN ? "S" : "") + "+" + code;
    }
}
