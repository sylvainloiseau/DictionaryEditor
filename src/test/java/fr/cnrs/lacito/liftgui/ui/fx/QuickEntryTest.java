package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import java.util.Comparator;
import java.util.List;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

/** Quick entry: each filled row becomes an entry with a sense. */
class QuickEntryTest extends MainWindowTest {

    /** The text fields of the rows, row by row, left to right. */
    private List<TextField> rowFields() {
        return lookup("#tableContainer .table-row-cell .text-field").queryAllAs(TextField.class).stream()
            .filter(TextField::isVisible)
            .sorted(Comparator.comparingDouble((TextField t) -> t.localToScene(0, 0).getY())
                .thenComparingDouble(t -> t.localToScene(0, 0).getX()))
            .toList();
    }

    private int fieldsPerRow() {
        return dictionary().getObjectLanguageManager().getLanguages().size()
            + dictionary().getMetaLanguageManager().getLanguages().size();
    }

    private int entryCount() {
        return dictionary().getLiftDictionaryRegistry().getEntries().size();
    }

    private LiftEntry lastEntry() {
        return dictionary().getLiftDictionaryRegistry().getEntries().getLast();
    }

    @Test
    void theCreateButtonCreatesTheFilledRows() {
        openView("nav.quickEntry");
        int objLangs = dictionary().getObjectLanguageManager().getLanguages().size();
        List<TextField> fields = rowFields();
        clickOn(fields.get(0)).write("kwik");
        clickOn(fields.get(objLangs)).write("quick");
        fire(addButton());
        if (dialogOpen()) pressDialogButton(ButtonType.OK);
        assertEquals(2102, entryCount());
        LiftEntry e = lastEntry();
        assertEquals("kwik", e.getForms().getForms().iterator().next().toPlainText());
        assertEquals(1, e.getSenses().size());
        assertEquals("quick", e.getSenses().get(0).getGlosses().getForms().iterator().next().toPlainText());
        assertTrue(isDirty());
    }

    @Test
    void aRowWithoutGlossCreatesAnEntryWithoutSense() {
        openView("nav.quickEntry");
        clickOn(rowFields().get(0)).write("solo");
        fire(addButton());
        if (dialogOpen()) pressDialogButton(ButtonType.OK);
        assertEquals(2102, entryCount());
        assertTrue(lastEntry().getSenses().isEmpty());
    }

    @Test
    void leavingAFilledRowCreatesItsEntryOnce() {
        openView("nav.quickEntry");
        clickOn(rowFields().get(0)).write("auto");
        clickOn(rowFields().get(fieldsPerRow()));
        assertEquals(2102, entryCount(), "the entry is created when the user moves to the next row");
        fire(addButton());
        if (dialogOpen()) pressDialogButton(ButtonType.OK);
        assertEquals(2102, entryCount(), "the row is not created twice");
    }
}
