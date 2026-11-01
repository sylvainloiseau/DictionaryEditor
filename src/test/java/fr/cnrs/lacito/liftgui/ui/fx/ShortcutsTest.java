package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.AppPreferences;
import fr.cnrs.lacito.liftgui.ui.app.Commands;
import fr.cnrs.lacito.liftgui.ui.controls.ComponentKind;
import fr.cnrs.lacito.liftgui.ui.controls.ItemCard;
import fr.cnrs.lacito.liftgui.ui.controls.Reveal;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** The Add menu and its shortcuts, the shortcuts of the menus, and the shortcuts dialog. */
class ShortcutsTest extends MainWindowTest {

    private static final boolean MAC = System.getProperty("os.name").startsWith("Mac");

    /* ─── The Add menu ─── */

    private LiftEntry entryWithSenses(int n) {
        return dictionary().getLiftDictionaryRegistry().getEntries().stream()
            .filter(e -> e.getSenses().size() >= n).findFirst().orElseThrow();
    }

    private void showEntry(LiftEntry e) {
        openView("nav.entries");
        interact(() -> table().getSelectionModel().select(e));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private MenuItem item(String id) {
        return lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
            .flatMap(m -> flatten(m).stream()).filter(i -> id.equals(i.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("no menu item " + id));
    }

    private static java.util.List<MenuItem> flatten(Menu m) {
        java.util.List<MenuItem> all = new java.util.ArrayList<>();
        for (MenuItem i : m.getItems()) {
            all.add(i);
            if (i instanceof Menu sub) all.addAll(flatten(sub));
        }
        return all;
    }

    @Test
    void addExampleWithTheFocusInTheSecondSenseAddsToIt() {
        LiftEntry entry = entryWithSenses(2);
        showEntry(entry);
        LiftSense second = entry.getSenses().get(1);
        int examples = second.getExamples().size();
        ItemCard card = card(second);
        interact(() -> {
            card.setExpanded(true);
            Reveal.expandAncestors(card);
        });
        WaitForAsyncUtils.waitForFxEvents();
        TextField field = from(card.content()).lookup(".text-field").queryAllAs(TextField.class).stream()
            .filter(TextField::isEditable).findFirst().orElseThrow();
        interact(field::requestFocus);
        fireMenuItem("add.example");
        assertEquals(examples + 1, second.getExamples().size(), "the example goes to the sense with the cursor");
    }

    @Test
    void addExampleIsDisabledWhereThereIsNoTarget() {
        LiftEntry entry = entryWithSenses(2);
        showEntry(entry);
        interact(() -> table().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();
        MenuItem example = item("add.example");
        assertTrue(example.isDisable(), "two senses: the cursor decides");
        assertEquals(I18n.get("menu.add.chooseFirst", ComponentKind.EXAMPLE.label()), example.getText());

        LiftEntry one = dictionary().getLiftDictionaryRegistry().getEntries().stream()
            .filter(e -> e.getSenses().size() == 1).findFirst().orElseThrow();
        showEntry(one);
        interact(() -> table().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(item("add.example").isDisable(), "one sense: it takes the example");

        openView("nav.notes");
        selectRow(0);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(item("add.example").isDisable(), "a note has no examples");
        assertFalse(item("add.trait").isDisable() && item("add.annotation").isDisable(), "a note has traits and annotations");
    }

    @Test
    void addNoteOpensTheMenuOfTheTypes() {
        LiftEntry entry = entryWithSenses(1);
        showEntry(entry);
        interact(() -> table().requestFocus());
        fireMenuItem("add.note");
        MenuButton plus = (MenuButton) section(entry, ComponentKind.NOTE).addButton();
        assertTrue(plus.isShowing());
        interact(plus::hide);
    }

    @Test
    void theAddItemsHaveTheirDefaultShortcuts() {
        KeyCombination alt = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);
        assertEquals(alt, item("add.sense").getAccelerator());
        assertNull(item("add.variant").getAccelerator(), "no default for the less frequent kinds");
        assertEquals(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), item("file.save").getAccelerator());
        assertEquals(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN), item("view.senses").getAccelerator());
    }

    @Test
    void shortcutTwoOpensTheSensesWhileTyping() {
        LiftEntry entry = entryWithSenses(1);
        showEntry(entry);
        TextField field = editorTextFields().getFirst();
        interact(field::requestFocus);
        press(KeyCode.DIGIT2, false, false);
        assertEquals(I18n.get("nav.senses"), viewTitle());
    }

    @Test
    void theAddShortcutAddsASense() {
        LiftEntry entry = entryWithSenses(1);
        showEntry(entry);
        int senses = entry.getSenses().size();
        interact(() -> table().requestFocus());
        press(KeyCode.S, true, false);
        assertEquals(senses + 1, entry.getSenses().size());
    }

    /* ─── The shortcuts dialog ─── */

    private TreeTableView<?> openDialog() {
        fireMenu("menu.help", "menu.help.shortcuts");
        return lookup("#shortcutsTable").queryAs(TreeTableView.class);
    }

    private void select(TreeTableView<?> table, String label) {
        interact(() -> {
            for (int i = 0; i < table.getExpandedItemCount(); i++) {
                TreeItem<?> t = table.getTreeItem(i);
                if (t != null && t.isLeaf() && label.equals(table.getColumns().getFirst().getCellData(i))) {
                    table.getSelectionModel().select(i);
                    table.requestFocus();
                    return;
                }
            }
            throw new AssertionError("no command " + label);
        });
    }

    /** Starts recording on the selected line (Enter), then presses the keys. */
    private void record(TreeTableView<?> table, KeyCode code, boolean alt, boolean shift) {
        interact(() -> Event.fireEvent(table, key(KeyCode.ENTER, false, false, false)));
        interact(() -> Event.fireEvent(table, key(code, true, alt, shift)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static KeyEvent key(KeyCode code, boolean shortcut, boolean alt, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut && !MAC, alt, shortcut && MAC);
    }

    /** Sends shortcut (+alt, +shift) + key to the focused node. */
    private void press(KeyCode code, boolean alt, boolean shift) {
        interact(() -> {
            Node target = stage.getScene().getFocusOwner() != null ? stage.getScene().getFocusOwner() : stage.getScene().getRoot();
            Event.fireEvent(target, key(code, true, alt, shift));
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void closeDialog() {
        pressDialogButton(ButtonType.CLOSE);
    }

    private String message() {
        return lookup("#shortcutsMessage").queryAs(Label.class).getText();
    }

    @Test
    void aChangedShortcutIsTheAcceleratorAtOnceAndIsSaved() {
        TreeTableView<?> table = openDialog();
        select(table, I18n.get("menu.tools.exportCsv"));
        record(table, KeyCode.K, true, false);
        closeDialog();
        KeyCombination expected = new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN);
        assertTrue(Commands.sameKeys(expected, item("tools.exportCsv").getAccelerator()), String.valueOf(item("tools.exportCsv").getAccelerator()));
        assertTrue(Commands.sameKeys(expected, KeyCombination.valueOf(new AppPreferences().shortcut("tools.exportCsv"))));
    }

    @Test
    void aShortcutAlreadyUsedOffersToReplaceTheOther() {
        TreeTableView<?> table = openDialog();
        select(table, I18n.get("menu.tools.exportCsv"));
        record(table, KeyCode.O, false, false);
        assertTrue(message().contains(I18n.get("menu.file.open")), message());
        clickOn("#shortcutsReplace");
        closeDialog();
        assertNull(item("file.open").getAccelerator(), "Open lost its shortcut");
        assertEquals(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), item("tools.exportCsv").getAccelerator());
    }

    @Test
    void theKeysOfTextEditingAreRefused() {
        TreeTableView<?> table = openDialog();
        select(table, I18n.get("menu.tools.exportCsv"));
        record(table, KeyCode.C, false, false);
        assertTrue(message().contains(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN).getDisplayText()), message());
        interact(() -> Event.fireEvent(table, key(KeyCode.ESCAPE, false, false, false)));
        closeDialog();
        assertNull(item("tools.exportCsv").getAccelerator());
    }

    @Test
    void resetAllPutsTheDefaultsBack() {
        TreeTableView<?> table = openDialog();
        select(table, I18n.get("menu.file.save"));
        interact(() -> Event.fireEvent(table, key(KeyCode.ENTER, false, false, false)));
        interact(() -> Event.fireEvent(table, key(KeyCode.BACK_SPACE, false, false, false)));
        closeDialog();
        assertNull(item("file.save").getAccelerator(), "Backspace removes the shortcut");

        openDialog();
        clickOn("#shortcutsResetAll");
        closeDialog();
        assertEquals(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), item("file.save").getAccelerator());
        assertNull(new AppPreferences().shortcut("file.save"), "the default is not saved");
    }

    @Test
    void thePreferencesOpenTheShortcuts() {
        fireMenu("menu.file", "menu.file.preferences");
        DialogPane prefs = dialog();
        javafx.scene.control.Button b = from(prefs).lookup("#prefsShortcuts").queryButton();
        Platform.runLater(b::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(lookup("#shortcutsTable").tryQuery().isPresent());
    }
}
