package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.nav.ViewKey;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout.ColumnRef;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSection;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.FilterableTable;
import fr.cnrs.lacito.liftgui.ui.table.Leaf;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A view listing objects in a {@link FilterableTable}: selecting a row shows it in the
 * editor panel.
 * <p>
 * For a component table (a {@link ViewKey} is given), the columns shown are those chosen
 * for that table ({@link fr.cnrs.lacito.liftgui.ui.table.ColumnLayoutStore}), else the
 * default ones of the catalog; a change of the chosen columns (Columns popover, Preferences)
 * is applied at once; columns dragged or resized in the header are remembered; and, when the
 * table lists all the objects (no subset), its filters are kept for the session.
 *
 * @param <T> the type of the rows
 */
public class ObjectTableView<T> implements DictionaryView {

    protected final EditorContext ctx;
    protected final FilterableTable<T> table;
    private final Class<T> rowType;
    private final String title;
    private final ViewKey key;
    private final List<ColumnSection<T>> sections;
    private final List<ColumnSpec<T>> catalog;
    private Node node;
    /** Clears the table's cached cell texts on every change of the dictionary. */
    private final javafx.util.Subscription changes;
    private final ChangeListener<ColumnLayout> layoutListener;

    /**
     * A table with fixed columns (the catalog's defaults).
     *
     * @param rowType     the type of the rows (to recognise the objects to select)
     * @param catalog     all the columns available for the rows
     * @param rows        the rows; a registry list keeps the table up to date
     * @param title       the title of the view; null for the view's name
     * @param placeholder i18n key of the text shown when there is no row; may be null
     * @param onSelect    shows the selected row
     */
    public ObjectTableView(EditorContext ctx, Class<T> rowType, List<ColumnSpec<T>> catalog, ObservableList<T> rows,
                           String title, String placeholder, Consumer<T> onSelect) {
        this(ctx, null, false, rowType, List.of(new ColumnSection<>("columns", "", catalog)), rows, title, placeholder,
            onSelect);
    }

    /**
     * A component table whose columns can be chosen.
     *
     * @param key      the view (the chosen columns and the session filters are kept per view)
     * @param subset   whether the rows are a subset of the objects (no session filters then)
     * @param sections all the columns available, in sections; the visible ones are the default
     */
    public ObjectTableView(EditorContext ctx, ViewKey key, boolean subset, Class<T> rowType,
                           List<ColumnSection<T>> sections, ObservableList<T> rows, String title, String placeholder,
                           Consumer<T> onSelect) {
        this.ctx = ctx;
        this.rowType = rowType;
        this.title = title;
        this.key = key;
        this.sections = List.copyOf(sections);
        this.catalog = ColumnSection.flatten(sections);
        String store = storeKey();
        this.table = new FilterableTable<>(shownColumns(), rows,
                store == null ? Map.of() : ctx.columnLayouts().widths(store))
            .bindSearch(ctx.searchText())
            .onCountChanged(ctx::showCount)
            .onSelect(onSelect);
        if (placeholder != null) table.placeholder(I18n.get(placeholder));
        this.node = table.node();
        this.changes = ctx.dictionary() == null ? javafx.util.Subscription.EMPTY
            : ctx.dictionary().addChangeListener(change -> table.invalidate());

        if (store == null) {
            layoutListener = null;
            return;
        }
        // Widths given by the user; columns dragged in the header.
        table.onWidthsChanged(w -> {
            Map<String, Double> all = new java.util.LinkedHashMap<>(ctx.columnLayouts().widths(store));
            table.visibleLeaves().forEach(l -> all.remove(l.id()));
            all.putAll(w);
            ctx.columnLayouts().setWidths(store, all);
        });
        table.onOrderChanged(this::orderChanged);
        layoutListener = (obs, o, n) -> {
            table.setColumns(shownColumns());
            updateColumnsButton();
        };
        ctx.columnLayouts().property(store).addListener(layoutListener);
        // The Columns button and the menus of the column headers.
        columnsButton.setId("columnsButton");
        columnsButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        columnsButton.getStyleClass().add("columns-button");
        columnsButton.setTooltip(new javafx.scene.control.Tooltip(I18n.get("columns.tooltip")));
        columnsButton.setOnAction(e -> openColumnChooser(false));
        table.toolbar().leftSlot().getChildren().add(columnsButton);
        updateColumnsButton();
        table.headerMenu(this::headerMenu);
        // Filters kept for the session, for the table of all the objects.
        if (!subset) {
            Map<String, ColumnFilter> saved = ctx.filterSessions().get(store);
            if (!saved.isEmpty()) table.filterModel().restore(saved);
            table.filterModel().filters().addListener((MapChangeListener<String, ColumnFilter>) c ->
                ctx.filterSessions().put(store, table.filterModel().snapshot()));
        }
    }

    private final javafx.scene.control.Button columnsButton = new javafx.scene.control.Button();
    private fr.cnrs.lacito.liftgui.ui.table.widgets.FilterPopover chooserPopover;

    /** "▥ Columns", with the number of columns shown when they are not the default ones. */
    private void updateColumnsButton() {
        String store = storeKey();
        boolean custom = store != null && ctx.columnLayouts().layout(store).isPresent();
        int n = table.specs().size();
        columnsButton.setText("▥ " + I18n.get("columns.button") + (custom ? "  " + n : ""));
        columnsButton.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("custom"), custom);
    }

    /** Opens the column chooser under the Columns button (Ctrl+Shift+K). */
    public void openColumnChooser(boolean focusSearch) {
        String store = storeKey();
        if (store == null) return;
        if (chooserPopover != null && chooserPopover.isShowing()) {
            chooserPopover.hide();
            return;
        }
        var chooser = new fr.cnrs.lacito.liftgui.ui.table.widgets.ColumnChooser<>(
            title != null ? title : I18n.get(key.i18nKey()), sections, ctx.columnLayouts().effective(store, catalog));
        chooser.setPrefWidth(420);
        chooser.setPrefHeight(560);
        javafx.animation.PauseTransition apply = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
        apply.setOnFinished(e -> ctx.columnLayouts().set(store, chooser.layoutProperty().get()));
        chooser.layoutProperty().addListener((obs, o, n) -> apply.playFromStart());
        chooserPopover = new fr.cnrs.lacito.liftgui.ui.table.widgets.FilterPopover(chooser);
        chooserPopover.setOnHidden(e -> {
            if (apply.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                apply.stop();
                ctx.columnLayouts().set(store, chooser.layoutProperty().get());
            }
        });
        chooserPopover.showBelow(columnsButton.getScene() != null ? columnsButton : table.node());
        javafx.application.Platform.runLater(chooser::focusSearch);
    }

    /** Changes the chosen layout of this table; the default one is stored as "no choice". */
    private void updateLayout(java.util.function.UnaryOperator<ColumnLayout> change) {
        String store = storeKey();
        ColumnLayout next = change.apply(ctx.columnLayouts().effective(store, catalog));
        ctx.columnLayouts().set(store, next.equals(ColumnLayout.defaults(catalog)) ? null : next);
    }

    /** The context menu of a column header. */
    private javafx.scene.control.ContextMenu headerMenu(Leaf<T> leaf) {
        ColumnSpec<T> spec = leaf.spec();
        String id = spec.id();
        var m = new javafx.scene.control.ContextMenu();
        var hide = new javafx.scene.control.MenuItem(I18n.get("columns.hide", spec.title()));
        hide.setOnAction(e -> updateLayout(l -> l.without(id)));
        m.getItems().add(hide);
        if (leaf.lang() != null && spec.languages().size() > 1) {
            String lang = leaf.lang();
            var hideLang = new javafx.scene.control.MenuItem(I18n.get("columns.hideLanguage", lang));
            hideLang.setOnAction(e -> updateLayout(l -> l.with(l.ref(id).orElse(ColumnRef.of(id))
                .withLanguages(spec.languages().stream().filter(x -> !x.equals(lang)).toList()))));
            var only = new javafx.scene.control.MenuItem(I18n.get("columns.onlyLanguage", lang));
            only.setOnAction(e -> updateLayout(l -> l.with(l.ref(id).orElse(ColumnRef.of(id)).withLanguages(List.of(lang)))));
            m.getItems().addAll(hideLang, only);
        }
        Optional<javafx.scene.control.TableColumn<T, ?>> col = table.column(leaf.id());
        if (col.isPresent()) {
            m.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            var asc = new javafx.scene.control.MenuItem(I18n.get("columns.sortAsc"));
            asc.setOnAction(e -> sort(col.get(), javafx.scene.control.TableColumn.SortType.ASCENDING));
            var desc = new javafx.scene.control.MenuItem(I18n.get("columns.sortDesc"));
            desc.setOnAction(e -> sort(col.get(), javafx.scene.control.TableColumn.SortType.DESCENDING));
            var clear = new javafx.scene.control.MenuItem(I18n.get("filter.clearOne"));
            clear.setOnAction(e -> table.filterModel().remove(leaf.id()));
            var filterAs = new javafx.scene.control.Menu(I18n.get("columns.filterAs"));
            var group = new javafx.scene.control.ToggleGroup();
            for (fr.cnrs.lacito.liftgui.ui.table.FilterKind kind : fr.cnrs.lacito.liftgui.ui.table.FilterKind.values()) {
                if (!spec.type().accepts(kind)) continue;
                var item = new javafx.scene.control.RadioMenuItem(I18n.get("columns.filterKind." + kind.name()));
                item.setToggleGroup(group);
                item.setSelected(leaf.filterKind() == kind);
                item.setOnAction(e -> updateLayout(l -> l.with(l.ref(id).orElse(ColumnRef.of(id))
                    .withFilterAs(kind == spec.type().defaultFilter() ? null : kind))));
                filterAs.getItems().add(item);
            }
            m.getItems().addAll(asc, desc, clear, filterAs);
        }
        var add = new javafx.scene.control.MenuItem(I18n.get("columns.add"));
        add.setOnAction(e -> openColumnChooser(true));
        var reset = new javafx.scene.control.MenuItem(I18n.get("columns.default"));
        reset.setOnAction(e -> ctx.columnLayouts().set(storeKey(), null));
        m.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), add, reset);
        return m;
    }

    private void sort(javafx.scene.control.TableColumn<T, ?> col, javafx.scene.control.TableColumn.SortType type) {
        col.setSortType(type);
        table.table().getSortOrder().setAll(List.of(col));
    }

    /** The key of this table in the stores; null for a table whose columns are fixed. */
    private String storeKey() {
        return key == null ? null : key.name();
    }

    /** The columns to show: the chosen layout, else the catalog's defaults. */
    private List<ColumnSpec<T>> shownColumns() {
        String store = storeKey();
        if (store == null) return ColumnLayout.defaults(catalog).apply(catalog);
        return ctx.columnLayouts().effective(store, catalog).apply(catalog);
    }

    /** The layout after the user dragged a column (or a language) to another place. */
    private void orderChanged(List<Leaf<T>> leaves) {
        String store = storeKey();
        ColumnLayout current = ctx.columnLayouts().effective(store, catalog);
        List<ColumnRef> refs = new ArrayList<>();
        for (Leaf<T> leaf : leaves) {
            String id = leaf.spec().id();
            if (!refs.isEmpty() && refs.get(refs.size() - 1).id().equals(id)) continue;
            ColumnRef ref = current.ref(id).orElse(ColumnRef.of(id));
            List<String> langs = leaves.stream().filter(l -> l.spec().id().equals(id) && l.lang() != null)
                .map(Leaf::lang).toList();
            Optional<ColumnSpec<T>> spec = catalog.stream().filter(s -> s.id().equals(id)).findFirst();
            boolean allInOrder = spec.isPresent() && langs.equals(spec.get().languages());
            refs.add(ref.allLanguages() && allInOrder ? ref : ref.withLanguages(langs));
        }
        // Ids of the stored layout not in this catalog (another dictionary's fields…) are kept.
        for (ColumnRef r : current.columns()) {
            if (catalog.stream().noneMatch(s -> s.id().equals(r.id()))) refs.add(r);
        }
        ColumnLayout next = new ColumnLayout(refs);
        if (!next.equals(current)) {
            ctx.columnLayouts().property(store).removeListener(layoutListener);
            try {
                ctx.columnLayouts().set(store, next);
            } finally {
                ctx.columnLayouts().property(store).addListener(layoutListener);
            }
        }
    }

    /** All the columns available, in sections (for the column chooser). */
    public List<ColumnSection<T>> sections() {
        return sections;
    }

    /** The view of this table, or null for a table whose columns are fixed. */
    public ViewKey key() {
        return key;
    }

    /**
     * The rows of a view: the given (live) list, or, when a subset is given, the objects of
     * that list which belong to the subset, in the subset's order. The rows stay live: a
     * deleted object disappears, and comes back when the deletion is undone.
     */
    public static <T> ObservableList<T> rows(List<?> subset, Class<T> type, ObservableList<T> all) {
        if (subset == null) return all;
        Map<Object, Integer> position = new IdentityHashMap<>();
        for (Object o : subset) if (type.isInstance(o)) position.putIfAbsent(o, position.size());
        return new SortedList<>(new FilteredList<>(all, position::containsKey),
            Comparator.comparingInt(position::get));
    }

    /** Puts controls (buttons…) under the table. */
    protected void addBelow(Node controls) {
        VBox box = new VBox(6, table.node(), controls);
        VBox.setVgrow(table.node(), Priority.ALWAYS);
        node = box;
    }

    @Override
    public Node node() {
        return node;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public boolean select(Object object) {
        if (!rowType.isInstance(object)) return false;
        T row = rowType.cast(object);
        // An object reached by navigation must not be hidden by the filters.
        table.reveal(row);
        return table.select(row);
    }

    @Override
    public void dispose() {
        table.dispose();
        changes.unsubscribe();
        if (layoutListener != null) ctx.columnLayouts().property(storeKey()).removeListener(layoutListener);
    }

    @Override
    public void refreshCells() {
        table.table().refresh();
    }

    @Override
    public Optional<FilterableTable<?>> table() {
        return Optional.of(table);
    }

    /** The table of this view. */
    public FilterableTable<T> filterableTable() {
        return table;
    }
}
