package fr.cnrs.lacito.liftgui.undo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.cnrs.lacito.liftapi.change.ModelChange;

/**
 * One undo step: the changes of one user action, of one typing session in a field, or of
 * one call that changed the dictionary outside both.
 *
 * It refers to the model's objects through lift-api's change events, never through ids, so
 * renaming an entry cannot break it.
 */
public final class HistoryEntry {

    /** What made the entry: it decides how changes are merged into it. */
    public enum Kind {
        /** A user action, opened with {@link History#perform}. */
        ACTION,
        /** The changes typed in one field while it had the focus. */
        TYPING,
        /** Changes made outside an action and outside typing, during one JavaFX event. */
        CHANGE
    }

    private final Kind kind;
    private final Object mergeKey;
    private final String actionLabel;
    private final Object actionSubject;
    private final List<ModelChange> changes = new ArrayList<>();
    /** The label, once the entry no longer changes. */
    String cachedLabel;

    HistoryEntry(Kind kind, Object mergeKey, String actionLabel, Object actionSubject) {
        this.kind = kind;
        this.mergeKey = mergeKey;
        this.actionLabel = actionLabel;
        this.actionSubject = actionSubject;
    }

    public Kind kind() {
        return kind;
    }

    /** For a typing entry, the field typed in; otherwise null. */
    Object mergeKey() {
        return mergeKey;
    }

    /** The label of the action, or null for an entry labelled from its changes. */
    public String actionLabel() {
        return actionLabel;
    }

    /** What the entry is about: the action's subject, or the subject of its first change. */
    public Object subject() {
        if (actionSubject != null) return actionSubject;
        return changes.isEmpty() ? null : changes.getFirst().subject();
    }

    /** The changes, in the order they were made. */
    public List<ModelChange> changes() {
        return Collections.unmodifiableList(changes);
    }

    void add(ModelChange change) {
        changes.add(change);
    }

    /**
     * Reverts the changes, last first. If lift-api refuses one, the changes already
     * reverted are reapplied, so that the dictionary is left as it was, and the error is
     * rethrown.
     */
    void undo() {
        int i = changes.size() - 1;
        try {
            for (; i >= 0; i--) changes.get(i).revert();
        } catch (RuntimeException e) {
            for (int k = i + 1; k < changes.size(); k++) changes.get(k).reapply();
            throw e;
        }
    }

    /** Reapplies the changes, first first; the mirror of {@link #undo()}. */
    void redo() {
        int i = 0;
        try {
            for (; i < changes.size(); i++) changes.get(i).reapply();
        } catch (RuntimeException e) {
            for (int k = i - 1; k >= 0; k--) changes.get(k).revert();
            throw e;
        }
    }

    /** Reverts everything, for a failed action; errors are added to {@code failure}. */
    void revertQuietly(RuntimeException failure) {
        for (int i = changes.size() - 1; i >= 0; i--) {
            try {
                changes.get(i).revert();
            } catch (RuntimeException e) {
                failure.addSuppressed(e);
            }
        }
    }

    @Override
    public String toString() {
        return kind + (actionLabel == null ? "" : " '" + actionLabel + "'") + " " + changes;
    }
}
