package fr.cnrs.lacito.liftgui.ui.table.filter;

import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import java.util.List;

/** What the {@link FilterEngine} needs of a column: its id, the text and values of its cells, its type. */
public interface FilterColumn<T> {

    String id();

    String text(T row);

    List<String> values(T row);

    ValueType type();
}
