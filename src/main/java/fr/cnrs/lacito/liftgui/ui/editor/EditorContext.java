package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.DictionaryDocument;
import fr.cnrs.lacito.liftgui.ui.nav.Navigator;
import fr.cnrs.lacito.liftgui.undo.History;
import java.util.function.BiConsumer;
import javafx.beans.property.StringProperty;

/**
 * What the views and the editor panel share: the document, the editor panel, the navigator,
 * the undo history (through the document), the search text and the row-count label.
 */
public final class EditorContext {

    private final DictionaryDocument document;
    private final EditorPanel panel;
    private final Navigator navigator;
    private final StringProperty searchText;
    private final BiConsumer<Integer, Integer> countLabel;
    private final EditorPresenters editors;
    private Runnable refreshTable = () -> {};

    /**
     * @param searchText the global search text, followed by the tables
     * @param countLabel shows (rows shown, rows in total) for the current view
     */
    public EditorContext(DictionaryDocument document, EditorPanel panel, Navigator navigator,
                         StringProperty searchText, BiConsumer<Integer, Integer> countLabel) {
        this.document = document;
        this.panel = panel;
        this.navigator = navigator;
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

    /** The undo history of the document. */
    public History history() {
        return document.history();
    }

    /**
     * Runs a user gesture as one undo step labelled {@code label} (see {@link History#perform}).
     *
     * @param subject what it is about, shown again after an undo; null for the first change's
     */
    public void perform(String label, Object subject, Runnable gesture) {
        document.history().perform(label, subject, gesture);
    }

    /** Redraws the cells of the current table (after an edit made in the editor panel). */
    public void refreshTable() {
        refreshTable.run();
    }

    public void setRefreshTable(Runnable r) {
        this.refreshTable = r == null ? () -> {} : r;
    }

    /**
     * Runs a deletion as one undo step. lift-api refuses to delete an object that other
     * objects refer to: the user is told, and nothing is recorded.
     *
     * @return whether the object was deleted
     */
    public boolean delete(String label, Object subject, Runnable remove) {
        try {
            perform(label, subject, remove);
            return true;
        } catch (IllegalStateException e) {
            Dialogs.error(I18n.get("btn.delete"), I18n.get("error.deleteReferenced", e.getMessage()));
            return false;
        }
    }
}
