package fr.cnrs.lacito.liftgui.ui.table.filter;

import fr.cnrs.lacito.liftgui.core.LiftDate;
import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import java.util.Optional;

/**
 * A minimum and a maximum, both optional and inclusive, for numbers and dates. A date (which
 * may be partial: {@code 2023}, {@code 2023-05}) is kept when its interval overlaps the
 * range ({@link LiftDate#overlaps}); the bounds may be partial too ("to 2023" = until the end
 * of 2023). Empty cells are kept only if {@code includeEmpty}.
 *
 * @param type {@link ValueType#NUMBER} or {@link ValueType#DATE}
 */
public record RangeFilter(ValueType type, String min, String max, boolean includeEmpty) implements ColumnFilter {

    public RangeFilter {
        type = type == ValueType.DATE ? ValueType.DATE : ValueType.NUMBER;
        min = min == null ? "" : min.trim();
        max = max == null ? "" : max.trim();
    }

    public RangeFilter withMin(String m) { return new RangeFilter(type, m, max, includeEmpty); }
    public RangeFilter withMax(String m) { return new RangeFilter(type, min, m, includeEmpty); }
    public RangeFilter withIncludeEmpty(boolean b) { return new RangeFilter(type, min, max, b); }

    @Override
    public boolean active() {
        return (bound(min) != null || bound(max) != null) && error().isEmpty();
    }

    @Override
    public Optional<String> error() {
        if (!min.isEmpty() && bound(min) == null) return Optional.of(min);
        if (!max.isEmpty() && bound(max) == null) return Optional.of(max);
        return Optional.empty();
    }

    private Object bound(String s) {
        if (s.isEmpty()) return null;
        return type == ValueType.DATE ? LiftDate.parse(s).orElse(null) : ValueType.number(s);
    }

    @Override
    public CellMatcher compile() {
        if (!active()) return CellMatcher.ALL;
        if (type == ValueType.DATE) {
            LiftDate lo = (LiftDate) bound(min);
            LiftDate hi = (LiftDate) bound(max);
            return (t, v) -> {
                Optional<LiftDate> d = LiftDate.parse(t);
                return d.isEmpty() ? includeEmpty : d.get().overlaps(lo, hi);
            };
        }
        Double lo = (Double) bound(min);
        Double hi = (Double) bound(max);
        return (t, v) -> {
            Double n = ValueType.number(t);
            if (n == null) return includeEmpty;
            return (lo == null || n >= lo) && (hi == null || n <= hi);
        };
    }
}
