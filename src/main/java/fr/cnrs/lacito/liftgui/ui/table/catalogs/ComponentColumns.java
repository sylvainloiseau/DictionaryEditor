package fr.cnrs.lacito.liftgui.ui.table.catalogs;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.controls.EditorSupport;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.Columns;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The columns available for notes, variants, relations, etymologies, annotations and fields. */
public final class ComponentColumns {

    private ComponentColumns() {}

    public static List<ColumnSpec<LiftNote>> notes(LiftDictionary d) {
        return List.of(
            Columns.text("note.parentType", I18n.get(Keys.COL_PARENT_TYPE), n -> ObjectLabels.describeParentType(n.getParent())),
            Columns.text("note.type", I18n.get(Keys.COL_TYPE), n -> EditorSupport.featureId(n.getType())),
            Columns.multiText("note.text", I18n.get(Keys.COL_TEXT), d.getMetaLanguageManager().getLanguages(), LiftNote::getText)
                .textFilter()
        );
    }

    public static List<ColumnSpec<LiftVariant>> variants(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        return List.of(
            Columns.multiText("variant.parentEntry", I18n.get(Keys.COL_PARENT_ENTRY), objLangs,
                v -> v.getParent() == null ? null : v.getParent().getForms()),
            Columns.text("variant.variantType", "variant-type", v -> DictionaryQueries.traitValue(v, "variant-type")),
            Columns.text("variant.isPrimary", "is-primary", v -> DictionaryQueries.traitValue(v, "is-primary")),
            Columns.text("variant.ref", I18n.get(Keys.COL_REF), v -> v.getRefId().orElse("")),
            Columns.multiText("variant.forms", I18n.get(Keys.COL_FORMS), objLangs, LiftVariant::getForms)
        );
    }

    public static List<ColumnSpec<LiftRelation>> relations(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        return List.of(
            Columns.multiText("relation.parentEntry", I18n.get(Keys.COL_PARENT_ENTRY), objLangs,
                r -> formsOf(DictionaryQueries.owningEntry(r))),
            Columns.text("relation.type", I18n.get(Keys.COL_TYPE), r -> EditorSupport.featureId(r.getType())),
            // The forms of the entry the relation points to (or of the entry of the sense it points to).
            Columns.multiText("relation.target", I18n.get("col.refForm"), objLangs,
                r -> formsOf(DictionaryQueries.owningEntry(r.getRefObject()))),
            Columns.multiText("relation.usage", I18n.get(Keys.COL_USAGE), d.getMetaLanguageManager().getLanguages(),
                LiftRelation::getUsage)
        );
    }

    private static MultiText formsOf(LiftEntry e) {
        return e == null ? null : e.getForms();
    }

    public static List<ColumnSpec<LiftEtymology>> etymologies(LiftDictionary d) {
        return List.of(
            Columns.text("etymology.type", I18n.get(Keys.COL_TYPE), e -> EditorSupport.featureId(e.getType())),
            Columns.text("etymology.source", I18n.get(Keys.COL_SOURCE), e -> e.getSource() != null ? e.getSource() : ""),
            Columns.multiText("etymology.forms", I18n.get(Keys.COL_FORMS), d.getObjectLanguageManager().getLanguages(),
                LiftEtymology::getForms),
            Columns.multiText("etymology.gloss", I18n.get(Keys.COL_GLOSS), d.getMetaLanguageManager().getLanguages(),
                LiftEtymology::getGlosses).hidden()
        );
    }

    public static List<ColumnSpec<LiftAnnotation>> annotations(LiftDictionary d) {
        // Number of annotations with the same type and value, computed once.
        Map<String, Long> frequency = new HashMap<>();
        for (LiftAnnotation a : d.getLiftDictionaryRegistry().getAnnotations()) frequency.merge(annotationKey(a), 1L, Long::sum);
        return List.of(
            Columns.text("annotation.parentType", I18n.get(Keys.COL_PARENT_TYPE), a -> ObjectLabels.describeParentType(a.getParent())),
            Columns.text("annotation.parent", I18n.get(Keys.COL_PARENT), a -> ObjectLabels.describeParent(a.getParent())),
            Columns.text("annotation.name", I18n.get(Keys.COL_NAME), a -> EditorSupport.featureId(a.getType())),
            Columns.text("annotation.value", I18n.get(Keys.COL_VALUE), LiftAnnotation::getValue),
            Columns.text("annotation.who", I18n.get(Keys.COL_WHO), LiftAnnotation::getWho),
            Columns.text("annotation.when", I18n.get(Keys.COL_WHEN), LiftAnnotation::getWhen),
            Columns.text("annotation.frequency", I18n.get(Keys.COL_FREQUENCY),
                a -> String.valueOf(frequency.getOrDefault(annotationKey(a), 0L)))
        );
    }

    private static String annotationKey(LiftAnnotation a) {
        return EditorSupport.featureId(a.getType()) + "\u0000" + Objects.toString(a.getValue(), "");
    }

    public static List<ColumnSpec<LiftField>> fields(LiftDictionary d) {
        return List.of(
            Columns.text("field.parentType", I18n.get(Keys.COL_PARENT_TYPE), f -> ObjectLabels.describeParentType(f.getParent())),
            Columns.text("field.type", I18n.get(Keys.COL_TYPE), f -> f.getSpecification().getName()),
            Columns.text("field.text", I18n.get(Keys.COL_TEXT),
                f -> f.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse("")),
            Columns.multiText("field.textByLanguage", I18n.get(Keys.COL_TEXT), d.getMetaLanguageManager().getLanguages(),
                LiftField::getText).hidden()
        );
    }
}
