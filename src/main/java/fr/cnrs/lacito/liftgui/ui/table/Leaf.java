package fr.cnrs.lacito.liftgui.ui.table;

import java.util.List;

/**
 * A leaf column of a table: a column spec, and the language for a multilingual spec
 * (null otherwise). Stored as the user data of the corresponding {@code TableColumn}.
 */
public record Leaf<T>(ColumnSpec<T> spec, String lang) implements fr.cnrs.lacito.liftgui.ui.table.filter.FilterColumn<T> {

    /** The id of the leaf: {@code id}, or {@code id@lang}; filters and widths are keyed by it. */
    @Override
    public String id() {
        return spec.leafId(lang);
    }

    @Override
    public String text(T row) {
        return spec.text(row, lang);
    }

    @Override
    public List<String> values(T row) {
        return spec.cellValues(row, lang);
    }

    @Override
    public ValueType type() {
        return spec.type();
    }

    public FilterKind filterKind() {
        return spec.filterKind();
    }

    /** Header for exports: the column title, with the language for a multilingual column. */
    public String header() {
        return lang == null ? spec.title() : spec.title() + " [" + lang + "]";
    }

    /** Short name for the filter chips and accessible texts: "Gloss·en". */
    public String shortName() {
        return lang == null ? spec.title() : spec.title() + "·" + lang;
    }
}
