package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.Leaf;
import fr.cnrs.lacito.liftgui.ui.table.filter.ColumnFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextFilter;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextOp;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;

/**
 * A text filter: a field, the "Aa" (match case) and ".*" (regular expression) toggles, and
 * a menu of operations (contains, starts with, is exactly, is empty, is not empty, not).
 * Typing filters live (after a short pause); an invalid regular expression is shown in red,
 * with the error in a tooltip. Under 170 px the toggles are only in the menu.
 */
final class TextFilterControl implements FilterControl {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double COMPACT = 170;

    private final String leafId;
    private final FilterHost host;
    private final HBox box = new HBox();
    private final TextField field = new TextField();
    private final ToggleButton caseBtn = new ToggleButton("Aa");
    private final ToggleButton regexBtn = new ToggleButton(".*");
    private final MenuButton menu = new MenuButton();
    private final ToggleGroup opGroup = new ToggleGroup();
    private final CheckMenuItem negateItem = new CheckMenuItem(I18n.get("filter.negate"));
    private final CheckMenuItem caseItem = new CheckMenuItem(I18n.get("filter.matchCase"));
    private final CheckMenuItem regexItem = new CheckMenuItem(I18n.get("filter.regex"));
    private final PauseTransition debounce = new PauseTransition(Duration.millis(150));
    private final Tooltip errorTip = new Tooltip();
    private TextOp op = TextOp.CONTAINS;
    private boolean syncing;

    TextFilterControl(Leaf<?> leaf, FilterHost host) {
        this.leafId = leaf.id();
        this.host = host;
        box.getStyleClass().add("filter-text-box");
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        field.getStyleClass().add("filter-text");
        field.setMinWidth(0);
        field.setAccessibleText(I18n.get("filter.accessible", leaf.shortName()));
        HBox.setHgrow(field, Priority.ALWAYS);

        for (ToggleButton b : new ToggleButton[] {caseBtn, regexBtn}) {
            b.getStyleClass().add("filter-toggle");
            b.setFocusTraversable(false);
            b.managedProperty().bind(b.visibleProperty());
            b.visibleProperty().bind(box.widthProperty().greaterThanOrEqualTo(COMPACT));
        }
        caseBtn.setTooltip(new Tooltip(I18n.get("filter.matchCase")));
        regexBtn.setTooltip(new Tooltip(I18n.get("filter.regex")));
        caseItem.selectedProperty().bindBidirectional(caseBtn.selectedProperty());
        regexItem.selectedProperty().bindBidirectional(regexBtn.selectedProperty());

        menu.getStyleClass().add("filter-menu");
        menu.setFocusTraversable(false);
        menu.setTooltip(new Tooltip(I18n.get("filter.options")));
        for (TextOp o : TextOp.values()) {
            RadioMenuItem item = new RadioMenuItem(FilterTexts.opLabel(o));
            item.setToggleGroup(opGroup);
            item.setUserData(o);
            item.setSelected(o == op);
            menu.getItems().add(item);
        }
        MenuItem clear = new MenuItem(I18n.get("filter.clearOne"));
        clear.setOnAction(e -> clear());
        menu.getItems().addAll(new SeparatorMenuItem(), negateItem, caseItem, regexItem, new SeparatorMenuItem(), clear);
        box.getChildren().addAll(field, caseBtn, regexBtn, menu);

        debounce.setOnFinished(e -> apply());
        field.textProperty().addListener((obs, o, n) -> {
            if (!syncing) debounce.playFromStart();
        });
        opGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            op = (TextOp) n.getUserData();
            field.setDisable(!op.usesText());
            updatePrompt();
            if (!syncing) apply();
        });
        caseBtn.selectedProperty().addListener((obs, o, n) -> { if (!syncing) apply(); });
        regexBtn.selectedProperty().addListener((obs, o, n) -> {
            updatePrompt();
            if (!syncing) apply();
        });
        negateItem.selectedProperty().addListener((obs, o, n) -> { if (!syncing) apply(); });
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && (!field.getText().isEmpty() || !op.usesText())) {
                clear();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                debounce.stop();
                apply();
                host.focusTable();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                debounce.stop();
                apply();
            }
        });
        updatePrompt();
    }

    private TextFilter current() {
        return new TextFilter(field.getText(), op, caseBtn.isSelected(), regexBtn.isSelected(), negateItem.isSelected());
    }

    private void apply() {
        TextFilter f = current();
        Optional<String> error = f.error();
        box.pseudoClassStateChanged(ERROR, error.isPresent());
        if (error.isPresent()) {
            errorTip.setText(I18n.get("filter.invalid", error.get()));
            field.setTooltip(errorTip);
        } else {
            field.setTooltip(null);
        }
        box.pseudoClassStateChanged(ACTIVE, f.active());
        host.model().set(leafId, f);
    }

    private void clear() {
        syncing = true;
        try {
            field.clear();
            selectOp(TextOp.CONTAINS);
            negateItem.setSelected(false);
        } finally {
            syncing = false;
        }
        apply();
    }

    private void selectOp(TextOp o) {
        opGroup.getToggles().stream().filter(t -> t.getUserData() == o).findFirst().ifPresent(t -> t.setSelected(true));
        op = o;
        field.setDisable(!o.usesText());
    }

    private void updatePrompt() {
        String key = !op.usesText() ? (op == TextOp.EMPTY ? "filter.op.empty" : "filter.op.notEmpty")
            : regexBtn.isSelected() ? "filter.prompt.regex"
            : switch (op) {
                case STARTS_WITH -> "filter.prompt.startsWith";
                case EQUALS -> "filter.prompt.equals";
                default -> "filter.prompt.contains";
            };
        field.setPromptText(I18n.get(key));
    }

    @Override
    public Node node() {
        return box;
    }

    @Override
    public void sync(ColumnFilter filter) {
        debounce.stop();
        syncing = true;
        try {
            if (filter instanceof TextFilter t) {
                if (!t.pattern().equals(field.getText())) field.setText(t.pattern());
                selectOp(t.op());
                caseBtn.setSelected(t.matchCase());
                regexBtn.setSelected(t.regex());
                negateItem.setSelected(t.negate());
                box.pseudoClassStateChanged(ACTIVE, t.active());
            } else if (current().active()) {
                // Cleared elsewhere (chip, "Clear all").
                field.clear();
                selectOp(TextOp.CONTAINS);
                negateItem.setSelected(false);
                box.pseudoClassStateChanged(ACTIVE, false);
            }
            box.pseudoClassStateChanged(ERROR, current().error().isPresent());
            updatePrompt();
        } finally {
            syncing = false;
        }
    }

    @Override
    public void focus() {
        if (field.isDisabled()) menu.requestFocus();
        else field.requestFocus();
    }

    @Override
    public void open() {
        focus();
    }
}
