package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionaryRegistry;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/** Deleting objects from the editor panel, and undoing the deletions. */
class DeletionTest extends MainWindowTest {

    private LiftDictionaryRegistry reg() {
        return dictionary().getLiftDictionaryRegistry();
    }

    /** Selects in the entry table an entry that nothing refers to, and returns it. */
    private LiftEntry selectUnreferencedEntry() {
        LiftEntry entry = reg().getEntries().stream()
            .filter(x -> x.getId().map(id -> !reg().isReferenced(id)).orElse(true)
                && x.getSenses().stream().allMatch(s -> s.getId().map(id -> !reg().isReferenced(id)).orElse(true)))
            .findFirst().orElseThrow();
        interact(() -> table().getSelectionModel().select(entry));
        return entry;
    }

    @Test
    void deletingAnEntryCanBeUndoneAndRedone() {
        LiftEntry entry = selectUnreferencedEntry();
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertFalse(reg().getEntries().contains(entry));
        assertFalse(table().getItems().contains(entry));
        assertTrue(isDirty());

        fireMenuItem("undoMenuItem");
        assertTrue(reg().getEntries().contains(entry));
        assertTrue(table().getItems().contains(entry));

        fireMenuItem("redoMenuItem");
        assertFalse(reg().getEntries().contains(entry));
    }

    @Test
    void theUndoShortcutUndoesTheDeletion() {
        LiftEntry entry = selectUnreferencedEntry();
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        pressShortcut(KeyCode.Z);
        assertTrue(reg().getEntries().contains(entry));
    }

    @Test
    void cancellingTheConfirmationKeepsTheEntry() {
        LiftEntry entry = selectUnreferencedEntry();
        fire(deleteButton());
        pressDialogButton(ButtonType.CANCEL);
        assertTrue(reg().getEntries().contains(entry));
    }

    @Test
    void cancellingTheConfirmationLeavesTheDictionaryUnmodified() {
        LiftEntry entry = selectUnreferencedEntry();
        fire(deleteButton());
        pressDialogButton(ButtonType.CANCEL);
        assertTrue(reg().getEntries().contains(entry));
        assertFalse(isDirty());
    }

    @Test
    void anEntryOthersReferToIsNotDeleted() {
        LiftEntry referenced = reg().getEntries().stream()
            .filter(x -> x.getId().map(id -> reg().isReferenced(id)).orElse(false))
            .findFirst().orElseThrow();
        interact(() -> table().getSelectionModel().select(referenced));
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertTrue(dialogOpen(), "an error explains why");
        pressDialogButton(ButtonType.OK);
        assertTrue(reg().getEntries().contains(referenced));
    }

    @Test
    void deletingANoteFromTheNotesView() {
        openView("nav.notes");
        selectRow(0);
        LiftNote note = (LiftNote) table().getSelectionModel().getSelectedItem();
        int rows = table().getItems().size();
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertFalse(reg().getNotes().contains(note));
        assertEquals(rows - 1, table().getItems().size());
        fireMenuItem("undoMenuItem");
        assertTrue(reg().getNotes().contains(note));
    }

    @Test
    void deletingFromASubsetRemovesTheRow() {
        openView("nav.traitValues");
        selectRow(0);
        fire(editorButton(fr.cnrs.lacito.liftgui.ui.I18n.get("btn.accessObjects")));
        LiftEntry entry = table().getItems().stream().map(x -> (LiftEntry) x)
            .filter(x -> x.getId().map(id -> !reg().isReferenced(id)).orElse(true)
                && x.getSenses().stream().allMatch(s -> s.getId().map(id -> !reg().isReferenced(id)).orElse(true)))
            .findFirst().orElseThrow();
        interact(() -> table().getSelectionModel().select(entry));
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertFalse(table().getItems().contains(entry));
    }

    @Test
    void deletingASenseFromTheSensesView() {
        openView("nav.senses");
        LiftSense sense = reg().getSenses().stream()
            .filter(s -> s.getId().map(id -> !reg().isReferenced(id)).orElse(true) && s.getSenses().isEmpty())
            .findFirst().orElseThrow();
        interact(() -> table().getSelectionModel().select(sense));
        LiftEntry entry = sense.getParentEntry();
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertFalse(entry.getSenses().contains(sense));
        assertFalse(table().getItems().contains(sense));
        fireMenuItem("undoMenuItem");
        assertTrue(entry.getSenses().contains(sense));
    }

    @Test
    void deletingAnExampleFromTheExamplesView() {
        openView("nav.examples");
        selectRow(0);
        LiftExample ex = (LiftExample) table().getSelectionModel().getSelectedItem();
        LiftSense parent = ex.getParent();
        fire(deleteButton());
        pressDialogButton(ButtonType.OK);
        assertFalse(parent.getExamples().contains(ex));
        fireMenuItem("undoMenuItem");
        assertTrue(parent.getExamples().contains(ex));
    }
}
