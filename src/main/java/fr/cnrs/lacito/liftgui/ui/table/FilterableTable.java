package fr.cnrs.lacito.liftgui.ui.table;

import fr.cnrs.lacito.liftgui.ui.I18n;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A table with a filter above each column and an optional global search text.
 * <p>
 * Each column gets either a free-text filter (rows containing the text) or a facet: a list
 * of the values found in the rows that match the other filters, after an "all" item. A row is shown when it
 * matches every filter and the search text. The rows come from an observable list, so rows
 * added to or removed from the source appear or disappear without rebuilding the table.
 * <p>
 * Layout: a "reset filters" bar, the filter row, then the table.
 */
public final class FilterableTable<T> {

    private final TableView<T> table = new TableView<>();
    private final ObservableList<T> source;
    private final FilteredList<T> filtered;
    private final List<Leaf<T>> leaves;
    private final List<Node> inputs = new ArrayList<>();
    private final VBox node;
    private final String clearLabel = I18n.get("filter.clear");

    private Function<T, String> searchText;
    private StringProperty searchProperty;
    private final ChangeListener<String> searchListener = (obs, o, n) -> refresh();
    private BiConsumer<Integer, Integer> onCountChanged = (shown, total) -> {};
    /** Set while filter inputs are changed by code, so that each change does not refilter. */
    private boolean internalUpdate;

    public FilterableTable(List<ColumnSpec<T>> specs, ObservableList<T> source) {
        this.source = source;
        this.filtered = new FilteredList<>(source, t -> true);
        SortedList<T> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        this.leaves = TableFactory.build(table, specs);
        this.searchText = row -> leaves.stream().map(l -> l.text(row)).collect(Collectors.joining(" "));

        Button resetBtn = new Button(I18n.get("filter.resetAll"));
        resetBtn.setOnAction(e -> {
            resetFilters();
            table.getSelectionModel().clearSelection();
        });
        HBox header = new HBox();
        header.getStyleClass().add("no-dirty");
        header.setPadding(new Insets(0, 6, 4, 6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, resetBtn);

        GridPane filterRow = buildFilterRow();
        node = new VBox(header, filterRow, table);
        node.setMinWidth(0);
        table.setMinWidth(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh();
    }

    /* ─── Configuration ─── */

    /** Text searched by the global search (default: the text of all the cells of the row). */
    public FilterableTable<T> searchText(Function<T, String> fn) {
        this.searchText = fn;
        refresh();
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

    public FilterableTable<T> placeholder(String text) {
        table.setPlaceholder(new Label(text));
        return this;
    }

    /** Stops following the search text. */
    public void dispose() {
        if (searchProperty != null) searchProperty.removeListener(searchListener);
        searchProperty = null;
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

    /** Selects a row (and scrolls to it); returns false when the row is not shown. */
    public boolean select(T row) {
        if (row == null || !table.getItems().contains(row)) return false;
        table.getSelectionModel().select(row);
        table.scrollTo(row);
        return true;
    }

    /** The headers of the leaf columns, for exports. */
    public List<String> headers() {
        return leaves.stream().map(Leaf::header).toList();
    }

    /** The text of the rows shown, for exports. */
    public List<List<String>> rowTexts() {
        return table.getItems().stream()
            .map(row -> leaves.stream().map(l -> l.text(row)).toList())
            .toList();
    }

    /* ─── Filtering ─── */

    /** Clears the search text and every column filter. */
    public void resetFilters() {
        internalUpdate = true;
        try {
            if (searchProperty != null && !searchProperty.get().isBlank()) searchProperty.set("");
            for (Node input : inputs) {
                if (input instanceof TextField tf) tf.clear();
                else comboOf(input).getSelectionModel().select(0);
            }
        } finally {
            internalUpdate = false;
        }
        refresh();
    }

    /** Re-applies the filters and recomputes the values offered by the facets. */
    public void refresh() {
        if (internalUpdate) return;
        String q = query();
        filtered.setPredicate(row -> matches(row, -1, q));
        refreshFacets(q);
        table.refresh();
        onCountChanged.accept(filtered.size(), source.size());
    }

    private String query() {
        String q = searchProperty == null ? null : searchProperty.get();
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(T row, int ignoredColumn, String q) {
        if (row == null) return false;
        for (int i = 0; i < inputs.size(); i++) {
            if (i == ignoredColumn) continue;
            Node input = inputs.get(i);
            String cell = leaves.get(i).text(row);
            if (input instanceof TextField tf) {
                String f = tf.getText();
                if (f != null && !f.isBlank()
                    && !cell.toLowerCase(Locale.ROOT).contains(f.trim().toLowerCase(Locale.ROOT))) return false;
            } else {
                ComboBox<String> cb = comboOf(input);
                if (isActive(cb) && !cb.getValue().equals(cell)) return false;
            }
        }
        if (q.isEmpty()) return true;
        String text = searchText.apply(row);
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    private void refreshFacets(String q) {
        internalUpdate = true;
        try {
            for (int i = 0; i < inputs.size(); i++) {
                if (!(inputs.get(i) instanceof ComboBox<?>)) continue;
                ComboBox<String> cb = comboOf(inputs.get(i));
                boolean active = isActive(cb);
                String current = cb.getValue();
                final int col = i;
                List<String> values = source.stream()
                    .filter(row -> matches(row, col, q))
                    .map(row -> leaves.get(col).text(row))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
                ObservableList<String> items = FXCollections.observableArrayList();
                items.add(clearLabel);
                items.addAll(values);
                cb.setItems(items);
                int idx = active ? values.indexOf(current) : -1;
                if (idx >= 0) cb.getSelectionModel().select(idx + 1);
                else cb.getSelectionModel().select(0);
            }
        } finally {
            internalUpdate = false;
        }
    }

    /** A facet filters when a value other than the first ("all") item is selected. */
    private static boolean isActive(ComboBox<String> cb) {
        return cb.getSelectionModel().getSelectedIndex() > 0 && cb.getValue() != null;
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> comboOf(Node n) {
        return (ComboBox<String>) n;
    }

    /* ─── Filter row ─── */

    private GridPane buildFilterRow() {
        GridPane row = new GridPane();
        row.getStyleClass().addAll("no-dirty", "filter-row");
        row.setHgap(0);
        row.setPadding(new Insets(4, 0, 4, 0));
        row.setMinWidth(0);
        for (int i = 0; i < leaves.size(); i++) {
            Leaf<T> leaf = leaves.get(i);
            Node input = useTextFilter(leaf) ? textFilter() : facetFilter();
            inputs.add(input);
            ColumnConstraints cc = new ColumnConstraints();
            cc.prefWidthProperty().bind(table.getVisibleLeafColumn(i).widthProperty());
            row.getColumnConstraints().add(cc);
            GridPane.setHgrow(input, Priority.ALWAYS);
            row.add(input, i, 0);
        }
        // The filter row is as wide as the columns (it ignores the vertical scrollbar).
        row.maxWidthProperty().bind(Bindings.createDoubleBinding(
            () -> table.getVisibleLeafColumns().stream().mapToDouble(c -> c.getWidth()).sum(),
            table.getVisibleLeafColumns().stream().map(c -> (Observable) c.widthProperty()).toArray(Observable[]::new)
        ));
        return row;
    }

    /** Free text when asked, or when the column has no repeated value (a facet would be useless). */
    private boolean useTextFilter(Leaf<T> leaf) {
        return switch (leaf.spec().filter()) {
            case TEXT -> true;
            case FACET -> false;
            case AUTO -> {
                long distinct = source.stream().map(leaf::text).filter(s -> !s.isBlank()).distinct().count();
                yield distinct == 0 || distinct >= source.size();
            }
        };
    }

    private TextField textFilter() {
        TextField tf = new TextField();
        tf.setPromptText(I18n.get("filter.prompt"));
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.setMinWidth(0);
        tf.setMinHeight(26);
        tf.setPrefHeight(26);
        tf.getStyleClass().add("filter-text");
        tf.textProperty().addListener((obs, o, n) -> refresh());
        return tf;
    }

    private ComboBox<String> facetFilter() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().add("filter-combo");
        cb.setPromptText(I18n.get("filter.prompt"));
        cb.setMaxWidth(Double.MAX_VALUE);
        cb.setMinWidth(0);
        cb.setMinHeight(26);
        cb.setPrefHeight(26);
        // The "all" item is greyed out while the facet does not filter.
        cb.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                    setStyle("");
                    return;
                }
                setText(item);
                boolean disabled = getIndex() == 0 && !isActive(cb);
                setDisable(disabled);
                setStyle(disabled ? "-fx-opacity: 0.45;" : "");
            }
        });
        cb.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        cb.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (!internalUpdate) refresh();
        });
        return cb;
    }
}
