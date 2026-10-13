package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.controls.EditorSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The editor panel on the right: a title, a code, and the editor of the selected object.
 * It also remembers the entry owning the object shown, whose modification date is updated
 * when the user edits it ({@link #touchEditedEntry()}).
 */
public final class EditorPanel {

    private final VBox container;
    private final Label title;
    private final Label code;
    private LiftEntry editedEntry;

    public EditorPanel(VBox container, Label title, Label code) {
        this.container = container;
        this.title = title;
        this.code = code;
    }

    /**
     * Clears the panel and sets its header.
     *
     * @param edited the object about to be shown; its entry's modification date follows the
     *               user's edits. Null for read-only content.
     */
    public EditorPanel show(String titleText, String codeText, Object edited) {
        container.getChildren().clear();
        title.setText(titleText == null ? "" : titleText);
        code.setText(codeText == null ? "" : codeText);
        editedEntry = DictionaryQueries.owningEntry(edited);
        return this;
    }

    /** Empties the panel ("select an element"). */
    public void clear() {
        show(I18n.get("panel.selectElement"), "", null);
    }

    public EditorPanel add(Node... nodes) {
        container.getChildren().addAll(nodes);
        return this;
    }

    /** Shows read-only values (label → value). */
    public EditorPanel summary(String titleText, Map<String, String> values) {
        show(titleText, "", null);
        GridPane g = grid();
        int row = 0;
        for (Map.Entry<String, String> e : values.entrySet()) addReadOnlyRow(g, row++, e.getKey(), e.getValue());
        return add(g);
    }

    /** Adds a full-width navigation button (e.g. "Access objects", "Go to parent"). */
    public EditorPanel navButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("example-add-button", "no-dirty");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return add(b);
    }

    /** Adds a "Back to …" button. */
    public EditorPanel backButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("example-add-button", "back-btn", "no-dirty");
        b.setOnAction(e -> action.run());
        return add(b);
    }

    /** Adds a collapsible section. */
    public EditorPanel section(String titleText, Node content, boolean expanded) {
        TitledPane tp = new TitledPane(titleText, content);
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        return add(tp);
    }

    /** Sets the modification date of the entry being edited to now. */
    public void touchEditedEntry() {
        if (editedEntry != null) {
            editedEntry.setDateModified(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        }
    }

    /** The red "Delete" button shown at the top of an object's editor. */
    public static Button deleteButton() {
        Button b = new Button(I18n.get(Keys.BTN_DELETE));
        b.getStyleClass().add("delete-btn");
        return b;
    }

    public static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        return g;
    }

    public static void addReadOnlyRow(GridPane grid, int row, String label, String value) {
        grid.add(new Label(label), 0, row);
        TextField tf = new TextField(value == null ? "" : value);
        tf.setEditable(false);
        tf.setFocusTraversable(false);
        tf.getStyleClass().add("read-only-meta-field");
        GridPane.setHgrow(tf, Priority.ALWAYS);
        grid.add(tf, 1, row);
    }

    /** A read-only field with the look of the editors. */
    public static TextField readOnlyField(String value) {
        return EditorSupport.readOnlyField(value);
    }
}
