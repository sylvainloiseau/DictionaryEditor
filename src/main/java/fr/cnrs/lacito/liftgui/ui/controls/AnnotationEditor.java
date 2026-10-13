/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.HasAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftAnnotation}: type (from the header annotation types),
 * value, who, when, and the annotation text.
 */
public final class AnnotationEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final TextField valueField = new TextField();
    private final TextField whoField = new TextField();
    private final TextField whenField = new TextField();
    private final MultiTextEditor textEditor;
    private ChangeListener<Feature> typeListener;
    private LiftAnnotation annotation;
    private final LiftDictionary dictionary;

    public AnnotationEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.textEditor = new MultiTextEditor(dictionary);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #ddd; -fx-border-radius: 4; -fx-background-color: #fafafa; -fx-background-radius: 4;");

        typeCombo.setEditable(false);
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setPromptText(I18n.get("col.type"));
        EditorSupport.showFeatureIds(typeCombo);
        valueField.setPromptText(I18n.get("col.value"));
        whoField.setPromptText(I18n.get("col.who"));
        whenField.setPromptText(I18n.get("col.when"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label(I18n.get("col.name")), 0, r);
        grid.add(typeCombo, 1, r++);
        grid.add(new Label(I18n.get("col.value")), 0, r);
        grid.add(valueField, 1, r++);
        grid.add(new Label(I18n.get("col.who")), 0, r);
        grid.add(whoField, 1, r++);
        grid.add(new Label(I18n.get("col.when")), 0, r);
        grid.add(whenField, 1, r++);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        GridPane.setHgrow(valueField, Priority.ALWAYS);
        GridPane.setHgrow(whoField, Priority.ALWAYS);
        GridPane.setHgrow(whenField, Priority.ALWAYS);

        TitledPane textPane = new TitledPane(I18n.get("editor.text"), textEditor);
        textPane.setExpanded(false);
        textPane.setAnimated(false);

        getChildren().addAll(grid, textPane);
    }

    public void setAnnotation(LiftAnnotation a) {
        unbind();
        this.annotation = a;
        if (a == null) {
            typeCombo.getItems().clear();
            valueField.setText("");
            whoField.setText("");
            whenField.setText("");
            textEditor.setMultiText(null);
            return;
        }

        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getAnnotationTypeManager())));
        if (a.getType() != null && !typeCombo.getItems().contains(a.getType())) {
            typeCombo.getItems().add(0, a.getType());
        }
        typeCombo.setValue(a.getType());
        HasAnnotation parent = a.getParent();
        typeCombo.setDisable(parent == null);
        typeListener = (obs, o, n) -> {
            if (n != null && n != a.getType() && parent != null) parent.retypeAnnotation(a, n);
        };
        typeCombo.valueProperty().addListener(typeListener);

        valueField.textProperty().bindBidirectional(a.valueProperty());
        whoField.textProperty().bindBidirectional(a.whoProperty());
        whenField.textProperty().bindBidirectional(a.whenProperty());
        textEditor.setMultiText(a.getText());
    }

    private void unbind() {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        typeListener = null;
        if (annotation != null) {
            valueField.textProperty().unbindBidirectional(annotation.valueProperty());
            whoField.textProperty().unbindBidirectional(annotation.whoProperty());
            whenField.textProperty().unbindBidirectional(annotation.whenProperty());
        }
    }
}
