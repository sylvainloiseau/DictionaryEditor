/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import java.util.List;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * Editor for {@link AbstractNotable} objects.
 *
 * Extends {@link ExtensibleWithFieldEditor} by adding a "Notes" section.
 * Used by LiftEntry, LiftSense, and LiftExample which all inherit from AbstractNotable.
 */
public class NotableEditor extends ExtensibleWithFieldEditor {

    private final VBox notesBox = new VBox(6);
    private final LiftDictionary dictionary;

    public NotableEditor(LiftDictionary dictionary) {
        super(dictionary);
        this.dictionary = dictionary;

        TitledPane notesPane = new TitledPane(I18n.get("editor.notes"), notesBox);
        notesPane.setExpanded(false);
        notesPane.setAnimated(false);

        getChildren().add(notesPane);
    }

    public void setModel(AbstractNotable model) {
        setModel(model, null);
    }

    public void setModel(AbstractNotable model, ExtensibleAddActions addActions) {
        super.setModel(model, addActions);

        notesBox.getChildren().clear();
        if (model == null) return;

        if (addActions != null) {
            Button addNoteBtn = new Button(I18n.get("btn.addNote"));
            addNoteBtn.getStyleClass().add("example-add-button");
            addNoteBtn.setOnAction(e -> {
                List<String> types = EditorSupport.sortedFeatureIds(dictionary.getHeader().getNoteTypeManager());
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addNote"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    addActions.addNote(type);
                    addActions.refresh();
                });
            });
            notesBox.getChildren().add(new FlowPane(6, 4, addNoteBtn));
        }

        for (LiftNote note : model.getNotes().values()) {
            NoteEditor ne = new NoteEditor(dictionary);
            ne.setNote(note);
            Runnable onDelete = addActions == null ? null : () -> ComponentDeletion.deleteChecked(
                () -> model.deleteNote(note), () -> model.addNote(note), addActions::refresh);
            notesBox.getChildren().add(EditorSupport.withDeleteButton(ne, onDelete));
        }
    }
}
