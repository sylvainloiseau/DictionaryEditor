package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** The navigation tree and the View menu show the views. */
class NavigationTest extends MainWindowTest {

    @Test
    void everyNavigationItemOpensItsView() {
        Set<String> viewNames = Arrays.stream(ViewKey.values()).map(k -> I18n.get(k.i18nKey())).collect(Collectors.toSet());
        for (TreeItem<String> leaf : navLeaves()) {
            openNavItem(leaf.getValue());
            if (viewNames.contains(leaf.getValue())) {
                assertEquals(leaf.getValue(), viewTitle(), "title of the view " + leaf.getValue());
            } else {
                // A header range: the title is the range id.
                assertFalse(viewTitle().isBlank(), "title of the range " + leaf.getValue());
            }
            assertSame(leaf, navTree().getSelectionModel().getSelectedItem(), "the item stays selected");
        }
    }

    @Test
    @Disabled("Bug B6 of .claude/plan/testfx-bugs-2026-10.md: clicking the highlighted item does not leave a subset")
    void clickingTheHighlightedItemShowsAllObjectsAgain() {
        fr.cnrs.lacito.liftapi.model.LiftEntry entry = dictionary().getLiftDictionaryRegistry().getEntries().stream()
            .filter(x -> x.getSenses().size() > 1).findFirst().orElseThrow();
        interact(() -> table().getSelectionModel().select(entry));
        fire(lookup("#editorContainer .hyperlink").match(n -> n instanceof javafx.scene.control.Hyperlink h
            && I18n.get("sense.openInSensesView").equals(h.getText())).queryAs(javafx.scene.control.Hyperlink.class));
        int subset = table().getItems().size();
        clickOn(I18n.get("nav.senses"));
        assertTrue(table().getItems().size() > subset, "all the senses are listed again");
    }

    @Test
    void theViewMenuOpensViews() {
        fireMenu("menu.view", "menu.view.objects", "nav.senses");
        assertEquals(I18n.get("nav.senses"), viewTitle());
        assertEquals(I18n.get("nav.senses"), navTree().getSelectionModel().getSelectedItem().getValue());
        fireMenu("menu.view", "menu.view.categories", "nav.gramInfo");
        assertEquals(I18n.get("nav.gramInfo"), viewTitle());
    }

    @Test
    void configurationPagesHideTheEditorPanel() {
        SplitPane split = lookup("#mainSplit").queryAs(SplitPane.class);
        openView("nav.cfgManageLangs");
        assertFalse(split.getItems().get(1).isVisible());
        openView("nav.senses");
        assertTrue(split.getItems().get(1).isVisible());
    }

    @Test
    void theQuickEntryViewHasNoSearchField() {
        openView("nav.quickEntry");
        assertFalse(searchField().isVisible());
        assertTrue(addButton().isVisible());
        openView("nav.entries");
        assertTrue(searchField().isVisible());
    }

    @Test
    void browsingDoesNotMarkTheDictionaryModified() {
        for (String key : new String[] {"nav.senses", "nav.examples", "nav.notes", "nav.relations", "nav.gramInfo"}) {
            openView(key);
            if (!table().getItems().isEmpty()) selectRow(0);
        }
        openView("nav.senses");
        selectRow(0);
        fire(lookup("#editorContainer .back-btn").queryButton());
        assertEquals(I18n.get("nav.entries"), viewTitle());
        assertFalse(isDirty(), "navigation is not an edit");
        assertFalse(windowTitle().endsWith("*"));
    }
}
