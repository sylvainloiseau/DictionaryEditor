package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftSense;

/**
 * Commande de suppression d'un exemple, annulable via Undo.
 *
 * The dictionary does not appear here any more: {@code deleteExample} and
 * {@code addExample} take care of unregistering and registering the example when the
 * sense belongs to a dictionary, and do only the wiring when it does not.
 */
public final class DeleteExampleCommand implements UndoableCommand {
    private final LiftExample example;
    private final LiftSense parent;
    private final int parentIndex;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteExampleCommand(LiftExample example, LiftSense parent, int parentIndex,
                                Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.example = example;
        this.parent = parent;
        this.parentIndex = parentIndex;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        parent.addExample(
            Math.min(parentIndex, parent.getExamples().size()),
            example
        );
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        parent.deleteExample(example);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
