/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.DuplicateTypeException;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.LiftNote;
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
        setStyle("-fx-border-color: #c9b; -fx-border-radius: 4; -fx-background-color: #faf5f8; -fx-background-radius: 4;");

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

        TitledPane textPane = new TitledPane(I18n.get("editor.text"), textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane(I18n.get("editor.properties.withFields"), extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, textPane, extPane);
    }

    public void setNote(LiftNote note) {
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
                EditorSupport.showError(I18n.get("col.type"), I18n.get("error.duplicateNoteType", n.getId()));
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
        extensibleEditor.setModel(note);
    }
}
