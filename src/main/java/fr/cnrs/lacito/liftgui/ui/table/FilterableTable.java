package fr.cnrs.lacito.liftgui.ui.table;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.FilterEngine;
import fr.cnrs.lacito.liftgui.ui.table.filter.FilterModel;
import fr.cnrs.lacito.liftgui.ui.table.filter.OptionFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.RangeFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextFilter;
import fr.cnrs.lacito.liftgui.ui.table.widgets.FilterControl;
import fr.cnrs.lacito.liftgui.ui.table.widgets.FilterControls;
import fr.cnrs.lacito.liftgui.ui.table.widgets.FilterHost;
import fr.cnrs.lacito.liftgui.ui.table.widgets.TableToolbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A table with a filter in the header of each column, a bar of the active filters, and an
 * optional global search text.
 * <p>
 * Each leaf column has the filter of its {@link FilterKind}: text (match case, regular
 * expression, operators), options (values to tick, with counts) or range (numbers, counts,
 * dates). Being in the column headers, the filters follow the width and the position of their
 * column. A row is shown when it passes every filter and the search ({@link FilterEngine}).
 * The rows come from an observable list: rows added to or removed from the source appear or
 * disappear without rebuilding the table. The columns can be changed in place
 * ({@link #setColumns}), keeping the filters, sort and selection of the columns that remain.
 * <p>
 * Layout: the toolbar ({@link TableToolbar}: Columns button, filter chips, Clear all), then
 * the table.
 */
public final class FilterableTable<T> {

    private final TableView<T> table = new TableView<>();
    private final ObservableList<T> source;
    private final FilteredList<T> filtered;
    private final FilterModel model = new FilterModel();
    private final FilterEngine<T> engine = new FilterEngine<>(List.of());
    private final Map<String, FilterControl> controls = new HashMap<>();
    private final TableToolbar toolbar;
    private final VBox node;
    private List<ColumnSpec<T>> specs = List.of();
    private List<Leaf<T>> leaves = List.of();

    private Function<T, String> searchText;
    private Function<T, String> rowLabel;
    private StringProperty searchProperty;
    private final ChangeListener<String> searchListener = (obs, o, n) -> {
        pinned = null;
        refresh();
    };
    private BiConsumer<Integer, Integer> onCountChanged = (shown, total) -> {};
    private Consumer<List<Leaf<T>>> onOrderChanged;
    private Consumer<Map<String, Double>> onWidthsChanged;
    private Function<Leaf<T>, javafx.scene.control.ContextMenu> headerMenu;
    private final PauseTransition widthsDebounce = new PauseTransition(Duration.millis(300));
    /** Set while the filters or columns are changed by code, so that each change does not refilter. */
    private boolean internalUpdate;
    private final InvalidationListener markEmptyListener = o -> table.refresh();
    /** The last edited row, kept visible until the filters or the selection change. */
    private T pinned;

    public FilterableTable(List<ColumnSpec<T>> specs, ObservableList<T> source) {
        this(specs, source, Map.of());
    }

    /** @param widths widths chosen by the user, by leaf id */
    public FilterableTable(List<ColumnSpec<T>> specs, ObservableList<T> source, Map<String, Double> widths) {
        this.source = source;
        this.filtered = new FilteredList<>(source, t -> true);
        SortedList<T> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        this.searchText = row -> leaves.stream().map(l -> l.text(row)).collect(Collectors.joining(" "));
        engine.setSearchText(row -> searchText.apply(row));

        toolbar = new TableToolbar(model, this::shortName, this::revealColumn, () -> {
            resetFilters();
            table.getSelectionModel().clearSelection();
        });
        model.filters().addListener((MapChangeListener<String, ColumnFilter>) c -> {
            FilterControl control = controls.get(c.getKey());
            if (control != null) control.sync(model.filters().get(c.getKey()));
            if (internalUpdate) return;
            pinned = null;
            toolbar.clearNotice();
            refresh();
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (pinned != null && n != pinned) {
                pinned = null;
                refresh();
            }
        });
        // Columns reordered by dragging their headers.
        table.getColumns().addListener((ListChangeListener<TableColumn<T, ?>>) c -> {
            if (!internalUpdate && onOrderChanged != null) onOrderChanged.accept(visibleLeaves());
        });
        widthsDebounce.setOnFinished(e -> {
            if (onWidthsChanged != null) onWidthsChanged.accept(userWidths());
        });
        // A weak listener: the static property must not keep every table ever shown alive.
        TableFactory.MARK_EMPTY.addListener(new javafx.beans.WeakInvalidationListener(markEmptyListener));

        node = new VBox(toolbar, table);
        node.setMinWidth(0);
        table.setMinWidth(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        setColumns(specs, widths);
    }

    /* ─── Configuration ─── */

    /** Text searched by the global search (default: the text of all the cells of the row). */
    public FilterableTable<T> searchText(Function<T, String> fn) {
        this.searchText = fn;
        engine.invalidate();
        refresh();
        return this;
    }

    /** The name of a row in notices (default: the text of its first cell). */
    public FilterableTable<T> rowLabel(Function<T, String> fn) {
        this.rowLabel = fn;
        return this;
    }

    /** Filters the rows with the text of {@code search}; {@link #dispose()} detaches it. */
    public FilterableTable<T> bindSearch(StringProperty search) {
        dispose();
        this.searchProperty = search;
        if (search != null) search.addListener(searchListener);
        refresh();
        return this;
    }

    /** Called with (rows shown, rows in the source) whenever the filtering changes. */
    public FilterableTable<T> onCountChanged(BiConsumer<Integer, Integer> callback) {
        this.onCountChanged = callback == null ? (s, t) -> {} : callback;
        onCountChanged.accept(filtered.size(), source.size());
        return this;
    }

    /** Called with the selected row (never null). */
    public FilterableTable<T> onSelect(Consumer<T> callback) {
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) callback.accept(n);
        });
        return this;
    }

    /** Called with the leaves in their new order after the user dragged a column header. */
    public FilterableTable<T> onOrderChanged(Consumer<List<Leaf<T>>> callback) {
        this.onOrderChanged = callback;
        return this;
    }

    /** Called with the widths that differ from the columns' defaults, after the user resized a column. */
    public FilterableTable<T> onWidthsChanged(Consumer<Map<String, Double>> callback) {
        this.onWidthsChanged = callback;
        return this;
    }

    /**
     * The context menu of a column header. For a multilingual column's group header, the
     * leaf given has no language.
     */
    public FilterableTable<T> headerMenu(Function<Leaf<T>, javafx.scene.control.ContextMenu> menu) {
        this.headerMenu = menu;
        applyHeaderMenus();
        return this;
    }

    private void applyHeaderMenus() {
        if (headerMenu == null) return;
        for (TableColumn<T, ?> c : table.getColumns()) {
            Leaf<T> leaf = TableFactory.leaf(c);
            if (leaf == null && !c.getColumns().isEmpty()) {
                Leaf<T> first = TableFactory.leaf(c.getColumns().get(0));
                if (first != null) leaf = new Leaf<>(first.spec(), null);
                for (TableColumn<T, ?> sub : c.getColumns()) {
                    Leaf<T> l = TableFactory.leaf(sub);
                    if (l != null) sub.setContextMenu(headerMenu.apply(l));
                }
            }
            if (leaf != null) c.setContextMenu(headerMenu.apply(leaf));
        }
    }

    public FilterableTable<T> placeholder(String text) {
        table.setPlaceholder(new Label(text));
        return this;
    }

    /** Stops following the search text. */
    public void dispose() {
        if (searchProperty != null) searchProperty.removeListener(searchListener);
        searchProperty = null;
    }

    /* ─── Columns ─── */

    public void setColumns(List<ColumnSpec<T>> newSpecs) {
        setColumns(newSpecs, userWidths());
    }

    /**
     * Replaces the columns, keeping the filters, sort order and selection of the leaves that
     * remain (a filter whose kind no longer fits its column is dropped).
     *
     * @param widths widths chosen by the user, by leaf id
     */
    public void setColumns(List<ColumnSpec<T>> newSpecs, Map<String, Double> widths) {
        Map<String, TableColumn.SortType> sort = new LinkedHashMap<>();
        for (TableColumn<T, ?> c : table.getSortOrder()) {
            Leaf<T> l = TableFactory.leaf(c);
            if (l != null) sort.put(l.id(), c.getSortType());
        }
        T selected = table.getSelectionModel().getSelectedItem();
        internalUpdate = true;
        try {
            this.specs = List.copyOf(newSpecs);
            controls.clear();
            this.leaves = TableFactory.build(table, specs, new TableFactory.Options<>(widths, this::header,
                (leaf, row) -> onEdited(row)));
            engine.setColumns(leaves);
            Map<String, Leaf<T>> byId = leaves.stream().collect(Collectors.toMap(Leaf::id, l -> l, (a, b) -> a));
            for (String id : List.copyOf(model.filters().keySet())) {
                Leaf<T> l = byId.get(id);
                if (l == null || !fits(model.filters().get(id), l.filterKind())) model.remove(id);
            }
            controls.forEach((id, c) -> c.sync(model.filters().get(id)));
            List<TableColumn<T, ?>> order = new ArrayList<>();
            sort.forEach((id, type) -> column(id).ifPresent(c -> {
                c.setSortType(type);
                order.add(c);
            }));
            table.getSortOrder().setAll(order);
            for (Leaf<T> l : leaves) {
                column(l.id()).ifPresent(c -> c.widthProperty().addListener((obs, o, n) -> {
                    if (!internalUpdate) widthsDebounce.playFromStart();
                }));
            }
            // Languages dragged inside their group column.
            for (TableColumn<T, ?> c : table.getColumns()) {
                if (c.getColumns().isEmpty()) continue;
                c.getColumns().addListener((ListChangeListener<TableColumn<T, ?>>) ch -> {
                    if (!internalUpdate && onOrderChanged != null) onOrderChanged.accept(visibleLeaves());
                });
            }
        } finally {
            internalUpdate = false;
        }
        applyHeaderMenus();
        toolbar.refresh();
        refresh();
        if (selected != null && table.getItems().contains(selected)) table.getSelectionModel().select(selected);
    }

    private static boolean fits(ColumnFilter f, FilterKind kind) {
        return switch (kind) {
            case TEXT -> f instanceof TextFilter;
            case OPTIONS -> f instanceof OptionFilter;
            case RANGE -> f instanceof RangeFilter;
        };
    }

    /** The header of a leaf: its title, and its filter under it, as wide as the column. */
    private Node header(Leaf<T> leaf) {
        FilterControl control = FilterControls.create(leaf, host);
        controls.put(leaf.id(), control);
        control.sync(model.filters().get(leaf.id()));
        String title = leaf.lang() != null && leaf.spec().languages().size() > 1 ? leaf.lang()
            : leaf.lang() != null ? leaf.spec().title() + " · " + leaf.lang() : leaf.spec().title();
        Label label = new Label(title);
        label.getStyleClass().add("filter-title");
        label.setMinWidth(0);
        javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(control.node());
        holder.setMinWidth(0);
        holder.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        FilterControls.isolate(holder);
        VBox box = new VBox(3, label, holder);
        box.getStyleClass().add("filter-header");
        // A column spanning both header rows is taller: its title and filter sit at the bottom.
        box.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        box.setMinWidth(0);
        // As wide as the header's label, which the header sizes to the column (minus the sort arrow).
        box.parentProperty().addListener((obs, o, p) -> {
            box.prefWidthProperty().unbind();
            box.maxWidthProperty().unbind();
            if (p instanceof Region r) {
                box.prefWidthProperty().bind(r.widthProperty().subtract(6));
                box.maxWidthProperty().bind(r.widthProperty().subtract(6));
            }
        });
        return box;
    }

    private final FilterHost host = new FilterHost() {
        @Override
        public FilterModel model() {
            return model;
        }

        @Override
        public List<FilterEngine.OptionCount> options(String leafId) {
            return engine.options(source, leafId, model.filters(), query());
        }

        @Override
        public Optional<FilterEngine.Bounds> bounds(String leafId) {
            return engine.bounds(source, leafId, model.filters(), query());
        }

        @Override
        public List<FilterEngine.Bin> histogram(String leafId) {
            return engine.histogram(source, leafId, model.filters(), query());
        }

        @Override
        public void focusTable() {
            table.requestFocus();
            if (table.getSelectionModel().isEmpty() && !table.getItems().isEmpty()) {
                table.getSelectionModel().selectFirst();
            }
        }
    };

    /** The column of a leaf, by its id. */
    public Optional<TableColumn<T, ?>> column(String leafId) {
        return table.getVisibleLeafColumns().stream()
            .filter(c -> { Leaf<T> l = TableFactory.leaf(c); return l != null && l.id().equals(leafId); })
            .findFirst();
    }

    private String shortName(String leafId) {
        return leaves.stream().filter(l -> l.id().equals(leafId)).findFirst().map(Leaf::shortName).orElse(leafId);
    }

    /** The leaves in their displayed order. */
    public List<Leaf<T>> visibleLeaves() {
        List<Leaf<T>> out = new ArrayList<>();
        for (TableColumn<T, ?> c : table.getVisibleLeafColumns()) {
            Leaf<T> l = TableFactory.leaf(c);
            if (l != null) out.add(l);
        }
        return out;
    }

    /** The widths that differ from the columns' defaults, by leaf id. */
    public Map<String, Double> userWidths() {
        Map<String, Double> w = new LinkedHashMap<>();
        if (table == null) return w;
        for (TableColumn<T, ?> c : table.getVisibleLeafColumns()) {
            Leaf<T> l = TableFactory.leaf(c);
            if (l != null && Math.abs(c.getWidth() - l.spec().prefWidth()) > 1 && c.getWidth() > 0) {
                w.put(l.id(), Math.rint(c.getWidth()));
            }
        }
        return w;
    }

    public List<ColumnSpec<T>> specs() {
        return specs;
    }

    /** Scrolls to a column and opens its filter. */
    public void revealColumn(String leafId) {
        column(leafId).ifPresent(table::scrollToColumn);
        FilterControl c = controls.get(leafId);
        if (c != null) c.open();
    }

    /** Focuses the filter of the column of the focused cell, else of the first column. */
    public void focusFilter() {
        var cell = table.getFocusModel().getFocusedCell();
        Leaf<T> leaf = cell == null || cell.getTableColumn() == null ? null : TableFactory.leaf(cell.getTableColumn());
        if (leaf == null && !leaves.isEmpty()) leaf = visibleLeaves().isEmpty() ? leaves.get(0) : visibleLeaves().get(0);
        if (leaf == null) return;
        column(leaf.id()).ifPresent(table::scrollToColumn);
        FilterControl c = controls.get(leaf.id());
        if (c != null) c.focus();
    }

    /* ─── Access ─── */

    public Node node() {
        return node;
    }

    public TableView<T> table() {
        return table;
    }

    public ObservableList<T> source() {
        return source;
    }

    public FilterModel filterModel() {
        return model;
    }

    public TableToolbar toolbar() {
        return toolbar;
    }

    /** The filter widget of a column (tests, keyboard). */
    public Optional<FilterControl> filterControl(String leafId) {
        return Optional.ofNullable(controls.get(leafId));
    }

    /** Selects a row (and scrolls to it); returns false when the row is not shown. */
    public boolean select(T row) {
        if (row == null || !table.getItems().contains(row)) return false;
        table.getSelectionModel().select(row);
        table.scrollTo(row);
        return true;
    }

    /**
     * Makes a row of the source visible: clears the filters (and the search) that hide it,
     * with a notice offering to restore them. Returns false when the row is not in the
     * source.
     */
    public boolean reveal(T row) {
        if (row == null || !source.contains(row)) return false;
        if (table.getItems().contains(row)) return true;
        FilterEngine.Hiding hiding = engine.hiding(row, model.filters(), query());
        Map<String, ColumnFilter> saved = model.snapshot();
        String savedSearch = searchProperty == null ? "" : searchProperty.get();
        internalUpdate = true;
        try {
            hiding.filters().forEach(model::remove);
            if (hiding.search() && searchProperty != null) {
                searchProperty.removeListener(searchListener);
                searchProperty.set("");
                searchProperty.addListener(searchListener);
            }
        } finally {
            internalUpdate = false;
        }
        refresh();
        if (!hiding.filters().isEmpty()) {
            String name = rowLabel != null ? rowLabel.apply(row) : leaves.isEmpty() ? "" : leaves.get(0).text(row);
            toolbar.setNotice(I18n.get("filter.revealed", name), () -> {
                internalUpdate = true;
                try {
                    model.restore(saved);
                    if (searchProperty != null) searchProperty.set(savedSearch);
                } finally {
                    internalUpdate = false;
                }
                refresh();
            });
        }
        return table.getItems().contains(row);
    }

    /** The headers of the leaf columns, for exports. */
    public List<String> headers() {
        return visibleLeaves().stream().map(Leaf::header).toList();
    }

    /** The text of the rows shown, for exports. */
    public List<List<String>> rowTexts() {
        List<Leaf<T>> shown = visibleLeaves();
        return table.getItems().stream()
            .map(row -> shown.stream().map(l -> l.text(row)).toList())
            .toList();
    }

    /* ─── Filtering ─── */

    /** Clears the search text and every column filter. */
    public void resetFilters() {
        internalUpdate = true;
        try {
            if (searchProperty != null && !searchProperty.get().isBlank()) {
                searchProperty.removeListener(searchListener);
                searchProperty.set("");
                searchProperty.addListener(searchListener);
            }
            model.clear();
            pinned = null;
        } finally {
            internalUpdate = false;
        }
        toolbar.clearNotice();
        refresh();
    }

    /** Forgets the cached cell texts (after edits); the next filtering reads the cells again. */
    public void invalidate() {
        engine.invalidate();
    }

    /** Re-applies the filters and the search. */
    public void refresh() {
        if (internalUpdate) return;
        Predicate<T> p = engine.predicate(model.filters(), query());
        T pin = pinned;
        filtered.setPredicate(row -> row == pin || p.test(row));
        int shown = filtered.size();
        onCountChanged.accept(shown, source.size());
        toolbar.setCount(shown, source.size());
    }

    private void onEdited(T row) {
        engine.invalidate();
        pinned = row;
    }

    private String query() {
        String q = searchProperty == null ? null : searchProperty.get();
        return q == null ? "" : q;
    }

    /** For tests: the node of the filter of a column. */
    Parent headerOf(String leafId) {
        FilterControl c = controls.get(leafId);
        return c == null ? null : c.node().getParent();
    }
}
