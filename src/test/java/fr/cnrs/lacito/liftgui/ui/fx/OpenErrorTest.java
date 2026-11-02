package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.AppPreferences;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** The error shown when a file cannot be opened from the File menu. */
class OpenErrorTest extends MainWindowTest {

    static final String RECENT = "recent.file.0";

    /** A LIFT file whose XML breaks on line 3. */
    static File broken() throws IOException {
        return temp(".lift", "<?xml version=\"1.0\"?>\n<lift producer=\"test\" version=\"0.13\">\n  <entry id=\"e1\"><lexical-unit></entry>\n</lift>\n");
    }

    static File temp(String suffix, String content) throws IOException {
        Path p = Files.createTempFile("open-error-", suffix);
        p.toFile().deleteOnExit();
        Files.writeString(p, content);
        return p.toFile();
    }

    static String text(MainWindowTest test, DialogPane pane, String styleClass) {
        return test.from(pane).lookup("." + styleClass).queryAs(Label.class).getText();
    }

    static java.util.Optional<Button> button(MainWindowTest test, DialogPane pane, String text) {
        return test.from(pane).lookup(".button").queryAllAs(Button.class).stream()
            .filter(b -> text.equals(b.getText())).findFirst();
    }

    /** File ▸ Open recent on a file, as the menu does (the dialog then opens). */
    private void openRecent(File f) throws ReflectiveOperationException {
        Field field = controller.getClass().getDeclaredField("fileActions");
        field.setAccessible(true);
        Object actions = field.get(controller);
        java.lang.reflect.Method open = actions.getClass().getMethod("openRecent", File.class);
        Platform.runLater(() -> {
            try {
                open.invoke(actions, f);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void aBrokenRecentFileIsExplainedAndCanBeRemoved() throws Exception {
        LiftDictionary before = dictionary();
        File f = broken();
        new AppPreferences().node().put(RECENT, f.getAbsolutePath());
        openRecent(f);

        DialogPane pane = dialog();
        assertEquals(I18n.get("open.error.malformed.header", f.getName()), pane.getHeaderText());
        assertTrue(text(this, pane, "open-error-position").startsWith("Line 3, column "), text(this, pane, "open-error-position"));
        assertTrue(text(this, pane, "open-error-excerpt").startsWith("  <entry id=\"e1\"><lexical-unit></entry>"));
        assertNotNull(pane.getExpandableContent(), "the technical details");

        clickOn(button(this, pane, I18n.get("open.error.removeRecent")).orElseThrow());
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(dialogOpen());
        assertNull(new AppPreferences().node().get(RECENT, null), "removed from the recent files");
        assertSame(before, dictionary(), "the dictionary shown before stays");
    }

    @Test
    void aFileThatIsNotLift() throws Exception {
        File f = temp(".xml", "<?xml version=\"1.0\"?><html><body/></html>");
        openRecent(f);

        DialogPane pane = dialog();
        assertEquals(I18n.get("open.error.notLift.header", f.getName()), pane.getHeaderText());
        assertEquals(I18n.get("open.error.notLift", "html"), text(this, pane, "open-error-message"));
        assertTrue(button(this, pane, I18n.get("open.error.removeRecent")).isEmpty(), "not a recent file");
        pressDialogButton(ButtonType.OK);
    }
}
