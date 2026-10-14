package fr.cnrs.lacito.liftgui.undo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.change.ModelChange;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.util.Subscription;

/**
 * The undo history of a dictionary, built on the changes lift-api reports.
 *
 * <h2>How changes become entries</h2>
 *
 * The history listens to the dictionary. Each change goes:
 * <ol>
 * <li>into the user action being performed ({@link #perform}), if any: one entry per
 * action, with its label and subject;</li>
 * <li>otherwise, into the typing entry of the focused field ({@link #beginTyping}): all
 * the keystrokes of one focus session are one entry;</li>
 * <li>otherwise, into an entry of its own, which stays open until the JavaFX event being
 * handled is over: a cascade made by one call is one entry, even without an action.</li>
 * </ol>
 * Nothing escapes the history, whichever code made the change. The changes the history
 * makes itself, while undoing or redoing, are not recorded.
 *
 * <h2>The list</h2>
 *
 * One list and a cursor: entries before the cursor can be undone, entries after it
 * redone, and recording a new entry discards the latter. An entry is added at its first
 * change and stays open while changes are merged into it, so that the document is
 * modified, and the change can be undone, at once. The save point is the cursor at the
 * last save; the document is modified when the cursor is elsewhere. The oldest entries are
 * dropped beyond the limit.
 *
 * <h2>Closing an entry</h2>
 *
 * When an entry closes, outside the delivery of a change (lift-api refuses changes made
 * while it reports one), the finisher runs - the application sets the modification date of
 * the entries it touched there - and its changes are added to the same entry.
 *
 * Not thread-safe: the JavaFX application thread only, like the dictionary.
 */
public final class History {

    public static final int DEFAULT_LIMIT = 1000;

    private final Consumer<Runnable> later;
    private final List<HistoryEntry> entries = new ArrayList<>();
    private int cursor;
    /** The cursor at the last save; -1 once that state can no longer be reached. */
    private int savePoint;
    private int limit = DEFAULT_LIMIT;

    private LiftDictionary dictionary;
    private Subscription subscription = Subscription.EMPTY;

    /** The entry changes are merged into, if any: always the last one. */
    private HistoryEntry open;
    private int actionDepth;
    private String actionLabel;
    private Object actionSubject;
    private HistoryEntry actionEntry;
    private Object typingKey;
    /** The entry a finisher is completing: its changes go there. */
    private HistoryEntry finishing;
    private boolean replaying;
    private boolean delivering;

    private Function<HistoryEntry, String> labeler = e -> e.actionLabel() != null ? e.actionLabel() : e.kind().name();
    private Consumer<HistoryEntry> finisher = e -> {};

    private final ReadOnlyBooleanWrapper atSavePoint = new ReadOnlyBooleanWrapper(this, "atSavePoint", true);
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo", false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo", false);
    private final ReadOnlyStringWrapper undoLabel = new ReadOnlyStringWrapper(this, "undoLabel");
    private final ReadOnlyStringWrapper redoLabel = new ReadOnlyStringWrapper(this, "redoLabel");

    /** A history whose change entries close at the end of the current JavaFX event. */
    public History() {
        this(Platform::runLater);
    }

    /**
     * @param later runs a task once the event being handled is over ({@code Platform::runLater}
     *              in the application; a test can run the tasks when it wants)
     */
    public History(Consumer<Runnable> later) {
        this.later = later;
    }

    /* ─── Configuration ─── */

    /** How entries are labelled; an action's label is used as is. */
    public void setLabeler(Function<HistoryEntry, String> labeler) {
        this.labeler = labeler;
        update();
    }

    /** What runs when an entry closes; its changes join the entry. */
    public void setFinisher(Consumer<HistoryEntry> finisher) {
        this.finisher = finisher == null ? e -> {} : finisher;
    }

    /** The number of entries kept; the oldest are dropped beyond it. */
    public void setLimit(int limit) {
        this.limit = Math.max(1, limit);
        enforceLimit();
        update();
    }

    public int limit() {
        return limit;
    }

    /* ─── The dictionary ─── */

    /** Forgets everything, and listens to {@code d} (none when null). */
    public void attach(LiftDictionary d) {
        subscription.unsubscribe();
        subscription = Subscription.EMPTY;
        entries.clear();
        cursor = 0;
        savePoint = 0;
        open = null;
        actionEntry = null;
        typingKey = null;
        dictionary = d;
        if (d != null) subscription = d.addChangeListener(this::onChange);
        update();
    }

    /**
     * Takes over the state of another history (a reloaded scene keeps the history of the
     * dictionary it takes over), which stops listening.
     */
    public void takeOver(History previous) {
        previous.breakMerge();
        previous.subscription.unsubscribe();
        previous.subscription = Subscription.EMPTY;
        attach(previous.dictionary);
        entries.addAll(previous.entries);
        cursor = previous.cursor;
        savePoint = previous.savePoint;
        limit = previous.limit;
        update();
    }

    public LiftDictionary dictionary() {
        return dictionary;
    }

    /* ─── Recording ─── */

    private void onChange(ModelChange change) {
        if (replaying) return;
        delivering = true;
        try {
            route(change);
        } finally {
            delivering = false;
        }
        update();
    }

    private void route(ModelChange change) {
        if (finishing != null) {
            finishing.add(change);
            return;
        }
        if (actionDepth > 0) {
            if (actionEntry == null) {
                close();
                actionEntry = append(new HistoryEntry(HistoryEntry.Kind.ACTION, null, actionLabel, actionSubject));
            }
            actionEntry.add(change);
            return;
        }
        if (typingKey != null) {
            if (open == null || open.kind() != HistoryEntry.Kind.TYPING || open.mergeKey() != typingKey) {
                close();
                open = append(new HistoryEntry(HistoryEntry.Kind.TYPING, typingKey, null, null));
            }
            open.add(change);
            return;
        }
        if (open == null || open.kind() != HistoryEntry.Kind.CHANGE) {
            close();
            HistoryEntry e = append(new HistoryEntry(HistoryEntry.Kind.CHANGE, null, null, null));
            open = e;
            later.accept(() -> finish(e));
        }
        open.add(change);
    }

    /**
     * Runs a user gesture as one undo step, labelled {@code label}.
     *
     * Everything the gesture changes, whatever code changes it, is one entry; a gesture that
     * changes nothing (a cancelled dialog) records nothing. If the gesture throws, its
     * changes are reverted, nothing is recorded, and the exception is rethrown. Nested
     * calls belong to the outer one.
     *
     * @param subject what the action is about, shown after an undo; null to use the first
     *                change's subject
     */
    public void perform(String label, Object subject, Runnable gesture) {
        if (actionDepth > 0) {
            gesture.run();
            return;
        }
        breakMerge();
        actionDepth = 1;
        actionLabel = label;
        actionSubject = subject;
        actionEntry = null;
        boolean done = false;
        try {
            gesture.run();
            done = true;
        } catch (RuntimeException failure) {
            HistoryEntry e = actionEntry;
            if (e != null) {
                replaying = true;
                try {
                    e.revertQuietly(failure);
                } finally {
                    replaying = false;
                }
                entries.remove(e);
                cursor--;
            }
            throw failure;
        } finally {
            actionDepth = 0;
            HistoryEntry e = actionEntry;
            actionEntry = null;
            if (done && e != null) {
                open = e;
                finish(e);
            }
            update();
        }
    }

    /** A field got the focus: what is typed in it, until {@link #endTyping}, is one entry. */
    public void beginTyping(Object field) {
        if (typingKey != field) breakMerge();
        typingKey = field;
    }

    /** The field lost the focus: its typing entry is closed. */
    public void endTyping(Object field) {
        if (typingKey != field) return;
        typingKey = null;
        breakMerge();
    }

    /** Closes the open entry: the next change makes a new one. */
    public void breakMerge() {
        if (open != null) finish(open);
    }

    private void finish(HistoryEntry e) {
        if (e != open) return;
        if (!delivering && !replaying && !entries.isEmpty() && entries.getLast() == e) {
            finishing = e;
            try {
                finisher.accept(e);
            } finally {
                finishing = null;
            }
        }
        open = null;
        update();
    }

    /** Closes the open entry without its finisher: called while a change is delivered. */
    private void close() {
        open = null;
    }

    private HistoryEntry append(HistoryEntry e) {
        if (cursor < entries.size()) {
            entries.subList(cursor, entries.size()).clear();
            if (savePoint > cursor) savePoint = -1;
        }
        entries.add(e);
        cursor++;
        enforceLimit();
        return e;
    }

    private void enforceLimit() {
        while (entries.size() > limit) {
            entries.removeFirst();
            cursor--;
            if (savePoint >= 0) savePoint--;
        }
    }

    /* ─── Undo and redo ─── */

    /**
     * Undoes the last entry.
     *
     * @return the entry undone, or empty when there is none
     * @throws RuntimeException if lift-api refuses to revert a change: the dictionary and
     *         the history are then left as they were
     */
    public Optional<HistoryEntry> undo() {
        breakMerge();
        if (cursor == 0) return Optional.empty();
        HistoryEntry e = entries.get(cursor - 1);
        replay(e::undo);
        cursor--;
        update();
        return Optional.of(e);
    }

    /** Redoes the entry after the cursor; the mirror of {@link #undo()}. */
    public Optional<HistoryEntry> redo() {
        breakMerge();
        if (cursor == entries.size()) return Optional.empty();
        HistoryEntry e = entries.get(cursor);
        replay(e::redo);
        cursor++;
        update();
        return Optional.of(e);
    }

    private void replay(Runnable r) {
        replaying = true;
        try {
            r.run();
        } finally {
            replaying = false;
        }
    }

    /** Records that the dictionary was saved in its current state. */
    public void markSaved() {
        breakMerge();
        savePoint = cursor;
        update();
    }

    /* ─── State ─── */

    /** The entries, oldest first (those after {@link #cursor()} can be redone). */
    public List<HistoryEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int cursor() {
        return cursor;
    }

    /** The label of an entry, as shown in the menus. */
    public String label(HistoryEntry e) {
        if (e.cachedLabel != null) return e.cachedLabel;
        String l = labeler.apply(e);
        // Fixed once the entry is complete: later, the objects it names may be gone.
        if (e != open && e != actionEntry) e.cachedLabel = l;
        return l;
    }

    public ReadOnlyBooleanProperty atSavePointProperty() {
        return atSavePoint.getReadOnlyProperty();
    }

    public boolean isAtSavePoint() {
        return atSavePoint.get();
    }

    public ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    /** The label of the entry {@link #undo()} would undo; null when there is none. */
    public ReadOnlyStringProperty undoLabelProperty() {
        return undoLabel.getReadOnlyProperty();
    }

    /** The label of the entry {@link #redo()} would redo; null when there is none. */
    public ReadOnlyStringProperty redoLabelProperty() {
        return redoLabel.getReadOnlyProperty();
    }

    private void update() {
        atSavePoint.set(cursor == savePoint);
        canUndo.set(cursor > 0);
        canRedo.set(cursor < entries.size());
        undoLabel.set(cursor > 0 ? label(entries.get(cursor - 1)) : null);
        redoLabel.set(cursor < entries.size() ? label(entries.get(cursor)) : null);
    }
}
