package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.DictionaryDocument;
import fr.cnrs.lacito.liftgui.ui.nav.Navigator;
import fr.cnrs.lacito.liftgui.undo.UndoManager;
import fr.cnrs.lacito.liftgui.undo.UndoableCommand;
import java.util.function.BiConsumer;
import javafx.beans.property.StringProperty;

/**
 * What the views and the editor panel share: the document, the editor panel, the navigator,
 * the undo history, the search text and the row-count label.
 */
public final class EditorContext {

    private final DictionaryDocument document;
    private final EditorPanel panel;
    private final Navigator navigator;
    private final UndoManager undoManager;
    private final StringProperty searchText;
    private final BiConsumer<Integer, Integer> countLabel;
    private final EditorPresenters editors;
    private Runnable refreshTable = () -> {};

    /**
     * @param searchText the global search text, followed by the tables
     * @param countLabel shows (rows shown, rows in total) for the current view
     */
    public EditorContext(DictionaryDocument document, EditorPanel panel, Navigator navigator, UndoManager undoManager,
                         StringProperty searchText, BiConsumer<Integer, Integer> countLabel) {
        this.document = document;
        this.panel = panel;
        this.navigator = navigator;
        this.undoManager = undoManager;
        this.searchText = searchText;
        this.countLabel = countLabel;
        this.editors = new EditorPresenters(this);
    }

    public LiftDictionary dictionary() {
        return document.dictionary();
    }

    public DictionaryDocument document() {
        return document;
    }

    public EditorPanel panel() {
        return panel;
    }

    public Navigator navigator() {
        return navigator;
    }

    public EditorPresenters editors() {
        return editors;
    }

    public StringProperty searchText() {
        return searchText;
    }

    public void showCount(int shown, int total) {
        countLabel.accept(shown, total);
    }

    public void markDirty() {
        document.markDirty();
    }

    /** Redraws the cells of the current table (after an edit made in the editor panel). */
    public void refreshTable() {
        refreshTable.run();
    }

    public void setRefreshTable(Runnable r) {
        this.refreshTable = r == null ? () -> {} : r;
    }

    /**
     * Runs a deletion and records it for undo. lift-api refuses to delete an object that
     * other objects refer to: the user is told, and nothing is recorded.
     */
    public void executeDeletion(UndoableCommand cmd) {
        try {
            cmd.redo();
        } catch (IllegalStateException e) {
            Dialogs.error(I18n.get("btn.delete"), I18n.get("error.deleteReferenced", e.getMessage()));
            return;
        }
        undoManager.execute(cmd);
        markDirty();
    }

    /** Records a command that has already been run (deletions made inside the editors). */
    public void recordExecuted(UndoableCommand cmd) {
        undoManager.execute(cmd);
        markDirty();
    }
}
