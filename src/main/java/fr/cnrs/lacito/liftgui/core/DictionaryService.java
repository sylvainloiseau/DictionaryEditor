package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDocumentLoadingException;
import fr.cnrs.lacito.liftgui.core.LiftOpenException.Reason;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Loads a LIFT dictionary from a file, through lift-api. The file may have any name: its
     * content is checked ({@link LiftFileCheck}), and every reason it cannot be opened is
     * reported as a {@link LiftOpenException} explaining it to the user.
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
        LiftFileCheck.check(file);
        try {
            return LiftDictionary.loadDictionaryFromFile(file);
        } catch (LiftDocumentLoadingException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Impossible de charger le fichier LIFT: " + file.getAbsolutePath(), e);
            throw refused(file, e);
        }
    }

    /**
     * What lift-api's failure means for the user. A {@link SAXParseException} reported by
     * the parser itself is an XML error; one wrapping an exception of lift-api (which gives
     * the position of the element it could not build) is LIFT content refused.
     */
    private static LiftOpenException refused(File file, Exception e) {
        SAXParseException parse = null;
        for (Throwable t = e; t != null && parse == null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SAXParseException p) parse = p;
        }
        if (parse != null) {
            int line = parse.getLineNumber() < 1 ? -1 : parse.getLineNumber();
            int column = parse.getColumnNumber() < 1 ? -1 : parse.getColumnNumber();
            if (parse.getException() == null) {
                return LiftFileCheck.malformed(file, I18n.get("open.error.malformed", parse.getMessage()), e, line, column);
            }
            // An error in the ranges file is placed in that file: no excerpt of this one.
            boolean here = parse.getSystemId() == null || parse.getSystemId().endsWith(file.getName());
            return new LiftOpenException(Reason.INVALID_LIFT, file, I18n.get("open.error.invalid", parse.getException().getMessage()),
                e, line, column, here ? LiftFileCheck.excerpt(file, line, column) : null);
        }
        if (e instanceof LiftDocumentLoadingException && e.getCause() instanceof IOException io) {
            return LiftFileCheck.io(file, io);
        }
        // lift-api's own exceptions (a duplicate id, an unsupported version) say what is wrong.
        String message = e instanceof RuntimeException && e.getMessage() != null ? e.getMessage() : describeThrowable(e);
        return new LiftOpenException(Reason.INVALID_LIFT, file, I18n.get("open.error.invalid", message), e);
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
