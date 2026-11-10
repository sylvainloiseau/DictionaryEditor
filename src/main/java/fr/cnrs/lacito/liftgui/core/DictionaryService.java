package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDocumentLoadingException;
import fr.cnrs.lacito.liftapi.NotALiftDocumentException;
import fr.cnrs.lacito.liftgui.core.LiftOpenException.Reason;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.SAXParseException;

/**
 * Loading of dictionaries: LIFT files chosen by the user and the bundled demo.
 */
public final class DictionaryService {

    private static final Logger LOGGER = Logger.getLogger(
        DictionaryService.class.getName()
    );

    /** The width of the excerpt of a faulty line. */
    private static final int EXCERPT_WIDTH = 80;

    /**
     * Loads a LIFT dictionary from a file, through lift-api. The file may have any name:
     * lift-api checks its content (XML, root element {@code <lift>}), and every reason it
     * cannot be opened is reported as a {@link LiftOpenException} explaining it to the user.
     */
    public LiftDictionary loadFromFile(File file) throws LiftOpenException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }
        if (!file.isFile()) {
            throw new LiftOpenException(Reason.NOT_FOUND, file, I18n.get("open.error.notFound", file.getAbsolutePath()), null);
        }
        if (!Files.isReadable(file.toPath())) {
            throw new LiftOpenException(Reason.NOT_READABLE, file, I18n.get("open.error.notReadable", file.getAbsolutePath()), null);
        }
        try {
            return LiftDictionary.loadDictionaryFromFile(file);
        } catch (LiftDocumentLoadingException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Impossible de charger le fichier LIFT: " + file.getAbsolutePath(), e);
            throw refused(file, e);
        }
    }

    /**
     * What lift-api's failure means for the user. The file is examined only here, once
     * lift-api has refused it: its first character tells an empty file and a file that is
     * not XML from malformed XML. A {@link SAXParseException} reported by the parser itself
     * is an XML error; one wrapping an exception of lift-api (which gives the position of the
     * element it could not build) is LIFT content refused.
     */
    private static LiftOpenException refused(File file, Exception e) {
        if (e instanceof NotALiftDocumentException) {
            return new LiftOpenException(Reason.NOT_LIFT, file, I18n.get("open.error.notLift", e.getMessage()), e);
        }
        SAXParseException parse = null;
        for (Throwable t = e; t != null && parse == null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SAXParseException p) parse = p;
        }
        int line = parse == null || parse.getLineNumber() < 1 ? -1 : parse.getLineNumber();
        int column = parse == null || parse.getColumnNumber() < 1 ? -1 : parse.getColumnNumber();
        int first = firstSignificantChar(file);
        if (first == -1) {
            return new LiftOpenException(Reason.EMPTY, file, I18n.get("open.error.empty"), e);
        }
        if (first >= 0 && first != '<') {
            return new LiftOpenException(Reason.NOT_XML, file, I18n.get("open.error.notXml"), e, line, column, null);
        }
        if (parse != null) {
            if (parse.getException() == null) {
                String message = parse.getMessage() != null && parse.getMessage().contains("DOCTYPE")
                    ? I18n.get("open.error.doctype")
                    : I18n.get("open.error.malformed", parse.getMessage());
                return malformed(file, message, e, line, column);
            }
            // An error in the ranges file is placed in that file: no excerpt of this one.
            boolean here = parse.getSystemId() == null || parse.getSystemId().endsWith(file.getName());
            return new LiftOpenException(Reason.INVALID_LIFT, file, I18n.get("open.error.invalid", parse.getException().getMessage()),
                e, line, column, here ? excerpt(file, line, column) : null);
        }
        if (e instanceof LiftDocumentLoadingException && e.getCause() instanceof IOException io) {
            return io(file, io);
        }
        // lift-api's own exceptions (a duplicate id, an unsupported version) say what is wrong.
        String message = e instanceof RuntimeException && e.getMessage() != null ? e.getMessage() : describeThrowable(e);
        return new LiftOpenException(Reason.INVALID_LIFT, file, I18n.get("open.error.invalid", message), e);
    }

    /** A MALFORMED error, with the faulty line of the file when the position is known. */
    private static LiftOpenException malformed(File file, String message, Throwable cause, int line, int column) {
        return new LiftOpenException(Reason.MALFORMED, file, message, cause, line, column, excerpt(file, line, column));
    }

    private static LiftOpenException io(File file, IOException e) {
        return new LiftOpenException(Reason.IO, file, I18n.get("open.error.io", e.getClass().getSimpleName() + ": " + e.getMessage()), e);
    }

    /**
     * The first character of the file that is not whitespace (after a byte order mark), -1
     * when there is none, or -2 when the file cannot be read.
     */
    private static int firstSignificantChar(File file) {
        try (Reader r = reader(file)) {
            int c;
            while ((c = r.read()) >= 0) {
                if (c != '\uFEFF' && !Character.isWhitespace(c)) return c;
            }
            return -1;
        } catch (IOException e) {
            return -2;
        }
    }

    /**
     * The faulty line, cut to {@link #EXCERPT_WIDTH} characters around the column, and a
     * caret under the column; null when the line cannot be read.
     */
    static String excerpt(File file, int line, int column) {
        if (line < 1) return null;
        try (BufferedReader r = new BufferedReader(reader(file))) {
            String text = null;
            for (int i = 0; i < line; i++) {
                text = r.readLine();
                if (text == null) return null;
            }
            text = text.replace('\t', ' ').replace("\uFEFF", "");
            if (text.isBlank() || text.indexOf('\uFFFD') >= 0) return null;
            int col = Math.max(1, Math.min(column < 1 ? 1 : column, text.length() + 1));
            int from = Math.max(0, Math.min(col - 1 - EXCERPT_WIDTH / 2, text.length() - EXCERPT_WIDTH));
            int to = Math.min(text.length(), from + EXCERPT_WIDTH);
            String shown = (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
            int caret = col - 1 - from + (from > 0 ? 1 : 0);
            return column < 1 ? shown : shown + "\n" + " ".repeat(Math.max(0, caret)) + "^";
        } catch (IOException e) {
            return null;
        }
    }

    /** A reader of the file, in UTF-16 when its first bytes say so, else in UTF-8. */
    private static Reader reader(File file) throws IOException {
        InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()));
        in.mark(2);
        int b0 = in.read();
        int b1 = in.read();
        in.reset();
        Charset charset = StandardCharsets.UTF_8;
        if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0 && b1 == '<')) charset = StandardCharsets.UTF_16BE;
        else if ((b0 == 0xFF && b1 == 0xFE) || (b0 == '<' && b1 == 0)) charset = StandardCharsets.UTF_16LE;
        return new InputStreamReader(in, charset);
    }

    /** The message of the root cause, or its class name when it has none. */
    private static String describeThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /**
     * Loads the demo dictionary bundled with the application (with its ranges file when
     * present), or the minimal fallback demo; null when neither can be loaded. The files are
     * copied to a temporary directory removed when the application exits.
     */
    public LiftDictionary loadDemo() {
        try {
            Path tempDir = Files.createTempDirectory("dict-default-");
            tempDir.toFile().deleteOnExit();
            File liftFile = tempDir.resolve("20260302.lift").toFile();
            File rangesFile = tempDir.resolve("20260302.lift-ranges").toFile();
            // deleteOnExit runs in reverse order of registration: the files, then the directory.
            liftFile.deleteOnExit();
            rangesFile.deleteOnExit();
            try (
                InputStream inLift = DictionaryService.class.getResourceAsStream("/lift/20260302.lift");
                InputStream inRanges = DictionaryService.class.getResourceAsStream("/lift/20260302.lift-ranges")
            ) {
                if (inLift == null) return loadFallbackDemo();
                Files.copy(inLift, liftFile.toPath());
                if (inRanges != null) Files.copy(inRanges, rangesFile.toPath());
            }
            return LiftDictionary.loadDictionaryFromFile(liftFile);
        } catch (Exception e) {
            return loadFallbackDemo();
        }
    }

    private LiftDictionary loadFallbackDemo() {
        try (InputStream in = DictionaryService.class.getResourceAsStream("/lift/demo.lift")) {
            if (in == null) return null;
            File tmp = Files.createTempFile("dict-demo-", ".lift").toFile();
            tmp.deleteOnExit();
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return LiftDictionary.loadDictionaryFromFile(tmp);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de charger le dictionnaire de démonstration", e);
            return null;
        }
    }
}
