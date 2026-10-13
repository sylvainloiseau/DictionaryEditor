package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.editor.EditorPresenters;
import fr.cnrs.lacito.liftgui.ui.nav.ViewRequest;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.ComponentColumns;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.ExampleColumns;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.SenseColumns;
import fr.cnrs.lacito.liftgui.ui.view.header.FeatureSetView;
import fr.cnrs.lacito.liftgui.ui.view.header.FieldDefinitionsView;
import fr.cnrs.lacito.liftgui.ui.view.header.HeaderDescriptionView;
import fr.cnrs.lacito.liftgui.ui.view.header.LanguagesView;
import fr.cnrs.lacito.liftgui.ui.view.header.RangeView;
import fr.cnrs.lacito.liftgui.ui.view.quickentry.QuickEntryView;
import java.util.List;
import javafx.scene.control.Label;

/** Creates the view of a {@link ViewRequest}. */
public final class Views {

    private Views() {}

    /** The view of a request; a placeholder when no dictionary is open. */
    public static DictionaryView create(ViewRequest r, EditorContext ctx) {
        LiftDictionary d = ctx.dictionary();
        if (d == null) return () -> new Label(I18n.get("placeholder.noDictionary"));
        LiftDictionaryRegistry reg = d.getLiftDictionaryRegistry();
        EditorPresenters editors = ctx.editors();
        List<?> subset = r.subset();
        String title = r.title();
        LiftHeader header = d.getHeader();
        return switch (r.key()) {
            case ENTRIES -> new EntryView(ctx, subset, title);
            case SENSES -> new ObjectTableView<>(ctx, LiftSense.class, SenseColumns.catalog(d),
                ObjectTableView.rows(subset, LiftSense.class, reg.getSenses()), title, "placeholder.noSense", editors::showSense);
            case EXAMPLES -> new ObjectTableView<>(ctx, LiftExample.class, ExampleColumns.catalog(d),
                ObjectTableView.rows(subset, LiftExample.class, reg.getExamples()), title, "placeholder.noExample",
                editors::showExample);
            case NOTES -> new ObjectTableView<>(ctx, LiftNote.class, ComponentColumns.notes(d),
                ObjectTableView.rows(subset, LiftNote.class, reg.getNotes()), title, null, editors::showNote);
            case VARIANTS -> new ObjectTableView<>(ctx, LiftVariant.class, ComponentColumns.variants(d),
                ObjectTableView.rows(subset, LiftVariant.class, reg.getVariants()), title, "placeholder.noVariant",
                editors::showVariant);
            case ETYMOLOGIES -> new ObjectTableView<>(ctx, LiftEtymology.class, ComponentColumns.etymologies(d),
                ObjectTableView.rows(subset, LiftEtymology.class, reg.getEtymologies()), title, null, editors::showEtymology);
            case RELATIONS -> new ObjectTableView<>(ctx, LiftRelation.class, ComponentColumns.relations(d),
                ObjectTableView.rows(subset, LiftRelation.class, reg.getRelations()), title, "placeholder.noRelation",
                editors::showRelation);
            case FIELDS -> new ObjectTableView<>(ctx, LiftField.class, ComponentColumns.fields(d),
                ObjectTableView.rows(subset, LiftField.class, reg.getFields()), title, "placeholder.noField", editors::showField);
            case ANNOTATIONS -> new ObjectTableView<>(ctx, LiftAnnotation.class, ComponentColumns.annotations(d),
                ObjectTableView.rows(subset, LiftAnnotation.class, reg.getAnnotations()), title, "placeholder.noAnnotation",
                editors::showAnnotation);
            case OBJECT_LANGUAGES -> new LanguageFieldView(ctx, true);
            case META_LANGUAGES -> new LanguageFieldView(ctx, false);
            case GRAM_INFO -> CategoryViews.gramInfo(ctx);
            case TRAITS -> CategoryViews.traits(ctx);
            case TRANSLATION_TYPES -> CategoryViews.translationTypes(ctx);
            case NOTE_TYPES -> CategoryViews.noteTypes(ctx);
            case RELATION_TYPES -> CategoryViews.relationTypes(ctx);
            case FIELD_TYPES -> CategoryViews.fieldTypes(ctx);
            case CFG_DESCRIPTION -> new HeaderDescriptionView(ctx);
            case CFG_FIELD_DEFINITIONS -> new FieldDefinitionsView(ctx);
            case CFG_LANGUAGES -> new LanguagesView(ctx);
            case CFG_NOTE_TYPES -> new FeatureSetView(ctx, I18n.get("nav.cfgManageNoteTypes"), header.getNoteTypeManager());
            case CFG_TRANSLATION_TYPES -> new FeatureSetView(ctx, I18n.get("nav.cfgManageTransTypes"),
                header.getTranslationTypeManager());
            case CFG_ANNOTATION_TYPES -> new FeatureSetView(ctx, I18n.get("nav.cfgManageAnnotationTypes"),
                header.getAnnotationTypeManager());
            case CFG_RELATION_TYPES -> new FeatureSetView(ctx, I18n.get("nav.cfgManageRelationTypes"),
                header.getRelationTypeManager());
            case RANGE -> new RangeView(ctx, r.rangeId());
            case QUICK_ENTRY -> new QuickEntryView(ctx);
        };
    }
}
