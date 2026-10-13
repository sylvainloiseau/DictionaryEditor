package fr.cnrs.lacito.liftgui.ui.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which columns of a catalog are shown, and in which order.
 * <p>
 * {@link #defaults(List)} gives the columns visible by default. A layout chosen by the user
 * (a list of column ids) is applied with {@link #apply(List)}; ids that no longer exist are
 * ignored, so a saved layout survives changes of the catalog.
 */
public record ColumnLayout(List<String> columnIds) {

    public ColumnLayout {
        columnIds = List.copyOf(columnIds);
    }

    /** The columns visible by default, in catalog order. */
    public static <T> ColumnLayout defaults(List<ColumnSpec<T>> catalog) {
        return new ColumnLayout(catalog.stream().filter(ColumnSpec::visible).map(ColumnSpec::id).toList());
    }

    /** The specs of the catalog listed in this layout, in the layout's order. */
    public <T> List<ColumnSpec<T>> apply(List<ColumnSpec<T>> catalog) {
        Map<String, ColumnSpec<T>> byId = catalog.stream()
            .collect(Collectors.toMap(ColumnSpec::id, Function.identity(), (a, b) -> a));
        List<ColumnSpec<T>> shown = new ArrayList<>();
        for (String id : columnIds) {
            ColumnSpec<T> spec = byId.get(id);
            if (spec != null) shown.add(spec);
        }
        return shown;
    }
}
