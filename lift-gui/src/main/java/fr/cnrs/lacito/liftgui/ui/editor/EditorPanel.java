package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The editor panel on the right: a title, a code, and the editor of the selected object,
 * which it remembers ({@link #shown()}).
 *
 * When the empty sections are hidden ({@link ComponentSection#HIDE_EMPTY}), an "Add…" menu
 * at the end of the panel offers the hidden sections of the object shown.
 */
public final class EditorPanel {

    private final VBox container;
    private final Label title;
    private final Label code;
    private Object shown;
    /** Incremented each time the panel shows something (again): the editor was rebuilt. */
    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper(this, "revision", 0);
    private final MenuButton addHidden = new MenuButton(I18n.get("menu.addHidden"));

    public EditorPanel(VBox container, Label title, Label code) {
        this.container = container;
        this.title = title;
        this.code = code;
        addHidden.setId("addHiddenSections");
        addHidden.getStyleClass().add("add-hidden-sections");
        addHidden.getItems().add(new MenuItem());
        addHidden.setOnShowing(e -> addHidden.getItems().setAll(hiddenSectionItems()));
        addHidden.visibleProperty().bind(ComponentSection.HIDE_EMPTY);
        addHidden.managedProperty().bind(addHidden.visibleProperty());
    }

    /**
     * Clears the panel and sets its header.
     *
     * @param edited the object about to be shown; null for read-only content
     */
    public EditorPanel show(String titleText, String codeText, Object edited) {
        container.getChildren().clear();
        title.setText(titleText == null ? "" : titleText);
        code.setText(codeText == null ? "" : codeText);
        shown = edited;
        if (edited != null) container.getChildren().add(addHidden);
        revision.set(revision.get() + 1);
        return this;
    }

    /** Changes each time the panel shows an object, or shows it again after a change. */
    public ReadOnlyIntegerProperty revisionProperty() {
        return revision.getReadOnlyProperty();
    }

    /** The node holding the panel's content. */
    public Node container() {
        return container;
    }

    /** "Add a note"… for each hidden section of the object shown. */
    private List<MenuItem> hiddenSectionItems() {
        List<MenuItem> items = ComponentSection.sectionsOf(container, shown).stream()
            .filter(s -> !s.isVisible() && s.canAdd())
            .map(ComponentSection::addMenuItem)
            .toList();
        if (!items.isEmpty()) return items;
        MenuItem none = new MenuItem(I18n.get("menu.addHidden"));
        none.setDisable(true);
        return List.of(none);
    }

    /** Empties the panel ("select an element"). */
    public void clear() {
        show(I18n.get("panel.selectElement"), "", null);
    }

    public EditorPanel add(Node... nodes) {
        container.getChildren().addAll(nodes);
        // The "Add…" menu stays at the end.
        if (container.getChildren().remove(addHidden)) container.getChildren().add(addHidden);
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
        b.getStyleClass().addAll("panel-nav-button", "no-dirty");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return add(b);
    }

    /** Adds a "Back to …" button. */
    public EditorPanel backButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().addAll("panel-nav-button", "back-btn", "no-dirty");
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

    /** The object the panel shows; null when it shows nothing editable. */
    public Object shown() {
        return shown;
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
