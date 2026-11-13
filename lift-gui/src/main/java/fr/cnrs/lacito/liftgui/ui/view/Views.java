package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.nav.ViewRequest;
import fr.cnrs.lacito.liftgui.ui.view.header.FeatureSetView;
import fr.cnrs.lacito.liftgui.ui.view.header.FieldDefinitionsView;
import fr.cnrs.lacito.liftgui.ui.view.header.HeaderDescriptionView;
import fr.cnrs.lacito.liftgui.ui.view.header.LanguagesView;
import fr.cnrs.lacito.liftgui.ui.view.header.RangeView;
import fr.cnrs.lacito.liftgui.ui.view.quickentry.QuickEntryView;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Label;

/** Creates the view of a {@link ViewRequest}. */
public final class Views {

    private Views() {}

    /** The view of a request; a placeholder when no dictionary is open. */
    public static DictionaryView create(ViewRequest r, EditorContext ctx) {
        LiftDictionary d = ctx.dictionary();
        if (d == null) return () -> new Label(I18n.get("placeholder.noDictionary"));
        List<?> subset = r.subset();
        String title = r.title();
        LiftHeader header = d.getHeader();
        if (r.key() == ViewKey.ENTRIES) return new EntryView(ctx, subset, title);
        // The component tables (senses, notes, traits…) are declared in ComponentKinds.
        Optional<ComponentKinds.ComponentKind<?>> kind = ComponentKinds.ofKey(r.key());
        if (kind.isPresent()) return kind.get().view(r, ctx);
        return switch (r.key()) {
            case OBJECT_LANGUAGES -> new LanguageFieldView(ctx, true);
            case META_LANGUAGES -> new LanguageFieldView(ctx, false);
            case GRAM_INFO -> CategoryViews.gramInfo(ctx);
            case TRAIT_VALUES -> CategoryViews.traits(ctx);
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
            default -> throw new IllegalStateException("No view for " + r.key());
        };
    }
}
