package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.I18n;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** The application opens on the demo dictionary, in the Entries view. */
class StartupTest extends MainWindowTest {

    @Test
    void opensTheDemoDictionaryInTheEntriesView() {
        assertEquals(I18n.get("nav.entries"), viewTitle());
        assertEquals(2101, table().getItems().size());
        assertTrue(windowTitle().contains(I18n.get("title.demo")), windowTitle());
        assertFalse(windowTitle().endsWith("*"), "a freshly opened dictionary has no unsaved change");
    }

    @Test
    @Disabled("Bug B5 of .claude/plan/testfx-bugs-2026-10.md: numbers follow the system locale, and \"1 rows\"")
    void countsFollowTheInterfaceLanguage() {
        java.util.Locale system = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);
            openView("nav.senses");
            openView("nav.entries");
            assertTrue(countLabel().contains("2,101"), "English interface: " + countLabel());
            assertFalse(I18n.get("table.count.filtered", 1, 5).contains("1 rows"));
        } finally {
            java.util.Locale.setDefault(system);
        }
    }

    @Test
    void theFirstEntryIsShownInTheEditorPanel() {
        assertNotNull(table().getSelectionModel().getSelectedItem());
        assertNotEquals(I18n.get("panel.selectElement"), editorTitle());
    }
}
