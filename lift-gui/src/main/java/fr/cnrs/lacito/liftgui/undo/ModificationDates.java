package fr.cnrs.lacito.liftgui.undo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

import fr.cnrs.lacito.liftapi.change.ComponentRemoved;
import fr.cnrs.lacito.liftapi.change.FormAdded;
import fr.cnrs.lacito.liftapi.change.FormRemoved;
import fr.cnrs.lacito.liftapi.change.ModelChange;
import fr.cnrs.lacito.liftapi.change.ModelProperty;
import fr.cnrs.lacito.liftapi.change.ValueChanged;
import fr.cnrs.lacito.liftapi.model.AbstractLiftRoot;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.MultiText;

/**
 * Sets the modification date of the entries a history entry touched: the finisher of the
 * application's {@link History}. The date is changed inside the same history entry, so
 * that undoing it restores the previous date too.
 */
public final class ModificationDates {

    private ModificationDates() {}

    /** Today's date and time, to the second, as LIFT writes it. */
    public static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** Dates every entry of the dictionary that {@code entry} changed. */
    public static void touch(HistoryEntry entry, Supplier<String> now) {
        Set<LiftEntry> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelChange c : entry.changes()) {
            if (c instanceof ValueChanged<?> v && v.property() == ModelProperty.DATE_MODIFIED) continue;
            LiftEntry e = owningEntry(where(c));
            if (e != null && e.getOwningDictionary() != null) touched.add(e);
        }
        if (touched.isEmpty()) return;
        String date = now.get();
        for (LiftEntry e : touched) e.setDateModified(date);
    }

    /** Where a change happened: for a removal, the parent the component was taken from. */
    private static Object where(ModelChange c) {
        return switch (c) {
            case ComponentRemoved r -> r.parent();
            case FormAdded f -> f.text();
            case FormRemoved f -> f.text();
            default -> c.subject();
        };
    }

    /** The entry an object belongs to (a component, a multitext, a form), or null. */
    public static LiftEntry owningEntry(Object o) {
        if (o instanceof Form f) o = f.getMultiText();
        if (o instanceof MultiText m) o = m.getParent();
        for (Object n = o; n instanceof AbstractLiftRoot r; n = r.getParentNode()) {
            if (r instanceof LiftEntry e) return e;
        }
        return null;
    }
}
