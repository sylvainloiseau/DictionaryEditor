package fr.cnrs.lacito.liftgui.ui.table.filter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

/**
 * The filters of a table, by leaf id, in the order they were set (the order of the chips).
 * Only active filters are kept: setting an inactive one removes the filter of that column.
 */
public final class FilterModel {

    private final ObservableMap<String, ColumnFilter> filters = FXCollections.observableMap(new LinkedHashMap<>());

    public ObservableMap<String, ColumnFilter> filters() {
        return filters;
    }

    public Optional<ColumnFilter> get(String leafId) {
        return Optional.ofNullable(filters.get(leafId));
    }

    /** Sets the filter of a column; null or inactive removes it. */
    public void set(String leafId, ColumnFilter filter) {
        if (filter == null || !filter.active()) filters.remove(leafId);
        else if (!filter.equals(filters.get(leafId))) filters.put(leafId, filter);
    }

    public void remove(String leafId) {
        filters.remove(leafId);
    }

    public boolean isEmpty() {
        return filters.isEmpty();
    }

    public void clear() {
        filters.clear();
    }

    /** A copy of the filters, to restore later. */
    public Map<String, ColumnFilter> snapshot() {
        return new LinkedHashMap<>(filters);
    }

    /** Replaces the filters by {@code saved}. */
    public void restore(Map<String, ColumnFilter> saved) {
        filters.clear();
        saved.forEach(this::set);
    }
}
