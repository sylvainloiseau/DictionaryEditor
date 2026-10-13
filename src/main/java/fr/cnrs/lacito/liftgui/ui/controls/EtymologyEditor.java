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
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor for a single {@link LiftEtymology}: type (from the header etymology types), source,
 * forms (object languages), glosses (meta-languages) and the extensible properties.
 */
public final class EtymologyEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final TextField sourceField = new TextField();
    private final MultiTextEditor formsEditor;
    private final MultiTextEditor glossesEditor;
    private final ExtensibleWithFieldEditor extensibleEditor;
    private ChangeListener<Feature> typeListener;
    private ChangeListener<String> sourceListener;
    private final LiftDictionary dictionary;

    public EtymologyEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.formsEditor = new MultiTextEditor(dictionary);
        this.glossesEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));
        setStyle(
            "-fx-border-color: #abc; -fx-border-radius: 4; -fx-background-color: #f4f8fa; -fx-background-radius: 4;"
        );

        typeCombo.setEditable(false);
        typeCombo.setPromptText(I18n.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);
        sourceField.setPromptText(I18n.get("col.source"));
        formsEditor.useObjectLanguages();

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label(I18n.get("col.source")), 0, 1);
        grid.add(sourceField, 1, 1);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane(I18n.get("col.forms"), formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane glossesPane = new TitledPane(I18n.get("col.gloss"), glossesEditor);
        glossesPane.setExpanded(false);
        glossesPane.setAnimated(false);

        TitledPane extPane = new TitledPane(I18n.get("editor.properties.withFields"), extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, glossesPane, extPane);
    }

    public void setEtymology(LiftEtymology ety) {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        if (sourceListener != null) sourceField.textProperty().removeListener(sourceListener);
        typeListener = null;
        sourceListener = null;
        if (ety == null) {
            typeCombo.setDisable(true);
            typeCombo.getItems().clear();
            sourceField.setText("");
            formsEditor.setMultiText(null);
            glossesEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }
        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getEtymologyTypeManager())));
        if (ety.getType() != null && !typeCombo.getItems().contains(ety.getType())) {
            typeCombo.getItems().add(0, ety.getType());
        }
        typeCombo.setValue(ety.getType());
        LiftEntry parent = ety.getParent();
        typeCombo.setDisable(parent == null);
        typeListener = (obs, o, n) -> {
            if (n != null && n != ety.getType() && parent != null) parent.retypeEtymology(ety, n);
        };
        typeCombo.valueProperty().addListener(typeListener);

        sourceField.setText(ety.getSource() != null ? ety.getSource() : "");
        sourceListener = (obs, o, n) -> ety.setSource(n == null ? "" : n);
        sourceField.textProperty().addListener(sourceListener);

        formsEditor.setMultiText(ety.getForms());
        glossesEditor.setMultiText(ety.getGlosses());
        extensibleEditor.setModel(ety);
    }
}
