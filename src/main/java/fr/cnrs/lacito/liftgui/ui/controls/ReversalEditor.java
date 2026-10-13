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
import fr.cnrs.lacito.liftapi.model.HasReversal;
import fr.cnrs.lacito.liftapi.model.LiftReversal;
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
 * Editor for a {@link LiftReversal}: type (from the header reversal types), forms, and the
 * recursive "main" reversal.
 */
public final class ReversalEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final MultiTextEditor formsEditor;
    private final VBox mainBox = new VBox(6);
    private ChangeListener<Feature> typeListener;
    private final LiftDictionary dictionary;

    public ReversalEditor(LiftDictionary dictionary) {
        this.dictionary = dictionary;
        this.formsEditor = new MultiTextEditor(dictionary);
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #b9c; -fx-border-radius: 4; -fx-background-color: #f8f4fa; -fx-background-radius: 4;");

        typeCombo.setEditable(false);
        typeCombo.setPromptText(I18n.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane(I18n.get("col.forms"), formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane mainPane = new TitledPane(I18n.get("editor.reversalMain"), mainBox);
        mainPane.setExpanded(false);
        mainPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, mainPane);
    }

    public void setReversal(LiftReversal rev) {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        typeListener = null;
        mainBox.getChildren().clear();
        if (rev == null) {
            typeCombo.getItems().clear();
            typeCombo.setDisable(true);
            formsEditor.setMultiText(null);
            return;
        }
        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getInverseTypeManager())));
        if (rev.getType() != null && !typeCombo.getItems().contains(rev.getType())) {
            typeCombo.getItems().add(0, rev.getType());
        }
        typeCombo.setValue(rev.getType());
        HasReversal parent = rev.getParent();
        typeCombo.setDisable(parent == null);
        typeListener = (obs, o, n) -> {
            if (n != null && n != rev.getType() && parent != null) parent.retypeReversal(rev, n);
        };
        typeCombo.valueProperty().addListener(typeListener);

        formsEditor.setMultiText(rev.getForms());
        if (rev.getMain() != null) {
            ReversalEditor mainEditor = new ReversalEditor(dictionary);
            mainEditor.setReversal(rev.getMain());
            mainBox.getChildren().add(mainEditor);
        }
    }
}
