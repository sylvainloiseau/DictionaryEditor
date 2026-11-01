package fr.cnrs.lacito.liftgui.ui.table.widgets;

import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout.ColumnRef;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSection;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSpec;
import fr.cnrs.lacito.liftgui.ui.table.filter.TextMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Chooses the columns of a table: the columns shown (drag to reorder, a chip per language of
 * a multitext column to show or hide it, ✕ to remove), and the columns available, in
 * collapsible sections (a click adds one). A search field filters both lists on the titles,
 * sections and language codes. "↺ Default" goes back to the default columns; Ctrl+Z undoes
 * the last change.
 * <p>
 * The chosen layout is {@link #layoutProperty()}: the Columns popover applies it at once,
 * the Preferences dialog on OK.
 */
public final class ColumnChooser<T> extends VBox {

    private final List<ColumnSection<T>> sections;
    private final Map<String, ColumnSpec<T>> byId = new LinkedHashMap<>();
    private final Map<String, String> sectionOf = new LinkedHashMap<>();
    private final ColumnLayout defaults;
    private final ObjectProperty<ColumnLayout> layout = new SimpleObjectProperty<>();
    private final Deque<ColumnLayout> undo = new ArrayDeque<>();
    private final TextField search = new TextField();
    private final ObservableList<ColumnRef> shownItems = FXCollections.observableArrayList();
    private final ListView<ColumnRef> shown = new ListView<>(shownItems);
    private final Label shownCount = new Label();
    private final VBox available = new VBox(4);
    private final Map<String, Boolean> expanded = new LinkedHashMap<>();

    /**
     * @param title    the title (the table's name); null for none
     * @param sections all the columns available
     * @param current  the layout to start from
     */
    public ColumnChooser(String title, List<ColumnSection<T>> sections, ColumnLayout current) {
        this.sections = List.copyOf(sections);
        for (ColumnSection<T> s : sections) {
            for (ColumnSpec<T> c : s.columns()) {
                if (byId.putIfAbsent(c.id(), c) == null) sectionOf.put(c.id(), s.title());
            }
        }
        this.defaults = ColumnLayout.defaults(ColumnSection.flatten(sections));
        getStyleClass().add("column-chooser");
        setSpacing(6);

        Label heading = new Label(title == null ? I18n.get("columns.title") : I18n.get("columns.titleOf", title));
        heading.getStyleClass().add("filter-popover-title");
        Hyperlink reset = new Hyperlink("↺ " + I18n.get("columns.default"));
        reset.setId("columnsDefault");
        reset.setOnAction(e -> change(null));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(6, heading, spacer, reset);
        top.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText(I18n.get("columns.search"));
        search.setId("columnsSearch");
        search.textProperty().addListener((obs, o, n) -> refresh());

        Label shownLabel = new Label(I18n.get("columns.shown"));
        shownLabel.getStyleClass().add("column-chooser-heading");
        shownCount.getStyleClass().add("filter-count");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        HBox shownHead = new HBox(shownLabel, sp2, shownCount);

        shown.getStyleClass().add("column-chooser-shown");
        shown.setId("columnsShown");
        shown.setPrefHeight(220);
        shown.setCellFactory(lv -> new ShownCell());
        shown.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            ColumnRef sel = shown.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int i = current().columns().indexOf(sel);
            if (e.isAltDown() && e.getCode() == KeyCode.UP) {
                change(current().moved(sel.id(), i - 1));
                shown.getSelectionModel().select(Math.max(0, i - 1));
                e.consume();
            } else if (e.isAltDown() && e.getCode() == KeyCode.DOWN) {
                change(current().moved(sel.id(), i + 1));
                shown.getSelectionModel().select(Math.min(shownItems.size() - 1, i + 1));
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                change(current().without(sel.id()));
                e.consume();
            }
        });

        Label availableLabel = new Label(I18n.get("columns.available"));
        availableLabel.getStyleClass().add("column-chooser-heading");
        ScrollPane scroll = new ScrollPane(available);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        scroll.getStyleClass().add("column-chooser-available");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(top, search, shownHead, shown, availableLabel, scroll);
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.Z && e.isShortcutDown() && !e.isShiftDown() && !undo.isEmpty()) {
                ColumnLayout previous = undo.pop();
                layout.set(previous.equals(defaults) ? null : previous);
                refresh();
                e.consume();
            }
        });
        layout.set(current == null || current.equals(defaults) ? null : current);
        refresh();
    }

    /** The chosen layout; null for the default columns. */
    public ObjectProperty<ColumnLayout> layoutProperty() {
        return layout;
    }

    /** Gives the focus to the search field. */
    public void focusSearch() {
        search.requestFocus();
    }

    /** The layout shown: the chosen one, or the default one. */
    public ColumnLayout current() {
        return layout.get() == null ? defaults : layout.get();
    }

    private void change(ColumnLayout next) {
        ColumnLayout before = current();
        ColumnLayout after = next == null ? defaults : next;
        if (after.equals(before)) return;
        undo.push(before);
        layout.set(after.equals(defaults) ? null : after);
        refresh();
    }

    /** Adds a column at the end of the shown ones. */
    public void add(String id) {
        change(current().with(ColumnRef.of(id)));
    }

    /* ─── Display ─── */

    private boolean matches(ColumnSpec<T> c, String q) {
        if (q.isEmpty()) return true;
        String text = TextMatch.fold(c.title() + " " + sectionOf.getOrDefault(c.id(), "") + " " + String.join(" ", c.languages()));
        for (String word : q.split("\\s+")) if (!text.contains(word)) return false;
        return true;
    }

    private void refresh() {
        String q = TextMatch.fold(search.getText() == null ? "" : search.getText().trim());
        ColumnLayout l = current();
        ColumnRef selected = shown.getSelectionModel().getSelectedItem();
        shownItems.setAll(l.columns().stream()
            .filter(r -> byId.containsKey(r.id()) && matches(byId.get(r.id()), q)).toList());
        if (selected != null) shownItems.stream().filter(r -> r.id().equals(selected.id())).findFirst()
            .ifPresent(r -> shown.getSelectionModel().select(r));
        shownCount.setText(String.valueOf(l.columns().stream().filter(r -> byId.containsKey(r.id())).count()));

        available.getChildren().clear();
        for (ColumnSection<T> s : sections) {
            List<ColumnSpec<T>> free = s.columns().stream()
                .filter(c -> !l.contains(c.id()) && matches(c, q) && sectionOf.get(c.id()).equals(s.title()))
                .toList();
            if (free.isEmpty()) continue;
            FlowPane items = new FlowPane(4, 4);
            items.getStyleClass().add("column-chooser-items");
            for (ColumnSpec<T> c : free) {
                Button b = new Button("+ " + c.title());
                b.getStyleClass().add("column-chooser-add");
                b.setUserData(c.id());
                if (c.multilingual()) b.setTooltip(new Tooltip(String.join(", ", c.languages())));
                b.setOnAction(e -> add(c.id()));
                items.getChildren().add(b);
            }
            TitledPane pane = new TitledPane(s.title() + "  (" + free.size() + ")", items);
            pane.getStyleClass().add("column-chooser-section");
            pane.setUserData(s.id());
            pane.setExpanded(!q.isEmpty() || expanded.getOrDefault(s.id(), false));
            pane.expandedProperty().addListener((obs, o, n) -> {
                if (q.isEmpty()) expanded.put(s.id(), n);
            });
            available.getChildren().add(pane);
        }
    }

    /** A shown column: drag handle, title, language chips, ✕. */
    private final class ShownCell extends ListCell<ColumnRef> {

        ShownCell() {
            setOnDragDetected(e -> {
                if (getItem() == null) return;
                var db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString("column:" + getItem().id());
                db.setContent(cc);
                e.consume();
            });
            setOnDragOver(e -> {
                if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith("column:")) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            setOnDragDropped(e -> {
                String s = e.getDragboard().getString();
                if (s != null && s.startsWith("column:")) {
                    String id = s.substring("column:".length());
                    int target = getItem() == null ? current().columns().size() : current().columns().indexOf(getItem());
                    change(current().moved(id, target));
                    e.setDropCompleted(true);
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(ColumnRef ref, boolean empty) {
            super.updateItem(ref, empty);
            if (empty || ref == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            ColumnSpec<T> spec = byId.get(ref.id());
            Label handle = new Label("⠿");
            handle.getStyleClass().add("column-chooser-handle");
            Label title = new Label(spec.title());
            title.setMinWidth(60);
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            HBox line = new HBox(6, handle, title);
            line.setAlignment(Pos.CENTER_LEFT);
            if (spec.commit() != null) {
                Label pen = new Label("✎");
                pen.getStyleClass().add("column-chooser-editable");
                pen.setTooltip(new Tooltip(I18n.get("columns.editable")));
                line.getChildren().add(pen);
            }
            if (spec.multilingual()) line.getChildren().add(chips(ref, spec));
            Button remove = new Button("✕");
            remove.getStyleClass().add("filter-chip-remove");
            remove.setTooltip(new Tooltip(I18n.get("columns.remove")));
            remove.setFocusTraversable(false);
            remove.setOnAction(e -> change(current().without(ref.id())));
            line.getChildren().add(remove);
            setText(null);
            setGraphic(line);
        }

        /** One toggle per language: shown or hidden; drag a chip onto another to reorder. */
        private Node chips(ColumnRef ref, ColumnSpec<T> spec) {
            List<String> order = new ArrayList<>();
            if (!ref.allLanguages()) order.addAll(ref.languages().stream().filter(spec.languages()::contains).toList());
            for (String l : spec.languages()) if (!order.contains(l)) order.add(l);
            HBox box = new HBox(3);
            box.setAlignment(Pos.CENTER_LEFT);
            for (String lang : order) {
                ToggleButton chip = new ToggleButton(lang);
                chip.getStyleClass().add("language-chip");
                chip.setFocusTraversable(false);
                chip.setSelected(ref.allLanguages() || ref.languages().contains(lang));
                chip.setOnAction(e -> {
                    List<String> langs = new ArrayList<>();
                    for (Node n : box.getChildren()) {
                        if (n instanceof ToggleButton t && t.isSelected()) langs.add(t.getText());
                    }
                    if (langs.isEmpty()) {
                        chip.setSelected(true);
                        return;
                    }
                    change(current().with(languages(ref, spec, langs)));
                });
                chip.setOnDragDetected(e -> {
                    var db = chip.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString("lang:" + lang);
                    db.setContent(cc);
                    e.consume();
                });
                chip.setOnDragOver(e -> {
                    if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith("lang:")) {
                        e.acceptTransferModes(TransferMode.MOVE);
                    }
                    e.consume();
                });
                chip.setOnDragDropped(e -> {
                    String moved = e.getDragboard().getString().substring("lang:".length());
                    List<String> langs = new ArrayList<>(order);
                    langs.remove(moved);
                    langs.add(Math.max(0, langs.indexOf(lang)), moved);
                    List<String> selected = langs.stream()
                        .filter(l -> ref.allLanguages() || ref.languages().contains(l)).toList();
                    change(current().with(new ColumnRef(ref.id(), selected, ref.filterAs())));
                    e.setDropCompleted(true);
                    e.consume();
                });
                box.getChildren().add(chip);
            }
            return box;
        }
    }

    /** The ref with these languages: "all" when they are all the column's, in its order. */
    private static <T> ColumnRef languages(ColumnRef ref, ColumnSpec<T> spec, List<String> langs) {
        return langs.equals(spec.languages()) ? ref.withLanguages(List.of()) : ref.withLanguages(langs);
    }

    /** The spec of a column id, if available. */
    public Optional<ColumnSpec<T>> spec(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
