package fr.cnrs.lacito.liftgui.ui.table;

import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.MultiText;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Factory of the usual kinds of {@link ColumnSpec}. */
public final class Columns {

    private Columns() {}

    /** A single-value text column. */
    public static <T> ColumnSpec<T> text(String id, String title, Function<T, String> value) {
        return ColumnSpec.of(id, title, List.of(), (row, lang) -> value.apply(row));
    }

    /** A single-value column holding a number (a count…). */
    public static <T> ColumnSpec<T> number(String id, String title, Function<T, ? extends Number> value) {
        return ColumnSpec.<T>of(id, title, List.of(), (row, lang) -> {
            Number n = value.apply(row);
            return n == null ? "" : String.valueOf(n);
        }).number().width(90);
    }

    /**
     * One sub-column per language, showing the form of a multitext in that language. The
     * cells follow the multitext and edit it (an empty text removes the form, as in the
     * editors); a row without the multitext (null) has read-only, unmarked cells.
     */
    public static <T> ColumnSpec<T> multiText(String id, String title, Collection<String> languages,
                                              Function<T, MultiText> multiText) {
        return ColumnSpec.<T>of(id, title, List.copyOf(languages), (row, lang) -> formText(multiText.apply(row), lang))
            .live((row, lang) -> multiText.apply(row).formTextProperty(lang))
            .editable((row, lang, typed) -> {
                MultiText mt = multiText.apply(row);
                if (mt != null) mt.formTextProperty(lang).set(typed);
            })
            .appliesWhen((row, lang) -> multiText.apply(row) != null)
            .markEmpty(true);
    }

    /** One sub-column per language, with any (read-only) value per language. */
    public static <T> ColumnSpec<T> perLanguage(String id, String title, Collection<String> languages,
                                                ColumnSpec.CellValue<T> value) {
        return ColumnSpec.of(id, title, List.copyOf(languages), value);
    }

    /** The plain text of a multitext in a language, or "". */
    public static String formText(MultiText mt, String lang) {
        if (mt == null) return "";
        return mt.getForm(lang).map(Form::toPlainText).orElse("");
    }
}
