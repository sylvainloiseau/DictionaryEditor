package fr.cnrs.lacito.liftgui.ui.table;

/** The filter above a column. */
public enum FilterKind {
    /** Free text, with match case, regular expression and operators. */
    TEXT,
    /** A list of the values of the column, to tick. */
    OPTIONS,
    /** A minimum and a maximum (numbers and dates). */
    RANGE
}
