package fr.cnrs.lacito.liftgui.doc;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftgui.ui.app.AppPreferences;
import fr.cnrs.lacito.liftgui.ui.fx.MainWindowTest;
import fr.cnrs.lacito.liftgui.ui.nav.NavNode;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Takes the screenshots of the documentation ({@code Doc/img}): the application opens the
 * dictionary {@code 20260302.lift}, shows a view, selects a row, and the window is saved as
 * a PNG file.
 *
 * Run by {@code mvn -Pscreenshots verify} (see the {@code screenshots} profile of the pom):
 * the images are written to {@code target/screenshots}, then copied to {@code Doc/img}. The
 * window is rendered headless, in English, at a fixed size, so the images do not depend on
 * the machine.
 *
 * To add a screenshot: a method showing a view, then {@link #save(String)}.
 */
class DocumentationScreenshotsIT extends MainWindowTest {

    /** Where the images go; set by the pom. */
    private static final Path OUTPUT = Path.of(System.getProperty("screenshots.dir", "target/screenshots"));
    /** The dictionary shown; set by the pom. */
    private static final File DICTIONARY = new File(System.getProperty("screenshots.dictionary",
        "src/main/resources/lift/20260302.lift"));
    /** The pixel density of the images: 2 for screens of high density. */
    private static final double SCALE = Double.parseDouble(System.getProperty("screenshots.scale", "2"));

    /** The dictionary is opened as the last file used, as the application does at startup. */
    @Override
    public void init() {
        super.init();
        new AppPreferences().node().put("recent.file.0", DICTIONARY.getAbsolutePath());
    }

    @Test
    void entries() throws IOException {
        openView("nav.entries");
        selectRow(0);
        interact(() -> table().requestFocus());
        save("entries.png");
    }

    @Test
    void senses() throws IOException {
        openView("nav.senses");
        selectRow(0);
        interact(() -> table().requestFocus());
        save("Sense.png");
    }

    @Test
    void metaLanguages() throws IOException {
        openView("nav.metaLangs");
        selectRow(0);
        interact(() -> table().requestFocus());
        save("MetaLanguages.png");
    }

    /** The range grammatical-info, under Dictionary configuration ▸ Ranges, its second element. */
    @Test
    void grammaticalInfo() throws IOException {
        TreeItem<NavNode> range = navLeaves().stream()
            .filter(i -> i.getValue() instanceof NavNode.Range r && r.range().getId().equals("grammatical-info"))
            .findFirst().orElseThrow(() -> new AssertionError("no range grammatical-info"));
        interact(() -> navTree().getSelectionModel().select(range));
        WaitForAsyncUtils.waitForFxEvents();
        TreeView<?> elements = lookup("#tableContainer .tree-view").queryAs(TreeView.class);
        interact(() -> {
            elements.getSelectionModel().select(1);
            elements.scrollTo(0);
            elements.requestFocus();
            navTree().scrollTo(Math.max(0, navTree().getRow(range) - 10));
        });
        save("GramInfo.png");
    }

    /** Category browser ▸ Note types. */
    @Test
    void noteTypes() throws IOException {
        openView("nav.noteTypes");
        selectRow(0);
        interact(() -> table().requestFocus());
        save("BrowseNoteType.png");
    }

    /** Saves the window as {@code name} in the output directory. */
    private void save(String name) throws IOException {
        assertTrue(DICTIONARY.isFile(), "no dictionary " + DICTIONARY);
        assertEquals(DICTIONARY.getAbsoluteFile(), document().file().getAbsoluteFile(), "the dictionary shown");
        WaitForAsyncUtils.waitForFxEvents();
        WritableImage[] image = new WritableImage[1];
        interact(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(Transform.scale(SCALE, SCALE));
            image[0] = stage.getScene().getRoot().snapshot(params, null);
        });
        Files.createDirectories(OUTPUT);
        ImageIO.write(toBufferedImage(image[0]), "png", OUTPUT.resolve(name).toFile());
    }

    /** The image as AWT's, for {@link ImageIO} (without the javafx-swing module). */
    private static BufferedImage toBufferedImage(WritableImage image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            reader.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, w);
            b.setRGB(0, y, w, 1, row, 0, w);
        }
        return b;
    }
}
