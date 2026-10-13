package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

/** The global search and the column filters of the tables. */
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
    void resetFiltersShowsAllRowsAgain() {
        interact(() -> searchField().setText("zzzz-nothing-matches"));
        assertEquals(0, table().getItems().size());
        fire(lookup(".button").match(n -> n instanceof javafx.scene.control.Button b
            && I18n.get("filter.resetAll").equals(b.getText())).queryButton());
        assertEquals(2101, table().getItems().size());
        assertEquals("", searchField().getText());
    }

    @Test
    void aFacetKeepsTheRowsHavingTheChosenValue() {
        // Morph-type column: the filter after the form columns.
        @SuppressWarnings("unchecked")
        ComboBox<String> morph = (ComboBox<String>) lookup("#tableContainer .filter-row .combo-box").queryAll().stream()
            .skip(table().getVisibleLeafColumns().size() - 2)
            .findFirst().orElseThrow();
        assertTrue(morph.getItems().size() > 1, "the facet lists the values");
        String value = morph.getItems().get(1);
        interact(() -> morph.getSelectionModel().select(1));
        TableView<LiftEntry> t = table();
        assertFalse(t.getItems().isEmpty());
        assertTrue(t.getItems().stream().allMatch(e -> value.equals(DictionaryQueries.traitValue(e, Keys.MORPH_TYPE))));
        interact(() -> morph.getSelectionModel().select(0));
        assertEquals(2101, t.getItems().size(), "the first item clears the facet");
    }

    @Test
    void aTextFilterKeepsTheRowsContainingTheText() {
        openView("nav.notes");
        // The text columns of the notes have free-text filters.
        TextField filter = lookup("#tableContainer .filter-row .text-field").queryAs(TextField.class);
        int all = table().getItems().size();
        interact(() -> filter.setText("e"));
        assertTrue(table().getItems().size() <= all);
        interact(filter::clear);
        assertEquals(all, table().getItems().size());
    }

    @Test
    void filteringDoesNotMarkTheDictionaryModified() {
        clickOn(searchField()).write("abc");
        TextField filter = lookup("#tableContainer .filter-row .text-field").tryQueryAs(TextField.class).orElse(null);
        if (filter != null) clickOn(filter).write("x");
        assertFalse(isDirty());
    }
}
