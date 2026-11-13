package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftgui.ui.table.FilterableTable;
import java.util.Optional;
import javafx.scene.Node;

/** A view shown in the central area (a table of objects, a configuration page…). */
public interface DictionaryView {

    /** The content of the central area. */
    Node node();

    /** The title shown above the content; null to use the view's name. */
    default String title() {
        return null;
    }

    /** Selects an object (and shows it in the editor panel); false when it is not listed. */
    default boolean select(Object object) {
        return false;
    }

    /** Called when the view is replaced: detach listeners on shared objects. */
    default void dispose() {
    }

    /** Redraws the cells after an edit made in the editor panel. */
    default void refreshCells() {
    }

    /** The label of the "add" button of the toolbar; null when the view has none. */
    default String addButtonText() {
        return null;
    }

    /** Action of the "add" button (and of the "create object" menu item). */
    default void onAdd() {
    }

    /** The table of the view, for the CSV export. */
    default Optional<FilterableTable<?>> table() {
        return Optional.empty();
    }
}
