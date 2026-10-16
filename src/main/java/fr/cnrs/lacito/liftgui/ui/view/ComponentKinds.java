package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftIllustration;
import fr.cnrs.lacito.liftapi.model.LiftMedia;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.editor.EditorPresenters;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.nav.ViewRequest;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.ComponentColumns;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.ExampleColumns;
import fr.cnrs.lacito.liftgui.ui.table.catalogs.SenseColumns;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.collections.ObservableList;

/**
 * The kinds of component that have a table in the "Objects" subtree, in the order of the
 * model: their view, their columns, their rows, and the editor that shows one of them.
 *
 * The navigation tree, the View menu, {@link Views} and {@link EditorPresenters#show} all
 * read this list, so that a kind of component is declared once. Entries are listed first,
 * but have a view of their own ({@link EntryView}: new entry, paste).
 */
public final class ComponentKinds {

    /**
     * A kind of component with its table.
     *
     * @param key         its view
     * @param type        the class of its components
     * @param columns     all the columns available for it
     * @param rows        the live list of its components in a dictionary
     * @param placeholder i18n key of the text shown when there is none; may be null
     * @param editor      shows one of them in the editor panel
     */
    public record ComponentKind<T>(
        ViewKey key,
        Class<T> type,
        Function<LiftDictionary, List<ColumnSpec<T>>> columns,
        Function<LiftDictionaryRegistry, ObservableList<T>> rows,
        String placeholder,
        BiConsumer<EditorPresenters, T> editor
    ) {

        /** The table of this kind, restricted to the request's subset if it has one. */
        public DictionaryView view(ViewRequest r, EditorContext ctx) {
            LiftDictionary d = ctx.dictionary();
            EditorPresenters editors = ctx.editors();
            return new ObjectTableView<>(ctx, type, columns.apply(d),
                ObjectTableView.rows(r.subset(), type, rows.apply(d.getLiftDictionaryRegistry())),
                r.title(), placeholder, o -> editor.accept(editors, o));
        }

        /** Shows {@code o}, which must be of this kind, in the editor panel. */
        public void show(EditorPresenters editors, Object o) {
            editor.accept(editors, type.cast(o));
        }
    }

    private static final List<ComponentKind<?>> ALL = List.of(
        new ComponentKind<>(ViewKey.ENTRIES, LiftEntry.class, d -> List.of(),
            LiftDictionaryRegistry::getEntries, null, EditorPresenters::showEntry),
        new ComponentKind<>(ViewKey.SENSES, LiftSense.class, SenseColumns::catalog,
            LiftDictionaryRegistry::getSenses, "placeholder.noSense", EditorPresenters::showSense),
        new ComponentKind<>(ViewKey.EXAMPLES, LiftExample.class, ExampleColumns::catalog,
            LiftDictionaryRegistry::getExamples, "placeholder.noExample", EditorPresenters::showExample),
        new ComponentKind<>(ViewKey.TRANSLATIONS, LiftTranslation.class, ComponentColumns::translations,
            LiftDictionaryRegistry::getTranslations, "placeholder.noTranslation", EditorPresenters::showTranslation),
        new ComponentKind<>(ViewKey.NOTES, LiftNote.class, ComponentColumns::notes,
            LiftDictionaryRegistry::getNotes, "placeholder.noNote", EditorPresenters::showNote),
        new ComponentKind<>(ViewKey.VARIANTS, LiftVariant.class, ComponentColumns::variants,
            LiftDictionaryRegistry::getVariants, "placeholder.noVariant", EditorPresenters::showVariant),
        new ComponentKind<>(ViewKey.PRONUNCIATIONS, LiftPronunciation.class, ComponentColumns::pronunciations,
            LiftDictionaryRegistry::getPronunciations, "placeholder.noPronunciation", EditorPresenters::showPronunciation),
        new ComponentKind<>(ViewKey.MEDIA, LiftMedia.class, ComponentColumns::media,
            LiftDictionaryRegistry::getMedias, "placeholder.noMedia", EditorPresenters::showMedia),
        new ComponentKind<>(ViewKey.ETYMOLOGIES, LiftEtymology.class, ComponentColumns::etymologies,
            LiftDictionaryRegistry::getEtymologies, "placeholder.noEtymology", EditorPresenters::showEtymology),
        new ComponentKind<>(ViewKey.RELATIONS, LiftRelation.class, ComponentColumns::relations,
            LiftDictionaryRegistry::getRelations, "placeholder.noRelation", EditorPresenters::showRelation),
        new ComponentKind<>(ViewKey.REVERSALS, LiftReversal.class, ComponentColumns::reversals,
            LiftDictionaryRegistry::getReversals, "placeholder.noReversal", EditorPresenters::showReversal),
        new ComponentKind<>(ViewKey.ILLUSTRATIONS, LiftIllustration.class, ComponentColumns::illustrations,
            LiftDictionaryRegistry::getIllustrations, "placeholder.noIllustration", EditorPresenters::showIllustration),
        new ComponentKind<>(ViewKey.FIELDS, LiftField.class, ComponentColumns::fields,
            LiftDictionaryRegistry::getFields, "placeholder.noField", EditorPresenters::showField),
        new ComponentKind<>(ViewKey.TRAITS, LiftTrait.class, ComponentColumns::traits,
            LiftDictionaryRegistry::getTraits, "placeholder.noTrait", EditorPresenters::showTrait),
        new ComponentKind<>(ViewKey.ANNOTATIONS, LiftAnnotation.class, ComponentColumns::annotations,
            LiftDictionaryRegistry::getAnnotations, "placeholder.noAnnotation", EditorPresenters::showAnnotation)
    );

    private ComponentKinds() {}

    /** Every kind, in the order of the navigation tree. */
    public static List<ComponentKind<?>> all() {
        return ALL;
    }

    /** The views of {@link #all()}. */
    public static ViewKey[] keys() {
        return ALL.stream().map(ComponentKind::key).toArray(ViewKey[]::new);
    }

    /** The kind of a view, if it is a component table. */
    public static Optional<ComponentKind<?>> ofKey(ViewKey key) {
        return ALL.stream().filter(k -> k.key() == key).findFirst();
    }

    /** The kind of a component, if it has a table. */
    public static Optional<ComponentKind<?>> of(Object component) {
        return component == null
            ? Optional.empty()
            : ALL.stream().filter(k -> k.type().isInstance(component)).findFirst();
    }
}
