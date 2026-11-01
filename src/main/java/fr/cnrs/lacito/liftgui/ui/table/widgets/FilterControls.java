package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.table.Leaf;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

/** Creates the filter widget of a leaf column, by its {@link fr.cnrs.lacito.liftgui.ui.table.FilterKind}. */
public final class FilterControls {

    private FilterControls() {}

    public static FilterControl create(Leaf<?> leaf, FilterHost host) {
        FilterControl c = switch (leaf.filterKind()) {
            case TEXT -> new TextFilterControl(leaf, host);
            case OPTIONS -> new OptionFilterControl(leaf, host);
            case RANGE -> new RangeFilterControl(leaf, host);
        };
        return c;
    }

    /**
     * Keeps the mouse events of a widget placed in a column header from reaching the header:
     * a click in a filter must neither sort the column nor start dragging it. {@code node}
     * must be a container of the widget, not the widget itself: a control ignores the events
     * consumed on its own node.
     */
    public static void isolate(Node node) {
        for (var type : java.util.List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED, MouseEvent.MOUSE_DRAGGED)) {
            node.addEventHandler(type, Event::consume);
        }
    }
}
