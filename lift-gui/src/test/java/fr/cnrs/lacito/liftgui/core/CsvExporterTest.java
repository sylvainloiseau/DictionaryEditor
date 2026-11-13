package fr.cnrs.lacito.liftgui.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExporterTest {

    @Test
    void escapeQuotesOnlyWhenNeeded() {
        assertEquals("plain", CsvExporter.escape("plain"));
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""));
        assertEquals("\"two\nlines\"", CsvExporter.escape("two\nlines"));
        assertEquals("", CsvExporter.escape(null));
    }

    @Test
    void writesBomHeaderAndRows(@TempDir Path dir) throws Exception {
        File f = dir.resolve("out.csv").toFile();
        CsvExporter.write(f, List.of("Form [xx]", "Gloss [en]"), List.of(List.of("kato", "cat, feline")));
        String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        assertEquals("﻿Form [xx],Gloss [en]" + System.lineSeparator()
            + "kato,\"cat, feline\"" + System.lineSeparator(), content);
    }
}
