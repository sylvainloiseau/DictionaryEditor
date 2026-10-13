package fr.cnrs.lacito.liftgui.ui.app;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;

/**
 * Detects the user's edits in the editor and table panels: typing in an editable text
 * control, pressing a button, or acting on a focused control (combo box, date picker…).
 * Navigation controls are ignored: hyperlinks and nodes inside a {@code no-dirty} style class.
 */
public final class DirtyTracker {

    private DirtyTracker() {}

    /** Calls {@code onEdit} for each edit made in one of the containers. */
    public static void install(Runnable onEdit, Pane... containers) {
        for (Pane container : containers) {
            container.addEventFilter(KeyEvent.KEY_TYPED, e -> {
                if (isEditEvent(e)) onEdit.run();
            });
            container.addEventFilter(ActionEvent.ACTION, e -> {
                if (isEditEvent(e)) onEdit.run();
            });
        }
    }

    static boolean isEditEvent(Event e) {
        if (!(e.getTarget() instanceof Node node)) return false;
        if (e instanceof KeyEvent ke) {
            if (!(node instanceof TextInputControl tic) || !tic.isEditable()) return false;
            String c = ke.getCharacter();
            // Tab, Enter and Escape move around; they do not change the text.
            if (c.isEmpty() || ke.isShortcutDown() || "\t\r\n\u001b".contains(c)) return false;
        } else if (!(node instanceof ButtonBase) && !node.isFocused()) {
            // Combo boxes and date pickers also fire actions when their value is set by code
            // (e.g. while an editor is filled); only the control the user operates has the focus.
            return false;
        }
        if (node instanceof Hyperlink) return false;
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("no-dirty")) return false;
        }
        return true;
    }
}
