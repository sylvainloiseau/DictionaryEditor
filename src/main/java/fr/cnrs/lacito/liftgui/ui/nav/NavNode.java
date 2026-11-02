package fr.cnrs.lacito.liftgui.ui.nav;

import fr.cnrs.lacito.liftapi.model.FeatureSet;

/**
 * What an item of the navigation tree is: a group of items, a view, or a range of the
 * header. The cell draws each kind its own way; a new kind of item is a new record here.
 */
public sealed interface NavNode {

    /** The text of the item. */
    String label();

    /** The view the item opens, or null for a group. */
    ViewRequest request();

    /**
     * A group of items.
     *
     * @param id      a stable name, kept in the preferences when the group is folded
     * @param heading a section heading (Objects, Languages…), not selectable; otherwise a
     *                group nested among the items (Ranges), selectable
     */
    record Group(String id, String label, boolean heading) implements NavNode {
        @Override
        public ViewRequest request() {
            return null;
        }
    }

    /** An item opening a view. */
    record View(ViewKey key, String label) implements NavNode {
        @Override
        public ViewRequest request() {
            return ViewRequest.of(key);
        }
    }

    /** An item opening a range of the header. */
    record Range(FeatureSet range, String label) implements NavNode {
        @Override
        public ViewRequest request() {
            return ViewRequest.range(range.getId());
        }
    }
}
