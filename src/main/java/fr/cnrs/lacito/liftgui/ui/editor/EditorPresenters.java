package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.controls.*;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.nav.ViewRequest;
import fr.cnrs.lacito.liftgui.ui.view.ComponentKinds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shows an object in the editor panel: a "Delete" button, a link to its parent (which opens
 * the table of the parent's kind on the parent's row), and the editor of the object.
 */
public final class EditorPresenters {

    private final EditorContext ctx;
    private final DeleteActions delete;
    /** Shows again the object currently shown, after an addition inside its editor. */
    private Runnable redisplay = () -> {};

    EditorPresenters(EditorContext ctx) {
        this.ctx = ctx;
        this.delete = new DeleteActions(ctx);
    }

    private LiftDictionary dictionary() {
        return ctx.dictionary();
    }

    private EditorPanel panel() {
        return ctx.panel();
    }

    /** Add actions for a component shown in the panel: an addition shows the object again. */
    private ExtensibleAddActions addActions(Object model) {
        return BuilderAddActions.of(dictionary(), model, () -> {
            redisplay();
        });
    }

    /** Shows again the object currently shown (e.g. after a component was added to it). */
    public void redisplay() {
        redisplay.run();
    }

    /**
     * Shows any dictionary object with its editor: a component with its own table (see
     * {@link ComponentKinds}); anything else with the nearest component above it that has one.
     */
    public void show(Object o) {
        Object shown = o;
        while (shown != null && ComponentKinds.of(shown).isEmpty()) {
            shown = shown instanceof MultiText mt ? mt.getParent()
                : shown instanceof AbstractLiftRoot r ? r.getParentNode() : null;
        }
        if (shown == null) {
            panel().clear();
            return;
        }
        Object target = shown;
        ComponentKinds.of(target).orElseThrow().show(this, target);
    }

    /* ─── Objects with their own view ─── */

    public void showEntry(LiftEntry entry) {
        redisplay = () -> showEntry(entry);
        Form preferred = entry.getForms().getForms().stream().findFirst().orElse(Form.EMPTY_FORM);
        panel().show(
            preferred == Form.EMPTY_FORM ? I18n.get("editor.noForm") : preferred.toPlainText(),
            DictionaryQueries.traitValue(entry, "code"),
            entry
        );
        try {
            panel().add(delete.entry(entry));
            LiftEntryEditor entryEditor = new LiftEntryEditor(dictionary());
            entryEditor.setOnOpenSense(ctx.navigator()::goToSenseAmongEntrySenses);
            entryEditor.setOnChanged(ctx::refreshTable);
            entryEditor.setEntry(entry);
            addChildrenLinks(entry,
                new Children(ViewKey.PRONUNCIATIONS, entry.getPronunciations()),
                new Children(ViewKey.VARIANTS, entry.getVariants()),
                new Children(ViewKey.RELATIONS, entry.getRelations()),
                new Children(ViewKey.ETYMOLOGIES, entry.getEtymologies()),
                new Children(ViewKey.TRAITS, entry.getTraits()),
                new Children(ViewKey.ANNOTATIONS, entry.getAnnotations()));
            panel().add(entryEditor);
        } catch (Exception ex) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(I18n.get("field.id"), entry.getId().orElse(""));
            values.put(I18n.get("editor.error"), Optional.ofNullable(ex.getMessage()).orElse(ex.getClass().getSimpleName()));
            panel().summary(I18n.get("editor.entryDisplayError"), values);
        }
    }

    public void showSense(LiftSense sense) {
        redisplay = () -> showSense(sense);
        panel().show(
            LiftEntryEditor.senseDisplayText(sense),
            sense.getGrammaticalInfo().map(x -> x.getGramInfoValue().getId()).orElse(""),
            sense
        );
        panel().add(delete.sense(sense));
        addParentLink(sense);
        addChildrenLinks(sense,
            new Children(ViewKey.REVERSALS, sense.getReversals()),
            new Children(ViewKey.ILLUSTRATIONS, sense.getIllustrations()),
            new Children(ViewKey.RELATIONS, sense.getRelations()),
            new Children(ViewKey.TRAITS, sense.getTraits()),
            new Children(ViewKey.ANNOTATIONS, sense.getAnnotations()));

        // Links to the examples, by number.
        List<LiftExample> examples = sense.getExamples();
        for (int i = 0; i < examples.size(); i++) {
            LiftExample ex = examples.get(i);
            Hyperlink exLink = new Hyperlink(I18n.get("sense.exampleN", i + 1));
            exLink.setOnAction(e -> ctx.navigator().goToExample(ex));
            panel().add(exLink);
        }

        SenseEditor se = new SenseEditor(dictionary());
        se.setGrammaticalInfoValues(EditorSupport.sortedFeatureIds(dictionary().getHeader().getGrammaticalInfoManager()));
        se.setOnGramInfoChanged(ctx::refreshTable);
        se.setSense(
            sense,
            BuilderAddActions.multiTextAnnotator(dictionary()),
            EditorSupport.sortedFeatureIds(dictionary().getHeader().getAnnotationTypeManager()),
            this::addActions,
            this::addActions
        );
        panel().add(se, senseAddButtons(sense));
    }

    private FlowPane senseAddButtons(LiftSense sense) {
        // An example needs its text, a sense a gloss (or a definition), a reversal a type.
        Button addExBtn = new Button(I18n.get("btn.addExample"));
        addExBtn.setOnAction(e ->
            Dialogs.firstLanguage(dictionary().getObjectLanguageManager().getLanguages()).ifPresent(lang ->
                Dialogs.askText(I18n.get("btn.addExample"), I18n.get("col.example") + " (" + lang + ")").ifPresent(text -> {
                    dictionary().getComponentBuilder().example(sense).withExample(lang, text).build();
                    showSense(sense);
                })
            )
        );
        Button addNoteBtn = new Button(I18n.get("btn.addNote"));
        addNoteBtn.setOnAction(e ->
            Dialogs.chooseType(I18n.get("btn.addNote"), dictionary().getHeader().getNoteTypeManager())
                .ifPresent(type -> {
                    dictionary().getComponentBuilder().note(sense, type).build();
                    showSense(sense);
                })
        );
        Button addRelationBtn = new Button(I18n.get("btn.addRelation"));
        addRelationBtn.setOnAction(e ->
            Dialogs.chooseType(I18n.get("btn.addRelation"), dictionary().getHeader().getRelationTypeManager())
                .ifPresent(type -> {
                    dictionary().getComponentBuilder().relation(type, sense).build();
                    showSense(sense);
                })
        );
        Button addReversalBtn = new Button(I18n.get("btn.addReversal"));
        addReversalBtn.setOnAction(e ->
            Dialogs.chooseType(I18n.get("btn.addReversal"), dictionary().getHeader().getInverseTypeManager())
                .ifPresent(type -> {
                    dictionary().getComponentBuilder().reversal(sense).withType(type).build();
                    showSense(sense);
                })
        );
        Button addSubSenseBtn = new Button(I18n.get("btn.addSubSense"));
        addSubSenseBtn.setOnAction(e ->
            Dialogs.firstLanguage(dictionary().getMetaLanguageManager().getLanguages()).ifPresent(lang ->
                Dialogs.askText(I18n.get("btn.addSubSense"), I18n.get("col.gloss") + " (" + lang + ")").ifPresent(gloss -> {
                    dictionary().getComponentBuilder().sense(sense).withGloss(lang, gloss).build();
                    showSense(sense);
                })
            )
        );
        FlowPane addButtons = new FlowPane(8, 6, addExBtn, addNoteBtn, addRelationBtn, addReversalBtn, addSubSenseBtn);
        addButtons.setPadding(new Insets(8, 0, 0, 0));
        return addButtons;
    }

    public void showExample(LiftExample ex) {
        redisplay = () -> showExample(ex);
        panel().show(I18n.get("nav.examples"), ex.getSource().orElse(""), ex);
        panel().add(delete.example(ex));
        addParentLink(ex);
        addChildrenLinks(ex, new Children(ViewKey.TRANSLATIONS, List.copyOf(ex.getTranslationComponents())));
        ExampleEditor ee = new ExampleEditor(dictionary());
        ee.setExample(ex, BuilderAddActions.multiTextAnnotator(dictionary()), addActions(ex));
        panel().add(ee);
    }

    public void showNote(LiftNote note) {
        redisplay = () -> showNote(note);
        panel().show(I18n.get("nav.notes") + " : " + EditorSupport.featureId(note.getType()), "", note);
        AbstractNotable parent = note.getParent();
        if (parent != null) panel().add(delete.component(note, () -> parent.deleteNote(note)));
        addParentLink(note);
        NoteEditor ne = new NoteEditor(dictionary());
        ne.setNote(note);
        panel().add(ne);
    }

    public void showVariant(LiftVariant v) {
        redisplay = () -> showVariant(v);
        panel().show(I18n.get("nav.variants") + " : " + v.getRefId().orElse("?"), "", v);
        LiftEntry parent = v.getParent();
        if (parent != null) panel().add(delete.component(v, () -> parent.deleteVariant(v)));
        addParentLink(v);
        VariantEditor ve = new VariantEditor(dictionary());
        ve.setVariant(v, addActions(v));
        panel().add(ve);
    }

    public void showRelation(LiftRelation relation) {
        redisplay = () -> showRelation(relation);
        panel().show(
            I18n.get("nav.relations") + " : " + (relation.getType() != null ? relation.getType().getId() : "?"),
            relation.getRefId().orElse(""),
            relation
        );
        HasRelations parent = relation.getParent();
        if (parent != null) {
            panel().add(delete.component(relation, () -> parent.deleteRelation(relation)));
        }
        addParentLink(relation);
        RelationEditor re = new RelationEditor(dictionary());
        re.setRelation(relation);
        panel().add(re);
    }

    public void showEtymology(LiftEtymology et) {
        redisplay = () -> showEtymology(et);
        panel().show(
            et.getType() == null ? I18n.get("nav.etymologies") : et.getType().getId(),
            et.getSource() != null ? et.getSource() : "",
            et
        );
        LiftEntry parent = et.getParent();
        if (parent != null) panel().add(delete.component(et, () -> parent.deleteEtymology(et)));
        addParentLink(et);
        EtymologyEditor ee = new EtymologyEditor(dictionary());
        ee.setEtymology(et);
        panel().add(ee);
    }

    public void showAnnotation(LiftAnnotation annotation) {
        redisplay = () -> showAnnotation(annotation);
        panel().show(I18n.get("nav.annotations") + " : " + EditorSupport.featureId(annotation.getType()),
            ObjectLabels.describeParent(annotation.getParent()), annotation);
        HasAnnotation parent = annotation.getParent();
        if (parent != null) panel().add(delete.component(annotation, () -> parent.deleteAnnotation(annotation)));
        addParentLink(annotation);
        AnnotationEditor ae = new AnnotationEditor(dictionary());
        ae.setAnnotation(annotation);
        panel().add(ae);
    }

    public void showField(LiftField field) {
        redisplay = () -> showField(field);
        panel().show(I18n.get("nav.fields") + " : " + field.getName(), "", field);
        AbstractExtensibleWithField parent = field.getParent();
        if (parent != null) panel().add(delete.component(field, () -> parent.deleteField(field)));
        addParentLink(field);
        FieldEditor fe = new FieldEditor(dictionary());
        fe.setField(field);
        panel().add(fe);
    }

    public void showTranslation(LiftTranslation translation) {
        redisplay = () -> showTranslation(translation);
        panel().show(I18n.get("nav.translations") + " : " + EditorSupport.featureId(translation.getType()), "", translation);
        LiftExample parent = translation.getParent();
        if (parent != null) panel().add(delete.component(translation, () -> parent.deleteTranslation(translation)));
        addParentLink(translation);
        TranslationEditor te = new TranslationEditor(dictionary());
        te.setTranslation(translation);
        panel().add(te);
    }

    public void showPronunciation(LiftPronunciation pronunciation) {
        redisplay = () -> showPronunciation(pronunciation);
        panel().show(I18n.get("nav.pronunciations") + " : " + ObjectLabels.firstForm(pronunciation.getPronunciation()),
            "", pronunciation);
        HasPronunciation parent = pronunciation.getParent();
        if (parent != null) panel().add(delete.component(pronunciation, () -> parent.deletePronunciation(pronunciation)));
        addParentLink(pronunciation);
        addChildrenLinks(pronunciation, new Children(ViewKey.MEDIA, pronunciation.getMedias()));
        PronunciationEditor pe = new PronunciationEditor(dictionary());
        pe.setPronunciation(pronunciation);
        panel().add(pe);
    }

    public void showMedia(LiftMedia media) {
        redisplay = () -> showMedia(media);
        panel().show(I18n.get("nav.media"), Objects.toString(media.getHref(), ""), media);
        LiftPronunciation parent = media.getParent();
        if (parent != null) panel().add(delete.component(media, () -> parent.deleteMedia(media)));
        addParentLink(media);
        ExternalDocumentEditor ede = new ExternalDocumentEditor(dictionary(), () -> ctx.document().file());
        ede.setDocument(media);
        panel().add(ede);
    }

    public void showIllustration(LiftIllustration illustration) {
        redisplay = () -> showIllustration(illustration);
        panel().show(I18n.get("nav.illustrations"), Objects.toString(illustration.getHref(), ""), illustration);
        LiftSense parent = illustration.getParent();
        if (parent != null) panel().add(delete.component(illustration, () -> parent.deleteIllustration(illustration)));
        addParentLink(illustration);
        ExternalDocumentEditor ede = new ExternalDocumentEditor(dictionary(), () -> ctx.document().file());
        ede.setDocument(illustration);
        panel().add(ede);
    }

    public void showReversal(LiftReversal reversal) {
        redisplay = () -> showReversal(reversal);
        panel().show(I18n.get("nav.reversals") + " : " + ObjectLabels.firstForm(reversal.getForms()),
            EditorSupport.featureId(reversal.getType()), reversal);
        HasReversal parent = reversal.getParent();
        if (parent instanceof LiftReversal main && main.getMain() == reversal) {
            panel().add(delete.component(reversal, main::deleteMain));
        } else if (parent != null) {
            panel().add(delete.component(reversal, () -> parent.deleteReversal(reversal)));
        }
        addParentLink(reversal);
        ReversalEditor re = new ReversalEditor(dictionary());
        re.setReversal(reversal);
        panel().add(re);
    }

    public void showTrait(LiftTrait trait) {
        redisplay = () -> showTrait(trait);
        panel().show(I18n.get("nav.traits") + " : " + trait.getName(),
            Objects.toString(trait.getValue(), ""), trait);
        HasTrait parent = trait.getParent();
        if (parent != null) panel().add(delete.component(trait, () -> parent.deleteTrait(trait)));
        addParentLink(trait);
        TraitEditor te = new TraitEditor(dictionary());
        te.setTrait(trait, EditorSupport.targetOf(parent));
        panel().add(te);
    }

    /* ─── Other views ─── */

    /**
     * Shows one form of a multitext, with the editor of the whole multitext.
     *
     * @param title           the panel title (the view's name)
     * @param objectLanguages whether the multitext is in the object languages
     */
    public void showMultiText(MultiText mt, String lang, String title, boolean objectLanguages) {
        redisplay = () -> showMultiText(mt, lang, title, objectLanguages);
        panel().show(title, "", mt.getParent());
        GridPane g = EditorPanel.grid();
        EditorPanel.addReadOnlyRow(g, 0, I18n.get("col.parentType"), ObjectLabels.multiTextKind(mt));
        g.add(new javafx.scene.control.Label(I18n.get("col.parent")), 0, 1);
        Button parentLink = new Button(ObjectLabels.ownerId(mt));
        parentLink.getStyleClass().addAll("hyperlink", "no-dirty");
        parentLink.setOnAction(e -> ctx.navigator().goToEntryOf(mt));
        GridPane.setHgrow(parentLink, Priority.ALWAYS);
        g.add(parentLink, 1, 1);
        EditorPanel.addReadOnlyRow(g, 2, I18n.get("nav.languages"), lang);
        MultiTextEditor mte = new MultiTextEditor(dictionary());
        if (objectLanguages) mte.useObjectLanguages();
        mte.setMultiText(mt);
        VBox textSection = new VBox(6, new javafx.scene.control.Label(I18n.get("col.text")), mte);
        VBox.setVgrow(mte, Priority.ALWAYS);
        panel().add(g, textSection);
    }

    /** Read-only values and a button opening the objects concerned. */
    public void showSummary(String title, Map<String, String> values, String accessLabel, Runnable access) {
        redisplay = () -> showSummary(title, values, accessLabel, access);
        panel().summary(title, values);
        if (access != null) panel().navButton(accessLabel, access);
    }

    /* ─── Navigation ─── */

    /**
     * Adds the link to the parent of a component: "↑ Sense: to walk". It opens the table of
     * the parent's kind, with the parent's row selected (which shows the parent here). An
     * annotation on a text leads to the component of the text; a parent without a table of
     * its own (a grammatical info) to the nearest component above it that has one.
     */
    private void addParentLink(AbstractLiftRoot component) {
        Object parent = parentWithTable(component);
        if (parent == null) return;
        ViewKey key = ComponentKinds.of(parent).orElseThrow().key();
        panel().backButton(I18n.get("editor.parentLink", ObjectLabels.kindName(parent), ObjectLabels.shortLabel(parent)),
            () -> ctx.navigator().go(ViewRequest.of(key).selecting(parent)));
    }

    /** The parent of a component that has a table, climbing over those that have none. */
    static Object parentWithTable(AbstractLiftRoot component) {
        Object p = component instanceof LiftAnnotation a ? a.getParent() : component.getParentNode();
        while (p != null && ComponentKinds.of(p).isEmpty()) {
            p = switch (p) {
                case Form f -> f.getMultiText();
                case MultiText m -> m.getParent();
                case AbstractLiftRoot r -> r.getParentNode();
                default -> null;
            };
        }
        return p;
    }

    /** The children of a component, of one kind, for {@link #addChildrenLinks}. */
    private record Children(ViewKey key, List<?> list) {}

    /**
     * "Pronunciations (2) · Relations (1)": links opening the table of each kind of children
     * of the component shown, restricted to them ("Pronunciations of 'walk'"). Kinds without
     * children are left out.
     */
    private void addChildrenLinks(Object owner, Children... children) {
        FlowPane links = new FlowPane(8, 2);
        String ownerLabel = ObjectLabels.shortLabel(owner);
        for (Children c : children) {
            if (c.list().isEmpty()) continue;
            String kind = I18n.get(c.key().i18nKey());
            Hyperlink link = new Hyperlink(I18n.get("editor.openChildren", kind, c.list().size()));
            link.getStyleClass().add("no-dirty");
            String title = I18n.get("editor.childrenOf", kind, ownerLabel);
            List<?> list = List.copyOf(c.list());
            link.setOnAction(e -> ctx.navigator().go(ViewRequest.subset(c.key(), list, title)));
            links.getChildren().add(link);
        }
        if (!links.getChildren().isEmpty()) panel().add(links);
    }
}
