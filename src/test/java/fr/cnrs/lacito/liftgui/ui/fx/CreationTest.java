package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
    @Disabled("Bug B4 of .claude/plan/testfx-bugs-2026-10.md: a new entry hidden by the search is not shown")
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
    }

    @Test
    void theEntryEditorAddsASenseWithAGloss() {
        LiftEntry entry = (LiftEntry) table().getSelectionModel().getSelectedItem();
        int senses = entry.getSenses().size();
        fire(editorButton(I18n.get("btn.addSense")));
        answerTextDialog("new meaning");
        // Then the optional grammatical info.
        if (dialogOpen()) pressDialogButton(ButtonType.OK);
        assertEquals(senses + 1, entry.getSenses().size());
        assertEquals("new meaning", entry.getSenses().getLast().getGlosses().getForms().iterator().next().toPlainText());
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
        fire(editorButton(I18n.get("btn.addExample")));
        answerTextDialog("an example");
        assertEquals(examples + 1, sense.getExamples().size());
        assertTrue(lookup("#editorContainer .hyperlink").queryAll().stream()
            .anyMatch(n -> I18n.get("sense.exampleN", examples + 1).equals(((javafx.scene.control.Hyperlink) n).getText())),
            "the editor links to the new example");
    }

    @Test
    void theSenseEditorAddsASubSense() {
        LiftSense sense = openFirstSense();
        int subSenses = sense.getSenses().size();
        int tableRows = table().getItems().size();
        fire(editorButton(I18n.get("btn.addSubSense")));
        answerTextDialog("narrower meaning");
        assertEquals(subSenses + 1, sense.getSenses().size());
        assertEquals(tableRows + 1, table().getItems().size(), "the senses table lists the new sub-sense");
    }

    @Test
    void theSenseEditorAddsANoteOfAChosenType() {
        LiftSense sense = openFirstSense();
        int notes = sense.getNotes().size();
        fire(editorButton(I18n.get("btn.addNote")));
        pressDialogButton(ButtonType.OK);
        assertEquals(notes + 1, sense.getNotes().size());
    }

    @Test
    @Disabled("Bug B3 of .claude/plan/testfx-bugs-2026-10.md: an undeclared reversal type is refused by lift-api")
    void theSenseEditorAddsAReversal() {
        LiftSense sense = openFirstSense();
        int reversals = sense.getReversals().size();
        fire(editorButton(I18n.get("btn.addReversal")));
        if (lookup(".dialog-pane .text-field").tryQuery().isPresent()
            && lookup(".dialog-pane .combo-box").tryQuery().isEmpty()) {
            answerTextDialog("rev-type");
        } else {
            pressDialogButton(ButtonType.OK);
        }
        assertEquals(reversals + 1, sense.getReversals().size());
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
    }
}
