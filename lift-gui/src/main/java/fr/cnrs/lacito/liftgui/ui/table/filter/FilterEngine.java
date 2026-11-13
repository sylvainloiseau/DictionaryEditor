package fr.cnrs.lacito.liftgui.ui.table.filter;

import fr.cnrs.lacito.liftgui.core.LiftDate;
import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Applies the filters of a {@link FilterModel} and the search to rows, and computes what the
 * filter widgets show: the values of a column with their counts, the bounds and the
 * histogram of a numeric or date column, the filters that hide a row.
 * <p>
 * A row is kept when it passes <b>every</b> column filter (AND between columns) and the
 * search. The values, bounds and histogram of a column are computed over the rows that pass
 * the <i>other</i> filters, so that they show what choosing in this column would give.
 * <p>
 * The texts of the cells are cached per row ({@link #invalidate()} clears the cache: after an
 * edit, an undo, a change of columns).
 */
public final class FilterEngine<T> {

    /** A value of a column and the number of rows having it. */
    public record OptionCount(String value, int count) {}

    /** The smallest and largest values of a column, as texts. */
    public record Bounds(String min, String max) {}

    /**
     * A bar of a histogram.
     *
     * @param label the text under the bar
     * @param from  the minimum of a range filter for this bar
     * @param to    the maximum of a range filter for this bar
     */
    public record Bin(String label, String from, String to, int count) {}

    /** Why a row is hidden: the ids of the filters it fails, and whether the search hides it. */
    public record Hiding(Set<String> filters, boolean search) {
        public boolean hidden() {
            return search || !filters.isEmpty();
        }
    }

    private record Compiled(int column, String id, CellMatcher matcher) {}

    private List<? extends FilterColumn<T>> columns = List.of();
    private Map<String, Integer> index = Map.of();
    private Function<T, String> searchText = row -> "";
    private final Map<T, String[]> cache = new IdentityHashMap<>();
    private final Map<T, String> searchCache = new IdentityHashMap<>();

    public FilterEngine(List<? extends FilterColumn<T>> columns) {
        setColumns(columns);
    }

    public void setColumns(List<? extends FilterColumn<T>> cols) {
        this.columns = List.copyOf(cols);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) idx.putIfAbsent(columns.get(i).id(), i);
        this.index = idx;
        invalidate();
    }

    public List<? extends FilterColumn<T>> columns() {
        return columns;
    }

    /** The text searched by the global search for a row. */
    public void setSearchText(Function<T, String> fn) {
        this.searchText = fn == null ? row -> "" : fn;
        searchCache.clear();
    }

    /** Clears the cached cell texts. */
    public void invalidate() {
        cache.clear();
        searchCache.clear();
    }

    /** The (cached) text of a cell. */
    public String text(T row, int column) {
        String[] texts = cache.computeIfAbsent(row, r -> new String[columns.size()]);
        String s = texts[column];
        if (s == null) {
            s = columns.get(column).text(row);
            if (s == null) s = "";
            texts[column] = s;
        }
        return s;
    }

    private String search(T row) {
        return searchCache.computeIfAbsent(row, r -> {
            String s = searchText.apply(r);
            return TextMatch.fold(s == null ? "" : s);
        });
    }

    private List<Compiled> compile(Map<String, ColumnFilter> filters) {
        List<Compiled> out = new ArrayList<>();
        filters.forEach((id, f) -> {
            Integer col = index.get(id);
            if (col != null && f != null && f.active()) out.add(new Compiled(col, id, f.compile()));
        });
        return out;
    }

    private static String query(String search) {
        return search == null ? "" : TextMatch.fold(search.trim());
    }

    private boolean passes(T row, List<Compiled> compiled, String q, int ignored) {
        if (row == null) return false;
        for (Compiled c : compiled) {
            if (c.column() == ignored) continue;
            int col = c.column();
            if (!c.matcher().test(text(row, col), () -> columns.get(col).values(row))) return false;
        }
        return q.isEmpty() || search(row).contains(q);
    }

    /** The test of the rows for these filters and this search. */
    public Predicate<T> predicate(Map<String, ColumnFilter> filters, String search) {
        List<Compiled> compiled = compile(filters);
        String q = query(search);
        return row -> passes(row, compiled, q, -1);
    }

    /** The rows that pass every filter except the one of column {@code id}. */
    private List<T> others(Collection<T> rows, String id, Map<String, ColumnFilter> filters, String search) {
        List<Compiled> compiled = compile(filters);
        String q = query(search);
        int ignored = index.getOrDefault(id, -1);
        return rows.stream().filter(r -> passes(r, compiled, q, ignored)).toList();
    }

    /**
     * The values of column {@code id} with the number of rows having each (a row is counted
     * once per distinct value of its cell), over the rows that pass the other filters;
     * values found only in the rows hidden by the other filters come with 0. The empty
     * value ("") counts the rows whose cell is empty. Sorted by decreasing count, then
     * alphabetically.
     */
    public List<OptionCount> options(Collection<T> rows, String id, Map<String, ColumnFilter> filters, String search) {
        Integer col = index.get(id);
        if (col == null) return List.of();
        FilterColumn<T> column = columns.get(col);
        Map<String, Integer> counts = new HashMap<>();
        int empty = 0;
        for (T row : others(rows, id, filters, search)) {
            List<String> values = column.values(row);
            if (values.isEmpty()) empty++;
            for (String v : new LinkedHashSet<>(values)) counts.merge(v, 1, Integer::sum);
        }
        for (T row : rows) for (String v : column.values(row)) counts.putIfAbsent(v, 0);
        java.text.Collator collator = java.text.Collator.getInstance();
        List<OptionCount> out = new ArrayList<>();
        counts.forEach((v, n) -> out.add(new OptionCount(v, n)));
        out.sort((a, b) -> a.count() != b.count() ? Integer.compare(b.count(), a.count()) : collator.compare(a.value(), b.value()));
        out.add(new OptionCount("", empty));
        return out;
    }

    /** The smallest and largest values of a number or date column, over the rows that pass the other filters. */
    public Optional<Bounds> bounds(Collection<T> rows, String id, Map<String, ColumnFilter> filters, String search) {
        Integer col = index.get(id);
        if (col == null) return Optional.empty();
        ValueType type = columns.get(col).type();
        String min = null, max = null;
        Comparable<Object> lo = null, hi = null;
        for (T row : others(rows, id, filters, search)) {
            String t = text(row, col);
            @SuppressWarnings("unchecked")
            Comparable<Object> k = (Comparable<Object>) type.key(t).orElse(null);
            if (k == null) continue;
            if (lo == null || k.compareTo(lo) < 0) { lo = k; min = t; }
            if (hi == null || k.compareTo(hi) > 0) { hi = k; max = t; }
        }
        return min == null ? Optional.empty() : Optional.of(new Bounds(min, max));
    }

    /**
     * The histogram of a number or date column over the rows that pass the other filters:
     * one bar per integer for counts spanning at most 20 values (else 20 equal bars); per
     * month for dates spanning at most 3 years, per year up to 60 years, else per decade.
     */
    public List<Bin> histogram(Collection<T> rows, String id, Map<String, ColumnFilter> filters, String search) {
        Integer col = index.get(id);
        if (col == null) return List.of();
        ValueType type = columns.get(col).type();
        List<String> texts = others(rows, id, filters, search).stream().map(r -> text(r, col)).toList();
        return type == ValueType.DATE ? dateBins(texts) : numberBins(texts);
    }

    static List<Bin> numberBins(List<String> texts) {
        List<Double> values = texts.stream().map(ValueType::number).filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) return List.of();
        double lo = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double hi = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        boolean integers = values.stream().allMatch(v -> v == Math.rint(v));
        List<Bin> bins = new ArrayList<>();
        if (integers && hi - lo <= 20) {
            int[] counts = new int[(int) (hi - lo) + 1];
            for (double v : values) counts[(int) (v - lo)]++;
            for (int i = 0; i < counts.length; i++) {
                String s = fmt(lo + i);
                bins.add(new Bin(s, s, s, counts[i]));
            }
            return bins;
        }
        int n = 20;
        double w = (hi - lo) / n;
        int[] counts = new int[n];
        for (double v : values) counts[Math.min(n - 1, (int) ((v - lo) / w))]++;
        for (int i = 0; i < n; i++) {
            String from = fmt(lo + i * w);
            String to = fmt(i == n - 1 ? hi : lo + (i + 1) * w);
            bins.add(new Bin(from, from, to, counts[i]));
        }
        return bins;
    }

    static List<Bin> dateBins(List<String> texts) {
        List<LocalDate> days = texts.stream().map(LiftDate::parse).flatMap(Optional::stream).map(LiftDate::startDay).toList();
        if (days.isEmpty()) return List.of();
        LocalDate lo = days.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate hi = days.stream().max(LocalDate::compareTo).orElseThrow();
        TreeMap<String, Integer> counts = new TreeMap<>();
        List<Bin> bins = new ArrayList<>();
        YearMonth first = YearMonth.from(lo), last = YearMonth.from(hi);
        long months = java.time.temporal.ChronoUnit.MONTHS.between(first, last);
        if (months < 36) {
            for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) counts.put(m.toString(), 0);
            for (LocalDate d : days) counts.merge(YearMonth.from(d).toString(), 1, Integer::sum);
            counts.forEach((k, v) -> bins.add(new Bin(k, k, k, v)));
        } else if (hi.getYear() - lo.getYear() < 60) {
            for (int y = lo.getYear(); y <= hi.getYear(); y++) counts.put(String.valueOf(y), 0);
            for (LocalDate d : days) counts.merge(String.valueOf(d.getYear()), 1, Integer::sum);
            counts.forEach((k, v) -> bins.add(new Bin(k, k, k, v)));
        } else {
            for (int y = lo.getYear() / 10 * 10; y <= hi.getYear(); y += 10) counts.put(String.valueOf(y), 0);
            for (LocalDate d : days) counts.merge(String.valueOf(d.getYear() / 10 * 10), 1, Integer::sum);
            counts.forEach((k, v) -> bins.add(new Bin(k + "s", k, String.valueOf(Integer.parseInt(k) + 9), v)));
        }
        return bins;
    }

    private static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    /** The filters (and the search) that hide {@code row}. */
    public Hiding hiding(T row, Map<String, ColumnFilter> filters, String search) {
        Set<String> failing = new LinkedHashSet<>();
        for (Compiled c : compile(filters)) {
            int col = c.column();
            if (!c.matcher().test(text(row, col), () -> columns.get(col).values(row))) failing.add(c.id());
        }
        String q = query(search);
        return new Hiding(failing, !q.isEmpty() && !search(row).contains(q));
    }
}
