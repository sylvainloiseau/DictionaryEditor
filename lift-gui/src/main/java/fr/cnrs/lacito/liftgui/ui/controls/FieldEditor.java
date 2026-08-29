/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionKind;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;

/**
 * Editor for a {@link LiftField}.
 * Uses a ComboBox for the field name/type (read-only, shows known field types).
 */
public final class FieldEditor extends VBox {

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final MultiTextEditor textEditor;
    private final ExtensibleWithoutFieldEditor extensibleEditor;
    private final LiftDictionary dictionary;

    public FieldEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.textEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithoutFieldEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bcd; -fx-border-radius: 4; -fx-background-color: #f0f4f8; -fx-background-radius: 4;");

        nameCombo.setEditable(false);
        nameCombo.valueProperty().addListener((obs, o, n) -> validateFieldType(n));
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        nameCombo.setPromptText("type de champ");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(nameCombo, 1, 0);
        GridPane.setHgrow(nameCombo, Priority.ALWAYS);

        TitledPane textPane = new TitledPane("Texte (MultiText)", textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, textPane, extPane);
    }
    private List<String> knownFieldTypes = new ArrayList<>();

    private void validateFieldType(String type) {
        if (type == null || type.isBlank()) {
            nameCombo.setStyle("-fx-border-color: #bcd; -fx-border-radius: 4;");
            nameCombo.setTooltip(null);
            return;
        }
        if (!knownFieldTypes.contains(type)) {
            nameCombo.setStyle(
                    "-fx-border-color: orange; -fx-border-width: 2; -fx-border-radius: 4;"
            );
            Tooltip tip = new Tooltip("⚠ Type non documenté dans la configuration du dictionnaire");
            tip.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404;");
            nameCombo.setTooltip(tip);
        } else {
            nameCombo.setStyle("-fx-border-color: #bcd; -fx-border-radius: 4;");
            nameCombo.setTooltip(null);
        }
    }
    /**
     * @param f               the field to edit
     * @param availableLangs  languages for the MultiTextEditor
     * @param fieldTypes      all known field type names in the dictionary
     */
    public void setField(LiftField f) {
        if (f == null) {
            nameCombo.getItems().clear();
            textEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }
        Collection<String> fieldTypes = dictionary.getHeader().getFieldsAndTraitsDefinitions().stream()
                .filter(x -> x.getKind() != LiftFieldAndTraitDefinitionKind.TRAIT)
                .map(LiftFieldAndTraitDefinition::getName)
                .toList();

        nameCombo.setItems(
            FXCollections.observableArrayList(
                fieldTypes
            )
        );
        nameCombo.setValue(f.getSpecification().getName());
        this.knownFieldTypes = new ArrayList<>(fieldTypes);
        validateFieldType(f.getSpecification().getName());
        //textEditor.setAvailableLanguages(availableLangs);
        textEditor.setMultiText(f.getText());
        extensibleEditor.setModel(f);
    }

}
