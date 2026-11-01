package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** Choosing the columns of the tables, and what is kept when navigating. */
class ColumnsTest extends MainWindowTest {

    @Test
    void theColumnsPopoverAddsACountColumn() {
        openView("nav.senses");
        clickOn(lookup("#columnsButton").queryButton());
        TextField search = chooser().lookup("#columnsSearch").queryAs(TextField.class);
        interact(() -> search.setText("examples count"));
        Button add = chooser().lookup(".column-chooser-add")
            .match(n -> ("+ " + I18n.get("col.count", I18n.get("nav.examples"))).equals(((Button) n).getText()))
            .queryButton();
        interact(add::fire);
        waitApply();
        TableView<LiftSense> t = table();
        TableColumn<LiftSense, ?> col = leaf(t, "count:examples");
        LiftSense s = t.getItems().get(0);
        assertEquals(String.valueOf(s.getExamples().size()), col.getCellObservableValue(s).getValue());
        assertTrue(lookup("#columnsButton").queryButton().getText().matches(".*\\d+$"), "the badge counts the columns");
    }

    @Test
    void aLanguageChipHidesOneLanguage() {
        openView("nav.senses");
        TableView<LiftSense> t = table();
        List<String> glossLeaves = leafIds(t, "sense.gloss@");
        assertTrue(glossLeaves.size() >= 2, glossLeaves.toString());
        String hidden = glossLeaves.get(0).substring("sense.gloss@".length());
        clickOn(lookup("#columnsButton").queryButton());
        ToggleButton chip = chooser().lookup(".language-chip")
            .match(n -> hidden.equals(((ToggleButton) n).getText())
                && isInRowOf(n, I18n.get("col.gloss")))
            .queryAs(ToggleButton.class);
        interact(chip::fire);
        waitApply();
        assertFalse(leafIds(t, "sense.gloss@").contains("sense.gloss@" + hidden));
        assertEquals(glossLeaves.size() - 1, leafIds(t, "sense.gloss@").size());
        // ↺ Default brings it back.
        interact(() -> ((Hyperlink) chooser().lookup("#columnsDefault").query()).fire());
        waitApply();
        assertEquals(glossLeaves, leafIds(table(), "sense.gloss@"));
    }

    @Test
    void theChosenColumnsAndTheirOrderAreKeptWhenNavigating() {
        openView("nav.senses");
        TableView<Object> t = table();
        String first = t.getColumns().get(0).getId();
        interact(() -> {
            var c = t.getColumns().remove(0);
            t.getColumns().add(c);
        });
        WaitForAsyncUtils.waitForFxEvents();
        openView("nav.entries");
        openView("nav.senses");
        TableView<Object> again = table();
        String last = again.getColumns().get(again.getColumns().size() - 1).getId();
        assertEquals(first, last, "the dragged column stays last");
    }

    @Test
    void theHeaderMenuHidesAColumn() {
        openView("nav.senses");
        TableView<Object> t = table();
        TableColumn<Object, ?> gram = leaf(t, "sense.gramInfo");
        ContextMenu menu = gram.getContextMenu();
        assertNotNull(menu);
        MenuItem hide = menu.getItems().stream()
            .filter(i -> I18n.get("columns.hide", I18n.get("col.gramInfo")).equals(i.getText())).findFirst().orElseThrow();
        interact(hide::fire);
        assertTrue(table().getVisibleLeafColumns().stream().noneMatch(c -> "sense.gramInfo".equals(c.getId())));
    }

    @Test
    void filtersAreKeptWhenNavigatingAwayAndBack() {
        openView("nav.notes");
        int all = table().getItems().size();
        TextField filter = lookup("#tableContainer .filter-text").queryAs(TextField.class);
        interact(() -> filter.setText("on"));
        waitDebounce();
        int filtered = table().getItems().size();
        assertTrue(filtered < all);
        openView("nav.entries");
        openView("nav.notes");
        assertEquals(filtered, table().getItems().size());
        assertTrue(lookup(".filter-chip").tryQuery().isPresent());
        assertEquals("on", lookup("#tableContainer .filter-text").queryAs(TextField.class).getText());
    }

    @Test
    void reachingAnObjectHiddenByTheFiltersClearsThem() {
        LiftSense sense = dictionary().getLiftDictionaryRegistry().getSenses().stream()
            .filter(s -> !s.getExamples().isEmpty()).findFirst().orElseThrow();
        LiftExample target = sense.getExamples().get(0);
        openView("nav.examples");
        TextField filter = lookup("#tableContainer .filter-text").queryAs(TextField.class);
        interact(() -> filter.setText("zzzz-nothing"));
        waitDebounce();
        assertTrue(table().getItems().isEmpty());
        openView("nav.senses");
        interact(() -> table().getSelectionModel().select(sense));
        fire(lookup("#editorContainer .hyperlink")
            .match(n -> n instanceof Hyperlink h && I18n.get("sense.exampleN", 1).equals(h.getText()))
            .queryAs(Hyperlink.class));
        assertSame(target, table().getSelectionModel().getSelectedItem());
        assertTrue(lookup(".filter-notice").query().isVisible(), "a notice says the filters were cleared");
        assertEquals("", lookup("#tableContainer .filter-text").queryAs(TextField.class).getText());
    }

    @Test
    void anEditedRowStaysVisibleUntilTheFiltersChange() {
        openView("nav.notes");
        TableView<LiftNote> t = table();
        String textLeaf = leafIds(t, "note.text@").stream()
            .filter(id -> t.getItems().stream().anyMatch(n -> n.getText().getForm(id.substring("note.text@".length())).isPresent()))
            .findFirst().orElseThrow();
        String lang = textLeaf.substring("note.text@".length());
        LiftNote note = t.getItems().stream().filter(n -> n.getText().getForm(lang).isPresent()).findFirst().orElseThrow();
        String text = note.getText().getForm(lang).orElseThrow().toPlainText();
        Node header = lookup("#tableContainer .column-header")
            .match(n -> n instanceof javafx.scene.control.skin.TableColumnHeader h && h.getTableColumn() != null
                && textLeaf.equals(h.getTableColumn().getId())).query();
        TextField filter = from(header).lookup(".filter-text").queryAs(TextField.class);
        interact(() -> filter.setText(text));
        waitDebounce();
        assertTrue(t.getItems().contains(note));
        // Edit the cell as the table does, then check that the row is still listed.
        @SuppressWarnings("unchecked")
        TableColumn<LiftNote, String> col = (TableColumn<LiftNote, String>) leaf(t, textLeaf);
        interact(() -> {
            t.getSelectionModel().select(note);
            int row = t.getItems().indexOf(note);
            javafx.event.Event.fireEvent(col, new TableColumn.CellEditEvent<>(t, new javafx.scene.control.TablePosition<>(t, row, col),
                TableColumn.editCommitEvent(), "zzz changed"));
        });
        assertEquals("zzz changed", note.getText().getForm(lang).orElseThrow().toPlainText());
        assertTrue(t.getItems().contains(note), "the edited row stays visible");
        interact(() -> filter.setText(text + " "));
        waitDebounce();
        assertFalse(t.getItems().contains(note), "until the filters change");
    }

    @Test
    void thePreferencesChooseTheColumnsOfEveryTable() {
        openView("nav.senses");
        fireMenu("menu.file", "menu.file.preferences");
        DialogPane prefs = dialog();
        TabPane tabs = from(prefs).lookup("#prefsTabs").queryAs(TabPane.class);
        interact(() -> tabs.getSelectionModel().select(1));
        @SuppressWarnings("unchecked")
        ListView<Object> kinds = from(prefs).lookup("#prefsTables").queryAs(ListView.class);
        interact(() -> kinds.getSelectionModel().select(1)); // Senses
        Node chooserNode = from(prefs).lookup(".column-chooser").query();
        Button add = from(chooserNode).lookup(".column-chooser-add")
            .match(n -> ("+ " + I18n.get("col.id")).equals(((Button) n).getText())).queryButton();
        interact(add::fire);
        assertTrue(kinds.getItems().size() > 2);
        assertTrue(table().getVisibleLeafColumns().stream().noneMatch(c -> "sense.id".equals(c.getId())),
            "nothing changes before OK");
        Button ok = (Button) prefs.lookupButton(ButtonType.OK);
        Platform.runLater(ok::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(table().getVisibleLeafColumns().stream().anyMatch(c -> "sense.id".equals(c.getId())),
            "applied to the open table on OK");
    }

    /* ─── Helpers ─── */

    private org.testfx.service.query.NodeQuery chooser() {
        Window w = Window.getWindows().stream()
            .filter(x -> x.isShowing() && x.getScene() != null && x.getScene().getRoot().lookup(".column-chooser") != null)
            .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("no column chooser"));
        return from(w.getScene().getRoot().lookup(".column-chooser"));
    }

    private static boolean isInRowOf(Node n, String title) {
        for (Node p = n; p != null; p = p.getParent()) {
            if (p instanceof javafx.scene.layout.HBox h && h.getChildren().stream()
                .anyMatch(c -> c instanceof javafx.scene.control.Label l && title.equals(l.getText()))) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> TableColumn<T, ?> leaf(TableView<T> t, String id) {
        return t.getVisibleLeafColumns().stream().filter(c -> id.equals(c.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("no column " + id));
    }

    private static List<String> leafIds(TableView<?> t, String prefix) {
        return t.getVisibleLeafColumns().stream().map(TableColumn::getId).filter(id -> id != null && id.startsWith(prefix)).toList();
    }

    private static void waitApply() {
        WaitForAsyncUtils.sleep(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void waitDebounce() {
        waitApply();
    }
}
