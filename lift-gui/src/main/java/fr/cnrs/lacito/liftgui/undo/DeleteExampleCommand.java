package fr.cnrs.lacito.liftgui.undo;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftSense;

import java.util.function.Supplier;

/**
 * Commande de suppression d'un exemple, annulable via Undo.
 */
public final class DeleteExampleCommand implements UndoableCommand {
    private final LiftExample example;
    private final LiftSense parent;
    private final int parentIndex;
    private final Supplier<LiftDictionary> dictionarySupplier;
    private final Runnable onUndoRefresh;
    private final Runnable onRedoRefresh;

    public DeleteExampleCommand(LiftExample example, LiftSense parent, int parentIndex,
                                Supplier<LiftDictionary> dictionarySupplier,
                                Runnable onUndoRefresh, Runnable onRedoRefresh) {
        this.example = example;
        this.parent = parent;
        this.parentIndex = parentIndex;
        this.dictionarySupplier = dictionarySupplier;
        this.onUndoRefresh = onUndoRefresh;
        this.onRedoRefresh = onRedoRefresh;
    }

    @Override
    public void undo() {
        // Wire first, then register: the registry refuses a component that is not
        // attached to its parent, since no traversal could reach it afterwards.
        parent.getExamples().add(Math.min(parentIndex, parent.getExamples().size()), example);
        example.setParent(parent);
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) {
            dictionary.getLiftDictionaryRegistry().addToDictionaryLowLevel(example);
        }
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        // removeFromDictionary() unlinks the example from its sense as well as
        // unregistering it, and leaving the parent reference set afterwards made the
        // deleted example still claim to be part of the entry.
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) {
            dictionary.getLiftDictionaryRegistry().removeFromDictionary(example);
        } else {
            example.detach();
        }
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
