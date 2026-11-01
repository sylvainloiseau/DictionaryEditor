package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.table.filter.FilterEngine;
import fr.cnrs.lacito.liftgui.ui.table.filter.FilterModel;
import java.util.List;
import java.util.Optional;

/** What a filter widget needs from its table: the filters, and the data shown in its popover. */
public interface FilterHost {

    FilterModel model();

    /** The values of a column with their counts (over the rows of the other filters). */
    List<FilterEngine.OptionCount> options(String leafId);

    /** The smallest and largest values of a column (over the rows of the other filters). */
    Optional<FilterEngine.Bounds> bounds(String leafId);

    /** The histogram of a column (over the rows of the other filters). */
    List<FilterEngine.Bin> histogram(String leafId);

    /** Moves the focus to the first row of the table. */
    void focusTable();
}
