package fr.cnrs.lacito.liftgui.undo;

/**
 * Commande de suppression générique d'un composant (variante, note, trait…), annulable via Undo.
 *
 * The component is removed and restored through its parent's {@code deleteX} / {@code addX}
 * methods, which take care of unregistering and registering it in the dictionary.
 */
public final class DeleteComponentCommand implements UndoableCommand {

    private final Runnable remove;
    private final Runnable restore;
    private final Runnable refresh;

    public DeleteComponentCommand(Runnable remove, Runnable restore, Runnable refresh) {
        this.remove = remove;
        this.restore = restore;
        this.refresh = refresh;
    }

    @Override
    public void undo() {
        restore.run();
        if (refresh != null) refresh.run();
    }

    @Override
    public void redo() {
        remove.run();
        if (refresh != null) refresh.run();
    }
}
