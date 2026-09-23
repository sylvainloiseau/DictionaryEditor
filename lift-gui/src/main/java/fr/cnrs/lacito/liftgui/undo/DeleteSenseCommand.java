package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.HasSense;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import java.util.function.Supplier;

/**
 * Commande de suppression d'un sens, annulable via Undo.
 */
public final class DeleteSenseCommand implements UndoableCommand {
    private final LiftSense sense;
    private final HasSense parent;
    private final int parentIndex;
    private final Supplier<LiftDictionary> dictionarySupplier;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteSenseCommand(LiftSense sense, HasSense parent, int parentIndex,
                              Supplier<LiftDictionary> dictionarySupplier,
                              Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.sense = sense;
        this.parent = parent;
        this.parentIndex = parentIndex;
        this.dictionarySupplier = dictionarySupplier;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        // Wire the sense back at its original position first, then register: the
        // registry refuses to adopt a component that is not attached to its parent,
        // because nothing would ever be able to reach it again. The entry the sense
        // belongs to is derived from that link, so there is nothing else to restore.
        sense.setParent(parent);
        parent.getSenses().add(Math.min(parentIndex, parent.getSenses().size()), sense);
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) {
            dictionary.getLiftDictionaryRegistry().addToDictionaryLowLevel(sense);
        }
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        // removeFromDictionary() detaches as well as unregisters; unlinking again by
        // index here would take a different sense out of the list.
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) {
            dictionary.getLiftDictionaryRegistry().removeFromDictionary(sense);
        } else {
            sense.detach();
        }
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
