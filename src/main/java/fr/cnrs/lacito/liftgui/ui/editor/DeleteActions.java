package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.model.HasSense;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.controls.ComponentDeletion;
import fr.cnrs.lacito.liftgui.ui.controls.LiftEntryEditor;
import fr.cnrs.lacito.liftgui.undo.DeleteEntryCommand;
import fr.cnrs.lacito.liftgui.undo.DeleteExampleCommand;
import fr.cnrs.lacito.liftgui.undo.DeleteSenseCommand;
import fr.cnrs.lacito.liftgui.undo.UndoableCommand;
import javafx.scene.control.Button;

/**
 * The "Delete" buttons of the editor panel. Each deletion is confirmed, can be undone, and
 * leaves the panel empty and the current view rebuilt.
 */
public final class DeleteActions {

    private final EditorContext ctx;

    DeleteActions(EditorContext ctx) {
        this.ctx = ctx;
    }

    public Button entry(LiftEntry entry) {
        String label = ObjectLabels.entryLabel(entry);
        return button(I18n.get(Keys.CONFIRM_DELETE_ENTRY, "?".equals(label) ? I18n.get("editor.noForm") : label), () -> {
            int idx = ctx.dictionary().getLiftDictionaryRegistry().getEntries().indexOf(entry);
            ctx.executeDeletion(new DeleteEntryCommand(entry, idx, ctx::dictionary, this::afterChange, this::afterChange));
        });
    }

    public Button sense(LiftSense sense) {
        return button(I18n.get(Keys.CONFIRM_DELETE_SENSE, LiftEntryEditor.senseDisplayText(sense)), () -> {
            HasSense parent = sense.getParent();
            UndoableCommand cmd = new DeleteSenseCommand(sense, parent, parent.getSenses().indexOf(sense),
                this::afterChange, this::afterChange);
            ctx.executeDeletion(cmd);
        });
    }

    public Button example(LiftExample ex) {
        return button(I18n.get("confirm.delete.example"), () -> {
            LiftSense parent = ex.getParent();
            ctx.executeDeletion(new DeleteExampleCommand(ex, parent, parent.getExamples().indexOf(ex),
                this::afterChange, this::afterChange));
        });
    }

    /**
     * Deletes a component shown in its own view (note, variant, relation, etymology).
     *
     * @param remove  removes the component (typically {@code parent.deleteX(x)})
     * @param restore puts it back (typically {@code parent.addX(x)})
     */
    public Button component(Runnable remove, ComponentDeletion.CheckedRunnable restore) {
        return button(I18n.get("confirm.delete.component"),
            () -> ComponentDeletion.deleteChecked(remove, restore, this::afterChange));
    }

    private Button button(String confirmText, Runnable delete) {
        Button b = EditorPanel.deleteButton();
        b.setOnAction(e -> {
            if (Dialogs.confirm(I18n.get(Keys.CONFIRM_DELETE_TITLE), confirmText)) delete.run();
        });
        return b;
    }

    /** After a deletion, its undo or its redo: the object is gone or back, the view is rebuilt. */
    private void afterChange() {
        ctx.panel().clear();
        ctx.navigator().refresh();
    }
}
