package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.List;
import java.util.Set;

/** The columns available for senses. */
public final class SenseColumns {

    private SenseColumns() {}

    public static List<ColumnSpec<LiftSense>> catalog(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = d.getMetaLanguageManager().getLanguages();
        return List.of(
            Columns.<LiftSense>multiText("sense.parentEntry", I18n.get(Keys.COL_PARENT_ENTRY), objLangs,
                    s -> s.getParentEntry() == null ? null : s.getParentEntry().getForms())
                .width(120),
            Columns.<LiftSense>text("sense.gramInfo", I18n.get(Keys.COL_GRAM_INFO),
                s -> s.getGrammaticalInfo().map(x -> x.getGramInfoValue().getId()).orElse("")),
            Columns.multiText("sense.gloss", I18n.get(Keys.COL_GLOSS), metaLangs, LiftSense::getGlosses),
            Columns.multiText("sense.definition", I18n.get(Keys.COL_DEFINITION), metaLangs, LiftSense::getDefinition),
            Columns.<LiftSense>text("sense.id", I18n.get("col.id"), s -> s.getId().orElse("")).hidden()
        );
    }
}
