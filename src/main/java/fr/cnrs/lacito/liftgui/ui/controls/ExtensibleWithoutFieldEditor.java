/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinitionTarget;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Programmatic editor for any {@link AbstractExtensibleWithoutField}.
 *
 * Displays: dateCreation, dateModification, list of Traits (TraitEditor), list of Annotations (AnnotationEditor).
 * When add actions are given, also offers buttons to add and delete traits and annotations.
 * This component is reused inside FieldEditor, NoteEditor, etc.
 */
public class ExtensibleWithoutFieldEditor extends VBox {

    private final TextField dateCreatedField = EditorSupport.readOnlyField("");
    private final TextField dateModifiedField = EditorSupport.readOnlyField("");
    private final VBox traitsBox = new VBox(6);
    private final VBox annotationsBox = new VBox(6);
    private final LiftDictionary dictionary;

    public ExtensibleWithoutFieldEditor(LiftDictionary dictionary) {
        super(8);
        this.dictionary = dictionary;
        setPadding(new Insets(4));

        dateCreatedField.setPromptText(I18n.get("editor.undefined"));
        dateModifiedField.setPromptText(I18n.get("editor.undefined"));

        GridPane datesGrid = new GridPane();
        datesGrid.setHgap(8);
        datesGrid.setVgap(6);
        datesGrid.add(new Label(I18n.get("field.dateCreated")), 0, 0);
        datesGrid.add(dateCreatedField, 1, 0);
        datesGrid.add(new Label(I18n.get("field.dateModified")), 0, 1);
        datesGrid.add(dateModifiedField, 1, 1);
        GridPane.setHgrow(dateCreatedField, Priority.ALWAYS);
        GridPane.setHgrow(dateModifiedField, Priority.ALWAYS);

        TitledPane traitsPane = new TitledPane(I18n.get("editor.traits"), traitsBox);
        traitsPane.setExpanded(false);
        traitsPane.setAnimated(false);

        TitledPane annotationsPane = new TitledPane(I18n.get("editor.annotations"), annotationsBox);
        annotationsPane.setExpanded(false);
        annotationsPane.setAnimated(false);

        getChildren().addAll(datesGrid, traitsPane, annotationsPane);
    }

    public void setModel(AbstractExtensibleWithoutField model) {
        setModel(model, null);
    }

    public void setModel(AbstractExtensibleWithoutField model, ExtensibleAddActions addActions) {
        traitsBox.getChildren().clear();
        annotationsBox.getChildren().clear();

        if (model == null) {
            dateCreatedField.setText("");
            dateModifiedField.setText("");
            return;
        }

        dateCreatedField.setText(model.getDateCreated().orElse(""));
        dateModifiedField.setText(model.getDateModified().orElse(""));
        LiftFieldAndTraitDefinitionTarget target = EditorSupport.targetOf(model);

        if (addActions != null) addButtons(target, addActions);

        for (LiftTrait t : model.getTraits()) {
            TraitEditor te = new TraitEditor(dictionary);
            te.setTrait(t, target);
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.delete(
                () -> model.deleteTrait(t), addActions::refresh);
            traitsBox.getChildren().add(EditorSupport.withDeleteButton(te, onDelete));
        }

        for (LiftAnnotation a : model.getAnnotations()) {
            AnnotationEditor ae = new AnnotationEditor(dictionary);
            ae.setAnnotation(a);
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.delete(
                () -> model.deleteAnnotation(a), addActions::refresh);
            annotationsBox.getChildren().add(EditorSupport.withDeleteButton(ae, onDelete));
        }
    }

    private void addButtons(LiftFieldAndTraitDefinitionTarget target, ExtensibleAddActions addActions) {
        Button addTraitBtn = new Button(I18n.get("btn.addTrait"));
        addTraitBtn.getStyleClass().add("example-add-button");
        addTraitBtn.setOnAction(e -> {
            List<String> names = EditorSupport.traitDefinitions(dictionary.getHeader(), target).stream()
                .map(LiftFieldAndTraitDefinition::getName).sorted().toList();
            ChoiceDialog<String> dlg = new ChoiceDialog<>(names.isEmpty() ? null : names.get(0), names);
            dlg.setTitle(I18n.get("btn.addTrait"));
            dlg.setHeaderText(I18n.get("col.name"));
            dlg.showAndWait().ifPresent(name -> {
                addActions.addTrait(name, "");
                addActions.refresh();
            });
        });
        traitsBox.getChildren().add(new FlowPane(6, 4, addTraitBtn));

        Button addAnnotBtn = new Button(I18n.get("btn.addAnnotation"));
        addAnnotBtn.getStyleClass().add("example-add-button");
        addAnnotBtn.setOnAction(e -> {
            List<String> names = EditorSupport.sortedFeatureIds(dictionary.getHeader().getAnnotationTypeManager());
            Optional<String> nameOpt;
            if (names.isEmpty()) {
                TextInputDialog tid = new TextInputDialog();
                tid.setTitle(I18n.get("btn.addAnnotation"));
                tid.setHeaderText(I18n.get("col.name"));
                nameOpt = tid.showAndWait();
            } else {
                ChoiceDialog<String> dlg = new ChoiceDialog<>(names.get(0), names);
                dlg.setTitle(I18n.get("btn.addAnnotation"));
                dlg.setHeaderText(I18n.get("col.name"));
                nameOpt = dlg.showAndWait();
            }
            nameOpt.filter(n -> n != null && !n.isBlank()).ifPresent(name -> {
                addActions.addAnnotation(name.trim());
                addActions.refresh();
            });
        });
        annotationsBox.getChildren().add(new FlowPane(6, 4, addAnnotBtn));
    }
}
