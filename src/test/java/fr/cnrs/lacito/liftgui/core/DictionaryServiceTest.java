package fr.cnrs.lacito.liftgui.core;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.core.LiftOpenException.Reason;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opening a file: what is accepted, and why the rest is refused. */
class DictionaryServiceTest {

    private static final String ENTRY = "<entry id=\"e1\"><lexical-unit><form lang=\"fr\"><text>chat</text></form></lexical-unit></entry>";

    @TempDir
    Path dir;

    private final DictionaryService service = new DictionaryService();

    @BeforeAll
    static void english() {
        I18n.setLocale(Locale.ENGLISH);
    }

    private File file(String name, String content) throws IOException {
        return file(name, content, StandardCharsets.UTF_8);
    }

    private File file(String name, String content, Charset charset) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, charset);
        return p.toFile();
    }

    private LiftOpenException refused(File f) {
        return assertThrows(LiftOpenException.class, () -> service.loadFromFile(f));
    }

    /* ─── Accepted ─── */

    @Test
    void theDeclarationAndTheRootOnOneLine() throws Exception {
        File f = file("one-line.lift", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><lift producer=\"test\" version=\"0.13\">" + ENTRY + "</lift>");
        assertEquals(1, service.loadFromFile(f).getLiftDictionaryRegistry().nEntries());
    }

    @Test
    void aCommentOnSeveralLinesAndAProcessingInstructionBeforeTheRoot() throws Exception {
        File f = file("comment.lift", "<?xml version=\"1.0\"?>\n<!-- a\n comment\n-->\n<?app something?>\n<lift producer=\"test\" version=\"0.13\">\n" + ENTRY + "\n</lift>\n");
        assertEquals(1, service.loadFromFile(f).getLiftDictionaryRegistry().nEntries());
    }

    @Test
    void aByteOrderMark() throws Exception {
        File f = file("bom.lift", "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<lift producer=\"test\" version=\"0.13\">" + ENTRY + "</lift>");
        assertNotNull(service.loadFromFile(f));
    }

    @Test
    void aUtf16File() throws Exception {
        File f = file("utf16.lift", "﻿<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n<lift producer=\"test\" version=\"0.13\">" + ENTRY + "</lift>", StandardCharsets.UTF_16BE);
        assertEquals(1, service.loadFromFile(f).getLiftDictionaryRegistry().nEntries());
    }

    @Test
    void anyFileName() throws Exception {
        File f = file("export.xml", "<lift producer=\"test\" version=\"0.13\">" + ENTRY + "</lift>");
        LiftDictionary d = service.loadFromFile(f);
        assertEquals(1, d.getLiftDictionaryRegistry().nEntries());
    }

    /* ─── Refused ─── */

    @Test
    void anEmptyFile() throws Exception {
        assertEquals(Reason.EMPTY, refused(file("empty.lift", "")).reason());
        assertEquals(Reason.EMPTY, refused(file("blank.lift", "  \n\n ")).reason());
    }

    @Test
    void aPlainTextFile() throws Exception {
        LiftOpenException e = refused(file("notes.lift", "Some notes about <lift> files.\n"));
        assertEquals(Reason.NOT_XML, e.reason());
        assertEquals(1, e.line());
    }

    @Test
    void anImage() throws Exception {
        Path p = dir.resolve("image.lift");
        Files.write(p, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13});
        assertEquals(Reason.NOT_XML, refused(p.toFile()).reason());
    }

    @Test
    void anotherRootElement() throws Exception {
        LiftOpenException e = refused(file("page.lift", "<?xml version=\"1.0\"?>\n<html><body><lift/></body></html>"));
        assertEquals(Reason.NOT_LIFT, e.reason());
        assertTrue(e.getMessage().contains("<html>"), e.getMessage());
    }

    @Test
    void aRootInANamespace() throws Exception {
        LiftOpenException e = refused(file("ns.lift", "<lift xmlns=\"urn:x\" version=\"0.13\"/>"));
        assertEquals(Reason.NOT_LIFT, e.reason());
        assertTrue(e.getMessage().contains("urn:x"), e.getMessage());
    }

    @Test
    void anUnclosedTagIsPlacedWithItsLine() throws Exception {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\"?>\n<lift producer=\"test\" version=\"0.13\">\n");
        for (int i = 3; i < 12; i++) b.append("  <!-- line ").append(i).append(" -->\n");
        b.append("  <entry id=\"e1\"><lexical-unit></entry>\n</lift>\n");
        LiftOpenException e = refused(file("broken.lift", b.toString()));
        assertEquals(Reason.MALFORMED, e.reason());
        assertEquals(12, e.line());
        assertNotNull(e.excerpt());
        assertTrue(e.excerpt().startsWith("  <entry id=\"e1\"><lexical-unit></entry>"), e.excerpt());
        assertTrue(e.excerpt().contains("\n") && e.excerpt().endsWith("^"), e.excerpt());
    }

    @Test
    void anErrorInTheRootTagIsMalformedXml() throws Exception {
        LiftOpenException e = refused(file("root.lift", "<?xml version=\"1.0\"?>\n<lift version=0.13>\n</lift>"));
        assertEquals(Reason.MALFORMED, e.reason());
        assertEquals(2, e.line());
    }

    @Test
    void aDoctype() throws Exception {
        LiftOpenException e = refused(file("doctype.lift", "<?xml version=\"1.0\"?>\n<!DOCTYPE lift>\n<lift producer=\"test\" version=\"0.13\"/>"));
        assertEquals(Reason.MALFORMED, e.reason());
        assertEquals(I18n.get("open.error.doctype"), e.getMessage());
    }

    @Test
    void aDuplicateId() throws Exception {
        LiftOpenException e = refused(file("dup.lift", "<lift producer=\"test\" version=\"0.13\">" + ENTRY + "\n" + ENTRY + "</lift>"));
        assertEquals(Reason.INVALID_LIFT, e.reason());
        assertTrue(e.getMessage().contains("e1"), e.getMessage());
    }

    @Test
    void aMissingFile() {
        assertEquals(Reason.NOT_FOUND, refused(dir.resolve("none.lift").toFile()).reason());
        assertEquals(Reason.NOT_FOUND, refused(dir.toFile()).reason());
    }

    @Test
    void anExcerptOfALongLineIsCutAroundTheColumn() throws Exception {
        String line = "x".repeat(200) + "!" + "y".repeat(200);
        File f = file("long.txt", "first\n" + line + "\n");
        String excerpt = LiftFileCheck.excerpt(f, 2, 201);
        String[] parts = excerpt.split("\n");
        assertEquals(parts[0].indexOf('!'), parts[1].indexOf('^'), excerpt);
        assertTrue(parts[0].startsWith("…") && parts[0].endsWith("…"));
    }
}
