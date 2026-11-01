
/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Editor for a single {@link LiftVariant}.
 *
 * Displays: the parent entry forms (read-only), ref, variant type (trait {@code variant-type},
 * values from the {@code variant-type} range), forms MultiText, pronunciations, relations
 * + ExtensibleWithFieldEditor.
 */
public final class VariantEditor extends VBox {
    private static final String VARIANT_TYPE_TRAIT = "variant-type";
    private static final String EMPTY_CHOICE = "";

    private final TextField refField = EditorSupport.readOnlyField("");
    private final ComboBox<String> variantTypeCombo = new ComboBox<>();
    private final MultiTextEditor parentEntryFormsEditor;
    private final MultiTextEditor formsEditor;
    private final ComponentSection pronunciationsSection;
    private final ComponentSection relationsSection;
    private final ExtensibleWithFieldEditor extensibleEditor;
    private LiftVariant currentVariant;
    private EditActions currentActions;
    private boolean updatingVariantType = false;
    private final LiftDictionary dictionary;

    public VariantEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.parentEntryFormsEditor = new MultiTextEditor(dictionary);
        this.formsEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));

        variantTypeCombo.setEditable(false);
        variantTypeCombo.setMaxWidth(Double.MAX_VALUE);
        variantTypeCombo.setPromptText(I18n.get("editor.variantType"));
        variantTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingVariantType) updateVariantType(newVal);
        });

        parentEntryFormsEditor.useObjectLanguages();
        parentEntryFormsEditor.setFixedLanguageRows(true);
        formsEditor.useObjectLanguages();
        formsEditor.setFixedLanguageRows(true);
        formsEditor.getStyleClass().add(Reveal.FIRST_INPUT);

        TitledPane parentFormsPane = new TitledPane(I18n.get("editor.parentEntryForms"), parentEntryFormsEditor);
        parentFormsPane.setExpanded(true);
        parentFormsPane.setAnimated(false);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.ref")), 0, 0);
        grid.add(refField, 1, 0);
        grid.add(new Label(I18n.get("editor.variantType")), 0, 1);
        grid.add(variantTypeCombo, 1, 1);
        GridPane.setHgrow(refField, Priority.ALWAYS);
        GridPane.setHgrow(variantTypeCombo, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane(I18n.get("col.forms"), formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        pronunciationsSection = new ComponentSection(ComponentKind.PRONUNCIATION, I18n.get("editor.pronunciations"), false);
        relationsSection = new ComponentSection(ComponentKind.RELATION, I18n.get("editor.relations"), false);
        SectionPane extPane = SectionPane.properties(I18n.get("editor.properties.withFields"), extensibleEditor);

        getChildren().addAll(parentFormsPane, grid, formsPane, pronunciationsSection, relationsSection, extPane);
    }

    public void setVariant(LiftVariant v) {
        setVariant(v, null);
    }

    /**
     * @param v       the variant
     * @param actions to add and delete its pronunciations, relations and properties; when
     *                null the variant is shown read-only
     */
    public void setVariant(LiftVariant v, EditActions actions) {
        pronunciationsSection.setOwner(v, actions);
        relationsSection.setOwner(v, actions);
        currentVariant = v;
        currentActions = actions;

        if (v == null) {
            refField.setText("");
            updatingVariantType = true;
            try {
                variantTypeCombo.getItems().clear();
                variantTypeCombo.setValue(null);
                variantTypeCombo.setDisable(true);
            } finally {
                updatingVariantType = false;
            }
            parentEntryFormsEditor.setMultiText(null);
            formsEditor.setMultiText(null);
            formsEditor.setReadOnly(true);
            extensibleEditor.setModel(null, null);
            return;
        }

        refField.setText(v.getRefId().orElse(""));

        String currentVariantType = findVariantTypeTrait(v)
            .map(LiftTrait::getValue)
            .filter(value -> !value.isBlank())
            .orElse(null);
        LinkedHashSet<String> comboItems = new LinkedHashSet<>();
        comboItems.add(EMPTY_CHOICE);
        variantTypeRange().ifPresent(range -> comboItems.addAll(EditorSupport.sortedFeatureIds(range)));
        if (currentVariantType != null) comboItems.add(currentVariantType);
        updatingVariantType = true;
        try {
            variantTypeCombo.getItems().setAll(comboItems);
            variantTypeCombo.setValue(currentVariantType == null ? EMPTY_CHOICE : currentVariantType);
            variantTypeCombo.setDisable(actions == null || comboItems.size() <= 1);
        } finally {
            updatingVariantType = false;
        }

        MultiText parentForms = v.getParent() != null ? v.getParent().getForms() : null;
        parentEntryFormsEditor.setMultiText(parentForms);
        parentEntryFormsEditor.setReadOnly(true);

        formsEditor.setMultiText(v.getForms());
        // Editable when the host gives add actions; parent lexical forms stay read-only above
        formsEditor.setReadOnly(actions == null);

        formsEditor.setEditActions(actions);

        List<Node> prons = new ArrayList<>();
        for (LiftPronunciation p : v.getPronunciations()) {
            PronunciationEditor pe = new PronunciationEditor(dictionary);
            pe.setPronunciation(p, actions);
            prons.add(pronunciationsSection.card(p, EditorSupport.firstForm(p.getPronunciation()), pe,
                () -> v.deletePronunciation(p)));
        }
        pronunciationsSection.setItems(prons);

        List<Node> relations = new ArrayList<>();
        for (LiftRelation r : v.getRelations()) {
            RelationEditor re = new RelationEditor(dictionary);
            re.setRelation(r, actions);
            relations.add(relationsSection.card(r, SenseEditor.relationTitle(r), re, () -> v.deleteRelation(r)));
        }
        relationsSection.setItems(relations);
        extensibleEditor.setModel(v, actions);
    }

    /**
     * The values of the {@code variant-type} trait: the range of its definition when it
     * declares one, otherwise the range of that name, if the file has one - what the
     * header resolves the name to.
     */
    private Optional<FeatureSet> variantTypeRange() {
        return dictionary.getHeader().resolveTraitSpecification(VARIANT_TYPE_TRAIT).getResolvedFeatureSet();
    }

    private static Optional<LiftTrait> findVariantTypeTrait(LiftVariant v) {
        return v.getTraits().stream()
            .filter(t -> VARIANT_TYPE_TRAIT.equals(t.getName()))
            .findFirst();
    }

    private void updateVariantType(String newValue) {
        if (currentVariant == null) return;
        LiftTrait existingTrait = findVariantTypeTrait(currentVariant).orElse(null);
        if (newValue == null || newValue.isBlank()) {
            if (existingTrait != null) currentVariant.deleteTrait(existingTrait);
            return;
        }
        if (existingTrait != null) {
            existingTrait.setValue(newValue);
            return;
        }
        if (currentActions != null) {
            currentActions.dictionary().getComponentBuilder().trait(currentVariant, VARIANT_TYPE_TRAIT, newValue).build();
        }
    }
}
