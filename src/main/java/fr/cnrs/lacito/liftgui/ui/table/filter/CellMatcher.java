package fr.cnrs.lacito.liftgui.ui.table.filter;

import java.util.List;
import java.util.function.Supplier;

/** A compiled {@link ColumnFilter}: tests the text of a cell (and its values, computed on demand). */
@FunctionalInterface
public interface CellMatcher {

    boolean test(String text, Supplier<List<String>> values);

    CellMatcher ALL = (text, values) -> true;
}
