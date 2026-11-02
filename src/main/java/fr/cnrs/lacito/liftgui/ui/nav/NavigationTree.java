package fr.cnrs.lacito.liftgui.ui.nav;

import static fr.cnrs.lacito.liftgui.ui.nav.ViewKey.*;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.FeatureSet;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftHeader;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.Keys;
import fr.cnrs.lacito.liftgui.ui.view.CategoryViews;
import fr.cnrs.lacito.liftgui.ui.view.ComponentKinds;
import java.text.Collator;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

/**
 * The navigation panel on the left: a filter field over a tree of section headings
 * (objects, languages, categories, dictionary configuration with one item per header range)
 * and the quick entry. Each item has an icon and, where it means something, the number of
 * rows of its view.
 */
public final class NavigationTree {

    /** The ids of the groups, kept in the preferences when folded. */
    public static final String OBJECTS = "objects";
    public static final String LANGUAGES = "languages";
    public static final String CATEGORIES = "categories";
    public static final String CONFIGURATION = "configuration";
    public static final String RANGES = "ranges";

    private static final PseudoClass HEADING = PseudoClass.getPseudoClass("heading");
    private static final PseudoClass NESTED_GROUP = PseudoClass.getPseudoClass("nested-group");
    private static final PseudoClass RANGE_ITEM = PseudoClass.getPseudoClass("range");
    private static final PseudoClass ACTION = PseudoClass.getPseudoClass("action");
    private static final PseudoClass FOLDED = PseudoClass.getPseudoClass("folded");
    private static final PseudoClass UNDECLARED = PseudoClass.getPseudoClass("undeclared");
    /** The horizontal paddings of a cell, which its graphic spans the rest of. */
    private static final double CELL_INSETS = 14;

    private final TreeView<NavNode> tree;
    private final TextField filter;
    private final Consumer<ViewRequest> onSelect;
    private final Runnable onNewEntry;
    private final Function<ViewKey, KeyCombination> shortcut;
    private final Consumer<Set<String>> onFoldedChanged;

    /** The top-level items, then for each group its full list of items; the tree shows them filtered. */
    private final List<TreeItem<NavNode>> topLevel = new ArrayList<>();
    private final Map<TreeItem<NavNode>, List<TreeItem<NavNode>>> children = new LinkedHashMap<>();
    private final TreeItem<NavNode> rangesItem = new TreeItem<>(new NavNode.Group(RANGES, I18n.get(Keys.NAV_CFG_RANGES), false));
    private final Set<String> folded;

    /** The counts of the items, by view key, by range, or for the ranges group. */
    private final Map<Object, ObservableIntegerValue> counts = new HashMap<>();
    /** The counts of the category views, recomputed a moment after a change. */
    private final Map<ViewKey, SimpleIntegerProperty> categoryCounts = new HashMap<>();
    private final PauseTransition recount = new PauseTransition(Duration.millis(250));
    private LiftDictionary dictionary;

    /** Re-sorts the ranges when one is added, removed, renamed or declared; held here, the models hold it weakly. */
    private final InvalidationListener rangesChanged = o -> Platform.runLater(this::listRanges);
    private boolean ignoreSelection;
    private boolean filtering;
    /** The request of the view shown, as last given to {@link #select(ViewRequest)}. */
    private ViewRequest shown;

    /**
     * @param filter          the field filtering the items
     * @param onSelect        called when the user selects a view
     * @param onNewEntry      called by "New entry" in the context menu of Entries
     * @param shortcut        the keys of a view's command, or null
     * @param initiallyFolded the ids of the groups folded
     * @param onFoldedChanged called with the ids of the groups folded when they change
     */
    public NavigationTree(TreeView<NavNode> tree, TextField filter, Consumer<ViewRequest> onSelect, Runnable onNewEntry,
                          Function<ViewKey, KeyCombination> shortcut, Set<String> initiallyFolded,
                          Consumer<Set<String>> onFoldedChanged) {
        this.tree = tree;
        this.filter = filter;
        this.onSelect = onSelect;
        this.onNewEntry = onNewEntry;
        this.shortcut = shortcut;
        this.folded = new HashSet<>(initiallyFolded);
        this.onFoldedChanged = onFoldedChanged;

        // One table per kind of component, in the order of the model (see ComponentKinds).
        group(OBJECTS, Keys.NAV_OBJECTS, ComponentKinds.keys());
        group(LANGUAGES, Keys.NAV_LANGUAGES, OBJECT_LANGUAGES, META_LANGUAGES);
        group(CATEGORIES, Keys.NAV_CATEGORIES, GRAM_INFO, TRAIT_VALUES, TRANSLATION_TYPES, NOTE_TYPES, RELATION_TYPES, FIELD_TYPES);
        TreeItem<NavNode> configuration = group(CONFIGURATION, Keys.NAV_HEADER_CONFIG, CFG_DESCRIPTION, CFG_FIELD_DEFINITIONS,
            CFG_LANGUAGES, CFG_NOTE_TYPES, CFG_TRANSLATION_TYPES, CFG_ANNOTATION_TYPES, CFG_RELATION_TYPES);
        children.get(configuration).add(rangesItem);
        children.put(rangesItem, new ArrayList<>());
        topLevel.add(item(QUICK_ENTRY));
        for (TreeItem<NavNode> g : children.keySet()) {
            String id = ((NavNode.Group) g.getValue()).id();
            g.setExpanded(!folded.contains(id));
            g.expandedProperty().addListener((obs, o, expanded) -> {
                tree.refresh();
                if (filtering) return;
                if (expanded) folded.remove(id);
                else folded.add(id);
                onFoldedChanged.accept(Set.copyOf(folded));
            });
        }

        tree.setRoot(new TreeItem<>(new NavNode.Group("root", I18n.get(Keys.NAV_DICTIONARY), true)));
        tree.getRoot().setExpanded(true);
        tree.setShowRoot(false);
        tree.setAccessibleText(I18n.get("panel.navigation"));
        show("");

        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            if (n.getValue() instanceof NavNode.Group g && g.heading()) {
                // A heading is not selectable: the selection goes on in the direction it came from.
                int row = tree.getRow(n);
                int dir = o == null || tree.getRow(o) <= row ? 1 : -1;
                Platform.runLater(() -> selectItemAround(row, dir));
                return;
            }
            if (ignoreSelection || n.getValue().request() == null) return;
            onSelect.accept(n.getValue().request());
        });
        tree.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        tree.setCellFactory(tv -> new NavCell());

        filter.textProperty().addListener((obs, o, text) -> show(text));
        filter.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                filter.clear();
                tree.requestFocus();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                firstMatch().ifPresent(item -> tree.getSelectionModel().select(item));
                tree.requestFocus();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                firstMatch().ifPresent(item -> {
                    ignoreSelection = true;
                    try {
                        tree.getSelectionModel().select(item);
                    } finally {
                        ignoreSelection = false;
                    }
                });
                tree.requestFocus();
                e.consume();
            }
        });

        recount.setOnFinished(e -> countCategories());
    }

    private TreeItem<NavNode> group(String id, String i18nKey, ViewKey... keys) {
        TreeItem<NavNode> g = new TreeItem<>(new NavNode.Group(id, I18n.get(i18nKey), true));
        List<TreeItem<NavNode>> items = new ArrayList<>();
        for (ViewKey k : keys) items.add(item(k));
        children.put(g, items);
        topLevel.add(g);
        return g;
    }

    private static TreeItem<NavNode> item(ViewKey key) {
        return new TreeItem<>(new NavNode.View(key, I18n.get(key.i18nKey())));
    }

    /* ─── The dictionary: ranges and counts ─── */

    /** Shows the ranges and the counts of a dictionary (none when {@code d} is null). */
    public void setDictionary(LiftDictionary d) {
        dictionary = d;
        counts.clear();
        categoryCounts.clear();
        if (d != null) {
            var reg = d.getLiftDictionaryRegistry();
            LiftHeader h = d.getHeader();
            for (var kind : ComponentKinds.all()) counts.put(kind.key(), Bindings.size(kind.rows().apply(reg)));
            var objectLangs = d.getObjectLanguageManager().getLanguages();
            var metaLangs = d.getMetaLanguageManager().getLanguages();
            counts.put(OBJECT_LANGUAGES, Bindings.size(objectLangs));
            counts.put(META_LANGUAGES, Bindings.size(metaLangs));
            counts.put(CFG_LANGUAGES, Bindings.createIntegerBinding(() -> {
                Set<String> all = new HashSet<>(objectLangs);
                all.addAll(metaLangs);
                return all.size();
            }, objectLangs, metaLangs));
            if (h != null) {
                counts.put(CFG_NOTE_TYPES, Bindings.size(h.getNoteTypeManager().getFeatures()));
                counts.put(CFG_TRANSLATION_TYPES, Bindings.size(h.getTranslationTypeManager().getFeatures()));
                counts.put(CFG_ANNOTATION_TYPES, Bindings.size(h.getAnnotationTypeManager().getFeatures()));
                counts.put(CFG_RELATION_TYPES, Bindings.size(h.getRelationTypeManager().getFeatures()));
                counts.put(RANGES, Bindings.size(h.getFeatureSets()));
                h.getFeatureSets().addListener(new WeakInvalidationListener(rangesChanged));
            }
            for (ViewKey k : new ViewKey[] {GRAM_INFO, TRAIT_VALUES, TRANSLATION_TYPES, NOTE_TYPES, RELATION_TYPES, FIELD_TYPES}) {
                SimpleIntegerProperty p = new SimpleIntegerProperty();
                categoryCounts.put(k, p);
                counts.put(k, p);
            }
            countCategories();
        }
        listRanges();
    }

    /** The dictionary changed: the counts of the categories are recomputed a moment later. */
    public void dictionaryChanged() {
        if (!categoryCounts.isEmpty()) recount.playFromStart();
    }

    private void countCategories() {
        if (dictionary == null) return;
        categoryCounts.forEach((k, p) -> p.set(CategoryViews.rowCount(k, dictionary.getLiftDictionaryRegistry())));
    }

    /** The ranges of the header, A→Z by their label. */
    private void listRanges() {
        List<TreeItem<NavNode>> items = children.get(rangesItem);
        items.clear();
        LiftHeader h = dictionary == null ? null : dictionary.getHeader();
        if (h != null) {
            Collator collator = Collator.getInstance(I18n.getLocale());
            collator.setStrength(Collator.PRIMARY);
            List<NavNode.Range> ranges = new ArrayList<>();
            for (FeatureSet range : h.getFeatureSets()) {
                ranges.add(new NavNode.Range(range, rangeLabel(range, dictionary)));
                counts.put(range, Bindings.size(range.getFeatures()));
                WeakInvalidationListener weak = new WeakInvalidationListener(rangesChanged);
                range.declaredProperty().addListener(weak);
                range.idProperty().addListener(weak);
                range.getLabel().formsProperty().addListener(weak);
            }
            ranges.sort(Comparator.comparing(NavNode.Range::label, collator)
                .thenComparing(r -> r.range().getId()));
            for (NavNode.Range r : ranges) items.add(new TreeItem<>(r));
        }
        show(filter.getText());
        if (shown != null && shown.key() == RANGE) select(shown);
    }

    /**
     * The label of a range: in the language of the interface, else in the first
     * meta-language, else its first label, else its id.
     */
    static String rangeLabel(FeatureSet range, LiftDictionary d) {
        List<String> langs = new ArrayList<>();
        langs.add(I18n.getLocale().getLanguage());
        if (d != null) langs.addAll(d.getMetaLanguageManager().getLanguages());
        for (String lang : langs) {
            String text = range.getLabel().getForm(lang).map(Form::toPlainText).orElse("");
            if (!text.isBlank()) return text;
        }
        return range.getLabel().getForms().stream().map(Form::toPlainText).filter(t -> !t.isBlank())
            .findFirst().orElse(range.getId());
    }

    /* ─── Filtering ─── */

    /** Shows the items whose label contains {@code text}, with their groups; all of them when it is blank. */
    private void show(String text) {
        String q = normalize(text);
        filtering = !q.isEmpty();
        List<TreeItem<NavNode>> top = new ArrayList<>();
        for (TreeItem<NavNode> item : topLevel) {
            if (keep(item, q)) top.add(item);
        }
        ignoreSelection = true;
        try {
            tree.getRoot().getChildren().setAll(top);
            if (filtering) {
                children.keySet().forEach(g -> g.setExpanded(true));
            } else {
                children.keySet().forEach(g -> g.setExpanded(!folded.contains(((NavNode.Group) g.getValue()).id())));
            }
        } finally {
            ignoreSelection = false;
        }
        if (!filtering && shown != null && tree.getSelectionModel().getSelectedItem() == null) select(shown);
        tree.refresh();
    }

    /** Whether an item stays, setting the children of a group to those that stay. */
    private boolean keep(TreeItem<NavNode> item, String q) {
        boolean matches = q.isEmpty() || normalize(item.getValue().label()).contains(q);
        List<TreeItem<NavNode>> all = children.get(item);
        if (all == null) return matches;
        List<TreeItem<NavNode>> kept = new ArrayList<>();
        for (TreeItem<NavNode> child : all) {
            // A group that matches keeps all its items.
            if (keep(child, matches ? "" : q)) kept.add(child);
        }
        item.getChildren().setAll(kept);
        return matches || !kept.isEmpty();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** The first item that opens a view, in the order of the tree as filtered. */
    private java.util.Optional<TreeItem<NavNode>> firstMatch() {
        for (int row = 0; row < tree.getExpandedItemCount(); row++) {
            TreeItem<NavNode> item = tree.getTreeItem(row);
            if (item != null && item.getValue().request() != null) return java.util.Optional.of(item);
        }
        return java.util.Optional.empty();
    }

    /* ─── Selection and keys ─── */

    private void selectItemAround(int row, int dir) {
        for (int d : new int[] {dir, -dir}) {
            for (int r = row + d; r >= 0 && r < tree.getExpandedItemCount(); r += d) {
                TreeItem<NavNode> item = tree.getTreeItem(r);
                if (item != null && !(item.getValue() instanceof NavNode.Group g && g.heading())) {
                    tree.getSelectionModel().select(item);
                    tree.getFocusModel().focus(r);
                    return;
                }
            }
        }
        tree.getSelectionModel().clearSelection();
    }

    private void onKey(KeyEvent e) {
        TreeItem<NavNode> item = tree.getSelectionModel().getSelectedItem();
        if (item == null) return;
        boolean group = children.containsKey(item);
        if (group && (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)) {
            item.setExpanded(!item.isExpanded());
            e.consume();
        } else if (e.getCode() == KeyCode.LEFT && !group && item.getParent() != null
                && item.getParent().getValue() instanceof NavNode.Group g && g.heading()) {
            // The heading above is not selectable: nothing to go back to.
            e.consume();
        }
    }

    /** Highlights the item of a view without triggering a view change; unfolds its group. */
    public void select(ViewRequest request) {
        shown = request;
        TreeItem<NavNode> item = find(request);
        if (item == null) return;
        ignoreSelection = true;
        try {
            for (TreeItem<NavNode> p = item.getParent(); p != null; p = p.getParent()) p.setExpanded(true);
            tree.getSelectionModel().select(item);
        } finally {
            ignoreSelection = false;
        }
    }

    private TreeItem<NavNode> find(ViewRequest request) {
        List<TreeItem<NavNode>> all = new ArrayList<>(topLevel);
        for (int i = 0; i < all.size(); i++) {
            TreeItem<NavNode> item = all.get(i);
            ViewRequest r = item.getValue().request();
            if (r != null && r.key() == request.key() && Objects.equals(r.rangeId(), request.rangeId())) {
                return isShown(item) ? item : null;
            }
            List<TreeItem<NavNode>> c = children.get(item);
            if (c != null) all.addAll(c);
        }
        return null;
    }

    /** Whether an item is in the tree, i.e. not filtered out (folded groups included). */
    private boolean isShown(TreeItem<NavNode> item) {
        TreeItem<NavNode> p = item;
        while (p.getParent() != null) p = p.getParent();
        return p == tree.getRoot();
    }

    /** Folds or unfolds every group. */
    private void foldAll(boolean fold) {
        children.keySet().forEach(g -> g.setExpanded(!fold));
    }

    /** The count of an item, or null when it has none. */
    private ObservableIntegerValue countOf(NavNode node) {
        return switch (node) {
            case NavNode.View v -> counts.get(v.key());
            case NavNode.Range r -> counts.get(r.range());
            case NavNode.Group g -> g.heading() ? null : counts.get(g.id());
        };
    }

    private static String format(int n) {
        return NumberFormat.getIntegerInstance(I18n.getLocale()).format(n);
    }

    /* ─── The cell ─── */

    /** An item: an icon, the label (its match highlighted), the count, and a chevron for a group. */
    private final class NavCell extends TreeCell<NavNode> {

        private final Region bar = new Region();
        private final Region icon = new Region();
        private final HBox text = new HBox();
        private final Label count = new Label();
        private final Region chevron = NavIcons.region(NavIcons.CHEVRON, "nav-chevron");
        private final HBox box;
        private final Tooltip tooltip = new Tooltip();
        private ObservableIntegerValue bound;
        private final InvalidationListener showCount = o -> showCount();

        NavCell() {
            getStyleClass().add("nav-cell");
            bar.getStyleClass().add("nav-bar");
            icon.getStyleClass().add("nav-icon");
            text.getStyleClass().add("nav-text");
            text.setAlignment(Pos.CENTER_LEFT);
            count.getStyleClass().add("nav-count");
            count.setMinWidth(Region.USE_PREF_SIZE);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox.setHgrow(text, Priority.SOMETIMES);
            text.setMinWidth(0);
            box = new HBox(8, bar, icon, text, spacer, count, chevron);
            box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().add("nav-row");
            box.prefWidthProperty().bind(widthProperty().subtract(CELL_INSETS));
            box.maxWidthProperty().bind(widthProperty().subtract(CELL_INSETS));
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            tooltip.setShowDelay(Duration.millis(600));
            tooltip.setOnShowing(e -> tooltip.setText(tooltipText(getItem())));

            // A click on a group folds or unfolds it; a heading is not selected.
            addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (isEmpty() || e.getButton() != MouseButton.PRIMARY) return;
                TreeItem<NavNode> item = getTreeItem();
                if (item == null || !children.containsKey(item)) return;
                item.setExpanded(!item.isExpanded());
                if (item.getValue() instanceof NavNode.Group g && g.heading()) e.consume();
            });
            // Clicking the highlighted item is not a selection change. When it shows a subset
            // (a drill-down, "Open in Senses view"…), it goes back to all the objects.
            setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY || isEmpty()) return;
                ViewRequest r = getItem().request();
                if (r != null && isSelected() && shown != null && shown.subset() != null) onSelect.accept(r);
            });
        }

        @Override
        protected void updateItem(NavNode node, boolean empty) {
            super.updateItem(node, empty);
            if (bound != null) bound.removeListener(showCount);
            bound = null;
            setText(null);
            if (empty || node == null) {
                setGraphic(null);
                setContextMenu(null);
                setTooltip(null);
                setAccessibleText(null);
                for (PseudoClass p : List.of(HEADING, NESTED_GROUP, RANGE_ITEM, ACTION, FOLDED, UNDECLARED)) pseudoClassStateChanged(p, false);
                return;
            }
            boolean heading = node instanceof NavNode.Group g && g.heading();
            boolean group = node instanceof NavNode.Group;
            pseudoClassStateChanged(HEADING, heading);
            pseudoClassStateChanged(NESTED_GROUP, group && !heading);
            pseudoClassStateChanged(RANGE_ITEM, node instanceof NavNode.Range);
            pseudoClassStateChanged(ACTION, node instanceof NavNode.View v && v.key() == QUICK_ENTRY);
            pseudoClassStateChanged(FOLDED, group && getTreeItem() != null && !getTreeItem().isExpanded());
            pseudoClassStateChanged(UNDECLARED, node instanceof NavNode.Range r && !r.range().isDeclared());

            String path = switch (node) {
                case NavNode.View v -> NavIcons.of(v.key());
                case NavNode.Group g -> g.heading() ? null : NavIcons.of(RANGE);
                case NavNode.Range r -> null;
            };
            icon.setStyle(path == null ? null : "-fx-shape: \"" + path + "\";");
            icon.setVisible(path != null);
            icon.setManaged(path != null);
            bar.setVisible(!heading);
            bar.setManaged(!heading);
            chevron.setVisible(group);
            chevron.setManaged(group);
            showLabel(heading ? node.label().toUpperCase(I18n.getLocale()) : node.label());

            bound = countOf(node);
            if (bound != null) bound.addListener(showCount);
            showCount();

            setTooltip(heading ? null : tooltip);
            setContextMenu(contextMenu(node));
            setGraphic(box);
        }

        /** The label; while filtering, in three parts with the match highlighted. */
        private void showLabel(String label) {
            String q = normalize(filter.getText());
            int at = q.isEmpty() ? -1 : matchIndex(label, q);
            if (at < 0) {
                Label l = new Label(label);
                l.getStyleClass().add("nav-label");
                text.getChildren().setAll(l);
                return;
            }
            Text before = new Text(label.substring(0, at));
            Text match = new Text(label.substring(at, at + q.length()));
            Text after = new Text(label.substring(at + q.length()));
            for (Text t : List.of(before, match, after)) t.getStyleClass().add("nav-label-text");
            match.getStyleClass().add("nav-match");
            TextFlow flow = new TextFlow(before, match, after);
            flow.setMinWidth(0);
            text.getChildren().setAll(flow);
        }

        private void showCount() {
            boolean has = bound != null;
            count.setVisible(has);
            count.setManaged(has);
            count.setText(has ? format(bound.get()) : "");
            NavNode node = getItem();
            if (node != null) {
                setAccessibleText(has ? I18n.get("nav.count.accessible", node.label(), format(bound.get())) : node.label());
            }
        }

        private ContextMenu contextMenu(NavNode node) {
            List<MenuItem> items = new ArrayList<>();
            switch (node) {
                case NavNode.Group g when g.heading() -> {
                    MenuItem fold = new MenuItem(I18n.get("nav.foldAll"));
                    fold.setOnAction(e -> foldAll(true));
                    MenuItem unfold = new MenuItem(I18n.get("nav.unfoldAll"));
                    unfold.setOnAction(e -> foldAll(false));
                    items.add(fold);
                    items.add(unfold);
                }
                case NavNode.View v when v.key() == ENTRIES -> {
                    MenuItem create = new MenuItem(I18n.get("nav.newEntry"));
                    create.setOnAction(e -> onNewEntry.run());
                    items.add(create);
                }
                case NavNode.Range r -> {
                    MenuItem copy = new MenuItem(I18n.get("nav.copyId"));
                    copy.setOnAction(e -> {
                        ClipboardContent content = new ClipboardContent();
                        content.putString(r.range().getId());
                        Clipboard.getSystemClipboard().setContent(content);
                    });
                    items.add(copy);
                }
                default -> { }
            }
            return items.isEmpty() ? null : new ContextMenu(items.toArray(new MenuItem[0]));
        }
    }

    /** Where {@code q} (normalized) is in {@code label}, or -1; the normalized text keeps the length of the label. */
    private static int matchIndex(String label, String q) {
        StringBuilder b = new StringBuilder();
        for (char c : label.toCharArray()) {
            String n = normalize(String.valueOf(c));
            b.append(n.length() == 1 ? n.charAt(0) : Character.toLowerCase(c));
        }
        return b.toString().indexOf(q);
    }

    private String tooltipText(NavNode node) {
        if (node == null) return null;
        return switch (node) {
            case NavNode.View v -> {
                KeyCombination keys = shortcut.apply(v.key());
                yield keys == null ? v.label() : v.label() + "   " + keys.getDisplayText();
            }
            case NavNode.Range r -> r.range().isDeclared()
                ? I18n.get("nav.range.tooltip", r.range().getId())
                : I18n.get("nav.range.tooltip", r.range().getId()) + "\n" + I18n.get("nav.range.undeclared");
            case NavNode.Group g -> g.label();
        };
    }
}
