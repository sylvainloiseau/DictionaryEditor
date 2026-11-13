package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** The document: unsaved changes, closing, new dictionary, validation. */
class DocumentTest extends MainWindowTest {

    /** Sends a close request to the window; returns it once handled (consumed = the window stays). */
    private WindowEvent requestClose() {
        WindowEvent ev = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        Platform.runLater(() -> stage.getOnCloseRequest().handle(ev));
        WaitForAsyncUtils.waitForFxEvents();
        return ev;
    }

    private void makeAChange() {
        clickOn(editorTextFields().get(0)).write("z");
        assertTrue(isDirty());
    }

    private void pressButtonWithData(ButtonBar.ButtonData data) {
        DialogPane pane = dialog();
        ButtonType type = pane.getButtonTypes().stream().filter(b -> b.getButtonData() == data).findFirst().orElseThrow();
        pressDialogButton(type);
    }

    @Test
    void closingWithoutChangesDoesNotAsk() {
        WindowEvent ev = requestClose();
        assertFalse(dialogOpen());
        assertFalse(ev.isConsumed());
    }

    @Test
    void closingWithChangesAsksAndCancelKeepsTheWindow() {
        makeAChange();
        WindowEvent ev = requestClose();
        assertTrue(dialogOpen(), "the unsaved changes are announced");
        pressDialogButton(ButtonType.CANCEL);
        assertTrue(ev.isConsumed(), "the window stays open");
        assertTrue(isDirty());
    }

    @Test
    void closingWithChangesCanDiscardThem() {
        makeAChange();
        WindowEvent ev = requestClose();
        pressButtonWithData(ButtonBar.ButtonData.NO);
        assertFalse(ev.isConsumed(), "the window closes");
    }

    @Test
    void newDictionaryAsksForItsLanguages() {
        fireMenu("menu.file", "menu.file.new");
        DialogPane pane = dialog();
        List<TextField> fields = from(pane).lookup(".text-field").queryAllAs(TextField.class).stream().toList();
        interact(() -> {
            fields.get(0).setText("xx");
            fields.get(1).setText("en, fr");
        });
        pressDialogButton(ButtonType.OK);
        assertEquals(0, dictionary().getLiftDictionaryRegistry().getEntries().size());
        assertTrue(dictionary().getObjectLanguageManager().getLanguages().contains("xx"));
        assertEquals(I18n.get("nav.entries"), viewTitle());
        assertEquals(0, table().getItems().size());
        assertTrue(windowTitle().contains(I18n.get("title.untitled")), windowTitle());
        assertFalse(isDirty());
    }

    @Test
    void newDictionaryRefusesMissingLanguages() {
        fireMenu("menu.file", "menu.file.new");
        pressDialogButton(ButtonType.OK);
        assertTrue(dialogOpen(), "an error is shown");
        pressDialogButton(ButtonType.OK);
        assertEquals(2101, dictionary().getLiftDictionaryRegistry().getEntries().size());
    }

    @Test
    void newDictionaryWithUnsavedChangesAsksFirst() {
        makeAChange();
        fireMenu("menu.file", "menu.file.new");
        assertTrue(dialog().getContentText().contains(I18n.get("confirm.unsavedChanges")));
        pressDialogButton(ButtonType.CANCEL);
        assertEquals(2101, dictionary().getLiftDictionaryRegistry().getEntries().size());
    }

    @Test
    void validationShowsAReport() {
        fireMenu("menu.tools", "menu.tools.validate");
        DialogPane pane = dialog();
        assertTrue(digits(pane.getHeaderText()).contains("2101"), pane.getHeaderText());
        pressDialogButton(ButtonType.OK);
    }

    @Test
    void addingAnEntryInANewDictionaryWorks() {
        fireMenu("menu.file", "menu.file.new");
        DialogPane pane = dialog();
        List<TextField> fields = from(pane).lookup(".text-field").queryAllAs(TextField.class).stream().toList();
        interact(() -> {
            fields.get(0).setText("xx");
            fields.get(1).setText("en");
        });
        pressDialogButton(ButtonType.OK);
        fire(addButton());
        answerTextDialog("first");
        assertEquals(1, table().getItems().size());
        assertTrue(isDirty());
        assertTrue(windowTitle().endsWith("*"));
    }
}
