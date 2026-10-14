package fr.cnrs.lacito.liftgui.ui.editor;

import fr.cnrs.lacito.liftapi.model.HasSense;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.ObjectLabels;
import fr.cnrs.lacito.liftgui.ui.controls.LiftEntryEditor;
import javafx.scene.control.Button;

/**
 * The "Delete" buttons of the editor panel. Each deletion is confirmed, is one undo step,
 * and leaves the panel empty and the current view rebuilt.
 */
public final class DeleteActions {

    private final EditorContext ctx;

    DeleteActions(EditorContext ctx) {
        this.ctx = ctx;
    }

    public Button entry(LiftEntry entry) {
        String label = ObjectLabels.entryLabel(entry);
        String shown = "?".equals(label) ? I18n.get("editor.noForm") : label;
        return button(I18n.get(Keys.CONFIRM_DELETE_ENTRY, shown), () -> {
            if (ctx.delete(I18n.get("undo.deleteEntry", shown), entry, () -> ctx.dictionary().removeEntry(entry))) afterChange();
        });
    }

    public Button sense(LiftSense sense) {
        String shown = LiftEntryEditor.senseDisplayText(sense);
        return button(I18n.get(Keys.CONFIRM_DELETE_SENSE, shown), () -> {
            HasSense parent = sense.getParent();
            if (ctx.delete(I18n.get("undo.deleteSense", shown), sense, () -> parent.deleteSense(sense))) afterChange();
        });
    }

    public Button example(LiftExample ex) {
        return button(I18n.get("confirm.delete.example"), () -> {
            LiftSense parent = ex.getParent();
            if (ctx.delete(I18n.get("undo.deleteExample"), ex, () -> parent.deleteExample(ex))) afterChange();
        });
    }

    /**
     * Deletes a component shown in its own view (note, variant, relation, etymology).
     *
     * @param component the component
     * @param remove    removes it (typically {@code parent.deleteX(x)})
     */
    public Button component(Object component, Runnable remove) {
        return button(I18n.get("confirm.delete.component"), () -> {
            if (ctx.delete(null, component, remove)) afterChange();
        });
    }

    private Button button(String confirmText, Runnable delete) {
        Button b = EditorPanel.deleteButton();
        b.setOnAction(e -> {
            if (Dialogs.confirm(I18n.get(Keys.CONFIRM_DELETE_TITLE), confirmText)) delete.run();
        });
        return b;
    }

    /** After a deletion: the object is gone, the view is rebuilt. */
    private void afterChange() {
        ctx.panel().clear();
        ctx.navigator().refresh();
    }
}
