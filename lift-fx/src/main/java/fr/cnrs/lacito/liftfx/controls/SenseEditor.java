/**

 * @author Inès GBADAMASSI
 * @author Maryse GOEH-AKUE
 * @author Ermeline BRESSON
 * @author Ayman JARI
 * @author Erij MAZOUZ

 **/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.ComponentSection;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.ItemCard;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.Reveal;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.scene.Node;
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
 * reversals, illustrations, sub-senses (recursive SenseEditor each),
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates).
 * With {@link EditActions}, all of them can be added and deleted.
 */
public final class SenseEditor extends VBox {

    private static final String NO_GRAM_INFO = "";

    /** Non-editable ComboBox for grammatical info — values come from header range grammatical-info */
    private final ComboBox<String> grammaticalInfoCombo = new ComboBox<>();
    private final GridPane identityBlock = new GridPane();
    private final MultiTextEditor definitionEditor;
    private final MultiTextEditor glossEditor;
    private final ComponentSection examplesSection;
    private final ComponentSection relationsSection;
    private final ComponentSection reversalsSection;
    private final ComponentSection illustrationsSection;
    private final ComponentSection subSensesSection;
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

        grammaticalInfoCombo.setEditable(false);
        grammaticalInfoCombo.setPromptText(Messages.get("col.gramInfo"));
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
        grid.add(new Label(Messages.get("col.gramInfo")), 0, 0);
        grid.add(grammaticalInfoCombo, 1, 0);
        GridPane.setHgrow(grammaticalInfoCombo, Priority.ALWAYS);

        definitionEditor.setFixedLanguageRows(true);
        glossEditor.setFixedLanguageRows(true);
        // A new sense: the user types its gloss first.
        glossEditor.getStyleClass().add(Reveal.FIRST_INPUT);

        TitledPane defPane = titledPane(Messages.get("col.definition"), definitionEditor, true);
        TitledPane glossPane = titledPane(Messages.get("col.gloss"), glossEditor, true);
        examplesSection = new ComponentSection(ComponentKind.EXAMPLE, Messages.get("editor.examples"), true);
        relationsSection = new ComponentSection(ComponentKind.RELATION, Messages.get("editor.relations"), false);
        reversalsSection = new ComponentSection(ComponentKind.REVERSAL, Messages.get("editor.reversals"), false);
        illustrationsSection = new ComponentSection(ComponentKind.ILLUSTRATION, Messages.get("nav.illustrations"), false);
        subSensesSection = new ComponentSection(ComponentKind.SUB_SENSE, Messages.get("editor.subSenses"), false);
        SectionPane extPane = SectionPane.properties(Messages.get("editor.properties.inherited"), notableEditor);

        identityBlock.setHgap(8);
        identityBlock.setVgap(6);
        identityBlock.getStyleClass().add("identity-block");
        TitledPane identityPane = titledPane(Messages.get("editor.identity"), identityBlock, true);

        getChildren().addAll(
            sectionTitle("editor.section.gramInfo"),
            grid,
            sectionTitle("editor.section.semanticContent"),
            defPane,
            glossPane,
            sectionTitle("editor.section.examplesRelations"),
            examplesSection,
            relationsSection,
            reversalsSection,
            illustrationsSection,
            subSensesSection,
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
        setSense(sense, null);
    }

    /**
     * Shows a sense. With {@code actions}, its examples, relations, reversals, illustrations,
     * sub-senses, annotations and properties can be added and deleted; without, read-only.
     */
    public void setSense(LiftSense sense, EditActions actions) {
        examplesSection.setOwner(sense, actions);
        relationsSection.setOwner(sense, actions);
        reversalsSection.setOwner(sense, actions);
        illustrationsSection.setOwner(sense, actions);
        subSensesSection.setOwner(sense, actions);

        if (sense == null) {
            currentSense = null;
            grammaticalInfoCombo.setValue(null);
            definitionEditor.setMultiText(null);
            definitionEditor.setEditActions(null);
            glossEditor.setMultiText(null);
            glossEditor.setEditActions(null);
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
        addIdentityRow(0, Messages.get("field.id"), sense.getId().orElse(""));
        addIdentityRow(1, Messages.get("field.dateCreated"), sense.getDateCreated().orElse(""));
        addIdentityRow(2, Messages.get("field.dateModified"), sense.getDateModified().orElse(""));

        definitionEditor.setMultiText(sense.getDefinition());
        glossEditor.setMultiText(sense.getGlosses());
        definitionEditor.setEditActions(actions);
        glossEditor.setEditActions(actions);

        List<Node> examples = new ArrayList<>();
        List<LiftExample> exampleList = sense.getExamples();
        for (int i = 0; i < exampleList.size(); i++) {
            LiftExample ex = exampleList.get(i);
            ExampleEditor ee = new ExampleEditor(dictionary);
            ee.setExample(ex, actions);
            String text = EditorSupport.firstForm(ex.getExample());
            ItemCard card = examplesSection.card(ex, "#" + (i + 1) + "  " + (text.isBlank() ? Messages.get("sense.empty") : text),
                ee, () -> sense.deleteExample(ex));
            if (text.isBlank()) card.getStyleClass().add("item-empty");
            examples.add(card);
        }
        examplesSection.setItems(examples);

        List<Node> relations = new ArrayList<>();
        for (LiftRelation rel : sense.getRelations()) {
            RelationEditor re = new RelationEditor(dictionary);
            re.setRelation(rel, actions);
            relations.add(relationsSection.card(rel, relationTitle(rel), re, () -> sense.deleteRelation(rel)));
        }
        relationsSection.setItems(relations);

        List<Node> reversals = new ArrayList<>();
        for (LiftReversal rev : sense.getReversals()) {
            ReversalEditor rve = new ReversalEditor(dictionary);
            rve.setReversal(rev, actions);
            String title = (EditorSupport.featureId(rev.getType()) + "  " + EditorSupport.firstForm(rev.getForms())).strip();
            reversals.add(reversalsSection.card(rev, title, rve, () -> sense.deleteReversal(rev)));
        }
        reversalsSection.setItems(reversals);

        Supplier<File> file = actions == null ? () -> null : actions::dictionaryFile;
        List<Node> illustrations = new ArrayList<>();
        for (LiftIllustration ill : sense.getIllustrations()) {
            ExternalDocumentEditor ede = new ExternalDocumentEditor(dictionary, file);
            ede.setDocument(ill, actions);
            illustrations.add(illustrationsSection.card(ill, Objects.toString(ill.getHref(), ""), ede,
                () -> sense.deleteIllustration(ill)));
        }
        illustrationsSection.setItems(illustrations);

        List<Node> subSenses = new ArrayList<>();
        List<LiftSense> subList = sense.getSenses();
        for (int i = 0; i < subList.size(); i++) {
            LiftSense sub = subList.get(i);
            SenseEditor se = new SenseEditor(dictionary);
            se.setGrammaticalInfoValues(
                grammaticalInfoCombo.getItems().stream().filter(v -> !v.isBlank()).toList()
            );
            se.setOnGramInfoChanged(onGramInfoChanged);
            se.setSense(sub, actions);
            subSenses.add(senseCard(subSensesSection, i, sub, se, () -> sense.deleteSense(sub), true));
        }
        subSensesSection.setItems(subSenses);
        subSensesSection.setExpanded(!subList.isEmpty());

        notableEditor.setModel(sense, actions);
    }

    /**
     * The card of a sense or a sub-sense: "#2  to walk", or "#2  (empty)" in grey for a
     * sense without gloss nor definition. It folds.
     */
    static ItemCard senseCard(ComponentSection section, int index, LiftSense sense, Node editor, Runnable remove,
                              boolean expanded) {
        String text = LiftEntryEditor.senseDisplayText(sense);
        boolean empty = "?".equals(text);
        ItemCard card = section.card(sense, "#" + (index + 1) + "  " + (empty ? Messages.get("sense.empty") : text),
            editor, remove);
        if (empty) card.getStyleClass().add("item-empty");
        return card.collapsible(expanded);
    }

    /** "synonym → walk". */
    static String relationTitle(LiftRelation rel) {
        String type = rel.getType() == null ? "?" : rel.getType().getId();
        return rel.getRefId().map(r -> type + " → " + r).orElse(type);
    }

    private void addIdentityRow(int row, String label, String value) {
        TextField tf = EditorSupport.readOnlyField(value);
        GridPane.setHgrow(tf, Priority.ALWAYS);
        identityBlock.add(new Label(label), 0, row);
        identityBlock.add(tf, 1, row);
    }

    private static Label sectionTitle(String i18nKey) {
        Label lbl = new Label(Messages.get(i18nKey));
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
