package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.controls.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shows an object in the editor panel: a "Delete" button, a "Back to …" button leading to
 * its parent, and the editor of the object (or a read-only summary).
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
        return BuilderAddActions.of(dictionary(), model, this::redisplay);
    }

    /** Shows again the object currently shown (e.g. after a component was added to it). */
    public void redisplay() {
        redisplay.run();
    }

    /** Shows any dictionary object with its editor; objects without one are shown with their entry. */
    public void show(Object o) {
        switch (o) {
            case LiftEntry e -> showEntry(e);
            case LiftSense s -> showSense(s);
            case LiftExample ex -> showExample(ex);
            case LiftNote n -> showNote(n);
            case LiftVariant v -> showVariant(v);
            case LiftRelation r -> showRelation(r);
            case LiftEtymology et -> showEtymology(et);
            case LiftAnnotation a -> showAnnotation(a);
            case LiftField f -> showField(f);
            case null -> panel().clear();
            default -> {
                LiftEntry entry = DictionaryQueries.owningEntry(o);
                if (entry != null) showEntry(entry);
                else panel().clear();
            }
        }
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
        addBackToParentButton(sense.getParentEntry());

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
        addBackToParentButton(ex.getParent());
        ExampleEditor ee = new ExampleEditor(dictionary());
        ee.setExample(ex, BuilderAddActions.multiTextAnnotator(dictionary()), addActions(ex));
        panel().add(ee);
    }

    public void showNote(LiftNote note) {
        redisplay = () -> showNote(note);
        panel().show(I18n.get("nav.notes") + " : " + EditorSupport.featureId(note.getType()), "", note);
        AbstractNotable parent = note.getParent();
        if (parent != null) panel().add(delete.component(() -> parent.deleteNote(note), () -> parent.addNote(note)));
        addBackToParentButton(parent);
        NoteEditor ne = new NoteEditor(dictionary());
        ne.setNote(note);
        panel().add(ne);
    }

    public void showVariant(LiftVariant v) {
        redisplay = () -> showVariant(v);
        panel().show(I18n.get("nav.variants") + " : " + v.getRefId().orElse("?"), "", v);
        LiftEntry parent = v.getParent();
        if (parent != null) panel().add(delete.component(() -> parent.deleteVariant(v), () -> parent.addVariant(v)));
        addBackToParentButton(parent);
        VariantEditor ve = new VariantEditor(dictionary());
        ve.setVariant(v, addActions(v));
        panel().add(ve);
    }

    public void showRelation(LiftRelation relation) {
        redisplay = () -> showRelation(relation);
        panel().show(
            I18n.get("nav.relations") + " : " + (relation.getType() != null ? relation.getType().getId() : "?"),
            relation.getRefID().orElse(""),
            relation
        );
        HasRelations parent = relation.getParent();
        if (parent != null) {
            panel().add(delete.component(() -> parent.deleteRelation(relation), () -> parent.addRelation(relation)));
        }
        addBackToParentButton(parent);
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
        if (parent != null) panel().add(delete.component(() -> parent.deleteEtymology(et), () -> parent.addEtymology(et)));
        EtymologyEditor ee = new EtymologyEditor(dictionary());
        ee.setEtymology(et);
        panel().add(ee);
    }

    /* ─── Read-only summaries ─── */

    public void showAnnotation(LiftAnnotation annotation) {
        redisplay = () -> showAnnotation(annotation);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), ObjectLabels.describeParentType(annotation.getParent()));
        values.put(I18n.get("col.name"), EditorSupport.featureId(annotation.getType()));
        values.put(I18n.get("col.value"), annotation.getValue());
        values.put(I18n.get("col.who"), annotation.getWho());
        values.put(I18n.get("col.when"), annotation.getWhen());
        panel().summary(I18n.get("nav.annotations"), values);
        addGoToParentButton(annotation.getParent());
    }

    public void showField(LiftField field) {
        redisplay = () -> showField(field);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), ObjectLabels.describeParentType(field.getParent()));
        values.put(I18n.get("col.type"), field.getSpecification().getName());
        values.put(I18n.get("col.text"), field.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse(""));
        panel().summary(I18n.get("nav.fields"), values);
        addGoToParentButton(field.getParent());
    }

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

    /* ─── Navigation buttons ─── */

    /**
     * Adds a "Back to …" button leading to the parent of the object shown: the entry (or the
     * entry of a variant), or the sense. Does nothing for other parents.
     */
    private void addBackToParentButton(Object parent) {
        if (parent instanceof LiftVariant v) parent = v.getParent();
        if (parent instanceof LiftEntry entry) {
            panel().backButton(I18n.get("sense.backToEntry", ObjectLabels.entryLabel(entry)),
                () -> ctx.navigator().goToEntryOf(entry));
        } else if (parent instanceof LiftSense sense) {
            panel().backButton(I18n.get("sense.backToSense", LiftEntryEditor.senseDisplayText(sense)),
                () -> ctx.navigator().goToSense(sense));
        }
    }

    /** "Go to parent": shows the entry the parent belongs to. */
    private void addGoToParentButton(Object parent) {
        if (DictionaryQueries.owningEntry(parent) == null) return;
        panel().navButton(I18n.get("btn.goToParent"), () -> ctx.navigator().goToEntryOf(parent));
    }
}
