package fr.cnrs.lacito.liftgui.ui.table.filter;

import java.util.Optional;

/**
 * The filter of one leaf column: a text, a choice of values or a range. Filters are
 * immutable data, without JavaFX; {@link #compile()} turns one into a fast test of a cell.
 * An inactive filter (empty text, no value chosen, no bound) keeps every row.
 */
public sealed interface ColumnFilter permits TextFilter, OptionFilter, RangeFilter {

    /** Whether the filter removes rows. */
    boolean active();

    /** Why the filter cannot apply (an invalid regular expression, an unreadable bound). */
    default Optional<String> error() {
        return Optional.empty();
    }

    /** A test of a cell; compiled once per change of the filter. */
    CellMatcher compile();

    /** Text form, for saved views ({@link FilterTokens}). */
    default String toToken() {
        return FilterTokens.format(this);
    }
}
