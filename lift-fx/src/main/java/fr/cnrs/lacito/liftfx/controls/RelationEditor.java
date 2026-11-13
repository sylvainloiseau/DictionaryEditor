/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.HasRelations;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
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
 * Editor for a single {@link LiftRelation}: type (from the header relation types),
 * target (read-only), order, usage, and the extensible properties.
 */
public final class RelationEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final TextField targetField = EditorSupport.readOnlyField("");
    private final TextField orderField = new TextField();
    private ChangeListener<Feature> typeListener;
    private ChangeListener<String> orderListener;
    private final MultiTextEditor usageEditor;
    private final ExtensibleWithFieldEditor extensibleEditor;
    private final LiftDictionary dictionary;

    public RelationEditor(LiftDictionary dictionary) {
        super(6);
        this.dictionary = dictionary;
        this.usageEditor = new MultiTextEditor(dictionary);
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        setPadding(new Insets(4));

        typeCombo.setEditable(false);
        typeCombo.setPromptText(Messages.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);
        orderField.setPromptText(Messages.get("col.order"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label(Messages.get("col.type")), 0, r);
        grid.add(typeCombo, 1, r++);
        grid.add(new Label(Messages.get("col.ref")), 0, r);
        grid.add(targetField, 1, r++);
        grid.add(new Label(Messages.get("col.order")), 0, r);
        grid.add(orderField, 1, r++);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        GridPane.setHgrow(targetField, Priority.ALWAYS);
        GridPane.setHgrow(orderField, Priority.ALWAYS);

        TitledPane usagePane = new TitledPane(Messages.get("col.usage"), usageEditor);
        usagePane.setExpanded(false);
        usagePane.setAnimated(false);

        SectionPane extPane = SectionPane.properties(Messages.get("editor.properties.withFields"), extensibleEditor);

        getChildren().addAll(grid, usagePane, extPane);
    }

    public void setRelation(LiftRelation rel) {
        setRelation(rel, null);
    }

    /** With {@code actions}, the properties of the relation can be added and deleted. */
    public void setRelation(LiftRelation rel, EditActions actions) {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        if (orderListener != null) orderField.textProperty().removeListener(orderListener);
        typeListener = null;
        orderListener = null;

        if (rel == null) {
            typeCombo.getItems().clear();
            typeCombo.setDisable(true);
            targetField.setText("");
            orderField.setText("");
            usageEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }

        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getRelationTypeManager())));
        if (rel.getType() != null && !typeCombo.getItems().contains(rel.getType())) {
            typeCombo.getItems().add(0, rel.getType());
        }
        typeCombo.setValue(rel.getType());
        HasRelations parent = rel.getParent();
        typeCombo.setDisable(parent == null);
        typeListener = (obs, o, n) -> {
            if (n != null && n != rel.getType() && parent != null) parent.retypeRelation(rel, n);
        };
        typeCombo.valueProperty().addListener(typeListener);

        targetField.setText(rel.getRefId().orElse(""));

        orderField.setText(rel.getOrder().map(String::valueOf).orElse(""));
        orderListener = (obs, o, n) -> {
            if (n != null && n.trim().matches("\\d+")) rel.setOrder(Integer.parseInt(n.trim()));
        };
        orderField.textProperty().addListener(orderListener);

        usageEditor.setMultiText(rel.getUsage());
        usageEditor.setEditActions(actions);
        extensibleEditor.setModel(rel, actions);
    }
}
