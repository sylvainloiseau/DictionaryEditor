package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftgui.core.LiftOpenException;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/**
 * The error shown when a file cannot be opened: what is wrong in plain words, where in the
 * file (with the faulty line), and the technical details in an expandable area.
 */
final class OpenErrorDialog {

    private OpenErrorDialog() {}

    /**
     * Shows the error and waits.
     *
     * @param atStartup      the file is the last one, reopened at startup
     * @param removeFromRecent offered as a button when not null
     */
    static void show(LiftOpenException e, boolean atStartup, Runnable removeFromRecent) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(I18n.get("open.error.title"));
        String name = e.file() == null ? "" : e.file().getName();
        String header = I18n.get("open.error." + key(e.reason()) + ".header", name);
        a.setHeaderText(atStartup ? I18n.get("open.error.startup") + "\n" + header : header);

        VBox content = new VBox(8);
        Label explanation = new Label(e.getMessage());
        explanation.setWrapText(true);
        explanation.getStyleClass().add("open-error-message");
        content.getChildren().add(explanation);
        if (e.line() > 0) {
            Label position = new Label(e.column() > 0
                ? I18n.get("open.error.position", String.valueOf(e.line()), String.valueOf(e.column()))
                : I18n.get("open.error.line", String.valueOf(e.line())));
            position.getStyleClass().add("open-error-position");
            content.getChildren().add(position);
        }
        if (e.excerpt() != null) {
            Label excerpt = new Label(e.excerpt());
            excerpt.getStyleClass().add("open-error-excerpt");
            content.getChildren().add(excerpt);
        }
        if (e.file() != null) {
            Label path = new Label(e.file().getAbsolutePath());
            path.setWrapText(true);
            path.getStyleClass().add("open-error-path");
            content.getChildren().add(path);
        }
        a.getDialogPane().setContent(content);

        if (e.getCause() != null) {
            TextArea details = new TextArea(stackTrace(e.getCause()));
            details.setEditable(false);
            details.setPrefRowCount(10);
            details.getStyleClass().add("open-error-details");
            a.getDialogPane().setExpandableContent(details);
        }

        ButtonType remove = new ButtonType(I18n.get("open.error.removeRecent"), ButtonBar.ButtonData.LEFT);
        if (removeFromRecent != null) a.getButtonTypes().add(0, remove);
        Optional<ButtonType> choice = Dialogs.owned(a).showAndWait();
        if (removeFromRecent != null && choice.isPresent() && choice.get() == remove) removeFromRecent.run();
    }

    /** The part of the message keys for a reason: NOT_FOUND → notFound. */
    private static String key(LiftOpenException.Reason reason) {
        String[] words = reason.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) b.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        return b.toString();
    }

    private static String stackTrace(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }
}
