package fr.cnrs.lacito.liftfx.internal; 

import fr.cnrs.lacito.liftfx.ComponentKind;
import fr.cnrs.lacito.liftfx.EditActions;


/**
 * Deletes a component from an editor.
 *
 * The deletion is undoable without anything more: lift-api reports it, with the position
 * of the component, and the application's history records it. So only the main
 * components (sense, example, variant, etymology, reversal) ask first, and only when they
 * are not empty (see {@link #needsConfirmation}).
 */
public final class ComponentDeletion {

    private ComponentDeletion() {}

    /**
     * @param component the component deleted
     * @param remove    removes the component (typically {@code parent.deleteX(x)})
     * @param refresh   rebuilds the editor showing the parent; may be null
     * @return whether the component was deleted
     */
    public static boolean delete(Object component, Runnable remove, Runnable refresh) {
        if (needsConfirmation(component)
            && !Dialogs.confirm(Messages.get("confirm.delete.title"), Messages.get("confirm.delete.component"))) {
            return false;
        }
        try {
            remove.run();
        } catch (IllegalStateException e) {
            // lift-api refuses to delete a component that other components refer to.
            EditorSupport.showError(Messages.get("btn.delete"), Messages.get("error.deleteReferenced", e.getMessage()));
            return false;
        }
        if (refresh != null) refresh.run();
        return true;
    }

    /** Whether deleting a component asks first: a main component that is not empty. */
    public static boolean needsConfirmation(Object component) {
        ComponentKind kind = ComponentKind.of(component);
        return kind != null && kind.confirmsDeletion() && !EditActions.isEmpty(component);
    }
}
