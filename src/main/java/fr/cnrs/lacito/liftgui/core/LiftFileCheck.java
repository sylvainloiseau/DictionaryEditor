package fr.cnrs.lacito.liftgui.core;

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
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Checks, before lift-api reads it, that a file is an XML document whose root element is
 * {@code <lift>}. The standard StAX parser reads only up to the root element, so the check
 * follows the XML rules (encoding, declaration and comments anywhere before the root) and
 * costs the same whatever the size of the file. The rest of the file is left to lift-api.
 */
final class LiftFileCheck {

    /** The root element of a LIFT document, in no namespace. */
    private static final String ROOT = "lift";
    /** The width of the excerpt of a faulty line. */
    private static final int EXCERPT_WIDTH = 80;

    private LiftFileCheck() {}

    /** Throws when the file is empty, not XML, or XML whose root is not {@code <lift>}. */
    static void check(File file) throws LiftOpenException {
        try {
            if (firstSignificantChar(file) < 0) {
                throw new LiftOpenException(Reason.EMPTY, file, I18n.get("open.error.empty"), null);
            }
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                XMLStreamReader reader = factory.createXMLStreamReader(in);
                try {
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.DTD) {
                            Location at = reader.getLocation();
                            throw malformed(file, I18n.get("open.error.doctype"), null, line(at), column(at));
                        }
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            checkRoot(file, reader);
                            return;
                        }
                    }
                } finally {
                    reader.close();
                }
            }
        } catch (XMLStreamException e) {
            Location at = e.getLocation();
            String message = parserMessage(e);
            try {
                if (firstSignificantChar(file) != '<') {
                    throw new LiftOpenException(Reason.NOT_XML, file, I18n.get("open.error.notXml"), e, line(at), column(at), null);
                }
            } catch (IOException io) {
                throw io(file, io);
            }
            throw malformed(file, I18n.get("open.error.malformed", message), e, line(at), column(at));
        } catch (IOException e) {
            throw io(file, e);
        }
    }

    private static void checkRoot(File file, XMLStreamReader reader) throws LiftOpenException {
        String namespace = reader.getNamespaceURI();
        String prefix = reader.getPrefix();
        String name = (prefix == null || prefix.isEmpty() ? "" : prefix + ":") + reader.getLocalName();
        if (!ROOT.equals(reader.getLocalName())) {
            throw new LiftOpenException(Reason.NOT_LIFT, file, I18n.get("open.error.notLift", name), null);
        }
        if (namespace != null && !namespace.isEmpty()) {
            throw new LiftOpenException(Reason.NOT_LIFT, file, I18n.get("open.error.notLift.namespace", name, namespace), null);
        }
    }

    /** A MALFORMED error, with the faulty line of the file when the position is known. */
    static LiftOpenException malformed(File file, String message, Throwable cause, int line, int column) {
        return new LiftOpenException(Reason.MALFORMED, file, message, cause, line, column, excerpt(file, line, column));
    }

    static LiftOpenException io(File file, IOException e) {
        return new LiftOpenException(Reason.IO, file, I18n.get("open.error.io", e.getClass().getSimpleName() + ": " + e.getMessage()), e);
    }

    /** The parser's own explanation, without the "ParseError at [row,col]" prefix of StAX. */
    private static String parserMessage(XMLStreamException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        int i = m.indexOf("Message: ");
        return (i >= 0 ? m.substring(i + "Message: ".length()) : m).strip();
    }

    private static int line(Location at) {
        return at == null || at.getLineNumber() < 1 ? -1 : at.getLineNumber();
    }

    private static int column(Location at) {
        return at == null || at.getColumnNumber() < 1 ? -1 : at.getColumnNumber();
    }

    /**
     * The first character of the file that is not whitespace (after a byte order mark), or
     * -1 when there is none.
     */
    private static int firstSignificantChar(File file) throws IOException {
        try (Reader r = reader(file)) {
            int c;
            while ((c = r.read()) >= 0) {
                if (c != '﻿' && !Character.isWhitespace(c)) return c;
            }
            return -1;
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
            text = text.replace('\t', ' ').replace("﻿", "");
            if (text.isBlank() || text.indexOf('�') >= 0) return null;
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
}
