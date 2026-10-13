package fr.cnrs.lacito.liftgui.ui.nav;

import java.util.List;

/**
 * What to show: a view, optionally restricted to a subset of its objects (with a title
 * saying why), and optionally an object to select.
 *
 * @param key     the view
 * @param rangeId the header range shown, for {@link ViewKey#RANGE}; null otherwise
 * @param subset  the objects listed instead of all the objects of the view; null for all
 * @param title   the title of the view when it shows a subset; null for the view's name
 * @param select  the object to select and show in the editor panel; may be null
 */
public record ViewRequest(ViewKey key, String rangeId, List<?> subset, String title, Object select) {

    public static ViewRequest of(ViewKey key) {
        return new ViewRequest(key, null, null, null, null);
    }

    public static ViewRequest range(String rangeId) {
        return new ViewRequest(ViewKey.RANGE, rangeId, null, null, null);
    }

    /** The objects of {@code subset} only, under {@code title}. */
    public static ViewRequest subset(ViewKey key, List<?> subset, String title) {
        return new ViewRequest(key, null, List.copyOf(subset), title, null);
    }

    /** The same request, selecting {@code object}. */
    public ViewRequest selecting(Object object) {
        return new ViewRequest(key, rangeId, subset, title, object);
    }

    /** The same request without the object to select (to rebuild the view as it is). */
    public ViewRequest withoutSelection() {
        return new ViewRequest(key, rangeId, subset, title, null);
    }
}
