package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.model.HasSense;
import fr.cnrs.lacito.liftapi.model.LiftSense;

/**
 * Commande de suppression d'un sens, annulable via Undo.
 *
 * The dictionary does not appear here any more: {@code deleteSense} and {@code addSense}
 * take care of unregistering and registering the sense - and everything under it - when
 * the parent belongs to a dictionary, and do only the wiring when it does not.
 */
public final class DeleteSenseCommand implements UndoableCommand {
    private final LiftSense sense;
    private final HasSense parent;
    private final int parentIndex;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteSenseCommand(LiftSense sense, HasSense parent, int parentIndex,
                              Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.sense = sense;
        this.parent = parent;
        this.parentIndex = parentIndex;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        parent.addSense(
            Math.min(parentIndex, parent.getSenses().size()),
            sense
        );
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        parent.deleteSense(sense);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
