package fr.cnrs.lacito.liftgui.ui.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;

/**
 * Creates the {@code TableColumn}s of a table from column specs: sorting by value type,
 * editable cells (cell by cell: {@link ColumnSpec#isEditable}), right-aligned numbers, and
 * the empty-cell marker. A header factory may put a node (the filter) in each leaf header.
 */
public final class TableFactory {

    /** Minimum width of a leaf column: room for its filter. */
    public static final double MIN_WIDTH = 90;

    /** Whether empty cells of text columns are marked (View ▸ Mark empty cells). */
    public static final BooleanProperty MARK_EMPTY = new SimpleBooleanProperty(true);

    private static final PseudoClass EMPTY_VALUE = PseudoClass.getPseudoClass("empty-value");
    private static final PseudoClass NOT_APPLICABLE = PseudoClass.getPseudoClass("not-applicable");

    private TableFactory() {}

    /**
     * What to do around the columns.
     *
     * @param widths   widths chosen by the user, by leaf id; may be empty
     * @param header   the node of a leaf header (title and filter); null for a plain title
     * @param onEdited called after a cell was edited, with the row
     */
    public record Options<T>(Map<String, Double> widths, Function<Leaf<T>, Node> header, BiConsumer<Leaf<T>, T> onEdited) {
        public static <T> Options<T> plain() {
            return new Options<>(Map.of(), null, null);
        }
    }

    /** Replaces the columns of {@code table} by those of {@code specs}; returns the leaves in display order. */
    public static <T> List<Leaf<T>> build(TableView<T> table, List<ColumnSpec<T>> specs) {
        return build(table, specs, Options.plain());
    }

    /**
     * Replaces the columns of {@code table} by those described by {@code specs} and returns
     * the leaf columns, in display order. A multilingual column with a single language is a
     * single column titled "Title · lang".
     */
    public static <T> List<Leaf<T>> build(TableView<T> table, List<ColumnSpec<T>> specs, Options<T> options) {
        table.getColumns().clear();
        List<Leaf<T>> leaves = new ArrayList<>();
        boolean editable = false;
        for (ColumnSpec<T> spec : specs) {
            editable |= spec.commit() != null;
            if (!spec.multilingual()) {
                TableColumn<T, String> col = leafColumn(spec, null, spec.title(), options);
                leaves.add(leaf(col));
                table.getColumns().add(col);
                continue;
            }
            if (spec.languages().size() == 1) {
                String lang = spec.languages().get(0);
                TableColumn<T, String> col = leafColumn(spec, lang, spec.title() + " · " + lang, options);
                leaves.add(leaf(col));
                table.getColumns().add(col);
                continue;
            }
            TableColumn<T, String> group = new TableColumn<>(spec.title());
            group.setId("group:" + spec.id());
            for (String lang : spec.languages()) {
                TableColumn<T, String> col = leafColumn(spec, lang, lang, options);
                leaves.add(leaf(col));
                group.getColumns().add(col);
            }
            table.getColumns().add(group);
        }
        table.setEditable(editable);
        return leaves;
    }

    @SuppressWarnings("unchecked")
    public static <T> Leaf<T> leaf(TableColumn<T, ?> col) {
        return col.getUserData() instanceof Leaf<?> l ? (Leaf<T>) l : null;
    }

    private static <T> TableColumn<T, String> leafColumn(ColumnSpec<T> spec, String lang, String header,
                                                         Options<T> options) {
        Leaf<T> leaf = new Leaf<>(spec, lang);
        TableColumn<T, String> col = new TableColumn<>(header);
        col.setId(leaf.id());
        col.setUserData(leaf);
        Double w = options.widths().get(leaf.id());
        col.setPrefWidth(w != null ? Math.max(MIN_WIDTH, w) : spec.prefWidth());
        col.setMinWidth(MIN_WIDTH);
        col.setComparator(spec.type().comparator());
        col.setCellValueFactory(cd -> spec.cellObservable(cd.getValue(), lang));
        col.setCellFactory(c -> spec.commit() != null ? new EditableCell<>(leaf) : new ValueCell<>(leaf));
        col.setEditable(spec.commit() != null);
        if (spec.commit() != null) {
            col.setOnEditCommit(ev -> {
                T row = ev.getRowValue();
                if (row == null || !spec.isEditable(row, lang)) return;
                String text = ev.getNewValue() == null ? "" : ev.getNewValue();
                if (text.equals(spec.text(row, lang))) return;
                spec.commit().commit(row, lang, text);
                if (options.onEdited() != null) options.onEdited().accept(leaf, row);
            });
        }
        if (options.header() != null) {
            Node graphic = options.header().apply(leaf);
            if (graphic != null) {
                // The graphic holds the title; the header's own text (kept for accessibility and
                // the code that reads it) is hidden by app.css (content display: graphic only).
                col.setGraphic(graphic);
                col.getStyleClass().add("filter-column");
            }
        }
        if (spec.type() == ValueType.NUMBER) col.getStyleClass().add("number-column");
        return col;
    }

    /** Sets the pseudo-classes of a cell: empty value, not applicable. */
    private static <T> void decorate(TableCell<T, String> cell, Leaf<T> leaf, String item, boolean empty) {
        T row = empty || cell.getTableRow() == null ? null : cell.getTableRow().getItem();
        boolean applies = row != null && leaf.spec().applies(row, leaf.lang());
        cell.pseudoClassStateChanged(EMPTY_VALUE, MARK_EMPTY.get() && leaf.spec().markEmpty() && applies
            && (item == null || item.isEmpty()));
        cell.pseudoClassStateChanged(NOT_APPLICABLE, row != null && !applies);
    }

    private static final class ValueCell<T> extends TableCell<T, String> {
        private final Leaf<T> leaf;

        ValueCell(Leaf<T> leaf) {
            this.leaf = leaf;
            if (leaf.type() == ValueType.NUMBER) getStyleClass().add("number-cell");
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
            decorate(this, leaf, item, empty);
        }
    }

    private static final class EditableCell<T> extends TextFieldTableCell<T, String> {
        private final Leaf<T> leaf;

        EditableCell(Leaf<T> leaf) {
            super(new DefaultStringConverter());
            this.leaf = leaf;
            if (leaf.type() == ValueType.NUMBER) getStyleClass().add("number-cell");
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            T row = empty || getTableRow() == null ? null : getTableRow().getItem();
            setEditable(row != null && leaf.spec().isEditable(row, leaf.lang()));
            decorate(this, leaf, item, empty);
        }
    }
}
