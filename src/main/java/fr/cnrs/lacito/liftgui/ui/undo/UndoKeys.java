package fr.cnrs.lacito.liftgui.ui.undo;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

/**
 * Routes the undo and redo shortcuts to the application's history, from anywhere in the
 * window, text fields included.
 *
 * A text field has an undo of its own, which would take ⌘Z / Ctrl+Z first and undo
 * something else than the menu's Undo: there is a single history. The filter runs before
 * the focused control sees the key, and consumes it.
 */
public final class UndoKeys {

    /** ⌘Z / Ctrl+Z. */
    public static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    /** ⇧⌘Z / Ctrl+Shift+Z (the macOS redo). */
    public static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    /** ⌘Y / Ctrl+Y (the Windows and Linux redo). */
    public static final KeyCombination REDO_ALT = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);

    private UndoKeys() {}

    public static void install(Scene scene, Runnable undo, Runnable redo) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (UNDO.match(e)) {
                e.consume();
                undo.run();
            } else if (REDO.match(e) || REDO_ALT.match(e)) {
                e.consume();
                redo.run();
            }
        });
    }
}
