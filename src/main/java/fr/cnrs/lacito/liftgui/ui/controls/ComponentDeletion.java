package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftgui.ui.I18n;

/**
 * Deletes a component from an editor.
 *
 * The deletion is undoable without anything more: lift-api reports it, with the position
 * of the component, and the application's history records it.
 */
public final class ComponentDeletion {

    private ComponentDeletion() {}

    /**
     * @param remove  removes the component (typically {@code parent.deleteX(x)})
     * @param refresh rebuilds the editor showing the parent; may be null
     */
    public static void delete(Runnable remove, Runnable refresh) {
        try {
            remove.run();
        } catch (IllegalStateException e) {
            // lift-api refuses to delete a component that other components refer to.
            EditorSupport.showError(I18n.get("btn.delete"), I18n.get("error.deleteReferenced", e.getMessage()));
            return;
        }
        if (refresh != null) refresh.run();
    }
}
