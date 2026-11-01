package fr.cnrs.lacito.liftgui.ui.table;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;

/**
 * The definition of a table column for rows of type {@code T}.
 * <p>
 * A column covers either a single value or one value per language: a multilingual column is
 * shown as a group column with one sub-column ("leaf") per language. Specs are immutable;
 * the {@code with…} style methods ({@link #width}, {@link #option}, {@link #editable}…)
 * return a modified copy, so that a column reads as a declaration:
 * <pre>{@code
 * Columns.multiText("sense.gloss", I18n.get("col.gloss"), metaLangs, LiftSense::getGlosses)
 * }</pre>
 *
 * <ul>
 *   <li>{@code id}: stable identifier, used to store the user's choice of columns;</li>
 *   <li>{@code title}: header text;</li>
 *   <li>{@code languages}: the languages of a multilingual column; empty for a single value;</li>
 *   <li>{@code value}: the text of a cell ({@code lang} is null for a single-value column);</li>
 *   <li>{@code observable}: optional live value of a cell, which follows edits made elsewhere;</li>
 *   <li>{@code commit}: optional; makes the cells editable and stores the edited text;</li>
 *   <li>{@code type}: how the column is sorted and filtered ({@link ValueType});</li>
 *   <li>{@code values}: the values of a cell holding several (repeated trait…), for the
 *       options filter; by default the cell's text;</li>
 *   <li>{@code applies}: whether a cell can hold a value for this row (an ancestor column on
 *       a row without that ancestor cannot): such cells are neither edited nor marked empty;</li>
 *   <li>{@code filterAs}: the filter chosen by the user; null for the type's default;</li>
 *   <li>{@code markEmpty}: whether empty cells are marked (texts, fields, notes);</li>
 *   <li>{@code prefWidth}, {@code visible}: preferred width; shown by default.</li>
 * </ul>
 */
public final class ColumnSpec<T> {

    /** The text of a cell. */
    @FunctionalInterface
    public interface CellValue<T> {
        String get(T row, String lang);
    }

    /** The live value of a cell. */
    @FunctionalInterface
    public interface CellObservable<T> {
        ObservableValue<String> get(T row, String lang);
    }

    /** Stores the text typed in a cell. */
    @FunctionalInterface
    public interface CellCommit<T> {
        void commit(T row, String lang, String text);
    }

    /** The values of a cell. */
    @FunctionalInterface
    public interface CellValues<T> {
        List<String> get(T row, String lang);
    }

    /** Whether a cell can hold a value. */
    @FunctionalInterface
    public interface CellApplies<T> {
        boolean test(T row, String lang);
    }

    public static final double DEFAULT_WIDTH = 140;

    private final String id;
    private final String title;
    private final List<String> languages;
    private final CellValue<T> value;
    private final CellObservable<T> observable;
    private final CellCommit<T> commit;
    private final ValueType type;
    private final CellValues<T> values;
    private final CellApplies<T> applies;
    private final FilterKind filterAs;
    private final boolean markEmpty;
    private final double prefWidth;
    private final boolean visible;

    private ColumnSpec(String id, String title, List<String> languages, CellValue<T> value,
                       CellObservable<T> observable, CellCommit<T> commit, ValueType type, CellValues<T> values,
                       CellApplies<T> applies, FilterKind filterAs, boolean markEmpty, double prefWidth,
                       boolean visible) {
        this.id = Objects.requireNonNull(id);
        this.title = title == null ? "" : title;
        this.languages = languages == null ? List.of() : List.copyOf(languages);
        this.value = Objects.requireNonNull(value);
        this.observable = observable;
        this.commit = commit;
        this.type = type == null ? ValueType.TEXT : type;
        this.values = values;
        this.applies = applies;
        this.filterAs = filterAs;
        this.markEmpty = markEmpty;
        this.prefWidth = prefWidth;
        this.visible = visible;
    }

    /** A column of text cells, shown by default. */
    public static <T> ColumnSpec<T> of(String id, String title, List<String> languages, CellValue<T> value) {
        return new ColumnSpec<>(id, title, languages, value, null, null, ValueType.TEXT, null, null, null,
            false, DEFAULT_WIDTH, true);
    }

    private ColumnSpec<T> copy(String id, String title, List<String> languages, CellObservable<T> observable,
                               CellCommit<T> commit, ValueType type, CellValues<T> values, CellApplies<T> applies,
                               FilterKind filterAs, boolean markEmpty, double prefWidth, boolean visible) {
        return new ColumnSpec<>(id, title, languages, value, observable, commit, type, values, applies, filterAs,
            markEmpty, prefWidth, visible);
    }

    /* ─── Accessors ─── */

    public String id() { return id; }
    public String title() { return title; }
    public List<String> languages() { return languages; }
    public CellValue<T> value() { return value; }
    public CellObservable<T> observable() { return observable; }
    public CellCommit<T> commit() { return commit; }
    public ValueType type() { return type; }
    public FilterKind filterAs() { return filterAs; }
    public boolean markEmpty() { return markEmpty; }
    public double prefWidth() { return prefWidth; }
    public boolean visible() { return visible; }

    public boolean multilingual() {
        return !languages.isEmpty();
    }

    /** The languages of the leaf columns: the column's languages, or a single null. */
    public List<String> leafLanguages() {
        return multilingual() ? languages : Collections.singletonList(null);
    }

    /** The id of a leaf: {@code id}, or {@code id@lang}. */
    public String leafId(String lang) {
        return lang == null ? id : id + "@" + lang;
    }

    /** The filter of the column: the one chosen, or the default of its type. */
    public FilterKind filterKind() {
        return filterAs != null && type.accepts(filterAs) ? filterAs : type.defaultFilter();
    }

    /** The text of a cell, never null. */
    public String text(T row, String lang) {
        if (row == null) return "";
        String s = value.get(row, lang);
        return s == null ? "" : s;
    }

    /** The values of a cell (for the options filter), never null; empty for an empty cell. */
    public List<String> cellValues(T row, String lang) {
        if (row == null) return List.of();
        if (values != null) {
            List<String> v = values.get(row, lang);
            return v == null ? List.of() : v.stream().filter(s -> s != null && !s.isEmpty()).toList();
        }
        String s = text(row, lang);
        return s.isEmpty() ? List.of() : List.of(s);
    }

    /** Whether the cell can hold a value for this row. */
    public boolean applies(T row, String lang) {
        return row != null && (applies == null || applies.test(row, lang));
    }

    /** Whether the cell can be edited. */
    public boolean isEditable(T row, String lang) {
        return commit != null && applies(row, lang);
    }

    /** The live value of a cell, or a constant one. */
    public ObservableValue<String> cellObservable(T row, String lang) {
        if (row != null && observable != null && applies(row, lang)) return observable.get(row, lang);
        return new ReadOnlyStringWrapper(text(row, lang));
    }

    /* ─── Modified copies ─── */

    public ColumnSpec<T> width(double w) {
        return copy(id, title, languages, observable, commit, type, values, applies, filterAs, markEmpty, w, visible);
    }

    public ColumnSpec<T> type(ValueType t) {
        return copy(id, title, languages, observable, commit, t, values, applies, filterAs, markEmpty, prefWidth, visible);
    }

    /** A column of values from a small set (options filter). */
    public ColumnSpec<T> option() {
        return type(ValueType.OPTION);
    }

    /** A column of numbers (range filter, numeric sort). */
    public ColumnSpec<T> number() {
        return type(ValueType.NUMBER);
    }

    /** A column of dates (range filter, chronological sort). */
    public ColumnSpec<T> date() {
        return type(ValueType.DATE);
    }

    /** The filter chosen by the user; null for the default of the type. */
    public ColumnSpec<T> filterAs(FilterKind kind) {
        return copy(id, title, languages, observable, commit, type, values, applies, kind, markEmpty, prefWidth, visible);
    }

    public ColumnSpec<T> hidden() {
        return copy(id, title, languages, observable, commit, type, values, applies, filterAs, markEmpty, prefWidth, false);
    }

    public ColumnSpec<T> shown(boolean v) {
        return copy(id, title, languages, observable, commit, type, values, applies, filterAs, markEmpty, prefWidth, v);
    }

    public ColumnSpec<T> live(CellObservable<T> obs) {
        return copy(id, title, languages, obs, commit, type, values, applies, filterAs, markEmpty, prefWidth, visible);
    }

    public ColumnSpec<T> editable(CellCommit<T> c) {
        return copy(id, title, languages, observable, c, type, values, applies, filterAs, markEmpty, prefWidth, visible);
    }

    public ColumnSpec<T> values(CellValues<T> v) {
        return copy(id, title, languages, observable, commit, type, v, applies, filterAs, markEmpty, prefWidth, visible);
    }

    public ColumnSpec<T> appliesWhen(CellApplies<T> a) {
        return copy(id, title, languages, observable, commit, type, values, a, filterAs, markEmpty, prefWidth, visible);
    }

    public ColumnSpec<T> markEmpty(boolean mark) {
        return copy(id, title, languages, observable, commit, type, values, applies, filterAs, mark, prefWidth, visible);
    }

    public ColumnSpec<T> titled(String t) {
        return copy(id, t, languages, observable, commit, type, values, applies, filterAs, markEmpty, prefWidth, visible);
    }

    /** The same column restricted to some of its languages, in the given order. */
    public ColumnSpec<T> withLanguages(List<String> langs) {
        return copy(id, title, langs, observable, commit, type, values, applies, filterAs, markEmpty, prefWidth, visible);
    }

    /**
     * The same column for rows of another type, through {@code target} (the ancestor of a
     * row…): empty, read-only and unmarked when {@code target} gives null.
     *
     * @param newId    the id of the new column
     * @param newTitle its title
     */
    public <S> ColumnSpec<S> through(String newId, String newTitle, Function<S, T> target) {
        CellValue<S> v = (row, lang) -> {
            T t = target.apply(row);
            return t == null ? "" : text(t, lang);
        };
        CellObservable<S> obs = observable == null ? null : (row, lang) -> cellObservable(target.apply(row), lang);
        CellCommit<S> com = commit == null ? null : (row, lang, text) -> {
            T t = target.apply(row);
            if (t != null && isEditable(t, lang)) commit.commit(t, lang, text);
        };
        CellValues<S> vals = (row, lang) -> cellValues(target.apply(row), lang);
        CellApplies<S> app = (row, lang) -> applies(target.apply(row), lang);
        return new ColumnSpec<>(newId, newTitle, languages, v, obs, com, type, vals, app, filterAs, markEmpty,
            prefWidth, visible);
    }

    @Override
    public String toString() {
        return "ColumnSpec[" + id + "]";
    }
}
