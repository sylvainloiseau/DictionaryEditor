package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.undo.History;
import java.util.List;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/** The undo history, seen from the window: typing, shortcuts, labels, the save point. */
class UndoTest extends MainWindowTest {

    private History history() {
        return document().history();
    }

    private LiftEntry selectedEntry() {
        return (LiftEntry) table().getSelectionModel().getSelectedItem();
    }

    private TextField firstField() {
        List<TextField> fields = editorTextFields();
        assertFalse(fields.isEmpty(), "the entry editor has editable fields");
        return fields.get(0);
    }

    private void undo() {
        fire(lookup("#undoButton").queryButton());
    }

    private void redo() {
        fire(lookup("#redoButton").queryButton());
    }

    private String undoMenuText() {
        MenuBar bar = lookup(".menu-bar").queryAs(MenuBar.class);
        return bar.getMenus().stream().flatMap(m -> ((Menu) m).getItems().stream())
            .filter(i -> "undoMenuItem".equals(i.getId())).findFirst().map(MenuItem::getText).orElseThrow();
    }

    @Test
    void typingThenUndoRestoresTheTextAndTheSavedState() {
        TextField field = firstField();
        String before = field.getText();
        clickOn(field).write("xyz");
        assertTrue(isDirty());
        press(KeyCode.TAB).release(KeyCode.TAB);
        undo();
        assertFalse(isDirty(), "undoing back to the loaded state removes the *");
        assertEquals(before, firstField().getText());
    }

    @Test
    void oneUndoRemovesAWholeTypingSession() {
        TextField field = firstField();
        String before = field.getText();
        clickOn(field).write("abc");
        press(KeyCode.TAB).release(KeyCode.TAB);
        assertEquals(1, history().cursor());
        undo();
        assertEquals(before, firstField().getText());
        redo();
        assertEquals(before + "abc", firstField().getText());
    }

    @Test
    void theUndoShortcutInAFieldUndoesInTheHistory() {
        TextField field = firstField();
        String before = field.getText();
        clickOn(field).write("q");
        pressShortcut(KeyCode.Z);
        assertEquals(before, field.getText(), "the field shows the restored text");
        assertEquals(0, history().cursor());
        assertTrue(field.isFocused(), "the field keeps the focus");
    }

    @Test
    void theMenuNamesWhatWouldBeUndone() {
        assertEquals(I18n.get("menu.edit.undo"), undoMenuText());
        clickOn(firstField()).write("q");
        assertTrue(undoMenuText().startsWith(I18n.get("menu.edit.undoLabel", "").trim()), undoMenuText());
        assertNotEquals(I18n.get("menu.edit.undo"), undoMenuText());
    }

    @Test
    void anAdditionWithItsDialogIsOneUndoStep() {
        openView("nav.senses");
        selectRow(0);
        LiftSense sense = (LiftSense) table().getSelectionModel().getSelectedItem();
        int notes = sense.getNotes().size();
        int steps = history().cursor();
        fire(editorButton(I18n.get("btn.addNote")));
        pressDialogButton(ButtonType.OK);
        assertEquals(notes + 1, sense.getNotes().size());
        assertEquals(steps + 1, history().cursor());
        undo();
        assertEquals(notes, sense.getNotes().size());
        assertFalse(isDirty());
    }

    @Test
    void aCancelledDialogRecordsNothing() {
        openView("nav.senses");
        selectRow(0);
        fire(editorButton(I18n.get("btn.addNote")));
        pressDialogButton(ButtonType.CANCEL);
        assertEquals(0, history().entries().size());
        assertFalse(history().canUndoProperty().get());
    }

    @Test
    void aNewEntryIsUndoneAndRedone() {
        int entries = dictionary().getLiftDictionaryRegistry().getEntries().size();
        fire(addButton());
        answerTextDialog("zuzu");
        assertEquals(entries + 1, dictionary().getLiftDictionaryRegistry().getEntries().size());
        assertTrue(undoMenuText().contains("zuzu"), undoMenuText());
        undo();
        assertEquals(entries, dictionary().getLiftDictionaryRegistry().getEntries().size());
        redo();
        assertEquals(entries + 1, dictionary().getLiftDictionaryRegistry().getEntries().size());
        assertEquals("zuzu", editorTitle(), "the redone entry is shown");
    }

    @Test
    void undoingADeletedSenseShowsItAgain() {
        LiftEntry entry = dictionary().getLiftDictionaryRegistry().getEntries().stream()
            .filter(e -> e.getSenses().size() > 1)
            .filter(e -> e.getSenses().stream().allMatch(s -> s.getId()
                .map(id -> !dictionary().getLiftDictionaryRegistry().isReferenced(id)).orElse(true)))
            .findFirst().orElseThrow();
        LiftSense first = entry.getSenses().getFirst();
        interact(() -> entry.deleteSense(first));
        undo();
        assertSame(first, entry.getSenses().getFirst(), "back at its position");
    }

    @Test
    void savingSetsTheSavePoint() {
        clickOn(firstField()).write("q");
        press(KeyCode.TAB).release(KeyCode.TAB);
        interact(() -> history().markSaved());
        assertFalse(isDirty());
        undo();
        assertTrue(isDirty(), "undoing past the save is a change again");
        redo();
        assertFalse(isDirty());
    }

    @Test
    void theEntryDateIsRestoredByUndo() {
        LiftEntry entry = selectedEntry();
        String before = entry.getDateModified().orElse(null);
        clickOn(firstField()).write("q");
        press(KeyCode.TAB).release(KeyCode.TAB);
        assertNotEquals(before, entry.getDateModified().orElse(null));
        undo();
        assertEquals(before, entry.getDateModified().orElse(null));
    }
}
