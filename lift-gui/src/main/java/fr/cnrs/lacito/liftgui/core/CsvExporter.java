package fr.cnrs.lacito.liftgui.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes a table as CSV (RFC 4180, UTF-8 with a BOM so that spreadsheets detect the encoding). */
public final class CsvExporter {

    private CsvExporter() {}

    public static void write(File file, List<String> headers, List<List<String>> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.print('﻿');
            writeLine(pw, headers);
            for (List<String> row : rows) writeLine(pw, row);
        }
    }

    private static void writeLine(PrintWriter pw, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) pw.print(",");
            pw.print(escape(values.get(i)));
        }
        pw.println();
    }

    /** Quotes a value containing a comma, a quote or a line break; doubles inner quotes. */
    static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
