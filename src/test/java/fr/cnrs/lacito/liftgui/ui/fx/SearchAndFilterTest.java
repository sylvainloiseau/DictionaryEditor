package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** The global search and the filters in the column headers. */
class SearchAndFilterTest extends MainWindowTest {

    @Test
    void theSearchFiltersTheEntries() {
        TableView<LiftEntry> t = table();
        LiftEntry target = t.getItems().get(10);
        String form = target.getForms().getForms().iterator().next().toPlainText();
        interact(() -> searchField().setText(form));
        assertTrue(t.getItems().size() < 2101);
        assertTrue(t.getItems().contains(target));
        assertTrue(t.getItems().stream().allMatch(e -> DictionaryQueries.searchText(e).toLowerCase().contains(form.toLowerCase())));
        assertTrue(digits(countLabel()).endsWith("2101"), countLabel());
    }

    @Test
    void clearAllShowsAllRowsAgain() {
        interact(() -> searchField().setText("zzzz-nothing-matches"));
        assertEquals(0, table().getItems().size());
        fire(lookup("#resetFilters").queryButton());
        assertEquals(2101, table().getItems().size());
        assertEquals("", searchField().getText());
    }

    @Test
    void anOptionsFilterKeepsTheRowsHavingTheTickedValues() {
        Button morph = from(header("entry.morphType")).lookup(".filter-button").queryButton();
        assertEquals(I18n.get("filter.all") + "  ▾", morph.getText());
        clickOn(morph);
        ListView<?> list = popover().lookup(".filter-options-list").queryAs(ListView.class);
        CheckBox first = from(list).lookup(".check-box").queryAs(CheckBox.class);
        String value = first.getText();
        clickOn(first);
        TableView<LiftEntry> t = table();
        assertFalse(t.getItems().isEmpty());
        assertTrue(t.getItems().size() < 2101);
        assertTrue(t.getItems().stream().allMatch(e -> value.equals(DictionaryQueries.traitValue(e, Keys.MORPH_TYPE))));
        assertTrue(morph.getText().startsWith(value), morph.getText());
        // A chip names the filter; its ✕ removes it.
        Node chip = lookup(".filter-chip").query();
        clickOn(from(chip).lookup(".filter-chip-remove").queryButton());
        assertEquals(2101, t.getItems().size());
        assertEquals(I18n.get("filter.all") + "  ▾", morph.getText());
    }

    @Test
    void aTextFilterKeepsTheRowsContainingTheText() {
        openView("nav.notes");
        TextField filter = lookup("#tableContainer .filter-text").queryAs(TextField.class);
        int all = table().getItems().size();
        clickOn(filter).write("on");
        waitDebounce();
        assertTrue(table().getItems().size() < all);
        interact(filter::clear);
        waitDebounce();
        assertEquals(all, table().getItems().size());
    }

    @Test
    void aRegularExpressionFiltersAndAnInvalidOneIsShown() {
        String id = formColumnId();
        TableView<LiftEntry> table = table();
        TextField filter = from(header(id)).lookup(".filter-text").queryAs(TextField.class);
        ToggleButton regex = from(header(id)).lookup(".filter-toggle").match(n -> ".*".equals(((ToggleButton) n).getText()))
            .queryAs(ToggleButton.class);
        // A narrow column: the toggles are in the menu of the filter.
        interact(() -> leafColumn(table, id).setPrefWidth(140));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(regex.isVisible());
        javafx.scene.control.MenuButton menu = from(header(id)).lookup(".filter-menu").queryAs(javafx.scene.control.MenuButton.class);
        javafx.scene.control.CheckMenuItem regexItem = menu.getItems().stream()
            .filter(i -> i instanceof javafx.scene.control.CheckMenuItem && I18n.get("filter.regex").equals(i.getText()))
            .map(javafx.scene.control.CheckMenuItem.class::cast).findFirst().orElseThrow();
        interact(() -> regexItem.setSelected(true));
        assertTrue(regex.isSelected(), "menu and toggle are in sync");
        // A wide column: the toggles are shown.
        interact(() -> leafColumn(table, id).setPrefWidth(260));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(regex.isVisible());
        interact(() -> filter.setText("^ka"));
        waitDebounce();
        TableView<LiftEntry> t = table();
        assertFalse(t.getItems().isEmpty());
        assertTrue(t.getItems().size() < 2101);
        assertTrue(t.getItems().stream().allMatch(e -> e.getForms().getForms().stream()
            .anyMatch(f -> f.toPlainText().toLowerCase(Locale.ROOT).startsWith("ka"))));
        interact(() -> filter.setText("(oops"));
        waitDebounce();
        assertTrue(filter.getParent().getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("error")));
        assertEquals(2101, t.getItems().size(), "an invalid expression filters nothing");
    }

    @Test
    void aRangeFilterKeepsTheCountsInTheRange() {
        openView("nav.pronunciations");
        TableView<LiftPronunciation> pt = table();
        interact(() -> pt.scrollToColumn(leafColumn(pt, "pronunciation.mediaCount")));
        WaitForAsyncUtils.waitForFxEvents();
        Button media = from(header("pronunciation.mediaCount")).lookup(".filter-button").queryButton();
        clickOn(media);
        TextField min = popover().lookup(".filter-range-field").queryAs(TextField.class);
        clickOn(min).write("1");
        type(javafx.scene.input.KeyCode.ENTER);
        TableView<LiftPronunciation> t = table();
        assertFalse(t.getItems().isEmpty());
        assertTrue(t.getItems().stream().allMatch(p -> p.getMedias().size() >= 1));
        assertTrue(media.getText().startsWith("≥ 1"), media.getText());
    }

    @Test
    void numbersSortAsNumbers() {
        openView("nav.pronunciations");
        TableView<LiftPronunciation> t = table();
        interact(() -> {
            TableColumn<LiftPronunciation, ?> col = leafColumn(t, "pronunciation.mediaCount");
            col.setSortType(TableColumn.SortType.DESCENDING);
            t.getSortOrder().setAll(List.of(col));
        });
        List<Integer> counts = t.getItems().stream().map(p -> p.getMedias().size()).toList();
        for (int i = 1; i < counts.size(); i++) assertTrue(counts.get(i - 1) >= counts.get(i));
    }

    @Test
    void filtersStayInTheirColumnWhenColumnsAreResizedMovedOrScrolled() {
        openView("nav.senses");
        TableView<Object> t = table();
        assertFiltersInsideTheirColumns(t);
        interact(() -> t.getVisibleLeafColumns().get(0).setPrefWidth(400));
        WaitForAsyncUtils.waitForFxEvents();
        assertFiltersInsideTheirColumns(t);
        interact(() -> {
            var first = t.getColumns().remove(0);
            t.getColumns().add(first);
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFiltersInsideTheirColumns(t);
        interact(() -> t.getVisibleLeafColumns().forEach(c -> c.setPrefWidth(500)));
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> t.scrollToColumn(t.getVisibleLeafColumns().get(t.getVisibleLeafColumns().size() - 1)));
        WaitForAsyncUtils.waitForFxEvents();
        assertFiltersInsideTheirColumns(t);
    }

    @Test
    void filtersOfSingleAndLanguageColumnsShareOneBaseline() {
        openView("nav.senses");
        double gramInfo = bottom(from(header("sense.gramInfo")).lookup(".filter-button").query());
        String glossLeaf = table().getVisibleLeafColumns().stream().map(TableColumn::getId)
            .filter(id -> id.startsWith("sense.gloss@")).findFirst().orElseThrow();
        double gloss = bottom(from(header(glossLeaf)).lookup(".filter-text-box").query());
        assertEquals(gramInfo, gloss, 1.5);
    }

    @Test
    void clickingAFilterNeitherSortsNorDragsTheColumn() {
        TableView<?> t = table();
        var before = List.copyOf(t.getColumns());
        clickOn(from(header(formColumnId())).lookup(".filter-text").queryAs(TextField.class));
        clickOn(from(header("entry.morphType")).lookup(".filter-button").queryButton());
        type(javafx.scene.input.KeyCode.ESCAPE);
        assertTrue(t.getSortOrder().isEmpty(), "no sort");
        assertEquals(before, t.getColumns());
    }

    @Test
    void filteringDoesNotMarkTheDictionaryModified() {
        clickOn(searchField()).write("abc");
        TextField filter = lookup("#tableContainer .filter-text").tryQueryAs(TextField.class).orElse(null);
        if (filter != null) clickOn(filter).write("x");
        assertFalse(isDirty());
    }

    /* ─── Helpers ─── */

    /** A form column with text in the first row. */
    private String formColumnId() {
        TableView<LiftEntry> t = table();
        LiftEntry first = t.getItems().get(0);
        return t.getVisibleLeafColumns().stream().map(TableColumn::getId)
            .filter(id -> id.startsWith("entry.form@") && first.getForms().containsLang(id.substring("entry.form@".length())))
            .findFirst().orElseThrow();
    }

    /** The header of a leaf column, by its id. */
    private TableColumnHeader header(String leafId) {
        return lookup("#tableContainer .column-header").match(n -> n instanceof TableColumnHeader h
            && h.getTableColumn() != null && leafId.equals(h.getTableColumn().getId())).queryAs(TableColumnHeader.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> TableColumn<T, ?> leafColumn(TableView<T> t, String id) {
        return t.getVisibleLeafColumns().stream().filter(c -> id.equals(c.getId())).findFirst().orElseThrow();
    }

    private org.testfx.service.query.NodeQuery popover() {
        Window w = Window.getWindows().stream()
            .filter(x -> x.isShowing() && x.getScene() != null && x.getScene().getRoot().lookup(".filter-popover") != null)
            .findFirst().orElseThrow(() -> new AssertionError("no filter popover"));
        return from(w.getScene().getRoot());
    }

    private static double bottom(Node n) {
        return n.localToScreen(n.getBoundsInLocal()).getMaxY();
    }

    private void assertFiltersInsideTheirColumns(TableView<?> t) {
        for (Node n : lookup("#tableContainer .column-header").queryAll()) {
            if (!(n instanceof TableColumnHeader h) || h.getTableColumn() == null
                || !h.getTableColumn().getStyleClass().contains("filter-column")) continue;
            Node filter = from(h).lookup(".filter-header").query();
            Bounds hb = h.localToScreen(h.getBoundsInLocal());
            Bounds fb = filter.localToScreen(filter.getBoundsInLocal());
            String id = h.getTableColumn().getId();
            assertTrue(fb.getMinX() >= hb.getMinX() - 0.5 && fb.getMaxX() <= hb.getMaxX() + 0.5,
                id + ": filter " + fb.getMinX() + "–" + fb.getMaxX() + " outside its header " + hb.getMinX() + "–" + hb.getMaxX());
            assertTrue(fb.getWidth() >= hb.getWidth() - 30, id + ": the filter is as wide as its column");
        }
    }

    private static void waitDebounce() {
        WaitForAsyncUtils.sleep(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
