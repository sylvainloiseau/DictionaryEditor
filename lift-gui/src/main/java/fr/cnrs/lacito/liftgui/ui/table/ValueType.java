package fr.cnrs.lacito.liftgui.ui.table;

import fr.cnrs.lacito.liftgui.core.LiftDate;
import java.text.Collator;
import java.util.Comparator;
import java.util.Optional;

/**
 * The kind of value a column holds. It decides how the column is sorted, which filter it
 * gets by default ({@link FilterKind}) and how its cells are aligned.
 */
public enum ValueType {
    /** Texts and multitexts: free text. */
    TEXT,
    /** Values from a closed or small set (types, trait values, parent type…). */
    OPTION,
    /** Counts, orders, frequencies. */
    NUMBER,
    /** LIFT dates, possibly partial ({@link LiftDate}). */
    DATE;

    /** The filter a column of this type gets when none is chosen. */
    public FilterKind defaultFilter() {
        return switch (this) {
            case TEXT -> FilterKind.TEXT;
            case OPTION -> FilterKind.OPTIONS;
            case NUMBER, DATE -> FilterKind.RANGE;
        };
    }

    /** Whether a column of this type may be filtered with {@code kind}. */
    public boolean accepts(FilterKind kind) {
        return switch (this) {
            case TEXT, OPTION -> kind != FilterKind.RANGE;
            case NUMBER, DATE -> kind != FilterKind.OPTIONS;
        };
    }

    /** Sorts the texts of the cells of this type; empty cells come last. */
    public Comparator<String> comparator() {
        Comparator<String> c = switch (this) {
            case NUMBER -> Comparator.comparing(ValueType::number, Comparator.nullsLast(Comparator.naturalOrder()));
            case DATE -> Comparator.comparing((String s) -> LiftDate.parse(s).orElse(null),
                Comparator.nullsLast(Comparator.naturalOrder()));
            case TEXT, OPTION -> collator();
        };
        return (a, b) -> {
            boolean ea = a == null || a.isEmpty();
            boolean eb = b == null || b.isEmpty();
            if (ea || eb) return Boolean.compare(ea, eb);
            return c.compare(a, b);
        };
    }

    private static Comparator<String> collator() {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.TERTIARY);
        return collator::compare;
    }

    /** The number in a cell's text, or null. */
    public static Double number(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.valueOf(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The key of a cell's text for range filters: a {@link Double} or a {@link LiftDate}. */
    public Optional<Comparable<?>> key(String s) {
        return switch (this) {
            case NUMBER -> Optional.ofNullable(number(s));
            case DATE -> LiftDate.parse(s).map(d -> d);
            default -> Optional.ofNullable(s == null || s.isEmpty() ? null : s);
        };
    }
}
