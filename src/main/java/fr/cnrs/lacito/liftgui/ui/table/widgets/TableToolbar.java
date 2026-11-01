package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.FilterModel;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.collections.MapChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * The bar above a table: the Columns button (set by the view), one chip per active filter
 * ("Gloss·en contains "to" ✕"; a click shows the column's filter), a notice (filters
 * cleared to show an object, with Undo), "Clear all" and the number of rows shown.
 */
public final class TableToolbar extends HBox {

    private final FilterModel model;
    private final Function<String, String> nameOf;
    private final Consumer<String> reveal;
    private final HBox leftSlot = new HBox(6);
    private final Label filtersLabel = new Label(I18n.get("filter.chipsLabel"));
    private final FlowPane chips = new FlowPane(6, 4);
    private final Label notice = new Label();
    private final Hyperlink undo = new Hyperlink(I18n.get("filter.undo"));
    private final HBox noticeBox = new HBox(4, notice, undo);
    private final Button clearAll = new Button(I18n.get("filter.resetAll"));
    private final Label count = new Label();

    /**
     * @param nameOf   the short name of a column ("Gloss·en"), by leaf id
     * @param reveal   shows the filter of a column, by leaf id
     * @param onClear  "Clear all"
     */
    public TableToolbar(FilterModel model, Function<String, String> nameOf, Consumer<String> reveal, Runnable onClear) {
        this.model = model;
        this.nameOf = nameOf;
        this.reveal = reveal;
        getStyleClass().addAll("no-dirty", "table-toolbar");
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(0, 6, 4, 6));
        filtersLabel.getStyleClass().add("filter-chips-label");
        chips.getStyleClass().add("filter-chips");
        HBox.setHgrow(chips, Priority.ALWAYS);
        chips.setMinWidth(0);
        chips.setPrefWrapLength(400);
        noticeBox.getStyleClass().add("filter-notice");
        noticeBox.setAlignment(Pos.CENTER_LEFT);
        clearNotice();
        clearAll.setId("resetFilters");
        clearAll.setOnAction(e -> onClear.run());
        count.getStyleClass().add("filter-count-label");
        // Only the chips shrink: the buttons and labels keep their size.
        for (javafx.scene.layout.Region r : new javafx.scene.layout.Region[] {leftSlot, filtersLabel, clearAll, count}) {
            r.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        getChildren().addAll(leftSlot, filtersLabel, chips, noticeBox, clearAll, count);
        model.filters().addListener((MapChangeListener<String, ColumnFilter>) c -> refresh());
        refresh();
    }

    /** Where the view puts its Columns button. */
    public HBox leftSlot() {
        return leftSlot;
    }

    /** Rebuilds the chips (after a change of the filters or of the columns). */
    public void refresh() {
        chips.getChildren().clear();
        for (Map.Entry<String, ColumnFilter> e : model.filters().entrySet()) chips.getChildren().add(chip(e.getKey(), e.getValue()));
        boolean any = !model.isEmpty();
        filtersLabel.setVisible(any);
        filtersLabel.setManaged(any);
        count.setVisible(any);
        count.setManaged(any);
    }

    private Node chip(String leafId, ColumnFilter f) {
        Hyperlink text = new Hyperlink(nameOf.apply(leafId) + " " + FilterTexts.describe(f));
        text.getStyleClass().add("filter-chip-text");
        text.setOnAction(e -> reveal.accept(leafId));
        Button remove = new Button("✕");
        remove.getStyleClass().add("filter-chip-remove");
        remove.setTooltip(new Tooltip(I18n.get("filter.removeChip")));
        remove.setFocusTraversable(false);
        remove.setOnAction(e -> model.remove(leafId));
        HBox chip = new HBox(2, text, remove);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("filter-chip");
        chip.setUserData(leafId);
        return chip;
    }

    public void setCount(int shown, int total) {
        count.setText(I18n.get("filter.count", shown, total));
    }

    /** Shows a notice, with an Undo link when {@code onUndo} is not null. */
    public void setNotice(String text, Runnable onUndo) {
        notice.setText(text);
        undo.setVisible(onUndo != null);
        undo.setManaged(onUndo != null);
        undo.setOnAction(e -> {
            clearNotice();
            if (onUndo != null) onUndo.run();
        });
        noticeBox.setVisible(true);
        noticeBox.setManaged(true);
    }

    public void clearNotice() {
        noticeBox.setVisible(false);
        noticeBox.setManaged(false);
    }

    public String noticeText() {
        return noticeBox.isVisible() ? notice.getText() : "";
    }
}
