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

/**
 * The columns available for the components shown in tables of their own: notes, variants,
 * relations, etymologies, annotations, fields, translations, pronunciations, media,
 * illustrations, reversals and traits.
 */
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

    /* ─── Components whose texts are edited in the cells ─── */

    /** A multilingual column whose cells follow and edit the forms of a multitext. */
    private static <T> ColumnSpec<T> editableTexts(String id, String title, Set<String> langs,
                                                   java.util.function.Function<T, MultiText> text) {
        return Columns.multiText(id, title, langs, text)
            .live((row, lang) -> text.apply(row).formTextProperty(lang))
            // An empty text removes the form, as in the editors.
            .editable((row, lang, typed) -> text.apply(row).formTextProperty(lang).set(typed));
    }

    public static List<ColumnSpec<LiftTranslation>> translations(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        Set<String> metaLangs = d.getMetaLanguageManager().getLanguages();
        return List.of(
            Columns.<LiftTranslation>multiText("translation.example", I18n.get("col.example"), objLangs,
                t -> t.getParent() == null ? null : t.getParent().getExample()),
            Columns.<LiftTranslation>text("translation.type", I18n.get(Keys.COL_TYPE), t -> EditorSupport.featureId(t.getType())).facet(),
            ComponentColumns.<LiftTranslation>editableTexts("translation.text", I18n.get("col.translation"), metaLangs, LiftTranslation::getTranslation)
                .textFilter(),
            Columns.<LiftTranslation>multiText("translation.sense", I18n.get(Keys.COL_GLOSS), metaLangs,
                t -> t.getParent() == null || t.getParent().getParent() == null ? null : t.getParent().getParent().getGlosses())
                .hidden()
        );
    }

    public static List<ColumnSpec<LiftPronunciation>> pronunciations(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        return List.of(
            Columns.<LiftPronunciation>text("pronunciation.parentType", I18n.get(Keys.COL_PARENT_TYPE),
                p -> ObjectLabels.describeParentType(p.getParent())).facet(),
            Columns.<LiftPronunciation>multiText("pronunciation.parentEntry", I18n.get(Keys.COL_PARENT_ENTRY), objLangs,
                p -> formsOf(DictionaryQueries.owningEntry(p))),
            ComponentColumns.<LiftPronunciation>editableTexts("pronunciation.forms", I18n.get(Keys.COL_FORMS), objLangs, LiftPronunciation::getPronunciation),
            Columns.<LiftPronunciation>text("pronunciation.mediaCount", I18n.get("col.mediaCount"), p -> String.valueOf(p.getMedias().size())),
            Columns.<LiftPronunciation>text("pronunciation.media", I18n.get("col.file"),
                p -> String.join(", ", p.getMedias().stream().map(LiftMedia::getHref).filter(Objects::nonNull).toList()))
                .hidden()
        );
    }

    public static List<ColumnSpec<LiftMedia>> media(LiftDictionary d) {
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        return List.of(
            Columns.<LiftMedia>multiText("media.parentEntry", I18n.get(Keys.COL_PARENT_ENTRY), objLangs,
                m -> formsOf(DictionaryQueries.owningEntry(m))),
            Columns.<LiftMedia>multiText("media.pronunciation", I18n.get("mtkind.pronunciation"), objLangs,
                m -> m.getParent() == null ? null : m.getParent().getPronunciation()),
            Columns.<LiftMedia>text("media.href", I18n.get("col.file"), m -> Objects.toString(m.getHref(), ""))
                .live((m, lang) -> m.hrefProperty())
                .textFilter(),
            Columns.<LiftMedia>multiText("media.label", I18n.get("col.label"), d.getMetaLanguageManager().getLanguages(),
                LiftMedia::getLabel).hidden()
        );
    }

    public static List<ColumnSpec<LiftIllustration>> illustrations(LiftDictionary d) {
        Set<String> metaLangs = d.getMetaLanguageManager().getLanguages();
        return List.of(
            Columns.<LiftIllustration>multiText("illustration.parentEntry", I18n.get(Keys.COL_PARENT_ENTRY),
                d.getObjectLanguageManager().getLanguages(), i -> formsOf(DictionaryQueries.owningEntry(i))),
            Columns.<LiftIllustration>multiText("illustration.sense", I18n.get(Keys.COL_GLOSS), metaLangs,
                i -> i.getParent() == null ? null : i.getParent().getGlosses()),
            Columns.<LiftIllustration>text("illustration.href", I18n.get("col.file"), i -> Objects.toString(i.getHref(), ""))
                .live((i, lang) -> i.hrefProperty())
                .textFilter(),
            Columns.<LiftIllustration>multiText("illustration.label", I18n.get("col.label"), metaLangs, LiftIllustration::getLabel)
        );
    }

    public static List<ColumnSpec<LiftReversal>> reversals(LiftDictionary d) {
        // lift-api counts the forms of a reversal among the object-language texts.
        Set<String> objLangs = d.getObjectLanguageManager().getLanguages();
        return List.of(
            Columns.<LiftReversal>text("reversal.parentType", I18n.get(Keys.COL_PARENT_TYPE),
                r -> r.getParent() instanceof LiftReversal ? I18n.get("mtkind.reversalMain") : I18n.get("nav.senses")).facet(),
            Columns.<LiftReversal>multiText("reversal.sense", I18n.get(Keys.COL_GLOSS), d.getMetaLanguageManager().getLanguages(),
                r -> owningSense(r) == null ? null : owningSense(r).getGlosses()),
            Columns.<LiftReversal>text("reversal.type", I18n.get(Keys.COL_TYPE), r -> EditorSupport.featureId(r.getType())).facet(),
            ComponentColumns.<LiftReversal>editableTexts("reversal.forms", I18n.get(Keys.COL_FORMS), objLangs, LiftReversal::getForms).textFilter(),
            Columns.<LiftReversal>multiText("reversal.main", I18n.get("mtkind.reversalMain"), objLangs,
                r -> r.getMain() == null ? null : r.getMain().getForms()).hidden()
        );
    }

    private static LiftSense owningSense(AbstractLiftRoot c) {
        for (AbstractLiftRoot n = c; n != null; n = n.getParentNode()) {
            if (n instanceof LiftSense s) return s;
        }
        return null;
    }

    public static List<ColumnSpec<LiftTrait>> traits(LiftDictionary d) {
        return List.of(
            Columns.<LiftTrait>text("trait.parentType", I18n.get(Keys.COL_PARENT_TYPE), t -> ObjectLabels.describeParentType(t.getParent())).facet(),
            Columns.<LiftTrait>text("trait.parent", I18n.get(Keys.COL_PARENT), t -> ObjectLabels.describeParent(t.getParent())),
            Columns.<LiftTrait>text("trait.name", I18n.get(Keys.COL_NAME), t -> t.getSpecification().getName()).facet(),
            Columns.<LiftTrait>text("trait.value", I18n.get(Keys.COL_VALUE), t -> Objects.toString(t.getValue(), "")).facet(),
            Columns.<LiftTrait>text("trait.dataModel", I18n.get("col.dataModel"),
                t -> t.getSpecification().getTypeStr().orElse("")).hidden()
        );
    }
}
