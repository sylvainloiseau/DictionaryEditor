package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftEntry;

import java.util.function.Supplier;

/**
 * Commande de suppression d'une entrée, annulable via Undo.
 */
public final class DeleteEntryCommand implements UndoableCommand {
    private final LiftEntry entry;
    private final int baseEntriesIndex;
    private final Supplier<LiftDictionary> dictionarySupplier;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteEntryCommand(LiftEntry entry, int baseEntriesIndex,
                              Supplier<LiftDictionary> dictionarySupplier,
                              Runnable onUndoRefresh,
                              Runnable onRedoRefresh) {
        this.entry = entry;
        this.baseEntriesIndex = baseEntriesIndex;
        this.dictionarySupplier = dictionarySupplier;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) {
            dictionary.addEntry(entry, baseEntriesIndex);
        }
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) {
            dictionary.removeEntry(entry);
        }
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
