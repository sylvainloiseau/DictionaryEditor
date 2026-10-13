package fr.cnrs.lacito.liftgui.ui.table;

import java.util.Collections;
import java.util.List;
import javafx.beans.value.ObservableValue;

/**
 * The definition of a table column for rows of type {@code T}.
 * <p>
 * A column covers either a single value or one value per language: a multilingual column is
 * shown as a group column with one sub-column per language. Specs are immutable; the
 * {@code with…} style methods ({@link #width}, {@link #facet}, {@link #editable}…) return a
 * modified copy, so that a column reads as a declaration:
 * <pre>{@code
 * Columns.multiText("sense.gloss", I18n.get("col.gloss"), metaLangs, LiftSense::getGlosses).textFilter()
 * }</pre>
 *
 * @param id         stable identifier, used to save the user's choice of columns
 * @param title      header text
 * @param languages  the languages of a multilingual column; empty for a single-value column
 * @param value      the text of a cell ({@code lang} is null for a single-value column)
 * @param observable optional live value of a cell, so that the cell follows edits made elsewhere
 * @param commit     optional; makes the cells editable and stores the edited text
 * @param filter     the kind of filter shown above the column
 * @param prefWidth  preferred width of each (sub-)column
 * @param visible    whether the column is shown by default
 */
public record ColumnSpec<T>(
    String id,
    String title,
    List<String> languages,
    CellValue<T> value,
    CellObservable<T> observable,
    CellCommit<T> commit,
    FilterMode filter,
    double prefWidth,
    boolean visible
) {

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

    /** The filter above a column: a list of the values (facet), a free text, or chosen from the data. */
    public enum FilterMode { AUTO, FACET, TEXT }

    public static final double DEFAULT_WIDTH = 140;

    public ColumnSpec {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    public boolean multilingual() {
        return !languages.isEmpty();
    }

    /** The languages of the leaf columns: the column's languages, or a single null. */
    public List<String> leafLanguages() {
        return multilingual() ? languages : Collections.singletonList(null);
    }

    /** The text of a cell, never null. */
    public String text(T row, String lang) {
        if (row == null) return "";
        String s = value.get(row, lang);
        return s == null ? "" : s;
    }

    public ColumnSpec<T> width(double w) {
        return new ColumnSpec<>(id, title, languages, value, observable, commit, filter, w, visible);
    }

    public ColumnSpec<T> facet() {
        return withFilter(FilterMode.FACET);
    }

    public ColumnSpec<T> textFilter() {
        return withFilter(FilterMode.TEXT);
    }

    public ColumnSpec<T> withFilter(FilterMode mode) {
        return new ColumnSpec<>(id, title, languages, value, observable, commit, mode, prefWidth, visible);
    }

    public ColumnSpec<T> hidden() {
        return new ColumnSpec<>(id, title, languages, value, observable, commit, filter, prefWidth, false);
    }

    public ColumnSpec<T> live(CellObservable<T> obs) {
        return new ColumnSpec<>(id, title, languages, value, obs, commit, filter, prefWidth, visible);
    }

    public ColumnSpec<T> editable(CellCommit<T> c) {
        return new ColumnSpec<>(id, title, languages, value, observable, c, filter, prefWidth, visible);
    }
}
