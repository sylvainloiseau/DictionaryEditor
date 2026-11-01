package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.Leaf;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.FilterEngine.OptionCount;
import fr.cnrs.lacito.liftgui.ui.table.filter.OptionFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextMatch;
import java.text.Collator;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * An options filter: a button summing up the choice ("All", "noun, verb", "3 selected"),
 * which opens a checklist of the values of the column with their counts (over the rows of
 * the other filters), a search field, an "only" link per value, "(empty)" and "Exclude
 * selected". Every tick filters at once.
 */
final class OptionFilterControl implements FilterControl {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private final String leafId;
    private final FilterHost host;
    private final Button button = new Button();
    private FilterPopover popover;
    private OptionFilter filter = new OptionFilter(Set.of(), false, false);

    OptionFilterControl(Leaf<?> leaf, FilterHost host) {
        this.leafId = leaf.id();
        this.host = host;
        button.getStyleClass().add("filter-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinWidth(0);
        button.setAccessibleText(I18n.get("filter.accessible", leaf.shortName()));
        button.setOnAction(e -> open());
        button.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN && e.isAltDown()) {
                open();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                host.focusTable();
                e.consume();
            }
        });
        refreshButton();
    }

    private void refreshButton() {
        button.setText(FilterTexts.optionSummary(filter) + "  ▾");
        button.pseudoClassStateChanged(ACTIVE, filter.active());
    }

    private void set(OptionFilter f) {
        filter = f;
        refreshButton();
        host.model().set(leafId, f);
    }

    @Override
    public Node node() {
        return button;
    }

    @Override
    public void sync(ColumnFilter f) {
        filter = f instanceof OptionFilter o ? o : new OptionFilter(Set.of(), false, false);
        refreshButton();
    }

    @Override
    public void focus() {
        button.requestFocus();
    }

    @Override
    public void open() {
        if (popover != null && popover.isShowing()) {
            popover.hide();
            return;
        }
        popover = new FilterPopover(content());
        popover.showBelow(button);
    }

    /** A line of the list: a value (or "" for the empty cells) and its count. */
    private record Item(String value, int count) {}

    private VBox content() {
        emptyBox = new CheckBox();
        List<OptionCount> counts = host.options(leafId);
        ObservableList<Item> items = FXCollections.observableArrayList();
        for (OptionCount c : counts) {
            if (!c.value().isEmpty()) items.add(new Item(c.value(), c.count()));
        }
        int emptyCount = counts.stream().filter(c -> c.value().isEmpty()).mapToInt(OptionCount::count).sum();
        // A value selected before but absent now stays listed, so that it can be unticked.
        for (String v : filter.selected()) {
            if (items.stream().noneMatch(i -> i.value().equals(v))) items.add(new Item(v, 0));
        }

        TextField search = new TextField();
        search.setPromptText(I18n.get("filter.searchValues"));
        search.getStyleClass().add("filter-popover-search");
        FilteredList<Item> shown = new FilteredList<>(items);
        search.textProperty().addListener((obs, o, n) -> {
            String q = TextMatch.fold(n == null ? "" : n.trim());
            shown.setPredicate(i -> q.isEmpty() || TextMatch.fold(i.value()).contains(q));
        });
        Collator collator = Collator.getInstance();
        Comparator<Item> byCount = Comparator.comparingInt(Item::count).reversed()
            .thenComparing(Item::value, collator::compare);
        Comparator<Item> byName = Comparator.comparing(Item::value, collator::compare);
        SortedList<Item> sorted = new SortedList<>(shown, byCount);

        ListView<Item> list = new ListView<>(sorted);
        list.getStyleClass().add("filter-options-list");
        list.setPrefHeight(Math.min(280, Math.max(60, items.size() * 26 + 6)));
        list.setPrefWidth(260);
        list.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            private final Label count = new Label();
            private final Hyperlink only = new Hyperlink(I18n.get("filter.only"));
            private final Region spacer = new Region();
            private final HBox line = new HBox(6, check, spacer, only, count);
            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                line.setAlignment(Pos.CENTER_LEFT);
                count.getStyleClass().add("filter-count");
                only.getStyleClass().add("filter-only");
                only.visibleProperty().bind(hoverProperty());
                check.setOnAction(e -> toggle(getItem().value(), check.isSelected()));
                only.setOnAction(e -> {
                    set(filter.withSelected(Set.of(getItem().value())).withIncludeEmpty(false));
                    list.refresh();
                    emptyBox.setSelected(false);
                });
            }

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                check.setText(item.value());
                check.setSelected(filter.selected().contains(item.value()));
                count.setText(String.valueOf(item.count()));
                pseudoClassStateChanged(PseudoClass.getPseudoClass("zero"), item.count() == 0);
                setGraphic(line);
            }
        });

        CheckBox empty = emptyBox;
        empty.setText(FilterTexts.value("") + "  " + emptyCount);
        empty.setSelected(filter.includeEmpty());
        empty.setOnAction(e -> set(filter.withIncludeEmpty(empty.isSelected())));

        Hyperlink all = new Hyperlink(I18n.get("filter.selectAll"));
        all.setOnAction(e -> {
            set(filter.withSelected(new LinkedHashSet<>(shown.stream().map(Item::value).toList())));
            list.refresh();
        });
        Hyperlink none = new Hyperlink(I18n.get("filter.selectNone"));
        none.setOnAction(e -> {
            set(new OptionFilter(Set.of(), false, filter.negate()));
            empty.setSelected(false);
            list.refresh();
        });
        ToggleButton alpha = new ToggleButton(I18n.get("filter.sortAlpha"));
        alpha.getStyleClass().add("filter-toggle");
        alpha.selectedProperty().addListener((obs, o, n) -> sorted.setComparator(n ? byName : byCount));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox links = new HBox(4, all, none, spacer, alpha);
        links.setAlignment(Pos.CENTER_LEFT);

        CheckBox exclude = new CheckBox(I18n.get("filter.exclude"));
        exclude.setSelected(filter.negate());
        exclude.setOnAction(e -> set(filter.withNegate(exclude.isSelected())));

        VBox box = new VBox(6, search, links, list, empty, new Separator(), exclude);
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                list.requestFocus();
                list.getSelectionModel().selectFirst();
                e.consume();
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE && list.getSelectionModel().getSelectedItem() != null) {
                Item i = list.getSelectionModel().getSelectedItem();
                toggle(i.value(), !filter.selected().contains(i.value()));
                list.refresh();
                e.consume();
            }
        });
        javafx.application.Platform.runLater(search::requestFocus);
        return box;
    }

    /** The "(empty)" box of the open popover. */
    private CheckBox emptyBox = new CheckBox();

    private void toggle(String value, boolean on) {
        Set<String> sel = new LinkedHashSet<>(filter.selected());
        if (on) sel.add(value);
        else sel.remove(value);
        set(filter.withSelected(sel));
    }
}
