package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.AppPreferences;
import java.io.File;
import java.io.IOException;
import javafx.scene.control.DialogPane;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/** The last file, reopened at startup, cannot be read any more. */
class OpenErrorAtStartupTest extends MainWindowTest {

    private File lastFile;

    @Override
    public void init() {
        super.init();
        try {
            lastFile = OpenErrorTest.broken();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        new AppPreferences().node().put(OpenErrorTest.RECENT, lastFile.getAbsolutePath());
    }

    @Test
    void theErrorSaysItIsTheLastDictionaryAndOffersToForgetIt() {
        DialogPane pane = dialog();
        assertEquals(I18n.get("open.error.startup") + "\n" + I18n.get("open.error.malformed.header", lastFile.getName()),
            pane.getHeaderText());
        clickOn(OpenErrorTest.button(this, pane, I18n.get("open.error.removeRecent")).orElseThrow());
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(new AppPreferences().node().get(OpenErrorTest.RECENT, null));
        assertNull(dictionary(), "no dictionary is shown");
    }
}
