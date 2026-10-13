package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.Test;

/** The dictionary configuration pages. */
class HeaderConfigurationTest extends MainWindowTest {

    private Button centerButton(String text) {
        return lookup("#tableContainer .button").match((Node n) -> n instanceof Button b && text.equals(b.getText())).queryButton();
    }

    private TextField centerField(String prompt) {
        return lookup("#tableContainer .text-field")
            .match((Node n) -> n instanceof TextField t && prompt.equals(t.getPromptText())).queryAs(TextField.class);
    }

    @Test
    void noteTypesCanBeAddedAndUnusedOnesDeleted() {
        openView("nav.cfgManageNoteTypes");
        FeatureSet types = dictionary().getHeader().getNoteTypeManager();
        clickOn(centerField(I18n.get("config.addAbbr"))).write("new-type");
        fire(centerButton(I18n.get("btn.add")));
        assertTrue(types.hasFeature("new-type"));
        assertTrue(isDirty());

        TableView<?> t = lookup("#tableContainer .table-view").queryAs(TableView.class);
        interact(() -> t.getItems().stream()
            .filter(r -> r.toString().contains("id=new-type"))
            .findFirst().ifPresent(r -> ((TableView<Object>) (TableView<?>) t).getSelectionModel().select(r)));
        fire(centerButton(I18n.get("btn.delete")));
        assertFalse(types.hasFeature("new-type"));
    }

    @Test
    void aUsedNoteTypeIsNotDeleted() {
        openView("nav.cfgManageNoteTypes");
        FeatureSet types = dictionary().getHeader().getNoteTypeManager();
        String used = types.getFeatures().keySet().stream()
            .filter(id -> dictionary().getLiftDictionaryRegistry().getNotes().stream()
                .anyMatch(n -> n.getType() != null && id.equals(n.getType().getId())))
            .findFirst().orElseThrow();
        TableView<?> t = lookup("#tableContainer .table-view").queryAs(TableView.class);
        interact(() -> t.getItems().stream()
            .filter(r -> r.toString().contains("id=" + used + ","))
            .findFirst().ifPresent(r -> ((TableView<Object>) (TableView<?>) t).getSelectionModel().select(r)));
        fire(centerButton(I18n.get("btn.delete")));
        assertTrue(dialogOpen(), "an error explains why");
        pressDialogButton(ButtonType.OK);
        assertTrue(types.hasFeature(used));
    }

    @Test
    void fieldDefinitionsCanBeAddedAndDeleted() {
        openView("nav.cfgFieldDefs");
        int rows = table().getItems().size();
        fire(centerButton(I18n.get("cfg.newFieldOrTrait")));
        DialogPane pane = dialog();
        TextField name = from(pane).lookup(".text-field")
            .match((Node n) -> n instanceof TextField t && I18n.get("cfg.fieldDefName").equals(t.getPromptText()))
            .queryAs(TextField.class);
        interact(() -> name.setText("my-field"));
        pressDialogButton(ButtonType.OK);
        java.util.Collection<LiftFieldAndTraitDefinition> defs = dictionary().getHeader().getFieldsAndTraitsDefinitions();
        LiftFieldAndTraitDefinition created = defs.stream().filter(d -> "my-field".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(rows + 1, table().getItems().size());
        assertSame(created, table().getSelectionModel().getSelectedItem());

        fire(centerButton(I18n.get("btn.delete")));
        assertFalse(dictionary().getHeader().getFieldsAndTraitsDefinitions().contains(created));
        assertEquals(rows, table().getItems().size());
    }

    @Test
    void aRangeElementCanBeAdded() {
        openView("nav.cfgManageNoteTypes"); // any view, to reach the ranges node
        TreeItem<String> rangeItem = navLeaves().stream()
            .filter(i -> i.getParent() != null && I18n.get("nav.cfgRanges").equals(i.getParent().getValue()))
            .findFirst().orElseThrow();
        openNavItem(rangeItem.getValue());
        String rangeId = viewTitle();
        FeatureSet range = dictionary().getHeader().getFeatureSet(rangeId);
        int before = range.getFeatures().size();
        clickOn(centerField(I18n.get("cfg.newElementLabel"))).write("new-element");
        fire(centerButton(I18n.get("cfg.addElement")));
        assertEquals(before + 1, range.getFeatures().size());
        TreeView<?> tree = lookup("#tableContainer .tree-view").queryAs(TreeView.class);
        assertEquals(before + 1, countItems(tree.getRoot()), "the tree shows the new element");
    }

    private static int countItems(TreeItem<?> item) {
        int n = 0;
        for (TreeItem<?> c : item.getChildren()) n += 1 + countItems(c);
        return n;
    }

    @Test
    void aMetaLanguageCanBeAddedAndRemoved() {
        openView("nav.cfgManageLangs");
        List<TextField> addFields = lookup("#tableContainer .text-field").queryAllAs(TextField.class).stream().toList();
        // Second panel: meta languages.
        clickOn(addFields.get(1)).write("zz");
        List<Button> addButtons = lookup("#tableContainer .button")
            .match((Node n) -> n instanceof Button b && I18n.get("btn.add").equals(b.getText())).queryAllAs(Button.class)
            .stream().toList();
        fire(addButtons.get(1));
        assertTrue(dictionary().getMetaLanguageManager().hasLanguage("zz"));
        assertTrue(isDirty());
    }
}
