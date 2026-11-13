package fr.cnrs.lacito.liftgui.ui.app;

import fr.cnrs.lacito.liftgui.ui.Dialogs;
import fr.cnrs.lacito.liftgui.ui.I18n;
import fr.cnrs.lacito.liftgui.ui.app.Commands.Command;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The keyboard shortcuts: every command of the menus, grouped by menu, with its shortcut
 * and its default. A shortcut is changed by double-clicking it (or Enter) and pressing the
 * new keys; Backspace removes it, Esc cancels. A combination already used offers to replace
 * the other command's; the combinations of text editing and of the system are refused.
 * The changes apply when the dialog closes, at once.
 */
public final class ShortcutsDialog {

    /** A line of the table: a group (a menu), or a command. */
    private record Line(String group, Command command) {
        String label() {
            return command == null ? group : command.label();
        }
    }

    private final Commands commands;
    /** The shortcuts being edited, by command id; null for none. */
    private final Map<String, KeyCombination> edited = new HashMap<>();
    private final TreeTableView<Line> table = new TreeTableView<>();
    private final TextField filter = new TextField();
    private final Label message = new Label();
    private final Button replace = new Button(I18n.get("shortcuts.replace"));
    private final Button cancelReplace = new Button(ButtonType.CANCEL.getText());
    private final HBox messageBar;
    private Command recording;
    private KeyCombination pending;

    private ShortcutsDialog(Commands commands) {
        this.commands = commands;
        for (Command c : commands.all()) edited.put(c.id(), commands.keys(c.id()));

        filter.setId("shortcutsFilter");
        filter.setPromptText(I18n.get("shortcuts.filter"));
        filter.textProperty().addListener((obs, o, n) -> rebuild());

        table.setId("shortcutsTable");
        table.setShowRoot(false);
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefSize(640, 440);
        TreeTableColumn<Line, String> commandCol = new TreeTableColumn<>(I18n.get("shortcuts.command"));
        commandCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getValue().label()));
        commandCol.setPrefWidth(300);
        TreeTableColumn<Line, Line> keysCol = new TreeTableColumn<>(I18n.get("shortcuts.shortcut"));
        keysCol.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue().getValue()));
        keysCol.setCellFactory(col -> new KeysCell());
        keysCol.setPrefWidth(170);
        TreeTableColumn<Line, String> defaultCol = new TreeTableColumn<>(I18n.get("shortcuts.default"));
        defaultCol.setCellValueFactory(c -> {
            Command cmd = c.getValue().getValue().command();
            return new ReadOnlyStringWrapper(cmd == null ? "" : display(cmd.defaultKeys()));
        });
        defaultCol.setPrefWidth(120);
        table.getColumns().add(commandCol);
        table.getColumns().add(keysCol);
        table.getColumns().add(defaultCol);
        table.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) startRecording();
        });
        table.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);

        message.setId("shortcutsMessage");
        message.setWrapText(true);
        replace.setId("shortcutsReplace");
        replace.setOnAction(e -> confirmReplace());
        cancelReplace.setOnAction(e -> clearMessage());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        messageBar = new HBox(8, message, spacer, replace, cancelReplace);
        messageBar.setAlignment(Pos.CENTER_LEFT);
        messageBar.getStyleClass().add("shortcuts-message");
        clearMessage();

        rebuild();
    }

    /** Shows the dialog; the changes are applied when it closes. */
    public static void show(Commands commands, Window owner) {
        ShortcutsDialog d = new ShortcutsDialog(commands);
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("shortcuts.title"));
        if (owner != null) dlg.initOwner(owner);
        else Dialogs.owned(dlg);
        dlg.setResizable(true);
        Label help = new Label(I18n.get("shortcuts.help"));
        help.setWrapText(true);
        Button resetAll = new Button(I18n.get("shortcuts.resetAll"));
        resetAll.setId("shortcutsResetAll");
        resetAll.setOnAction(e -> d.resetAll());
        HBox bottom = new HBox(resetAll);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        VBox content = new VBox(8, help, d.filter, d.table, d.messageBar, bottom);
        content.setPadding(new Insets(4));
        VBox.setVgrow(d.table, Priority.ALWAYS);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().getStyleClass().add("shortcuts-dialog");
        dlg.setOnShown(e -> d.table.requestFocus());
        dlg.showAndWait();
        d.apply();
    }

    /* ─── The table ─── */

    private void rebuild() {
        String f = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
        TreeItem<Line> root = new TreeItem<>(new Line("", null));
        Map<String, TreeItem<Line>> groups = new LinkedHashMap<>();
        for (Command c : commands.all()) {
            if (!f.isEmpty() && !matches(c, f)) continue;
            TreeItem<Line> group = groups.computeIfAbsent(c.group(), g -> {
                TreeItem<Line> item = new TreeItem<>(new Line(g, null));
                item.setExpanded(true);
                root.getChildren().add(item);
                return item;
            });
            group.getChildren().add(new TreeItem<>(new Line(c.group(), c)));
        }
        table.setRoot(root);
    }

    private boolean matches(Command c, String f) {
        if (c.label() != null && c.label().toLowerCase(Locale.ROOT).contains(f)) return true;
        KeyCombination k = edited.get(c.id());
        if (k == null) return false;
        String query = f.replace("cmd", "shortcut").replace("ctrl", "shortcut").replace(" ", "");
        return display(k).toLowerCase(Locale.ROOT).contains(f)
            || k.getName().toLowerCase(Locale.ROOT).replace("ctrl", "shortcut").replace("meta", "shortcut").contains(query);
    }

    private Command selected() {
        TreeItem<Line> item = table.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue().command();
    }

    private void refreshCells() {
        table.refresh();
    }

    /** The shortcut's cell: its keys, "press keys…" while recording, a lock for a fixed one, and ⟲. */
    private final class KeysCell extends TreeTableCell<Line, Line> {
        @Override
        protected void updateItem(Line line, boolean empty) {
            super.updateItem(line, empty);
            setText(null);
            setGraphic(null);
            if (empty || line == null || line.command() == null) return;
            Command c = line.command();
            String text = c == recording ? I18n.get("shortcuts.pressKeys")
                : display(edited.get(c.id())) + (c.fixed() ? "  🔒" : "");
            Label keys = new Label(text);
            keys.getStyleClass().add(c == recording ? "shortcut-recording" : "shortcut-keys");
            HBox box = new HBox(6, keys);
            box.setAlignment(Pos.CENTER_LEFT);
            if (c.fixed()) {
                keys.setTooltip(new javafx.scene.control.Tooltip(I18n.get("shortcuts.fixed")));
            } else if (!Commands.sameKeys(edited.get(c.id()), c.defaultKeys())) {
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button reset = new Button("⟲");
                reset.getStyleClass().add("shortcut-reset");
                reset.setTooltip(new javafx.scene.control.Tooltip(I18n.get("shortcuts.reset")));
                reset.setFocusTraversable(false);
                reset.setOnAction(e -> {
                    edited.put(c.id(), c.defaultKeys());
                    clearMessage();
                    refreshCells();
                });
                box.getChildren().addAll(spacer, reset);
            }
            setGraphic(box);
        }
    }

    /* ─── Recording ─── */

    private void startRecording() {
        Command c = selected();
        if (c == null || c.fixed()) return;
        recording = c;
        clearMessage();
        refreshCells();
    }

    private void onKey(KeyEvent e) {
        if (recording == null) {
            if (e.getCode() == KeyCode.ENTER && selected() != null) {
                e.consume();
                startRecording();
            }
            return;
        }
        e.consume();
        KeyCode code = e.getCode();
        boolean bare = !e.isShortcutDown() && !e.isAltDown() && !e.isControlDown() && !e.isMetaDown() && !e.isShiftDown();
        if (code == KeyCode.ESCAPE && bare) {
            stopRecording();
            return;
        }
        if ((code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) && bare) {
            edited.put(recording.id(), null);
            stopRecording();
            return;
        }
        KeyCombination keys = Commands.fromEvent(e);
        if (keys == null) return;
        record(keys);
    }

    /** Records a combination for the command being recorded, after the checks. */
    void record(KeyCombination keys) {
        if (recording == null) return;
        if (!Commands.hasModifier(keys)) {
            showMessage(I18n.get("shortcuts.needsModifier"), false);
            return;
        }
        if (Commands.isRefused(keys)) {
            showMessage(I18n.get("shortcuts.refused", display(keys)), false);
            return;
        }
        Optional<Command> other = usedBy(keys, recording.id());
        if (other.isPresent()) {
            pending = keys;
            showMessage(I18n.get("shortcuts.conflict", display(keys), other.get().group() + " ▸ " + other.get().label()),
                !other.get().fixed());
            return;
        }
        edited.put(recording.id(), keys);
        stopRecording();
    }

    private Optional<Command> usedBy(KeyCombination keys, String exceptId) {
        return commands.all().stream()
            .filter(c -> !c.id().equals(exceptId) && Commands.sameKeys(edited.get(c.id()), keys))
            .findFirst();
    }

    private void confirmReplace() {
        if (recording == null || pending == null) return;
        usedBy(pending, recording.id()).ifPresent(other -> edited.put(other.id(), null));
        edited.put(recording.id(), pending);
        stopRecording();
    }

    private void stopRecording() {
        recording = null;
        pending = null;
        clearMessage();
        refreshCells();
        table.requestFocus();
    }

    private void showMessage(String text, boolean canReplace) {
        message.setText(text);
        replace.setVisible(canReplace);
        replace.setManaged(canReplace);
        cancelReplace.setVisible(canReplace);
        cancelReplace.setManaged(canReplace);
        messageBar.setVisible(true);
        messageBar.setManaged(true);
    }

    private void clearMessage() {
        pending = null;
        message.setText("");
        messageBar.setVisible(false);
        messageBar.setManaged(false);
    }

    private void resetAll() {
        for (Command c : commands.all()) edited.put(c.id(), c.defaultKeys());
        recording = null;
        clearMessage();
        refreshCells();
    }

    /** Applies the shortcuts edited. */
    private void apply() {
        for (Command c : List.copyOf(commands.all())) {
            if (c.fixed()) continue;
            KeyCombination now = edited.get(c.id());
            if (!Commands.sameKeys(now, commands.keys(c.id()))) commands.setKeys(c.id(), now);
        }
    }

    /** "⌘S", or "—" for none. */
    static String display(KeyCombination keys) {
        return keys == null ? "—" : keys.getDisplayText();
    }
}
