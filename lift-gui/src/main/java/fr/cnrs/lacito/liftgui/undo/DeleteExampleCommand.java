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
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) dictionary.getLiftDictionaryRegistry().addToDictionaryLowLevel(example);
        parent.getExamples().add(Math.min(parentIndex, parent.getExamples().size()), example);
        example.setParent(parent);
        if (onUndoRefresh != null) onUndoRefresh.run();
    }

    @Override
    public void redo() {
        LiftDictionary dictionary = dictionarySupplier.get();
        if (dictionary != null) dictionary.getLiftDictionaryRegistry().removeFromDictionary(example);
        parent.getExamples().remove(example);
        example.setParent((LiftSense)parent);
        if (onRedoRefresh != null) onRedoRefresh.run();
    }
}
