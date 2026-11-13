package fr.cnrs.lacito.liftfx.internal;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The dialogs the editors open. */
public final class Dialogs {

    private Dialogs() {}

    /**
     * Gives a dialog the active window as owner, so that it is centred on it and stays above
     * it.
     */
    public static <D extends Dialog<?>> D owned(D dialog) {
        // A stage, not a popup (a menu, a tooltip) that may be showing.
        Window owner = Window.getWindows().stream().filter(w -> w instanceof Stage && w.isShowing() && w.isFocused())
            .findFirst()
            .orElseGet(() -> Window.getWindows().stream().filter(w -> w instanceof Stage && w.isShowing()).findFirst()
                .orElse(null));
        if (owner != null && dialog.getOwner() == null && !dialog.isShowing()) dialog.initOwner(owner);
        return dialog;
    }

    public static void error(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        owned(a).showAndWait();
    }

    /** Asks for confirmation; true when the user chose OK. */
    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        return owned(a).showAndWait().filter(r -> r == ButtonType.OK).isPresent();
    }
}
