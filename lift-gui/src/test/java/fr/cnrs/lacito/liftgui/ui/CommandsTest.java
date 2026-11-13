package fr.cnrs.lacito.liftgui.ui;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.app.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;

/** The default shortcuts: unique, and none taken by the text fields or the system. */
class CommandsTest {

    private static final java.util.Set<String> FIXED = java.util.Set.of("undoMenuItem", "redoMenuItem", "edit.copy", "edit.paste", "edit.cut");

    @Test
    void everyDefaultIsUnique() {
        List<Map.Entry<String, KeyCombination>> all = new ArrayList<>(Commands.allDefaults().entrySet());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                assertFalse(Commands.sameKeys(all.get(i).getValue(), all.get(j).getValue()),
                    all.get(i).getKey() + " and " + all.get(j).getKey());
            }
        }
    }

    @Test
    void noDefaultIsRefusedExceptTheFixedOnes() {
        Commands.allDefaults().forEach((id, keys) -> {
            if (!FIXED.contains(id)) {
                assertFalse(Commands.isRefused(keys), id);
                assertTrue(Commands.hasModifier(keys), id);
            }
        });
    }

    @Test
    void theKeysOfTextEditingAreRefused() {
        assertTrue(Commands.isRefused(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN)));
        assertTrue(Commands.isRefused(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)));
        assertFalse(Commands.isRefused(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN)));
        assertFalse(Commands.hasModifier(new KeyCodeCombination(KeyCode.K)));
        assertTrue(Commands.hasModifier(new KeyCodeCombination(KeyCode.F5)));
    }

    @Test
    void theOrderOfTheModifiersDoesNotMatter() {
        assertTrue(Commands.sameKeys(KeyCombination.valueOf("Shift+Shortcut+N"), KeyCombination.valueOf("Shortcut+Shift+N")));
        assertFalse(Commands.sameKeys(KeyCombination.valueOf("Shortcut+N"), KeyCombination.valueOf("Shortcut+Shift+N")));
    }
}
