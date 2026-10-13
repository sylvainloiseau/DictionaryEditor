package fr.cnrs.lacito.liftgui.ui.table;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;

/** Creates the {@code TableColumn}s of a table from column specs. */
public final class TableFactory {

    private static final double MIN_WIDTH = 85;

    private TableFactory() {}

    /**
     * Replaces the columns of {@code table} by those described by {@code specs} and returns
     * the leaf columns, in display order.
     */
    public static <T> List<Leaf<T>> build(TableView<T> table, List<ColumnSpec<T>> specs) {
        table.getColumns().clear();
        List<Leaf<T>> leaves = new ArrayList<>();
        boolean editable = false;
        for (ColumnSpec<T> spec : specs) {
            editable |= spec.commit() != null;
            if (!spec.multilingual()) {
                TableColumn<T, String> col = leafColumn(spec, null, spec.title());
                leaves.add(leaf(col));
                table.getColumns().add(col);
                continue;
            }
            TableColumn<T, String> group = new TableColumn<>(spec.title());
            for (String lang : spec.languages()) {
                TableColumn<T, String> col = leafColumn(spec, lang, lang);
                leaves.add(leaf(col));
                group.getColumns().add(col);
            }
            table.getColumns().add(group);
        }
        table.setEditable(editable);
        return leaves;
    }

    @SuppressWarnings("unchecked")
    private static <T> Leaf<T> leaf(TableColumn<T, String> col) {
        return (Leaf<T>) col.getUserData();
    }

    private static <T> TableColumn<T, String> leafColumn(ColumnSpec<T> spec, String lang, String header) {
        TableColumn<T, String> col = new TableColumn<>(header);
        col.setUserData(new Leaf<>(spec, lang));
        col.setPrefWidth(spec.prefWidth());
        col.setMinWidth(MIN_WIDTH);
        col.setCellValueFactory(cd -> {
            T row = cd.getValue();
            if (row != null && spec.observable() != null) return spec.observable().get(row, lang);
            return new ReadOnlyStringWrapper(spec.text(row, lang));
        });
        if (spec.commit() != null) {
            col.setCellFactory(TextFieldTableCell.forTableColumn());
            col.setOnEditCommit(ev -> {
                T row = ev.getRowValue();
                if (row != null) spec.commit().commit(row, lang, ev.getNewValue() == null ? "" : ev.getNewValue());
            });
        } else {
            col.setEditable(false);
        }
        return col;
    }
}
