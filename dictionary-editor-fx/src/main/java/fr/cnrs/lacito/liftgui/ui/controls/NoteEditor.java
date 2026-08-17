
/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftHeaderRangeElement;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Programmatic editor for a {@link LiftNote}.
 *
 * Displays:
 *  - type (read-only String)
 *  - MultiTextEditor for the note's text
 *  - ExtensibleWithFieldEditor for the inherited properties
 *    (dates, traits, annotations, fields) since LiftNote extends AbstractExtensibleWithField.
 */
public final class NoteEditor extends VBox {

    private final ComboBox<LiftHeaderRangeElement> typeCombo = new ComboBox<>();
    private final MultiTextEditor textEditor = new MultiTextEditor();
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();

    public NoteEditor(LiftDictionary dictionary) {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #c9b; -fx-border-radius: 4; -fx-background-color: #faf5f8; -fx-background-radius: 4;");

        typeCombo.setEditable(false);
        typeCombo.setPromptText("type");
        typeCombo.setItems(FXCollections.observableArrayList(dictionary.getHeader().getNoteTypeManager().typesProperty().get()));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(typeCombo, 1, 0);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);

        TitledPane textPane = new TitledPane("Texte (MultiText)", textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, textPane, extPane);
    }

    public void setNote(LiftNote note, Collection<String> availableLangs) {
        if (note == null) {
            typeCombo.setDisable(true);
            textEditor.setMultiText(null);
            extensibleEditor.setModel(null, availableLangs);
            return;
        }
        typeCombo.setDisable(false);
        typeCombo.getSelectionModel().select(note.getType());
        textEditor.setAvailableLanguages(availableLangs);
        textEditor.setMultiText(note.getText());
        extensibleEditor.setModel(note, availableLangs);
    }
}
