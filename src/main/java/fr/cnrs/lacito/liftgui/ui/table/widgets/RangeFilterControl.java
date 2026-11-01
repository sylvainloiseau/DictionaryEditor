package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.Leaf;
import fr.cnrs.lacito.liftgui.ui.table.ValueType;
import fr.cnrs.lacito.liftgui.ui.table.filter.CellMatcher;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.FilterEngine.Bin;
import fr.cnrs.lacito.liftgui.ui.table.filter.RangeFilter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A range filter for numbers and dates: a button summing up the range ("Any", "2 – 5",
 * "≥ 2"), which opens a popover with a histogram of the values (over the rows of the other
 * filters: dragging over the bars chooses a range), a minimum and a maximum (partial dates
 * accepted, with a date picker), presets, and "Include empty".
 */
final class RangeFilterControl implements FilterControl {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final String leafId;
    private final String name;
    private final ValueType type;
    private final FilterHost host;
    private final Button button = new Button();
    private FilterPopover popover;
    private RangeFilter filter;

    RangeFilterControl(Leaf<?> leaf, FilterHost host) {
        this.leafId = leaf.id();
        this.name = leaf.shortName();
        this.type = leaf.type() == ValueType.DATE ? ValueType.DATE : ValueType.NUMBER;
        this.host = host;
        this.filter = new RangeFilter(type, "", "", false);
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
        button.setText(FilterTexts.rangeSummary(filter) + "  ▾");
        button.pseudoClassStateChanged(ACTIVE, filter.active());
    }

    private void set(RangeFilter f) {
        filter = f;
        refreshButton();
        host.model().set(leafId, f);
        if (onChange != null) onChange.run();
    }

    /** Called after a change, to update the open popover. */
    private Runnable onChange;

    @Override
    public Node node() {
        return button;
    }

    @Override
    public void sync(ColumnFilter f) {
        filter = f instanceof RangeFilter r ? r : new RangeFilter(type, "", "", false);
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
        popover.setOnHidden(e -> onChange = null);
        popover.showBelow(button);
    }

    private VBox content() {
        Label title = new Label(name);
        title.getStyleClass().add("filter-popover-title");

        List<Bin> bins = host.histogram(leafId);
        HBox histogram = histogram(bins);

        TextField min = new TextField(filter.min());
        TextField max = new TextField(filter.max());
        min.setPromptText(I18n.get(type == ValueType.DATE ? "filter.from" : "filter.min"));
        max.setPromptText(I18n.get(type == ValueType.DATE ? "filter.to" : "filter.max"));
        for (TextField t : new TextField[] {min, max}) {
            t.setPrefColumnCount(type == ValueType.DATE ? 9 : 5);
            t.getStyleClass().add("filter-range-field");
        }
        PauseTransition debounce = new PauseTransition(Duration.millis(300));
        boolean[] syncing = {false};
        Runnable apply = () -> {
            RangeFilter f = filter.withMin(min.getText()).withMax(max.getText());
            min.pseudoClassStateChanged(ERROR, !f.min().isEmpty() && f.error().filter(f.min()::equals).isPresent());
            max.pseudoClassStateChanged(ERROR, !f.max().isEmpty() && f.error().filter(f.max()::equals).isPresent());
            set(f);
        };
        debounce.setOnFinished(e -> apply.run());
        min.textProperty().addListener((obs, o, n) -> { if (!syncing[0]) debounce.playFromStart(); });
        max.textProperty().addListener((obs, o, n) -> { if (!syncing[0]) debounce.playFromStart(); });
        for (TextField t : new TextField[] {min, max}) {
            t.setOnAction(e -> {
                debounce.stop();
                apply.run();
            });
        }

        HBox fields = new HBox(6);
        fields.setAlignment(Pos.CENTER_LEFT);
        if (type == ValueType.DATE) {
            fields.getChildren().addAll(min, picker(min), new Label("–"), max, picker(max));
        } else {
            fields.getChildren().addAll(min, new Label("–"), max);
        }

        Label hint = new Label();
        hint.getStyleClass().add("filter-hint");
        hint.setWrapText(true);
        host.bounds(leafId).ifPresent(b -> hint.setText(I18n.get("filter.dataRange", shortBound(b.min()), shortBound(b.max()))));

        FlowPane presets = new FlowPane(8, 2);
        if (type == ValueType.DATE) {
            LocalDate today = LocalDate.now();
            presets.getChildren().addAll(
                preset(I18n.get("filter.preset.today"), today.toString(), today.toString(), min, max, syncing),
                preset(I18n.get("filter.preset.days7"), today.minusDays(6).toString(), "", min, max, syncing),
                preset(I18n.get("filter.preset.days30"), today.minusDays(29).toString(), "", min, max, syncing),
                preset(I18n.get("filter.preset.thisYear"), String.valueOf(today.getYear()), "", min, max, syncing));
        } else {
            presets.getChildren().addAll(
                preset("= 0", "0", "0", min, max, syncing),
                preset("≥ 1", "1", "", min, max, syncing),
                preset("≥ 2", "2", "", min, max, syncing),
                preset("≥ 5", "5", "", min, max, syncing));
        }

        CheckBox includeEmpty = new CheckBox(I18n.get("filter.includeEmpty"));
        includeEmpty.setSelected(filter.includeEmpty());
        includeEmpty.setOnAction(e -> set(filter.withIncludeEmpty(includeEmpty.isSelected())));
        Hyperlink clear = new Hyperlink(I18n.get("filter.clearOne"));
        clear.setOnAction(e -> {
            syncing[0] = true;
            min.clear();
            max.clear();
            syncing[0] = false;
            includeEmpty.setSelected(false);
            set(new RangeFilter(type, "", "", false));
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(includeEmpty, spacer, clear);
        bottom.setAlignment(Pos.CENTER_LEFT);

        // Histogram selection follows the filter; a bar chosen fills the fields.
        onChange = () -> {
            highlight(histogram, bins);
            if (!min.getText().equals(filter.min()) || !max.getText().equals(filter.max())) {
                syncing[0] = true;
                min.setText(filter.min());
                max.setText(filter.max());
                syncing[0] = false;
            }
        };
        highlight(histogram, bins);

        VBox box = new VBox(8, title, histogram, fields, hint, presets, bottom);
        box.setPrefWidth(300);
        javafx.application.Platform.runLater(min::requestFocus);
        return box;
    }

    /** A bound as shown in the hint: a date to the day. */
    private String shortBound(String s) {
        return type == ValueType.DATE && s.length() > 10 ? s.substring(0, 10) : s;
    }

    private Hyperlink preset(String text, String lo, String hi, TextField min, TextField max, boolean[] syncing) {
        Hyperlink h = new Hyperlink(text);
        h.setOnAction(e -> {
            syncing[0] = true;
            min.setText(lo);
            max.setText(hi);
            syncing[0] = false;
            set(filter.withMin(lo).withMax(hi));
        });
        return h;
    }

    /** A date picker button that fills {@code field}. */
    private Node picker(TextField field) {
        DatePicker picker = new DatePicker();
        picker.getStyleClass().add("filter-date-picker");
        picker.getEditor().setVisible(false);
        picker.getEditor().setManaged(false);
        picker.setMinWidth(30);
        picker.setPrefWidth(30);
        picker.setMaxWidth(30);
        picker.setFocusTraversable(false);
        picker.valueProperty().addListener((obs, o, n) -> {
            if (n != null) field.setText(n.toString());
        });
        return picker;
    }

    /* ─── Histogram ─── */

    private static final double HISTOGRAM_HEIGHT = 48;

    private HBox histogram(List<Bin> bins) {
        HBox bars = new HBox(1);
        bars.getStyleClass().add("filter-histogram");
        bars.setAlignment(Pos.BOTTOM_LEFT);
        bars.setMinHeight(HISTOGRAM_HEIGHT);
        bars.setPrefHeight(HISTOGRAM_HEIGHT);
        bars.setMaxHeight(HISTOGRAM_HEIGHT);
        if (bins.isEmpty()) {
            bars.setVisible(false);
            bars.setManaged(false);
            return bars;
        }
        int maxCount = Math.max(1, bins.stream().mapToInt(Bin::count).max().orElse(1));
        for (Bin b : bins) {
            Region bar = new Region();
            bar.getStyleClass().add("filter-bar");
            double h = b.count() == 0 ? 1 : Math.max(3, HISTOGRAM_HEIGHT * b.count() / maxCount);
            bar.setMinHeight(h);
            bar.setPrefHeight(h);
            bar.setMaxHeight(h);
            bar.setMinWidth(2);
            HBox.setHgrow(bar, Priority.ALWAYS);
            bar.setMaxWidth(Double.MAX_VALUE);
            Tooltip.install(bar, new Tooltip(b.label() + " : " + b.count()));
            bars.getChildren().add(bar);
        }
        // Press and drag over the bars: the range of the bars covered.
        int[] anchor = {-1};
        bars.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            anchor[0] = index(bars, bins, e.getX());
            choose(bins, anchor[0], anchor[0]);
        });
        bars.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (anchor[0] >= 0) choose(bins, anchor[0], index(bars, bins, e.getX()));
        });
        bars.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> anchor[0] = -1);
        return bars;
    }

    private static int index(HBox bars, List<Bin> bins, double x) {
        double w = bars.getWidth() / bins.size();
        return (int) Math.max(0, Math.min(bins.size() - 1, Math.floor(x / Math.max(1, w))));
    }

    private void choose(List<Bin> bins, int a, int b) {
        Bin lo = bins.get(Math.min(a, b));
        Bin hi = bins.get(Math.max(a, b));
        set(filter.withMin(lo.from()).withMax(hi.to()));
    }

    private void highlight(HBox bars, List<Bin> bins) {
        CellMatcher m = filter.active() ? filter.compile() : null;
        List<Node> children = new ArrayList<>(bars.getChildren());
        for (int i = 0; i < children.size() && i < bins.size(); i++) {
            Bin b = bins.get(i);
            boolean sel = m != null && m.test(b.from(), List::of) && m.test(b.to(), List::of);
            children.get(i).pseudoClassStateChanged(SELECTED, sel);
        }
    }
}
