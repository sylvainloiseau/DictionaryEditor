/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftfx.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.DuplicateTypeException;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftfx.EditActions;
import fr.cnrs.lacito.liftfx.internal.EditorSupport;
import fr.cnrs.lacito.liftfx.internal.Messages;
import fr.cnrs.lacito.liftfx.internal.SectionPane;
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
 * Editor for a single {@link LiftNote}: type (from the header note types), text,
 * and the inherited extensible properties.
 */
public final class NoteEditor extends VBox {

    private final ComboBox<Feature> typeCombo = new ComboBox<>();
    private final MultiTextEditor textEditor;
    private final ExtensibleWithFieldEditor extensibleEditor;
    private ChangeListener<Feature> typeListener;
    private boolean reverting = false;
    private final LiftDictionary dictionary;

    public NoteEditor(LiftDictionary dictionary) {
        this.dictionary = dictionary;
        this.extensibleEditor = new ExtensibleWithFieldEditor(dictionary);
        this.textEditor = new MultiTextEditor(dictionary);
        super(6);
        setPadding(new Insets(4));

        typeCombo.setEditable(false);
        typeCombo.setPromptText(Messages.get("col.type"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        EditorSupport.showFeatureIds(typeCombo);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label(Messages.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane textPane = new TitledPane(Messages.get("editor.text"), textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        SectionPane extPane = SectionPane.properties(Messages.get("editor.properties.withFields"), extensibleEditor);

        getChildren().addAll(grid, textPane, extPane);
    }

    public void setNote(LiftNote note) {
        setNote(note, null);
    }

    /** With {@code actions}, the properties of the note can be added and deleted. */
    public void setNote(LiftNote note, EditActions actions) {
        if (typeListener != null) typeCombo.valueProperty().removeListener(typeListener);
        typeListener = null;
        if (note == null) {
            typeCombo.setDisable(true);
            typeCombo.getItems().clear();
            textEditor.setMultiText(null);
            extensibleEditor.setModel(null);
            return;
        }
        typeCombo.setItems(FXCollections.observableArrayList(
            EditorSupport.sortedFeatures(dictionary.getHeader().getNoteTypeManager())));
        if (note.getType() != null && !typeCombo.getItems().contains(note.getType())) {
            typeCombo.getItems().add(0, note.getType());
        }
        typeCombo.setValue(note.getType());
        AbstractNotable parent = note.getParent();
        typeCombo.setDisable(parent == null);
        typeListener = (obs, o, n) -> {
            if (reverting || n == null || n == note.getType() || parent == null) return;
            try {
                parent.retypeNote(note, n);
            } catch (DuplicateTypeException ex) {
                EditorSupport.showError(Messages.get("col.type"), Messages.get("error.duplicateNoteType", n.getId()));
                reverting = true;
                try {
                    typeCombo.setValue(o);
                } finally {
                    reverting = false;
                }
            }
        };
        typeCombo.valueProperty().addListener(typeListener);
        textEditor.setMultiText(note.getText());
        extensibleEditor.setModel(note, actions);
    }
}
