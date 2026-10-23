package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.I18n;
import java.lang.reflect.Method;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Quitting with unsaved changes. On macOS, Cmd+Q and Quit in the application menu do not go
 * through File → Quit: JavaFX calls GlassStage.requestClosingAllWindows(), which sends each
 * window a close request. The main window's close-request handler must ask, and a Cancel
 * must keep the application open.
 */
class QuitTest extends MainWindowTest {

    /** What JavaFX does on Cmd+Q (QuantumToolkit's handleQuitAction). */
    private static void systemQuit() throws Exception {
        Method m = Class.forName("com.sun.javafx.tk.quantum.GlassStage").getDeclaredMethod("requestClosingAllWindows");
        m.setAccessible(true);
        Platform.runLater(() -> {
            try {
                m.invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void makeAChange() {
        interact(() -> dictionary().getComponentBuilder().entry().withForm("fr", "quitter").build());
        assertTrue(isDirty());
    }

    private void press(String label) {
        DialogPane pane = dialog();
        Button b = (Button) pane.getButtonTypes().stream()
            .filter(t -> t.getText().equals(label)).findFirst().map(pane::lookupButton).orElseThrow();
        clickOn((Node) b);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void cmdQWithUnsavedChangesAsksAndCancelKeepsTheWindow() throws Exception {
        makeAChange();
        systemQuit();
        assertTrue(dialogOpen(), "the unsaved changes are asked about");
        assertEquals(I18n.get("confirm.unsavedChanges"), dialog().getContentText());
        press(ButtonType.CANCEL.getText());
        assertTrue(stage.isShowing(), "Cancel keeps the application open");
        assertTrue(isDirty());
    }

    @Test
    void cmdQThenDiscardCloses() throws Exception {
        makeAChange();
        systemQuit();
        press(I18n.get("btn.discard"));
        assertFalse(stage.isShowing());
    }

    @Test
    void cmdQWithoutChangesClosesWithoutAsking() throws Exception {
        assertFalse(isDirty());
        systemQuit();
        assertFalse(stage.isShowing());
    }
}
