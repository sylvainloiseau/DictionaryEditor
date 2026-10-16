package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.DuplicateTypeException;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.LiftTranslation;
import fr.cnrs.lacito.liftgui.ui.I18n;
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
        setStyle("-fx-border-color: #cba; -fx-border-radius: 4; -fx-background-color: #faf6f2; -fx-background-radius: 4;");

        typeCombo.setEditable(false);
        typeCombo.setPromptText(I18n.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);
        textEditor.setFixedLanguageRows(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane textPane = new TitledPane(I18n.get("col.translation"), textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        getChildren().addAll(grid, textPane);
    }

    public void setTranslation(LiftTranslation tr) {
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
                EditorSupport.showError(I18n.get("col.type"), I18n.get("error.duplicateTranslationType", n.getId()));
                typeCombo.setValue(tr.getType());
            }
        };
        typeCombo.valueProperty().addListener(typeListener);
        textEditor.setMultiText(tr.getTranslation());
    }
}
