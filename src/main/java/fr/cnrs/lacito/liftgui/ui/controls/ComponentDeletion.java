package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.undo.DeleteComponentCommand;
import fr.cnrs.lacito.liftgui.undo.UndoableCommand;
import java.util.function.Consumer;

/**
 * Deletes a component from an editor so that the deletion can be undone.
 *
 * The editors of this package do not know the application's undo manager: the host
 * registers it once with {@link #setUndoSink(Consumer)}. Without a sink, deletions
 * are simply not undoable.
 */
public final class ComponentDeletion {

    private static Consumer<UndoableCommand> undoSink = cmd -> {};

    private ComponentDeletion() {}

    public static void setUndoSink(Consumer<UndoableCommand> sink) {
        undoSink = sink == null ? cmd -> {} : sink;
    }

    /**
     * @param remove  removes the component (typically {@code parent.deleteX(x)})
     * @param restore puts it back (typically {@code parent.addX(x)})
     * @param refresh rebuilds the editor showing the parent; may be null
     */
    public static void delete(Runnable remove, Runnable restore, Runnable refresh) {
        DeleteComponentCommand cmd = new DeleteComponentCommand(remove, restore, refresh);
        try {
            cmd.redo();
        } catch (IllegalStateException e) {
            // lift-api refuses to delete a component that other components refer to.
            EditorSupport.showError(I18n.get("btn.delete"), I18n.get("error.deleteReferenced", e.getMessage()));
            return;
        }
        undoSink.accept(cmd);
    }

    /** Same as {@link #delete} for a restore action that may throw a checked exception. */
    public static void deleteChecked(Runnable remove, CheckedRunnable restore, Runnable refresh) {
        delete(remove, () -> {
            try {
                restore.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, refresh);
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}
