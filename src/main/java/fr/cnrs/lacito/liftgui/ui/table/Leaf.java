package fr.cnrs.lacito.liftgui.ui.table;

/**
 * A leaf column of a table: a column spec, and the language for a multilingual spec
 * (null otherwise). Stored as the user data of the corresponding {@code TableColumn}.
 */
public record Leaf<T>(ColumnSpec<T> spec, String lang) {

    public String text(T row) {
        return spec.text(row, lang);
    }

    /** Header for exports: the column title, with the language for a multilingual column. */
    public String header() {
        return lang == null ? spec.title() : spec.title() + " [" + lang + "]";
    }
}
