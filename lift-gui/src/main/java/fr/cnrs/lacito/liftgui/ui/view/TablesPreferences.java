package fr.cnrs.lacito.liftgui.ui.view;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.PreferencesDialog;
import fr.cnrs.lacito.liftgui.ui.editor.EditorContext;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayout;
import fr.cnrs.lacito.liftgui.ui.table.ColumnLayoutStore;
import fr.cnrs.lacito.liftgui.ui.table.ColumnSection;
import fr.cnrs.lacito.liftgui.ui.table.widgets.ColumnChooser;
import fr.cnrs.lacito.liftgui.ui.view.ComponentKinds.ComponentKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Preferences ▸ Tables: the columns of every component table, with the same
 * {@link ColumnChooser} as the Columns popover. The choices are applied on OK (to the open
 * table too), dropped on Cancel. The catalogs come from the open dictionary; without one,
 * there is nothing to choose from.
 */
public final class TablesPreferences implements PreferencesDialog.Page {

    private final ColumnLayoutStore store;
    private final LiftDictionary dictionary;
    /** The layouts chosen in the dialog, by table; absent = unchanged, empty = default. */
    private final Map<String, Optional<ColumnLayout>> pending = new HashMap<>();
    private final ListView<ComponentKind<?>> kinds = new ListView<>(FXCollections.observableArrayList(ComponentKinds.all()));
    private final StackPane chooserHolder = new StackPane();

    public TablesPreferences(EditorContext ctx) {
        this.store = ctx.columnLayouts();
        this.dictionary = ctx.dictionary();
    }

    @Override
    public String title() {
        return I18n.get("prefs.tables");
    }

    @Override
    public Node node() {
        if (dictionary == null) return new Label(I18n.get("placeholder.noDictionary"));
        kinds.setId("prefsTables");
        kinds.setPrefWidth(170);
        kinds.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ComponentKind<?> k, boolean empty) {
                super.updateItem(k, empty);
                setText(empty || k == null ? null : I18n.get(k.key().i18nKey()) + (chosen(k).isPresent() ? "  •" : ""));
            }
        });
        kinds.getSelectionModel().selectedItemProperty().addListener((obs, o, k) -> show(k));
        Button resetAll = new Button(I18n.get("prefs.tablesResetAll"));
        resetAll.setId("prefsTablesResetAll");
        resetAll.setMaxWidth(Double.MAX_VALUE);
        resetAll.setOnAction(e -> {
            for (ComponentKind<?> k : ComponentKinds.all()) pending.put(k.key().name(), Optional.empty());
            kinds.refresh();
            show(kinds.getSelectionModel().getSelectedItem());
        });
        VBox left = new VBox(6, kinds, resetAll);
        VBox.setVgrow(kinds, Priority.ALWAYS);
        Label hint = new Label(I18n.get("prefs.tablesHint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("filter-hint");
        BorderPane pane = new BorderPane(chooserHolder, hint, null, null, left);
        BorderPane.setMargin(left, new Insets(0, 10, 0, 0));
        BorderPane.setMargin(hint, new Insets(0, 0, 8, 0));
        pane.setPadding(new Insets(12));
        kinds.getSelectionModel().selectFirst();
        return pane;
    }

    /** The layout chosen for a kind: in the dialog, else stored. */
    private Optional<ColumnLayout> chosen(ComponentKind<?> k) {
        String key = k.key().name();
        return pending.containsKey(key) ? pending.get(key) : store.layout(key);
    }

    private void show(ComponentKind<?> k) {
        if (k == null) {
            chooserHolder.getChildren().clear();
            return;
        }
        chooserHolder.getChildren().setAll(chooser(k));
    }

    private <T> Node chooser(ComponentKind<T> k) {
        List<ColumnSection<T>> sections = k.sections(dictionary);
        ColumnLayout current = chosen(k).orElseGet(() -> ColumnLayout.defaults(ColumnSection.flatten(sections)));
        ColumnChooser<T> chooser = new ColumnChooser<>(I18n.get(k.key().i18nKey()), sections, current);
        chooser.layoutProperty().addListener((obs, o, n) -> {
            pending.put(k.key().name(), Optional.ofNullable(n));
            kinds.refresh();
        });
        return chooser;
    }

    @Override
    public void apply() {
        pending.forEach((key, layout) -> store.set(key, layout.orElse(null)));
        pending.clear();
    }
}
