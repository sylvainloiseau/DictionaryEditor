package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import java.util.List;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/** Edits made in the editor panel reach the dictionary, the table and the window title. */
class EditingTest extends MainWindowTest {

    /** The first editable field of the entry editor: a form of the entry. */
    private TextField firstFormField() {
        List<TextField> fields = editorTextFields();
        assertFalse(fields.isEmpty(), "the entry editor has editable fields");
        return fields.get(0);
    }

    @Test
    void typingInTheEditorMarksTheDictionaryModified() {
        clickOn(firstFormField()).write("q");
        assertTrue(isDirty());
        assertTrue(windowTitle().endsWith("*"), windowTitle());
    }

    @Test
    void anEditedFormIsShownInTheTable() {
        LiftEntry entry = (LiftEntry) table().getSelectionModel().getSelectedItem();
        TextField field = firstFormField();
        clickOn(field).write("Q");
        String text = entry.getForms().getForms().stream()
            .map(f -> f.toPlainText()).filter(t -> t.contains("Q")).findFirst().orElse(null);
        assertNotNull(text, "the model has the typed text");
        boolean cellShowsIt = table().getVisibleLeafColumns().stream()
            .anyMatch(c -> c.getCellObservableValue(table().getItems().indexOf(entry)) != null
                && text.equals(String.valueOf(c.getCellObservableValue(table().getItems().indexOf(entry)).getValue())));
        assertTrue(cellShowsIt, "the table follows the edit");
    }

    @Test
    void aFormCanBeEditedInTheTable() {
        TableView<LiftEntry> t = table();
        LiftEntry entry = t.getItems().get(0);
        TableColumn<LiftEntry, ?> firstForm = t.getVisibleLeafColumn(0);
        String lang = firstForm.getText();
        interact(() -> t.edit(0, firstForm));
        TextField cellField = lookup("#tableContainer .table-cell .text-field").queryAs(TextField.class);
        interact(() -> cellField.setText("tablecell"));
        clickOn(cellField).press(KeyCode.ENTER).release(KeyCode.ENTER);
        assertEquals("tablecell", entry.getForms().getForm(lang).orElseThrow().toPlainText());
        assertTrue(isDirty());
    }

    @Test
    void editingUpdatesTheModificationDateOfTheEntry() {
        LiftEntry entry = (LiftEntry) table().getSelectionModel().getSelectedItem();
        String before = entry.getDateModified().orElse("");
        clickOn(firstFormField()).write("x");
        String after = entry.getDateModified().orElse("");
        assertNotEquals(before, after);
        assertTrue(after.startsWith(String.valueOf(java.time.Year.now().getValue())), after);
    }
}
