
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
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
    private final VBox pronunciationsBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final ExtensibleWithFieldEditor extensibleEditor;
    private LiftVariant currentVariant;
    private ExtensibleAddActions currentAddActions;
    private boolean updatingVariantType = false;
    private final LiftDictionary dictionary;

    public VariantEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.parentEntryFormsEditor = new MultiTextEditor(dictionary);
        this.formsEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bbc; -fx-border-radius: 4; -fx-background-color: #f6f6fa; -fx-background-radius: 4;");

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

        TitledPane pronPane = new TitledPane(I18n.get("editor.pronunciations"), pronunciationsBox);
        pronPane.setExpanded(false);
        pronPane.setAnimated(false);

        TitledPane relPane = new TitledPane(I18n.get("editor.relations"), relationsBox);
        relPane.setExpanded(false);
        relPane.setAnimated(false);

        TitledPane extPane = new TitledPane(I18n.get("editor.properties.withFields"), extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(parentFormsPane, grid, formsPane, pronPane, relPane, extPane);
    }

    public void setVariant(LiftVariant v) {
        setVariant(v, null);
    }

    /**
     * @param v          the variant
     * @param addActions optional callbacks for adding pronunciation, relation, trait, annotation,
     *                   field; when null the variant is shown read-only
     */
    public void setVariant(LiftVariant v, ExtensibleAddActions addActions) {
        pronunciationsBox.getChildren().clear();
        relationsBox.getChildren().clear();
        currentVariant = v;
        currentAddActions = addActions;

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
            variantTypeCombo.setDisable(addActions == null || comboItems.size() <= 1);
        } finally {
            updatingVariantType = false;
        }

        MultiText parentForms = v.getParent() != null ? v.getParent().getForms() : null;
        parentEntryFormsEditor.setMultiText(parentForms);
        parentEntryFormsEditor.setReadOnly(true);

        formsEditor.setMultiText(v.getForms());
        // Editable when the host gives add actions; parent lexical forms stay read-only above
        formsEditor.setReadOnly(addActions == null);

        if (addActions != null) {
            Button addPronBtn = new Button(I18n.get("btn.addPronunciation"));
            addPronBtn.getStyleClass().add("example-add-button");
            addPronBtn.setOnAction(e -> {
                addActions.addPronunciation();
                addActions.refresh();
            });
            pronunciationsBox.getChildren().add(new FlowPane(6, 4, addPronBtn));

            Button addRelBtn = new Button(I18n.get("btn.addRelation"));
            addRelBtn.getStyleClass().add("example-add-button");
            addRelBtn.setOnAction(e -> {
                List<String> types = EditorSupport.sortedFeatureIds(dictionary.getHeader().getRelationTypeManager());
                Optional<String> typeOpt;
                if (types.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addRelation"));
                    tid.setHeaderText(I18n.get("col.type"));
                    typeOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(types.get(0), types);
                    dlg.setTitle(I18n.get("btn.addRelation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    typeOpt = dlg.showAndWait();
                }
                typeOpt.filter(t -> t != null && !t.isBlank()).ifPresent(type -> {
                    addActions.addRelation(type.trim());
                    addActions.refresh();
                });
            });
            relationsBox.getChildren().add(new FlowPane(6, 4, addRelBtn));
        }

        for (LiftPronunciation p : v.getPronunciations()) {
            PronunciationEditor pe = new PronunciationEditor(dictionary);
            pe.setPronunciation(p);
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.delete(
                () -> v.deletePronunciation(p), addActions::refresh);
            pronunciationsBox.getChildren().add(EditorSupport.withDeleteButton(pe, onDelete));
        }
        for (LiftRelation r : v.getRelations()) {
            RelationEditor re = new RelationEditor(dictionary);
            re.setRelation(r);
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.delete(
                () -> v.deleteRelation(r), addActions::refresh);
            relationsBox.getChildren().add(EditorSupport.withDeleteButton(re, onDelete));
        }
        extensibleEditor.setModel(v, addActions);
    }

    /**
     * The values of the {@code variant-type} trait: the range of its definition when it
     * declares one, otherwise the range of that name, if the file has one.
     */
    private Optional<FeatureSet> variantTypeRange() {
        LiftHeader header = dictionary.getHeader();
        if (header.containsFieldsAndTraitsDefinitions(VARIANT_TYPE_TRAIT)) {
            Optional<FeatureSet> declared = header.getFieldsAndTraitsDefinitions(VARIANT_TYPE_TRAIT).getResolvedFeatureSet();
            if (declared.isPresent()) return declared;
        }
        return header.hasFeatureSet(VARIANT_TYPE_TRAIT)
            ? Optional.of(header.getFeatureSet(VARIANT_TYPE_TRAIT))
            : Optional.empty();
    }

    private static Optional<LiftTrait> findVariantTypeTrait(LiftVariant v) {
        return v.getTraits().stream()
            .filter(t -> VARIANT_TYPE_TRAIT.equals(t.getSpecification().getName()))
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
        if (currentAddActions != null) {
            currentAddActions.addTrait(VARIANT_TYPE_TRAIT, newValue);
        }
    }
}
