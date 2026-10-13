package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.scene.control.Hyperlink;
import org.junit.jupiter.api.Test;

/** The links and buttons of the editor panel leading to other views. */
class CrossNavigationTest extends MainWindowTest {

    @Test
    void backToEntryFromASense() {
        openView("nav.senses");
        selectRow(0);
        LiftSense sense = (LiftSense) table().getSelectionModel().getSelectedItem();
        fire(lookup("#editorContainer .back-btn").queryButton());
        assertEquals(I18n.get("nav.entries"), viewTitle());
        assertSame(sense.getParentEntry(), table().getSelectionModel().getSelectedItem());
    }

    @Test
    void backToSenseFromAnExample() {
        openView("nav.examples");
        selectRow(0);
        LiftExample ex = (LiftExample) table().getSelectionModel().getSelectedItem();
        fire(lookup("#editorContainer .back-btn").queryButton());
        assertEquals(I18n.get("nav.senses"), viewTitle());
        assertSame(ex.getParent(), table().getSelectionModel().getSelectedItem());
    }

    @Test
    void openInSensesViewListsTheSensesOfTheEntry() {
        LiftEntry entry = dictionary().getLiftDictionaryRegistry().getEntries().stream()
            .filter(e -> e.getSenses().size() > 1).findFirst().orElseThrow();
        interact(() -> table().getSelectionModel().select(entry));
        Hyperlink link = lookup("#editorContainer .hyperlink")
            .match(n -> n instanceof Hyperlink h && I18n.get("sense.openInSensesView").equals(h.getText()))
            .queryAs(Hyperlink.class);
        fire(link);
        assertEquals(I18n.get("nav.senses"), viewTitle());
        assertTrue(table().getItems().stream().allMatch(s -> ((LiftSense) s).getParentEntry() == entry));
        assertTrue(table().getItems().size() >= entry.getSenses().size());
        assertSame(entry.getSenses().get(0), table().getSelectionModel().getSelectedItem());
    }

    @Test
    void anExampleLinkOfTheSenseEditorOpensTheExample() {
        LiftSense sense = dictionary().getLiftDictionaryRegistry().getSenses().stream()
            .filter(s -> !s.getExamples().isEmpty()).findFirst().orElseThrow();
        openView("nav.senses");
        interact(() -> table().getSelectionModel().select(sense));
        fire(lookup("#editorContainer .hyperlink")
            .match(n -> n instanceof Hyperlink h && I18n.get("sense.exampleN", 1).equals(h.getText()))
            .queryAs(Hyperlink.class));
        assertEquals(I18n.get("nav.examples"), viewTitle());
        assertSame(sense.getExamples().get(0), table().getSelectionModel().getSelectedItem());
    }

    @Test
    void goToParentFromAField() {
        openView("nav.fields");
        selectRow(0);
        LiftField field = (LiftField) table().getSelectionModel().getSelectedItem();
        fire(editorButton(I18n.get("btn.goToParent")));
        assertEquals(I18n.get("nav.entries"), viewTitle());
        assertSame(DictionaryQueries.owningEntry(field), table().getSelectionModel().getSelectedItem());
    }

    @Test
    void aGrammaticalInfoOpensItsSenses() {
        openView("nav.gramInfo");
        selectRow(0);
        Object row = table().getSelectionModel().getSelectedItem();
        fire(editorButton(I18n.get("btn.accessObjects")));
        assertTrue(viewTitle().startsWith(I18n.get("nav.gramInfo")), viewTitle());
        assertFalse(table().getItems().isEmpty());
        String value = ((LiftSense) table().getItems().get(0)).getGrammaticalInfo().orElseThrow().getGramInfoValue().getId();
        assertTrue(table().getItems().stream().allMatch(s ->
            ((LiftSense) s).getGrammaticalInfo().map(g -> g.getGramInfoValue().getId().equals(value)).orElse(false)), row.toString());
    }

    @Test
    void aTraitOpensTheEntriesCarryingIt() {
        openView("nav.traits");
        selectRow(0);
        fire(editorButton(I18n.get("btn.accessObjects")));
        assertTrue(viewTitle().startsWith(I18n.get("nav.traits")), viewTitle());
        assertFalse(table().getItems().isEmpty());
        assertTrue(table().getItems().stream().allMatch(e -> e instanceof LiftEntry));
    }

    @Test
    void aMultiTextRowLinksToItsEntry() {
        openView("nav.objectLangs");
        selectRow(0);
        fire(lookup("#editorContainer .hyperlink").queryButton());
        assertEquals(I18n.get("nav.entries"), viewTitle());
        assertNotNull(table().getSelectionModel().getSelectedItem());
    }
}
