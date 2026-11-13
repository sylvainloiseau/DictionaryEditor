package fr.cnrs.lacito.liftgui.ui.fx;

import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.AppPreferences;
import fr.cnrs.lacito.liftgui.ui.nav.NavNode;
import java.text.Collator;
import java.util.List;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

/** The navigation panel: its items, the sorted ranges, the counts, the filter, folding, hiding. */
class NavigationPanelTest extends MainWindowTest {

    private TreeItem<NavNode> topLevel(String i18nKey) {
        return navTree().getRoot().getChildren().stream()
            .filter(i -> I18n.get(i18nKey).equals(i.getValue().label())).findFirst().orElseThrow();
    }

    private List<TreeItem<NavNode>> rangeItems() {
        TreeItem<NavNode> config = topLevel("nav.headerConfig");
        return config.getChildren().getLast().getChildren();
    }

    @SuppressWarnings("unchecked")
    private TreeCell<NavNode> cell(String label) {
        WaitForAsyncUtils.waitForFxEvents();
        return lookup(".nav-cell").queryAll().stream().map(n -> (TreeCell<NavNode>) n)
            .filter(c -> c.getItem() != null && label.equals(c.getItem().label()))
            .findFirst().orElseThrow(() -> new AssertionError("no cell " + label));
    }

    private int count(String i18nKey) {
        return Integer.parseInt(digits(from(cell(I18n.get(i18nKey))).lookup(".nav-count").queryAs(Label.class).getText()));
    }

    private String selectedLabel() {
        return navTree().getSelectionModel().getSelectedItem().getValue().label();
    }

    private TextField filter() {
        return lookup("#navFilter").queryAs(TextField.class);
    }

    private void key(Node target, KeyCode code) {
        interact(() -> Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void theItemsKeepTheirGroupsAndOrder() {
        assertEquals(List.of(I18n.get("nav.objects"), I18n.get("nav.languages"), I18n.get("nav.categories"),
                I18n.get("nav.headerConfig"), I18n.get("nav.quickEntry")),
            navTree().getRoot().getChildren().stream().map(i -> i.getValue().label()).toList());
        assertEquals(List.of("nav.cfgDesc", "nav.cfgFieldDefs", "nav.cfgManageLangs", "nav.cfgManageNoteTypes",
                "nav.cfgManageTransTypes", "nav.cfgManageAnnotationTypes", "nav.cfgManageRelationTypes", "nav.cfgRanges")
                .stream().map(I18n::get).toList(),
            topLevel("nav.headerConfig").getChildren().stream().map(i -> i.getValue().label()).toList());
    }

    @Test
    void theRangesAreSortedAToZAndResortedWhenRenamed() {
        Collator collator = Collator.getInstance(I18n.getLocale());
        collator.setStrength(Collator.PRIMARY);
        List<String> labels = rangeItems().stream().map(i -> i.getValue().label()).toList();
        assertTrue(labels.size() > 5);
        assertEquals(labels.stream().sorted(collator).toList(), labels, "A→Z, case and accents aside");

        FeatureSet last = ((NavNode.Range) rangeItems().getLast().getValue()).range();
        interact(() -> {
            if (last.isExternallyDefined()) dictionary().getHeader().makeFeatureSetInternal(last);
            if (last.getLabel().containsLang("en")) last.getLabel().removeForm("en");
            last.getLabel().add(new Form("en", "Aaa first"));
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Aaa first", rangeItems().getFirst().getValue().label());
    }

    @Test
    void theCountsFollowTheDictionaryAndUndo() {
        assertEquals(dictionary().getLiftDictionaryRegistry().getEntries().size(), count("nav.entries"));
        int senses = count("nav.senses");
        assertEquals(dictionary().getLiftDictionaryRegistry().getSenses().size(), senses);

        LiftEntry entry = dictionary().getLiftDictionaryRegistry().getEntries().getFirst();
        interact(() -> table().getSelectionModel().select(entry));
        WaitForAsyncUtils.waitForFxEvents();
        add(entry, ComponentKind.SENSE);
        assertEquals(senses + 1, count("nav.senses"));
        fireMenuItem("undoMenuItem");
        assertEquals(senses, count("nav.senses"));
    }

    @Test
    void aHeadingIsSkippedByTheArrows() {
        openView("nav.objectLangs");
        interact(() -> navTree().requestFocus());
        key(navTree(), KeyCode.UP);
        assertEquals(I18n.get("nav.annotations"), selectedLabel(), "past the Languages heading");
        assertEquals(I18n.get("nav.annotations"), viewTitle());
    }

    @Test
    void theFilterKeepsTheMatchesAndEnterOpensTheFirst() {
        interact(() -> filter().setText("relation"));
        WaitForAsyncUtils.waitForFxEvents();
        List<TreeItem<NavNode>> shown = navLeaves();
        assertFalse(shown.isEmpty());
        assertTrue(shown.stream().allMatch(i -> i.getValue().label().toLowerCase().contains("relation")),
            shown.stream().map(i -> i.getValue().label()).toList().toString());
        assertTrue(navTree().getRoot().getChildren().stream().noneMatch(i -> i.getValue().label().equals(I18n.get("nav.languages"))));

        key(filter(), KeyCode.ENTER);
        assertEquals(I18n.get("nav.relations"), viewTitle());

        interact(() -> filter().requestFocus());
        key(filter(), KeyCode.ESCAPE);
        assertEquals("", filter().getText());
        assertEquals(5, navTree().getRoot().getChildren().size(), "everything is back");
    }

    @Test
    void theFilterIgnoresAccentsAndCase() {
        interact(() -> filter().setText("ÉNTRIES"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(navLeaves().stream().anyMatch(i -> i.getValue().label().equals(I18n.get("nav.entries"))));
    }

    @Test
    void aClickOnAHeadingFoldsItAndIsRemembered() {
        String previous = selectedLabel();
        clickOn(I18n.get("nav.languages").toUpperCase(I18n.getLocale()));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(topLevel("nav.languages").isExpanded());
        assertTrue(new AppPreferences().navFolded().contains("languages"));
        assertEquals(previous, selectedLabel(), "a heading is not selected");
        clickOn(I18n.get("nav.languages").toUpperCase(I18n.getLocale()));
        assertTrue(topLevel("nav.languages").isExpanded());
        assertFalse(new AppPreferences().navFolded().contains("languages"));
    }

    @Test
    void theWidthIsRemembered() {
        SplitPane split = lookup("#navSplit").queryAs(SplitPane.class);
        VBox panel = lookup("#navPanel").queryAs(VBox.class);
        interact(() -> split.setDividerPosition(0, 0.3));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(panel.getWidth(), new AppPreferences().navWidth(), 2);
    }

    @Test
    void shortcutZeroHidesAndShowsThePanel() {
        SplitPane split = lookup("#navSplit").queryAs(SplitPane.class);
        VBox panel = lookup("#navPanel").queryAs(VBox.class);
        pressShortcut(KeyCode.DIGIT0);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(split.getItems().contains(panel));
        assertFalse(new AppPreferences().navShown());
        pressShortcut(KeyCode.DIGIT0);
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(panel, split.getItems().getFirst());
        assertTrue(new AppPreferences().navShown());
    }

    @Test
    void shortcutKFocusesTheFilterAndShowsThePanel() {
        pressShortcut(KeyCode.DIGIT0);
        pressShortcut(KeyCode.K);
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(filter(), stage.getScene().getFocusOwner());
        assertTrue(lookup("#navSplit").queryAs(SplitPane.class).getItems().contains(lookup("#navPanel").query()));
    }

    @Test
    void theContextMenusFitTheItems() {
        assertEquals(I18n.get("nav.newEntry"), cell(I18n.get("nav.entries")).getContextMenu().getItems().getFirst().getText());
        assertNull(cell(I18n.get("nav.senses")).getContextMenu(), "no creation where it would not create senses");
        assertEquals(I18n.get("nav.foldAll"), cell(I18n.get("nav.objects")).getContextMenu().getItems().getFirst().getText());
        interact(() -> filter().setText(rangeItems().getFirst().getValue().label()));
        TreeItem<NavNode> range = navLeaves().stream().filter(i -> i.getValue() instanceof NavNode.Range).findFirst().orElseThrow();
        assertEquals(I18n.get("nav.copyId"), cell(range.getValue().label()).getContextMenu().getItems().getFirst().getText());
    }
}
