package fr.cnrs.lacito.liftgui.core;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.LiftDocumentLoadingException;
import fr.cnrs.lacito.liftgui.ui.I18n;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loading of dictionaries: LIFT files chosen by the user and the bundled demo.
 */
public final class DictionaryService {

    private static final Logger LOGGER = Logger.getLogger(
        DictionaryService.class.getName()
    );

    /**
     * Charge un dictionnaire LIFT a partir d'un fichier {@code .lift} via {@code lift-api}.
     *
     * Contraintes:
     * - ne re-implemente pas le parsing XML (delegue a {@code lift-api})
     * - retourne un dictionnaire en memoire
     * - gere des erreurs simples (fichier absent, format invalide)
     */
    public LiftDictionary loadFromFile(File file)
        throws IOException, LiftOpenException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException(
                I18n.get("service.fileNotFound", file.getAbsolutePath())
            );
        }

        if (!Files.isReadable(file.toPath())) {
            throw new IOException(
                I18n.get("service.fileNotReadable", file.getAbsolutePath())
            );
        }

        String name = file.getName().toLowerCase();
        if (!name.endsWith(".lift")) {
            throw new LiftOpenException(
                I18n.get("service.notLiftExtension")
            );
        }

        // Heuristique legere pour reperer un fichier qui ne ressemble pas du tout a du LIFT.
        // (Sans refaire de parsing XML.)
        if (!looksLikeLift(file)) {
            throw new LiftOpenException(
                I18n.get("service.notLiftDocument")
            );
        }

        try {
            return LiftDictionary.loadDictionaryFromFile(file);
        } catch (LiftDocumentLoadingException e) {
            LOGGER.log(
                Level.SEVERE,
                "Impossible de charger le fichier LIFT: " +
                    file.getAbsolutePath(),
                e
            );
            throw new LiftOpenException(
                I18n.get("service.cannotLoad", describeThrowable(e)),
                e
            );
        } catch (RuntimeException e) {
            // Le parser SAX peut jeter des IllegalStateException si le XML ne correspond pas a la grammaire attendue.
            LOGGER.log(
                Level.SEVERE,
                "Format invalide lors de la lecture du LIFT: " +
                    file.getAbsolutePath(),
                e
            );
            throw new LiftOpenException(
                I18n.get("service.invalidFormat", describeThrowable(e)),
                e
            );
        }
    }

    private static boolean looksLikeLift(File file) throws IOException {
        try (
            BufferedReader reader = Files.newBufferedReader(
                file.toPath(),
                StandardCharsets.UTF_8
            )
        ) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 50) {
                linesRead++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("<?xml")) continue;
                if (trimmed.startsWith("<!--")) continue;
                return trimmed.contains("<lift") || trimmed.startsWith("<lift");
            }
        }
        return false;
    }

    private static String describeThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + message;
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
