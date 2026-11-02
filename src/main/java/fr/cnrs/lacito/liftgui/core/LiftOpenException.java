/**

* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ
**/

package fr.cnrs.lacito.liftgui.core;

import java.io.File;

/**
 * A file that cannot be opened as a LIFT dictionary: why, and where in the file when the
 * parser tells. The message is the explanation shown to the user, in their language.
 */
public final class LiftOpenException extends Exception {

    /** Why the file cannot be opened; each reason has its own header in the error dialog. */
    public enum Reason {
        NOT_FOUND, NOT_READABLE, EMPTY, NOT_XML, MALFORMED, NOT_LIFT, INVALID_LIFT, IO
    }

    private final Reason reason;
    private final File file;
    private final int line;
    private final int column;
    private final String excerpt;

    public LiftOpenException(Reason reason, File file, String message, Throwable cause) {
        this(reason, file, message, cause, -1, -1, null);
    }

    /**
     * @param line    the line of the error, from 1, or -1
     * @param column  its column, from 1, or -1
     * @param excerpt the faulty line with a caret under the column, or null
     */
    public LiftOpenException(Reason reason, File file, String message, Throwable cause, int line, int column, String excerpt) {
        super(message, cause);
        this.reason = reason;
        this.file = file;
        this.line = line;
        this.column = column;
        this.excerpt = excerpt;
    }

    public Reason reason() {
        return reason;
    }

    public File file() {
        return file;
    }

    /** The line of the error, from 1; -1 when unknown. */
    public int line() {
        return line;
    }

    /** The column of the error, from 1; -1 when unknown. */
    public int column() {
        return column;
    }

    /** The faulty line and a caret under the column, on two lines; null when unknown. */
    public String excerpt() {
        return excerpt;
    }
}
