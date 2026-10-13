/**

 * @author Inès GBADAMASSI
 * @author Maryse GOEH-AKUE
 * @author Ermeline BRESSON
 * @author Ayman JARI
 * @author Erij MAZOUZ

 **/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftSense}.
 *
 * Displays: grammatical info (ComboBox, values from the header; the empty choice removes it),
 * definition MultiText, gloss MultiText, examples (ExampleEditor each), relations (RelationEditor each),
 * reversals, sub-senses (recursive SenseEditor each),
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates).
 * When the sense has add actions, its examples, relations, reversals and sub-senses can be deleted.
 */
public final class SenseEditor extends VBox {

    private static final String NO_GRAM_INFO = "";

    /** Non-editable ComboBox for grammatical info — values come from header range grammatical-info */
    private final ComboBox<String> grammaticalInfoCombo = new ComboBox<>();
    private final GridPane identityBlock = new GridPane();
    private final MultiTextEditor definitionEditor;
    private final MultiTextEditor glossEditor;
    private final VBox examplesBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final VBox reversalsBox = new VBox(6);
    private final VBox subSensesBox = new VBox(6);
    private final TitledPane subSensesPane;
    private final NotableEditor notableEditor;
    /** Currently displayed sense — used by the save listener. */
    private LiftSense currentSense = null;
    private Runnable onGramInfoChanged = null;
    private final LiftDictionary dictionary;

    public SenseEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.definitionEditor = new MultiTextEditor(dictionary);
        this.glossEditor = new MultiTextEditor(dictionary);
        this.notableEditor = new NotableEditor(this.dictionary);
        setPadding(new Insets(4));
        setStyle(
            "-fx-border-color: #aab; -fx-border-radius: 4; -fx-background-color: #f4f4fa; -fx-background-radius: 4;"
        );

        grammaticalInfoCombo.setEditable(false);
        grammaticalInfoCombo.setPromptText(I18n.get("col.gramInfo"));
        grammaticalInfoCombo.setMaxWidth(Double.MAX_VALUE);
        grammaticalInfoCombo
            .valueProperty()
            .addListener((obs, oldVal, newVal) -> {
                if (currentSense == null || newVal == null || newVal.equals(oldVal)) return;
                if (newVal.isBlank()) {
                    currentSense.deleteGrammaticalInfo();
                } else {
                    // Through the builder, so the GrammaticalInfo is registered in the
                    // dictionary. LiftSense.setGrammaticalInfo only wires the two
                    // components together.
                    dictionary
                        .getComponentBuilder()
                        .grammaticalInfo(currentSense, newVal.trim())
                        .build();
                }
                if (onGramInfoChanged != null) onGramInfoChanged.run();
            });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.gramInfo")), 0, 0);
        grid.add(grammaticalInfoCombo, 1, 0);
        GridPane.setHgrow(grammaticalInfoCombo, Priority.ALWAYS);

        definitionEditor.setFixedLanguageRows(true);
        glossEditor.setFixedLanguageRows(true);

        TitledPane defPane = titledPane(I18n.get("col.definition"), definitionEditor, true);
        TitledPane glossPane = titledPane(I18n.get("col.gloss"), glossEditor, true);
        TitledPane exPane = titledPane(I18n.get("editor.examples"), examplesBox, true);
        TitledPane relPane = titledPane(I18n.get("editor.relations"), relationsBox, false);
        TitledPane revPane = titledPane(I18n.get("editor.reversals"), reversalsBox, false);
        subSensesPane = titledPane(I18n.get("editor.subSenses"), subSensesBox, false);
        TitledPane extPane = titledPane(I18n.get("editor.properties.inherited"), notableEditor, false);

        identityBlock.setHgap(8);
        identityBlock.setVgap(6);
        identityBlock.setStyle(
            "-fx-background-color: #e8e8e8; -fx-padding: 8; -fx-background-radius: 4;"
        );
        TitledPane identityPane = titledPane(I18n.get("editor.identity"), identityBlock, true);

        getChildren().addAll(
            sectionTitle("editor.section.gramInfo"),
            grid,
            sectionTitle("editor.section.semanticContent"),
            defPane,
            glossPane,
            sectionTitle("editor.section.examplesRelations"),
            exPane,
            relPane,
            revPane,
            subSensesPane,
            sectionTitle("editor.section.metadata"),
            extPane,
            identityPane
        );
    }

    public void setOnGramInfoChanged(Runnable callback) {
        this.onGramInfoChanged = callback;
    }

    /**
     * Set the allowed grammatical info values from the header range "grammatical-info".
     * Call this before setSense() so the ComboBox is populated. An empty choice is added to
     * remove the grammatical info.
     */
    public void setGrammaticalInfoValues(List<String> values) {
        List<String> items = new ArrayList<>();
        items.add(NO_GRAM_INFO);
        if (values != null) items.addAll(values);
        grammaticalInfoCombo.getItems().setAll(items);
    }

    public void setSense(LiftSense sense) {
        setSense(sense, null, List.of());
    }

    /**
     * Same as above with optional callback to create annotations on MultiText (definition, gloss).
     */
    public void setSense(
        LiftSense sense,
        BiConsumer<String, MultiText> onAddAnnotation,
        Collection<String> knownAnnotationNames
    ) {
        setSense(sense, onAddAnnotation, knownAnnotationNames, s -> null, ex -> null);
    }

    /**
     * Same with optional add actions for trait/annotation/field/note in NotableEditor.
     * When the factory gives add actions for this sense, its examples, relations, reversals
     * and sub-senses can be deleted.
     */
    public void setSense(
        LiftSense sense,
        BiConsumer<String, MultiText> onAddAnnotation,
        Collection<String> knownAnnotationNames,
        Function<LiftSense, ExtensibleAddActions> addActionsFactory,
        Function<LiftExample, ExtensibleAddActions> exampleAddActionsFactory
    ) {
        ExtensibleAddActions addActions =
            addActionsFactory != null ? addActionsFactory.apply(sense) : null;
        examplesBox.getChildren().clear();
        relationsBox.getChildren().clear();
        reversalsBox.getChildren().clear();
        subSensesBox.getChildren().clear();

        if (sense == null) {
            currentSense = null;
            grammaticalInfoCombo.setValue(null);
            definitionEditor.setMultiText(null);
            definitionEditor.setOnAddAnnotation(null, null);
            glossEditor.setMultiText(null);
            glossEditor.setOnAddAnnotation(null, null);
            notableEditor.setModel(null);
            identityBlock.getChildren().clear();
            return;
        }

        // Clear currentSense BEFORE setting combo value to avoid spurious save on load
        currentSense = null;
        String gramVal = sense
            .getGrammaticalInfo()
            .map(x -> x.getGramInfoValue().getId())
            .orElse(NO_GRAM_INFO);
        // Add current value to combo if not already present (handles values not in header range)
        if (!grammaticalInfoCombo.getItems().contains(gramVal)) {
            grammaticalInfoCombo.getItems().add(gramVal);
        }
        grammaticalInfoCombo.setValue(gramVal);
        currentSense = sense; // re-enable listener after loading

        // Identity block: ID, dates (read-only, grey)
        identityBlock.getChildren().clear();
        addIdentityRow(0, I18n.get("field.id"), sense.getId().orElse(""));
        addIdentityRow(1, I18n.get("field.dateCreated"), sense.getDateCreated().orElse(""));
        addIdentityRow(2, I18n.get("field.dateModified"), sense.getDateModified().orElse(""));

        definitionEditor.setMultiText(sense.getDefinition());
        glossEditor.setMultiText(sense.getGlosses());
        if (onAddAnnotation != null) {
            definitionEditor.setOnAddAnnotation(onAddAnnotation, knownAnnotationNames);
            glossEditor.setOnAddAnnotation(onAddAnnotation, knownAnnotationNames);
        }

        Runnable refresh = addActions == null ? null : addActions::refresh;

        List<LiftExample> examples = sense.getExamples();
        for (int i = 0; i < examples.size(); i++) {
            LiftExample ex = examples.get(i);
            int index = i;
            ExampleEditor ee = new ExampleEditor(dictionary);
            ExtensibleAddActions exAddActions =
                exampleAddActionsFactory != null ? exampleAddActionsFactory.apply(ex) : null;
            ee.setExample(ex, onAddAnnotation, exAddActions);
            Runnable onDelete = refresh == null ? null : () -> ComponentDeletion.delete(
                () -> sense.deleteExample(ex),
                () -> sense.addExample(Math.min(index, sense.getExamples().size()), ex),
                refresh);
            examplesBox.getChildren().add(EditorSupport.withDeleteButton(ee, onDelete));
        }

        for (LiftRelation rel : sense.getRelations()) {
            RelationEditor re = new RelationEditor(dictionary);
            re.setRelation(rel);
            Runnable onDelete = refresh == null ? null : () -> ComponentDeletion.delete(
                () -> sense.deleteRelation(rel), () -> sense.addRelation(rel), refresh);
            relationsBox.getChildren().add(EditorSupport.withDeleteButton(re, onDelete));
        }

        for (LiftReversal rev : sense.getReversals()) {
            ReversalEditor rve = new ReversalEditor(dictionary);
            rve.setReversal(rev);
            Runnable onDelete = refresh == null ? null : () -> ComponentDeletion.delete(
                () -> sense.deleteReversal(rev), () -> sense.addReversal(rev), refresh);
            reversalsBox.getChildren().add(EditorSupport.withDeleteButton(rve, onDelete));
        }

        List<LiftSense> subSenses = sense.getSenses();
        for (int i = 0; i < subSenses.size(); i++) {
            LiftSense sub = subSenses.get(i);
            int index = i;
            SenseEditor se = new SenseEditor(dictionary);
            se.setGrammaticalInfoValues(
                grammaticalInfoCombo.getItems().stream().filter(v -> !v.isBlank()).toList()
            );
            se.setOnGramInfoChanged(onGramInfoChanged);
            se.setSense(sub, onAddAnnotation, knownAnnotationNames, addActionsFactory, exampleAddActionsFactory);
            Runnable onDelete = refresh == null ? null : () -> ComponentDeletion.delete(
                () -> sense.deleteSense(sub),
                () -> sense.addSense(Math.min(index, sense.getSenses().size()), sub),
                refresh);
            subSensesBox.getChildren().add(EditorSupport.withDeleteButton(se, onDelete));
        }
        subSensesPane.setExpanded(!subSenses.isEmpty());

        notableEditor.setModel(sense, addActions);
    }

    private void addIdentityRow(int row, String label, String value) {
        TextField tf = EditorSupport.readOnlyField(value);
        GridPane.setHgrow(tf, Priority.ALWAYS);
        identityBlock.add(new Label(label), 0, row);
        identityBlock.add(tf, 1, row);
    }

    private static Label sectionTitle(String i18nKey) {
        Label lbl = new Label(I18n.get(i18nKey));
        lbl.getStyleClass().add("editor-section-title");
        return lbl;
    }

    private static TitledPane titledPane(String title, javafx.scene.Node content, boolean expanded) {
        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        return tp;
    }
}
