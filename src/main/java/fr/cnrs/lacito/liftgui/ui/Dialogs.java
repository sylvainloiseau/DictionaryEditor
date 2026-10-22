package fr.cnrs.lacito.liftgui.ui;

import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;

/** The standard dialogs of the application. */
public final class Dialogs {

    private Dialogs() {}

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    /**
     * A warning with a list of details (one per line), shown in an expandable, read-only
     * text area.
     */
    public static void warnings(String title, String header, java.util.List<String> details) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(header);
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(String.join("\n", details));
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(12);
        a.getDialogPane().setExpandableContent(area);
        a.getDialogPane().setExpanded(true);
        a.showAndWait();
    }

    /** Asks for confirmation; true when the user chose OK. */
    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        return a.showAndWait().filter(r -> r == ButtonType.OK).isPresent();
    }

    /**
     * Asks for a text, e.g. the form of a new entry. Returns the trimmed text, or empty when
     * cancelled or blank.
     *
     * @param label what is asked (shown above the field)
     */
    public static Optional<String> askText(String title, String label) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(title);
        dlg.setHeaderText(label);
        return dlg.showAndWait().map(String::trim).filter(t -> !t.isEmpty());
    }

    /** The first of a set of languages in alphabetical order, or empty. */
    public static Optional<String> firstLanguage(java.util.Collection<String> languages) {
        return languages.stream().sorted().findFirst();
    }

    /**
     * Asks for a type among the features of {@code set}; when the set is empty, the type is
     * typed in freely. Returns the trimmed type, or empty when cancelled or blank.
     */
    public static Optional<String> chooseType(String title, FeatureSet set) {
        List<String> types = set == null ? List.of() : set.getFeatures().values().stream()
            .map(Feature::getId)
            .sorted(Comparator.naturalOrder())
            .toList();
        Dialog<String> dlg = types.isEmpty() ? new TextInputDialog() : new ChoiceDialog<>(types.get(0), types);
        dlg.setTitle(title);
        dlg.setHeaderText(I18n.get("col.type"));
        return dlg.showAndWait().map(String::trim).filter(t -> !t.isEmpty());
    }
}
