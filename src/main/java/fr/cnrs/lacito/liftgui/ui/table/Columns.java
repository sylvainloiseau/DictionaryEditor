package fr.cnrs.lacito.liftgui.ui.table;

import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.MultiText;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Factory of the usual kinds of {@link ColumnSpec}. */
public final class Columns {

    private Columns() {}

    /** A single-value column. */
    public static <T> ColumnSpec<T> text(String id, String title, Function<T, String> value) {
        return new ColumnSpec<>(id, title, List.of(), (row, lang) -> value.apply(row), null, null,
            ColumnSpec.FilterMode.AUTO, ColumnSpec.DEFAULT_WIDTH, true);
    }

    /** One sub-column per language, showing the form of a multitext in that language. */
    public static <T> ColumnSpec<T> multiText(String id, String title, Collection<String> languages,
                                              Function<T, MultiText> multiText) {
        return new ColumnSpec<>(id, title, List.copyOf(languages),
            (row, lang) -> formText(multiText.apply(row), lang), null, null,
            ColumnSpec.FilterMode.AUTO, ColumnSpec.DEFAULT_WIDTH, true);
    }

    /** One sub-column per language, with any value per language. */
    public static <T> ColumnSpec<T> perLanguage(String id, String title, Collection<String> languages,
                                                ColumnSpec.CellValue<T> value) {
        return new ColumnSpec<>(id, title, List.copyOf(languages), value, null, null,
            ColumnSpec.FilterMode.AUTO, ColumnSpec.DEFAULT_WIDTH, true);
    }

    /** The plain text of a multitext in a language, or "". */
    public static String formText(MultiText mt, String lang) {
        if (mt == null) return "";
        return mt.getForm(lang).map(Form::toPlainText).orElse("");
    }
}
