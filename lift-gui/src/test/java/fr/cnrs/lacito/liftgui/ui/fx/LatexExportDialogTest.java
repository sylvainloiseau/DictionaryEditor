package fr.cnrs.lacito.liftgui.ui.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.export.LatexEngine;
import fr.cnrs.lacito.liftapi.export.LatexExportWarning;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.LatexExportDialog;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

/** Tools → Export as LaTeX / PDF. */
class LatexExportDialogTest extends MainWindowTest {

    private static final String SMALL = """
        <?xml version="1.0" encoding="UTF-8"?>
        <lift version="0.15" producer="test">
        <entry id="e1"><lexical-unit><form lang="oc-FR-fonipa"><text>agyj</text></form></lexical-unit>
          <sense id="s1"><gloss lang="fr"><text>aiguille</text></gloss>
            <trait name="semantic-domain-ddp4" value="5.1.1 Mobilier"/></sense></entry>
        <entry id="e2"><lexical-unit><form lang="oc-FR-fonipa"><text>batøz</text></form></lexical-unit>
          <sense id="s2"><gloss lang="fr"><text>batteuse</text></gloss>
            <trait name="semantic-domain-ddp4" value="6.2 Agriculture"/></sense></entry>
        </lift>
        """;

    @BeforeEach
    void noEngine() {
        LatexExportDialog.setEngineFinderForTests(List::of);
    }

    @AfterEach
    void realEngines() {
        LatexExportDialog.setEngineFinderForTests(null);
    }

    /** Opens a small dictionary read from a file in {@code dir}. */
    private File openSmall(Path dir) throws Exception {
        File file = dir.resolve("small.lift").toFile();
        Files.writeString(file.toPath(), SMALL);
        LiftDictionary d = LiftDictionary.loadDictionaryFromFile(file);
        interact(() -> document().load(d, file, false));
        return file;
    }

    private DialogPane openDialog() {
        fireMenu("menu.tools", "menu.tools.exportLatex");
        return dialog();
    }

    private LatexExportDialog dialogObject(DialogPane pane) {
        return (LatexExportDialog) pane.getProperties().get(LatexExportDialog.PANE_PROPERTY);
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T in(DialogPane pane, String id) {
        return (T) from(pane).lookup("#" + id).query();
    }

    private void close(DialogPane pane) {
        interact(() -> pane.getScene().getWindow().hide());
    }

    @Test
    void theDialogListsTheLanguagesOfTheDictionaryPrefilled() {
        DialogPane pane = openDialog();
        ComboBox<String> occitan = in(pane, "polyglossia-oc-FR-fonipa");
        ComboBox<String> french = in(pane, "polyglossia-fr");
        ComboBox<String> unknown = in(pane, "polyglossia-qaa-x-spec");
        assertEquals("occitan", occitan.getValue());
        assertEquals("french", french.getValue());
        assertEquals("", unknown.getValue() == null ? "" : unknown.getValue());
        assertTrue(occitan.getItems().contains("tuwari") || occitan.getItems().contains("french"), "polyglossia's names to choose from");
        close(pane);
    }

    @Test
    void anUnmappedLanguageIsReportedAndSelectsItsRow() {
        DialogPane pane = openDialog();
        TitledPane warning = from((Node) in(pane, "latexWarnings")).lookup(".latex-warning")
            .match((Node n) -> n.getUserData() == LatexExportWarning.Kind.LANGUAGE_NOT_MAPPED)
            .queryAs(TitledPane.class);
        interact(() -> warning.setExpanded(true));
        Hyperlink link = from(warning).lookup(".hyperlink").match((Node n) -> "qaa-x-spec".equals(((Hyperlink) n).getText()))
            .queryAs(Hyperlink.class);
        interact(link::fire);
        WaitForAsyncUtils.waitForFxEvents();
        ComboBox<String> row = in(pane, "polyglossia-qaa-x-spec");
        assertTrue(row.isFocused() || row.getEditor().isFocused(), "the language's field is selected");
        close(pane);
    }

    @Test
    void theDefaultsOfAFirstExport(@TempDir Path dir) throws Exception {
        File file = openSmall(dir);
        DialogPane pane = openDialog();
        assertEquals(dir.resolve("small.tex").toString(), ((TextField) in(pane, "latexOutput")).getText(),
            "the .tex next to the dictionary");
        assertEquals("fr", ((ComboBox<?>) in(pane, "latexSort")).getValue(), "the language of the glosses");
        assertTrue(file.exists());
        close(pane);
    }

    @Test
    void withoutAnEngineThePdfCannotBeAskedFor() {
        DialogPane pane = openDialog();
        CheckBox pdf = in(pane, "latexPdf");
        assertTrue(pdf.isDisabled());
        assertFalse(pdf.isSelected());
        close(pane);
    }

    @Test
    void aFilledDialogWritesTheTex(@TempDir Path dir) throws Exception {
        openSmall(dir);
        DialogPane pane = openDialog();
        Path tex = dir.resolve("out.tex");
        interact(() -> {
            ((TextField) in(pane, "latexTitle")).setText("Mon lexique");
            ((TextField) in(pane, "latexOutput")).setText(tex.toString());
            ComboBox<String> font = in(pane, "font-oc-FR-fonipa");
            font.getEditor().setText("Doulos SIL");
            ComboBox<String> main = in(pane, "latexMainFont");
            main.getEditor().setText("Charis SIL");
        });
        clickOn((Button) in(pane, "latexExport"));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.exists(tex) && dialogCount() > 1);
        WaitForAsyncUtils.waitForFxEvents();
        String s = Files.readString(tex);
        assertTrue(s.contains("\\title{Mon lexique}"));
        assertTrue(s.contains("\\setdefaultlanguage{french}"));
        assertTrue(s.contains("\\setotherlanguage{occitan}"));
        assertTrue(s.contains("\\setmainfont{Charis SIL}"));
        assertTrue(s.contains("\\newfontfamily\\occitanfont{Doulos SIL}"));
        assertTrue(s.contains("\\entry{\\foreignlanguage{occitan}{agyj}}"));
        // The message "done", with its buttons.
        DialogPane done = lookup(".dialog-pane").match((Node n) -> n != pane).queryAs(DialogPane.class);
        assertTrue(done.getContentText().contains(tex.toString()));
        interact(() -> done.getScene().getWindow().hide());
        close(pane);
    }

    private long dialogCount() {
        return Window.getWindows().stream().filter(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane).count();
    }

    @Test
    void savedSettingsLoadBack(@TempDir Path dir) throws Exception {
        openSmall(dir);
        DialogPane pane = openDialog();
        Path json = dir.resolve("settings.json");
        interact(() -> dialogObject(pane).setChoosers(new Fixed(json.toFile())));
        interact(() -> ((TextField) in(pane, "latexTitle")).setText("Saved title"));
        clickOn((Button) in(pane, "latexSave"));
        assertTrue(Files.readString(json).contains("\"title\" : \"Saved title\""));
        assertTrue(Files.readString(json).contains("\"oc-FR-fonipa\" : \"occitan\""));

        interact(() -> ((TextField) in(pane, "latexTitle")).setText("Changed"));
        clickOn((Button) in(pane, "latexLoad"));
        assertEquals("Saved title", ((TextField) in(pane, "latexTitle")).getText());
        assertEquals("occitan", ((ComboBox<?>) in(pane, "polyglossia-oc-FR-fonipa")).getValue());
        close(pane);
    }

    @Test
    void theSettingsComeBackWhenTheDialogIsOpenedAgain(@TempDir Path dir) throws Exception {
        openSmall(dir);
        DialogPane pane = openDialog();
        Path tex = dir.resolve("again.tex");
        interact(() -> {
            ((TextField) in(pane, "latexTitle")).setText("Remembered");
            ((TextField) in(pane, "latexOutput")).setText(tex.toString());
        });
        clickOn((Button) in(pane, "latexExport"));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.exists(tex) && dialogCount() > 1);
        interact(() -> new java.util.ArrayList<>(Window.getWindows()).forEach(w -> {
            if (w != stage) w.hide();
        }));

        DialogPane again = openDialog();
        assertEquals("Remembered", ((TextField) in(again, "latexTitle")).getText());
        assertEquals(tex.toString(), ((TextField) in(again, "latexOutput")).getText());
        close(again);
    }

    @Test
    void withAnEngineThePdfIsMade(@TempDir Path dir) throws Exception {
        assumeTrue(!LatexEngine.findAll().isEmpty(), "no XeLaTeX or LuaLaTeX on this machine");
        LatexExportDialog.setEngineFinderForTests(null);
        openSmall(dir);
        DialogPane pane = openDialog();
        Path tex = dir.resolve("pdf.tex");
        CheckBox pdf = in(pane, "latexPdf");
        assertTrue(pdf.isSelected(), "checked when an engine is found");
        interact(() -> {
            ((TextField) in(pane, "latexOutput")).setText(tex.toString());
            ((CheckBox) in(pane, "latexOpenPdf")).setSelected(false);
        });
        clickOn((Button) in(pane, "latexExport"));
        Path pdfFile = dir.resolve("pdf.pdf");
        WaitForAsyncUtils.waitFor(120, TimeUnit.SECONDS, () -> dialogCount() > 1);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(Files.size(pdfFile) > 1000, "the PDF");
        DialogPane done = lookup(".dialog-pane").match((Node n) -> n != pane).queryAs(DialogPane.class);
        assertNotNull(done.lookupButton(done.getButtonTypes().stream()
            .filter(b -> b.getText().equals(I18n.get("latex.openPdfButton"))).findFirst().orElseThrow()));
        interact(() -> done.getScene().getWindow().hide());
        close(pane);
    }

    private static final String HEAD = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n";

    @Test
    void aCustomStylesheetIsUsedSavedAndRemembered(@TempDir Path dir) throws Exception {
        openSmall(dir);
        Path custom = dir.resolve("custom.xsl");
        Files.writeString(custom, HEAD + "<xsl:param name=\"author\" select=\"'From the stylesheet'\"/>\n</xsl:stylesheet>\n");
        DialogPane pane = openDialog();
        interact(() -> dialogObject(pane).setChoosers(new Fixed(custom.toFile())));
        clickOn((Button) from(pane).lookup(".button").match((Node n) -> n instanceof Button b
            && I18n.get("latex.browse").equals(b.getText()) && b.getParent().lookup("#latexStylesheet") != null).query());
        assertEquals(custom.toString(), ((TextField) in(pane, "latexStylesheet")).getText());
        Path tex = dir.resolve("custom.tex");
        interact(() -> ((TextField) in(pane, "latexOutput")).setText(tex.toString()));
        interact(() -> dialogObject(pane).setChoosers(new Fixed(dir.resolve("s.json").toFile())));
        clickOn((Button) in(pane, "latexSave"));
        assertTrue(Files.readString(dir.resolve("s.json")).contains("\"customStylesheet\" : \"custom.xsl\""),
            "relative to the dictionary's folder");
        clickOn((Button) in(pane, "latexExport"));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.exists(tex) && dialogCount() > 1);
        interact(() -> new java.util.ArrayList<>(Window.getWindows()).forEach(w -> {
            if (w != stage) w.hide();
        }));

        DialogPane again = openDialog();
        assertEquals(custom.toString(), ((TextField) in(again, "latexStylesheet")).getText(), "remembered");
        close(again);
    }

    @Test
    void aStartingStylesheetIsCreatedAndCompiles(@TempDir Path dir) throws Exception {
        openSmall(dir);
        Path custom = dir.resolve("start.xsl");
        DialogPane pane = openDialog();
        interact(() -> dialogObject(pane).setChoosers(new Fixed(custom.toFile())));
        fire((Button) in(pane, "latexStylesheetCreate"));
        DialogPane info = lookup(".dialog-pane").match((Node n) -> n != pane).queryAs(DialogPane.class);
        assertTrue(info.getContentText().contains(custom.toString()));
        interact(() -> info.getScene().getWindow().hide());
        assertTrue(Files.readString(custom).contains("disabled:template"));
        assertEquals(custom.toString(), ((TextField) in(pane, "latexStylesheet")).getText());
        interact(() -> dialogObject(pane).refreshWarnings());
        assertTrue(from((Node) in(pane, "latexWarnings")).lookup(".latex-warning")
            .match((Node n) -> n.getUserData() instanceof LatexExportWarning.Kind k && k.name().startsWith("CUSTOM"))
            .tryQuery().isEmpty(), "it compiles");
        close(pane);
    }

    @Test
    void anInvalidStylesheetIsReportedWithItsLine(@TempDir Path dir) throws Exception {
        openSmall(dir);
        Path bad = dir.resolve("bad.xsl");
        Files.writeString(bad, HEAD + "<xsl:template match=\"/\">\n<xsl:value-of select=\"concat(\"/>\n</xsl:template>\n</xsl:stylesheet>\n");
        DialogPane pane = openDialog();
        interact(() -> {
            ((TextField) in(pane, "latexStylesheet")).setText(bad.toString());
            dialogObject(pane).refreshWarnings();
        });
        TitledPane warning = from((Node) in(pane, "latexWarnings")).lookup(".latex-warning")
            .match((Node n) -> n.getUserData() == LatexExportWarning.Kind.CUSTOM_STYLESHEET_INVALID)
            .queryAs(TitledPane.class);
        interact(() -> warning.setExpanded(true));
        assertTrue(from(warning).lookup(".label").match((Node n) -> ((javafx.scene.control.Label) n).getText()
            .startsWith("bad.xsl, line 3: ")).tryQuery().isPresent());
        close(pane);
    }

    /** Every chooser answers the same file. */
    private record Fixed(File file) implements LatexExportDialog.Choosers {
        public File saveTex(Window owner, File initial) { return file; }
        public File chooseDirectory(Window owner, File initial) { return file; }
        public File chooseProgram(Window owner) { return file; }
        public File saveSettings(Window owner, File initial) { return file; }
        public File openSettings(Window owner, File initial) { return file; }
        public File openStylesheet(Window owner, File initial) { return file; }
        public File saveStylesheet(Window owner, File initial) { return file; }
    }
}
