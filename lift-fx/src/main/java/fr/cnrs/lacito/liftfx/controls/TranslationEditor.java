package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.DuplicateTypeException;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.Messages;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftTranslation} of an example: its type (from the header
 * translation types) and its text (meta-languages).
 *
 * An example holds one translation per type: a type the example already has for another
 * translation is refused, and the combo goes back to the current type.
 */
public final class TranslationEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final MultiTextEditor textEditor;
    private final LiftDictionary dictionary;
    private ChangeListener<Feature> typeListener;

    public TranslationEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.textEditor = new MultiTextEditor(dictionary);
        setPadding(new Insets(4));

        typeCombo.setEditable(false);
        typeCombo.setPromptText(Messages.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);
        textEditor.setFixedLanguageRows(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(Messages.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane textPane = new TitledPane(Messages.get("col.translation"), textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        getChildren().addAll(grid, textPane);
    }

    public void setTranslation(LiftTranslation tr) {
        setTranslation(tr, null);
    }

    /** With {@code actions}, annotations can be added to the text of the translation. */
    public void setTranslation(LiftTranslation tr, EditActions actions) {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        typeListener = null;
        if (tr == null) {
            typeCombo.getItems().clear();
            typeCombo.setDisable(true);
            textEditor.setMultiText(null);
            return;
        }
        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getTranslationTypeManager())));
        if (tr.getType() != null && !typeCombo.getItems().contains(tr.getType())) {
            typeCombo.getItems().add(0, tr.getType());
        }
        typeCombo.setValue(tr.getType());
        typeCombo.setDisable(false);
        typeListener = (obs, o, n) -> {
            if (n == null || n == tr.getType()) return;
            try {
                tr.setType(n);
            } catch (DuplicateTypeException e) {
                EditorSupport.showError(Messages.get("col.type"), Messages.get("error.duplicateTranslationType", n.getId()));
                typeCombo.setValue(tr.getType());
            }
        };
        typeCombo.valueProperty().addListener(typeListener);
        textEditor.setMultiText(tr.getTranslation());
        textEditor.setEditActions(actions);
    }
}
