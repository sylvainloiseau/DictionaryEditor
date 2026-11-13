package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One component in a {@link ComponentSection}: a header row (a short label, and the delete
 * button on the right) above the editor of the component.
 *
 * The header can take the focus: Delete or Backspace deletes the component, Enter or Space
 * folds it when it is collapsible. A right-click (or the context-menu key) offers to add
 * the components it can hold, and to delete it.
 */
public final class ItemCard extends VBox {

    /** The key, in {@link #getProperties()}, of the component a card shows. */
    public static final String COMPONENT = "lift-component";

    private static final PseudoClass COLLAPSED = PseudoClass.getPseudoClass("collapsed");

    private final Object component;
    private final HBox header;
    private final Label titleLabel;
    private final Node content;
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", true);
    private Button toggle;
    private Button deleteButton;
    private Runnable onDelete;

    public ItemCard(Object component, String title, Node content) {
        super(2);
        this.component = component;
        this.content = content;
        getStyleClass().add("item-card");
        getProperties().put(COMPONENT, component);

        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("item-card-title");
        titleLabel.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header = new HBox(4, titleLabel, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("item-card-header");
        header.setFocusTraversable(true);
        header.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) header.requestFocus();
        });
        header.setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) && onDelete != null) {
                e.consume();
                onDelete.run();
            } else if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && toggle != null) {
                e.consume();
                setExpanded(!isExpanded());
            }
        });
        header.setOnContextMenuRequested(e -> {
            e.consume();
            ContextMenu menu = contextMenu();
            if (!menu.getItems().isEmpty()) menu.show(header, e.getScreenX(), e.getScreenY());
        });

        content.visibleProperty().bind(expanded);
        content.managedProperty().bind(expanded);
        expanded.addListener((obs, o, n) -> {
            pseudoClassStateChanged(COLLAPSED, !n);
            if (toggle != null) toggle.setText(n ? "▾" : "▸");
        });
        getChildren().addAll(header, content);
    }

    /** Makes the card foldable, with an arrow before its label. */
    public ItemCard collapsible(boolean expandedAtFirst) {
        toggle = new Button();
        toggle.getStyleClass().add("item-card-toggle");
        toggle.setFocusTraversable(false);
        toggle.setOnAction(e -> setExpanded(!isExpanded()));
        header.getChildren().addFirst(toggle);
        setExpanded(expandedAtFirst);
        toggle.setText(expandedAtFirst ? "▾" : "▸");
        return this;
    }

    /** Shows the delete button, which runs {@code delete}. */
    public void setOnDelete(Runnable delete, String tooltip) {
        this.onDelete = delete;
        if (deleteButton == null) {
            deleteButton = new Button("✕");
            deleteButton.getStyleClass().add("item-delete");
            deleteButton.setFocusTraversable(false);
            header.getChildren().add(deleteButton);
        }
        deleteButton.setTooltip(new Tooltip(tooltip));
        deleteButton.setAccessibleText(tooltip);
        deleteButton.setOnAction(e -> delete.run());
    }

    public Object component() {
        return component;
    }

    public Node content() {
        return content;
    }

    public HBox header() {
        return header;
    }

    public String title() {
        return titleLabel.getText();
    }

    /** The delete button, or null when the component cannot be deleted here. */
    public Button deleteButton() {
        return deleteButton;
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public void setExpanded(boolean value) {
        expanded.set(value);
    }

    public BooleanProperty expandedProperty() {
        return expanded;
    }

    /** "Add …" for each section of the component, then "Delete". */
    ContextMenu contextMenu() {
        ContextMenu menu = new ContextMenu();
        List<MenuItem> items = new ArrayList<>();
        for (ComponentSection s : ComponentSection.sectionsOf(content, component)) {
            if (s.canAdd()) items.add(s.addMenuItem());
        }
        menu.getItems().addAll(items);
        if (onDelete != null) {
            if (!items.isEmpty()) menu.getItems().add(new SeparatorMenuItem());
            MenuItem delete = new MenuItem(I18n.get("btn.delete"));
            delete.getStyleClass().add("delete-menu-item");
            delete.setOnAction(e -> onDelete.run());
            menu.getItems().add(delete);
        }
        return menu;
    }

    /** The card a node is in, or null. */
    public static ItemCard of(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n instanceof ItemCard c) return c;
        }
        return null;
    }
}
