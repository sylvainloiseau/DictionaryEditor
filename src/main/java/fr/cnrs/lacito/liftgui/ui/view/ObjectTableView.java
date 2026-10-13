package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.FilterableTable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A view listing objects in a {@link FilterableTable}: selecting a row shows it in the
 * editor panel.
 *
 * @param <T> the type of the rows
 */
public class ObjectTableView<T> implements DictionaryView {

    protected final EditorContext ctx;
    protected final FilterableTable<T> table;
    private final Class<T> rowType;
    private final String title;
    private Node node;

    /**
     * @param rowType     the type of the rows (to recognise the objects to select)
     * @param catalog     all the columns available for the rows
     * @param rows        the rows; a registry list keeps the table up to date
     * @param title       the title of the view; null for the view's name
     * @param placeholder i18n key of the text shown when there is no row; may be null
     * @param onSelect    shows the selected row
     */
    public ObjectTableView(EditorContext ctx, Class<T> rowType, List<ColumnSpec<T>> catalog, ObservableList<T> rows,
                           String title, String placeholder, Consumer<T> onSelect) {
        this.ctx = ctx;
        this.rowType = rowType;
        this.title = title;
        this.table = new FilterableTable<>(ColumnLayout.defaults(catalog).apply(catalog), rows)
            .bindSearch(ctx.searchText())
            .onCountChanged(ctx::showCount)
            .onSelect(onSelect);
        if (placeholder != null) table.placeholder(I18n.get(placeholder));
        this.node = table.node();
    }

    /** The rows of a view: a subset when one is given, else the given (live) list. */
    public static <T> ObservableList<T> rows(List<?> subset, Class<T> type, ObservableList<T> all) {
        if (subset == null) return all;
        return FXCollections.observableArrayList(subset.stream().filter(type::isInstance).map(type::cast).toList());
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
        return rowType.isInstance(object) && table.select(rowType.cast(object));
    }

    @Override
    public void dispose() {
        table.dispose();
    }

    @Override
    public void refreshCells() {
        table.table().refresh();
    }

    @Override
    public Optional<FilterableTable<?>> table() {
        return Optional.of(table);
    }
}
