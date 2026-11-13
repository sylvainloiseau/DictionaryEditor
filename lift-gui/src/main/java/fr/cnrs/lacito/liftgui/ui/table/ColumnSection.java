package fr.cnrs.lacito.liftgui.ui.table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A group of the columns available for a table, as the column chooser shows them: the
 * component's own columns, its fields and traits, the counts, the columns of an ancestor…
 *
 * @param id      stable identifier of the section
 * @param title   its title
 * @param columns its columns
 */
public record ColumnSection<T>(String id, String title, List<ColumnSpec<T>> columns) {

    public ColumnSection {
        columns = List.copyOf(columns);
    }

    /** The columns of all the sections, the first column of each id only. */
    public static <T> List<ColumnSpec<T>> flatten(List<ColumnSection<T>> sections) {
        List<ColumnSpec<T>> all = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ColumnSection<T> s : sections) {
            for (ColumnSpec<T> c : s.columns()) if (ids.add(c.id())) all.add(c);
        }
        return all;
    }
}
