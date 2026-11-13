package fr.cnrs.lacito.liftfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftfx.internal.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The texts of the library: every key it uses exists, in English and in French. */
class MessagesTest {

    private static final String BUNDLE = "/fr/cnrs/lacito/liftfx/internal/messages%s.properties";
    /** A string literal that looks like a key: "editor.forms", "tooltip.noType.field"… */
    private static final Pattern KEY = Pattern.compile("\"([a-z][A-Za-z]*(?:\\.[A-Za-z]+)+)\"");

    @AfterEach
    void restoreLocale() {
        LiftFx.setLocale(Locale.ENGLISH);
    }

    private static Properties bundle(String suffix) throws IOException {
        Properties p = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream(BUNDLE.formatted(suffix))) {
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }

    /** The key-like literals of the sources, which are the keys given to Messages. */
    private static Set<String> keysInSources() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(f));
                while (m.find()) if (!m.group(1).startsWith("fr.")) keys.add(m.group(1));
            }
        }
        return keys;
    }

    @Test
    void englishAndFrenchHaveTheSameKeys() throws IOException {
        assertEquals(bundle("").stringPropertyNames(), bundle("_fr").stringPropertyNames());
    }

    @Test
    void everyKeyOfTheSourcesHasATextInBothLanguages() throws IOException {
        Set<String> en = bundle("").stringPropertyNames();
        Set<String> fr = bundle("_fr").stringPropertyNames();
        List<String> missing = new ArrayList<>();
        for (String k : keysInSources()) {
            if (!en.contains(k)) missing.add(k + " (en)");
            if (!fr.contains(k)) missing.add(k + " (fr)");
        }
        assertTrue(missing.isEmpty(), "missing texts: " + missing);
    }

    @Test
    void everyKindHasItsTextsInBothLanguages() {
        for (Locale l : List.of(Locale.ENGLISH, Locale.FRENCH)) {
            LiftFx.setLocale(l);
            for (ComponentKind k : ComponentKind.values()) {
                for (String text : List.of(k.label(), k.addTooltip(), k.deleteTooltip())) {
                    assertTrue(!text.startsWith("!"), "no text for " + k + " in " + l + ": " + text);
                }
            }
        }
    }

    @Test
    void theLocaleChoosesTheTexts() {
        LiftFx.setLocale(Locale.FRENCH);
        assertEquals("Ajouter un sens", Messages.get("tooltip.add.sense"));
        LiftFx.setLocale(Locale.ENGLISH);
        assertEquals("Add a sense", Messages.get("tooltip.add.sense"));
    }

    @Test
    void aLanguageWithoutTranslationGetsEnglish() {
        LiftFx.setLocale(Locale.GERMAN);
        assertEquals("Add a sense", Messages.get("tooltip.add.sense"));
    }
}
