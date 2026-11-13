package fr.cnrs.lacito.liftgui.ui.table;

import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The filters of each table, kept for the session: navigating away from a table and back
 * restores them. Only the full tables are remembered (a table restricted to a subset starts
 * without filters); everything is forgotten when another dictionary is opened.
 */
public final class FilterSessions {

    private final Map<String, Map<String, ColumnFilter>> saved = new HashMap<>();

    public Map<String, ColumnFilter> get(String table) {
        return saved.getOrDefault(table, Map.of());
    }

    public void put(String table, Map<String, ColumnFilter> filters) {
        if (filters.isEmpty()) saved.remove(table);
        else saved.put(table, new LinkedHashMap<>(filters));
    }

    public void clear() {
        saved.clear();
    }
}
