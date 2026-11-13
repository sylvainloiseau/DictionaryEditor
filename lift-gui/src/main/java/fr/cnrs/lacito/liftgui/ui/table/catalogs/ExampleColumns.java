package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The columns available for examples: one group of columns per translation type. */
public final class ExampleColumns {

    private ExampleColumns() {}

    public static List<ColumnSpec<LiftExample>> catalog(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = d.getMetaLanguageManager().getLanguages();
        List<ColumnSpec<LiftExample>> cols = new ArrayList<>();
        // The gloss of the parent sense, else its first gloss.
        cols.add(Columns.<LiftExample>perLanguage("example.parentSense", I18n.get(Keys.COL_PARENT_SENSE), metaLangs,
            (ex, lang) -> {
                LiftSense parent = ex.getParent();
                if (parent == null) return "";
                MultiText gloss = parent.getGlosses();
                return gloss.getForm(lang).map(Form::toPlainText)
                    .or(() -> gloss.getForms().stream().findFirst().map(Form::toPlainText))
                    .orElse("");
            }));
        cols.add(Columns.text("example.source", I18n.get(Keys.COL_SOURCE), ex -> ex.getSource().orElse("")));
        cols.add(Columns.multiText("example.text", I18n.get(Keys.COL_EXAMPLE), objLangs, LiftExample::getExample));
        // The translation types declared in the header, and those used without being declared.
        Set<String> types = new TreeSet<>();
        d.getHeader().getTranslationTypeManager().getFeatures().values().forEach(f -> types.add(f.getId()));
        d.getLiftDictionaryRegistry().getExamples().forEach(ex ->
            ex.getTranslations().keySet().forEach(f -> types.add(DictionaryQueries.featureId(f))));
        for (String type : types) {
            cols.add(Columns.<LiftExample>multiText("example.translation." + type,
                type.isEmpty() ? I18n.get(Keys.COL_TRANSLATION) : type, metaLangs,
                ex -> DictionaryQueries.translationOfType(ex, type)));
        }
        return cols;
    }
}
