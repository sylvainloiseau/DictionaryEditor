/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionTarget;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Programmatic editor for {@link AbstractExtensibleWithField}.
 *
 * Extends {@link ExtensibleWithoutFieldEditor} by also displaying
 * the list of {@link LiftField}s (each via a {@link FieldEditor}).
 */
public class ExtensibleWithFieldEditor extends ExtensibleWithoutFieldEditor {

    private final VBox fieldsBox = new VBox(6);
    private final LiftDictionary dictionary;

    public ExtensibleWithFieldEditor(LiftDictionary dictionary) {
        this.dictionary = dictionary;
        super(dictionary);

        TitledPane fieldsPane = new TitledPane(I18n.get("editor.fields"), fieldsBox);
        fieldsPane.setExpanded(false);
        fieldsPane.setAnimated(false);

        getChildren().add(fieldsPane);
    }

    public void setModel(AbstractExtensibleWithField model) {
        setModel(model, null);
    }

    public void setModel(AbstractExtensibleWithField model, ExtensibleAddActions addActions) {
        super.setModel(model, addActions);

        fieldsBox.getChildren().clear();
        if (model == null) return;

        if (addActions != null) {
            LiftFieldAndTraitDefinitionTarget target = EditorSupport.targetOf(model);
            Button addFieldBtn = new Button(I18n.get("btn.addField"));
            addFieldBtn.getStyleClass().add("example-add-button");
            addFieldBtn.setOnAction(e -> {
                List<String> types = EditorSupport.fieldDefinitions(dictionary.getHeader(), target).stream()
                    .map(LiftFieldAndTraitDefinition::getName).sorted().toList();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addField"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    addActions.addField(type);
                    addActions.refresh();
                });
            });
            fieldsBox.getChildren().add(new FlowPane(6, 4, addFieldBtn));
        }

        for (LiftField f : model.getFields().values()) {
            FieldEditor fe = new FieldEditor(dictionary);
            fe.setField(f);
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.delete(
                () -> model.deleteField(f), addActions::refresh);
            fieldsBox.getChildren().add(EditorSupport.withDeleteButton(fe, onDelete));
        }
    }
}
