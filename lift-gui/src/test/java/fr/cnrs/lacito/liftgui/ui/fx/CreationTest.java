package fr.cnrs.lacito.liftgui.ui.fx;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Creating entries, senses, examples… from the toolbar and the editors. */
class CreationTest extends MainWindowTest {

    private int entryCount() {
        return dictionary().getLiftDictionaryRegistry().getEntries().size();
    }

    @Test
    void newEntryAsksForItsFormAndSelectsIt() {
        fire(addButton());
        answerTextDialog("zuzu");
        assertEquals(2102, entryCount());
        LiftEntry selected = (LiftEntry) table().getSelectionModel().getSelectedItem();
        assertNotNull(selected);
        assertEquals("zuzu", selected.getForms().getForms().iterator().next().toPlainText());
        assertEquals(2102, table().getItems().size());
        assertTrue(isDirty());
    }

    @Test
    void aNewEntryHiddenByTheSearchIsShownAnyway() {
        interact(() -> searchField().setText("qqqqq"));
        fire(addButton());
        answerTextDialog("brandnew");
        LiftEntry selected = (LiftEntry) table().getSelectionModel().getSelectedItem();
        assertNotNull(selected, "the new entry is listed and selected");
        assertEquals("brandnew", editorTitle(), "the editor shows the new entry, not the previous one");
    }

    @Test
    void cancellingNewEntryCreatesNothing() {
        fire(addButton());
        pressDialogButton(ButtonType.CANCEL);
        assertEquals(2101, entryCount());
        assertFalse(isDirty());
    }

    @Test
    void theEntryEditorAddsAnEmptySenseAndTheGlossIsTypedAtOnce() {
        LiftEntry entry = (LiftEntry) table().getSelectionModel().getSelectedItem();
        int senses = entry.getSenses().size();
        add(entry, ComponentKind.SENSE);
        assertFalse(dialogOpen(), "no dialog: the sense is created empty");
        assertEquals(senses + 1, entry.getSenses().size());
        LiftSense created = entry.getSenses().getLast();
        assertTrue(created.getGlosses().isEmpty());
        assertTrue(focusOwner() instanceof TextField, "the focus is in the gloss");
        write("new meaning");
        assertEquals("new meaning", created.getGlosses().getForms().iterator().next().toPlainText());
        assertTrue(isDirty());
    }

    private LiftSense openFirstSense() {
        openView("nav.senses");
        selectRow(0);
        return (LiftSense) table().getSelectionModel().getSelectedItem();
    }

    @Test
    void theSenseEditorAddsAnExample() {
        LiftSense sense = openFirstSense();
        int examples = sense.getExamples().size();
        add(sense, ComponentKind.EXAMPLE);
        assertEquals(examples + 1, sense.getExamples().size());
        assertTrue(isDirty());
        assertTrue(lookup("#editorContainer .hyperlink").queryAll().stream()
            .anyMatch(n -> I18n.get("sense.exampleN", examples + 1).equals(((javafx.scene.control.Hyperlink) n).getText())),
            "the editor links to the new example");
    }

    @Test
    void theSenseEditorAddsASubSense() {
        LiftSense sense = openFirstSense();
        int subSenses = sense.getSenses().size();
        int tableRows = table().getItems().size();
        add(sense, ComponentKind.SUB_SENSE);
        assertEquals(subSenses + 1, sense.getSenses().size());
        assertTrue(isDirty());
        assertEquals(tableRows + 1, table().getItems().size(), "the senses table lists the new sub-sense");
    }

    @Test
    void theSenseEditorAddsANoteOfAChosenType() {
        LiftSense sense = openFirstSense();
        int notes = sense.getNotes().size();
        addTyped(sense, ComponentKind.NOTE, null);
        assertEquals(notes + 1, sense.getNotes().size());
        assertTrue(isDirty());
    }

    @Test
    void closingTheMenuOfTypesLeavesTheDictionaryUnmodified() {
        LiftSense sense = openFirstSense();
        int notes = sense.getNotes().size();
        typeMenu(sense, ComponentKind.NOTE);
        assertEquals(notes, sense.getNotes().size());
        assertFalse(isDirty());
    }

    @Test
    void theSenseEditorAddsAReversal() {
        LiftSense sense = openFirstSense();
        int reversals = sense.getReversals().size();
        boolean anyType = typeMenu(sense, ComponentKind.REVERSAL).stream()
            .anyMatch(i -> !(i instanceof javafx.scene.control.SeparatorMenuItem) && !I18n.get("menu.otherType").equals(i.getText()));
        if (anyType) {
            addTyped(sense, ComponentKind.REVERSAL, null);
        } else {
            addTyped(sense, ComponentKind.REVERSAL, I18n.get("menu.otherType"));
            answerTextDialog("rev-type");
        }
        assertEquals(reversals + 1, sense.getReversals().size());
        assertTrue(isDirty());
    }

    @Test
    void pasteInTheEntriesViewCreatesAnEntry() {
        interact(() -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString("pastedform");
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            table().requestFocus();
        });
        fireMenu("menu.edit", "menu.edit.paste");
        assertEquals(2102, entryCount());
        LiftEntry selected = (LiftEntry) table().getSelectionModel().getSelectedItem();
        assertEquals("pastedform", selected.getForms().getForms().iterator().next().toPlainText());
        assertTrue(isDirty());
    }
}
