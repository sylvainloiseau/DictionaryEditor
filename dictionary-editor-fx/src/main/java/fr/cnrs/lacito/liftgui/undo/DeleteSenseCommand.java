package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.HasSense;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import java.util.function.Supplier;

/**
 * Commande de suppression d'un sens, annulable via Undo.
 */
public final class DeleteSenseCommand implements UndoableCommand {
    private final LiftSense sense;
    private final HasSense parent;
    private final LiftEntry parentEntry;
    private final int parentIndex;
    private final Supplier<LiftDictionary> dictionarySupplier;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteSenseCommand(LiftSense sense, HasSense parent, LiftEntry parentEntry, int parentIndex,
                              Supplier<LiftDictionary> dictionarySupplier,
                              Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.sense = sense;
        this.parent = parent;
        this.parentEntry = parentEntry;
        this.parentIndex = parentIndex;
        this.dictionarySupplier = dictionarySupplier;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) dictionary.getLiftDictionaryRegistry().addToDictionaryLowLevel(sense);
        sense.setParentEntry(parentEntry);
        sense.setParent(parent);
        parent.getSenses().add(parentIndex, sense);
        //parentList.add(Math.min(parentIndex, parentList.size()), sense);
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) dictionary.getLiftDictionaryRegistry().removeFromDictionary(sense);
        parent.getSenses().remove(parentIndex);
        sense.setParent(null);
        sense.setParentEntry(null);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
