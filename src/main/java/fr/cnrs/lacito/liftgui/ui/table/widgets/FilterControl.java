package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import javafx.scene.Node;

/** The filter widget of a leaf column, shown in its header. */
public interface FilterControl {

    Node node();

    /** Shows {@code filter} (null: no filter); called when the filters change elsewhere. */
    void sync(ColumnFilter filter);

    /** Gives the focus to the widget. */
    void focus();

    /** Opens the widget's popover (or focuses its field). */
    void open();
}
