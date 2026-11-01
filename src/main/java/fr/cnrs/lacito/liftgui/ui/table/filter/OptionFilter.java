package fr.cnrs.lacito.liftgui.ui.table.filter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A choice of values: keeps the rows of which <b>any</b> value of the cell is selected
 * (a cell may hold several values: a repeated trait…), and the empty cells if
 * {@code includeEmpty}. With {@code negate}, keeps the others.
 */
public record OptionFilter(Set<String> selected, boolean includeEmpty, boolean negate) implements ColumnFilter {

    public OptionFilter {
        selected = selected == null ? Set.of() : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    public static OptionFilter of(String... values) {
        return new OptionFilter(new LinkedHashSet<>(List.of(values)), false, false);
    }

    public OptionFilter withSelected(Set<String> s) { return new OptionFilter(s, includeEmpty, negate); }
    public OptionFilter withIncludeEmpty(boolean b) { return new OptionFilter(selected, b, negate); }
    public OptionFilter withNegate(boolean b) { return new OptionFilter(selected, includeEmpty, b); }

    @Override
    public boolean active() {
        return !selected.isEmpty() || includeEmpty;
    }

    @Override
    public CellMatcher compile() {
        if (!active()) return CellMatcher.ALL;
        Set<String> sel = selected;
        return (t, values) -> {
            List<String> v = values.get();
            boolean match = v.isEmpty() ? includeEmpty : v.stream().anyMatch(sel::contains);
            return match != negate;
        };
    }
}
