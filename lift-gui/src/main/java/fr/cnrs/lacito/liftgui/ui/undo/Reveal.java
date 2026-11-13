package fr.cnrs.lacito.liftgui.ui.undo;

import fr.cnrs.lacito.liftapi.change.ComponentAdded;
import fr.cnrs.lacito.liftapi.change.ComponentRemoved;
import fr.cnrs.lacito.liftapi.change.FormAdded;
import fr.cnrs.lacito.liftapi.change.FormRemoved;
import fr.cnrs.lacito.liftapi.change.ModelChange;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.Feature;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftFieldAndTraitDefinition;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.undo.HistoryEntry;

/**
 * What to show after an undo or a redo: the component it changed, so that the user sees
 * what happened. A form or a text leads to its component; a component the undo took out of
 * the dictionary leads to the parent it was in.
 */
public final class Reveal {

    private Reveal() {}

    /** The component to show for {@code entry}, now that it was undone or redone; null for none. */
    public static Object target(HistoryEntry entry) {
        Object subject = component(entry.subject(), entry);
        if (isPresent(subject)) return subject;
        // Taken out of the dictionary by the undo (or the redo): show where it was.
        for (ModelChange c : entry.changes()) {
            Object parent = switch (c) {
                case ComponentAdded a when a.component() == entry.subject() -> a.parent();
                case ComponentRemoved r when r.component() == entry.subject() -> r.parent();
                default -> null;
            };
            if (parent != null) {
                Object p = component(parent, entry);
                return isPresent(p) ? p : null;
            }
        }
        return isHeader(subject) ? subject : null;
    }

    /** Whether {@code o} belongs to the header (a range, a type, a field definition). */
    public static boolean isHeader(Object o) {
        return o instanceof Feature || o instanceof FeatureSet
            || o instanceof LiftFieldAndTraitDefinition || o instanceof LiftHeader;
    }

    private static Object component(Object o, HistoryEntry entry) {
        if (o instanceof Form f) {
            // A form the undo (or the redo) took out of its text: the event knows the text.
            MultiText text = f.getMultiText();
            for (ModelChange c : entry.changes()) {
                if (text != null) break;
                if (c instanceof FormAdded a && a.form() == f) text = a.text();
                if (c instanceof FormRemoved r && r.form() == f) text = r.text();
            }
            o = text;
        }
        if (o instanceof MultiText m) o = m.getParent();
        return o;
    }

    /** Whether a component is in the dictionary's entries (the views can show it). */
    private static boolean isPresent(Object o) {
        return o instanceof AbstractLiftRoot r && !isHeader(r) && r.getOwningDictionary() != null;
    }
}
