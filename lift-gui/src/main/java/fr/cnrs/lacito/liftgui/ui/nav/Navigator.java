package fr.cnrs.lacito.liftgui.ui.nav;

import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftgui.core.DictionaryQueries;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Moves between views. The host (the main controller) shows each request; the navigator
 * knows how to reach a given object.
 */
public final class Navigator {

    private final Consumer<ViewRequest> host;
    private final Supplier<List<LiftSense>> allSenses;
    private ViewRequest current = ViewRequest.of(ViewKey.ENTRIES);

    /**
     * @param host      shows a request
     * @param allSenses every sense of the dictionary (to list the senses of an entry)
     */
    public Navigator(Consumer<ViewRequest> host, Supplier<List<LiftSense>> allSenses) {
        this.host = host;
        this.allSenses = allSenses;
    }

    public ViewRequest current() {
        return current;
    }

    public void go(ViewRequest request) {
        current = request;
        host.accept(request);
    }

    public void go(ViewKey key) {
        go(ViewRequest.of(key));
    }

    /** Rebuilds the current view (e.g. after a deletion or an undo), keeping its subset. */
    public void refresh() {
        go(current.withoutSelection());
    }

    /** Shows the entry an object belongs to in the Entries view. */
    public void goToEntryOf(Object object) {
        LiftEntry entry = DictionaryQueries.owningEntry(object);
        if (entry != null) go(ViewRequest.of(ViewKey.ENTRIES).selecting(entry));
    }

    public void goToSense(LiftSense sense) {
        if (sense != null) go(ViewRequest.of(ViewKey.SENSES).selecting(sense));
    }

    /** The Senses view restricted to the senses (and sub-senses) of the sense's entry. */
    public void goToSenseAmongEntrySenses(LiftSense sense) {
        if (sense == null) return;
        LiftEntry entry = sense.getParentEntry();
        List<LiftSense> senses = allSenses.get().stream().filter(s -> s.getParentEntry() == entry).toList();
        go(ViewRequest.subset(ViewKey.SENSES, senses, null).selecting(sense));
    }

    public void goToExample(LiftExample example) {
        if (example != null) go(ViewRequest.of(ViewKey.EXAMPLES).selecting(example));
    }
}
