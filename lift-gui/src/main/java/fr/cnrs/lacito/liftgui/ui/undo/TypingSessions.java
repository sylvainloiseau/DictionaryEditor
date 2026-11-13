package fr.cnrs.lacito.liftgui.ui.undo;

import fr.cnrs.lacito.liftgui.undo.History;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;

/**
 * Tells the history which field is being typed in, so that all that is typed in a field
 * while it has the focus is one undo step ("Typing in gloss (fr)").
 *
 * Only the editable text fields of the editor panel and of the tables count: the search
 * field and the navigation (style class {@code no-dirty}) change nothing.
 */
public final class TypingSessions {

    private TypingSessions() {}

    public static void install(Scene scene, History history, Node... containers) {
        scene.focusOwnerProperty().addListener((obs, old, now) -> {
            if (old != null) history.endTyping(old);
            if (isTypingField(now, containers)) history.beginTyping(now);
        });
    }

    static boolean isTypingField(Node node, Node... containers) {
        if (!(node instanceof TextInputControl tic) || !tic.isEditable()) return false;
        boolean inside = false;
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("no-dirty")) return false;
            for (Node c : containers) if (n == c) inside = true;
        }
        return inside;
    }
}
