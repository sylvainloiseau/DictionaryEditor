package fr.cnrs.lacito.liftgui.ui.table.filter;

/** How a {@link TextFilter} compares its text with a cell. */
public enum TextOp {
    CONTAINS, STARTS_WITH, EQUALS, EMPTY, NOT_EMPTY;

    /** Whether the operation uses the text of the filter. */
    public boolean usesText() {
        return this != EMPTY && this != NOT_EMPTY;
    }
}
