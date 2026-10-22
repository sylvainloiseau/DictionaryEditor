package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dictionary configuration pages. */
class HeaderConfigurationTest extends MainWindowTest {

    private Button centerButton(String text) {
        return lookup("#tableContainer .button").match((Node n) -> n instanceof Button b && text.equals(b.getText())).queryButton();
    }

    private TextField centerField(String prompt) {
        return lookup("#tableContainer .text-field")
            .match((Node n) -> n instanceof TextField t && prompt.equals(t.getPromptText())).queryAs(TextField.class);
    }

    /**
     * The demo dictionary defines its ranges in a shared file, or not at all: read-only
     * until made internal, or declared.
     */
    private void makeInternal(FeatureSet set) {
        interact(() -> {
            if (set.isExternallyDefined()) dictionary().getHeader().makeFeatureSetInternal(set);
            if (!set.isDeclared()) dictionary().getHeader().declareFeatureSet(set);
        });
    }

    @Test
    void noteTypesCanBeAddedAndUnusedOnesDeleted() {
        FeatureSet types = dictionary().getHeader().getNoteTypeManager();
        makeInternal(types);
        openView("nav.cfgManageNoteTypes");
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
        FeatureSet types = dictionary().getHeader().getNoteTypeManager();
        makeInternal(types);
        openView("nav.cfgManageNoteTypes");
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
        makeInternal(dictionary().getHeader().getFeatureSet(rangeItem.getValue()));
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

    @Test
    void aRangeOfASharedFileIsReadOnlyUntilItIsMadeEditable(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("shared.lift-ranges"), """
            <lift-ranges><range id="dialects"><range-element id="north"/></range></lift-ranges>
            """);
        File file = dir.resolve("test.lift").toFile();
        Files.writeString(file.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <lift version="0.15" producer="test">
            <header><ranges><range id="dialects" href="shared.lift-ranges"/></ranges></header>
            <entry id="e1"><lexical-unit><form lang="xx"><text>kato</text></form></lexical-unit></entry>
            </lift>
            """);
        LiftDictionary d = LiftDictionary.loadDictionaryFromFile(file);
        interact(() -> document().load(d, file, false));
        openView("nav.cfgManageNoteTypes"); // any view, to reach the ranges node
        openNavItem("dialects");
        FeatureSet dialects = d.getHeader().getFeatureSet("dialects");
        assertTrue(centerButton(I18n.get("cfg.addElement")).isDisabled());
        assertTrue(centerButton(I18n.get("cfg.rename")).isDisabled());

        fire(centerButton(I18n.get("cfg.makeInternal")));
        pressDialogButton(ButtonType.OK);
        assertFalse(dialects.isExternallyDefined());
        assertFalse(centerButton(I18n.get("cfg.addElement")).isDisabled());
        clickOn(centerField(I18n.get("cfg.newElementLabel"))).write("south");
        fire(centerButton(I18n.get("cfg.addElement")));
        assertTrue(dialects.hasFeature("south"));
    }

    /**
     * An inline (so editable) note-type range whose values are used in every way lift-api
     * counts: {@code general} is a parent, {@code flagged} the value of a range element's
     * trait, {@code picked} part of a collection, {@code named} the value of a trait named
     * after the range; {@code free} is unused.
     */
    private LiftDictionary loadUsageDictionary(Path dir) throws Exception {
        File file = dir.resolve("usage.lift").toFile();
        Files.writeString(file.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <lift version="0.15" producer="test">
            <header>
            <ranges>
            <range id="note-type">
              <range-element id="general"/>
              <range-element id="sub" parent="general"/>
              <range-element id="flagged"/>
              <range-element id="carrier"><trait name="note-type" value="flagged"/></range-element>
              <range-element id="picked"/>
              <range-element id="named"/>
              <range-element id="free"/>
            </range>
            </ranges>
            <fields>
            <field-definition name="tags" type="option-collection" option-range="note-type"/>
            </fields>
            </header>
            <entry id="e1">
            <lexical-unit><form lang="xx"><text>kato</text></form></lexical-unit>
            <trait name="tags" value="picked, sub"/>
            <trait name="note-type" value="named"/>
            </entry>
            </lift>
            """);
        LiftDictionary d = LiftDictionary.loadDictionaryFromFile(file);
        interact(() -> document().load(d, file, false));
        return d;
    }

    /** The row of the feature {@code id} in the center table (rows are records). */
    private Object featureRow(String id) {
        return table().getItems().stream()
            .filter(r -> r.toString().contains(", id=" + id + ","))
            .findFirst().orElseThrow();
    }

    private static String usageOf(Object row) {
        String s = row.toString();
        return s.substring(s.lastIndexOf("usage=") + "usage=".length(), s.length() - 1);
    }

    private void selectFeatureRow(String id) {
        Object row = featureRow(id);
        interact(() -> ((TableView<Object>) table()).getSelectionModel().select(row));
    }

    @Test
    void theUsesColumnCountsWhatLiftApiCounts(@TempDir Path dir) throws Exception {
        loadUsageDictionary(dir);
        openView("nav.cfgManageNoteTypes");
        assertEquals("1", usageOf(featureRow("general")), "a parent of another value");
        assertEquals("1", usageOf(featureRow("flagged")), "the trait of a range element");
        assertEquals("1", usageOf(featureRow("picked")), "a value of a collection");
        assertEquals("1", usageOf(featureRow("named")), "a trait named after the range");
        assertEquals("0", usageOf(featureRow("free")));
    }

    @Test
    void deletingAValueUsedOnlyAsAParentIsRefusedWithTheNumber(@TempDir Path dir) throws Exception {
        LiftDictionary d = loadUsageDictionary(dir);
        FeatureSet types = d.getHeader().getNoteTypeManager();
        openView("nav.cfgManageNoteTypes");
        selectFeatureRow("general");
        fire(centerButton(I18n.get("btn.delete")));
        assertTrue(dialogOpen(), "an error explains why");
        assertEquals(I18n.get("cfg.featureInUse", 1L), dialog().getContentText());
        pressDialogButton(ButtonType.OK);
        assertTrue(types.hasFeature("general"));
        assertSame(types.getFeature("general"), types.getFeature("sub").superordinateFeatureProperty().get());
        assertFalse(isDirty());
    }

    @Test
    void renamingAValueRenamesItsTraitsAndOneUndoRestoresBoth(@TempDir Path dir) throws Exception {
        LiftDictionary d = loadUsageDictionary(dir);
        FeatureSet types = d.getHeader().getNoteTypeManager();
        LiftTrait trait = d.getLiftDictionaryRegistry().getTraits().stream()
            .filter(t -> "note-type".equals(t.getName()) && "named".equals(t.getValue()))
            .findFirst().orElseThrow();
        openView("nav.cfgManageNoteTypes");
        TableView<Object> t = (TableView<Object>) table();
        TableColumn<Object, String> idCol = (TableColumn<Object, String>) t.getColumns().get(0);
        int index = t.getItems().indexOf(featureRow("named"));
        interact(() -> Event.fireEvent(idCol, new TableColumn.CellEditEvent<>(t,
            new TablePosition<>(t, index, idCol), TableColumn.editCommitEvent(), "renamed")));
        assertTrue(types.hasFeature("renamed"));
        assertEquals("renamed", trait.getValue());

        fire(lookup("#undoButton").queryButton());
        assertTrue(types.hasFeature("named"));
        assertFalse(types.hasFeature("renamed"));
        assertEquals("named", trait.getValue());
    }

    @Test
    void deletingAUsedDefinitionIsRefusedWithTheNumber(@TempDir Path dir) throws Exception {
        LiftDictionary d = loadUsageDictionary(dir);
        LiftFieldAndTraitDefinition tags = d.getHeader().getFieldsAndTraitsDefinitions().stream()
            .filter(fd -> "tags".equals(fd.getName())).findFirst().orElseThrow();
        openView("nav.cfgFieldDefs");
        interact(() -> ((TableView<Object>) table()).getSelectionModel().select(tags));
        fire(centerButton(I18n.get("btn.delete")));
        assertTrue(dialogOpen(), "an error explains why");
        assertEquals(I18n.get("cfg.deleteNotAllowed", 1L), dialog().getContentText());
        pressDialogButton(ButtonType.OK);
        assertTrue(d.getHeader().getFieldsAndTraitsDefinitions().contains(tags));
    }

    @Test
    void anUndeclaredValueIsGreyAndCanBeDeclared(@TempDir Path dir) throws Exception {
        File file = dir.resolve("test.lift").toFile();
        Files.writeString(file.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <lift version="0.15" producer="test">
            <header><ranges><range id="note-type"><range-element id="grammar"/></range></ranges></header>
            <entry id="e1"><lexical-unit><form lang="xx"><text>kato</text></form></lexical-unit>
            <note type="reference"><form lang="en"><text>p. 3</text></form></note></entry>
            </lift>
            """);
        LiftDictionary d = LiftDictionary.loadDictionaryFromFile(file);
        interact(() -> document().load(d, file, false));
        openView("nav.cfgManageNoteTypes");
        Feature reference = d.getHeader().getNoteTypeManager().getFeature("reference");
        assertFalse(reference.isDeclared());
        TableView<?> t = lookup("#tableContainer .table-view").queryAs(TableView.class);
        assertTrue(lookup(".table-row-cell.undeclared").tryQuery().isPresent(), "the undeclared value is in grey");
        Button declare = centerButton(I18n.get("cfg.declareValue"));
        assertTrue(declare.isDisabled());
        interact(() -> t.getItems().stream()
            .filter(r -> r.toString().contains("id=reference,"))
            .findFirst().ifPresent(r -> ((TableView<Object>) (TableView<?>) t).getSelectionModel().select(r)));
        assertFalse(declare.isDisabled());
        fire(declare);
        assertTrue(reference.isDeclared());
        assertFalse(lookup(".table-row-cell.undeclared").tryQuery().isPresent());
    }

    @Test
    void anUndeclaredRangeCanBeDeclared(@TempDir Path dir) throws Exception {
        File file = dir.resolve("test.lift").toFile();
        Files.writeString(file.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <lift version="0.15" producer="test">
            <entry id="e1"><lexical-unit><form lang="xx"><text>kato</text></form></lexical-unit>
            <note type="reference"><form lang="en"><text>p. 3</text></form></note></entry>
            </lift>
            """);
        LiftDictionary d = LiftDictionary.loadDictionaryFromFile(file);
        interact(() -> document().load(d, file, false));
        openView("nav.cfgManageNoteTypes");
        FeatureSet types = d.getHeader().getNoteTypeManager();
        assertFalse(types.isDeclared());
        assertTrue(centerButton(I18n.get("btn.add")).isDisabled());

        fire(centerButton(I18n.get("cfg.declareRange")));
        assertTrue(types.isDeclared());
        assertFalse(types.getFeature("reference").isDeclared(), "its values stay undeclared");
        assertFalse(centerButton(I18n.get("btn.add")).isDisabled());
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
